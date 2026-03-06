package io.mersel.services.xslt.infrastructure;

import io.mersel.services.xslt.application.models.SanitizationResult;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Attribute;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.regex.Pattern;

/**
 * XSLT HTML çıktısını güvenlik açısından temizler (No-Exfiltration Sandbox).
 * <p>
 * Temel felsefe: scriptler çalışsın ama dışarıya veri çıkaramasın.
 * QR kod, barkod, canvas gibi yerel işlemler serbest;
 * cookie, fetch, location gibi exfiltration API'leri engellenir.
 * <p>
 * Üç aşamalı temizlik:
 * <ol>
 *   <li>Tehlikeli tag ve attribute temizliği (iframe, on*, javascript: URL)</li>
 *   <li>Script exfiltration analizi (her script içeriği blocked API listesine karşı taranır)</li>
 *   <li>Temiz scriptlerin SHA-256 hash hesaplaması (dinamik CSP için)</li>
 * </ol>
 */
@Service
public class HtmlSanitizer {

    private static final Logger log = LoggerFactory.getLogger(HtmlSanitizer.class);

    private static final Set<String> DANGEROUS_TAGS = Set.of(
            "iframe", "object", "embed", "base", "applet"
    );

    /**
     * Exfiltration vektörü olan JavaScript API'leri.
     * Case-insensitive regex pattern olarak derlenir.
     * Her pattern, script içeriğinde bulunursa o script kaldırılır.
     */
    private static final List<ExfiltrationPattern> BLOCKED_PATTERNS = List.of(
            new ExfiltrationPattern("document\\.cookie", "cookie access"),
            new ExfiltrationPattern("document\\.domain", "domain manipulation"),
            new ExfiltrationPattern("\\blocalStorage\\b", "localStorage access"),
            new ExfiltrationPattern("\\bsessionStorage\\b", "sessionStorage access"),
            new ExfiltrationPattern("window\\.location", "redirect/exfiltration via location"),
            new ExfiltrationPattern("location\\.href\\s*=", "redirect/exfiltration via location.href"),
            new ExfiltrationPattern("location\\.replace\\s*\\(", "redirect/exfiltration via location.replace"),
            new ExfiltrationPattern("location\\.assign\\s*\\(", "redirect/exfiltration via location.assign"),
            new ExfiltrationPattern("window\\.open\\s*\\(", "window.open exfiltration"),
            new ExfiltrationPattern("\\bXMLHttpRequest\\b", "XHR network call"),
            new ExfiltrationPattern("\\bfetch\\s*\\(", "fetch API network call"),
            new ExfiltrationPattern("navigator\\.sendBeacon\\s*\\(", "sendBeacon exfiltration"),
            new ExfiltrationPattern("\\bnew\\s+WebSocket\\s*\\(", "WebSocket connection"),
            new ExfiltrationPattern("\\bpostMessage\\s*\\(", "cross-origin messaging"),
            new ExfiltrationPattern("\\beval\\s*\\(", "dynamic code execution (eval)"),
            new ExfiltrationPattern("\\bnew\\s+Function\\s*\\(", "dynamic code execution (Function constructor)"),
            new ExfiltrationPattern("\\bimport\\s*\\(", "dynamic module import"),
            new ExfiltrationPattern("\\balert\\s*\\(", "UI blocking dialog (alert)"),
            new ExfiltrationPattern("\\bconfirm\\s*\\(", "UI blocking dialog (confirm)"),
            new ExfiltrationPattern("\\bprompt\\s*\\(", "UI blocking dialog (prompt)")
    );

    /**
     * Verilen HTML içeriğini sanitize eder.
     *
     * @param htmlContent ham HTML (UTF-8 byte dizisi)
     * @return sanitize sonucu — temiz HTML, izin verilen script hash'leri, kaldırılan script bilgileri
     */
    public SanitizationResult sanitize(byte[] htmlContent) {
        if (htmlContent == null || htmlContent.length == 0) {
            return new SanitizationResult(htmlContent, List.of(), 0, List.of());
        }

        String html = new String(htmlContent, StandardCharsets.UTF_8);
        Document doc = Jsoup.parse(html);
        doc.outputSettings()
                .charset(StandardCharsets.UTF_8)
                .outline(false)
                .indentAmount(0);

        removeDangerousTags(doc);
        removeEventHandlerAttributes(doc);
        removeJavascriptUrls(doc);
        removeMetaRefresh(doc);
        removeDangerousLinks(doc);

        var allowedHashes = new ArrayList<String>();
        var removalReasons = new ArrayList<String>();
        int removedCount = analyzeAndFilterScripts(doc, allowedHashes, removalReasons);

        byte[] sanitizedHtml = doc.outerHtml().getBytes(StandardCharsets.UTF_8);

        if (removedCount > 0) {
            log.info("HTML sanitization tamamlandı — {} script kaldırıldı, {} script izin verildi",
                    removedCount, allowedHashes.size());
        }

        return new SanitizationResult(sanitizedHtml, List.copyOf(allowedHashes), removedCount, List.copyOf(removalReasons));
    }

    private void removeDangerousTags(Document doc) {
        for (String tag : DANGEROUS_TAGS) {
            Elements elements = doc.getElementsByTag(tag);
            if (!elements.isEmpty()) {
                log.debug("Tehlikeli tag kaldırılıyor: <{}> ({} adet)", tag, elements.size());
                elements.remove();
            }
        }
    }

    private void removeEventHandlerAttributes(Document doc) {
        for (Element el : doc.getAllElements()) {
            List<String> toRemove = new ArrayList<>();
            for (Attribute attr : el.attributes()) {
                if (attr.getKey().toLowerCase(Locale.ROOT).startsWith("on")) {
                    toRemove.add(attr.getKey());
                }
            }
            for (String attr : toRemove) {
                el.removeAttr(attr);
            }
        }
    }

    private void removeJavascriptUrls(Document doc) {
        for (Element el : doc.getAllElements()) {
            for (String attr : List.of("href", "src", "action")) {
                String value = el.attr(attr);
                if (!value.isEmpty()) {
                    String trimmed = value.stripLeading().toLowerCase(Locale.ROOT);
                    if (trimmed.startsWith("javascript:") || trimmed.startsWith("vbscript:")) {
                        if ("src".equals(attr) && "img".equalsIgnoreCase(el.tagName())) {
                            // img src'de javascript: URL'i silmek yeterli, tag'i silmeye gerek yok
                        }
                        el.removeAttr(attr);
                    }
                }
            }
        }
    }

    private void removeMetaRefresh(Document doc) {
        Elements metas = doc.select("meta[http-equiv=refresh]");
        if (!metas.isEmpty()) {
            log.debug("meta http-equiv=refresh kaldırılıyor ({} adet)", metas.size());
            metas.remove();
        }
    }

    private void removeDangerousLinks(Document doc) {
        doc.select("link[rel=import]").remove();
        doc.select("link[rel=preload][as=script]").remove();
        doc.select("link[rel=modulepreload]").remove();
    }

    /**
     * Script tag'lerini analiz eder: exfiltration API'si içerenleri kaldırır,
     * temiz olanların SHA-256 hash'ini hesaplar.
     *
     * @return kaldırılan script sayısı
     */
    private int analyzeAndFilterScripts(Document doc, List<String> allowedHashes, List<String> removalReasons) {
        Elements scripts = doc.getElementsByTag("script");
        int removedCount = 0;

        List<Element> toRemove = new ArrayList<>();

        for (Element script : scripts) {
            if (script.hasAttr("src")) {
                String reason = "Harici script kaynağı engellendi: " + script.attr("src");
                removalReasons.add(reason);
                log.debug(reason);
                toRemove.add(script);
                removedCount++;
                continue;
            }

            String content = script.data();
            if (content == null || content.isBlank()) {
                toRemove.add(script);
                removedCount++;
                continue;
            }

            String blockedReason = findExfiltrationPattern(content);
            if (blockedReason != null) {
                String reason = "Script exfiltration API içeriyor: " + blockedReason;
                removalReasons.add(reason);
                log.debug(reason);
                toRemove.add(script);
                removedCount++;
            } else {
                String hash = computeSha256Base64(content);
                allowedHashes.add(hash);
            }
        }

        for (Element el : toRemove) {
            el.remove();
        }

        return removedCount;
    }

    /**
     * Script içeriğini blocked pattern listesine karşı tarar.
     *
     * @return eşleşen ilk pattern'in açıklaması, yoksa {@code null}
     */
    private String findExfiltrationPattern(String scriptContent) {
        for (ExfiltrationPattern bp : BLOCKED_PATTERNS) {
            if (bp.pattern().matcher(scriptContent).find()) {
                return bp.description();
            }
        }
        return null;
    }

    static String computeSha256Base64(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }

    private record ExfiltrationPattern(Pattern pattern, String description) {
        ExfiltrationPattern(String regex, String description) {
            this(Pattern.compile(regex, Pattern.CASE_INSENSITIVE), description);
        }
    }
}

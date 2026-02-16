package io.mersel.services.xslt.infrastructure;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * HTML çıktısına filigran (watermark) ekler.
 * <p>
 * Dönüştürülmüş HTML'in head ve body bölümlerine CSS stillendirilmiş
 * filigran divleri ekler.
 */
@Service
public class WatermarkService {

    private static final Logger log = LoggerFactory.getLogger(WatermarkService.class);

    @Value("${xslt.watermark.count:3}")
    private int numberOfWatermarks = 3;

    private static final Pattern HEAD_PATTERN = Pattern.compile("<head[^>]*>", Pattern.CASE_INSENSITIVE);
    private static final Pattern BODY_CLOSE_PATTERN = Pattern.compile("</body>", Pattern.CASE_INSENSITIVE);

    private static final String WATERMARK_STYLE = """
            <style>
                .watermark {
                    position: absolute;
                    transform: rotate(-45deg);
                    transform-origin: left top;
                    font-size: 2rem;
                    color: red;
                    z-index: 999;
                    pointer-events: none;
                    opacity: 0.3;
                }
            </style>""";

    /**
     * HTML içeriğine filigran ekler.
     *
     * @param htmlContent Filigran eklenecek HTML (byte dizisi)
     * @param watermarkText Filigran metni
     * @return Filigranlı HTML (byte dizisi)
     */
    public byte[] addWatermark(byte[] htmlContent, String watermarkText) {
        if (htmlContent == null || htmlContent.length == 0 || watermarkText == null || watermarkText.isBlank()) {
            return htmlContent;
        }

        String html = new String(htmlContent, StandardCharsets.UTF_8);

        // CSS stilini head'e ekle
        Matcher headMatcher = HEAD_PATTERN.matcher(html);
        if (headMatcher.find()) {
            html = headMatcher.replaceFirst(headMatcher.group() + WATERMARK_STYLE);
        }

        // Filigran divlerini body sonuna ekle
        Matcher bodyCloseMatcher = BODY_CLOSE_PATTERN.matcher(html);
        if (!bodyCloseMatcher.find()) {
            log.warn("HTML'de </body> etiketi bulunamadı — filigran eklenemedi");
            return html.getBytes(StandardCharsets.UTF_8);
        }

        int count = Math.max(1, numberOfWatermarks);
        int width = 100 / count;
        int left = 10;

        // XSS koruması — filigran metnini HTML escape et
        String escapedText = escapeHtml(watermarkText);

        StringBuilder watermarkDivs = new StringBuilder();
        for (int i = 0; i < count; i++) {
            watermarkDivs.append(String.format(
                    "<div class=\"watermark\" style=\"top: 40%%; left: %d%%;\">%s</div>", left, escapedText));
            watermarkDivs.append(String.format(
                    "<div class=\"watermark\" style=\"bottom: 50%%; left: %d%%; top: 90%%\">%s</div>", left, escapedText));
            left += width - 4;
        }

        html = bodyCloseMatcher.replaceFirst(Matcher.quoteReplacement(watermarkDivs.toString()) + "</body>");

        return html.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Basit HTML escape — &amp;, &lt;, &gt;, &quot; karakterlerini entity'lere dönüştürür.
     */
    private static String escapeHtml(String text) {
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;");
    }
}

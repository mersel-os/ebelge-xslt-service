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
 * Filigran, belgenin tamamını kaplayan tek bir katmandır ({@code #watermark}). Katmanın içinde
 * 300 px arayla döşenmiş satırlar, her satırda da çapraz yazılmış {@code xslt.watermark.count}
 * adet metin bulunur. Böylece çok sayfalı belgeler yazdırıldığında ya da PDF'e çevrildiğinde
 * her sayfaya katmanın kendi dilimi düşer.
 * <ul>
 *   <li>Ekranda katman {@code html}'e göre {@code position: absolute} ile tüm belge yüksekliğini
 *       kaplar; önizlemede belgeyle birlikte kayar. {@code html} göreli yapılmazsa katman yalnızca
 *       ilk ekranı (ilk sayfayı) kaplar.</li>
 *   <li>Baskıda ({@code @media print}) katman {@code position: fixed} olur; Chromium sabit öğeleri
 *       her sayfaya yeniden basar, böylece her sayfa baştan sona filigranlanır. Yalnız
 *       {@code absolute} ile katman içerik yüksekliğinde biter: içeriği ~100 px'ten kısa olan son
 *       sayfada ya da tek sayfalık belgede satırlar kırpılır ve o sayfada filigran görünmez.</li>
 *   <li>Eski WebKit tabanlı PDF motorları (wkhtmltopdf) yalnızca {@code -webkit-transform} tanır;
 *       ön ek olmadan filigran düz basılır.</li>
 *   <li>Saydamlık {@code opacity} yerine {@code rgba} renkle verilir; {@code opacity}
 *       wkhtmltopdf'te filigranı resme çevirip PDF'i büyütüyor.</li>
 *   <li>Özellikler {@code !important} ve id seçiciyle verilir ki XSLT tasarımındaki stiller
 *       filigranı etkilemesin.</li>
 * </ul>
 * Stil {@code <head>} varsa oraya, yoksa katmanın hemen önüne eklenir. {@code </body>} yoksa
 * filigran eklenmez ve içerik değiştirilmeden döner.
 */
@Service
public class WatermarkService {

    private static final Logger log = LoggerFactory.getLogger(WatermarkService.class);

    /**
     * Filigran satır sayısı. Satırlar 300 px arayla döşenir; uzun belgelerde her sayfaya filigran
     * düşmesi bu sayıya bağlıdır (200 satır = 60.000 px, yaklaşık 40+ A4 sayfa). Belge sonunu
     * aşan satırlar katmanın {@code overflow: hidden} ayarıyla kırpılır, sayfa eklemez.
     */
    static final int ROW_COUNT = 200;

    /** Satır başına filigran metni sayısı. */
    @Value("${xslt.watermark.count:3}")
    private int numberOfWatermarks = 3;

    private static final Pattern HEAD_PATTERN = Pattern.compile("<head\\b[^>]*>", Pattern.CASE_INSENSITIVE);
    private static final Pattern BODY_CLOSE_PATTERN = Pattern.compile("</body\\s*>", Pattern.CASE_INSENSITIVE);

    private static final String WATERMARK_BASE_STYLE = """
            #watermark, #watermark > div, #watermark > div > div {
                margin: 0 !important; padding: 0 !important; border: 0 !important; background: none !important; float: none !important;
                min-width: 0 !important; max-width: none !important; min-height: 0 !important; max-height: none !important;
                visibility: visible !important; opacity: 1 !important; box-shadow: none !important; outline: 0 !important; pointer-events: none !important;
            }
            html { position: relative !important; }
            #watermark {
                position: absolute !important; top: 0 !important; right: 0 !important; bottom: 0 !important; left: 0 !important;
                display: block !important; width: auto !important; height: auto !important; overflow: hidden !important; z-index: 2147483647 !important;
                -webkit-transform: none !important; transform: none !important;
            }
            @media print {
                #watermark { position: fixed !important; }
            }
            #watermark > div {
                position: relative !important; display: block !important; width: auto !important; height: 300px !important;
                -webkit-transform: none !important; transform: none !important;
            }
            #watermark > div > div {
                position: absolute !important; top: 240px !important; display: block !important; width: auto !important; height: auto !important;
                white-space: nowrap !important; color: rgba(255, 0, 0, 0.3) !important;
                font-family: Arial, Helvetica, sans-serif !important; font-size: 32px !important; font-style: normal !important; font-weight: normal !important;
                line-height: normal !important; letter-spacing: normal !important; word-spacing: normal !important;
                text-align: left !important; text-indent: 0 !important; text-transform: none !important; text-decoration: none !important; text-shadow: none !important;
                -webkit-transform: rotate(-45deg) !important; transform: rotate(-45deg) !important;
                -webkit-transform-origin: left top !important; transform-origin: left top !important;
            }
            """;

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

        // Katman body'nin son çocuğu olur; son </body> kullanılır
        int bodyCloseIndex = lastMatchStart(BODY_CLOSE_PATTERN, html);
        if (bodyCloseIndex < 0) {
            log.warn("HTML'de </body> etiketi bulunamadı — filigran eklenemedi");
            return htmlContent;
        }

        int count = Math.max(1, numberOfWatermarks);
        String style = buildStyle(count);
        // XSS koruması — filigran metnini HTML escape et
        String layer = buildLayer(escapeHtml(watermarkText), count);

        StringBuilder result = new StringBuilder(html.length() + style.length() + layer.length());
        Matcher headMatcher = HEAD_PATTERN.matcher(html.substring(0, bodyCloseIndex));
        if (headMatcher.find()) {
            result.append(html, 0, headMatcher.end())
                  .append(style)
                  .append(html, headMatcher.end(), bodyCloseIndex)
                  .append(layer);
        } else {
            // head yok — stil katmanla birlikte body'ye eklenir, aksi halde satırlar düz metin olarak basılır
            result.append(html, 0, bodyCloseIndex)
                  .append(style)
                  .append(layer);
        }
        result.append(html, bodyCloseIndex, html.length());

        return result.toString().getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Stil bloğunu oluşturur. Metinlerin yatay konumları satır başına metin sayısına göre hesaplanır
     * (3 metin için %10, %39, %68).
     */
    private static String buildStyle(int count) {
        StringBuilder style = new StringBuilder("<style type=\"text/css\">\n").append(WATERMARK_BASE_STYLE);
        int width = 100 / count;
        int left = 10;
        for (int i = 1; i <= count; i++) {
            style.append("#watermark > div > div:nth-child(").append(i)
                 .append(") { left: ").append(left).append("% !important; }\n");
            left += width - 4;
        }
        return style.append("</style>").toString();
    }

    private static String buildLayer(String escapedText, int count) {
        String cell = "<div>" + escapedText + "</div>";
        String row = "<div>" + cell.repeat(count) + "</div>";
        return "<div id=\"watermark\">" + row.repeat(ROW_COUNT) + "</div>";
    }

    private static int lastMatchStart(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        int last = -1;
        while (matcher.find()) {
            last = matcher.start();
        }
        return last;
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

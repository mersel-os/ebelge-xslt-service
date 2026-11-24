package io.mersel.services.xslt.infrastructure;

import io.mersel.services.xslt.application.interfaces.ReloadResult;
import io.mersel.services.xslt.application.interfaces.Reloadable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * HTML çıktısına filigran (watermark) ekler.
 * <p>
 * Filigran, belgenin tamamını kaplayan tek bir katmandır ({@code #watermark}). Katmanın içinde
 * 300 px arayla döşenmiş {@code xslt.watermark.rows} satır, her satırda da çapraz yazılmış
 * {@code xslt.watermark.count} adet metin bulunur. Böylece çok sayfalı belgeler yazdırıldığında ya da PDF'e çevrildiğinde
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
 * <p>
 * <b>Özelleştirme.</b> Servisi çalıştıran, görünümü kod değiştirmeden ayarlayabilir:
 * <ul>
 *   <li>{@code xslt.watermark.count} ve {@code xslt.watermark.rows}: satır başına metin ve satır
 *       sayısı (açılışta okunur).</li>
 *   <li>Dış asset dizinindeki {@value #CUSTOM_STYLE_ASSET}: dahili stilden <b>sonra</b> eklenir.
 *       Aynı seçiciler ve {@code !important} ile dahili değerler ezilir. Dosya açılışta, dosya
 *       izleyicisi değişiklik gördüğünde ya da {@code POST /v1/admin/assets/reload} ile yeniden
 *       okunur. Geçersizse yok sayılır ve dahili stil kullanılır.</li>
 * </ul>
 * Stil sözleşmesi sabittir: {@code #watermark} katman, {@code #watermark > div} satır,
 * {@code #watermark > div > div} metin.
 */
@Service
public class WatermarkService implements Reloadable {

    private static final Logger log = LoggerFactory.getLogger(WatermarkService.class);

    /** Dış asset dizininde, dahili stilden sonra eklenen isteğe bağlı stil dosyası. */
    static final String CUSTOM_STYLE_ASSET = "watermark.css";

    /** Özel stil dosyası için üst sınır (bayt). Stil her filigranlı yanıta eklenir. */
    static final int MAX_CUSTOM_STYLE_BYTES = 64 * 1024;

    /** {@code </style} dizisi stil bloğunu erken kapatıp HTML enjekte edebilir. */
    private static final Pattern STYLE_CLOSE_PATTERN = Pattern.compile("</style", Pattern.CASE_INSENSITIVE);

    /**
     * Varsayılan satır sayısı. Satırlar 300 px arayla döşenir; ekranda uzun belgelerin sonuna kadar
     * filigran düşmesi bu sayıya bağlıdır (200 satır = 60.000 px, yaklaşık 40+ A4 sayfa). Belge
     * sonunu aşan satırlar katmanın {@code overflow: hidden} ayarıyla kırpılır, sayfa eklemez.
     */
    static final int DEFAULT_ROW_COUNT = 200;

    /** Satır sayısı üst sınırı; yanıt boyutunu sınırlar. */
    static final int MAX_ROW_COUNT = 1000;

    private final AssetManager assetManager;

    /** Satır başına filigran metni sayısı. */
    @Value("${xslt.watermark.count:3}")
    private int numberOfWatermarks = 3;

    /** Satır sayısı (1–{@value #MAX_ROW_COUNT}). */
    @Value("${xslt.watermark.rows:200}")
    private int numberOfRows = DEFAULT_ROW_COUNT;

    /** Geçerli özel stil; yoksa boş. {@link #reload()} ile atomik olarak değiştirilir. */
    private volatile String customStyle = "";

    public WatermarkService(AssetManager assetManager) {
        this.assetManager = assetManager;
    }

    // ── Reloadable ──────────────────────────────────────────────────

    @Override
    public String getName() {
        return "Filigran Stili";
    }

    /**
     * Dış asset dizinindeki {@value #CUSTOM_STYLE_ASSET} dosyasını okur. Dosya yoksa özel stil
     * kaldırılır; geçersizse özel stil kaldırılır ve sonuç başarısız döner.
     */
    @Override
    public ReloadResult reload() {
        long start = System.currentTimeMillis();
        if (!assetManager.assetExists(CUSTOM_STYLE_ASSET)) {
            customStyle = "";
            return ReloadResult.success(getName(), 0, System.currentTimeMillis() - start);
        }
        try {
            String css = readCustomStyle();
            customStyle = css;
            if (!css.isEmpty()) {
                log.info("Özel filigran stili yüklendi: {} ({} karakter)", CUSTOM_STYLE_ASSET, css.length());
            }
            return ReloadResult.success(getName(), css.isEmpty() ? 0 : 1, System.currentTimeMillis() - start);
        } catch (IOException | IllegalArgumentException e) {
            customStyle = "";
            String error = CUSTOM_STYLE_ASSET + " yok sayıldı, dahili stil kullanılıyor: " + e.getMessage();
            log.warn("  {}", error);
            return ReloadResult.failed(getName(), System.currentTimeMillis() - start, error);
        }
    }

    private String readCustomStyle() throws IOException {
        long size = assetManager.getAssetSize(CUSTOM_STYLE_ASSET);
        if (size > MAX_CUSTOM_STYLE_BYTES) {
            throw new IllegalArgumentException("dosya " + MAX_CUSTOM_STYLE_BYTES + " bayttan büyük olamaz (" + size + ")");
        }
        byte[] bytes = assetManager.readAssetBytes(CUSTOM_STYLE_ASSET);
        if (bytes.length > MAX_CUSTOM_STYLE_BYTES) {
            throw new IllegalArgumentException("dosya " + MAX_CUSTOM_STYLE_BYTES + " bayttan büyük olamaz (" + bytes.length + ")");
        }
        String css;
        try {
            css = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        } catch (CharacterCodingException e) {
            throw new IllegalArgumentException("dosya geçerli UTF-8 değil");
        }
        if (css.startsWith("﻿")) {
            css = css.substring(1);
        }
        if (STYLE_CLOSE_PATTERN.matcher(css).find()) {
            throw new IllegalArgumentException("dosya '</style' içeremez");
        }
        return css.strip();
    }

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
        int rows = Math.min(MAX_ROW_COUNT, Math.max(1, numberOfRows));
        String style = buildStyle(count, customStyle);
        // XSS koruması — filigran metnini HTML escape et
        String layer = buildLayer(escapeHtml(watermarkText), count, rows);

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
     * (3 metin için %10, %39, %68). Özel stil en sona eklenir ki dahili kuralları ezebilsin.
     */
    private static String buildStyle(int count, String customCss) {
        StringBuilder style = new StringBuilder("<style type=\"text/css\">\n").append(WATERMARK_BASE_STYLE);
        int width = 100 / count;
        int left = 10;
        for (int i = 1; i <= count; i++) {
            style.append("#watermark > div > div:nth-child(").append(i)
                 .append(") { left: ").append(left).append("% !important; }\n");
            left += width - 4;
        }
        if (!customCss.isEmpty()) {
            style.append("/* ").append(CUSTOM_STYLE_ASSET).append(" */\n").append(customCss).append('\n');
        }
        return style.append("</style>").toString();
    }

    private static String buildLayer(String escapedText, int count, int rows) {
        String cell = "<div>" + escapedText + "</div>";
        String row = "<div>" + cell.repeat(count) + "</div>";
        return "<div id=\"watermark\">" + row.repeat(rows) + "</div>";
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

package io.mersel.services.xslt.web.infrastructure;

/**
 * XSLT Service özel HTTP response header sabitleri.
 * <p>
 * Transform endpoint'i başarılı yanıtlarda {@code text/html} body ile birlikte
 * bu header'ları döner. Tüketici uygulamalar bu header'ları okuyarak
 * dönüşüm metadata'sına erişir.
 *
 * <pre>
 * HTTP/1.1 200 OK
 * Content-Type: text/html; charset=utf-8
 * X-Xslt-Default-Used: true
 * X-Xslt-Custom-Error: XSLT compilation failed at line 42
 * X-Xslt-Duration-Ms: 145
 * X-Xslt-Watermark-Applied: true
 * X-Xslt-Output-Size: 45678
 *
 * &lt;html&gt;...&lt;/html&gt;
 * </pre>
 */
public final class XsltHeaders {

    private XsltHeaders() {
    }

    /** Varsayılan XSLT şablonu kullanıldı mı? ({@code true} / {@code false}) */
    public static final String DEFAULT_USED = "X-Xslt-Default-Used";

    /**
     * Belgeden çıkarılan gömülü (embedded) XSLT kullanıldı mı? ({@code true} / {@code false}).
     * Sadece {@code useEmbeddedXslt=true} parametresi ile istek yapıldığında set edilir.
     */
    public static final String EMBEDDED_USED = "X-Xslt-Embedded-Used";

    /**
     * Kullanıcının sağladığı XSLT başarısız olduğunda hata mesajı.
     * Sadece varsayılana geri dönüldüğünde ({@code X-Xslt-Default-Used: true}) set edilir.
     */
    public static final String CUSTOM_ERROR = "X-Xslt-Custom-Error";

    /** İşlem süresi (milisaniye). */
    public static final String DURATION_MS = "X-Xslt-Duration-Ms";

    /** Filigran uygulandı mı? ({@code true} / {@code false}) */
    public static final String WATERMARK_APPLIED = "X-Xslt-Watermark-Applied";

    /** Çıktı boyutu (byte). */
    public static final String OUTPUT_SIZE = "X-Xslt-Output-Size";

    /** Sanitization sırasında kaldırılan script sayısı. */
    public static final String SCRIPTS_REMOVED = "X-Xslt-Scripts-Removed";

    /**
     * Tespit edilen güvenlik ihlalleri (virgülle ayrılmış).
     * <p>
     * Boşsa veya header yoksa ihlal tespit edilmemiş demektir.
     * Tüketici uygulama bu header'ı inceleyerek XSLT şablonundaki
     * potansiyel saldırı vektörlerini değerlendirebilir.
     * <p>
     * Örnek: {@code cookie access, fetch API network call, redirect/exfiltration via location}
     */
    public static final String SECURITY_VIOLATIONS = "X-Xslt-Security-Violations";
}

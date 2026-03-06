package io.mersel.services.xslt.application.models;

import java.util.List;

/**
 * XSLT dönüşüm sonucu.
 * <p>
 * Infrastructure katmanından Web katmanına dönen dahili model.
 * Controller bu modeli HTTP yanıtına çevirir:
 * <ul>
 *   <li>Başarı: {@code 200 OK} + {@code text/html} body + metadata header'lar</li>
 *   <li>Hata: {@code 422 Unprocessable Entity} + ProblemDetail JSON</li>
 * </ul>
 */
public class TransformResult {

    private final byte[] htmlContent;
    private final boolean defaultXslUsed;
    private final boolean embeddedXsltUsed;
    private final String customXsltError;
    private final boolean watermarkApplied;
    private final long durationMs;
    private final List<String> allowedScriptHashes;
    private final int removedScriptCount;
    private final List<String> securityViolations;

    private TransformResult(Builder builder) {
        this.htmlContent = builder.htmlContent;
        this.defaultXslUsed = builder.defaultXslUsed;
        this.embeddedXsltUsed = builder.embeddedXsltUsed;
        this.customXsltError = builder.customXsltError;
        this.watermarkApplied = builder.watermarkApplied;
        this.durationMs = builder.durationMs;
        this.allowedScriptHashes = builder.allowedScriptHashes != null ? builder.allowedScriptHashes : List.of();
        this.removedScriptCount = builder.removedScriptCount;
        this.securityViolations = builder.securityViolations != null ? builder.securityViolations : List.of();
    }

    /** Dönüştürülmüş HTML içeriği (ham byte dizisi, UTF-8). */
    public byte[] getHtmlContent() {
        return htmlContent;
    }

    /** Varsayılan XSLT şablonu kullanıldı mı? */
    public boolean isDefaultXslUsed() {
        return defaultXslUsed;
    }

    /** Belgeden çıkarılan gömülü XSLT kullanıldı mı? */
    public boolean isEmbeddedXsltUsed() {
        return embeddedXsltUsed;
    }

    /**
     * Kullanıcının sağladığı XSLT başarısız olduğunda hata mesajı.
     * {@code null} ise özel XSLT sağlanmadı veya başarılı oldu.
     */
    public String getCustomXsltError() {
        return customXsltError;
    }

    /** Filigran uygulandı mı? */
    public boolean isWatermarkApplied() {
        return watermarkApplied;
    }

    /** İşlem süresi (milisaniye). */
    public long getDurationMs() {
        return durationMs;
    }

    /** Sanitization sonrası izin verilen script'lerin Base64-encoded SHA-256 hash'leri. */
    public List<String> getAllowedScriptHashes() {
        return allowedScriptHashes;
    }

    /** Sanitization sırasında kaldırılan (engellenen) script sayısı. */
    public int getRemovedScriptCount() {
        return removedScriptCount;
    }

    /**
     * Tespit edilen güvenlik ihlalleri listesi.
     * Boşsa ihlal yok demektir. Tüketici uygulama bu listeyi inceleyerek
     * XSLT kaynağının güvenilirliğini değerlendirebilir.
     */
    public List<String> getSecurityViolations() {
        return securityViolations;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private byte[] htmlContent;
        private boolean defaultXslUsed;
        private boolean embeddedXsltUsed;
        private String customXsltError;
        private boolean watermarkApplied;
        private long durationMs;
        private List<String> allowedScriptHashes;
        private int removedScriptCount;
        private List<String> securityViolations;

        private Builder() {
        }

        public Builder htmlContent(byte[] htmlContent) {
            this.htmlContent = htmlContent;
            return this;
        }

        public Builder defaultXslUsed(boolean defaultXslUsed) {
            this.defaultXslUsed = defaultXslUsed;
            return this;
        }

        public Builder embeddedXsltUsed(boolean embeddedXsltUsed) {
            this.embeddedXsltUsed = embeddedXsltUsed;
            return this;
        }

        public Builder customXsltError(String customXsltError) {
            this.customXsltError = customXsltError;
            return this;
        }

        public Builder watermarkApplied(boolean watermarkApplied) {
            this.watermarkApplied = watermarkApplied;
            return this;
        }

        public Builder durationMs(long durationMs) {
            this.durationMs = durationMs;
            return this;
        }

        public Builder allowedScriptHashes(List<String> allowedScriptHashes) {
            this.allowedScriptHashes = allowedScriptHashes;
            return this;
        }

        public Builder removedScriptCount(int removedScriptCount) {
            this.removedScriptCount = removedScriptCount;
            return this;
        }

        public Builder securityViolations(List<String> securityViolations) {
            this.securityViolations = securityViolations;
            return this;
        }

        public TransformResult build() {
            return new TransformResult(this);
        }
    }
}

package io.mersel.services.xslt.application.models;

import io.mersel.services.xslt.application.enums.TransformType;

/**
 * XSLT dönüşüm isteği modeli.
 * <p>
 * XML belgesini HTML'e dönüştürmek için gerekli bilgileri taşır.
 */
public class TransformRequest {

    private TransformType transformType;

    /**
     * Dönüştürülecek XML belge içeriği.
     */
    private byte[] document;

    /**
     * Kullanıcı tarafından sağlanan özel XSLT şablonu (opsiyonel).
     * null ise varsayılan şablon kullanılır.
     */
    private byte[] transformer;

    /**
     * Filigran metni (opsiyonel).
     * Boş değilse, dönüştürülmüş HTML'e filigran eklenir.
     */
    private String watermarkText;

    /**
     * Belgenin içindeki gömülü XSLT şablonunu kullan.
     * <p>
     * {@code true} ise ve {@code transformer} null ise, servis XML belgesindeki
     * {@code AdditionalDocumentReference/EmbeddedDocumentBinaryObject} içinden
     * {@code .xslt} uzantılı dosyayı çıkarır ve XSLT olarak kullanır.
     */
    private boolean useEmbeddedXslt;

    public TransformRequest() {
    }

    public TransformRequest(TransformType transformType, byte[] document, byte[] transformer,
                            String watermarkText, boolean useEmbeddedXslt) {
        this.transformType = transformType;
        this.document = document;
        this.transformer = transformer;
        this.watermarkText = watermarkText;
        this.useEmbeddedXslt = useEmbeddedXslt;
    }

    public TransformType getTransformType() {
        return transformType;
    }

    public void setTransformType(TransformType transformType) {
        this.transformType = transformType;
    }

    public byte[] getDocument() {
        return document;
    }

    public void setDocument(byte[] document) {
        this.document = document;
    }

    public byte[] getTransformer() {
        return transformer;
    }

    public void setTransformer(byte[] transformer) {
        this.transformer = transformer;
    }

    public String getWatermarkText() {
        return watermarkText;
    }

    public void setWatermarkText(String watermarkText) {
        this.watermarkText = watermarkText;
    }

    public boolean isUseEmbeddedXslt() {
        return useEmbeddedXslt;
    }

    public void setUseEmbeddedXslt(boolean useEmbeddedXslt) {
        this.useEmbeddedXslt = useEmbeddedXslt;
    }
}

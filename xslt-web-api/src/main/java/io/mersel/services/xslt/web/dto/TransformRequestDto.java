package io.mersel.services.xslt.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.web.multipart.MultipartFile;

/**
 * XSLT dönüşüm isteği DTO'su.
 * <p>
 * multipart/form-data olarak alınır.
 */
public class TransformRequestDto {

    @NotBlank(message = "Dönüşüm tipi boş olamaz")
    @Schema(description = "Dönüşüm tipi",
            example = "INVOICE",
            allowableValues = {"INVOICE", "ARCHIVE_INVOICE", "DESPATCH_ADVICE", "RECEIPT_ADVICE", "EMM", "ECHECK"})
    private String transformType;

    @Size(max = 200)
    @Schema(description = "Filigran metni (opsiyonel)",
            example = "TASLAK",
            nullable = true)
    private String watermarkText;

    @NotNull(message = "XML belgesi boş olamaz")
    @Schema(description = "Dönüştürülecek XML belgesi", requiredMode = Schema.RequiredMode.REQUIRED)
    private MultipartFile document;

    @Schema(description = "Özel XSLT şablonu (opsiyonel). Sağlanmazsa varsayılan şablon kullanılır.",
            nullable = true)
    private MultipartFile transformer;

    @Schema(description = """
            Belgede gömülü XSLT şablonunu kullan.
            true ise ve transformer sağlanmadıysa, XML belgesindeki
            AdditionalDocumentReference/EmbeddedDocumentBinaryObject içinden
            .xslt uzantılı dosya çıkarılır ve XSLT olarak kullanılır.
            UBL-TR e-fatura/e-irsaliye belgeleri bu şekilde kendi XSLT'lerini taşır.""",
            nullable = true,
            defaultValue = "true")
    private Boolean useEmbeddedXslt = true;

    public String getTransformType() {
        return transformType;
    }

    public void setTransformType(String transformType) {
        this.transformType = transformType;
    }

    public String getWatermarkText() {
        return watermarkText;
    }

    public void setWatermarkText(String watermarkText) {
        this.watermarkText = watermarkText;
    }

    public MultipartFile getDocument() {
        return document;
    }

    public void setDocument(MultipartFile document) {
        this.document = document;
    }

    public MultipartFile getTransformer() {
        return transformer;
    }

    public void setTransformer(MultipartFile transformer) {
        this.transformer = transformer;
    }

    public Boolean getUseEmbeddedXslt() {
        return useEmbeddedXslt;
    }

    public void setUseEmbeddedXslt(Boolean useEmbeddedXslt) {
        this.useEmbeddedXslt = useEmbeddedXslt;
    }
}

package io.mersel.services.xslt.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.web.multipart.MultipartFile;

/**
 * XML doğrulama isteği DTO'su.
 * <p>
 * multipart/form-data olarak alınır.
 * Belge türü XML içeriğinden otomatik tespit edilir.
 * Sadece {@code source} zorunludur.
 */
public class ValidationRequestDto {

    @NotNull(message = "XML belgesi boş olamaz")
    @Schema(description = "Doğrulanacak XML belgesi", requiredMode = Schema.RequiredMode.REQUIRED)
    private MultipartFile source;

    @Size(max = 100)
    @Schema(description = "Doğrulama profili adı. Profiller, belirli hataların bastırılmasını sağlar. " +
            "Örn: 'unsigned' imza kontrollerini, 'signed' tüm kontrolleri uygular.",
            example = "unsigned",
            nullable = true)
    private String profile;

    @Size(max = 2000)
    @Schema(description = """
            Ek bastırma kuralları (virgülle ayrılmış). \
            Profil üzerindeki bastırmalara ek olarak ad-hoc bastırma uygular. \
            Desteklenen formatlar: \
            • 'RULE_ID' — ruleId eşleşmesi (varsayılan). Örn: InvoiceIDCheck \
            • 'test:XPATH_EXPR' — XPath test ifadesi ile tam eşleşme (e-Defter vb. için). \
              Örn: test:($countKurumUnvani=1 and not($countAdiSoyadi=1)) or ($countAdiSoyadi=1 and not($countKurumUnvani=1)) \
            • 'text:REGEX' — Hata mesajı regex eşleşmesi. Örn: text:.*organizationDescription.*""",
            example = "InvoiceIDCheck",
            nullable = true)
    private String suppressions;

    @Size(max = 5000)
    @Schema(description = """
            Schematron XSLT parametreleri (JSON array formatında). \
            Doğrulama sırasında Schematron XSLT'sine xsl:param olarak geçirilir. \
            Özel şematron kurallarında $parametre_adi şeklinde tanımlanan değişkenleri doldurmak için kullanılır. \
            Her eleman {"key":"parametre_adi","value":"deger"} formatında olmalıdır. \
            Not: 'type' parametresi UBL-TR Main Schematron alt tipini belirler (örn: "TEMELFATURA").""",
            example = """
            [{"key":"documentCurrency","value":"TRY"},{"key":"maxAmount","value":"1000"}]""",
            nullable = true)
    private String parameters;

    public MultipartFile getSource() {
        return source;
    }

    public void setSource(MultipartFile source) {
        this.source = source;
    }

    public String getProfile() {
        return profile;
    }

    public void setProfile(String profile) {
        this.profile = profile;
    }

    public String getSuppressions() {
        return suppressions;
    }

    public void setSuppressions(String suppressions) {
        this.suppressions = suppressions;
    }

    public String getParameters() {
        return parameters;
    }

    public void setParameters(String parameters) {
        this.parameters = parameters;
    }
}

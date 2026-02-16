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
    @Schema(description = "UBL-TR Main Schematron alt tipi (sadece UBL-TR belgeleri için). " +
            "Belirtilmezse varsayılan 'efatura' kullanılır.",
            example = "efatura",
            nullable = true)
    private String ublTrMainSchematronType;

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

    public MultipartFile getSource() {
        return source;
    }

    public void setSource(MultipartFile source) {
        this.source = source;
    }

    public String getUblTrMainSchematronType() {
        return ublTrMainSchematronType;
    }

    public void setUblTrMainSchematronType(String ublTrMainSchematronType) {
        this.ublTrMainSchematronType = ublTrMainSchematronType;
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
}

package io.mersel.services.xslt.application.models;

import java.util.List;

/**
 * GİB paket tanımı.
 * <p>
 * Her paket; indirme URL'i, ZIP içindeki dosya eşleştirme kuralları ve
 * hedef dizin bilgisini içerir.
 *
 * @param id            Paket benzersiz kimliği (örn: "efatura", "ubltr-xsd", "earsiv", "edefter")
 * @param displayName   Gösterim adı (örn: "e-Fatura Paketi")
 * @param downloadUrl   GİB ZIP dosyası indirme URL'i
 * @param fileMapping   ZIP içindeki dosya yolları → hedef asset dizini eşleştirmesi.
 *                      Key: ZIP içindeki glob pattern, Value: hedef dizin yolu.
 * @param description   Paket açıklaması
 */
public record GibPackageDefinition(
        String id,
        String displayName,
        String downloadUrl,
        List<FileExtraction> fileMapping,
        String description
) {

    /**
     * ZIP içinden çıkarılacak dosya tanımı.
     *
     * @param zipPathPattern ZIP içindeki dosya yolu pattern'i (glob-style, örn: "schematron/*.xml")
     * @param targetDir      Hedef asset dizini (örn: "validator/ubl-tr-package/schematron/")
     */
    public record FileExtraction(
            String zipPathPattern,
            String targetDir
    ) {}
}

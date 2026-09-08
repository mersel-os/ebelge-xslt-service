package io.mersel.services.xslt.application.models;

import java.util.List;

/**
 * GİB paket tanımı.
 * <p>
 * Her paket; indirme URL'i, arşiv içindeki dosya eşleştirme kuralları ve
 * hedef dizin bilgisini içerir.
 *
 * @param id            Paket benzersiz kimliği (örn: "efatura", "ubltr-xsd", "earsiv", "edefter")
 * @param displayName   Gösterim adı (örn: "e-Fatura Paketi")
 * @param downloadUrl   GİB arşiv dosyası indirme URL'i
 * @param fileMapping   Arşiv içindeki dosya yolları → hedef asset dizini eşleştirmesi.
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
     * Arşiv içinden çıkarılacak dosya tanımı.
     *
     * @param zipPathPattern Arşiv içindeki dosya yolu pattern'i (glob-style, örn: "schematron/*.xml")
     * @param targetDir      Hedef asset dizini (örn: "validator/ubl-tr-package/schematron/")
     * @param targetFileName Hedefte kullanılacak sabit dosya adı; {@code null} ise kaynak adı korunur
     * @param extractionMode Eşleşen dosyanın doğrudan kopyalanacağını veya içindeki gömülü XSLT'nin
     *                       çıkarılacağını belirtir
     */
    public record FileExtraction(
            String zipPathPattern,
            String targetDir,
            String targetFileName,
            ExtractionMode extractionMode
    ) {
        public FileExtraction(String zipPathPattern, String targetDir) {
            this(zipPathPattern, targetDir, null, ExtractionMode.COPY);
        }

        public FileExtraction(String zipPathPattern, String targetDir, String targetFileName) {
            this(zipPathPattern, targetDir, targetFileName, ExtractionMode.COPY);
        }

        public enum ExtractionMode {
            COPY,
            EMBEDDED_XSLT
        }
    }
}

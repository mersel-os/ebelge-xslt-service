package io.mersel.services.xslt.application.models;

/**
 * İki versiyon arasındaki tek bir dosya için değişiklik özeti.
 *
 * @param path    Dosya yolu (göreceli, örn: "schematron/UBL-TR_Main_Schematron.xml")
 * @param status  Dosya değişiklik durumu
 * @param oldSize Eski dosya boyutu (byte), yeni dosya ise -1
 * @param newSize Yeni dosya boyutu (byte), silinen dosya ise -1
 */
public record FileDiffSummary(
        String path,
        FileChangeStatus status,
        long oldSize,
        long newSize
) {

    public enum FileChangeStatus {
        ADDED,
        REMOVED,
        MODIFIED,
        UNCHANGED
    }
}

package io.mersel.services.xslt.application.models;

/**
 * Tek bir dosya için detaylı diff bilgisi (unified diff formatında).
 *
 * @param path        Dosya yolu (göreceli)
 * @param status      Dosya değişiklik durumu
 * @param unifiedDiff Unified diff formatında değişiklikler (satır satır)
 * @param oldContent  Eski dosya içeriği (isteğe bağlı, büyük dosyalarda null olabilir)
 * @param newContent  Yeni dosya içeriği (isteğe bağlı, büyük dosyalarda null olabilir)
 * @param isBinary    İkili (binary) dosya mı
 */
public record FileDiffDetail(
        String path,
        FileDiffSummary.FileChangeStatus status,
        String unifiedDiff,
        String oldContent,
        String newContent,
        boolean isBinary
) {

    /**
     * İkili dosya için diff oluşturur (içerik gösterilemez).
     */
    public static FileDiffDetail binary(String path, FileDiffSummary.FileChangeStatus status) {
        return new FileDiffDetail(path, status, null, null, null, true);
    }

    /**
     * Metin dosya için diff oluşturur.
     */
    public static FileDiffDetail text(String path, FileDiffSummary.FileChangeStatus status,
                                      String unifiedDiff, String oldContent, String newContent) {
        return new FileDiffDetail(path, status, unifiedDiff, oldContent, newContent, false);
    }
}

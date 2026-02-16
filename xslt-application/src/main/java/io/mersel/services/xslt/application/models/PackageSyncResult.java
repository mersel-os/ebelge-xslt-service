package io.mersel.services.xslt.application.models;

import java.time.Instant;
import java.util.List;

/**
 * GİB paket sync işlemi sonucu.
 *
 * @param packageId      İndirilen paket kimliği
 * @param displayName    Paket gösterim adı
 * @param success        İndirme ve çıkartma başarılı mı
 * @param filesExtracted Çıkartılan dosya sayısı
 * @param extractedFiles Çıkartılan dosya adları listesi
 * @param durationMs     İşlem süresi (milisaniye)
 * @param error          Hata mesajı (başarısız ise)
 */
public record PackageSyncResult(
        String packageId,
        String displayName,
        boolean success,
        int filesExtracted,
        List<String> extractedFiles,
        long durationMs,
        String error
) {

    /**
     * Başarılı sync sonucu oluşturur.
     */
    public static PackageSyncResult success(String packageId, String displayName,
                                            int filesExtracted, List<String> extractedFiles,
                                            long durationMs) {
        return new PackageSyncResult(packageId, displayName, true, filesExtracted, extractedFiles, durationMs, null);
    }

    /**
     * Başarısız sync sonucu oluşturur.
     */
    public static PackageSyncResult failure(String packageId, String displayName, long durationMs, String error) {
        return new PackageSyncResult(packageId, displayName, false, 0, List.of(), durationMs, error);
    }
}

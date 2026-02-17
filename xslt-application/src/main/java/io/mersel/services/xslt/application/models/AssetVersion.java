package io.mersel.services.xslt.application.models;

import java.time.Instant;

/**
 * GİB asset dosyalarının bir sync snapshot'ını temsil eder.
 * <p>
 * Her sync işlemi bir version oluşturur. Pending durumundaki version'lar
 * onaylanmadan live asset'lere uygulanmaz.
 *
 * @param id           Benzersiz versiyon kimliği (örn: "v1", "v2")
 * @param packageId    Paket kimliği (örn: "efatura", "ubltr-xsd")
 * @param displayName  Paket gösterim adı
 * @param timestamp    Sync zaman damgası
 * @param status       Versiyon durumu (PENDING, APPLIED, REJECTED)
 * @param filesSummary Dosya değişiklik özeti
 * @param appliedAt    Onaylanma zamanı (null ise henüz onaylanmamış)
 * @param rejectedAt   Reddedilme zamanı (null ise reddedilmemiş)
 * @param durationMs   Sync indirme süresi (milisaniye)
 */
public record AssetVersion(
        String id,
        String packageId,
        String displayName,
        Instant timestamp,
        VersionStatus status,
        FilesSummary filesSummary,
        Instant appliedAt,
        Instant rejectedAt,
        long durationMs
) {

    public enum VersionStatus {
        PENDING,
        APPLIED,
        REJECTED
    }

    /**
     * Dosya değişiklik özeti.
     */
    public record FilesSummary(
            int added,
            int removed,
            int modified,
            int unchanged
    ) {
        public int total() {
            return added + removed + modified + unchanged;
        }
    }

    /**
     * Yeni pending version oluşturur.
     */
    public static AssetVersion pending(String id, String packageId, String displayName,
                                       FilesSummary filesSummary, long durationMs) {
        return new AssetVersion(id, packageId, displayName, Instant.now(),
                VersionStatus.PENDING, filesSummary, null, null, durationMs);
    }

    /**
     * Pending version'ı applied durumuna geçirir.
     */
    public AssetVersion asApplied() {
        return new AssetVersion(id, packageId, displayName, timestamp,
                VersionStatus.APPLIED, filesSummary, Instant.now(), null, durationMs);
    }

    /**
     * Pending version'ı rejected durumuna geçirir.
     */
    public AssetVersion asRejected() {
        return new AssetVersion(id, packageId, displayName, timestamp,
                VersionStatus.REJECTED, filesSummary, null, Instant.now(), durationMs);
    }
}

package io.mersel.services.xslt.application.models;

import java.util.List;

/**
 * Staging'e indirilen GİB paketinin önizlemesi.
 * <p>
 * Live asset'ler ile staging arasındaki farkları, etkilenen
 * suppression uyarılarını ve versiyon bilgisini içerir.
 *
 * @param packageId Paket kimliği
 * @param version   Pending versiyon bilgisi
 * @param fileDiffs Dosya bazında değişiklik listesi
 * @param warnings  Suppression etki uyarıları
 */
public record SyncPreview(
        String packageId,
        AssetVersion version,
        List<FileDiffSummary> fileDiffs,
        List<SuppressionWarning> warnings
) {
}

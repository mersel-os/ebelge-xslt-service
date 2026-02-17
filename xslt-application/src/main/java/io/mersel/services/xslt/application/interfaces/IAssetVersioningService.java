package io.mersel.services.xslt.application.interfaces;

import io.mersel.services.xslt.application.models.AssetVersion;
import io.mersel.services.xslt.application.models.FileDiffDetail;
import io.mersel.services.xslt.application.models.FileDiffSummary;
import io.mersel.services.xslt.application.models.SyncPreview;

import java.io.IOException;
import java.util.List;

/**
 * GİB asset dosyaları için versiyonlama servisi.
 * <p>
 * GİB'den indirilen paketleri staging alanına alır, mevcut live dosyalarla
 * karşılaştırır, diff üretir ve onay mekanizması sağlar.
 * <p>
 * İş akışı:
 * <ol>
 *   <li>{@link #syncToStaging(String)} — Paketi staging'e indir, diff hesapla</li>
 *   <li>Admin UI'da diff'i incele, suppression uyarılarını kontrol et</li>
 *   <li>{@link #approvePending(String)} — Onayla: live'a uygula, snapshot al</li>
 *   <li>veya {@link #rejectPending(String)} — Reddet: staging'i temizle</li>
 * </ol>
 */
public interface IAssetVersioningService {

    // ── Staging ─────────────────────────────────────────────────────

    /**
     * Belirli bir GİB paketini staging alanına indirir ve diff hesaplar.
     *
     * @param packageId Paket kimliği (örn: "efatura", "ubltr-xsd", "earsiv", "edefter")
     * @return Staging önizlemesi (diff, uyarılar, versiyon bilgisi)
     * @throws IOException İndirme veya dosya işlemi hatası
     */
    SyncPreview syncToStaging(String packageId) throws IOException;

    /**
     * Tüm GİB paketlerini staging alanına indirir ve diff hesaplar.
     *
     * @return Her paket için staging önizlemesi
     * @throws IOException İndirme veya dosya işlemi hatası
     */
    List<SyncPreview> syncAllToStaging() throws IOException;

    /**
     * Pending durumundaki staging'i onaylar ve live'a uygular.
     * <p>
     * İşlem sırası:
     * <ol>
     *   <li>Mevcut live dosyaları history snapshot'a kopyala</li>
     *   <li>Staging dosyalarını live'a kopyala</li>
     *   <li>Staging'i temizle</li>
     *   <li>Asset'leri yeniden yükle (reload)</li>
     * </ol>
     *
     * @param packageId Onaylanacak paket kimliği
     * @return Onaylanan versiyon
     * @throws IOException Dosya işlemi hatası
     * @throws IllegalStateException Bu paket için pending staging yoksa
     */
    AssetVersion approvePending(String packageId) throws IOException;

    /**
     * Pending durumundaki staging'i reddeder ve temizler.
     *
     * @param packageId Reddedilecek paket kimliği
     * @throws IOException Dosya silme hatası
     * @throws IllegalStateException Bu paket için pending staging yoksa
     */
    void rejectPending(String packageId) throws IOException;

    // ── History ──────────────────────────────────────────────────────

    /**
     * Tüm versiyon geçmişini döndürür (en yeni ilk).
     *
     * @return Versiyon listesi
     */
    List<AssetVersion> listVersions();

    /**
     * Belirli bir paket için versiyon geçmişini döndürür.
     *
     * @param packageId Paket kimliği
     * @return Versiyon listesi
     */
    List<AssetVersion> listVersions(String packageId);

    /**
     * Belirli bir versiyonun dosya bazında diff özetini döndürür.
     *
     * @param versionId Versiyon kimliği (örn: "v1")
     * @return Dosya değişiklik özetleri
     * @throws IOException Dosya okuma hatası
     * @throws IllegalArgumentException Versiyon bulunamazsa
     */
    List<FileDiffSummary> getVersionDiff(String versionId) throws IOException;

    /**
     * Belirli bir versiyondaki tek dosyanın detaylı diff'ini döndürür.
     *
     * @param versionId Versiyon kimliği
     * @param filePath  Dosya yolu (göreceli)
     * @return Dosya diff detayı (unified diff formatında)
     * @throws IOException Dosya okuma hatası
     * @throws IllegalArgumentException Versiyon veya dosya bulunamazsa
     */
    FileDiffDetail getFileDiff(String versionId, String filePath) throws IOException;

    // ── Pending Query ───────────────────────────────────────────────

    /**
     * Belirli bir paket için mevcut staging önizlemesini döndürür.
     *
     * @param packageId Paket kimliği
     * @return Staging önizlemesi, pending yoksa null
     */
    SyncPreview getPendingPreview(String packageId);

    /**
     * Tüm pending staging önizlemelerini döndürür.
     *
     * @return Pending staging listesi
     */
    List<SyncPreview> getAllPendingPreviews();

    /**
     * Pending durumundaki bir dosyanın detaylı diff'ini döndürür.
     *
     * @param packageId Paket kimliği
     * @param filePath  Dosya yolu (göreceli)
     * @return Dosya diff detayı
     * @throws IOException Dosya okuma hatası
     * @throws IllegalStateException Pending staging yoksa
     */
    FileDiffDetail getPendingFileDiff(String packageId, String filePath) throws IOException;
}

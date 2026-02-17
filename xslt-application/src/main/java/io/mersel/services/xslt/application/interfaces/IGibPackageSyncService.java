package io.mersel.services.xslt.application.interfaces;

import io.mersel.services.xslt.application.models.GibPackageDefinition;
import io.mersel.services.xslt.application.models.PackageSyncResult;

import java.util.List;

/**
 * GİB paket sync servisi arayüzü.
 * <p>
 * GİB resmi web sitesinden XML paketlerini (e-Fatura, UBL-TR XSD, e-Arşiv, e-Defter)
 * indirip, ZIP'ten çıkartıp, asset dizinine yerleştirir.
 */
public interface IGibPackageSyncService {

    /**
     * Tüm GİB paketlerini sync eder.
     *
     * @return Her paket için sync sonucu
     */
    List<PackageSyncResult> syncAll();

    /**
     * Belirli bir GİB paketini sync eder.
     *
     * @param packageId Paket kimliği (örn: "efatura", "ubltr-xsd", "earsiv", "edefter")
     * @return Sync sonucu
     */
    PackageSyncResult syncPackage(String packageId);

    /**
     * Mevcut GİB paket tanımlarını döndürür.
     *
     * @return Tanımlı paket listesi
     */
    List<GibPackageDefinition> getAvailablePackages();

    /**
     * GİB sync özelliğinin aktif olup olmadığını döndürür.
     *
     * @return aktifse true
     */
    boolean isEnabled();

    /**
     * Mevcut asset kaynağını döndürür ("bundled", "external", "synced").
     *
     * @return asset kaynak bilgisi
     */
    String getCurrentAssetSource();

    /**
     * Belirli bir GİB paketini verilen hedef dizine sync eder.
     * <p>
     * Asset versiyonlama sistemi için staging alanına indirme yapmak amacıyla
     * kullanılır. Bu metot:
     * <ul>
     *   <li>Live asset'leri değiştirmez</li>
     *   <li>{@code assetRegistry.reload()} tetiklemez</li>
     *   <li>Sadece indirip hedef dizine çıkartır</li>
     * </ul>
     *
     * @param packageId Paket kimliği (örn: "efatura", "ubltr-xsd")
     * @param targetDir İndirilen dosyaların yazılacağı dizin
     * @return Sync sonucu
     */
    PackageSyncResult syncPackageToTarget(String packageId, java.nio.file.Path targetDir);
}

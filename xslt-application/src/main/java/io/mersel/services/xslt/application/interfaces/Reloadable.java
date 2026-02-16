package io.mersel.services.xslt.application.interfaces;

/**
 * Asset yaşam döngüsü yönetimi için yeniden yükleme arayüzü.
 * <p>
 * Bu arayüzü uygulayan servisler, {@code AssetRegistry} tarafından
 * keşfedilir ve asset değişikliklerinde otomatik olarak yeniden yüklenir.
 */
public interface Reloadable {

    /**
     * Servise ait tüm önbelleğe alınmış asset'leri yeniden yükler.
     * <p>
     * Uygulama, mevcut cache'i koruyarak yeni cache'i hazırlamalı
     * ve hazır olduğunda atomic swap ile değiştirmelidir.
     *
     * @return Yeniden yükleme sonucu
     */
    ReloadResult reload();

    /**
     * Servisin loglama ve raporlama için kullanılacak adı.
     */
    String getName();
}

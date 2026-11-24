package io.mersel.services.xslt.infrastructure;

import io.mersel.services.xslt.application.interfaces.IGibPackageSyncService;
import io.mersel.services.xslt.application.models.PackageSyncResult;
import io.mersel.services.xslt.infrastructure.config.GibSyncProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * İlk kurulumda GİB paketlerini otomatik indirir.
 * <p>
 * Uygulama tamamen hazır olduktan sonra asset dizinini kontrol eder.
 * GİB paket dizini ({@code validator/}) boşsa (ilk kurulum) ve sync aktifse, tüm GİB paketlerini
 * arka planda (virtual thread) indirir — HTTP isteklerini bloklamaz.
 * <p>
 * {@code validation-assets.gib.sync.auto-sync-on-startup=false} ile devre dışı bırakılabilir.
 */
@Component
public class GibAutoSyncStartupListener {

    private static final Logger log = LoggerFactory.getLogger(GibAutoSyncStartupListener.class);

    /** Tüm GİB paketlerinin şema/Schematron dosyalarını yazdığı kök dizin. */
    static final String GIB_PACKAGE_ROOT = "validator";

    private volatile boolean initialSyncInProgress;

    private final IGibPackageSyncService syncService;
    private final AssetManager assetManager;
    private final GibSyncProperties syncProperties;

    public GibAutoSyncStartupListener(IGibPackageSyncService syncService,
                                       AssetManager assetManager,
                                       GibSyncProperties syncProperties) {
        this.syncService = syncService;
        this.assetManager = assetManager;
        this.syncProperties = syncProperties;
    }

    public boolean isInitialSyncInProgress() {
        return initialSyncInProgress;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        if (!syncProperties.isEnabled() || !syncProperties.isAutoSyncOnStartup()) {
            return;
        }

        // Yalnızca GİB paketlerinin yazıldığı dizine bakılır: açılışta konan özel asset'ler
        // (custom-document-types.yml, custom/, default_transformers/, profiller) ilk sync'i engellemez.
        if (!assetManager.isEmpty(GIB_PACKAGE_ROOT)) {
            log.debug("GİB paket dizini ({}/) dolu — otomatik GİB sync atlanıyor", GIB_PACKAGE_ROOT);
            return;
        }

        log.info("GİB paket dizini ({}/) boş — ilk kurulum algılandı. GİB paketleri arka planda indiriliyor...",
                GIB_PACKAGE_ROOT);
        initialSyncInProgress = true;

        Thread.ofVirtual()
                .name("gib-auto-sync")
                .start(this::performAutoSync);
    }

    private void performAutoSync() {
        try {
            List<PackageSyncResult> results = syncService.syncAll();

            long successCount = results.stream().filter(PackageSyncResult::success).count();
            long failCount = results.size() - successCount;

            if (failCount == 0) {
                log.info("GİB otomatik sync tamamlandı: {} paket başarıyla indirildi", successCount);
            } else {
                log.warn("GİB otomatik sync tamamlandı: {} başarılı, {} başarısız", successCount, failCount);
                results.stream()
                        .filter(r -> !r.success())
                        .forEach(r -> log.warn("  Başarısız paket: {} — {}", r.packageId(), r.error()));
            }
        } catch (Exception e) {
            log.error("GİB otomatik sync sırasında beklenmeyen hata: {}", e.getMessage(), e);
        } finally {
            initialSyncInProgress = false;
        }
    }
}

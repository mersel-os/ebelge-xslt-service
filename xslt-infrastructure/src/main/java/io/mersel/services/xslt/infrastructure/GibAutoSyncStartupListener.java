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
 * Dizin boşsa (ilk kurulum) ve sync aktifse, tüm GİB paketlerini
 * arka planda (virtual thread) indirir — HTTP isteklerini bloklamaz.
 * <p>
 * {@code validation-assets.gib.sync.auto-sync-on-startup=false} ile devre dışı bırakılabilir.
 */
@Component
public class GibAutoSyncStartupListener {

    private static final Logger log = LoggerFactory.getLogger(GibAutoSyncStartupListener.class);

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

        if (!assetManager.isEmpty()) {
            log.debug("Asset dizini dolu — otomatik GİB sync atlanıyor");
            return;
        }

        log.info("Asset dizini boş — ilk kurulum algılandı. GİB paketleri arka planda indiriliyor...");
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

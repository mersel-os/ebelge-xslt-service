package io.mersel.services.xslt.infrastructure;

import io.mersel.services.xslt.application.interfaces.Reloadable;
import io.mersel.services.xslt.application.interfaces.ReloadResult;
import io.mersel.services.xslt.infrastructure.diagnostics.XsltMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Asset yaşam döngüsü orkestratörü.
 * <p>
 * Tüm {@link Reloadable} bileşenleri keşfeder ve koordineli yeniden yükleme sağlar.
 * Tetikleyiciler:
 * <ul>
 *   <li>{@code @PostConstruct} — uygulama başlangıcında</li>
 *   <li>{@code AssetFileWatcher} — dosya değişikliği algılandığında</li>
 *   <li>{@code POST /v1/admin/assets/reload} — manuel API tetiklemesi</li>
 * </ul>
 */
@Component
public class AssetRegistry {

    private static final Logger log = LoggerFactory.getLogger(AssetRegistry.class);

    private final List<Reloadable> reloadables;
    private final XsltMetrics xsltMetrics;
    private final ReentrantLock reloadLock = new ReentrantLock();

    public AssetRegistry(List<Reloadable> reloadables, XsltMetrics xsltMetrics) {
        this.reloadables = reloadables;
        this.xsltMetrics = xsltMetrics;
    }

    @PostConstruct
    void init() {
        log.info("AssetRegistry başlatılıyor — {} bileşen keşfedildi: {}",
                reloadables.size(),
                reloadables.stream().map(Reloadable::getName).toList());
        reload();
    }

    /**
     * Tüm Reloadable bileşenleri yeniden yükler.
     * <p>
     * Thread-safe: aynı anda sadece bir reload çalışır.
     * Bir bileşenin hatası diğerlerini engellemez.
     *
     * @return Her bileşenin sonuçlarını içeren liste
     */
    public List<ReloadResult> reload() {
        if (!reloadLock.tryLock()) {
            log.warn("Reload zaten devam ediyor, atlanıyor");
            return List.of(ReloadResult.failed("AssetRegistry", 0, "Reload already in progress"));
        }

        try {
            long startTime = System.currentTimeMillis();
            log.info("╔══════════════════════════════════════════════════════╗");
            log.info("║  Asset Reload Başlatılıyor                         ║");
            log.info("╚══════════════════════════════════════════════════════╝");

            List<ReloadResult> results = new ArrayList<>();

            for (Reloadable reloadable : reloadables) {
                try {
                    log.info("  → {} yeniden yükleniyor...", reloadable.getName());
                    ReloadResult result = reloadable.reload();
                    results.add(result);

                    switch (result.status()) {
                        case OK -> log.info("    ✓ {} — {} öğe yüklendi ({} ms)",
                                result.componentName(), result.loadedCount(), result.durationMs());
                        case PARTIAL -> {
                            log.warn("    ⚠ {} — {} öğe yüklendi, {} hata ({} ms)",
                                    result.componentName(), result.loadedCount(),
                                    result.errors().size(), result.durationMs());
                            result.errors().forEach(e -> log.warn("      {}", e));
                        }
                        case FAILED -> {
                            log.error("    ✗ {} — BAŞARISIZ ({} ms)", result.componentName(), result.durationMs());
                            result.errors().forEach(e -> log.error("      {}", e));
                        }
                    }
                } catch (Exception e) {
                    log.error("    ✗ {} — beklenmeyen hata: {}", reloadable.getName(), e.getMessage(), e);
                    results.add(ReloadResult.failed(reloadable.getName(), 0, e.getMessage()));
                }
            }

            long totalElapsed = System.currentTimeMillis() - startTime;
            log.info("Asset Reload tamamlandı — toplam süre: {} ms", totalElapsed);

            boolean success = results.stream().allMatch(r -> r.status() == ReloadResult.Status.OK);
            xsltMetrics.recordReload(success, totalElapsed);

            return results;
        } finally {
            reloadLock.unlock();
        }
    }
}

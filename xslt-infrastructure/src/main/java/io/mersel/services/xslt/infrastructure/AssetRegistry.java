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
import java.util.concurrent.atomic.AtomicLong;
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

    /** Reload bileşen döngüsü bir hatayla yarıda kesildiğinde dönen sonucun bileşen adı. */
    static final String INTERRUPTED_COMPONENT = "Asset Reload";

    private final List<Reloadable> reloadables;
    private final XsltMetrics xsltMetrics;
    private final ReentrantLock reloadLock = new ReentrantLock();

    /** Her reload çağrısına verilen artan numara. */
    private final AtomicLong requestSequence = new AtomicLong();

    /** Son başlatılan reload'un kapsadığı en büyük istek numarası. Yalnız kilit altında yazılır. */
    private long coveredSequence;

    /** Son tamamlanan reload'un sonuçları. Yalnız kilit altında yazılır. */
    private List<ReloadResult> lastResults = List.of();

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
     * Thread-safe: aynı anda sadece bir reload çalışır. Bir reload sürerken gelen çağrı
     * atlanmaz; süren reload bitene kadar bekler. Çağrıdan <em>sonra</em> başlamış bir reload
     * tamamlanmışsa onun sonucunu döndürür, yoksa yeni bir reload çalıştırır. Böylece
     * örneğin GİB sync dosyaları süren bir reload'dan sonra yazıp reload istediğinde istek
     * kaybolmaz; aynı anda bekleyen çağrılar tek bir reload'da birleşir.
     * Bir bileşenin hatası (aşırı derin bir şemadan gelen {@link StackOverflowError} dahil)
     * diğerlerini engellemez. Reload başka bir {@link Error} ile yarıda kesilirse son sonuç
     * FAILED olarak işaretlenir ve hata yeniden fırlatılır; bu reload'a bağlanan bekleyen
     * çağrılar önceki başarılı sonucu değil bu FAILED sonucu alır.
     *
     * @return Bu çağrıdan sonra başlamış reload'un bileşen sonuçları
     */
    public List<ReloadResult> reload() {
        long request = requestSequence.incrementAndGet();
        if (reloadLock.isLocked() && !reloadLock.isHeldByCurrentThread()) {
            log.info("Reload devam ediyor — bittiğinde değişiklikleri kapsayan bir reload daha çalışacak");
        }
        reloadLock.lock();
        try {
            if (coveredSequence >= request) {
                // Bu çağrıdan sonra başlamış bir reload az önce tamamlandı: değişiklikler yüklendi.
                log.debug("Reload isteği #{} önceki reload ile karşılandı", request);
                return lastResults;
            }
            coveredSequence = requestSequence.get();
            try {
                lastResults = reloadAll();
            } catch (Throwable t) {
                // coveredSequence bu reload'a bağlanan çağrıları karşılanmış sayıyor: onlara eski
                // sonuç değil, yarıda kalmayı bildiren bir sonuç dönmeli.
                lastResults = List.of(ReloadResult.failed(INTERRUPTED_COMPONENT, 0,
                        "Reload yarıda kesildi, bileşenler kısmen yüklenmiş olabilir: " + describe(t)));
                throw t;
            }
            return lastResults;
        } finally {
            reloadLock.unlock();
        }
    }

    private List<ReloadResult> reloadAll() {
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
            } catch (Exception | StackOverflowError e) {
                log.error("    ✗ {} — beklenmeyen hata: {}", reloadable.getName(), describe(e), e);
                results.add(ReloadResult.failed(reloadable.getName(), 0, describe(e)));
            }
        }

        long totalElapsed = System.currentTimeMillis() - startTime;
        log.info("Asset Reload tamamlandı — toplam süre: {} ms", totalElapsed);

        boolean success = results.stream().allMatch(r -> r.status() == ReloadResult.Status.OK);
        xsltMetrics.recordReload(success, totalElapsed);

        return List.copyOf(results);
    }

    /** Hata mesajı; mesajsız hatalarda (örn. {@link StackOverflowError}) hata sınıfı. */
    private static String describe(Throwable t) {
        return t.getMessage() != null ? t.getMessage() : t.toString();
    }
}

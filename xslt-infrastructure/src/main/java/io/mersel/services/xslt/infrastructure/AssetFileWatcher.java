package io.mersel.services.xslt.infrastructure;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;

/**
 * External asset dizinini dosya değişiklikleri için izler.
 * <p>
 * {@code xslt.assets.watch-enabled=true} olduğunda aktif olur.
 * Dosya değişikliği algılandığında debounce sonrası {@link AssetRegistry#reload()} tetikler.
 * <p>
 * Debounce sayesinde GİB paketindeki 3 dosya aynı anda güncellendiğinde
 * tek bir reload tetiklenir.
 */
@Component
@ConditionalOnProperty(name = "xslt.assets.watch-enabled", havingValue = "true")
public class AssetFileWatcher {

    private static final Logger log = LoggerFactory.getLogger(AssetFileWatcher.class);

    /** Reload tarafından üretilen dosyaların dizini — izleme dışı bırakılır (sonsuz döngü önleme). */
    private static final String IGNORED_DIR = "auto-generated";

    /**
     * Asset sayılmayan kök dizinler: reload çıktıları ve sürüm yönetiminin indirme ({@code staging/})
     * ile geçmiş ({@code history/}) alanları. Sürüm onayı reload'u kendisi tetikler; bu dizinlerdeki
     * yazmalar ayrıca reload planlamamalı.
     */
    private static final Set<String> IGNORED_ROOT_DIRS = Set.of(IGNORED_DIR, "staging", "history");

    private final AssetRegistry assetRegistry;
    private final long debounceMs;
    private final String externalPath;

    private WatchService watchService;
    /** İzlenen dizinler ve kayıt anahtarları; silinen dizinin anahtarı geçersizleşir. */
    private final Map<Path, WatchKey> registeredDirs = new ConcurrentHashMap<>();
    private ExecutorService watchExecutor;
    private ScheduledExecutorService debounceExecutor;
    private ScheduledFuture<?> pendingReload;
    /** Olay kuyruğu taştı; planlanan reload'dan önce dizinler yeniden taranmalı. */
    private volatile boolean rescanPending;

    public AssetFileWatcher(
            AssetRegistry assetRegistry,
            @Value("${xslt.watcher.debounce-ms:500}") long debounceMs,
            @Value("${xslt.assets.external-path:}") String externalPath) {
        this.assetRegistry = assetRegistry;
        this.debounceMs = debounceMs;
        this.externalPath = externalPath;
    }

    @PostConstruct
    void init() {
        if (externalPath == null || externalPath.isBlank()) {
            log.info("AssetFileWatcher: external-path yapılandırılmamış, dosya izleme atlanıyor");
            return;
        }

        Path watchDir = Path.of(externalPath);
        if (!Files.isDirectory(watchDir)) {
            log.warn("AssetFileWatcher: dizin bulunamadı: {} — dosya izleme atlanıyor", watchDir);
            return;
        }

        try {
            openWatchService(watchDir);

            debounceExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "asset-watcher-debounce");
                t.setDaemon(true);
                return t;
            });

            watchExecutor = Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "asset-file-watcher");
                t.setDaemon(true);
                return t;
            });
            watchExecutor.submit(this::watchLoop);

            log.info("AssetFileWatcher başlatıldı — {} izleniyor (debounce: {} ms)", watchDir, debounceMs);
        } catch (IOException e) {
            log.error("AssetFileWatcher başlatılamadı: {}", e.getMessage(), e);
        }
    }

    /**
     * WatchService'i açar ve asset dizinini alt dizinleriyle kaydeder (olay döngüsü başlatılmaz).
     * Test edilebilirlik için package-private.
     */
    void openWatchService(Path watchDir) throws IOException {
        watchService = FileSystems.getDefault().newWatchService();
        registerRecursive(watchDir);
    }

    @PreDestroy
    void shutdown() {
        log.info("AssetFileWatcher kapatılıyor...");

        if (watchExecutor != null) {
            watchExecutor.shutdownNow();
        }
        if (debounceExecutor != null) {
            debounceExecutor.shutdownNow();
        }
        if (watchService != null) {
            try {
                watchService.close();
            } catch (IOException e) {
                log.warn("WatchService kapatma hatası: {}", e.getMessage());
            }
        }
    }

    /**
     * WatchService event loop.
     */
    private void watchLoop() {
        Path rootDir = Path.of(externalPath);
        while (!Thread.currentThread().isInterrupted()) {
            try {
                WatchKey key = watchService.take(); // blocks
                Path watchable = (Path) key.watchable();

                boolean hasRelevantChange = false;

                for (WatchEvent<?> event : key.pollEvents()) {
                    if (event.kind() == StandardWatchEventKinds.OVERFLOW) {
                        // Olay kuyruğu taştı: neyin değiştiği bilinmiyor, tam reload güvenli taraftır.
                        // Kaybolan ENTRY_CREATE olaylarının dizinleri de kayda alınmalı; yoksa o
                        // dizinlerdeki sonraki değişiklikler hiç algılanmaz.
                        // Linux taşmayı her kayıtlı anahtara bildirir; tarama planlanan reload'dan önce
                        // bir kez yapılır.
                        rescanPending = true;
                        log.debug("WatchService olay taşması — dizinler yeniden taranacak, reload planlanıyor");
                        hasRelevantChange = true;
                        continue;
                    }

                    Path changed = ((WatchEvent<Path>) event).context();

                    // auto-generated, staging ve history değişikliklerini yok say
                    Path fullPath = watchable.resolve(changed);

                    if (isIgnored(rootDir, fullPath)) {
                        log.trace("Asset dışı değişiklik yok sayıldı: {} ({})", changed, event.kind().name());
                        continue;
                    }

                    // Açılıştan sonra oluşan dizinler (örn. GİB sync'in silip yeniden yazdığı paket
                    // dizinleri ya da sonradan eklenen özel asset dizinleri) de izlenmeli. Kayıt anından
                    // önce içine yazılan dosyaların olayları kaçar; bu oluşturma olayı zaten reload planlar.
                    if (event.kind() == StandardWatchEventKinds.ENTRY_CREATE
                            && Files.isDirectory(fullPath, LinkOption.NOFOLLOW_LINKS)) {
                        try {
                            registerRecursive(fullPath);
                            log.debug("Yeni dizin izlemeye alındı: {}", fullPath);
                        } catch (IOException e) {
                            log.warn("Yeni dizin izlemeye alınamadı: {} — {}", fullPath, e.getMessage());
                        }
                    }

                    log.debug("Dosya değişikliği algılandı: {} ({})", changed, event.kind().name());
                    hasRelevantChange = true;
                }

                if (hasRelevantChange) {
                    scheduleReload();
                }

                boolean valid = key.reset();
                if (!valid) {
                    registeredDirs.remove(watchable, key);
                    // Alt dizin silindiyse (örn. GİB sync hedef dizinini temizledi) yalnız onun kaydı
                    // düşer; izleme ancak kök asset dizini kaybolursa durur.
                    if (watchable.equals(rootDir)) {
                        log.warn("Asset dizini artık izlenemiyor, izleme durduruluyor: {}", rootDir);
                        break;
                    }
                    log.debug("İzlenen dizin kaldırıldı: {}", watchable);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (ClosedWatchServiceException e) {
                break;
            }
        }
    }

    /**
     * Debounce ile reload planlar. Her yeni event zamanlayıcıyı sıfırlar.
     */
    private synchronized void scheduleReload() {
        if (pendingReload != null && !pendingReload.isDone()) {
            pendingReload.cancel(false);
        }

        pendingReload = debounceExecutor.schedule(() -> {
            log.info("Dosya değişikliği algılandı — asset reload tetikleniyor");
            try {
                if (rescanPending) {
                    rescanPending = false;
                    int added = registerMissingDirectories();
                    log.debug("Olay taşması sonrası {} yeni dizin izlemeye alındı", added);
                }
                assetRegistry.reload();
            } catch (Throwable t) {
                // Planlanmış görevin Future'ı hatayı sessizce saklar; en azından loglanmalı.
                log.error("File watcher reload hatası: {}", t.getMessage(), t);
            }
        }, debounceMs, TimeUnit.MILLISECONDS);
    }

    /**
     * Path'in asset sayılmayan bir kök dizin ({@code auto-generated/}, {@code staging/},
     * {@code history/}) altında olup olmadığını kontrol eder. Yalnız kök seviyesine bakılır:
     * örneğin {@code validator/.../history} izlenmeye devam eder.
     * Test edilebilirlik için package-private.
     */
    static boolean isIgnored(Path rootDir, Path fullPath) {
        Path relativePath = rootDir.relativize(fullPath);
        return relativePath.getNameCount() > 0
                && IGNORED_ROOT_DIRS.contains(relativePath.getName(0).toString());
    }

    /**
     * Asset dizininin tamamını tarar ve dizinleri (ilk kayıttaki istisnalarla) yeniden kaydeder;
     * zaten izlenen dizinler aynı anahtarla kalır. Olay kuyruğu taştığında ({@code OVERFLOW})
     * oluşturma olayı kaybolan dizinler için kullanılır. Test edilebilirlik için package-private.
     *
     * @return kaydı yeni ya da değişen dizin sayısı (bkz. {@link #registerRecursive})
     */
    int registerMissingDirectories() {
        try {
            return registerRecursive(Path.of(externalPath));
        } catch (IOException e) {
            log.warn("Olay taşması sonrası dizinler izlemeye alınamadı: {}", e.getMessage());
            return 0;
        }
    }

    /** Dizin izleniyor mu (geçerli bir kaydı var mı). Test edilebilirlik için package-private. */
    boolean isRegistered(Path dir) {
        WatchKey key = registeredDirs.get(dir);
        return key != null && key.isValid();
    }

    /**
     * Dizin ve tüm alt dizinlerini WatchService'e kaydeder.
     * {@code auto-generated/} dizinleri ile kökteki {@code staging/} ve {@code history/} kayıt dışı bırakılır.
     *
     * @return kaydı yeni ya da değişen dizin sayısı: yolu ilk kez kaydedilen ya da kayıtlı anahtarından
     *         farklı bir anahtar alan dizinler (zaten izlenen dizin yeniden kaydedilince aynı anahtar döner,
     *         sayılmaz; yerinde yeniden oluşturulan dizin yeni anahtar alır, sayılır; başka ada taşınan dizin
     *         eski anahtarıyla yeni yolunda ilk kez kaydedildiği için o da sayılır)
     */
    private int registerRecursive(Path root) throws IOException {
        Path assetRoot = Path.of(externalPath);
        int[] added = {0};
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                // auto-generated ve sürüm yönetimi dizinlerini izleme dışı bırak
                if ((dir.getFileName() != null && IGNORED_DIR.equals(dir.getFileName().toString()))
                        || isIgnored(assetRoot, dir)) {
                    log.debug("Asset dışı dizin izleme dışı bırakıldı: {}", dir);
                    return FileVisitResult.SKIP_SUBTREE;
                }

                try {
                    // Kayıt her zaman yapılır: aynı dizin için WatchService aynı anahtarı döner. Yolun
                    // kayıtlı anahtarına bakıp atlamak yetmez; dizin başka ada taşınınca anahtarı geçerli
                    // kalır ve aynı yolda yeniden oluşturulan dizin hiç izlenmez.
                    WatchKey key = dir.register(watchService,
                            StandardWatchEventKinds.ENTRY_CREATE,
                            StandardWatchEventKinds.ENTRY_MODIFY,
                            StandardWatchEventKinds.ENTRY_DELETE);
                    if (registeredDirs.put(dir, key) != key) {
                        added[0]++;
                    }
                } catch (NoSuchFileException e) {
                    // Tarama sırasında silindi (örn. GİB sync dizini yeniden yazıyor): taramayı durdurma.
                    log.debug("Dizin kayıttan önce silindi: {}", dir);
                    return FileVisitResult.SKIP_SUBTREE;
                } catch (IOException e) {
                    if (dir.equals(root)) {
                        throw e;
                    }
                    // Tek bir dizin (örn. izleme sınırı) taramanın geri kalanını engellemesin.
                    log.warn("Dizin izlemeye alınamadı: {} — {}", dir, e.getMessage());
                    return FileVisitResult.SKIP_SUBTREE;
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException e) {
                // Tarama sırasında silinen dizin taramayı durdurmamalı.
                log.debug("Dizin taranamadı: {} — {}", file, e.getMessage());
                return FileVisitResult.CONTINUE;
            }
        });
        return added[0];
    }
}

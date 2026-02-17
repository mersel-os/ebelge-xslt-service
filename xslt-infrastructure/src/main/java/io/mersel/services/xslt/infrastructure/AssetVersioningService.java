package io.mersel.services.xslt.infrastructure;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.mersel.services.xslt.application.interfaces.IAssetVersioningService;
import io.mersel.services.xslt.application.interfaces.IGibPackageSyncService;
import io.mersel.services.xslt.application.models.*;
import io.mersel.services.xslt.application.models.AssetVersion.FilesSummary;
import io.mersel.services.xslt.application.models.AssetVersion.VersionStatus;
import io.mersel.services.xslt.application.models.FileDiffSummary.FileChangeStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.*;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * GİB asset dosyaları için versiyonlama servisi implementasyonu.
 * <p>
 * Staging/snapshot yönetimini dosya sistemi üzerinde gerçekleştirir.
 * Versiyon metadata'sı {@code history/versions.json} dosyasında tutulur.
 */
@Service
public class AssetVersioningService implements IAssetVersioningService {

    private static final Logger log = LoggerFactory.getLogger(AssetVersioningService.class);

    private static final String HISTORY_DIR = "history";
    private static final String SNAPSHOTS_DIR = "snapshots";
    private static final String STAGING_DIR = "staging";
    private static final String VERSIONS_FILE = "versions.json";

    private final IGibPackageSyncService gibSyncService;
    private final AssetDiffService diffService;
    private final AssetManager assetManager;
    private final AssetRegistry assetRegistry;
    private final SuppressionImpactAnalyzer suppressionAnalyzer;
    private final ObjectMapper objectMapper;

    private final ReentrantLock versionLock = new ReentrantLock();
    private final Map<String, SyncPreview> pendingPreviews = new ConcurrentHashMap<>();

    @Value("${xslt.assets.external-path:}")
    private String externalPath;

    public AssetVersioningService(
            IGibPackageSyncService gibSyncService,
            AssetDiffService diffService,
            AssetManager assetManager,
            AssetRegistry assetRegistry,
            SuppressionImpactAnalyzer suppressionAnalyzer) {
        this.gibSyncService = gibSyncService;
        this.diffService = diffService;
        this.assetManager = assetManager;
        this.assetRegistry = assetRegistry;
        this.suppressionAnalyzer = suppressionAnalyzer;
        this.objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                .configure(SerializationFeature.INDENT_OUTPUT, true);
    }

    // ── Staging ─────────────────────────────────────────────────────

    @Override
    public SyncPreview syncToStaging(String packageId) throws IOException {
        requireExternalPath();

        log.info("Staging sync başlatılıyor: {}", packageId);

        // Staging dizinini hazırla (temizle + oluştur)
        Path stagingDir = getStagingDir(packageId);
        cleanDirectory(stagingDir);
        Files.createDirectories(stagingDir);

        // GİB paketini doğrudan staging dizinine indir (live'a dokunmaz)
        PackageSyncResult syncResult = gibSyncService.syncPackageToTarget(packageId, stagingDir);
        if (!syncResult.success()) {
            cleanDirectory(stagingDir);
            throw new IOException("GİB paket indirme başarısız: " + syncResult.error());
        }

        // Live vs staging arasında diff hesapla ve preview oluştur
        GibPackageDefinition pkg = findPackageDefinition(packageId);
        SyncPreview preview = buildPreview(packageId, pkg.displayName(), syncResult.durationMs());
        pendingPreviews.put(packageId, preview);

        log.info("Staging sync tamamlandı: {} — {} dosya, {}ms",
                packageId, syncResult.filesExtracted(), syncResult.durationMs());

        return preview;
    }

    @Override
    public List<SyncPreview> syncAllToStaging() throws IOException {
        var previews = new ArrayList<SyncPreview>();
        for (var pkg : gibSyncService.getAvailablePackages()) {
            try {
                previews.add(syncToStaging(pkg.id()));
            } catch (Exception e) {
                log.error("Staging sync başarısız: {} — {}", pkg.id(), e.getMessage());
            }
        }
        return previews;
    }

    @Override
    public AssetVersion approvePending(String packageId) throws IOException {
        requireExternalPath();
        versionLock.lock();
        try {
            SyncPreview preview = pendingPreviews.get(packageId);
            if (preview == null) {
                throw new IllegalStateException("Bu paket için pending staging bulunamadı: " + packageId);
            }

            String versionId = preview.version().id();
            GibPackageDefinition pkg = findPackageDefinition(packageId);
            Path externalDir = Path.of(externalPath);
            Path stagingDir = getStagingDir(packageId);

            Path snapshotDir = getSnapshotDir(versionId);

            // 1. _before: Mevcut live dosyaları kaydet (güncelleme öncesi)
            Path beforeDir = snapshotDir.resolve("_before");
            Files.createDirectories(beforeDir);
            for (var fm : pkg.fileMapping()) {
                Path liveSubDir = externalDir.resolve(fm.targetDir());
                Path beforeSubDir = beforeDir.resolve(fm.targetDir());
                if (Files.isDirectory(liveSubDir)) {
                    copyDirectory(liveSubDir, beforeSubDir);
                }
            }

            // 2. _after: Staging (yeni) dosyaları kaydet (güncelleme sonrası)
            Path afterDir = snapshotDir.resolve("_after");
            Files.createDirectories(afterDir);
            for (var fm : pkg.fileMapping()) {
                Path stagingSubDir = stagingDir.resolve(fm.targetDir());
                Path afterSubDir = afterDir.resolve(fm.targetDir());
                if (Files.isDirectory(stagingSubDir)) {
                    copyDirectory(stagingSubDir, afterSubDir);
                }
            }

            // 3. Staging dosyalarını live dizine kopyala (live'ı güncelle)
            for (var fm : pkg.fileMapping()) {
                Path liveSubDir = externalDir.resolve(fm.targetDir());
                Path stagingSubDir = stagingDir.resolve(fm.targetDir());
                if (Files.isDirectory(stagingSubDir)) {
                    cleanDirectory(liveSubDir);
                    Files.createDirectories(liveSubDir);
                    copyDirectory(stagingSubDir, liveSubDir);
                }
            }

            // 4. Version'ı applied olarak güncelle
            AssetVersion appliedVersion = preview.version().asApplied();
            List<AssetVersion> versions = loadVersions();
            versions.removeIf(v -> v.id().equals(versionId));
            versions.addFirst(appliedVersion);
            saveVersions(versions);

            // 4. Staging'i temizle
            cleanDirectory(stagingDir);
            pendingPreviews.remove(packageId);

            // 5. Asset'leri yeniden yükle (Schematron derleme, XSD cache vb.)
            assetRegistry.reload();

            log.info("Pending version onaylandı ve live'a uygulandı: {} — {}", versionId, packageId);
            return appliedVersion;

        } finally {
            versionLock.unlock();
        }
    }

    @Override
    public void rejectPending(String packageId) throws IOException {
        requireExternalPath();
        versionLock.lock();
        try {
            SyncPreview preview = pendingPreviews.get(packageId);
            if (preview == null) {
                throw new IllegalStateException("Bu paket için pending staging bulunamadı: " + packageId);
            }

            String versionId = preview.version().id();

            // Version'ı rejected olarak kaydet
            AssetVersion rejectedVersion = preview.version().asRejected();
            List<AssetVersion> versions = loadVersions();
            versions.removeIf(v -> v.id().equals(versionId));
            versions.addFirst(rejectedVersion);
            saveVersions(versions);

            // Staging'i temizle
            cleanDirectory(getStagingDir(packageId));
            pendingPreviews.remove(packageId);

            log.info("Pending version reddedildi: {} — {}", versionId, packageId);

        } finally {
            versionLock.unlock();
        }
    }

    // ── History ──────────────────────────────────────────────────────

    @Override
    public List<AssetVersion> listVersions() {
        return loadVersions();
    }

    @Override
    public List<AssetVersion> listVersions(String packageId) {
        return loadVersions().stream()
                .filter(v -> v.packageId().equals(packageId))
                .toList();
    }

    @Override
    public List<FileDiffSummary> getVersionDiff(String versionId) throws IOException {
        findVersion(versionId);
        Path snapshotDir = getSnapshotDir(versionId);
        Path beforeDir = snapshotDir.resolve("_before");
        Path afterDir = snapshotDir.resolve("_after");

        if (!Files.isDirectory(afterDir)) {
            return List.of();
        }

        return diffService.computeDirectoryDiff(
                Files.isDirectory(beforeDir) ? beforeDir : Path.of("/dev/null"),
                afterDir);
    }

    @Override
    public FileDiffDetail getFileDiff(String versionId, String filePath) throws IOException {
        findVersion(versionId);
        Path snapshotDir = getSnapshotDir(versionId);
        Path beforeDir = snapshotDir.resolve("_before");
        Path afterDir = snapshotDir.resolve("_after");

        Path newFile = afterDir.resolve(filePath);
        Path oldFile = Files.isDirectory(beforeDir) ? beforeDir.resolve(filePath) : null;

        return diffService.computeFileDiff(oldFile, newFile, filePath);
    }

    // ── Pending Query ───────────────────────────────────────────────

    @Override
    public SyncPreview getPendingPreview(String packageId) {
        return pendingPreviews.get(packageId);
    }

    @Override
    public List<SyncPreview> getAllPendingPreviews() {
        return new ArrayList<>(pendingPreviews.values());
    }

    @Override
    public FileDiffDetail getPendingFileDiff(String packageId, String filePath) throws IOException {
        SyncPreview preview = pendingPreviews.get(packageId);
        if (preview == null) {
            throw new IllegalStateException("Bu paket için pending staging bulunamadı: " + packageId);
        }

        Path stagingDir = getStagingDir(packageId);
        Path newFile = stagingDir.resolve(filePath);

        // Live'daki önceki versiyon: bir önceki snapshot'tan bul
        // Eğer önceki snapshot yoksa live dizinden al
        GibPackageDefinition pkg = findPackageDefinition(packageId);
        Path externalDir = Path.of(externalPath);

        Path oldFile = null;
        for (var fm : pkg.fileMapping()) {
            Path candidateOld = externalDir.resolve(fm.targetDir()).resolve(filePath);
            if (Files.isRegularFile(candidateOld)) {
                oldFile = candidateOld;
                break;
            }
            // filePath already includes targetDir prefix
            Path candidateOldDirect = externalDir.resolve(filePath);
            if (Files.isRegularFile(candidateOldDirect)) {
                oldFile = candidateOldDirect;
                break;
            }
        }

        return diffService.computeFileDiff(oldFile, newFile, filePath);
    }

    // ── Internal: Version Management ────────────────────────────────

    private SyncPreview buildPreview(String packageId, String displayName, long durationMs) throws IOException {
        Path stagingDir = getStagingDir(packageId);
        GibPackageDefinition pkg = findPackageDefinition(packageId);
        Path externalDir = Path.of(externalPath);

        // Her file mapping için live vs staging diff hesapla
        var allDiffs = new ArrayList<FileDiffSummary>();
        var allWarnings = new ArrayList<SuppressionWarning>();

        for (var fm : pkg.fileMapping()) {
            Path liveSubDir = externalDir.resolve(fm.targetDir());
            Path stagingSubDir = stagingDir.resolve(fm.targetDir());

            if (Files.isDirectory(liveSubDir) || Files.isDirectory(stagingSubDir)) {
                List<FileDiffSummary> diffs = diffService.computeDirectoryDiff(liveSubDir, stagingSubDir);
                // Prefix paths with targetDir
                for (var diff : diffs) {
                    allDiffs.add(new FileDiffSummary(
                            fm.targetDir() + diff.path(),
                            diff.status(), diff.oldSize(), diff.newSize()));
                }

                // Schematron dosyaları için suppression impact analizi
                List<SuppressionWarning> warnings = suppressionAnalyzer
                        .analyzeImpact(liveSubDir, stagingSubDir, diffs);
                allWarnings.addAll(warnings);
            }
        }

        // Dosya özeti
        int added = (int) allDiffs.stream().filter(d -> d.status() == FileChangeStatus.ADDED).count();
        int removed = (int) allDiffs.stream().filter(d -> d.status() == FileChangeStatus.REMOVED).count();
        int modified = (int) allDiffs.stream().filter(d -> d.status() == FileChangeStatus.MODIFIED).count();
        int unchanged = (int) allDiffs.stream().filter(d -> d.status() == FileChangeStatus.UNCHANGED).count();

        String versionId = generateNextVersionId();
        AssetVersion version = AssetVersion.pending(versionId, packageId, displayName,
                new FilesSummary(added, removed, modified, unchanged), durationMs);

        return new SyncPreview(packageId, version, allDiffs, allWarnings);
    }

    private String generateNextVersionId() {
        return DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss")
                .format(java.time.LocalDateTime.now());
    }

    @SuppressWarnings("unchecked")
    private List<AssetVersion> loadVersions() {
        try {
            Path versionsFile = getHistoryDir().resolve(VERSIONS_FILE);
            if (!Files.isRegularFile(versionsFile)) {
                return new ArrayList<>();
            }
            return objectMapper.readValue(versionsFile.toFile(),
                    new TypeReference<List<AssetVersion>>() {});
        } catch (IOException e) {
            log.warn("versions.json okunamadı: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    private void saveVersions(List<AssetVersion> versions) throws IOException {
        Path versionsFile = getHistoryDir().resolve(VERSIONS_FILE);
        Files.createDirectories(versionsFile.getParent());
        objectMapper.writeValue(versionsFile.toFile(), versions);
    }

    private AssetVersion findVersion(String versionId) {
        return loadVersions().stream()
                .filter(v -> v.id().equals(versionId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Versiyon bulunamadı: " + versionId));
    }

    private GibPackageDefinition findPackageDefinition(String packageId) {
        return gibSyncService.getAvailablePackages().stream()
                .filter(p -> p.id().equals(packageId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Bilinmeyen paket: " + packageId));
    }

    // ── Internal: Path Utilities ────────────────────────────────────

    private Path getHistoryDir() {
        return Path.of(externalPath).resolve(HISTORY_DIR);
    }

    private Path getSnapshotDir(String versionId) {
        return getHistoryDir().resolve(SNAPSHOTS_DIR).resolve(versionId);
    }

    private Path getStagingDir(String packageId) {
        return Path.of(externalPath).resolve(STAGING_DIR).resolve(packageId);
    }

    private void requireExternalPath() throws IOException {
        if (externalPath == null || externalPath.isBlank()) {
            throw new IOException("Asset dizini yapılandırılmamış. " +
                    "xslt.assets.external-path ayarlayın (env: XSLT_ASSETS_EXTERNAL_PATH).");
        }
    }

    // ── Internal: File Utilities ────────────────────────────────────

    private void copyDirectory(Path source, Path target) throws IOException {
        if (!Files.isDirectory(source)) return;
        Files.createDirectories(target);
        try (Stream<Path> stream = Files.walk(source)) {
            for (Path sourcePath : stream.toList()) {
                Path targetPath = target.resolve(source.relativize(sourcePath));
                if (Files.isDirectory(sourcePath)) {
                    Files.createDirectories(targetPath);
                } else {
                    Files.createDirectories(targetPath.getParent());
                    Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    private void cleanDirectory(Path dir) {
        if (dir == null || !Files.isDirectory(dir)) return;
        try (Stream<Path> stream = Files.walk(dir)) {
            stream.sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException e) {
                            log.debug("Dosya silinemedi: {}", path);
                        }
                    });
        } catch (IOException e) {
            log.warn("Dizin temizlenemedi: {} — {}", dir, e.getMessage());
        }
    }
}

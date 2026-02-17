package io.mersel.services.xslt.infrastructure;

import io.mersel.services.xslt.application.interfaces.IGibPackageSyncService;
import io.mersel.services.xslt.application.models.GibPackageDefinition;
import io.mersel.services.xslt.application.models.GibPackageDefinition.FileExtraction;
import io.mersel.services.xslt.application.models.PackageSyncResult;
import io.mersel.services.xslt.infrastructure.config.GibSyncProperties;
import io.mersel.services.xslt.infrastructure.diagnostics.XsltMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.*;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipFile;

/**
 * GİB resmi web sitesinden XML paketlerini indiren ve asset dizinine yerleştiren servis.
 * <p>
 * ZIP dosyasını indirir, geçici dizine çıkartır, dosya eşleştirme kurallarına göre
 * hedef dizine kopyalar ve asset registry'yi yeniden yükler.
 * <p>
 * İndirme başarısız olursa mevcut asset'ler korunur (atomic swap).
 */
@Service
public class GibPackageSyncService implements IGibPackageSyncService {

    private static final Logger log = LoggerFactory.getLogger(GibPackageSyncService.class);

    /** ZIP dosyası magic bytes (PK header) */
    private static final byte[] ZIP_MAGIC = { 0x50, 0x4B, 0x03, 0x04 };

    private final GibSyncProperties properties;
    private final AssetRegistry assetRegistry;
    private final XsltMetrics xsltMetrics;
    private final HttpClient httpClient;

    @Value("${xslt.assets.external-path:}")
    private String externalAssetPath;

    /**
     * GİB paket tanımları — sabit URL'ler ile.
     */
    private static final List<GibPackageDefinition> PACKAGE_DEFINITIONS = List.of(
            new GibPackageDefinition(
                    "efatura",
                    "UBL-TR Şematron Paketi",
                    "https://ebelge.gib.gov.tr/dosyalar/kilavuzlar/e-FaturaPaketi.zip",
                    List.of(
                            new FileExtraction("**/schematron/*.xml", "validator/ubl-tr-package/schematron/")
                    ),
                    "GİB UBL-TR Schematron paket dosyaları"
            ),
            new GibPackageDefinition(
                    "ubltr-xsd",
                    "UBL-TR XSD Paketi",
                    "https://ebelge.gib.gov.tr/dosyalar/kilavuzlar/UBL-TR1.2.1_Paketi.zip",
                    List.of(
                            new FileExtraction("**/xsdrt/common/*.xsd", "validator/ubl-tr-package/schema/common/"),
                            new FileExtraction("**/xsdrt/maindoc/*.xsd", "validator/ubl-tr-package/schema/maindoc/")
                    ),
                    "UBL-TR 1.2.1 XML Schema (XSD) dosyaları"
            ),
            new GibPackageDefinition(
                    "earsiv",
                    "e-Arşiv Paketi",
                    "https://ebelge.gib.gov.tr/dosyalar/kilavuzlar/earsiv_paket_v1.1_6.zip",
                    List.of(
                            new FileExtraction("*.xsl", "validator/earchive/schematron/"),
                            new FileExtraction("*.xsd", "validator/earchive/schema/")
                    ),
                    "GİB e-Arşiv Schematron ve XSD dosyaları"
            ),
            new GibPackageDefinition(
                    "edefter",
                    "e-Defter Paketi",
                    "https://www.edefter.gov.tr/dosyalar/paketler/e-Defter_Paketi.zip",
                    List.of(
                            new FileExtraction("**/sch/*.sch", "validator/eledger/schematron/"),
                            new FileExtraction("**/xsd/*.xsd", "validator/eledger/schema/"),
                            new FileExtraction("**/xsd/**/*.xsd", "validator/eledger/schema/")
                    ),
                    "GİB e-Defter ISO Schematron (.sch) ve XSD şema dosyaları"
            )
    );

    @org.springframework.beans.factory.annotation.Autowired
    public GibPackageSyncService(GibSyncProperties properties, AssetRegistry assetRegistry, XsltMetrics xsltMetrics) {
        this(properties, assetRegistry, xsltMetrics, HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(properties.getConnectTimeoutMs()))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .executor(java.util.concurrent.Executors.newFixedThreadPool(4))
                .build());
    }

    /**
     * Constructor with injectable HttpClient for testing.
     */
    GibPackageSyncService(GibSyncProperties properties, AssetRegistry assetRegistry,
                          XsltMetrics xsltMetrics, HttpClient httpClient) {
        this.properties = properties;
        this.assetRegistry = assetRegistry;
        this.xsltMetrics = xsltMetrics;
        this.httpClient = httpClient;
    }

    @PreDestroy
    void shutdown() {
        if (httpClient != null) {
            httpClient.close(); // Java 21+
            log.debug("HttpClient kapatıldı");
        }
    }

    @Override
    public boolean isEnabled() {
        return properties.isEnabled();
    }

    @Override
    public String getCurrentAssetSource() {
        if (externalAssetPath != null && !externalAssetPath.isBlank()) {
            return "external";
        }
        return "bundled";
    }

    @Override
    public List<GibPackageDefinition> getAvailablePackages() {
        return PACKAGE_DEFINITIONS;
    }

    @Override
    public List<PackageSyncResult> syncAll() {
        if (!isEnabled()) {
            log.info("GİB paket sync devre dışı — atlanıyor");
            return List.of();
        }
        log.info("Tüm GİB paketleri sync ediliyor...");
        var results = new ArrayList<PackageSyncResult>();
        for (var pkg : PACKAGE_DEFINITIONS) {
            results.add(doSyncPackage(pkg));
        }

        // Sync sonrası asset'leri yeniden yükle
        long reloadSuccessCount = results.stream().filter(PackageSyncResult::success).count();
        if (reloadSuccessCount > 0) {
            log.info("Sync tamamlandı, asset'ler yeniden yükleniyor...");
            assetRegistry.reload();
        }

        log.info("GİB paket sync tamamlandı: {}/{} başarılı", reloadSuccessCount, results.size());
        return results;
    }

    @Override
    public PackageSyncResult syncPackage(String packageId) {
        if (!isEnabled()) {
            return PackageSyncResult.failure(packageId, "Sync devre dışı", 0,
                    "GİB paket sync devre dışı (validation-assets.gib.sync.enabled=false)");
        }
        var pkg = PACKAGE_DEFINITIONS.stream()
                .filter(p -> p.id().equals(packageId))
                .findFirst()
                .orElse(null);

        if (pkg == null) {
            return PackageSyncResult.failure(packageId, "Bilinmiyor", 0,
                    "Geçersiz paket kimliği: " + packageId + ". Geçerli değerler: " +
                            PACKAGE_DEFINITIONS.stream().map(GibPackageDefinition::id).toList());
        }

        var result = doSyncPackage(pkg);

        if (result.success()) {
            log.info("Paket sync tamamlandı, asset'ler yeniden yükleniyor...");
            assetRegistry.reload();
        }

        return result;
    }

    @Override
    public PackageSyncResult syncPackageToTarget(String packageId, java.nio.file.Path targetDir) {
        if (!isEnabled()) {
            return PackageSyncResult.failure(packageId, "Sync devre dışı", 0,
                    "GİB paket sync devre dışı (validation-assets.gib.sync.enabled=false)");
        }
        var pkg = PACKAGE_DEFINITIONS.stream()
                .filter(p -> p.id().equals(packageId))
                .findFirst()
                .orElse(null);

        if (pkg == null) {
            return PackageSyncResult.failure(packageId, "Bilinmiyor", 0,
                    "Geçersiz paket kimliği: " + packageId + ". Geçerli değerler: " +
                            PACKAGE_DEFINITIONS.stream().map(GibPackageDefinition::id).toList());
        }

        return doSyncPackageToTarget(pkg, targetDir);
    }

    /**
     * Tek bir paketi indir, çıkart ve belirtilen hedef dizine yerleştir.
     * Live asset'leri değiştirmez, reload tetiklemez.
     */
    private PackageSyncResult doSyncPackageToTarget(GibPackageDefinition pkg, Path targetDir) {
        long startTime = System.currentTimeMillis();
        log.info("  Staging sync: {} — {} → {}", pkg.id(), pkg.downloadUrl(), targetDir);

        try {
            byte[] zipBytes = downloadZip(pkg.downloadUrl());
            if (!isValidZip(zipBytes)) {
                return PackageSyncResult.failure(pkg.id(), pkg.displayName(),
                        System.currentTimeMillis() - startTime,
                        "İndirilen dosya geçerli bir ZIP formatında değil");
            }

            List<String> extractedFiles = extractAndMap(zipBytes, pkg.fileMapping(), targetDir);

            long elapsed = System.currentTimeMillis() - startTime;
            log.info("  {} staging sync tamamlandı: {} dosya, {}ms", pkg.id(), extractedFiles.size(), elapsed);

            xsltMetrics.recordSync(true, elapsed);
            return PackageSyncResult.success(pkg.id(), pkg.displayName(),
                    extractedFiles.size(), extractedFiles, elapsed);

        } catch (java.nio.file.AccessDeniedException e) {
            long elapsed = System.currentTimeMillis() - startTime;
            String msg = "Yazma izni yok: " + e.getFile();
            log.error("  {} staging sync başarısız (AccessDenied): {}", pkg.id(), msg);
            xsltMetrics.recordSync(false, elapsed);
            return PackageSyncResult.failure(pkg.id(), pkg.displayName(), elapsed, msg);
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - startTime;
            String msg = e.getClass().getSimpleName() + ": " + e.getMessage();
            log.error("  {} staging sync başarısız: {}", pkg.id(), msg);
            xsltMetrics.recordSync(false, elapsed);
            return PackageSyncResult.failure(pkg.id(), pkg.displayName(), elapsed, msg);
        }
    }

    /**
     * Tek bir paketi indir, çıkart ve hedef dizine yerleştir.
     */
    private PackageSyncResult doSyncPackage(GibPackageDefinition pkg) {
        long startTime = System.currentTimeMillis();
        log.info("  Sync: {} — {}", pkg.id(), pkg.downloadUrl());

        try {
            // 1. ZIP indir
            byte[] zipBytes = downloadZip(pkg.downloadUrl());

            // 2. ZIP magic bytes kontrolü
            if (!isValidZip(zipBytes)) {
                return PackageSyncResult.failure(pkg.id(), pkg.displayName(),
                        System.currentTimeMillis() - startTime,
                        "İndirilen dosya geçerli bir ZIP formatında değil");
            }

            // 3. Hedef dizin belirle
            Path targetBase = resolveTargetPath();

            // 4. Geçici dizine çıkart ve eşleştir
            List<String> extractedFiles = extractAndMap(zipBytes, pkg.fileMapping(), targetBase);

            long elapsed = System.currentTimeMillis() - startTime;
            log.info("  {} sync tamamlandı: {} dosya, {}ms", pkg.id(), extractedFiles.size(), elapsed);

            xsltMetrics.recordSync(true, elapsed);
            return PackageSyncResult.success(pkg.id(), pkg.displayName(),
                    extractedFiles.size(), extractedFiles, elapsed);

        } catch (java.nio.file.AccessDeniedException e) {
            long elapsed = System.currentTimeMillis() - startTime;
            String msg = "Yazma izni yok: " + e.getFile()
                    + " — Docker volume mount izinlerini kontrol edin. "
                    + "Container non-root kullanıcı (appuser) ile çalışıyor, "
                    + "hedef dizinin yazılabilir olması gerekir.";
            log.error("  {} sync başarısız (AccessDenied): {}", pkg.id(), msg);
            xsltMetrics.recordSync(false, elapsed);
            return PackageSyncResult.failure(pkg.id(), pkg.displayName(), elapsed, msg);
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - startTime;
            String msg = e.getClass().getSimpleName() + ": " + e.getMessage();
            log.error("  {} sync başarısız: {}", pkg.id(), msg);
            xsltMetrics.recordSync(false, elapsed);
            return PackageSyncResult.failure(pkg.id(), pkg.displayName(), elapsed, msg);
        }
    }

    /**
     * ZIP dosyasını HTTP üzerinden indirir.
     * baseUrlOverride ayarlanmışsa URL'nin host kısmı değiştirilir (test için).
     */
    private byte[] downloadZip(String url) throws IOException, InterruptedException {
        String effectiveUrl = url;
        if (properties.getBaseUrlOverride() != null && !properties.getBaseUrlOverride().isBlank()) {
            try {
                URI orig = URI.create(url);
                String overrideBase = properties.getBaseUrlOverride().replaceAll("/$", "");
                effectiveUrl = overrideBase + (orig.getRawPath() != null ? orig.getRawPath() : "/")
                        + (orig.getRawQuery() != null ? "?" + orig.getRawQuery() : "");
            } catch (Exception e) {
                log.warn("Base URL override uygulanamadı: {}", e.getMessage());
            }
        }
        var request = HttpRequest.newBuilder()
                .uri(URI.create(effectiveUrl))
                .timeout(Duration.ofMillis(properties.getReadTimeoutMs()))
                .GET()
                .build();

        var response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());

        if (response.statusCode() != 200) {
            throw new IOException("HTTP " + response.statusCode() + " — URL: " + url);
        }

        // İndirme boyut limiti — 200 MB
        long maxDownloadSize = 200L * 1024 * 1024;
        if (response.body().length > maxDownloadSize) {
            throw new IOException("İndirilen dosya boyutu sınırı aşıyor: "
                    + (response.body().length / (1024 * 1024)) + " MB (max: 200 MB)");
        }

        log.debug("  İndirme tamamlandı: {} bytes", response.body().length);
        return response.body();
    }

    /**
     * ZIP magic bytes kontrolü.
     */
    private boolean isValidZip(byte[] data) {
        if (data == null || data.length < 4) {
            return false;
        }
        return data[0] == ZIP_MAGIC[0]
                && data[1] == ZIP_MAGIC[1]
                && data[2] == ZIP_MAGIC[2]
                && data[3] == ZIP_MAGIC[3];
    }

    /**
     * Hedef asset dizinini belirler.
     * <p>
     * Öncelik sırası:
     * <ol>
     *   <li>{@code validation-assets.gib.sync.target-path} (yapılandırmada belirtilmişse)</li>
     *   <li>{@code xslt.assets.external-path} (external asset dizini)</li>
     *   <li>Sistem temp dizininde geçici bir klasör</li>
     * </ol>
     */
    private Path resolveTargetPath() throws IOException {
        // 1. Sync-specific target path
        if (properties.getTargetPath() != null && !properties.getTargetPath().isBlank()) {
            Path target = Path.of(properties.getTargetPath());
            Files.createDirectories(target);
            return target;
        }

        // 2. External asset path
        if (externalAssetPath != null && !externalAssetPath.isBlank()) {
            Path target = Path.of(externalAssetPath);
            Files.createDirectories(target);
            return target;
        }

        // 3. Fallback — external path belirtilmediğinde sync yapılamaz
        throw new IOException("Hedef dizin belirlenemedi. " +
                "VALIDATION_ASSETS_GIB_SYNC_PATH veya XSLT_ASSETS_EXTERNAL_PATH ayarlayın.");
    }

    /**
     * ZIP içeriğini çıkartır ve eşleştirme kurallarına göre hedef dizine kopyalar.
     * <p>
     * GİB ZIP dosyaları Türkçe karakterli dosya/klasör isimleri içerir (ör: "İrsaliye", "Şema").
     * Bu isimler genellikle CP437 veya Windows-1254 encoding ile yazılmıştır.
     * {@link ZipFile}, charset parametresi ile bu encoding'i doğru okuyabilir;
     * {@code ZipInputStream} ise yalnızca UTF-8 destekler ve "malformed input" hatası verir.
     */
    private List<String> extractAndMap(byte[] zipBytes, List<FileExtraction> fileMappings, Path targetBase)
            throws IOException {

        var extractedFiles = new ArrayList<String>();

        // Geçici dizine çıkart
        Path tempDir = Files.createTempDirectory("gib-sync-");

        try {
            // ZIP byte'larını geçici dosyaya yaz (ZipFile dosya sistemi gerektirir)
            Path tempZip = tempDir.resolve("download.zip");
            Files.write(tempZip, zipBytes);

            // ZipFile ile charset belirterek aç — Türkçe dosya isimlerini doğru okur
            try (var zipFile = new ZipFile(tempZip.toFile(), java.nio.charset.Charset.forName("CP437"))) {
                var entries = zipFile.entries();
                while (entries.hasMoreElements()) {
                    var entry = entries.nextElement();
                    if (entry.isDirectory()) continue;

                    String entryName = entry.getName();
                    Path entryPath = tempDir.resolve(entryName).normalize();

                    // Path traversal koruması
                    if (!entryPath.startsWith(tempDir)) {
                        log.warn("  ZIP entry path traversal engellendi: {}", entryName);
                        continue;
                    }

                    Files.createDirectories(entryPath.getParent());
                    try (var is = zipFile.getInputStream(entry)) {
                        Files.copy(is, entryPath, StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            }

            // Geçici ZIP dosyasını sil
            Files.deleteIfExists(tempZip);

            // Hedef dizinleri temizle — her benzersiz dizin sadece bir kez silinir
            var cleanedDirs = new java.util.HashSet<Path>();
            for (var mapping : fileMappings) {
                Path targetDir = targetBase.resolve(mapping.targetDir());
                if (cleanedDirs.add(targetDir) && Files.isDirectory(targetDir)) {
                    log.debug("  Hedef dizin temizleniyor: {}", targetDir);
                    deleteRecursively(targetDir);
                }
                Files.createDirectories(targetDir);
            }

            // Eşleştirme kurallarına göre dosyaları hedef dizine kopyala
            for (var mapping : fileMappings) {
                Path targetDir = targetBase.resolve(mapping.targetDir());

                List<Path> matchedFiles = findMatchingFiles(tempDir, mapping.zipPathPattern());

                // Glob pattern'den "anchor" dizin bul — alt klasör yapısını korumak için
                String anchorDir = findAnchorDirectory(mapping.zipPathPattern());

                for (Path matchedFile : matchedFiles) {
                    // Alt klasör yapısını koru: anchor dizinden sonraki göreceli yolu hesapla
                    Path relativeSubPath = extractRelativePath(tempDir.relativize(matchedFile), anchorDir);
                    Path target = targetDir.resolve(relativeSubPath);
                    Files.createDirectories(target.getParent());
                    Files.copy(matchedFile, target, StandardCopyOption.REPLACE_EXISTING);
                    String relativePath = mapping.targetDir() + relativeSubPath.toString().replace('\\', '/');
                    extractedFiles.add(relativePath);
                    log.debug("  Kopyalandı: {} → {}", matchedFile, target);
                }
            }

        } finally {
            // Geçici dizini temizle
            deleteRecursively(tempDir);
        }

        return extractedFiles;
    }

    /**
     * Glob pattern'den "anchor" dizin adını bulur.
     * <p>
     * Anchor, pattern'deki son sabit (wildcard içermeyen) dizin segmentidir.
     * Örn: {@code *&#47;xsd/**&#47;*.xsd} → {@code "xsd"},
     *      {@code *&#47;sch/*.sch} → {@code "sch"},
     *      {@code *.xsl} → {@code null} (anchor yok).
     * <p>
     * Anchor dizin, dosya kopyalarken alt klasör yapısını korumak için kullanılır:
     * anchor'dan sonraki göreceli yol hedef dizine aynen aktarılır.
     */
    private String findAnchorDirectory(String pattern) {
        String[] parts = pattern.split("/");
        for (int i = parts.length - 1; i >= 0; i--) {
            if (!parts[i].contains("*")) {
                return parts[i];
            }
        }
        return null;
    }

    /**
     * Dosyanın göreceli yolundan, anchor dizin altındaki alt yolu çıkarır.
     * <p>
     * Örn: {@code relativeFromTemp} = {@code e-Defter_Paketi/xsd/sub/file.xsd},
     *      {@code anchorDir} = {@code "xsd"} → döner: {@code sub/file.xsd}.
     * <p>
     * Anchor bulunamazsa veya null ise sadece dosya adını döndürür (geriye uyumluluk).
     */
    private Path extractRelativePath(Path relativeFromTemp, String anchorDir) {
        if (anchorDir != null) {
            for (int i = 0; i < relativeFromTemp.getNameCount(); i++) {
                if (relativeFromTemp.getName(i).toString().equals(anchorDir)) {
                    if (i + 1 < relativeFromTemp.getNameCount()) {
                        return relativeFromTemp.subpath(i + 1, relativeFromTemp.getNameCount());
                    }
                }
            }
        }
        // Fallback: sadece dosya adı
        return relativeFromTemp.getFileName();
    }

    /**
     * Glob pattern'e göre dosyaları bulur.
     * <p>
     * Pattern formatı: "dizin/*.uzanti", "*&#47;**&#47;dizin/*.uzanti" veya "*.uzanti"
     */
    private List<Path> findMatchingFiles(Path baseDir, String pattern) throws IOException {
        var matchedFiles = new ArrayList<Path>();

        // Pattern'i PathMatcher'a uygun hale getir
        String globPattern = "glob:" + pattern;
        PathMatcher matcher = FileSystems.getDefault().getPathMatcher(globPattern);

        try (var stream = Files.walk(baseDir)) {
            stream.filter(Files::isRegularFile)
                    .filter(path -> !path.getFileName().toString().equals("download.zip"))
                    .filter(path -> {
                        Path relativePath = baseDir.relativize(path);
                        boolean matches = matcher.matches(relativePath);
                        if (log.isDebugEnabled()) {
                            log.debug("  Glob eşleştirme: pattern='{}' path='{}' → {}",
                                    pattern, relativePath, matches);
                        }
                        return matches;
                    })
                    .forEach(matchedFiles::add);
        }

        log.info("  Pattern '{}' ile {} dosya eşleşti", pattern, matchedFiles.size());

        return matchedFiles;
    }

    /**
     * Dizini ve içeriğini özyinelemeli siler.
     */
    private void deleteRecursively(Path dir) {
        try (var stream = Files.walk(dir)) {
            stream.sorted(java.util.Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException e) {
                            log.debug("Geçici dosya silinemedi: {}", path);
                        }
                    });
        } catch (IOException e) {
            log.debug("Geçici dizin temizlenemedi: {}", dir);
        }
    }
}

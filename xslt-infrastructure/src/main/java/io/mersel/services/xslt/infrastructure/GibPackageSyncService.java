package io.mersel.services.xslt.infrastructure;

import com.github.junrar.Archive;
import com.github.junrar.exception.RarException;
import io.mersel.services.xslt.application.interfaces.IGibPackageSyncService;
import io.mersel.services.xslt.application.models.GibPackageDefinition;
import io.mersel.services.xslt.application.models.GibPackageDefinition.FileExtraction;
import io.mersel.services.xslt.application.models.GibPackageDefinition.FileExtraction.ExtractionMode;
import io.mersel.services.xslt.application.models.PackageSyncResult;
import io.mersel.services.xslt.infrastructure.config.GibSyncProperties;
import io.mersel.services.xslt.infrastructure.diagnostics.XsltMetrics;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.*;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipFile;

/**
 * GİB resmi web sitesinden XML paketlerini indiren ve asset dizinine yerleştiren servis.
 * <p>
 * ZIP veya RAR dosyasını indirir, geçici dizine çıkartır, dosya eşleştirme kurallarına göre
 * hedef dizine kopyalar ve asset registry'yi yeniden yükler.
 * <p>
 * İndirme, arşiv açma veya zorunlu dosya eşleştirme başarısız olursa
 * live hedefe dokunulmaz ve mevcut asset'ler korunur.
 */
@Service
public class GibPackageSyncService implements IGibPackageSyncService {

    private static final Logger log = LoggerFactory.getLogger(GibPackageSyncService.class);

    /** ZIP dosyası magic bytes (PK header) */
    private static final byte[] ZIP_MAGIC = { 0x50, 0x4B, 0x03, 0x04 };
    /** RAR 4.x ve 5.x dosyalarının ortak magic bytes başlangıcı. */
    private static final byte[] RAR_MAGIC_PREFIX = { 0x52, 0x61, 0x72, 0x21, 0x1A, 0x07 };
    private static final int MAX_ARCHIVE_ENTRY_COUNT = 10_000;
    private static final long MAX_ARCHIVE_ENTRY_SIZE = 200L * 1024 * 1024;
    private static final long MAX_TOTAL_EXTRACTED_SIZE = 500L * 1024 * 1024;

    private final GibSyncProperties properties;
    private final AssetRegistry assetRegistry;
    private final XsltMetrics xsltMetrics;
    private final EmbeddedXsltExtractor embeddedXsltExtractor;
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
                    "https://ebelge.gib.gov.tr/dosyalar/kilavuzlar/earsiv_paket_v1.1_8.zip",
                    List.of(
                            new FileExtraction("*.xsl", "validator/earchive/schematron/"),
                            new FileExtraction("*.xsd", "validator/earchive/schema/")
                    ),
                    "GİB e-Arşiv Schematron ve XSD dosyaları"
            ),
            new GibPackageDefinition(
                    "edoviz",
                    "e-Döviz ve Kıymetli Maden Paketi",
                    "https://ebelge.gib.gov.tr/dosyalar/e-doviz_ve_kiymetlimaden_alim-satim_paketi_v1.2.rar",
                    List.of(
                            new FileExtraction("alim.xslt", "default_transformers/", "eDovizAlim_Base.xslt"),
                            new FileExtraction("satim.xslt", "default_transformers/", "eDovizSatim_Base.xslt"),
                            new FileExtraction("eArsiv.xsd", "validator/earchive-edoviz/schema/"),
                            new FileExtraction("earsiv_schematron.xsl", "validator/earchive-edoviz/schematron/")
                    ),
                    "GİB e-Döviz/e-Kıymetli Maden görüntüleme, XSD ve Schematron dosyaları"
            ),
            new GibPackageDefinition(
                    "edekont",
                    "e-Dekont Paketi",
                    "https://ebelge.gib.gov.tr/dosyalar/eDekont_Paketi.rar",
                    List.of(
                            new FileExtraction("**/*.xml", "default_transformers/", "eDekont_Base.xslt",
                                    ExtractionMode.EMBEDDED_XSLT),
                            new FileExtraction("**/eArsiv.xsd", "validator/earchive-edekont/schema/")
                    ),
                    "GİB e-Dekont gömülü görüntüleme dosyası ve XSD"
            ),
            new GibPackageDefinition(
                    "egider-pusulasi",
                    "e-Gider Pusulası Paketi",
                    "https://ebelge.gib.gov.tr/dosyalar/kilavuzlar/e-Gider_Pusulasi_Paketi.rar",
                    List.of(
                            new FileExtraction("**/giderPusulasi.xslt", "default_transformers/", "eGiderPusulasi_Base.xslt"),
                            new FileExtraction("**/eArsiv.xsd", "validator/earchive-egider-pusulasi/schema/")
                    ),
                    "GİB e-Gider Pusulası görüntüleme dosyası ve XSD"
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
    public GibPackageSyncService(GibSyncProperties properties, AssetRegistry assetRegistry,
                                 XsltMetrics xsltMetrics, EmbeddedXsltExtractor embeddedXsltExtractor) {
        this(properties, assetRegistry, xsltMetrics, embeddedXsltExtractor, HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(properties.getConnectTimeoutMs()))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .executor(java.util.concurrent.Executors.newFixedThreadPool(4))
                .build());
    }

    /**
     * Constructor with injectable HttpClient for testing.
     */
    GibPackageSyncService(GibSyncProperties properties, AssetRegistry assetRegistry,
                          XsltMetrics xsltMetrics, EmbeddedXsltExtractor embeddedXsltExtractor,
                          HttpClient httpClient) {
        this.properties = properties;
        this.assetRegistry = assetRegistry;
        this.xsltMetrics = xsltMetrics;
        this.embeddedXsltExtractor = embeddedXsltExtractor;
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
            byte[] archiveBytes = downloadArchive(pkg.downloadUrl());
            ArchiveFormat archiveFormat = detectArchiveFormat(archiveBytes);
            if (archiveFormat == null) {
                return PackageSyncResult.failure(pkg.id(), pkg.displayName(),
                        System.currentTimeMillis() - startTime,
                        "İndirilen dosya geçerli bir ZIP veya RAR formatında değil");
            }

            List<String> extractedFiles = extractAndMap(
                    archiveBytes, archiveFormat, pkg.fileMapping(), targetDir);

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
            // 1. Hedefi doğrula
            Path targetBase = resolveTargetPath();

            // 2. Arşivi indir
            byte[] archiveBytes = downloadArchive(pkg.downloadUrl());

            // 3. Formatı magic bytes üzerinden doğrula
            ArchiveFormat archiveFormat = detectArchiveFormat(archiveBytes);
            if (archiveFormat == null) {
                return PackageSyncResult.failure(pkg.id(), pkg.displayName(),
                        System.currentTimeMillis() - startTime,
                        "İndirilen dosya geçerli bir ZIP veya RAR formatında değil");
            }

            // 4. Geçici dizine çıkart ve eşleştir
            List<String> extractedFiles = extractAndMap(
                    archiveBytes, archiveFormat, pkg.fileMapping(), targetBase);

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
     * Arşiv dosyasını HTTP üzerinden indirir.
     * baseUrlOverride ayarlanmışsa URL'nin host kısmı değiştirilir (test için).
     */
    private byte[] downloadArchive(String url) throws IOException, InterruptedException {
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
     * Arşiv formatını dosya uzantısına güvenmeden magic bytes üzerinden belirler.
     */
    private ArchiveFormat detectArchiveFormat(byte[] data) {
        if (startsWith(data, ZIP_MAGIC)) {
            return ArchiveFormat.ZIP;
        }
        if (startsWith(data, RAR_MAGIC_PREFIX)) {
            return ArchiveFormat.RAR;
        }
        return null;
    }

    private boolean startsWith(byte[] data, byte[] magic) {
        if (data == null || data.length < magic.length) {
            return false;
        }
        for (int i = 0; i < magic.length; i++) {
            if (data[i] != magic[i]) {
                return false;
            }
        }
        return true;
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
     * ZIP/RAR içeriğini çıkartır ve eşleştirme kurallarına göre hedef dizine kopyalar.
     * <p>
     * GİB ZIP dosyaları Türkçe karakterli dosya/klasör isimleri içerir (ör: "İrsaliye", "Şema").
     * Bu isimler genellikle CP437 veya Windows-1254 encoding ile yazılmıştır.
     * {@link ZipFile}, charset parametresi ile bu encoding'i doğru okuyabilir;
     * {@code ZipInputStream} ise yalnızca UTF-8 destekler ve "malformed input" hatası verir.
     */
    private List<String> extractAndMap(byte[] archiveBytes, ArchiveFormat archiveFormat,
                                       List<FileExtraction> fileMappings, Path targetBase)
            throws IOException, RarException {

        var extractedFiles = new ArrayList<String>();

        // Geçici dizine çıkart
        Path tempDir = Files.createTempDirectory("gib-sync-");

        try {
            Path tempArchive = tempDir.resolve(
                    archiveFormat == ArchiveFormat.ZIP ? "download.zip" : "download.rar");
            Files.write(tempArchive, archiveBytes);

            Path archiveContentDir = Files.createDirectory(tempDir.resolve("content"));
            if (archiveFormat == ArchiveFormat.ZIP) {
                extractZip(tempArchive, archiveContentDir);
            } else {
                extractRar(tempArchive, archiveContentDir);
            }

            Files.deleteIfExists(tempArchive);

            // Live hedefe dokunmadan önce bütün zorunlu eşleşmeleri ve gömülü
            // XSLT'leri doğrula. Paket yapısı değiştiğinde mevcut asset'ler korunur.
            var resolvedExtractions = new ArrayList<ResolvedExtraction>(fileMappings.size());
            for (var mapping : fileMappings) {
                List<Path> matchedFiles = findMatchingFiles(
                        archiveContentDir, mapping.zipPathPattern());
                if (matchedFiles.isEmpty()) {
                    throw new IOException("Arşivde zorunlu pattern ile eşleşen dosya bulunamadı: "
                            + mapping.zipPathPattern());
                }
                if (mapping.targetFileName() != null && matchedFiles.size() > 1
                        && mapping.extractionMode() == ExtractionMode.COPY) {
                    throw new IOException("Sabit hedef dosya adı birden fazla arşiv girdisiyle eşleşti: "
                            + mapping.zipPathPattern());
                }
                byte[] embeddedXslt = mapping.extractionMode() == ExtractionMode.EMBEDDED_XSLT
                        ? findEmbeddedXslt(mapping, matchedFiles)
                        : null;
                resolvedExtractions.add(new ResolvedExtraction(mapping, matchedFiles, embeddedXslt));
            }

            // Hedef dizinleri temizle — her benzersiz dizin sadece bir kez silinir
            var cleanedDirs = new java.util.HashSet<Path>();
            for (var resolved : resolvedExtractions) {
                FileExtraction mapping = resolved.mapping();
                Path targetDir = targetBase.resolve(mapping.targetDir());
                if (mapping.targetFileName() != null) {
                    // default_transformers gibi paylaşılan dizinlerde yalnızca bu paketin
                    // yönettiği dosyayı yenile; diğer belge şablonlarını koru.
                    Files.createDirectories(targetDir);
                } else {
                    if (cleanedDirs.add(targetDir) && Files.isDirectory(targetDir)) {
                        log.debug("  Hedef dizin temizleniyor: {}", targetDir);
                        deleteRecursively(targetDir);
                    }
                    Files.createDirectories(targetDir);
                }
            }

            // Eşleştirme kurallarına göre dosyaları hedef dizine kopyala
            for (var resolved : resolvedExtractions) {
                FileExtraction mapping = resolved.mapping();
                Path targetDir = targetBase.resolve(mapping.targetDir());

                if (mapping.extractionMode() == ExtractionMode.EMBEDDED_XSLT) {
                    writeEmbeddedXslt(mapping, resolved.embeddedXslt(),
                            targetDir, extractedFiles);
                    continue;
                }

                // Glob pattern'den "anchor" dizin bul — alt klasör yapısını korumak için
                String anchorDir = findAnchorDirectory(mapping.zipPathPattern());

                for (Path matchedFile : resolved.matchedFiles()) {
                    // Alt klasör yapısını koru: anchor dizinden sonraki göreceli yolu hesapla
                    Path relativeSubPath = mapping.targetFileName() != null
                            ? Path.of(mapping.targetFileName())
                            : extractRelativePath(archiveContentDir.relativize(matchedFile), anchorDir);
                    Path target = targetDir.resolve(relativeSubPath);
                    Files.createDirectories(target.getParent());
                    Files.copy(matchedFile, target, StandardCopyOption.REPLACE_EXISTING);
                    String relativePath = assetPath(mapping.targetDir(), relativeSubPath.toString());
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
     * Eşleşen UBL örneklerini sırayla tarar ve ilk geçerli gömülü XSLT'yi döndürür.
     * Bazı GİB e-Dekont örnekleri yalnızca placeholder içerdiği için tek bir dosya adına
     * bağlı kalınmaz.
     */
    private byte[] findEmbeddedXslt(FileExtraction mapping, List<Path> matchedFiles) throws IOException {
        if (mapping.targetFileName() == null || mapping.targetFileName().isBlank()) {
            throw new IOException("Gömülü XSLT çıkarma kuralı için sabit hedef dosya adı gerekli");
        }

        for (Path matchedFile : matchedFiles) {
            byte[] xslt = embeddedXsltExtractor.extract(Files.readAllBytes(matchedFile));
            if (xslt == null || xslt.length == 0) {
                continue;
            }
            log.debug("  Geçerli gömülü XSLT bulundu: {}", matchedFile);
            return xslt;
        }

        throw new IOException("Pattern ile eşleşen dosyalarda geçerli gömülü XSLT bulunamadı: "
                + mapping.zipPathPattern());
    }

    private void writeEmbeddedXslt(FileExtraction mapping, byte[] xslt,
                                   Path targetDir, List<String> extractedFiles) throws IOException {
        Path normalizedTargetDir = targetDir.normalize();
        Path target = normalizedTargetDir.resolve(mapping.targetFileName()).normalize();
        if (!target.startsWith(normalizedTargetDir)) {
            throw new IOException("Gömülü XSLT hedef yolu geçersiz: " + mapping.targetFileName());
        }

        Files.createDirectories(target.getParent());
        Files.write(target, xslt, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        String relativePath = assetPath(mapping.targetDir(), mapping.targetFileName());
        extractedFiles.add(relativePath);
        log.debug("  Gömülü XSLT yazıldı: {}", target);
    }

    private void extractZip(Path archiveFile, Path tempDir) throws IOException {
        // ZipFile ile charset belirterek aç — Türkçe dosya isimlerini doğru okur
        try (var zipFile = new ZipFile(archiveFile.toFile(), java.nio.charset.Charset.forName("CP437"))) {
            var entries = zipFile.entries();
            int entryCount = 0;
            long totalExtractedSize = 0;
            while (entries.hasMoreElements()) {
                var entry = entries.nextElement();
                if (entry.isDirectory()) continue;

                String entryName = entry.getName();
                entryCount++;
                validateArchiveEntry(entryName, entryCount, entry.getSize(), totalExtractedSize);
                Path entryPath = safeArchiveEntryPath(tempDir, entryName, "ZIP");
                if (entryPath == null) continue;

                Files.createDirectories(entryPath.getParent());
                try (var input = zipFile.getInputStream(entry);
                     var fileOutput = Files.newOutputStream(entryPath,
                             StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                     var output = new LimitedOutputStream(
                             fileOutput, entryName,
                             Math.min(MAX_ARCHIVE_ENTRY_SIZE,
                                     MAX_TOTAL_EXTRACTED_SIZE - totalExtractedSize))) {
                    input.transferTo(output);
                    totalExtractedSize += output.getWrittenBytes();
                }
            }
        }
    }

    private void extractRar(Path archiveFile, Path tempDir) throws IOException, RarException {
        try (var archive = new Archive(archiveFile.toFile())) {
            int entryCount = 0;
            long totalExtractedSize = 0;
            for (var header : archive.getFileHeaders()) {
                if (header.isDirectory()) continue;

                String entryName = header.getFileName().replace('\\', '/');
                if (header.isSplitBefore() || header.isSplitAfter()) {
                    throw new IOException("Çok parçalı RAR arşivleri desteklenmiyor: " + entryName);
                }
                entryCount++;
                validateArchiveEntry(
                        entryName, entryCount, header.getFullUnpackSize(), totalExtractedSize);
                Path entryPath = safeArchiveEntryPath(tempDir, entryName, "RAR");
                if (entryPath == null) continue;

                Files.createDirectories(entryPath.getParent());
                try (var fileOutput = Files.newOutputStream(entryPath,
                        StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                     var output = new LimitedOutputStream(
                             fileOutput, entryName,
                             Math.min(MAX_ARCHIVE_ENTRY_SIZE,
                                     MAX_TOTAL_EXTRACTED_SIZE - totalExtractedSize))) {
                    archive.extractFile(header, output);
                    totalExtractedSize += output.getWrittenBytes();
                }
            }
        }
    }

    private void validateArchiveEntry(String entryName, int entryCount,
                                      long declaredSize, long totalExtractedSize) throws IOException {
        if (entryCount > MAX_ARCHIVE_ENTRY_COUNT) {
            throw new IOException("Arşiv entry sayısı sınırı aşıldı: "
                    + MAX_ARCHIVE_ENTRY_COUNT);
        }
        if (declaredSize > MAX_ARCHIVE_ENTRY_SIZE) {
            throw new IOException("Arşiv girdisi boyut sınırını aşıyor: " + entryName);
        }
        if (declaredSize >= 0 && declaredSize > MAX_TOTAL_EXTRACTED_SIZE - totalExtractedSize) {
            throw new IOException("Arşivin açılmış toplam boyut sınırı aşıldı: "
                    + MAX_TOTAL_EXTRACTED_SIZE + " byte");
        }
    }

    private Path safeArchiveEntryPath(Path tempDir, String entryName, String format) {
        Path entryPath = tempDir.resolve(entryName).normalize();
        if (!entryPath.startsWith(tempDir)) {
            log.warn("  {} entry path traversal engellendi: {}", format, entryName);
            return null;
        }
        return entryPath;
    }

    private String assetPath(String targetDir, String relativePath) {
        String normalizedDir = targetDir.replace('\\', '/');
        String normalizedRelativePath = relativePath.replace('\\', '/');
        return normalizedDir.endsWith("/")
                ? normalizedDir + normalizedRelativePath
                : normalizedDir + "/" + normalizedRelativePath;
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

    private enum ArchiveFormat {
        ZIP,
        RAR
    }

    private record ResolvedExtraction(
            FileExtraction mapping,
            List<Path> matchedFiles,
            byte[] embeddedXslt
    ) { }

    private static final class LimitedOutputStream extends FilterOutputStream {
        private final String entryName;
        private final long maxBytes;
        private long writtenBytes;

        private LimitedOutputStream(OutputStream output, String entryName, long maxBytes) {
            super(output);
            this.entryName = entryName;
            this.maxBytes = maxBytes;
        }

        @Override
        public void write(int value) throws IOException {
            ensureCapacity(1);
            out.write(value);
            writtenBytes++;
        }

        @Override
        public void write(byte[] bytes, int offset, int length) throws IOException {
            ensureCapacity(length);
            out.write(bytes, offset, length);
            writtenBytes += length;
        }

        private void ensureCapacity(int additionalBytes) throws IOException {
            if (writtenBytes > maxBytes - additionalBytes) {
                throw new IOException("Arşiv girdisi veya toplam açılmış boyut sınırı aşıldı: "
                        + entryName);
            }
        }

        private long getWrittenBytes() {
            return writtenBytes;
        }
    }
}

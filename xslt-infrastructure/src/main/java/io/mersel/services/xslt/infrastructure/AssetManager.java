package io.mersel.services.xslt.infrastructure;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Uygulama içindeki XSD, XSL, Schematron ve profil asset dosyalarını yönetir.
 * <p>
 * <b>Yalnızca external dizinden</b> dosya okur — proje içinde bundled asset yoktur.
 * Asset dosyaları iki şekilde sağlanır:
 * <ol>
 *   <li><b>GIB Auto-Sync:</b> {@code POST /v1/admin/packages/sync} ile GIB'den otomatik indirilir</li>
 *   <li><b>Manuel:</b> {@code xslt.assets.external-path} ile belirtilen dizine kullanıcı tarafından kopyalanır</li>
 * </ol>
 * <p>
 * Docker ortamında tipik kullanım:
 * <pre>
 *   environment:
 *     XSLT_ASSETS_EXTERNAL_PATH: /opt/xslt-assets
 *   volumes:
 *     - xslt-assets:/opt/xslt-assets
 * </pre>
 */
@Component
public class AssetManager {

    private static final Logger log = LoggerFactory.getLogger(AssetManager.class);

    @Value("${xslt.assets.external-path:}")
    private String externalPath;

    @Value("${xslt.assets.seed-path:}")
    private String seedPath;

    private Path externalDir;

    @PostConstruct
    void init() {
        if (externalPath != null && !externalPath.isBlank()) {
            externalDir = Path.of(externalPath);
            if (Files.isDirectory(externalDir)) {
                log.info("AssetManager başlatıldı. Asset dizini: {}", externalDir);
            } else {
                // Dizin henüz yoksa oluşturmayı dene (GIB sync sonrası dolacak)
                try {
                    Files.createDirectories(externalDir);
                    log.info("AssetManager başlatıldı. Asset dizini oluşturuldu: {} (GIB sync veya manuel kopyalama ile doldurulacak)", externalDir);
                } catch (IOException e) {
                    log.warn("Asset dizini oluşturulamadı: {} — {}", externalDir, e.getMessage());
                    externalDir = null;
                }
            }
            if (externalDir != null && seedPath != null && !seedPath.isBlank()) {
                seedFrom(Path.of(seedPath));
            }
            if (externalDir != null) {
                logContents();
            }
        } else {
            externalDir = null;
            log.warn("AssetManager: xslt.assets.external-path yapılandırılmamış! " +
                    "Asset'ler kullanılamaz. GIB sync veya external-path ayarlayın.");
        }
    }

    /**
     * Seed dizinindeki dosyaları aynı göreli yollarla asset dizinine kopyalar (üzerine yazar).
     * <p>
     * Kendi imajına gömdüğü ya da salt-okunur bağladığı (ConfigMap, Secret, read-only volume)
     * asset'leri — özel belge türleri, varsayılan XSLT şablonları, doğrulama profilleri — her
     * açılışta yazılabilir asset dizinine taşımak isteyen dağıtımlar içindir. Seed'de olmayan
     * dosyalara (GİB sync çıktıları, admin API ile yazılanlar) dokunulmaz. Nokta ile başlayan
     * yollar (Kubernetes ConfigMap'in {@code ..data} bağlantıları) ve {@code auto-generated/} atlanır.
     * <p>
     * Seed dosyası kaynak kabul edilir: asset dizinindeki kopya (örneğin admin API/UI ile
     * düzenlenmiş) farklıysa üzerine yazılır ve bu bir uyarıyla loglanır.
     *
     * @return kopyalanan dosya sayısı
     */
    int seedFrom(Path seedDir) {
        if (!Files.isDirectory(seedDir)) {
            log.error("Seed dizini bulunamadı: {} (xslt.assets.seed-path) — seed atlandı", seedDir);
            return 0;
        }
        try {
            if (externalDir.toRealPath().startsWith(seedDir.toRealPath())) {
                // Asset dizini seed'in içindeyse her açılışta bir kat daha iç içe kopyalanırdı.
                log.error("Seed dizini asset dizininin kendisi ya da üst dizini olamaz: {} (asset dizini: {}) "
                        + "— seed atlandı", seedDir, externalDir);
                return 0;
            }
        } catch (IOException e) {
            log.error("Seed dizini çözümlenemedi: {} — {} — seed atlandı", seedDir, e.getMessage());
            return 0;
        }

        int[] copied = {0};
        int[] skipped = {0};
        // Bağlantılar izlenir: ConfigMap/Secret'ta alt dizinler ..data üzerinden symlink olarak gelir.
        // Okunamayan girdi (izin, bağlantı döngüsü) atlanır; açılışı durdurmaz.
        var visitor = new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                return isHiddenOrGenerated(seedDir.relativize(dir))
                        ? FileVisitResult.SKIP_SUBTREE
                        : FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path source, BasicFileAttributes attrs) {
                Path relative = seedDir.relativize(source);
                if (!attrs.isRegularFile() || isHiddenOrGenerated(relative)) {
                    return FileVisitResult.CONTINUE;
                }
                Path target = externalDir.resolve(relative.toString()).normalize();
                if (!target.startsWith(externalDir)) {
                    log.warn("Seed dosyası asset dizininin dışına çıkıyor, atlandı: {}", relative);
                    return FileVisitResult.CONTINUE;
                }
                try {
                    Files.createDirectories(target.getParent());
                    if (Files.isRegularFile(target) && Files.mismatch(source, target) != -1) {
                        // Admin API/UI ile yapılan düzenleme her açılışta sessizce geri alınmasın: görünür olsun.
                        log.warn("Asset dizinindeki {} seed'deki sürümden farklıydı; yerel sürüm seed ile "
                                + "değiştirildi. Seed ile gelen dosyaları seed kaynağında değiştirin.", relative);
                    }
                    Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
                    copied[0]++;
                } catch (IOException e) {
                    skipped[0]++;
                    log.warn("Seed dosyası kopyalanamadı, atlandı: {} — {}", relative, e.getMessage());
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException e) {
                skipped[0]++;
                log.warn("Seed girdisi okunamadı, atlandı: {} — {}", seedDir.relativize(file), e.toString());
                return FileVisitResult.CONTINUE;
            }
        };
        try {
            Files.walkFileTree(seedDir, EnumSet.of(FileVisitOption.FOLLOW_LINKS), Integer.MAX_VALUE, visitor);
        } catch (IOException e) {
            log.error("Seed kopyalama yarıda kaldı ({} dosya kopyalandı): {} — {}", copied[0], seedDir, e.getMessage());
            return copied[0];
        }
        if (skipped[0] > 0) {
            log.warn("Seed dizininden {} dosya kopyalandı, {} girdi atlandı: {} → {}",
                    copied[0], skipped[0], seedDir, externalDir);
        } else {
            log.info("Seed dizininden {} dosya asset dizinine kopyalandı: {} → {}", copied[0], seedDir, externalDir);
        }
        return copied[0];
    }

    private static boolean isHiddenOrGenerated(Path relative) {
        if (relative.getNameCount() > 0 && relative.getName(0).toString().equals("auto-generated")) {
            return true;
        }
        for (Path part : relative) {
            if (part.toString().startsWith(".")) {
                return true;
            }
        }
        return false;
    }

    /**
     * Belirtilen asset dosyasının InputStream'ini döndürür.
     * <p>
     * Sadece external dizinden okur. Dosya yoksa {@link IOException} fırlatır.
     * <p>
     * <strong>Önemli:</strong> Döndürülen InputStream mutlaka try-with-resources
     * ile kapatılmalıdır. Aksi halde kaynak sızıntısı (resource leak) oluşur.
     *
     * @param relativePath external dizin altındaki göreceli yol
     *                     (örn: "validator/ubl-tr-package/schema/common/UBL-CommonBasicComponents-2.1.xsd")
     * @return InputStream — <strong>caller kapatmakla yükümlüdür</strong>
     * @throws IOException Dosya bulunamazsa veya external path yapılandırılmamışsa
     */
    public InputStream getAssetStream(String relativePath) throws IOException {
        Path file = resolveAndValidate(relativePath);
        if (!Files.isRegularFile(file)) {
            throw new IOException("Asset dosyası bulunamadı: " + relativePath +
                    " (dizin: " + externalDir + "). GIB paket sync'i çalıştırın veya dosyayı manuel kopyalayın.");
        }
        return new FileInputStream(file.toFile());
    }

    /**
     * Asset dosyasının var olup olmadığını kontrol eder.
     */
    public boolean assetExists(String relativePath) {
        if (externalDir == null) return false;
        Path resolved = externalDir.resolve(relativePath).normalize();
        if (!resolved.startsWith(externalDir)) return false;
        return Files.isRegularFile(resolved);
    }

    /**
     * Asset dosyasını disk üzerindeki Path olarak döndürür.
     * <p>
     * Dosya zaten disk üzerinde olduğu için doğrudan Path döner.
     * {@code sch:include} gibi göreceli referanslar bu Path'in parent dizini
     * üzerinden çözümlenir.
     *
     * @param relativePath external dizin altındaki göreceli yol
     * @return Dosyanın disk üzerindeki Path'i
     * @throws IOException Dosya bulunamazsa
     */
    public Path resolveAssetOnDisk(String relativePath) throws IOException {
        Path file = resolveAndValidate(relativePath);
        if (!Files.isRegularFile(file)) {
            throw new IOException("Asset dosyası bulunamadı: " + relativePath +
                    " (dizin: " + externalDir + "). GIB paket sync'i çalıştırın veya dosyayı manuel kopyalayın.");
        }
        return file;
    }

    /**
     * External asset dizininin yolunu döndürür.
     *
     * @return External dizin Path'i, yapılandırılmamışsa {@code null}
     */
    public Path getExternalDir() {
        return externalDir;
    }

    /**
     * External path yapılandırılmış mı kontrol eder.
     */
    public boolean isConfigured() {
        return externalDir != null;
    }

    /**
     * Belirtilen göreceli dizindeki dosyaları listeler.
     * <p>
     * Yalnızca birinci seviye dosyaları döndürür (recursive değil).
     *
     * @param relativeDir external dizin altındaki göreceli dizin yolu
     *                    (örn: "validator/ubl-tr-package/schema/common")
     * @return Dosya adları listesi (sadece dosya adı, dizin yolu olmadan)
     */
    public List<String> listFiles(String relativeDir) {
        if (externalDir == null) return List.of();
        Path dir = externalDir.resolve(relativeDir).normalize();
        if (!dir.startsWith(externalDir)) return List.of();
        if (!Files.isDirectory(dir)) return List.of();

        try (var stream = Files.list(dir)) {
            return stream
                    .filter(Files::isRegularFile)
                    .map(p -> p.getFileName().toString())
                    .sorted()
                    .toList();
        } catch (IOException e) {
            log.warn("Dizin listelenemedi: {} — {}", dir, e.getMessage());
            return List.of();
        }
    }

    /**
     * Belirtilen göreceli dizini recursive olarak tarar ve ağaç yapısında döner.
     * <p>
     * Her düğüm {@code name}, {@code type} ("file" veya "directory"),
     * ve dizinler için {@code children} listesi içerir.
     * Dosyalar için isteğe bağlı {@code size} (byte) alanı eklenir.
     *
     * @param relativeDir external dizin altındaki göreceli dizin yolu
     * @return Tree yapısındaki düğüm listesi (boş veya null dizinler için boş liste)
     */
    public List<Map<String, Object>> listFileTree(String relativeDir) {
        if (externalDir == null) return List.of();
        Path dir = externalDir.resolve(relativeDir).normalize();
        if (!dir.startsWith(externalDir)) return List.of();
        if (!Files.isDirectory(dir)) return List.of();

        return buildTree(dir);
    }

    private List<Map<String, Object>> buildTree(Path dir) {
        var result = new ArrayList<Map<String, Object>>();

        try (var stream = Files.list(dir)) {
            var entries = stream.sorted((a, b) -> {
                // Dizinler önce, sonra dosyalar; her ikisi kendi içinde alfabetik
                boolean aDir = Files.isDirectory(a);
                boolean bDir = Files.isDirectory(b);
                if (aDir != bDir) return aDir ? -1 : 1;
                return a.getFileName().toString().compareToIgnoreCase(b.getFileName().toString());
            }).toList();

            for (var entry : entries) {
                var node = new LinkedHashMap<String, Object>();
                String name = entry.getFileName().toString();
                node.put("name", name);

                if (Files.isDirectory(entry)) {
                    node.put("type", "directory");
                    var children = buildTree(entry);
                    node.put("children", children);
                    node.put("fileCount", countFiles(children));
                } else {
                    node.put("type", "file");
                    try {
                        node.put("size", Files.size(entry));
                    } catch (IOException ignored) {
                        // Boyut okunamadıysa atla
                    }
                }
                result.add(node);
            }
        } catch (IOException e) {
            log.warn("Dizin ağacı oluşturulamadı: {} — {}", dir, e.getMessage());
        }

        return result;
    }

    private int countFiles(List<Map<String, Object>> nodes) {
        int count = 0;
        for (var node : nodes) {
            if ("file".equals(node.get("type"))) {
                count++;
            } else if ("directory".equals(node.get("type"))) {
                @SuppressWarnings("unchecked")
                var children = (List<Map<String, Object>>) node.get("children");
                if (children != null) count += countFiles(children);
            }
        }
        return count;
    }

    /**
     * Asset dosyasının tüm içeriğini byte dizisi olarak okur.
     * <p>
     * Küçük/orta boyutlu dosyalar için tercih edilir (InputStream yönetimi gerekmez).
     * Büyük dosyalar için {@link #getAssetStream(String)} kullanın.
     * <p>
     * <b>Not:</b> {@link #getAssetStream(String)} dönen InputStream'i çağıran tarafın
     * kapatması gerekir (try-with-resources). Bu metot bu riski ortadan kaldırır.
     *
     * @param relativePath external dizin altındaki göreceli yol
     * @return Dosya içeriği
     * @throws IOException Dosya bulunamazsa veya okunamazsa
     */
    public byte[] readAssetBytes(String relativePath) throws IOException {
        Path file = resolveAndValidate(relativePath);
        if (!Files.isRegularFile(file)) {
            throw new IOException("Asset dosyası bulunamadı: " + relativePath +
                    " (dizin: " + externalDir + "). GIB paket sync'i çalıştırın veya dosyayı manuel kopyalayın.");
        }
        return Files.readAllBytes(file);
    }

    // ── Asset Write/Delete ──────────────────────────────────────────

    /**
     * Asset dosyasını external dizine yazar.
     * <p>
     * Üst dizin yoksa otomatik olarak oluşturulur.
     * Path traversal koruması uygulanır.
     *
     * @param relativePath external dizin altındaki göreceli yol
     * @param content dosya içeriği
     * @throws IOException Yazma hatası veya external path yapılandırılmamışsa
     */
    public void writeAsset(String relativePath, byte[] content) throws IOException {
        Path file = resolveAndValidate(relativePath);
        Path parent = file.getParent();
        if (parent != null && !Files.isDirectory(parent)) {
            Files.createDirectories(parent);
        }
        Files.write(file, content);
        log.info("Asset dosyası yazıldı: {} ({} bytes)", relativePath, content.length);
    }

    /**
     * Asset dosyasını siler.
     *
     * @param relativePath external dizin altındaki göreceli yol
     * @return true silinirse, false dosya yoksa
     * @throws IOException Silme hatası veya external path yapılandırılmamışsa
     */
    public boolean deleteAsset(String relativePath) throws IOException {
        Path file = resolveAndValidate(relativePath);
        if (Files.isRegularFile(file)) {
            Files.delete(file);
            log.info("Asset dosyası silindi: {}", relativePath);
            return true;
        }
        return false;
    }

    /**
     * Asset dosyasının boyutunu döndürür.
     *
     * @param relativePath external dizin altındaki göreceli yol
     * @return dosya boyutu (byte), dosya yoksa -1
     */
    public long getAssetSize(String relativePath) {
        if (externalDir == null) return -1;
        Path resolved = externalDir.resolve(relativePath).normalize();
        if (!resolved.startsWith(externalDir)) return -1;
        try {
            return Files.isRegularFile(resolved) ? Files.size(resolved) : -1;
        } catch (IOException e) {
            return -1;
        }
    }

    /**
     * Asset dosyasının son değiştirilme zamanını döndürür.
     *
     * @param relativePath external dizin altındaki göreceli yol
     * @return son değiştirilme zamanı (epoch millis), dosya yoksa -1
     */
    public long getAssetLastModified(String relativePath) {
        if (externalDir == null) return -1;
        Path resolved = externalDir.resolve(relativePath).normalize();
        if (!resolved.startsWith(externalDir)) return -1;
        try {
            return Files.isRegularFile(resolved) ? Files.getLastModifiedTime(resolved).toMillis() : -1;
        } catch (IOException e) {
            return -1;
        }
    }

    // ── Auto-Generated ─────────────────────────────────────────────

    private static final String AUTO_GENERATED_DIR = "auto-generated";

    /**
     * Auto-generated dizininin yolunu döndürür.
     * Dizin yoksa oluşturur.
     *
     * @param subDir alt dizin adı (örn: "schematron", "schema-overrides")
     * @return Dizin Path'i
     * @throws IOException External path yapılandırılmamışsa veya dizin oluşturulamıyorsa
     */
    public Path getAutoGeneratedDir(String subDir) throws IOException {
        requireExternalDir();
        Path dir = externalDir.resolve(AUTO_GENERATED_DIR).resolve(subDir).normalize();
        if (!dir.startsWith(externalDir)) {
            throw new SecurityException("Path traversal engellendi: " + subDir);
        }
        if (!Files.isDirectory(dir)) {
            Files.createDirectories(dir);
            log.info("Auto-generated dizini oluşturuldu: {}", dir);
        }
        return dir;
    }

    /**
     * Derlenmiş/dönüştürülmüş dosyayı auto-generated dizinine yazar.
     *
     * @param subDir alt dizin (örn: "schematron", "schema-overrides")
     * @param fileName dosya adı (örn: "EDEFTER_KEBIR.xsl")
     * @param content dosya içeriği
     * @throws IOException Yazma hatası
     */
    public void writeAutoGenerated(String subDir, String fileName, byte[] content) throws IOException {
        Path dir = getAutoGeneratedDir(subDir);
        Path file = dir.resolve(fileName).normalize();
        if (!file.startsWith(dir)) {
            throw new SecurityException("Path traversal engellendi: " + fileName);
        }
        Files.write(file, content);
        log.debug("Auto-generated dosya yazıldı: {} ({} bytes)", file, content.length);
    }

    /**
     * Auto-generated dizinini temizler (reload öncesi).
     *
     * @param subDir temizlenecek alt dizin
     */
    public void clearAutoGenerated(String subDir) {
        if (externalDir == null) return;
        Path dir = externalDir.resolve(AUTO_GENERATED_DIR).resolve(subDir).normalize();
        if (!dir.startsWith(externalDir)) return;
        if (!Files.isDirectory(dir)) return;

        try (var stream = Files.list(dir)) {
            stream.filter(Files::isRegularFile).forEach(file -> {
                try {
                    Files.delete(file);
                } catch (IOException e) {
                    log.warn("Auto-generated dosya silinemedi: {} — {}", file, e.getMessage());
                }
            });
            log.debug("Auto-generated dizini temizlendi: {}", dir);
        } catch (IOException e) {
            log.warn("Auto-generated dizin temizlenemedi: {} — {}", dir, e.getMessage());
        }
    }

    // ── Internal ─────────────────────────────────────────────────────

    private void requireExternalDir() throws IOException {
        if (externalDir == null) {
            throw new IOException("Asset dizini yapılandırılmamış. " +
                    "xslt.assets.external-path ayarlayın (env: XSLT_ASSETS_EXTERNAL_PATH).");
        }
    }

    /**
     * Göreceli yolu normalize edip path traversal saldırısını engeller.
     * Çözümlenen yolun external dizin altında kaldığını doğrular.
     *
     * @param relativePath göreceli dosya/dizin yolu
     * @return Normalize edilmiş ve doğrulanmış Path
     * @throws IOException external path yapılandırılmamışsa
     * @throws SecurityException path traversal algılanırsa
     */
    private Path resolveAndValidate(String relativePath) throws IOException {
        requireExternalDir();
        Path resolved = externalDir.resolve(relativePath).normalize();
        if (!resolved.startsWith(externalDir)) {
            throw new SecurityException("Path traversal engellendi: " + relativePath);
        }
        return resolved;
    }

    /**
     * Asset dizininin yapılandırılmış ve en az bir dosya içerip içermediğini döndürür.
     *
     * @return dizin boşsa veya yapılandırılmamışsa {@code true}
     */
    public boolean isEmpty() {
        if (externalDir == null || !Files.isDirectory(externalDir)) {
            return true;
        }
        try (var stream = Files.walk(externalDir)) {
            return stream.noneMatch(Files::isRegularFile);
        } catch (IOException e) {
            return true;
        }
    }

    /**
     * Asset dizinindeki alt dizinin dosya içerip içermediğini döndürür.
     * <p>
     * Dizin ya da altındaki dizinler sembolik bağlantı olabilir (paket dizinini başka bir diske
     * bağlayan kurulumlar); bağlantılar izlenir. Dizin taranamazsa (izin, bağlantı döngüsü) dolu
     * kabul edilir: bu yöntem GİB ilk sync kararını verir ve emin olunamayan durumda paketleri
     * indirip dizinleri yeniden yazmak, sync'i atlamaktan daha zararlıdır.
     *
     * @param relativeDir asset dizinine göreli alt dizin (örn. {@code validator})
     * @return alt dizin yoksa, dosya içermiyorsa veya asset dizini yapılandırılmamışsa {@code true}
     */
    public boolean isEmpty(String relativeDir) {
        if (externalDir == null) {
            return true;
        }
        Path dir = externalDir.resolve(relativeDir).normalize();
        if (!dir.startsWith(externalDir) || !Files.isDirectory(dir)) {
            return true;
        }
        try (var stream = Files.walk(dir, FileVisitOption.FOLLOW_LINKS)) {
            return stream.noneMatch(Files::isRegularFile);
        } catch (IOException | UncheckedIOException e) {
            log.warn("Dizin taranamadı, dolu kabul ediliyor: {} — {}", dir, e.getMessage());
            return false;
        }
    }

    /**
     * Startup'ta external dizindeki mevcut dosyaları loglar.
     */
    private void logContents() {
        try (var stream = Files.walk(externalDir)) {
            var files = stream
                    .filter(Files::isRegularFile)
                    .map(externalDir::relativize)
                    .toList();

            if (files.isEmpty()) {
                log.info("  Asset dizini boş — GIB sync veya manuel kopyalama bekleniyor");
            } else {
                log.info("  {} asset dosyası mevcut:", files.size());
                for (var path : files) {
                    log.info("    {}", path);
                }
            }
        } catch (IOException e) {
            log.warn("  Asset dizin taranamadı: {}", e.getMessage());
        }
    }
}

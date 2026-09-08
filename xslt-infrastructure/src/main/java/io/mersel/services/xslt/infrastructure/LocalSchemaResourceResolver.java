package io.mersel.services.xslt.infrastructure;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.ls.LSInput;
import org.w3c.dom.ls.LSResourceResolver;

import java.io.InputStream;
import java.io.Reader;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

/**
 * XSD import/include referanslarını lokal dosyalara yönlendiren LSResourceResolver.
 * <p>
 * e-Defter ve XBRL XSD dosyaları, {@code xs:import} ile HTTP URL'ler üzerinden
 * diğer şemalara referans verir (örn: {@code http://www.xbrl.org/2003/xbrl-instance-2003-12-31.xsd}).
 * Bu dosyalar GIB paketi içinde lokal olarak da bulunur. Bu resolver, HTTP URL'leri
 * lokal dizinlerdeki dosya adına göre; göreceli referansları ise önce bildiren XSD'nin
 * konumuna, ardından göreceli yolun tamamına göre çözümler. Böylece internete erişim
 * gerekmez ve farklı GİB e-Arşiv rapor paketleri ortak imza şemalarını çoğaltmadan kullanır.
 * <p>
 * Çözümleme sırası:
 * <ol>
 *   <li>Göreceli referansı {@code baseURI} üzerinden çözmeyi dene</li>
 *   <li>Bulunamazsa lokal şema dizinlerinde göreceli yolun tamamını ara</li>
 *   <li>HTTP/HTTPS referanslarında geriye dönük uyumluluk için dosya adını ara</li>
 *   <li>Bulunursa lokal dosyayı döndür; yoksa {@code null} (varsayılan çözümleme)</li>
 * </ol>
 */
class LocalSchemaResourceResolver implements LSResourceResolver {

    private static final Logger log = LoggerFactory.getLogger(LocalSchemaResourceResolver.class);

    private final List<Path> schemaBaseDirs;

    /**
     * @param schemaBaseDirs Lokal XSD dosyalarının bulunduğu kök dizinler.
     *                       Verilen sırayla, alt dizinler dahil aranır.
     */
    LocalSchemaResourceResolver(Path... schemaBaseDirs) {
        this.schemaBaseDirs = Arrays.stream(schemaBaseDirs)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
    }

    @Override
    public LSInput resolveResource(String type, String namespaceURI,
                                   String publicId, String systemId, String baseURI) {
        if (systemId == null || systemId.isBlank()) {
            return null;
        }

        boolean httpReference = systemId.startsWith("http://") || systemId.startsWith("https://");
        if (!httpReference && hasNonFileAbsoluteScheme(systemId)) {
            return null;
        }

        Path localFile = httpReference
                ? resolveHttpReference(systemId)
                : resolveFileOrRelativeReference(systemId, baseURI);
        if (localFile == null) {
            log.debug("XSD referansı lokal olarak bulunamadı: {}", systemId);
            return null;
        }

        log.debug("XSD referansı lokal dosyaya yönlendirildi: {} → {}", systemId, localFile);
        return new PathLSInput(localFile, publicId, systemId, baseURI);
    }

    private Path resolveHttpReference(String systemId) {
        String path;
        try {
            path = URI.create(systemId).getPath();
        } catch (IllegalArgumentException e) {
            log.debug("XSD referansı geçerli bir URI değil: {}", systemId);
            return null;
        }
        if (path == null || path.isBlank()) {
            return null;
        }
        String fileName = path.substring(path.lastIndexOf('/') + 1);

        return schemaBaseDirs.stream()
                .map(dir -> findFileRecursive(dir, Path.of(fileName)))
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    private Path resolveFileOrRelativeReference(String systemId, String baseURI) {
        try {
            URI referenceUri = URI.create(systemId.replace('\\', '/'));
            if (referenceUri.isAbsolute() && "file".equalsIgnoreCase(referenceUri.getScheme())) {
                Path absolutePath = Path.of(referenceUri).normalize();
                return Files.isRegularFile(absolutePath) ? absolutePath : null;
            }

            Path relativePath = Path.of(referenceUri.getPath()).normalize();
            if (relativePath.toString().isBlank()) {
                return null;
            }

            // JAXP'nin normal davranışını koru: göreceli referansı önce
            // onu bildiren XSD'nin systemId/baseURI değerine göre çöz.
            Path baseResolved = resolveAgainstBaseUri(baseURI, relativePath);
            if (baseResolved != null) {
                return baseResolved;
            }

            // Ek arama dizinlerinde dosya adı değil, göreceli yolun tamamı
            // kullanılır. Böylece farklı alt dizinlerdeki aynı adlı XSD'ler
            // sessizce birbirinin yerine yüklenmez.
            return schemaBaseDirs.stream()
                    .map(dir -> findFileRecursive(dir, relativePath))
                    .filter(java.util.Objects::nonNull)
                    .findFirst()
                    .orElse(null);
        } catch (Exception e) {
            log.debug("XSD referansı geçerli bir lokal/göreceli yol değil: {}", systemId);
            return null;
        }
    }

    private Path resolveAgainstBaseUri(String baseURI, Path relativePath) {
        if (baseURI == null || baseURI.isBlank()) {
            return null;
        }
        try {
            URI base = URI.create(baseURI);
            if (!"file".equalsIgnoreCase(base.getScheme())) {
                return null;
            }
            Path baseFile = Path.of(base);
            Path parent = baseFile.getParent();
            if (parent == null) {
                return null;
            }
            Path candidate = parent.resolve(relativePath).normalize();
            return Files.isRegularFile(candidate) ? candidate : null;
        } catch (Exception e) {
            return null;
        }
    }

    private boolean hasNonFileAbsoluteScheme(String systemId) {
        int colonIndex = systemId.indexOf(':');
        if (colonIndex <= 1) {
            return false;
        }
        String scheme = systemId.substring(0, colonIndex);
        return scheme.chars().allMatch(ch -> Character.isLetterOrDigit(ch) || ch == '+' || ch == '-' || ch == '.')
                && !scheme.equalsIgnoreCase("file");
    }

    /**
     * Dizin ağacında verilen göreceli yol son ekini arar (ilk bulunan döner).
     */
    private static Path findFileRecursive(Path dir, Path relativePath) {
        if (!Files.isDirectory(dir)) {
            return null;
        }
        // Önce göreceli yolun tamamıyla doğrudan eşleştir.
        Path direct = dir.resolve(relativePath).normalize();
        if (direct.startsWith(dir.normalize()) && Files.isRegularFile(direct)) {
            return direct;
        }
        // Paket root'u arama dizininin üstündeyse aynı suffix'i alt dizinlerde ara.
        try (var stream = Files.walk(dir, 3)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(p -> p.endsWith(relativePath))
                    .findFirst()
                    .orElse(null);
        } catch (Exception e) {
            log.warn("Lokal şema araması başarısız: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Path tabanlı LSInput implementasyonu.
     * JAXP SchemaFactory'ye lokal dosyayı InputStream olarak sunar.
     */
    private static class PathLSInput implements LSInput {
        private final Path path;
        private final String publicId;
        private final String systemId;
        private final String baseURI;

        PathLSInput(Path path, String publicId, String systemId, String baseURI) {
            this.path = path;
            this.publicId = publicId;
            this.systemId = systemId;
            this.baseURI = baseURI;
        }

        @Override
        public Reader getCharacterStream() { return null; }

        @Override
        public void setCharacterStream(Reader characterStream) { }

        @Override
        public InputStream getByteStream() {
            try {
                return Files.newInputStream(path);
            } catch (Exception e) {
                log.error("Lokal XSD dosyası okunamadı: {}", path, e);
                return null;
            }
        }

        @Override
        public void setByteStream(InputStream byteStream) { }

        @Override
        public String getStringData() { return null; }

        @Override
        public void setStringData(String stringData) { }

        @Override
        public String getSystemId() {
            // Lokal dosyanın URI'sini systemId olarak döndür —
            // böylece bu dosyanın kendi göreceli import'ları da doğru çözümlenir
            return path.toUri().toString();
        }

        @Override
        public void setSystemId(String systemId) { }

        @Override
        public String getPublicId() { return publicId; }

        @Override
        public void setPublicId(String publicId) { }

        @Override
        public String getBaseURI() { return baseURI; }

        @Override
        public void setBaseURI(String baseURI) { }

        @Override
        public String getEncoding() {
            // null → encoding'i XML bildirimi belirler. Sabit bir değer döndürmek,
            // UTF-8 dışı bildirimi olan XSD'leri bozar.
            return null;
        }

        @Override
        public void setEncoding(String encoding) { }

        @Override
        public boolean getCertifiedText() { return false; }

        @Override
        public void setCertifiedText(boolean certifiedText) { }

        private static final Logger log = LoggerFactory.getLogger(PathLSInput.class);
    }
}

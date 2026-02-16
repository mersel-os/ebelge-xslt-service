package io.mersel.services.xslt.infrastructure;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.ls.LSInput;
import org.w3c.dom.ls.LSResourceResolver;

import java.io.InputStream;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * HTTP/HTTPS URL referanslarını lokal dosyalara yönlendiren LSResourceResolver.
 * <p>
 * e-Defter ve XBRL XSD dosyaları, {@code xs:import} ile HTTP URL'ler üzerinden
 * diğer şemalara referans verir (örn: {@code http://www.xbrl.org/2003/xbrl-instance-2003-12-31.xsd}).
 * Bu dosyalar GIB paketi içinde lokal olarak da bulunur. Bu resolver, HTTP URL'deki
 * dosya adını alarak lokal dizinde arar ve internete erişim gerektirmeden çözümler.
 * <p>
 * Çözümleme sırası:
 * <ol>
 *   <li>systemId bir HTTP/HTTPS URL mi? Değilse → {@code null} (varsayılan çözümleme)</li>
 *   <li>URL'den dosya adını çıkar (son {@code /} sonrası)</li>
 *   <li>Lokal şema dizininde bu adla dosya var mı?</li>
 *   <li>Varsa → lokal dosyayı döndür; yoksa → {@code null} (varsayılan çözümleme)</li>
 * </ol>
 */
class LocalSchemaResourceResolver implements LSResourceResolver {

    private static final Logger log = LoggerFactory.getLogger(LocalSchemaResourceResolver.class);

    private final Path schemaBaseDir;

    /**
     * @param schemaBaseDir Lokal XSD dosyalarının bulunduğu kök dizin.
     *                      Alt dizinlerde de arama yapılır.
     */
    LocalSchemaResourceResolver(Path schemaBaseDir) {
        this.schemaBaseDir = schemaBaseDir;
    }

    @Override
    public LSInput resolveResource(String type, String namespaceURI,
                                   String publicId, String systemId, String baseURI) {
        if (systemId == null || !(systemId.startsWith("http://") || systemId.startsWith("https://"))) {
            return null; // Lokal/göreceli referanslar varsayılan JAXP çözümlemesine bırakılır
        }

        // URL'den dosya adını çıkar: "http://www.xbrl.org/2003/xbrl-instance-2003-12-31.xsd" → "xbrl-instance-2003-12-31.xsd"
        String fileName = systemId.substring(systemId.lastIndexOf('/') + 1);
        if (fileName.isBlank()) {
            return null;
        }

        // Lokal dizinde bu adla dosya ara
        Path localFile = findFileRecursive(schemaBaseDir, fileName);
        if (localFile == null) {
            log.debug("HTTP referansı lokal olarak bulunamadı: {} (aranan: {})", systemId, fileName);
            return null;
        }

        log.debug("HTTP referansı lokal dosyaya yönlendirildi: {} → {}", systemId, localFile);
        return new PathLSInput(localFile, publicId, systemId, baseURI);
    }

    /**
     * Dizin ağacında verilen dosya adını arar (ilk bulunan döner).
     */
    private static Path findFileRecursive(Path dir, String fileName) {
        // Önce doğrudan dizinde ara (en yaygın durum)
        Path direct = dir.resolve(fileName);
        if (Files.isRegularFile(direct)) {
            return direct;
        }
        // Alt dizinlerde ara
        try (var stream = Files.walk(dir, 3)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().equals(fileName))
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
        public String getEncoding() { return "UTF-8"; }

        @Override
        public void setEncoding(String encoding) { }

        @Override
        public boolean getCertifiedText() { return false; }

        @Override
        public void setCertifiedText(boolean certifiedText) { }

        private static final Logger log = LoggerFactory.getLogger(PathLSInput.class);
    }
}

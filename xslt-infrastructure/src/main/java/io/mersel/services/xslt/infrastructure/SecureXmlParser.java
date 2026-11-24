package io.mersel.services.xslt.infrastructure;

import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

import javax.xml.XMLConstants;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.transform.Source;
import javax.xml.transform.sax.SAXSource;
import java.io.ByteArrayInputStream;
import java.util.Map;

/**
 * Güvenilmeyen XML'i harici DTD yüklemeden ve harici varlık çözmeden okuyan ayrıştırıcı.
 * <p>
 * Saxon'a düz {@code StreamSource} verildiğinde Saxon varsayılan JDK ayrıştırıcısını kullanır; o da
 * DOCTYPE içindeki {@code SYSTEM} varlıklarını ({@code file:}, {@code http:} ve
 * {@code systemId}'ye göre göreli yollar) çözer (XXE). Bu sınıfın döndürdüğü kaynak:
 * <ul>
 *   <li>Harici genel ve parametre varlıklarını çözmez; referans boş kalır.</li>
 *   <li>Harici DTD alt kümesini yüklemez.</li>
 *   <li>İç DTD alt kümesindeki varlıkları genişletmeye devam eder; genişleme sınırları
 *       {@link XMLConstants#FEATURE_SECURE_PROCESSING} ile gelir.</li>
 * </ul>
 * DOCTYPE'ı tümden reddetmek isteyen yerler (belge türü tespiti, Schematron çıktısı ayrıştırma)
 * bunun yerine {@code disallow-doctype-decl} kullanmaya devam eder.
 */
final class SecureXmlParser {

    private static final String EXTERNAL_GENERAL_ENTITIES =
            "http://xml.org/sax/features/external-general-entities";
    private static final String EXTERNAL_PARAMETER_ENTITIES =
            "http://xml.org/sax/features/external-parameter-entities";
    private static final String LOAD_EXTERNAL_DTD =
            "http://apache.org/xml/features/nonvalidating/load-external-dtd";

    /**
     * Ayrıştırıcıya uygulanan SAX özellikleri.
     * <p>
     * JDK ayrıştırıcısında {@code FEATURE_SECURE_PROCESSING=true}, {@code accessExternalDTD} ve
     * {@code accessExternalSchema} değerlerini de boşa çeker; böylece özellikler bir yerde
     * gözden kaçsa bile hiçbir protokolle dışarı çıkılamaz.
     */
    static final Map<String, Boolean> FEATURES = Map.of(
            XMLConstants.FEATURE_SECURE_PROCESSING, true,
            EXTERNAL_GENERAL_ENTITIES, false,
            EXTERNAL_PARAMETER_ENTITIES, false,
            LOAD_EXTERNAL_DTD, false);

    private SecureXmlParser() {
    }

    /**
     * İçeriği güvenli ayrıştırıcıyla okuyacak bir kaynak döner.
     *
     * @param content  XML içeriği
     * @param systemId belgenin {@code base-uri()} değeri; {@code null} ya da boşsa verilmez
     */
    static Source source(byte[] content, String systemId) {
        var input = new InputSource(new ByteArrayInputStream(content));
        if (systemId != null && !systemId.isBlank()) {
            input.setSystemId(systemId);
        }
        return new SAXSource(newXmlReader(), input);
    }

    /**
     * {@link #FEATURES} uygulanmış, namespace farkında bir JDK ayrıştırıcısı döner.
     */
    static XMLReader newXmlReader() {
        try {
            var factory = SAXParserFactory.newDefaultInstance();
            factory.setNamespaceAware(true);
            factory.setXIncludeAware(false);
            for (var feature : FEATURES.entrySet()) {
                factory.setFeature(feature.getKey(), feature.getValue());
            }
            var reader = factory.newSAXParser().getXMLReader();
            // Yukarıdaki özelliklerle çağrılmaması gerekir; çağrılırsa sessizce boş dönmek yerine reddet.
            reader.setEntityResolver((publicId, systemId) -> {
                throw new SAXException("Güvenlik: Harici entity engellendi: " + systemId);
            });
            return reader;
        } catch (ParserConfigurationException | SAXException e) {
            throw new IllegalStateException("Güvenli XML ayrıştırıcısı oluşturulamadı", e);
        }
    }
}

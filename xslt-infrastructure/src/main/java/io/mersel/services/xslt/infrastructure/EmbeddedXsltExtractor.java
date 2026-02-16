package io.mersel.services.xslt.infrastructure;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.xml.namespace.NamespaceContext;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * UBL XML belgelerinden gömülü (embedded) XSLT şablonunu çıkarır.
 * <p>
 * UBL-TR standartlarında e-fatura/e-irsaliye belgeleri,
 * {@code AdditionalDocumentReference/Attachment/EmbeddedDocumentBinaryObject}
 * elementi içinde Base64 kodlanmış XSLT şablonu taşıyabilir.
 *
 * <pre>{@code
 * <cac:AdditionalDocumentReference>
 *   <cac:Attachment>
 *     <cbc:EmbeddedDocumentBinaryObject
 *         filename="xxx.xslt"
 *         encodingCode="Base64"
 *         mimeCode="application/xml">
 *       PD94bWwg...
 *     </cbc:EmbeddedDocumentBinaryObject>
 *   </cac:Attachment>
 * </cac:AdditionalDocumentReference>
 * }</pre>
 *
 * Bu sınıf namespace-aware XPath ile gömülü XSLT'yi bulur ve decode eder.
 */
@Component
public class EmbeddedXsltExtractor {

    private static final Logger log = LoggerFactory.getLogger(EmbeddedXsltExtractor.class);

    private static final String CAC_NS = "urn:oasis:names:specification:ubl:schema:xsd:CommonAggregateComponents-2";
    private static final String CBC_NS = "urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2";

    /**
     * XPath: filename attribute'u .xslt veya .xsl ile biten ilk EmbeddedDocumentBinaryObject.
     * <p>
     * UBL belgesinde birden fazla AdditionalDocumentReference olabilir (PDF, resim vb.),
     * sadece XSLT uzantılı olanı alıyoruz.
     */
    private static final String XPATH_EXPRESSION =
            "//cac:AdditionalDocumentReference/cac:Attachment/cbc:EmbeddedDocumentBinaryObject" +
                    "[substring(@filename, string-length(@filename) - 4) = '.xslt'" +
                    " or substring(@filename, string-length(@filename) - 3) = '.xsl']";

    /**
     * Verilen XML belgesinden gömülü XSLT şablonunu çıkarır.
     *
     * @param xmlDocument XML belge içeriği (byte dizisi)
     * @return Gömülü XSLT içeriği (byte dizisi), bulunamazsa {@code null}
     */
    public byte[] extract(byte[] xmlDocument) {
        try {
            var factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);

            // XXE koruması
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);

            var builder = factory.newDocumentBuilder();
            var document = builder.parse(new ByteArrayInputStream(xmlDocument));

            var xpath = XPathFactory.newInstance().newXPath();
            xpath.setNamespaceContext(new UblNamespaceContext());

            var node = xpath.evaluate(XPATH_EXPRESSION, document, XPathConstants.NODE);

            if (node == null) {
                log.debug("Belgede gömülü XSLT bulunamadı");
                return null;
            }

            var base64Content = ((org.w3c.dom.Node) node).getTextContent();
            if (base64Content == null || base64Content.isBlank()) {
                log.warn("Gömülü XSLT elementi bulundu ancak içeriği boş");
                return null;
            }

            // Base64 whitespace toleranslı decode
            var decoded = Base64.getMimeDecoder().decode(base64Content.strip());

            // Windows-1254 → UTF-8 normalize
            var xsltString = new String(decoded, StandardCharsets.UTF_8);
            xsltString = xsltString.replace("Windows-1254", "UTF-8");

            var filename = ((org.w3c.dom.Element) node).getAttribute("filename");
            log.info("Belgeden gömülü XSLT çıkarıldı — dosya: {}, boyut: {} byte", filename, decoded.length);

            return xsltString.getBytes(StandardCharsets.UTF_8);

        } catch (Exception e) {
            log.warn("Gömülü XSLT çıkarma başarısız: {} — {}", e.getClass().getSimpleName(), e.getMessage());
            log.debug("Gömülü XSLT çıkarma hata detayı", e);
            return null;
        }
    }

    /**
     * UBL namespace context — XPath sorguları için gerekli namespace eşlemesi.
     */
    private static class UblNamespaceContext implements NamespaceContext {

        private static final Map<String, String> NAMESPACES = Map.of(
                "cac", CAC_NS,
                "cbc", CBC_NS
        );

        @Override
        public String getNamespaceURI(String prefix) {
            return NAMESPACES.getOrDefault(prefix, javax.xml.XMLConstants.NULL_NS_URI);
        }

        @Override
        public String getPrefix(String namespaceURI) {
            return NAMESPACES.entrySet().stream()
                    .filter(e -> e.getValue().equals(namespaceURI))
                    .map(Map.Entry::getKey)
                    .findFirst()
                    .orElse(null);
        }

        @Override
        public Iterator<String> getPrefixes(String namespaceURI) {
            var prefix = getPrefix(namespaceURI);
            return prefix != null ? List.of(prefix).iterator() : Collections.emptyIterator();
        }
    }
}

package io.mersel.services.xslt.infrastructure;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.XMLConstants;
import javax.xml.namespace.NamespaceContext;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import java.io.ByteArrayInputStream;
import java.io.StringReader;
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
    private static final String XSLT_NS = "http://www.w3.org/1999/XSL/Transform";

    /**
     * XPath: filename attribute'u .xslt veya .xsl ile biten ilk EmbeddedDocumentBinaryObject.
     * <p>
     * UBL belgesinde birden fazla AdditionalDocumentReference olabilir (PDF, resim vb.),
     * sadece XSLT uzantılı olanı alıyoruz.
     */
    private static final String XPATH_EXPRESSION =
            "//cac:AdditionalDocumentReference/cac:Attachment/cbc:EmbeddedDocumentBinaryObject" +
                    "[substring(@filename, string-length(@filename) - 4) = '.xslt'" +
                    " or substring(@filename, string-length(@filename) - 3) = '.xsl'" +
                    " or substring(@filename, string-length(@filename) - 4) = '.XSLT'" +
                    " or substring(@filename, string-length(@filename) - 3) = '.XSL']";

    /**
     * Verilen XML belgesinden gömülü XSLT şablonunu çıkarır.
     *
     * @param xmlDocument XML belge içeriği (byte dizisi)
     * @return Gömülü XSLT içeriği (byte dizisi), bulunamazsa {@code null}
     */
    public byte[] extract(byte[] xmlDocument) {
        try {
            var builder = newSecureDocumentBuilder();
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
            byte[] normalizedXslt = xsltString.getBytes(StandardCharsets.UTF_8);
            var filename = ((org.w3c.dom.Element) node).getAttribute("filename");

            if (!isXsltDocument(normalizedXslt)) {
                log.debug("Gömülü dosya geçerli bir XSLT değil, atlanıyor: {}", filename);
                return null;
            }

            log.info("Belgeden gömülü XSLT çıkarıldı — dosya: {}, boyut: {} byte", filename, decoded.length);

            return normalizedXslt;

        } catch (Exception e) {
            log.warn("Gömülü XSLT çıkarma başarısız: {} — {}", e.getClass().getSimpleName(), e.getMessage());
            log.debug("Gömülü XSLT çıkarma hata detayı", e);
            return null;
        }
    }

    private boolean isXsltDocument(byte[] content) {
        try {
            var document = newXsltInspectionDocumentBuilder().parse(new ByteArrayInputStream(content));
            var doctype = document.getDoctype();
            if (doctype != null && (doctype.getSystemId() != null || doctype.getPublicId() != null)) {
                return false;
            }
            var root = document.getDocumentElement();
            if (XSLT_NS.equals(root.getNamespaceURI())) {
                return "stylesheet".equals(root.getLocalName()) || "transform".equals(root.getLocalName());
            }
            // Simplified stylesheets use a literal result element (e.g. html) as
            // their root, with a namespace-qualified xsl:version attribute.
            return !root.getAttributeNS(XSLT_NS, "version").isBlank();
        } catch (Exception e) {
            return false;
        }
    }

    private DocumentBuilder newSecureDocumentBuilder() throws Exception {
        return newSecureDocumentBuilder(false);
    }

    /**
     * GİB şablonlarında görülebilen dahili entity tanımları için DOCTYPE'a izin verir;
     * harici DTD ve entity çözümlemesi yine kapalıdır. Yalnızca harici DTD içinde
     * tanımlanan entity'lere bağımlı şablonlar güvenlik gereği geçersiz sayılır.
     */
    private DocumentBuilder newXsltInspectionDocumentBuilder() throws Exception {
        return newSecureDocumentBuilder(true);
    }

    private DocumentBuilder newSecureDocumentBuilder(boolean allowDoctype) throws Exception {
        var factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", !allowDoctype);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        setExternalAccessAttributeIfSupported(factory, XMLConstants.ACCESS_EXTERNAL_DTD);
        setExternalAccessAttributeIfSupported(factory, XMLConstants.ACCESS_EXTERNAL_SCHEMA);

        var builder = factory.newDocumentBuilder();
        builder.setEntityResolver((publicId, systemId) -> new InputSource(new StringReader("")));
        builder.setErrorHandler(new DefaultHandler() {
            @Override
            public void error(SAXParseException exception) throws SAXException {
                throw exception;
            }

            @Override
            public void fatalError(SAXParseException exception) throws SAXException {
                throw exception;
            }
        });
        return builder;
    }

    /**
     * JAXP standart erişim kısıtlarını destekleyen parser'larda ek savunma katmanını etkinleştirir.
     * Bu attribute'ları tanımayan parser'larda dış entity/DTD feature'ları kapalı kalmaya devam eder.
     */
    private void setExternalAccessAttributeIfSupported(DocumentBuilderFactory factory, String attributeName) {
        try {
            factory.setAttribute(attributeName, "");
        } catch (IllegalArgumentException e) {
            log.debug("XML parser erişim kısıtı attribute'unu desteklemiyor: {} ({})",
                    attributeName, factory.getClass().getName());
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

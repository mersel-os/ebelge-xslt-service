package io.mersel.services.xslt.infrastructure;

import io.mersel.services.xslt.application.enums.DocumentType;
import io.mersel.services.xslt.application.interfaces.DocumentTypeDetectionException;
import io.mersel.services.xslt.application.interfaces.IDocumentTypeDetector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.ByteArrayInputStream;
import java.util.Map;

/**
 * SAX tabanlı XML belge türü tespit implementasyonu.
 * <p>
 * Performans için full DOM parse yapmaz — sadece root element, namespace URI,
 * namespace prefix ve e-Defter belgelerinde {@code xbrli:context id} attribute'ını okur.
 * <p>
 * Tespit kuralları:
 * <ul>
 *   <li>UBL-TR namespace ({@code urn:oasis:...:*-2}) → root element adına göre belge türü</li>
 *   <li>e-Arşiv namespace ({@code http://earsiv.efatura.gov.tr}) → EARCHIVE_REPORT</li>
 *   <li>e-Defter namespace ({@code http://www.edefter.gov.tr}) + prefix "edefter" → root element ve context id'ye göre</li>
 *   <li>e-Envanter namespace ({@code http://www.edefter.gov.tr}) + prefix "envanter" → root element'e göre</li>
 * </ul>
 */
@Service
public class DocumentTypeDetector implements IDocumentTypeDetector {

    private static final Logger log = LoggerFactory.getLogger(DocumentTypeDetector.class);

    // ── Namespace sabitleri ──
    private static final String NS_EDEFTER = "http://www.edefter.gov.tr";
    private static final String NS_EARCHIVE = "http://earsiv.efatura.gov.tr";
    private static final String NS_UBL_PREFIX = "urn:oasis:names:specification:ubl:schema:xsd:";

    // ── UBL-TR root element → DocumentType eşlemesi ──
    private static final Map<String, DocumentType> UBL_ROOT_MAP = Map.of(
            "Invoice", DocumentType.INVOICE,
            "CreditNote", DocumentType.CREDIT_NOTE,
            "DespatchAdvice", DocumentType.DESPATCH_ADVICE,
            "ReceiptAdvice", DocumentType.RECEIPT_ADVICE,
            "ApplicationResponse", DocumentType.APPLICATION_RESPONSE
    );

    // ── e-Defter / e-Envanter context id sabitleri ──
    private static final String CONTEXT_JOURNAL = "journal_context";
    private static final String CONTEXT_LEDGER = "ledger_context";
    private static final String CONTEXT_ASSETS = "assets_context";

    @Override
    public DocumentType detect(byte[] xmlContent) throws DocumentTypeDetectionException {
        if (xmlContent == null || xmlContent.length == 0) {
            throw new DocumentTypeDetectionException("XML içeriği boş");
        }

        try {
            SAXParserFactory factory = SAXParserFactory.newInstance();
            factory.setNamespaceAware(true);
            // XXE koruma
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);

            SAXParser parser = factory.newSAXParser();
            var handler = new DetectionHandler();

            try {
                parser.parse(new ByteArrayInputStream(xmlContent), handler);
            } catch (DetectionCompleteException e) {
                // Normal akış — parse erken durduruldu, sonuç handler'da
            }

            DocumentType result = handler.getDetectedType();
            if (result == null) {
                throw new DocumentTypeDetectionException(
                        "Belge türü tespit edilemedi. Tanınmayan namespace veya root element: "
                                + "namespace=" + handler.getRootNamespace()
                                + ", prefix=" + handler.getRootPrefix()
                                + ", localName=" + handler.getRootLocalName());
            }

            log.debug("Belge türü tespit edildi: {} (namespace={}, root={})",
                    result, handler.getRootNamespace(), handler.getRootLocalName());
            return result;

        } catch (DocumentTypeDetectionException e) {
            throw e;
        } catch (Exception e) {
            throw new DocumentTypeDetectionException("XML parse hatası: " + e.getMessage(), e);
        }
    }

    // ── SAX Handler ─────────────────────────────────────────────────

    /**
     * Parse'ı erken durdurmak için kullanılan sentinel exception.
     * Tüm gerekli bilgi toplandığında fırlatılır.
     */
    private static class DetectionCompleteException extends SAXException {
        DetectionCompleteException() {
            super("Detection complete");
        }
    }

    /**
     * SAX ContentHandler — root element bilgisi ve e-Defter context id'sini toplar.
     */
    private static class DetectionHandler extends DefaultHandler {

        private DocumentType detectedType;

        private String rootNamespace;
        private String rootPrefix;
        private String rootLocalName;

        // e-Defter defter root'u için context id aranıyor mu?
        private boolean searchingForContextId = false;
        private int depth = 0;

        DocumentType getDetectedType() {
            return detectedType;
        }

        String getRootNamespace() {
            return rootNamespace;
        }

        String getRootPrefix() {
            return rootPrefix;
        }

        String getRootLocalName() {
            return rootLocalName;
        }

        @Override
        public void startElement(String uri, String localName, String qName, Attributes attributes)
                throws SAXException {

            depth++;

            // İlk element = root element
            if (depth == 1) {
                rootNamespace = uri;
                rootLocalName = localName;

                // Prefix'i qName'den çıkar (ör: "edefter:defter" → "edefter")
                int colonIdx = qName.indexOf(':');
                rootPrefix = (colonIdx > 0) ? qName.substring(0, colonIdx) : "";

                detectedType = resolveFromRoot(uri, rootPrefix, localName);

                // e-Defter "defter" ve "berat" root'larında context id araması gerekiyor.
                // "berat" için: GIB hem e-Defter beratını hem envanter beratını
                // edefter:berat root'u ile yayınlıyor, ayrım context id ile yapılır.
                if (detectedType == null && NS_EDEFTER.equals(uri) && "edefter".equals(rootPrefix)
                        && ("defter".equals(localName) || "berat".equals(localName))) {
                    searchingForContextId = true;
                    return; // Parse'a devam et
                }

                if (detectedType != null) {
                    throw new DetectionCompleteException();
                }

                // Tanınmayan — parse'a devam edip bağlam aranmayacak
                if (!searchingForContextId) {
                    throw new DetectionCompleteException();
                }
            }

            // e-Defter/envanter context id araması: xbrli:context elementini bul
            if (searchingForContextId && "context".equals(localName)) {
                String id = attributes.getValue("id");
                if ("defter".equals(rootLocalName)) {
                    // edefter:defter → yevmiye veya kebir
                    if (CONTEXT_JOURNAL.equals(id)) {
                        detectedType = DocumentType.EDEFTER_YEVMIYE;
                        throw new DetectionCompleteException();
                    } else if (CONTEXT_LEDGER.equals(id)) {
                        detectedType = DocumentType.EDEFTER_KEBIR;
                        throw new DetectionCompleteException();
                    }
                } else if ("berat".equals(rootLocalName)) {
                    // edefter:berat → context id'ye göre envanter veya e-defter beratı
                    if (CONTEXT_ASSETS.equals(id)) {
                        detectedType = DocumentType.ENVANTER_BERAT;
                        throw new DetectionCompleteException();
                    } else if (CONTEXT_JOURNAL.equals(id) || CONTEXT_LEDGER.equals(id)) {
                        detectedType = DocumentType.EDEFTER_BERAT;
                        throw new DetectionCompleteException();
                    }
                }
            }
        }

        @Override
        public void endElement(String uri, String localName, String qName) throws SAXException {
            depth--;
        }

        /**
         * Root element bilgisinden belge türünü çözümler.
         * e-Defter "defter" root'u hariç — o context id gerektirir.
         */
        private DocumentType resolveFromRoot(String namespace, String prefix, String localName) {
            // UBL-TR namespace
            if (namespace != null && namespace.startsWith(NS_UBL_PREFIX)) {
                return UBL_ROOT_MAP.get(localName);
            }

            // e-Arşiv namespace
            if (NS_EARCHIVE.equals(namespace)) {
                return DocumentType.EARCHIVE_REPORT;
            }

            // e-Defter / e-Envanter namespace
            if (NS_EDEFTER.equals(namespace)) {
                if ("edefter".equals(prefix)) {
                    return resolveEdefter(localName);
                }
                if ("envanter".equals(prefix)) {
                    return resolveEnvanter(localName);
                }
            }

            return null;
        }

        /**
         * edefter: prefix'li root element'ten belge türünü çözümler.
         * "defter" ve "berat" root'ları null döner — context id araması gerekir.
         * (GIB hem e-Defter hem envanter beratını edefter:berat olarak yayınlıyor.)
         */
        private DocumentType resolveEdefter(String localName) {
            return switch (localName) {
                case "defterRaporu" -> DocumentType.EDEFTER_RAPOR;
                case "berat" -> null;  // context id gerekli — assets_context = envanter beratı
                case "defter" -> null; // context id gerekli — journal/ledger_context
                default -> null;
            };
        }

        /**
         * envanter: prefix'li root element'ten belge türünü çözümler.
         */
        private DocumentType resolveEnvanter(String localName) {
            return switch (localName) {
                case "defter" -> DocumentType.ENVANTER_DEFTER;
                case "berat" -> DocumentType.ENVANTER_BERAT;
                default -> null;
            };
        }
    }
}

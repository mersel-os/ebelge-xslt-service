package io.mersel.services.xslt.infrastructure;

import io.mersel.services.xslt.application.enums.SchemaValidationType;
import io.mersel.services.xslt.application.interfaces.ISchemaValidator;
import io.mersel.services.xslt.application.interfaces.Reloadable;
import io.mersel.services.xslt.application.interfaces.ReloadResult;
import io.mersel.services.xslt.application.models.XsdOverride;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.mersel.services.xslt.infrastructure.diagnostics.XsltMetrics;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

/**
 * JAXP tabanlı XML Schema (XSD) doğrulama implementasyonu.
 * <p>
 * Tüm XSD şemalarını ön-derleyerek (pre-compile) her doğrulama
 * isteğinde tekrar derleme maliyetini ortadan kaldırır.
 * <p>
 * Profil bazlı XSD override desteği: orijinal XSD dosyasını DOM olarak
 * parse eder, {@code minOccurs}/{@code maxOccurs} attribute'larını değiştirir,
 * değiştirilmiş XSD'yi derler ve cache'ler.
 * <p>
 * {@link Reloadable} arayüzü ile hot-reload destekler.
 */
@Service
public class JaxpSchemaValidator implements ISchemaValidator, Reloadable {

    private static final Logger log = LoggerFactory.getLogger(JaxpSchemaValidator.class);

    private final AssetManager assetManager;
    private final XsltMetrics metrics;

    /**
     * UBL-TR belge türleri — ortak XSD dosyaları gerektiren tipler.
     * EARCHIVE ve EDEFTER gibi bağımsız XSD'ler bu listeye dahil değildir.
     */
    private static final Set<SchemaValidationType> UBL_TYPES = Set.of(
            SchemaValidationType.INVOICE,
            SchemaValidationType.DESPATCH_ADVICE,
            SchemaValidationType.RECEIPT_ADVICE,
            SchemaValidationType.CREDIT_NOTE,
            SchemaValidationType.APPLICATION_RESPONSE
    );

    /**
     * Her UBL şema tipi için gerekli olan ortak XSD dosya yolları.
     * EARCHIVE ve EDEFTER gibi bağımsız XSD'ler için kullanılmaz.
     */
    private static final String[] COMMON_XSD_FILES = {
            "validator/ubl-tr-package/schema/common/CCTS_CCT_SchemaModule-2.1.xsd",
            "validator/ubl-tr-package/schema/common/UBL-CommonAggregateComponents-2.1.xsd",
            "validator/ubl-tr-package/schema/common/UBL-CommonBasicComponents-2.1.xsd",
            "validator/ubl-tr-package/schema/common/UBL-CommonExtensionComponents-2.1.xsd",
            "validator/ubl-tr-package/schema/common/UBL-CommonSignatureComponents-2.1.xsd",
            "validator/ubl-tr-package/schema/common/UBL-CoreComponentParameters-2.1.xsd",
            "validator/ubl-tr-package/schema/common/UBL-ExtensionContentDataType-2.1.xsd",
            "validator/ubl-tr-package/schema/common/UBL-QualifiedDataTypes-2.1.xsd",
            "validator/ubl-tr-package/schema/common/UBL-SignatureAggregateComponents-2.1.xsd",
            "validator/ubl-tr-package/schema/common/UBL-SignatureBasicComponents-2.1.xsd",
            "validator/ubl-tr-package/schema/common/UBL-UnqualifiedDataTypes-2.1.xsd",
            "validator/ubl-tr-package/schema/common/UBL-XAdESv132-2.1.xsd",
            "validator/ubl-tr-package/schema/common/UBL-XAdESv141-2.1.xsd",
            "validator/ubl-tr-package/schema/common/UBL-xmldsig-core-schema-2.1.xsd"
    };

    private static final Map<SchemaValidationType, String> MAIN_XSD_MAP = Map.ofEntries(
            Map.entry(SchemaValidationType.INVOICE, "validator/ubl-tr-package/schema/maindoc/UBL-Invoice-2.1.xsd"),
            Map.entry(SchemaValidationType.DESPATCH_ADVICE, "validator/ubl-tr-package/schema/maindoc/UBL-DespatchAdvice-2.1.xsd"),
            Map.entry(SchemaValidationType.RECEIPT_ADVICE, "validator/ubl-tr-package/schema/maindoc/UBL-ReceiptAdvice-2.1.xsd"),
            Map.entry(SchemaValidationType.CREDIT_NOTE, "validator/ubl-tr-package/schema/maindoc/UBL-CreditNote-2.1.xsd"),
            Map.entry(SchemaValidationType.APPLICATION_RESPONSE, "validator/ubl-tr-package/schema/maindoc/UBL-ApplicationResponse-2.1.xsd"),
            Map.entry(SchemaValidationType.EARCHIVE, "validator/earchive/schema/EArsiv.xsd"),
            Map.entry(SchemaValidationType.EDEFTER, "validator/eledger/schema/edefter.xsd")
    );

    /**
     * Derlenmiş XSD cache — volatile ile atomic swap.
     */
    private volatile Map<SchemaValidationType, Schema> compiledSchemas = Map.of();

    /**
     * Override'lı XSD cache — lazy compile, TTL ve max-size ile.
     * Key: "INVOICE::override-hash" formatında unique bir key.
     * Reload sırasında temizlenir.
     */
    private Cache<String, Schema> overrideCache;

    @Value("${xslt.cache.xsd-override-max-size:50}")
    private int xsdOverrideCacheMaxSize;

    @Value("${xslt.cache.xsd-override-ttl-hours:1}")
    private int xsdOverrideCacheTtlHours;

    public JaxpSchemaValidator(AssetManager assetManager, XsltMetrics metrics) {
        this.assetManager = assetManager;
        this.metrics = metrics;
    }

    @PostConstruct
    void init() {
        overrideCache = Caffeine.newBuilder()
                .maximumSize(xsdOverrideCacheMaxSize)
                .expireAfterWrite(Duration.ofHours(xsdOverrideCacheTtlHours))
                .build();
        metrics.registerXsdOverrideCacheSizeGauge(overrideCache);
    }

    /**
     * Derlenmiş XSD şema sayısını döndürür.
     */
    public int getLoadedCount() {
        return compiledSchemas.size();
    }

    // ── Reloadable ──────────────────────────────────────────────────

    @Override
    public String getName() {
        return "XSD Schemas";
    }

    @Override
    public ReloadResult reload() {
        long startTime = System.currentTimeMillis();
        var newCache = new HashMap<SchemaValidationType, Schema>();
        var errors = new ArrayList<String>();

        // Override cache'i temizle — base şemalar değiştiğinde override'lar da geçersiz
        overrideCache.invalidateAll();

        for (SchemaValidationType type : SchemaValidationType.values()) {
            try {
                var schema = compileSchema(type);
                newCache.put(type, schema);
                log.debug("  {} XSD şeması yüklendi", type);
            } catch (Exception e) {
                String error = type + " XSD şeması yüklenemedi: " + e.getMessage();
                errors.add(error);
                log.warn("  {}", error);
            }
        }

        // Atomic swap
        compiledSchemas = Map.copyOf(newCache);

        long elapsed = System.currentTimeMillis() - startTime;

        if (errors.isEmpty()) {
            return ReloadResult.success(getName(), newCache.size(), elapsed);
        } else if (!newCache.isEmpty()) {
            return ReloadResult.partial(getName(), newCache.size(), elapsed, errors);
        } else {
            return ReloadResult.failed(getName(), elapsed, String.join("; ", errors));
        }
    }

    // ── Doğrulama ───────────────────────────────────────────────────

    @Override
    public List<String> validate(byte[] source, SchemaValidationType schemaType) {
        return validate(source, schemaType, List.of());
    }

    @Override
    public List<String> validate(byte[] source, SchemaValidationType schemaType, List<XsdOverride> overrides) {
        long startTime = System.currentTimeMillis();
        List<String> errors = new ArrayList<>();

        try {
            Schema schema;

            if (overrides == null || overrides.isEmpty()) {
                // Override yok — base şemayı kullan
                schema = compiledSchemas.get(schemaType);
            } else {
                // Override var — cache'den al veya derle
                schema = getOrCompileOverriddenSchema(schemaType, overrides);
            }

            if (schema == null) {
                errors.add(schemaType + " için XSD şema dosyaları yüklenemedi. " +
                        "GIB paket sync çalıştırın veya XSD dosyalarını external-path dizinine kopyalayın.");
                metrics.recordValidation("schema", schemaType.name(), "error", System.currentTimeMillis() - startTime);
                return errors;
            }

            Validator validator = schema.newValidator();
            // XXE protection — kullanıcı XML'inde external entity çözümlemesini engelle
            try {
                validator.setProperty(javax.xml.XMLConstants.ACCESS_EXTERNAL_DTD, "");
                validator.setProperty(javax.xml.XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            } catch (org.xml.sax.SAXNotRecognizedException | org.xml.sax.SAXNotSupportedException e) {
                log.warn("XXE koruma özellikleri validator implementasyonu tarafından desteklenmiyor");
            }

            validator.setErrorHandler(new org.xml.sax.ErrorHandler() {
                @Override
                public void warning(org.xml.sax.SAXParseException e) { }

                @Override
                public void error(org.xml.sax.SAXParseException e) {
                    errors.add(formatError(e));
                }

                @Override
                public void fatalError(org.xml.sax.SAXParseException e) {
                    errors.add(formatError(e));
                }
            });

            validator.validate(new StreamSource(new ByteArrayInputStream(source)));

            String result = errors.isEmpty() ? "valid" : "invalid";
            metrics.recordValidation("schema", schemaType.name(), result, System.currentTimeMillis() - startTime);

        } catch (SAXException | IOException e) {
            errors.add("Şema doğrulama hatası: " + e.getMessage());
            metrics.recordValidation("schema", schemaType.name(), "error", System.currentTimeMillis() - startTime);
            log.warn("XSD doğrulama başarısız: {} - {}", schemaType, e.getMessage());
        }

        return errors;
    }

    // ── Override Schema Compilation ──────────────────────────────────

    /**
     * Override'lı şemayı cache'den döndürür veya yoksa yeni derler.
     * <p>
     * Orijinal XSD dosyasını DOM olarak parse eder, override'ları uygular,
     * değiştirilmiş XSD'yi byte[] olarak serialize edip systemId ile derler.
     */
    private Schema getOrCompileOverriddenSchema(SchemaValidationType schemaType, List<XsdOverride> overrides) {
        String cacheKey = buildOverrideCacheKey(schemaType, overrides);
        return overrideCache.get(cacheKey, key -> {
            try {
                return compileOverriddenSchema(schemaType, overrides);
            } catch (Exception e) {
                log.error("Override'lı XSD derleme hatası: {} - {}", schemaType, e.getMessage());
                return null;
            }
        });
    }

    /**
     * Override'ları uygulayarak şemayı derler.
     * Değiştirilmiş XSD'yi auto-generated dizinine de yazar.
     */
    private Schema compileOverriddenSchema(SchemaValidationType schemaType, List<XsdOverride> overrides) throws Exception {
        String mainXsd = MAIN_XSD_MAP.get(schemaType);
        if (mainXsd == null) {
            throw new IllegalArgumentException("Desteklenmeyen şema tipi: " + schemaType);
        }

        SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
        try {
            factory.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "file");
        } catch (org.xml.sax.SAXNotRecognizedException | org.xml.sax.SAXNotSupportedException e) {
            log.warn("SchemaFactory XXE koruma özellikleri desteklenmiyor");
        }

        // Common UBL XSD'leri yükle (değiştirilmemiş, sadece UBL türleri için)
        List<StreamSource> sources = new ArrayList<>();
        if (UBL_TYPES.contains(schemaType)) {
            for (String commonXsd : COMMON_XSD_FILES) {
                if (assetManager.assetExists(commonXsd)) {
                    var file = assetManager.resolveAssetOnDisk(commonXsd);
                    sources.add(new StreamSource(file.toFile()));
                }
            }
        }

        // Ana XSD'yi DOM olarak parse et ve override'ları uygula
        Path mainFile = assetManager.resolveAssetOnDisk(mainXsd);
        Document mainDoc = parseXsdDocument(mainFile);
        applyOverrides(mainDoc, overrides);

        // Değiştirilmiş XSD'yi byte[]'e serialize et
        byte[] modifiedXsd = serializeDocument(mainDoc);

        // Override edilmiş XSD'yi auto-generated dizinine yaz
        writeOverriddenXsd(schemaType, overrides, modifiedXsd);

        // systemId olarak orijinal dosya URI'sını kullan — import resolution için
        StreamSource mainSource = new StreamSource(
                new ByteArrayInputStream(modifiedXsd),
                mainFile.toUri().toString()
        );
        sources.add(mainSource);

        Schema schema = factory.newSchema(sources.toArray(new StreamSource[0]));
        log.info("Override'lı XSD derlendi: {} ({} override)", schemaType, overrides.size());
        return schema;
    }

    /**
     * Override edilmiş XSD'yi auto-generated dizinine yazar.
     */
    private void writeOverriddenXsd(SchemaValidationType schemaType, List<XsdOverride> overrides, byte[] xsdBytes) {
        try {
            // Dosya adı: INVOICE_override_2.xsd (override sayısı ile)
            String fileName = schemaType.name() + "_override_" + overrides.size() + ".xsd";
            assetManager.writeAutoGenerated("schema-overrides", fileName, xsdBytes);
            log.info("  Override edilmiş XSD yazıldı: auto-generated/schema-overrides/{}", fileName);
        } catch (Exception e) {
            log.warn("  Override edilmiş XSD diske yazılamadı: {} — {}", schemaType, e.getMessage());
        }
    }

    /**
     * XSD dosyasını DOM Document olarak parse eder.
     * XXE (XML External Entity) saldırılarına karşı korumalıdır.
     */
    private Document parseXsdDocument(Path xsdPath) throws Exception {
        DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
        dbFactory.setNamespaceAware(true);
        // XXE koruma
        dbFactory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        dbFactory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        dbFactory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        dbFactory.setExpandEntityReferences(false);
        DocumentBuilder builder = dbFactory.newDocumentBuilder();
        return builder.parse(xsdPath.toFile());
    }

    /**
     * Override kurallarını DOM Document'a uygular.
     * <p>
     * {@code <xsd:element ref="...">} node'larını bulur ve eşleşen
     * override'ların {@code minOccurs}/{@code maxOccurs} değerlerini set eder.
     */
    private void applyOverrides(Document doc, List<XsdOverride> overrides) {
        if (overrides == null || overrides.isEmpty()) return;

        // Element ref → override map oluştur
        Map<String, XsdOverride> overrideMap = new LinkedHashMap<>();
        for (XsdOverride ovr : overrides) {
            overrideMap.put(ovr.element(), ovr);
        }

        // Tüm xsd:element node'larını bul
        NodeList elements = doc.getElementsByTagNameNS(XMLConstants.W3C_XML_SCHEMA_NS_URI, "element");
        for (int i = 0; i < elements.getLength(); i++) {
            Element elem = (Element) elements.item(i);
            String ref = elem.getAttribute("ref");
            if (ref == null || ref.isEmpty()) continue;

            XsdOverride override = overrideMap.get(ref);
            if (override == null) continue;

            // minOccurs override
            if (override.minOccurs() != null) {
                elem.setAttribute("minOccurs", override.minOccurs());
                log.trace("XSD override uygulandı: {} minOccurs={}", ref, override.minOccurs());
            }

            // maxOccurs override
            if (override.maxOccurs() != null) {
                elem.setAttribute("maxOccurs", override.maxOccurs());
                log.trace("XSD override uygulandı: {} maxOccurs={}", ref, override.maxOccurs());
            }
        }
    }

    /**
     * DOM Document'ı byte[] olarak serialize eder.
     */
    private byte[] serializeDocument(Document doc) throws Exception {
        var tf = javax.xml.transform.TransformerFactory.newInstance();
        tf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        tf.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        tf.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
        var transformer = tf.newTransformer();
        transformer.setOutputProperty(javax.xml.transform.OutputKeys.INDENT, "no");
        transformer.setOutputProperty(javax.xml.transform.OutputKeys.ENCODING, "UTF-8");

        var out = new ByteArrayOutputStream();
        transformer.transform(new DOMSource(doc), new javax.xml.transform.stream.StreamResult(out));
        return out.toByteArray();
    }

    /**
     * Override listesi için deterministic cache key üretir.
     */
    private String buildOverrideCacheKey(SchemaValidationType schemaType, List<XsdOverride> overrides) {
        var sb = new StringBuilder(schemaType.name());
        // Override'ları sıralı hash
        overrides.stream()
                .sorted(Comparator.comparing(XsdOverride::element))
                .forEach(ovr -> {
                    sb.append("::").append(ovr.element());
                    if (ovr.minOccurs() != null) sb.append(":min=").append(ovr.minOccurs());
                    if (ovr.maxOccurs() != null) sb.append(":max=").append(ovr.maxOccurs());
                });
        return sb.toString();
    }

    // ── Base Schema Compilation ─────────────────────────────────────

    private Schema compileSchema(SchemaValidationType type) throws SAXException, IOException {
        SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
        try {
            factory.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "file");
        } catch (org.xml.sax.SAXNotRecognizedException | org.xml.sax.SAXNotSupportedException e) {
            log.warn("SchemaFactory XXE koruma özellikleri desteklenmiyor");
        }

        String mainXsd = MAIN_XSD_MAP.get(type);
        if (mainXsd == null) {
            throw new IllegalArgumentException("Desteklenmeyen şema tipi: " + type);
        }

        // StreamSource(File) kullanmak kritik — JAXP'nin xs:import/xs:include
        // referanslarını çözümleyebilmesi için systemId gerekli.
        // StreamSource(InputStream) ile systemId set edilmez → cross-reference hatası.
        List<StreamSource> sources = new ArrayList<>();

        // Ortak UBL XSD'leri sadece UBL belge türleri için eklenir.
        // EARCHIVE ve EDEFTER gibi bağımsız XSD'ler kendi import'larını kendi dizinlerinden çözer.
        if (UBL_TYPES.contains(type)) {
            for (String commonXsd : COMMON_XSD_FILES) {
                if (assetManager.assetExists(commonXsd)) {
                    var file = assetManager.resolveAssetOnDisk(commonXsd);
                    sources.add(new StreamSource(file.toFile()));
                }
            }
        }

        var mainFile = assetManager.resolveAssetOnDisk(mainXsd);
        sources.add(new StreamSource(mainFile.toFile()));

        // e-Defter / XBRL XSD dosyaları HTTP URL ile xs:import yapar
        // (örn: http://www.xbrl.org/2003/xbrl-instance-2003-12-31.xsd).
        // LocalSchemaResourceResolver bu HTTP URL'leri lokal dosyalara yönlendirir
        // ve internet erişimi gerektirmez.
        if (!UBL_TYPES.contains(type)) {
            Path schemaDir = mainFile.getParent();
            factory.setResourceResolver(new LocalSchemaResourceResolver(schemaDir));
        }

        return factory.newSchema(sources.toArray(new StreamSource[0]));
    }

    private String formatError(org.xml.sax.SAXParseException e) {
        return XsdErrorHumanizer.humanize(e.getLineNumber(), e.getColumnNumber(), e.getMessage());
    }
}

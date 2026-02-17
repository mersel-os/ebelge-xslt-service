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

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
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
        return validate(source, schemaType, List.of(), null);
    }

    @Override
    public List<String> validate(byte[] source, SchemaValidationType schemaType, List<XsdOverride> overrides) {
        return validate(source, schemaType, overrides, null);
    }

    @Override
    public List<String> validate(byte[] source, SchemaValidationType schemaType, List<XsdOverride> overrides, String profileName) {
        long startTime = System.currentTimeMillis();
        List<String> errors = new ArrayList<>();

        try {
            Schema schema;

            if (overrides == null || overrides.isEmpty()) {
                // Override yok — base şemayı kullan
                schema = compiledSchemas.get(schemaType);
            } else {
                // Override var — cache'den al veya derle
                schema = getOrCompileOverriddenSchema(schemaType, overrides, profileName);
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

        } catch (SchemaOverrideCompilationException e) {
            errors.add("Override'lı XSD derleme hatası: " + e.getMessage());
            metrics.recordValidation("schema", schemaType.name(), "error", System.currentTimeMillis() - startTime);
            log.warn("Override XSD derleme başarısız: {} (profil: {}) — {}", schemaType,
                    profileName != null ? profileName : "-", e.getMessage());
        } catch (SAXException | IOException e) {
            errors.add("Şema doğrulama hatası: " + e.getMessage());
            metrics.recordValidation("schema", schemaType.name(), "error", System.currentTimeMillis() - startTime);
            log.warn("XSD doğrulama başarısız: {} - {}", schemaType, e.getMessage());
        }

        return errors;
    }

    // ── Cache Invalidation ──────────────────────────────────────────

    @Override
    public void invalidateOverrideCache() {
        if (overrideCache != null) {
            long size = overrideCache.estimatedSize();
            overrideCache.invalidateAll();
            log.info("XSD override cache temizlendi ({} kayıt)", size);
        }
    }

    // ── Override Pre-compilation ─────────────────────────────────────

    @Override
    public void precompileOverrides(SchemaValidationType schemaType, List<XsdOverride> overrides, String profileName) {
        if (overrides == null || overrides.isEmpty()) {
            log.debug("Override yok, pre-derleme atlanıyor: {} (profil: {})", schemaType,
                    profileName != null ? profileName : "-");
            return;
        }

        try {
            getOrCompileOverriddenSchema(schemaType, overrides, profileName);
            log.info("Override pre-derleme tamamlandı: {} ({} override, profil: {})",
                    schemaType, overrides.size(), profileName != null ? profileName : "-");
        } catch (SchemaOverrideCompilationException e) {
            log.error("Override pre-derleme başarısız: {} (profil: {}) — {}",
                    schemaType, profileName != null ? profileName : "-", e.getMessage());
        }
    }

    // ── Override Schema Compilation ──────────────────────────────────

    /**
     * Override'lı şemayı cache'den döndürür veya yoksa yeni derler.
     * <p>
     * Orijinal XSD dosyasını DOM olarak parse eder, override'ları uygular,
     * değiştirilmiş XSD'yi byte[] olarak serialize edip systemId ile derler.
     * <p>
     * Derleme hatası durumunda cache'e null yazılmaz, her istekte yeniden derleme denenir.
     *
     * @throws SchemaOverrideCompilationException derleme başarısız olursa
     */
    private Schema getOrCompileOverriddenSchema(SchemaValidationType schemaType, List<XsdOverride> overrides, String profileName) {
        String cacheKey = buildOverrideCacheKey(schemaType, overrides, profileName);

        Schema cached = overrideCache.getIfPresent(cacheKey);
        if (cached != null) {
            return cached;
        }

        // Cache miss — derle ve cache'e yaz
        try {
            Schema schema = compileOverriddenSchema(schemaType, overrides, profileName);
            overrideCache.put(cacheKey, schema);
            return schema;
        } catch (Exception e) {
            log.error("Override'lı XSD derleme hatası: {} (profil: {}) — {}",
                    schemaType, profileName != null ? profileName : "-", e.getMessage());
            throw new SchemaOverrideCompilationException(
                    "Override'lı XSD derlenemedi: " + schemaType + " — " + e.getMessage(), e);
        }
    }

    /**
     * Override'ları uygulayarak şemayı derler.
     * Değiştirilmiş XSD'yi auto-generated dizinine de yazar.
     */
    private Schema compileOverriddenSchema(SchemaValidationType schemaType, List<XsdOverride> overrides, String profileName) throws Exception {
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
        OverrideApplyResult applyResult = applyOverrides(mainDoc, overrides);

        // Eşleşmeyen override'lar varsa uyarı logla
        if (!applyResult.unmatchedElements().isEmpty()) {
            log.warn("XSD override eşleşmedi — {} element XSD'de bulunamadı: {} (profil: {}, tip: {}). " +
                            "Element ref değerinin namespace prefix'i ile birlikte yazıldığından emin olun (örn: 'cac:Signature', 'cbc:UUID').",
                    applyResult.unmatchedElements().size(),
                    applyResult.unmatchedElements(),
                    profileName != null ? profileName : "-",
                    schemaType);
        }

        if (applyResult.matchedCount() > 0) {
            log.info("XSD override uygulandı: {}/{} element eşleşti (profil: {}, tip: {})",
                    applyResult.matchedCount(), overrides.size(),
                    profileName != null ? profileName : "-", schemaType);
        }

        // Değiştirilmiş XSD'yi byte[]'e serialize et
        byte[] modifiedXsd = serializeDocument(mainDoc);

        // Override edilmiş XSD'yi auto-generated dizinine yaz (metadata ile)
        writeOverriddenXsd(schemaType, overrides, modifiedXsd, profileName, applyResult);

        // systemId olarak orijinal dosya URI'sını kullan — import resolution için
        StreamSource mainSource = new StreamSource(
                new ByteArrayInputStream(modifiedXsd),
                mainFile.toUri().toString()
        );
        sources.add(mainSource);

        Schema schema = factory.newSchema(sources.toArray(new StreamSource[0]));
        log.info("Override'lı XSD derlendi: {} ({} override, profil: {})",
                schemaType, overrides.size(), profileName != null ? profileName : "-");
        return schema;
    }

    /**
     * Override edilmiş XSD'yi auto-generated dizinine yazar.
     * <p>
     * Dosya adı profil adını içerir ve XSD başına metadata yorumu eklenir.
     */
    private void writeOverriddenXsd(SchemaValidationType schemaType, List<XsdOverride> overrides,
                                     byte[] xsdBytes, String profileName, OverrideApplyResult applyResult) {
        try {
            String safeName = profileName != null && !profileName.isBlank() ? profileName : "adhoc";
            String fileName = schemaType.name() + "_" + safeName + "_override.xsd";

            byte[] withMetadata = prependXsdMetadataComment(xsdBytes, schemaType, profileName, overrides, applyResult);
            assetManager.writeAutoGenerated("schema-overrides", fileName, withMetadata);
            log.info("  Override edilmiş XSD yazıldı: auto-generated/schema-overrides/{}", fileName);
        } catch (Exception e) {
            log.warn("  Override edilmiş XSD diske yazılamadı: {} — {}", schemaType, e.getMessage());
        }
    }

    /**
     * XSD byte dizisinin başına profil ve override bilgisi içeren XML yorumu ekler.
     */
    private byte[] prependXsdMetadataComment(byte[] xsdBytes, SchemaValidationType schemaType,
                                              String profileName, List<XsdOverride> overrides,
                                              OverrideApplyResult applyResult) {
        var comment = new StringBuilder();
        comment.append("<!-- ══════════════════════════════════════════════════════\n");
        comment.append("     XSD Override Metadata\n");
        comment.append("     ══════════════════════════════════════════════════════\n");
        comment.append("     Profile    : ").append(profileName != null ? profileName : "(ad-hoc)").append("\n");
        comment.append("     SchemaType : ").append(schemaType.name()).append("\n");
        comment.append("     GeneratedAt: ").append(Instant.now()).append("\n");
        comment.append("     Matched    : ").append(applyResult.matchedCount()).append("/").append(overrides.size()).append("\n");

        if (!applyResult.unmatchedElements().isEmpty()) {
            comment.append("     UNMATCHED  : ").append(applyResult.unmatchedElements()).append("\n");
        }

        comment.append("     ──────────────────────────────────────────────────────\n");
        comment.append("     Overrides:\n");
        for (XsdOverride ovr : overrides) {
            comment.append("       - element: ").append(ovr.element());
            if (ovr.minOccurs() != null) comment.append(", minOccurs=").append(ovr.minOccurs());
            if (ovr.maxOccurs() != null) comment.append(", maxOccurs=").append(ovr.maxOccurs());
            boolean matched = !applyResult.unmatchedElements().contains(ovr.element());
            comment.append(matched ? " [OK]" : " [NOT FOUND]");
            comment.append("\n");
        }
        comment.append("     ══════════════════════════════════════════════════════ -->\n");

        String xsdStr = new String(xsdBytes, StandardCharsets.UTF_8);

        // XML declaration'dan sonra ekle (varsa)
        int insertPos = 0;
        if (xsdStr.startsWith("<?xml")) {
            int end = xsdStr.indexOf("?>");
            if (end > 0) {
                insertPos = end + 2;
                if (insertPos < xsdStr.length() && xsdStr.charAt(insertPos) == '\n') {
                    insertPos++;
                }
            }
        }

        String result = xsdStr.substring(0, insertPos) + comment + xsdStr.substring(insertPos);
        return result.getBytes(StandardCharsets.UTF_8);
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
     * <p>
     * Eşleşmeyen override'lar raporlanır — element ref değerinin doğru
     * QName formatında olduğundan emin olunmalıdır (örn: "cac:Signature").
     *
     * @return Uygulama sonucu — kaç override eşleşti, hangilerinin eşleşmedi
     */
    private OverrideApplyResult applyOverrides(Document doc, List<XsdOverride> overrides) {
        if (overrides == null || overrides.isEmpty()) {
            return new OverrideApplyResult(0, List.of());
        }

        // Element ref → override map oluştur
        Map<String, XsdOverride> overrideMap = new LinkedHashMap<>();
        for (XsdOverride ovr : overrides) {
            overrideMap.put(ovr.element(), ovr);
        }

        // Eşleşen element'leri takip et
        Set<String> matchedElements = new LinkedHashSet<>();

        // Tüm xsd:element node'larını bul
        NodeList elements = doc.getElementsByTagNameNS(XMLConstants.W3C_XML_SCHEMA_NS_URI, "element");
        for (int i = 0; i < elements.getLength(); i++) {
            Element elem = (Element) elements.item(i);
            String ref = elem.getAttribute("ref");
            if (ref == null || ref.isEmpty()) continue;

            XsdOverride override = overrideMap.get(ref);
            if (override == null) continue;

            matchedElements.add(ref);

            // minOccurs override
            if (override.minOccurs() != null) {
                String oldVal = elem.getAttribute("minOccurs");
                elem.setAttribute("minOccurs", override.minOccurs());
                log.debug("XSD override uygulandı: {} minOccurs={} (eski: {})", ref, override.minOccurs(),
                        oldVal.isEmpty() ? "default" : oldVal);
            }

            // maxOccurs override
            if (override.maxOccurs() != null) {
                String oldVal = elem.getAttribute("maxOccurs");
                elem.setAttribute("maxOccurs", override.maxOccurs());
                log.debug("XSD override uygulandı: {} maxOccurs={} (eski: {})", ref, override.maxOccurs(),
                        oldVal.isEmpty() ? "default" : oldVal);
            }
        }

        // Eşleşmeyen override'ları hesapla
        List<String> unmatchedElements = overrides.stream()
                .map(XsdOverride::element)
                .filter(el -> !matchedElements.contains(el))
                .toList();

        return new OverrideApplyResult(matchedElements.size(), unmatchedElements);
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
     * <p>
     * Profil adı cache key'e dahil edilir — her profil bağımsız olarak derlenir ve cache'lenir.
     */
    private String buildOverrideCacheKey(SchemaValidationType schemaType, List<XsdOverride> overrides, String profileName) {
        var sb = new StringBuilder(schemaType.name());

        // Profil adını key'e dahil et
        if (profileName != null && !profileName.isBlank()) {
            sb.append("@").append(profileName);
        }

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

    // ── Internal Records & Exceptions ────────────────────────────────

    /**
     * Override uygulama sonucu.
     *
     * @param matchedCount      Eşleşen override sayısı
     * @param unmatchedElements Eşleşmeyen element ref listesi
     */
    private record OverrideApplyResult(int matchedCount, List<String> unmatchedElements) {
    }

    /**
     * Override'lı XSD derleme hatası.
     * <p>
     * Caffeine cache'e null yazılmasını önlemek için kullanılır.
     */
    static class SchemaOverrideCompilationException extends RuntimeException {
        SchemaOverrideCompilationException(String message, Throwable cause) {
            super(message, cause);
        }
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

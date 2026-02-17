package io.mersel.services.xslt.infrastructure;

import io.mersel.services.xslt.application.enums.SchematronValidationType;
import io.mersel.services.xslt.application.interfaces.ISchematronValidator;
import io.mersel.services.xslt.application.interfaces.Reloadable;
import io.mersel.services.xslt.application.interfaces.ReloadResult;
import io.mersel.services.xslt.application.models.SchematronCustomAssertion;
import io.mersel.services.xslt.application.models.SchematronError;
import io.mersel.services.xslt.infrastructure.diagnostics.XsltMetrics;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import net.sf.saxon.s9api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import jakarta.annotation.PostConstruct;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Saxon HE tabanlı Schematron doğrulama implementasyonu.
 * <p>
 * Schematron kaynaklarını üç kategoride yönetir:
 * <ul>
 *   <li><b>Source XML</b> — Runtime'da ISO pipeline ile derlenir (örn: ubl-tr-package)</li>
 *   <li><b>Source SCH</b> — ISO Schematron (.sch) dosyaları, runtime'da ISO pipeline ile derlenir (örn: e-Defter)</li>
 *   <li><b>Pre-compiled XSL</b> — Doğrudan Saxon'a yüklenir (örn: e-Arşiv)</li>
 * </ul>
 * <p>
 * İki katmanlı özel Schematron kuralları (assertion) desteği:
 * <ul>
 *   <li><b>Global kurallar</b> — {@code reload()} sırasında orijinal XML'e enjekte edilir,
 *       {@code compiledSchematrons} cache'ine derlenir. Her zaman aktif.</li>
 *   <li><b>Profil kuralları</b> — Profil seçildiğinde global kurallar üzerine eklenir,
 *       {@code customRuleCache}'te tutulur.</li>
 * </ul>
 * <p>
 * {@link Reloadable} arayüzü ile hot-reload destekler.
 * <p>
 * Doğrulama sonuçları yapılandırılmış {@link SchematronError} nesneleri olarak döner.
 * Runtime'da derlenen Schematron'lar ({@code ruleId} ve {@code test} metadata'sı içerir),
 * pre-compiled XSL'ler ise sadece mesaj metni içerebilir.
 */
@Service
public class SaxonSchematronValidator implements ISchematronValidator, Reloadable {

    private static final Logger log = LoggerFactory.getLogger(SaxonSchematronValidator.class);

    private static final String SCHEMATRON_NS = "http://purl.oclc.org/dsdl/schematron";

    private final AssetManager assetManager;
    private final SchematronRuntimeCompiler runtimeCompiler;
    private final XsltMetrics metrics;
    private final Processor processor;

    /**
     * Derlenmesi gereken Schematron source XML dosyaları.
     * ubl-tr-package'daki ham Schematron XML → runtime'da XSLT'ye derlenir.
     */
    private static final Map<SchematronValidationType, String> SOURCE_XML_MAP = Map.of(
            SchematronValidationType.UBLTR_MAIN, "validator/ubl-tr-package/schematron/UBL-TR_Main_Schematron.xml"
    );

    /**
     * Derlenmesi gereken Schematron source SCH (ISO Schematron) dosyaları.
     * GIB'in dağıttığı ham .sch dosyaları → runtime'da ISO pipeline ile XSLT'ye derlenir.
     */
    private static final Map<SchematronValidationType, String> SOURCE_SCH_MAP = Map.of(
            SchematronValidationType.EDEFTER_YEVMIYE, "validator/eledger/schematron/edefter_yevmiye.sch",
            SchematronValidationType.EDEFTER_KEBIR, "validator/eledger/schematron/edefter_kebir.sch",
            SchematronValidationType.EDEFTER_BERAT, "validator/eledger/schematron/edefter_berat.sch",
            SchematronValidationType.EDEFTER_RAPOR, "validator/eledger/schematron/edefter_rapor.sch",
            SchematronValidationType.ENVANTER_BERAT, "validator/eledger/schematron/envanter_berat.sch",
            SchematronValidationType.ENVANTER_DEFTER, "validator/eledger/schematron/envanter_defter.sch"
    );

    /**
     * Önceden derlenmiş Schematron XSL dosyaları. Doğrudan Saxon'a yüklenir.
     */
    private static final Map<SchematronValidationType, String> PRECOMPILED_XSL_MAP = Map.of(
            SchematronValidationType.EARCHIVE_REPORT, "validator/earchive/schematron/earsiv_schematron.xsl"
    );

    /**
     * Derlenmiş Schematron cache — volatile ile atomic swap.
     * Global kurallar varsa, bunlar reload() sırasında base'e enjekte edilmiş olarak derlenir.
     */
    private volatile Map<SchematronValidationType, XsltExecutable> compiledSchematrons = Map.of();

    /**
     * Global özel Schematron kuralları — profil bağımsız, her zaman aktif.
     * reload() sırasında orijinal Schematron XML'e enjekte edilir.
     * ValidationProfileRegistry tarafından YAML'dan okunup set edilir.
     */
    private volatile Map<SchematronValidationType, List<SchematronCustomAssertion>> globalCustomRules = Map.of();

    /**
     * Profil bazlı özel kurallarla derlenmiş Schematron cache.
     * Key: "UBLTR_MAIN::profileName::profileRulesHash::globalRulesHash" formatında unique bir key.
     * Profil veya global kural değişikliklerinde temizlenir.
     */
    private Cache<String, XsltExecutable> customRuleCache;

    @Value("${xslt.cache.schematron-custom-rule-max-size:50}")
    private int customRuleCacheMaxSize;

    @Value("${xslt.cache.schematron-custom-rule-ttl-hours:1}")
    private int customRuleCacheTtlHours;

    public SaxonSchematronValidator(AssetManager assetManager,
                                   SchematronRuntimeCompiler runtimeCompiler,
                                   XsltMetrics metrics) {
        this.assetManager = assetManager;
        this.runtimeCompiler = runtimeCompiler;
        this.metrics = metrics;
        this.processor = new Processor(false);
    }

    @PostConstruct
    void init() {
        customRuleCache = Caffeine.newBuilder()
                .maximumSize(customRuleCacheMaxSize)
                .expireAfterWrite(Duration.ofHours(customRuleCacheTtlHours))
                .build();
    }

    /**
     * Derlenmiş Schematron kural sayısını döndürür.
     */
    public int getLoadedCount() {
        return compiledSchematrons.size();
    }

    // ── Reloadable ──────────────────────────────────────────────────

    @Override
    public String getName() {
        return "Schematron Rules";
    }

    @Override
    public ReloadResult reload() {
        long startTime = System.currentTimeMillis();
        var newCache = new HashMap<SchematronValidationType, XsltExecutable>();
        var errors = new ArrayList<String>();
        var compiler = processor.newXsltCompiler();

        // Auto-generated dizinini temizle (önceki derleme çıktıları)
        assetManager.clearAutoGenerated("schematron");

        // Özel kural cache'ini de temizle — base Schematron değiştiğinde eski cache geçersiz
        customRuleCache.invalidateAll();

        // Global kuralların snapshot'ını al (reload sırasında değişmemeli)
        var currentGlobalRules = this.globalCustomRules;
        int totalGlobalRules = currentGlobalRules.values().stream().mapToInt(List::size).sum();
        if (totalGlobalRules > 0) {
            log.info("  Global özel Schematron kuralları tespit edildi: {} tip, {} kural",
                    currentGlobalRules.size(), totalGlobalRules);
        }

        // Auto-generated global kural dizinini temizle
        assetManager.clearAutoGenerated("schematron-rules");

        // 1. Source XML'leri runtime'da derle (ubl-tr-package)
        //    sch:include çözümlemesi için dosya disk üzerinde olmalı (resolve-uri + document() gereksinimi)
        //    Global kurallar varsa derleme öncesi enjekte edilir
        for (var entry : SOURCE_XML_MAP.entrySet()) {
            try {
                if (assetManager.assetExists(entry.getValue())) {
                    var sourceFile = assetManager.resolveAssetOnDisk(entry.getValue());
                    List<SchematronCustomAssertion> globalRulesForType = currentGlobalRules.getOrDefault(entry.getKey(), List.of());

                    SchematronRuntimeCompiler.CompileResult result;
                    if (!globalRulesForType.isEmpty()) {
                        byte[] originalBytes = java.nio.file.Files.readAllBytes(sourceFile);
                        byte[] modifiedBytes = injectCustomRules(originalBytes, globalRulesForType, "global");
                        URI baseUri = sourceFile.toUri();
                        result = runtimeCompiler.compileAndReturn(modifiedBytes, baseUri);
                        writeCustomRuleOutput(entry.getKey(), "global", modifiedBytes, result.generatedXslt(), globalRulesForType);
                        log.info("  {} Schematron XML + {} global kural → XSLT derlendi (path={})",
                                entry.getKey(), globalRulesForType.size(), sourceFile);
                    } else {
                        result = runtimeCompiler.compileAndReturn(sourceFile);
                        log.debug("  {} Schematron XML → XSLT derlendi (path={})", entry.getKey(), sourceFile);
                    }

                    newCache.put(entry.getKey(), result.executable());
                    writeSchematronOutput(entry.getKey(), result.generatedXslt());
                } else {
                    String error = entry.getKey() + " kaynak dosyası bulunamadı: " + entry.getValue();
                    errors.add(error);
                    log.warn("  {}", error);
                }
            } catch (Exception e) {
                String error = entry.getKey() + " derleme hatası: " + e.getMessage();
                errors.add(error);
                log.warn("  {}", error);
            }
        }

        // 2. Source SCH'leri runtime'da derle (e-Defter ISO Schematron)
        //    Disk üzerinden derleme — sch:include olsa bile çözümlenir
        //    Global kurallar varsa derleme öncesi enjekte edilir
        for (var entry : SOURCE_SCH_MAP.entrySet()) {
            try {
                if (assetManager.assetExists(entry.getValue())) {
                    var sourceFile = assetManager.resolveAssetOnDisk(entry.getValue());
                    List<SchematronCustomAssertion> globalRulesForType = currentGlobalRules.getOrDefault(entry.getKey(), List.of());

                    SchematronRuntimeCompiler.CompileResult result;
                    if (!globalRulesForType.isEmpty()) {
                        byte[] originalBytes = java.nio.file.Files.readAllBytes(sourceFile);
                        byte[] modifiedBytes = injectCustomRules(originalBytes, globalRulesForType, "global");
                        URI baseUri = sourceFile.toUri();
                        result = runtimeCompiler.compileAndReturn(modifiedBytes, baseUri);
                        writeCustomRuleOutput(entry.getKey(), "global", modifiedBytes, result.generatedXslt(), globalRulesForType);
                        log.info("  {} Schematron SCH + {} global kural → XSLT derlendi (path={})",
                                entry.getKey(), globalRulesForType.size(), sourceFile);
                    } else {
                        result = runtimeCompiler.compileAndReturn(sourceFile);
                        log.debug("  {} Schematron SCH → XSLT derlendi (path={})", entry.getKey(), sourceFile);
                    }

                    newCache.put(entry.getKey(), result.executable());
                    writeSchematronOutput(entry.getKey(), result.generatedXslt());
                } else {
                    String error = entry.getKey() + " kaynak dosyası bulunamadı: " + entry.getValue();
                    errors.add(error);
                    log.warn("  {}", error);
                }
            } catch (Exception e) {
                String detail = e.getMessage();
                if (e.getCause() != null) {
                    detail += " — " + e.getCause().getMessage();
                }
                String error = entry.getKey() + " SCH derleme hatası: " + detail;
                errors.add(error);
                log.warn("  {}", error, e);
            }
        }

        // 3. Pre-compiled XSL'leri doğrudan yükle
        for (var entry : PRECOMPILED_XSL_MAP.entrySet()) {
            try {
                if (assetManager.assetExists(entry.getValue())) {
                    try (var is = assetManager.getAssetStream(entry.getValue())) {
                        var executable = compiler.compile(new StreamSource(is));
                        newCache.put(entry.getKey(), executable);
                        log.debug("  {} pre-compiled XSL yüklendi", entry.getKey());
                    }
                } else {
                    String error = entry.getKey() + " XSL dosyası bulunamadı: " + entry.getValue();
                    errors.add(error);
                    log.warn("  {}", error);
                }
            } catch (Exception e) {
                String error = entry.getKey() + " XSL yükleme hatası: " + e.getMessage();
                errors.add(error);
                log.warn("  {}", error);
            }
        }

        // Atomic swap
        compiledSchematrons = Map.copyOf(newCache);

        long elapsed = System.currentTimeMillis() - startTime;

        if (errors.isEmpty()) {
            return ReloadResult.success(getName(), newCache.size(), elapsed);
        } else if (!newCache.isEmpty()) {
            return ReloadResult.partial(getName(), newCache.size(), elapsed, errors);
        } else {
            return ReloadResult.failed(getName(), elapsed, String.join("; ", errors));
        }
    }

    /**
     * Derlenen Schematron XSLT çıktısını auto-generated dizinine yazar.
     */
    private void writeSchematronOutput(SchematronValidationType type, byte[] xsltBytes) {
        try {
            String fileName = type.name() + ".xsl";
            assetManager.writeAutoGenerated("schematron", fileName, xsltBytes);
            log.info("  {} derlenmiş XSLT yazıldı: auto-generated/schematron/{}", type, fileName);
        } catch (Exception e) {
            log.warn("  {} derlenmiş XSLT diske yazılamadı: {}", type, e.getMessage());
        }
    }

    // ── Doğrulama ───────────────────────────────────────────────────

    @Override
    public List<SchematronError> validate(byte[] source, SchematronValidationType schematronType,
                                          String ublTrMainSchematronType, String sourceFileName) {
        return validate(source, schematronType, ublTrMainSchematronType, sourceFileName, List.of(), null);
    }

    @Override
    public List<SchematronError> validate(byte[] source, SchematronValidationType schematronType,
                                          String ublTrMainSchematronType, String sourceFileName,
                                          List<SchematronCustomAssertion> customRules, String profileName) {
        long startTime = System.currentTimeMillis();
        List<SchematronError> errors = new ArrayList<>();

        try {
            // Özel kurallar varsa, özel derlenmiş Schematron kullan
            XsltExecutable executable;
            if (customRules != null && !customRules.isEmpty() && profileName != null) {
                executable = getOrCompileCustomSchematron(schematronType, customRules, profileName);
            } else {
                executable = compiledSchematrons.get(schematronType);
            }

            if (executable == null) {
                errors.add(new SchematronError(null, null,
                        schematronType + " için Schematron kuralları yüklenemedi. " +
                        "GIB paket sync çalıştırın veya Schematron dosyalarını external-path dizinine kopyalayın."));
                metrics.recordValidation("schematron", schematronType.name(), "error", System.currentTimeMillis() - startTime);
                return errors;
            }

            Xslt30Transformer transformer = executable.load30();

            // UBL-TR Main schematron için tip parametresi ayarla
            if (schematronType == SchematronValidationType.UBLTR_MAIN) {
                String typeParam = (ublTrMainSchematronType != null && !ublTrMainSchematronType.isBlank())
                        ? ublTrMainSchematronType : "efatura";
                transformer.setStylesheetParameters(Map.of(
                        new QName("type"), new XdmAtomicValue(typeParam)
                ));
            }

            // StreamSource oluştur
            var streamSource = new StreamSource(new ByteArrayInputStream(source));

            // Dosya adı varsa systemId olarak set et —
            // e-Defter Schematron base-uri() ile dosya adını kontrol eder
            // (örn: VKN/TCKN eşleştirmesi: contains($dosyaAdi, concat(xbrli:identifier,'-'))).
            if (sourceFileName != null && !sourceFileName.isBlank()) {
                streamSource.setSystemId(sourceFileName);
            }

            // Dönüşümü çalıştır
            var resultWriter = new StringWriter();
            var serializer = processor.newSerializer(resultWriter);
            transformer.transform(streamSource, serializer);

            // Sonucu parse et ve Error elementlerini çıkar
            String result = resultWriter.toString();
            if (result != null && !result.isBlank()) {
                errors.addAll(extractSchematronErrors(result));
            }

            String validationResult = errors.isEmpty() ? "valid" : "invalid";
            metrics.recordValidation("schematron", schematronType.name(), validationResult, System.currentTimeMillis() - startTime);

        } catch (SchematronCustomRuleCompilationException e) {
            errors.add(new SchematronError(null, null,
                    "Özel Schematron kural derleme hatası (profil: " + profileName + "): " + e.getMessage()));
            metrics.recordValidation("schematron", schematronType.name(), "error", System.currentTimeMillis() - startTime);
            log.warn("Özel Schematron kural derleme başarısız: {} (profil: {}) - {}", schematronType, profileName, e.getMessage());
        } catch (Exception e) {
            errors.add(new SchematronError(null, null,
                    "Schematron doğrulama hatası: " + e.getMessage()));
            metrics.recordValidation("schematron", schematronType.name(), "error", System.currentTimeMillis() - startTime);
            log.warn("Schematron doğrulama başarısız: {} - {}", schematronType, e.getMessage());
        }

        return errors;
    }

    // ── Global Kurallar ─────────────────────────────────────────────

    @Override
    public void setGlobalCustomRules(Map<SchematronValidationType, List<SchematronCustomAssertion>> rules) {
        this.globalCustomRules = rules != null ? Map.copyOf(rules) : Map.of();
        int totalRules = this.globalCustomRules.values().stream().mapToInt(List::size).sum();
        log.info("Global özel Schematron kuralları ayarlandı: {} tip, {} kural",
                this.globalCustomRules.size(), totalRules);
    }

    @Override
    public Map<SchematronValidationType, List<SchematronCustomAssertion>> getGlobalCustomRules() {
        return this.globalCustomRules;
    }

    // ── Custom Rule Cache ───────────────────────────────────────────

    @Override
    public void invalidateCustomRuleCache() {
        customRuleCache.invalidateAll();
        log.info("Özel Schematron kural cache'i temizlendi");
    }

    @Override
    public void precompileCustomRules(SchematronValidationType schematronType,
                                       List<SchematronCustomAssertion> customRules, String profileName) {
        if (customRules == null || customRules.isEmpty()) {
            return;
        }

        try {
            getOrCompileCustomSchematron(schematronType, customRules, profileName);
            log.info("Özel Schematron kuralları ön-derlendi: {} (profil: {}, {} kural)",
                    schematronType, profileName, customRules.size());
        } catch (Exception e) {
            log.warn("Özel Schematron kural ön-derleme başarısız: {} (profil: {}) — {}",
                    schematronType, profileName, e.getMessage());
        }
    }

    /**
     * Cache'ten alır veya profil+global kurallarla Schematron'u derler.
     * <p>
     * Profil kuralları geldiğinde, global kurallar da birleştirilir:
     * orijinal Schematron XML'e önce global, sonra profil kuralları enjekte edilir.
     * Bu sayede profil bazlı cache, global kuralları da içerir.
     */
    private XsltExecutable getOrCompileCustomSchematron(SchematronValidationType schematronType,
                                                         List<SchematronCustomAssertion> profileRules,
                                                         String profileName) {
        // Global kuralları al — profil kurallarıyla birleştirilecek
        List<SchematronCustomAssertion> globalRulesForType = globalCustomRules.getOrDefault(schematronType, List.of());

        // Birleşik kural listesi: global + profil
        List<SchematronCustomAssertion> combinedRules = new ArrayList<>(globalRulesForType.size() + profileRules.size());
        combinedRules.addAll(globalRulesForType);
        combinedRules.addAll(profileRules);

        String cacheKey = buildCustomRuleCacheKey(schematronType, profileRules, profileName, globalRulesForType);

        XsltExecutable cached = customRuleCache.getIfPresent(cacheKey);
        if (cached != null) {
            log.debug("Özel Schematron cache hit: {} (profil: {})", schematronType, profileName);
            return cached;
        }

        log.info("Özel Schematron derleniyor: {} (profil: {}, {} profil kural + {} global kural)",
                schematronType, profileName, profileRules.size(), globalRulesForType.size());

        // Kaynak Schematron dosya yolunu bul
        String sourcePath = SOURCE_XML_MAP.get(schematronType);
        if (sourcePath == null) {
            sourcePath = SOURCE_SCH_MAP.get(schematronType);
        }
        if (sourcePath == null) {
            throw new SchematronCustomRuleCompilationException(
                    schematronType + " tipi özel kural enjeksiyonunu desteklemiyor (pre-compiled XSL).");
        }

        try {
            if (!assetManager.assetExists(sourcePath)) {
                throw new SchematronCustomRuleCompilationException(
                        schematronType + " kaynak dosyası bulunamadı: " + sourcePath);
            }

            // Orijinal Schematron dosyasını oku (global enjekte EDİLMEMİŞ ham hali)
            var sourceFile = assetManager.resolveAssetOnDisk(sourcePath);
            byte[] originalBytes = java.nio.file.Files.readAllBytes(sourceFile);

            // Global + profil kurallarını birlikte enjekte et
            byte[] modifiedBytes = injectCustomRules(originalBytes, combinedRules, profileName);

            // Orijinal dosyanın URI'si ile derle (sch:include çözümlemesi için)
            URI baseUri = sourceFile.toUri();
            var result = runtimeCompiler.compileAndReturn(modifiedBytes, baseUri);

            // Cache'e yaz
            customRuleCache.put(cacheKey, result.executable());

            // Auto-generated dizinine yaz
            writeCustomRuleOutput(schematronType, profileName, modifiedBytes, result.generatedXslt(), combinedRules);

            return result.executable();

        } catch (SchematronCustomRuleCompilationException e) {
            throw e;
        } catch (Exception e) {
            throw new SchematronCustomRuleCompilationException(
                    "Özel kural derleme hatası: " + e.getMessage(), e);
        }
    }

    // ── Custom Rule Injection ───────────────────────────────────────

    /**
     * Özel Schematron kurallarını orijinal Schematron XML'e enjekte eder.
     * <p>
     * Kurallar, {@code context} değerine göre gruplanarak tek bir {@code <sch:pattern>}
     * bloğu oluşturulur. Her unique context için bir {@code <sch:rule>} oluşturulur,
     * aynı context'teki assertion'lar aynı rule altında toplanır.
     * <p>
     * Oluşturulan pattern, {@code </sch:schema>} kapanış tag'ından hemen önce eklenir.
     *
     * @param originalBytes Orijinal Schematron XML içeriği
     * @param customRules   Enjekte edilecek özel kurallar
     * @param profileName   Profil adı (pattern id'si için kullanılır)
     * @return Modifiye edilmiş Schematron XML byte'ları
     */
    byte[] injectCustomRules(byte[] originalBytes, List<SchematronCustomAssertion> customRules,
                                     String profileName) {
        try {
            var factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setExpandEntityReferences(false);

            var builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new ByteArrayInputStream(originalBytes));

            Element schemaElement = doc.getDocumentElement();

            // Profil-spesifik pattern oluştur
            String patternId = "custom-rules-" + sanitizeForId(profileName);
            Element patternElement = doc.createElementNS(SCHEMATRON_NS, "sch:pattern");
            patternElement.setAttribute("id", patternId);

            // Comment ekle
            var comment = doc.createComment(
                    " Custom Schematron rules — profile: " + profileName +
                    " — generated: " + LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) +
                    " — " + customRules.size() + " assertion(s) ");
            schemaElement.appendChild(comment);

            // Kuralları context'e göre grupla (insertion order korunsun)
            Map<String, List<SchematronCustomAssertion>> groupedByContext = new LinkedHashMap<>();
            for (var rule : customRules) {
                if (rule.context() == null || rule.context().isBlank()
                        || rule.test() == null || rule.test().isBlank()
                        || rule.message() == null || rule.message().isBlank()) {
                    log.warn("Eksik alan içeren özel kural atlanıyor (profil: {}): context={}, test={}, message={}",
                            profileName, rule.context(), rule.test(), rule.message());
                    continue;
                }
                groupedByContext.computeIfAbsent(rule.context(), k -> new ArrayList<>()).add(rule);
            }

            // Her context grubu için bir <sch:rule> oluştur
            for (var entry : groupedByContext.entrySet()) {
                Element ruleElement = doc.createElementNS(SCHEMATRON_NS, "sch:rule");
                ruleElement.setAttribute("context", entry.getKey());

                for (var assertion : entry.getValue()) {
                    Element assertElement = doc.createElementNS(SCHEMATRON_NS, "sch:assert");
                    assertElement.setAttribute("test", assertion.test());
                    if (assertion.id() != null && !assertion.id().isBlank()) {
                        assertElement.setAttribute("id", assertion.id());
                    }
                    assertElement.setTextContent(assertion.message());
                    ruleElement.appendChild(assertElement);
                }

                patternElement.appendChild(ruleElement);
            }

            schemaElement.appendChild(patternElement);

            // DOM'u byte'lara serialize et
            var tf = TransformerFactory.newInstance();
            var transformer = tf.newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");

            var baos = new ByteArrayOutputStream();
            transformer.transform(new DOMSource(doc), new StreamResult(baos));

            log.info("Özel kurallar enjekte edildi: profil={}, pattern={}, {} context, {} assertion",
                    profileName, patternId, groupedByContext.size(), customRules.size());

            return baos.toByteArray();

        } catch (Exception e) {
            throw new SchematronCustomRuleCompilationException(
                    "Özel kural enjeksiyon hatası: " + e.getMessage(), e);
        }
    }

    // ── Cache Key & Output ──────────────────────────────────────────

    /**
     * Özel kural cache key'i oluşturur.
     * Format: "UBLTR_MAIN::profileName::profileRulesHash::globalRulesHash"
     * Global kurallar değiştiğinde profil cache'leri de geçersiz olur.
     */
    private String buildCustomRuleCacheKey(SchematronValidationType type,
                                            List<SchematronCustomAssertion> profileRules,
                                            String profileName,
                                            List<SchematronCustomAssertion> globalRules) {
        int profileHash = computeRulesHash(profileRules);
        int globalHash = computeRulesHash(globalRules);
        return type.name() + "::" + (profileName != null ? profileName : "anonymous")
                + "::" + Integer.toHexString(profileHash)
                + "::" + Integer.toHexString(globalHash);
    }

    /**
     * Kural listesinin deterministik hash'ini hesaplar.
     */
    private int computeRulesHash(List<SchematronCustomAssertion> rules) {
        if (rules == null || rules.isEmpty()) return 0;
        String fingerprint = rules.stream()
                .sorted(Comparator.comparing(SchematronCustomAssertion::context, Comparator.nullsLast(String::compareTo))
                        .thenComparing(SchematronCustomAssertion::test, Comparator.nullsLast(String::compareTo))
                        .thenComparing(SchematronCustomAssertion::message, Comparator.nullsLast(String::compareTo)))
                .map(r -> r.context() + "|" + r.test() + "|" + r.message() + "|" + r.id())
                .collect(Collectors.joining(";;"));
        return fingerprint.hashCode();
    }

    /**
     * Özel kurallarla modifiye edilmiş Schematron XML ve derlenmiş XSLT'yi auto-generated dizinine yazar.
     */
    private void writeCustomRuleOutput(SchematronValidationType type, String profileName,
                                        byte[] modifiedSchematronXml, byte[] compiledXslt,
                                        List<SchematronCustomAssertion> customRules) {
        try {
            // Metadata comment ekle
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            String metadataComment = String.format(
                    "<!-- Custom Schematron Rules Output\n" +
                    "     Profile:        %s\n" +
                    "     Schema Type:    %s\n" +
                    "     Generated:      %s\n" +
                    "     Custom Rules:   %d assertion(s)\n" +
                    "     Rules:\n%s\n" +
                    "-->\n",
                    profileName, type.name(), timestamp, customRules.size(),
                    customRules.stream()
                            .map(r -> "       - [" + (r.id() != null ? r.id() : "no-id") + "] " +
                                    r.context() + " → " + r.test())
                            .collect(Collectors.joining("\n"))
            );

            // Modifiye edilmiş Schematron XML'i yaz
            String xmlFileName = type.name() + "_" + sanitizeForId(profileName) + "_custom.xml";
            byte[] xmlWithMetadata = (metadataComment + new String(modifiedSchematronXml, StandardCharsets.UTF_8))
                    .getBytes(StandardCharsets.UTF_8);
            assetManager.writeAutoGenerated("schematron-rules", xmlFileName, xmlWithMetadata);

            // Derlenmiş XSLT'yi yaz
            String xslFileName = type.name() + "_" + sanitizeForId(profileName) + "_custom.xsl";
            assetManager.writeAutoGenerated("schematron-rules", xslFileName, compiledXslt);

            log.info("  Özel Schematron çıktıları yazıldı: auto-generated/schematron-rules/{}", xmlFileName);
        } catch (Exception e) {
            log.warn("  Özel Schematron çıktıları diske yazılamadı: profil={}, {} — {}",
                    profileName, type, e.getMessage());
        }
    }

    /**
     * Profil adını XML id-safe formata dönüştürür.
     */
    private static String sanitizeForId(String input) {
        if (input == null || input.isBlank()) return "unknown";
        return input.replaceAll("[^a-zA-Z0-9_-]", "_");
    }

    // ── Error Extraction ────────────────────────────────────────────

    /**
     * Schematron XSLT çıktısından yapılandırılmış hata nesnelerini çıkarır.
     * <p>
     * Pipeline'a eklenen {@code ruleId} ve {@code test} attribute'larını parse eder.
     * Bu attribute'lar runtime'da derlenen Schematron'larda mevcuttur;
     * pre-compiled XSL'lerde bulunmayabilir (bu durumda {@code null} döner).
     */
    private List<SchematronError> extractSchematronErrors(String schematronOutput) {
        List<SchematronError> errors = new ArrayList<>();
        try {
            var factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            // XXE koruma
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setExpandEntityReferences(false);
            var builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new InputSource(new StringReader(schematronOutput)));

            NodeList errorNodes = doc.getElementsByTagName("Error");
            for (int i = 0; i < errorNodes.getLength(); i++) {
                Element errorElement = (Element) errorNodes.item(i);
                String errorText = errorElement.getTextContent();
                if (errorText != null && !errorText.isBlank()) {
                    String ruleId = getAttributeOrNull(errorElement, "ruleId");
                    String test = getAttributeOrNull(errorElement, "test");
                    errors.add(new SchematronError(ruleId, test, errorText.strip()));
                }
            }
        } catch (Exception e) {
            log.debug("Schematron çıktısı XML olarak parse edilemedi: {}", e.getMessage());
            if (!schematronOutput.isBlank()) {
                errors.add(new SchematronError(null, null, schematronOutput.strip()));
            }
        }
        return errors;
    }

    /**
     * Element'ten attribute değerini döndürür, yoksa veya boşsa {@code null}.
     */
    private static String getAttributeOrNull(Element element, String attributeName) {
        String value = element.getAttribute(attributeName);
        return (value != null && !value.isBlank()) ? value : null;
    }

    // ── Exception ───────────────────────────────────────────────────

    /**
     * Özel Schematron kural derleme hatası.
     */
    static class SchematronCustomRuleCompilationException extends RuntimeException {
        SchematronCustomRuleCompilationException(String message) {
            super(message);
        }
        SchematronCustomRuleCompilationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}

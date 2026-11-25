package io.mersel.services.xslt.infrastructure;

import io.mersel.services.xslt.application.enums.SchematronValidationType;
import io.mersel.services.xslt.application.models.SchematronCustomAssertion;
import io.mersel.services.xslt.application.models.SchematronError;
import io.mersel.services.xslt.infrastructure.diagnostics.XsltMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import net.sf.saxon.s9api.Processor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import javax.xml.transform.stream.StreamSource;
import java.io.ByteArrayInputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Özel Schematron kuralları için sandbox testleri.
 * <p>
 * Yönetici API'siyle tanımlanan kuralların {@code context}, {@code test}, {@code id} ve mesajdaki
 * {@code {{xpath}}} alanları derlenen Schematron XSLT'sinde XPath olarak çalışır. Her saldırı iki
 * yoldan denenir: global kurallar ({@code reload()} sırasında derlenir) ve profil kuralları (doğrulama
 * sırasında derlenir). {@link WithoutSandbox} aynı kuralların sandbox'sız Saxon'da gerçekten
 * sızdırdığını gösterir.
 * <p>
 * Ortam değişkeni denemesi yalnızca test görevinin verdiği {@value #PROBE_VAR} değişkenini kullanır.
 */
@DisplayName("Schematron özel kural sandbox")
class SchematronCustomRuleSandboxTest {

    private static final String SECRET = "SCH-SANDBOX-GIZLI-4c1d";
    private static final String PROBE_VAR = "XSLT_SANDBOX_PROBE";
    private static final String LEAK_MARKER = "SIZINTI";
    private static final String BLOCKED_PREFIX = "Schematron doğrulama hatası";
    /** Gizli XML dosyasını doğrudan {@code saxon:doc()} çağrısıyla okumaya çalışan kural mesajı. */
    private static final String SAXON_DOC_MESSAGE = "OUT=[{{string(Q{http://saxon.sf.net/}doc('{XML}', map{}))}}]";
    /** Saxon'a {@link SandboxInstantiationProbe} sınıfını collation olarak veren kural mesajı. */
    private static final String CLASS_COLLATION_MESSAGE = "OUT=[{{compare('a', 'b', 'http://saxon.sf.net/collation?class="
            + SandboxInstantiationProbe.class.getName() + "')}}]";
    /** Aynı okuma, fonksiyonu çalışma zamanında {@code function-lookup()} ile bularak. */
    private static final String SAXON_DOC_LOOKUP_MESSAGE =
            "OUT=[{{string(function-lookup(QName('http://saxon.sf.net/', 'doc'), 2)('{XML}', map{}))}}]";

    private static final String MAIN_PATH = "validator/ubl-tr-package/schematron/UBL-TR_Main_Schematron.xml";
    private static final String COMMON_FILE = "Test_Common_Schematron.xml";
    private static final String PRECOMPILED_PATH = "validator/earchive/schematron/earsiv_schematron.xsl";

    /** GİB UBL-TR Main yapısını taklit eder: kuralların bir kısmı {@code sch:include} ile diskten gelir. */
    private static final String MAIN_SCHEMATRON = """
            <?xml version="1.0" encoding="UTF-8"?>
            <sch:schema xmlns="http://purl.oclc.org/dsdl/schematron"
                        xmlns:sch="http://purl.oclc.org/dsdl/schematron">
                <sch:include href="%s#common"/>
                <sch:pattern id="main">
                    <sch:rule context="/Invoice">
                        <sch:assert test="ID" id="TEST-MainId">ID zorunlu</sch:assert>
                    </sch:rule>
                </sch:pattern>
            </sch:schema>
            """.formatted(COMMON_FILE);

    private static final String COMMON_SCHEMATRON = """
            <?xml version="1.0" encoding="UTF-8"?>
            <sch:schema xmlns="http://purl.oclc.org/dsdl/schematron"
                        xmlns:sch="http://purl.oclc.org/dsdl/schematron">
                <sch:pattern id="common">
                    <sch:rule context="/Invoice">
                        <sch:assert test="Supplier" id="TEST-CommonSupplier">Supplier zorunlu</sch:assert>
                    </sch:rule>
                </sch:pattern>
            </sch:schema>
            """;

    /** Temel kuralların hiçbirine takılmayan belge; çıkan her hata özel kuraldan gelir. */
    private static final String VALID_DOCUMENT = "<Invoice><ID>INV001</ID><Supplier>1111111111</Supplier></Invoice>";

    enum RulePath { GLOBAL, PROFILE }

    @TempDir
    Path tempDir;

    private Path assetRoot;
    private Path mainFile;
    private SchematronRuntimeCompiler runtimeCompiler;
    private SaxonSchematronValidator validator;

    private String secretTextUri;
    private String secretXmlUri;
    private String secretJsonUri;
    private String dirUri;

    @BeforeEach
    void setUp() throws Exception {
        Path secrets = Files.createDirectories(tempDir.resolve("secrets"));
        secretTextUri = Files.writeString(secrets.resolve("secret.txt"), SECRET).toUri().toString();
        secretXmlUri = Files.writeString(secrets.resolve("secret.xml"), "<s>" + SECRET + "</s>").toUri().toString();
        secretJsonUri = Files.writeString(secrets.resolve("secret.json"), "\"" + SECRET + "\"").toUri().toString();
        dirUri = secrets.toUri().toString();

        assetRoot = tempDir.resolve("assets");
        mainFile = assetRoot.resolve(MAIN_PATH);
        Files.createDirectories(mainFile.getParent());
        Files.writeString(mainFile, MAIN_SCHEMATRON);
        Files.writeString(mainFile.resolveSibling(COMMON_FILE), COMMON_SCHEMATRON);

        var assetManager = mock(AssetManager.class);
        when(assetManager.assetExists(anyString()))
                .thenAnswer(invocation -> Files.exists(assetRoot.resolve(invocation.<String>getArgument(0))));
        when(assetManager.resolveAssetOnDisk(anyString()))
                .thenAnswer(invocation -> assetRoot.resolve(invocation.<String>getArgument(0)));
        when(assetManager.getAssetStream(anyString()))
                .thenAnswer(invocation -> Files.newInputStream(assetRoot.resolve(invocation.<String>getArgument(0))));

        var metrics = new XsltMetrics(new SimpleMeterRegistry());
        runtimeCompiler = new SchematronRuntimeCompiler(metrics);
        Method init = SchematronRuntimeCompiler.class.getDeclaredMethod("init");
        init.setAccessible(true);
        init.invoke(runtimeCompiler);

        validator = new SaxonSchematronValidator(assetManager, runtimeCompiler, metrics);
        validator.init();
    }

    // ── Normal kurallar çalışmaya devam eder ───────────────────────

    @ParameterizedTest(name = "{0}")
    @EnumSource(RulePath.class)
    @DisplayName("Parametreli ve {{xpath}} mesajlı kural ile sch:include kuralları çalışır")
    void normalRulesStillWork(RulePath path) {
        var rule = new SchematronCustomAssertion("/Invoice", "Supplier = $sessionSupplier",
                "Satıcı ({{Supplier}}) oturumdaki firma ({{$sessionSupplier}}) değil", "TEST-Owner");

        var errors = validate(path, List.of(rule), "<Invoice><Supplier>1111111111</Supplier></Invoice>",
                Map.of("sessionSupplier", "2222222222"));

        assertThat(errors).containsExactlyInAnyOrder(
                new SchematronError("TEST-MainId", "ID", "ID zorunlu"),
                new SchematronError("TEST-Owner", "Supplier = $sessionSupplier",
                        "Satıcı (1111111111) oturumdaki firma (2222222222) değil"));

        var includeErrors = validate(path, List.of(rule), "<Invoice><ID>INV001</ID></Invoice>",
                Map.of("sessionSupplier", "2222222222"));
        assertThat(includeErrors).extracting(SchematronError::ruleId).contains("TEST-CommonSupplier");
    }

    // ── Ortam değişkenleri ve JVM özellikleri ─────────────────────

    @ParameterizedTest(name = "{0}")
    @EnumSource(RulePath.class)
    @DisplayName("environment-variable() mesajda boş döner")
    void environmentVariableIsEmpty(RulePath path) {
        assumeProbeVariable();

        var errors = validate(path, rule("/Invoice", "false()",
                "ENV=[{{environment-variable('" + PROBE_VAR + "')}}] "
                        + "COUNT=[{{count(available-environment-variables())}}]"));

        assertThat(messages(errors)).containsExactly("ENV=[] COUNT=[0]");
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(RulePath.class)
    @DisplayName("system-property() JVM özelliklerini döndürmez")
    void systemPropertyDoesNotExposeJvmProperties(RulePath path) {
        var errors = validate(path, rule("/Invoice", "false()",
                "HOME-LEN=[{{string-length(system-property('user.home'))}}]"));

        assertThat(messages(errors)).containsExactly("HOME-LEN=[0]");
    }

    static Stream<Arguments> oracleTests() {
        return Stream.of(
                // Sandbox her ad için aynı boş metni döner; değer ve varlık bilgisi çıkmaz.
                Arguments.of("environment-variable", "string(environment-variable('" + PROBE_VAR + "')) = ''"),
                Arguments.of("available-environment-variables", "empty(available-environment-variables())"),
                Arguments.of("unparsed-text-available", "not(unparsed-text-available('{TXT}'))"),
                Arguments.of("doc-available", "not(doc-available('{XML}'))"),
                Arguments.of("system-property", "system-property('user.home') = ''"),
                Arguments.of("transform", "not(function-available('transform', 1))"),
                Arguments.of("function-lookup transform",
                        "empty(function-lookup(QName('http://www.w3.org/2005/xpath-functions', 'transform'), 1))"),
                Arguments.of("function-lookup saxon:doc",
                        "empty(function-lookup(QName('http://saxon.sf.net/', 'doc'), 2))"));
    }

    static Stream<Arguments> oracleTestsOnBothPaths() {
        return oracleTests().flatMap(args -> Stream.of(RulePath.values())
                .map(path -> Arguments.of(path, args.get()[0], args.get()[1])));
    }

    @ParameterizedTest(name = "{0} / {1}")
    @MethodSource("oracleTestsOnBothPaths")
    @DisplayName("test ifadesi ortam ya da dosya hakkında bilgi vermez")
    void testExpressionCannotProbe(RulePath path, String name, String test) {
        if (test.contains(PROBE_VAR)) {
            assumeProbeVariable();
        }

        var errors = validate(path, rule("/Invoice", withUris(test), LEAK_MARKER));

        assertThat(errors).isEmpty();
    }

    // ── Dosya ve ağ okuma ──────────────────────────────────────────

    static Stream<Arguments> fileReadingExpressions() {
        return Stream.of(
                Arguments.of("unparsed-text", "unparsed-text('{TXT}')", SECRET),
                Arguments.of("unparsed-text-lines", "string-join(unparsed-text-lines('{TXT}'), ' ')", SECRET),
                Arguments.of("json-doc", "string(json-doc('{JSON}'))", SECRET),
                Arguments.of("collection",
                        "string-join(for $d in collection('{DIR}?select=*.xml') return string($d), ' ')", SECRET),
                Arguments.of("uri-collection", "string-join(uri-collection('{DIR}'), ' ')", "secret.txt"),
                Arguments.of("doc", "string(doc('{XML}'))", SECRET),
                Arguments.of("document", "string(document('{XML}'))", SECRET),
                Arguments.of("unparsed-text http", "unparsed-text('http://127.0.0.1:9/sandbox')", "sandbox-http"));
    }

    static Stream<Arguments> fileReadingExpressionsOnBothPaths() {
        return fileReadingExpressions().flatMap(args -> Stream.of(RulePath.values())
                .map(path -> Arguments.of(path, args.get()[0], args.get()[1], args.get()[2])));
    }

    @ParameterizedTest(name = "{0} / {1}")
    @MethodSource("fileReadingExpressionsOnBothPaths")
    @DisplayName("Mesajda dosya/ağ okuyan fonksiyonlar engellenir")
    void fileReadingFunctionsAreBlocked(RulePath path, String name, String expression, String forbidden) {
        var errors = validate(path, rule("/Invoice", "false()", "OUT=[{{" + withUris(expression) + "}}]"));

        assertBlocked(errors, forbidden);
    }

    static Stream<Arguments> saxonDocMessagesOnBothPaths() {
        return Stream.of(SAXON_DOC_MESSAGE, SAXON_DOC_LOOKUP_MESSAGE).flatMap(message -> Stream.of(RulePath.values())
                .map(path -> Arguments.of(path, message)));
    }

    @ParameterizedTest(name = "{0} / {1}")
    @MethodSource("saxonDocMessagesOnBothPaths")
    @DisplayName("Mesajdaki saxon:doc() dosya okuyamaz, kural çalışmaz")
    void saxonDocInMessageIsBlocked(RulePath path, String message) {
        var errors = validate(path, rule("/Invoice", "false()", message.replace("{XML}", secretXmlUri)));

        assertThat(errors).isNotEmpty();
        assertThat(messages(errors)).noneMatch(text -> text.startsWith("OUT=["));
        assertThat(errors).allSatisfy(error -> {
            assertThat(error.message()).doesNotContain(SECRET);
            assertThat(String.valueOf(error.ruleId())).doesNotContain(SECRET);
            assertThat(String.valueOf(error.test())).doesNotContain(SECRET);
        });
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(RulePath.class)
    @DisplayName("/etc/hostname içeriği dönmez")
    void etcHostnameIsNotReturned(RulePath path) throws Exception {
        String hostname = readHostname();

        var errors = validate(path, rule("/Invoice", "false()",
                "HOST=[{{unparsed-text('file:///etc/hostname')}}]"));

        assertBlocked(errors, hostname);
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(RulePath.class)
    @DisplayName("context ifadesindeki dosya okuması eşleşme vermez")
    void fileReadInContextDoesNotMatch(RulePath path) {
        var errors = validate(path, rule(
                "/Invoice[contains(unparsed-text('" + secretTextUri + "'), 'GIZLI')]", "false()", LEAK_MARKER));

        assertThat(messages(errors)).noneMatch(message -> message.contains(LEAK_MARKER));
        assertThat(messages(errors)).noneMatch(message -> message.contains(SECRET));
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(RulePath.class)
    @DisplayName("parse-xml() harici varlık çözmez")
    void parseXmlDoesNotResolveExternalEntities(RulePath path) {
        String inner = "<!DOCTYPE x [<!ENTITY e SYSTEM \"" + secretTextUri + "\">]><x>&e;</x>";

        var errors = validate(path, rule("/Invoice", "false()", "PX=[{{string(parse-xml('" + inner + "'))}}]"));

        assertThat(errors).isNotEmpty();
        assertThat(messages(errors)).noneMatch(message -> message.contains(SECRET));
    }

    // ── Nitelik değer şablonları (id ve test hata çıktısına yazılır) ─

    @ParameterizedTest(name = "{0}")
    @EnumSource(RulePath.class)
    @DisplayName("id ve test alanındaki {} ifadeleri ortamı okuyamaz")
    void attributeValueTemplatesAreSandboxed(RulePath path) {
        assumeProbeVariable();
        String probe = System.getenv(PROBE_VAR);

        var errors = validate(path, List.of(
                new SchematronCustomAssertion("/Invoice", "false()", "ID-AVT",
                        "{environment-variable('" + PROBE_VAR + "')}"),
                new SchematronCustomAssertion("/Invoice",
                        "false() and '{environment-variable(\"" + PROBE_VAR + "\")}' = ''", "TEST-AVT", null)));

        assertThat(messages(errors)).containsExactlyInAnyOrder("ID-AVT", "TEST-AVT");
        assertThat(errors).allSatisfy(error -> {
            assertThat(String.valueOf(error.ruleId())).doesNotContain(probe);
            assertThat(String.valueOf(error.test())).doesNotContain(probe);
        });
    }

    // ── Önceden derlenmiş Schematron XSL'leri ──────────────────────

    @Test
    @DisplayName("Pre-compiled Schematron XSL de sandbox'ta çalışır")
    void precompiledSchematronIsSandboxed() throws Exception {
        assumeProbeVariable();
        writePrecompiled("ENV=[<xsl:value-of select=\"environment-variable('" + PROBE_VAR + "')\"/>]");
        validator.reload();

        var errors = validator.validate(VALID_DOCUMENT.getBytes(StandardCharsets.UTF_8),
                SchematronValidationType.EARCHIVE_REPORT, null, List.of(), null, Map.of());

        assertThat(messages(errors)).containsExactly("ENV=[]");
    }

    @Test
    @DisplayName("Pre-compiled Schematron XSL dosya okuyamaz")
    void precompiledSchematronCannotReadFiles() throws Exception {
        writePrecompiled("OUT=[<xsl:value-of select=\"unparsed-text('" + secretTextUri + "')\"/>]");
        validator.reload();

        var errors = validator.validate(VALID_DOCUMENT.getBytes(StandardCharsets.UTF_8),
                SchematronValidationType.EARCHIVE_REPORT, null, List.of(), null, Map.of());

        assertBlocked(errors, SECRET);
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(RulePath.class)
    @DisplayName("Mesajdaki collation adresi Java sınıfı örneklemez")
    void collationClassIsNotInstantiated(RulePath path) {
        int before = SandboxInstantiationProbe.CREATED.get();

        var errors = validate(path, rule("/Invoice", "false()", CLASS_COLLATION_MESSAGE));

        assertBlocked(errors, "OUT=[");
        assertThat(SandboxInstantiationProbe.CREATED.get()).as("sınıf örneklenmemeli").isEqualTo(before);
    }

    // ── Kontrol: sandbox olmadan aynı kurallar gerçekten sızdırıyor ──

    /**
     * Sandbox testlerinin boşa geçmediğini gösterir: aynı kurallarla üretilen Schematron XSLT'si
     * sandbox'sız bir Saxon {@link Processor} ile derlenip çalıştırıldığında gizli içerik okunur.
     */
    @Nested
    @DisplayName("Kontrol: sandbox'sız Saxon")
    class WithoutSandbox {

        @Test
        @DisplayName("Ortam değişkeni okunur")
        void environmentVariableLeaks() throws Exception {
            assumeProbeVariable();

            String output = runWithoutSandbox(rule("/Invoice", "false()",
                    "ENV=[{{environment-variable('" + PROBE_VAR + "')}}]"));

            assertThat(output).contains("ENV=[" + System.getenv(PROBE_VAR) + "]");
        }

        static Stream<Arguments> leakingExpressions() {
            return fileReadingExpressions().filter(args -> !((String) args.get()[1]).contains("http"));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("leakingExpressions")
        @DisplayName("Dosya okuyan fonksiyonlar gizli içeriği döndürür")
        void fileReadingFunctionsLeak(String name, String expression, String forbidden) throws Exception {
            String output = runWithoutSandbox(rule("/Invoice", "false()",
                    "OUT=[{{" + withUris(expression) + "}}]"));

            assertThat(output).contains(forbidden);
        }

        @Test
        @DisplayName("Mesajdaki saxon:doc() dosya okur")
        void saxonDocLeaks() throws Exception {
            // Pipeline son XSLT'yi sandbox'ta derlediği için doğrudan çağrı burada derlenmez;
            // fonksiyonu çalışma zamanında bulan biçim aynı okumayı yapar.
            String output = runWithoutSandbox(rule("/Invoice", "false()",
                    SAXON_DOC_LOOKUP_MESSAGE.replace("{XML}", secretXmlUri)));

            assertThat(output).contains(SECRET);
        }

        @Test
        @DisplayName("Mesajdaki collation adresi Java sınıfı örnekler")
        void collationClassIsInstantiated() {
            int before = SandboxInstantiationProbe.CREATED.get();

            catchThrowable(() -> runWithoutSandbox(rule("/Invoice", "false()", CLASS_COLLATION_MESSAGE)));

            assertThat(SandboxInstantiationProbe.CREATED.get()).isGreaterThan(before);
        }

        @Test
        @DisplayName("/etc/hostname okunur")
        void etcHostnameLeaks() throws Exception {
            String hostname = readHostname();

            String output = runWithoutSandbox(rule("/Invoice", "false()",
                    "HOST=[{{unparsed-text('file:///etc/hostname')}}]"));

            assertThat(output.contains(hostname)).isTrue();
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("io.mersel.services.xslt.infrastructure.SchematronCustomRuleSandboxTest#oracleTests")
        @DisplayName("test ifadesi ortam ya da dosya hakkında bilgi verir")
        void testExpressionProbes(String name, String test) throws Exception {
            if (test.contains(PROBE_VAR)) {
                assumeProbeVariable();
            }

            assertThat(runWithoutSandbox(rule("/Invoice", withUris(test), LEAK_MARKER))).contains(LEAK_MARKER);
        }

        @Test
        @DisplayName("context ifadesindeki dosya okuması eşleşir")
        void fileReadInContextMatches() throws Exception {
            String output = runWithoutSandbox(rule(
                    "/Invoice[contains(unparsed-text('" + secretTextUri + "'), 'GIZLI')]", "false()", LEAK_MARKER));

            assertThat(output).contains(LEAK_MARKER);
        }

        @Test
        @DisplayName("id ve test alanındaki {} ifadeleri ortamı okur")
        void attributeValueTemplatesLeak() throws Exception {
            assumeProbeVariable();

            String output = runWithoutSandbox(List.of(
                    new SchematronCustomAssertion("/Invoice", "false()", "ID-AVT",
                            "{environment-variable('" + PROBE_VAR + "')}"),
                    new SchematronCustomAssertion("/Invoice",
                            "false() and '{environment-variable(\"" + PROBE_VAR + "\")}' = ''", "TEST-AVT", null)));

            assertThat(output)
                    .contains("ruleId=\"" + System.getenv(PROBE_VAR) + "\"")
                    .contains("'" + System.getenv(PROBE_VAR) + "' = ''");
        }

        private String runWithoutSandbox(List<SchematronCustomAssertion> rules) throws Exception {
            byte[] modified = validator.injectCustomRules(Files.readAllBytes(mainFile), rules, "kontrol");
            byte[] generatedXslt = runtimeCompiler.compileAndReturn(modified, mainFile.toUri()).generatedXslt();

            var processor = new Processor(false);
            var executable = processor.newXsltCompiler()
                    .compile(new StreamSource(new ByteArrayInputStream(generatedXslt)));
            var out = new StringWriter();
            executable.load30().transform(new StreamSource(new StringReader(VALID_DOCUMENT)),
                    processor.newSerializer(out));
            return out.toString();
        }
    }

    // ── Yardımcılar ────────────────────────────────────────────────

    private List<SchematronError> validate(RulePath path, List<SchematronCustomAssertion> rules) {
        return validate(path, rules, VALID_DOCUMENT, Map.of());
    }

    /**
     * Kuralları global ya da profil kuralı olarak tanımlayıp belgeyi UBL-TR Main tipinde doğrular.
     */
    private List<SchematronError> validate(RulePath path, List<SchematronCustomAssertion> rules,
                                           String document, Map<String, String> parameters) {
        byte[] source = document.getBytes(StandardCharsets.UTF_8);
        return switch (path) {
            case GLOBAL -> {
                validator.setGlobalCustomRules(Map.of(SchematronValidationType.UBLTR_MAIN, rules));
                validator.reload();
                yield validator.validate(source, SchematronValidationType.UBLTR_MAIN, null,
                        List.of(), null, parameters);
            }
            case PROFILE -> validator.validate(source, SchematronValidationType.UBLTR_MAIN, null,
                    rules, "test-profil", parameters);
        };
    }

    /**
     * Engellenen okuma doğrulamayı tek bir hata kaydıyla sonlandırır; yasaklı içerik hiçbir alanda yoktur.
     */
    private static void assertBlocked(List<SchematronError> errors, String forbidden) {
        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).message()).startsWith(BLOCKED_PREFIX);
        assertThat(errors).allSatisfy(error -> {
            assertThat(error.message()).doesNotContain(forbidden);
            assertThat(String.valueOf(error.ruleId())).doesNotContain(forbidden);
            assertThat(String.valueOf(error.test())).doesNotContain(forbidden);
        });
    }

    private void writePrecompiled(String errorContent) throws Exception {
        Path file = assetRoot.resolve(PRECOMPILED_PATH);
        Files.createDirectories(file.getParent());
        Files.writeString(file, """
                <xsl:stylesheet version="2.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
                    <xsl:template match="/"><Errors><Error>%s</Error></Errors></xsl:template>
                </xsl:stylesheet>
                """.formatted(errorContent));
    }

    private static List<SchematronCustomAssertion> rule(String context, String test, String message) {
        return List.of(new SchematronCustomAssertion(context, test, message, "TEST-Probe"));
    }

    private static List<String> messages(List<SchematronError> errors) {
        return errors.stream().map(SchematronError::message).toList();
    }

    private String withUris(String expression) {
        return expression
                .replace("{TXT}", secretTextUri)
                .replace("{XML}", secretXmlUri)
                .replace("{JSON}", secretJsonUri)
                .replace("{DIR}", dirUri);
    }

    private static void assumeProbeVariable() {
        String value = System.getenv(PROBE_VAR);
        assumeTrue(value != null && !value.isEmpty(), PROBE_VAR + " test görevinde tanımlanır");
    }

    /** İçerik yalnızca karşılaştırmada kullanılır, yazdırılmaz. */
    private static String readHostname() throws Exception {
        var hostnameFile = Path.of("/etc/hostname");
        assumeTrue(Files.isReadable(hostnameFile), "/etc/hostname yalnızca Linux'ta bulunur");
        String hostname = Files.readString(hostnameFile).strip();
        assumeTrue(hostname.length() >= 6, "hostname karşılaştırma için çok kısa");
        return hostname;
    }
}

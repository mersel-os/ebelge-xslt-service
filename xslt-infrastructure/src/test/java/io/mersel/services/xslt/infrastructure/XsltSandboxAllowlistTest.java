package io.mersel.services.xslt.infrastructure;

import io.mersel.services.xslt.application.enums.SchematronValidationType;
import io.mersel.services.xslt.application.enums.TransformType;
import io.mersel.services.xslt.application.interfaces.IXsltTransformer.TransformException;
import io.mersel.services.xslt.application.models.SchematronCustomAssertion;
import io.mersel.services.xslt.application.models.SchematronError;
import io.mersel.services.xslt.application.models.TransformRequest;
import io.mersel.services.xslt.application.models.TransformResult;
import io.mersel.services.xslt.infrastructure.diagnostics.XsltMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import net.sf.saxon.lib.AugmentedSource;
import net.sf.saxon.trans.XPathException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import org.xml.sax.InputSource;

import javax.xml.transform.Source;
import javax.xml.transform.sax.SAXSource;
import javax.xml.transform.stream.StreamSource;
import java.io.ByteArrayInputStream;
import java.io.StringReader;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Operatörün açtığı sandbox istisnalarının ({@code xslt.sandbox.*}) uçtan uca davranışı.
 * <p>
 * İstisnalar yalnızca varsayılan XSLT şablonlarına ve Schematron'a uygulanır: izinli dizindeki
 * dosyalar okunabilir; dizin dışı, ağ, listeleme, yazma, ortam değişkenleri ve JVM özellikleri kapalı
 * kalır. İstekle gelen ve belgeye gömülü XSLT aynı yapılandırmada da tam kısıtlıdır.
 */
@DisplayName("XSLT sandbox istisnaları")
class XsltSandboxAllowlistTest {

    private static final String PROBE_VAR = "XSLT_SANDBOX_PROBE";
    private static final String SECRET = "DIS-GIZLI-91b2";
    private static final String DEFAULT_OUTPUT = "VARSAYILAN-SABLON";
    private static final String TEMPLATE_PATH = "default_transformers/eInvoice_Base.xslt";
    private static final String MAIN_SCHEMATRON_PATH = "validator/ubl-tr-package/schematron/UBL-TR_Main_Schematron.xml";
    private static final String PRECOMPILED_PATH = "validator/earchive/schematron/earsiv_schematron.xsl";
    private static final String DOCUMENT = "<Invoice><ID>INV001</ID></Invoice>";

    @TempDir
    Path tempDir;

    private Path assets;
    private Path allowed;
    private Path outside;
    private AssetManager assetManager;
    private XsltSandboxPolicy policy;

    @BeforeEach
    void setUp() throws Exception {
        allowed = Files.createDirectories(tempDir.resolve("allowed"));
        outside = Files.createDirectories(tempDir.resolve("outside"));
        Files.writeString(allowed.resolve("codes.xml"), "<codes><code>KOD-123</code></codes>");
        Files.createDirectories(allowed.resolve("sub"));
        Files.writeString(allowed.resolve("sub/inner.xml"), "<i>ALT-DIZIN</i>");
        Files.writeString(allowed.resolve("note.txt"), "IZINLI-METIN");
        Files.writeString(allowed.resolve("data.json"), "{\"k\":\"IZINLI-JSON\"}");
        Files.writeString(allowed.resolve("module.xsl"), """
                <xsl:stylesheet version="3.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
                    <xsl:template name="from-module"><p>MODUL-CIKTISI</p></xsl:template>
                </xsl:stylesheet>""");
        // İzinli bir dosya, DOCTYPE ile dizin dışındaki bir dosyayı çekmeye çalışıyor.
        Files.writeString(allowed.resolve("xxe.xml"), "<!DOCTYPE r [<!ENTITY e SYSTEM \""
                + outside.resolve("secret.txt").toUri() + "\">]><r>[&e;]</r>");
        Files.writeString(outside.resolve("secret.txt"), SECRET);
        Files.writeString(outside.resolve("secret.xml"), "<s>" + SECRET + "</s>");
        Files.writeString(outside.resolve("module.xsl"), """
                <xsl:stylesheet version="3.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
                    <xsl:template name="from-module"><p>%s</p></xsl:template>
                </xsl:stylesheet>""".formatted(SECRET));

        assets = Files.createDirectories(tempDir.resolve("assets"));
        assetManager = mock(AssetManager.class);
        when(assetManager.assetExists(anyString()))
                .thenAnswer(invocation -> Files.exists(assets.resolve(invocation.<String>getArgument(0))));
        when(assetManager.resolveAssetOnDisk(anyString()))
                .thenAnswer(invocation -> assets.resolve(invocation.<String>getArgument(0)));
        when(assetManager.getAssetStream(anyString()))
                .thenAnswer(invocation -> Files.newInputStream(assets.resolve(invocation.<String>getArgument(0))));

        policy = XsltSandboxPolicy.of(List.of(allowed.toString()));
    }

    // ── Varsayılan şablon: izinli kaynaklar okunur ─────────────────

    @Test
    @DisplayName("Varsayılan şablon izinli dizindeki dosyaları okur")
    void defaultTemplateReadsAllowedFiles() throws Exception {
        String html = transformWithDefault(policy, stylesheet("""
                <p>DOC=[<xsl:value-of select="doc('%1$s/codes.xml')"/>]</p>
                <p>DOCUMENT=[<xsl:value-of select="document('%1$s/sub/inner.xml')"/>]</p>
                <p>TEXT=[<xsl:value-of select="unparsed-text('%1$s/note.txt')"/>]</p>
                <p>LINES=[<xsl:value-of select="string-join(unparsed-text-lines('%1$s/note.txt'), '')"/>]</p>
                <p>JSON=[<xsl:value-of select="json-doc('%1$s/data.json')?k"/>]</p>
                <p>DOC-AVAIL=[<xsl:value-of select="doc-available('%1$s/codes.xml')"/>]</p>
                <p>TEXT-AVAIL=[<xsl:value-of select="unparsed-text-available('%1$s/note.txt')"/>]</p>"""
                .formatted(allowedUri())));

        assertThat(html).contains("DOC=[KOD-123]", "DOCUMENT=[ALT-DIZIN]", "TEXT=[IZINLI-METIN]",
                "LINES=[IZINLI-METIN]", "JSON=[IZINLI-JSON]", "DOC-AVAIL=[true]", "TEXT-AVAIL=[true]");
    }

    @Test
    @DisplayName("Varsayılan şablon izinli dizinden xsl:include yapabilir")
    void defaultTemplateIncludesFromAllowedDirectory() throws Exception {
        String html = transformWithDefault(policy, """
                <xsl:stylesheet version="3.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
                    <xsl:include href="%s/module.xsl"/>
                    <xsl:template match="/"><html><body><xsl:call-template name="from-module"/></body></html></xsl:template>
                </xsl:stylesheet>""".formatted(allowedUri()));

        assertThat(html).contains("MODUL-CIKTISI");
    }

    @Test
    @DisplayName("İstisna tanımlıyken de ortam değişkenleri ve JVM özellikleri kapalı kalır")
    void environmentAndSystemPropertiesStayClosed() throws Exception {
        assumeProbeVariable();

        String html = transformWithDefault(policy, stylesheet("""
                <p>ENV=[<xsl:value-of select="environment-variable('%s')"/>]</p>
                <p>COUNT=[<xsl:value-of select="count(available-environment-variables())"/>]</p>
                <p>HOME-LEN=[<xsl:value-of select="string-length(system-property('user.home'))"/>]</p>"""
                .formatted(PROBE_VAR)));

        assertThat(html).contains("ENV=[]", "COUNT=[0]", "HOME-LEN=[0]");
    }

    // ── Varsayılan şablon: kapalı kalanlar ─────────────────────────

    static Stream<Arguments> blockedExpressions() {
        return Stream.of(
                Arguments.of("dizin dışı dosya", "unparsed-text('{OUT}/secret.txt')"),
                Arguments.of("dizin dışı doc", "string(doc('{OUT}/secret.xml'))"),
                Arguments.of(".. ile kaçış", "unparsed-text('{ALLOWED}/../outside/secret.txt')"),
                Arguments.of("http", "unparsed-text('http://127.0.0.1:9/sandbox')"),
                Arguments.of("collection", "string-join(for $d in collection('{ALLOWED}?select=*.xml') return string($d), ' ')"),
                Arguments.of("uri-collection", "string-join(uri-collection('{ALLOWED}'), ' ')"),
                Arguments.of("dizinin kendisi", "unparsed-text('{ALLOWED}')"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("blockedExpressions")
    @DisplayName("Varsayılan şablonda izinli dizin dışı, ağ ve listeleme kapalı kalır")
    void defaultTemplateStillBlocksEverythingElse(String name, String expression) {
        String xslt = stylesheet("<p>OUT=[<xsl:value-of select=\"" + withUris(expression) + "\"/>]</p>");

        assertSandboxBlocked(() -> transformWithDefault(policy, xslt));
    }

    @Test
    @DisplayName("İzinli dizindeki dizin dışını gösteren sembolik bağ okunamaz")
    void symlinkOutOfAllowedDirectoryIsBlocked() {
        Path link = allowed.resolve("link.txt");
        try {
            Files.createSymbolicLink(link, outside.resolve("secret.txt"));
        } catch (UnsupportedOperationException | java.io.IOException e) {
            assumeTrue(false, "Sembolik bağ oluşturulamıyor");
        }
        String text = stylesheet("<p>OUT=[<xsl:value-of select=\"unparsed-text('" + link.toUri() + "')\"/>]</p>");
        String xml = stylesheet("<p>OUT=[<xsl:value-of select=\"doc('" + link.toUri() + "')\"/>]</p>");

        assertSandboxBlocked(() -> transformWithDefault(policy, text));
        assertSandboxBlocked(() -> transformWithDefault(policy, xml));
    }

    @Test
    @DisplayName("Kök dizine giden sembolik bağ okunabilir dizin olarak kullanılamaz")
    void directoryResolvingToRootAllowsNothing() throws Exception {
        Path later = tempDir.resolve("later-root");
        var laterPolicy = XsltSandboxPolicy.of(List.of(later.toString()));
        try {
            Files.createSymbolicLink(later, Path.of("/"));
        } catch (UnsupportedOperationException | java.io.IOException e) {
            assumeTrue(false, "Sembolik bağ oluşturulamıyor");
        }
        String xslt = stylesheet("<p>OUT=[<xsl:value-of select=\"unparsed-text('"
                + outside.resolve("secret.txt").toUri() + "')\"/>]</p>");

        assertSandboxBlocked(() -> transformWithDefault(laterPolicy, xslt));
    }

    @Test
    @DisplayName("İstisna tanımlıyken de transform() yoktur")
    void transformStaysUnavailableWithAllowlist() throws Exception {
        String html = transformWithDefault(policy, stylesheet(
                "<p>FA=[<xsl:value-of select=\"function-available('transform', 1)\"/>]</p>"));

        assertThat(html).contains("FA=[false]");
    }

    @Test
    @DisplayName("İstisna tanımlıyken de saxon:doc() yoktur, doc() izinli dosyayı okur")
    void saxonDocStaysUnavailableWithAllowlist() throws Exception {
        String html = transformWithDefault(policy, stylesheet("""
                <p xmlns:saxon="http://saxon.sf.net/">FA=[<xsl:value-of select="function-available('saxon:doc', 2)"/>]</p>
                <p>LOOKUP=[<xsl:value-of select="exists(function-lookup(QName('http://saxon.sf.net/', 'doc'), 2))"/>]</p>
                <p>DOC=[<xsl:value-of select="doc('%s/codes.xml')"/>]</p>""".formatted(allowedUri())));

        assertThat(html).contains("FA=[false]", "LOOKUP=[false]", "DOC=[KOD-123]");
    }

    @Test
    @DisplayName("Varsayılan şablon izinli dosyayı da saxon:doc() ile okuyamaz")
    void defaultTemplateCannotUseSaxonDocEvenForAllowedFile() throws Exception {
        String xslt = stylesheet("<p>OUT=[<xsl:value-of select=\"Q{http://saxon.sf.net/}doc('"
                + allowedUri() + "/codes.xml', map{})\"/>]</p>");
        writeTemplate(xslt);

        var transformer = newTransformer(policy);
        var reload = transformer.reload();

        assertThat(reload.errors()).isNotEmpty();
        assertThatThrownBy(() -> transformer.transform(request()))
                .isInstanceOf(TransformException.class)
                .satisfies(e -> assertThat(e.getMessage()).doesNotContain("KOD-123"));
    }

    @Test
    @DisplayName("İstekle gelen XSLT izinli dosyayı saxon:doc() ile okuyamaz, varsayılana düşer")
    void requestXsltCannotUseSaxonDocForAllowedFile() throws Exception {
        writeTemplate(stylesheet("<p>" + DEFAULT_OUTPUT + "</p>"));
        var request = request();
        request.setTransformer(stylesheet("<p>OUT=[<xsl:value-of select=\"Q{http://saxon.sf.net/}doc('"
                + allowedUri() + "/codes.xml', map{})\"/>]</p>").getBytes(StandardCharsets.UTF_8));

        var result = transformer(policy).transform(request);

        assertThat(result.isDefaultXslUsed()).isTrue();
        assertThat(result.getCustomXsltError()).isNotBlank();
        assertThat(html(result)).contains(DEFAULT_OUTPUT).doesNotContain("KOD-123");
    }

    // ── Source resolver kapısı ─────────────────────────────────────

    @Test
    @DisplayName("Source resolver URI'den açılacak kaynağı reddeder")
    void sourceResolverRefusesUriOnlySources() throws Exception {
        String secretUri = outside.resolve("secret.xml").toUri().toString();

        for (var sandboxPolicy : List.of(XsltSandboxPolicy.STRICT, policy)) {
            var config = XsltSandbox.newProcessor(sandboxPolicy).getUnderlyingConfiguration();
            var resolver = config.getSourceResolver();

            for (Source source : List.of(
                    new StreamSource(secretUri),
                    new SAXSource(new InputSource(secretUri)),
                    AugmentedSource.makeAugmentedSource(new StreamSource(secretUri)))) {
                assertThatThrownBy(() -> resolver.resolveSource(source, config))
                        .isInstanceOf(XPathException.class)
                        .hasMessageContaining("Güvenlik")
                        .hasMessageNotContaining(SECRET);
            }
        }
    }

    @Test
    @DisplayName("Source resolver ağa bağlanmaz")
    void sourceResolverDoesNotConnect() throws Exception {
        var config = XsltSandbox.newProcessor(policy).getUnderlyingConfiguration();

        try (var listener = new SaxonXsltTransformerSandboxTest.ConnectionCounter()) {
            assertThatThrownBy(() -> config.getSourceResolver().resolveSource(new StreamSource(listener.uri()), config))
                    .isInstanceOf(XPathException.class)
                    .hasMessageContaining("Güvenlik");

            listener.assertNoConnection();
        }
    }

    @Test
    @DisplayName("Source resolver izinli dosyayı kapıdan açar")
    void sourceResolverOpensAllowedFileThroughGate() throws Exception {
        var processor = XsltSandbox.newProcessor(policy);
        var config = processor.getUnderlyingConfiguration();

        var resolved = config.getSourceResolver().resolveSource(
                new StreamSource(allowed.resolve("codes.xml").toUri().toString()), config);

        assertThat(processor.newDocumentBuilder().build(resolved).getStringValue()).isEqualTo("KOD-123");
    }

    @Test
    @DisplayName("Source resolver akış taşıyan kaynağı değiştirmeden geçirir")
    void sourceResolverPassesStreamBackedSources() throws Exception {
        // System id dizin dışını gösterse de içerik akıştan okunur; dosya açılmaz.
        String outsideUri = outside.resolve("secret.xml").toUri().toString();
        byte[] content = "<x>AKIS-ICERIGI</x>".getBytes(StandardCharsets.UTF_8);

        for (var sandboxPolicy : List.of(XsltSandboxPolicy.STRICT, policy)) {
            var processor = XsltSandbox.newProcessor(sandboxPolicy);
            var config = processor.getUnderlyingConfiguration();
            var input = new InputSource(new ByteArrayInputStream(content));
            input.setSystemId(outsideUri);

            for (Source source : List.of(
                    new StreamSource(new ByteArrayInputStream(content), outsideUri),
                    new StreamSource(new StringReader("<x>AKIS-ICERIGI</x>"), outsideUri),
                    new SAXSource(input))) {
                var resolved = config.getSourceResolver().resolveSource(source, config);

                assertThat(processor.newDocumentBuilder().build(resolved).getStringValue()).isEqualTo("AKIS-ICERIGI");
            }
        }
    }

    @Test
    @DisplayName("İzinli dosyadaki DOCTYPE dizin dışını çekemez")
    void allowedFileCannotPullOutsideFilesThroughEntities() throws Exception {
        String html = transformWithDefault(policy, stylesheet(
                "<p>XXE=<xsl:value-of select=\"doc('" + allowedUri() + "/xxe.xml')\"/></p>"));

        assertThat(html).contains("XXE=[]").doesNotContain(SECRET);
    }

    @Test
    @DisplayName("Dizin dışından xsl:include derlenmez")
    void includeOutsideAllowedDirectoryDoesNotCompile() throws Exception {
        String xslt = """
                <xsl:stylesheet version="3.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
                    <xsl:include href="%s"/>
                    <xsl:template match="/"><html><body><xsl:call-template name="from-module"/></body></html></xsl:template>
                </xsl:stylesheet>""".formatted(outside.resolve("module.xsl").toUri());
        writeTemplate(xslt);

        var reload = newTransformer(policy).reload();

        assertThat(reload.errors()).singleElement().asString().contains("Güvenlik").doesNotContain(SECRET);
    }

    @Test
    @DisplayName("xsl:result-document izinli dizine de yazamaz")
    void resultDocumentCannotWriteIntoAllowedDirectory() throws Exception {
        Path target = allowed.resolve("written.txt");
        String xslt = stylesheet("""
                <xsl:result-document href="%s" method="text">YAZILDI</xsl:result-document><p>RD</p>"""
                .formatted(target.toUri()));

        writeTemplate(xslt);
        var reload = newTransformer(policy).reload();

        assertThat(reload.errors()).isNotEmpty();
        assertThat(target).doesNotExist();
    }

    @Test
    @DisplayName("İstisna tanımlanmadıysa varsayılan şablon izinli dizini de okuyamaz")
    void strictPolicyBlocksDefaultTemplate() {
        String xslt = stylesheet("<p>DOC=[<xsl:value-of select=\"doc('" + allowedUri() + "/codes.xml')\"/>]</p>");

        assertSandboxBlocked(() -> transformWithDefault(XsltSandboxPolicy.STRICT, xslt));
    }

    // ── İstekle gelen ve gömülü XSLT tam kısıtlı kalır ─────────────

    static Stream<Arguments> allowedReads() {
        return Stream.of(
                Arguments.of("doc", "<p>OUT=[<xsl:value-of select=\"doc('{ALLOWED}/codes.xml')\"/>]</p>"),
                Arguments.of("unparsed-text", "<p>OUT=[<xsl:value-of select=\"unparsed-text('{ALLOWED}/note.txt')\"/>]</p>"),
                Arguments.of("json-doc", "<p>OUT=[<xsl:value-of select=\"json-doc('{ALLOWED}/data.json')?k\"/>]</p>"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("allowedReads")
    @DisplayName("İstekle gelen XSLT izinli dizini okuyamaz, varsayılana düşer")
    void requestXsltCannotUseAllowlist(String name, String body) throws Exception {
        writeTemplate(stylesheet("<p>" + DEFAULT_OUTPUT + "</p>"));
        var request = request();
        request.setTransformer(stylesheet(withUris(body)).getBytes(StandardCharsets.UTF_8));

        var result = transformer(policy).transform(request);

        assertThat(result.isDefaultXslUsed()).isTrue();
        assertThat(result.getCustomXsltError()).contains("Güvenlik");
        assertThat(html(result)).contains(DEFAULT_OUTPUT)
                .doesNotContain("KOD-123").doesNotContain("IZINLI-METIN").doesNotContain("IZINLI-JSON");
    }

    @Test
    @DisplayName("İstekle gelen XSLT izinli dizinden xsl:include yapamaz")
    void requestXsltCannotIncludeFromAllowlist() throws Exception {
        writeTemplate(stylesheet("<p>" + DEFAULT_OUTPUT + "</p>"));
        var request = request();
        request.setTransformer("""
                <xsl:stylesheet version="3.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
                    <xsl:include href="%s/module.xsl"/>
                    <xsl:template match="/"><html><body><xsl:call-template name="from-module"/></body></html></xsl:template>
                </xsl:stylesheet>""".formatted(allowedUri()).getBytes(StandardCharsets.UTF_8));

        var result = transformer(policy).transform(request);

        assertThat(result.isDefaultXslUsed()).isTrue();
        assertThat(result.getCustomXsltError()).contains("Güvenlik");
        assertThat(html(result)).doesNotContain("MODUL-CIKTISI");
    }

    @Test
    @DisplayName("Gömülü XSLT izinli dizini okuyamaz, varsayılana düşer")
    void embeddedXsltCannotUseAllowlist() throws Exception {
        writeTemplate(stylesheet("<p>" + DEFAULT_OUTPUT + "</p>"));
        String embedded = stylesheet("<p>DOC=[<xsl:value-of select=\"doc('" + allowedUri() + "/codes.xml')\"/>]</p>");
        var request = request();
        request.setDocument(embeddedInvoice(embedded).getBytes(StandardCharsets.UTF_8));
        request.setUseEmbeddedXslt(true);

        var result = transformer(policy).transform(request);

        assertThat(result.isDefaultXslUsed()).isTrue();
        assertThat(result.isEmbeddedXsltUsed()).isFalse();
        assertThat(result.getCustomXsltError()).contains("Güvenlik");
        assertThat(html(result)).contains(DEFAULT_OUTPUT).doesNotContain("KOD-123");
    }

    // ── Schematron ─────────────────────────────────────────────────

    @Test
    @DisplayName("Özel Schematron kuralı izinli dosyayı okur, ortam değişkeni kapalı kalır")
    void schematronRuleUsesAllowlist() throws Exception {
        assumeProbeVariable();
        var validator = schematronValidator(policy);
        var rule = new SchematronCustomAssertion("/Invoice", "false()",
                "DOC=[{{string(doc('" + allowedUri() + "/codes.xml'))}}] ENV=[{{environment-variable('"
                        + PROBE_VAR + "')}}]", "TEST-Allow");

        for (var errors : validateBothPaths(validator, rule)) {
            assertThat(errors).extracting(SchematronError::message).containsExactly("DOC=[KOD-123] ENV=[]");
        }
    }

    @Test
    @DisplayName("Özel Schematron kuralı dizin dışını okuyamaz")
    void schematronRuleStillBlocksOutside() throws Exception {
        var validator = schematronValidator(policy);
        var rule = new SchematronCustomAssertion("/Invoice", "false()",
                "OUT=[{{unparsed-text('" + outside.resolve("secret.txt").toUri() + "')}}]", "TEST-Out");

        for (var errors : validateBothPaths(validator, rule)) {
            assertThat(errors).hasSize(1);
            assertThat(errors.get(0).message()).startsWith("Schematron doğrulama hatası").doesNotContain(SECRET);
        }
    }

    @Test
    @DisplayName("Pre-compiled Schematron XSL izinli dosyayı okur")
    void precompiledSchematronUsesAllowlist() throws Exception {
        Path file = assets.resolve(PRECOMPILED_PATH);
        Files.createDirectories(file.getParent());
        Files.writeString(file, """
                <xsl:stylesheet version="2.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
                    <xsl:template match="/"><Errors><Error>DOC=[<xsl:value-of select="doc('%s/codes.xml')"/>]</Error></Errors></xsl:template>
                </xsl:stylesheet>""".formatted(allowedUri()));
        var validator = schematronValidator(policy);
        validator.reload();

        var errors = validator.validate(DOCUMENT.getBytes(StandardCharsets.UTF_8),
                SchematronValidationType.EARCHIVE_REPORT, null, List.of(), null, Map.of());

        assertThat(errors).extracting(SchematronError::message).containsExactly("DOC=[KOD-123]");
    }

    // ── Yardımcılar ────────────────────────────────────────────────

    private String transformWithDefault(XsltSandboxPolicy sandboxPolicy, String template) throws Exception {
        writeTemplate(template);
        var request = request();
        return html(transformer(sandboxPolicy).transform(request));
    }

    private SaxonXsltTransformer transformer(XsltSandboxPolicy sandboxPolicy) {
        var transformer = newTransformer(sandboxPolicy);
        transformer.reload();
        return transformer;
    }

    private SaxonXsltTransformer newTransformer(XsltSandboxPolicy sandboxPolicy) {
        return new SaxonXsltTransformer(assetManager, new WatermarkService(assetManager), new HtmlSanitizer(),
                new EmbeddedXsltExtractor(), new XsltMetrics(new SimpleMeterRegistry()), sandboxPolicy);
    }

    /**
     * Varsayılan şablon sandbox tarafından engellenince dönüşüm 422'ye karşılık gelen hatayla biter;
     * neden sandbox mesajıdır (bağlantı ya da bulunamama hatası değil) ve gizli içerik görünmez.
     */
    private static void assertSandboxBlocked(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call)
                .isInstanceOf(TransformException.class)
                .satisfies(e -> assertThat(e.getMessage()).contains("Güvenlik").doesNotContain(SECRET));
    }

    private void writeTemplate(String xslt) throws Exception {
        Path file = assets.resolve(TEMPLATE_PATH);
        Files.createDirectories(file.getParent());
        Files.writeString(file, xslt);
    }

    private static TransformRequest request() {
        var request = new TransformRequest();
        request.setTransformType(TransformType.INVOICE);
        request.setDocument(DOCUMENT.getBytes(StandardCharsets.UTF_8));
        return request;
    }

    private SaxonSchematronValidator schematronValidator(XsltSandboxPolicy sandboxPolicy) throws Exception {
        Path main = assets.resolve(MAIN_SCHEMATRON_PATH);
        Files.createDirectories(main.getParent());
        Files.writeString(main, """
                <?xml version="1.0" encoding="UTF-8"?>
                <sch:schema xmlns:sch="http://purl.oclc.org/dsdl/schematron">
                    <sch:pattern id="main">
                        <sch:rule context="/Invoice"><sch:assert test="ID" id="TEST-MainId">ID zorunlu</sch:assert></sch:rule>
                    </sch:pattern>
                </sch:schema>
                """);
        var metrics = new XsltMetrics(new SimpleMeterRegistry());
        var compiler = new SchematronRuntimeCompiler(metrics, sandboxPolicy);
        Method init = SchematronRuntimeCompiler.class.getDeclaredMethod("init");
        init.setAccessible(true);
        init.invoke(compiler);
        var validator = new SaxonSchematronValidator(assetManager, compiler, metrics, sandboxPolicy);
        validator.init();
        return validator;
    }

    /** Aynı kuralı önce global, sonra profil kuralı olarak dener. */
    private static List<List<SchematronError>> validateBothPaths(SaxonSchematronValidator validator,
                                                                 SchematronCustomAssertion rule) {
        byte[] source = DOCUMENT.getBytes(StandardCharsets.UTF_8);
        validator.setGlobalCustomRules(Map.of(SchematronValidationType.UBLTR_MAIN, List.of(rule)));
        validator.reload();
        var global = validator.validate(source, SchematronValidationType.UBLTR_MAIN, null, List.of(), null, Map.of());

        validator.setGlobalCustomRules(Map.of());
        validator.reload();
        var profile = validator.validate(source, SchematronValidationType.UBLTR_MAIN, null,
                List.of(rule), "test-profil", Map.of());
        return List.of(global, profile);
    }

    private static String stylesheet(String body) {
        return """
                <xsl:stylesheet version="3.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
                    <xsl:template match="/">
                        <html><head></head><body>"""
                + body
                + """
                </body></html>
                    </xsl:template>
                </xsl:stylesheet>""";
    }

    private static String embeddedInvoice(String xslt) {
        String encoded = Base64.getEncoder().encodeToString(xslt.getBytes(StandardCharsets.UTF_8));
        return "<Invoice xmlns=\"urn:oasis:names:specification:ubl:schema:xsd:Invoice-2\""
                + " xmlns:cac=\"urn:oasis:names:specification:ubl:schema:xsd:CommonAggregateComponents-2\""
                + " xmlns:cbc=\"urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2\">"
                + "<cbc:ID>INV001</cbc:ID><cac:AdditionalDocumentReference><cac:Attachment>"
                + "<cbc:EmbeddedDocumentBinaryObject filename=\"ornek.xslt\" encodingCode=\"Base64\">"
                + encoded + "</cbc:EmbeddedDocumentBinaryObject></cac:Attachment></cac:AdditionalDocumentReference>"
                + "</Invoice>";
    }

    /** İzinli dizinin sonda eğik çizgisiz file: URI'si. */
    private String allowedUri() {
        String uri = allowed.toUri().toString();
        return uri.endsWith("/") ? uri.substring(0, uri.length() - 1) : uri;
    }

    private String withUris(String expression) {
        String outsideUri = outside.toUri().toString();
        return expression
                .replace("{ALLOWED}", allowedUri())
                .replace("{OUT}", outsideUri.endsWith("/") ? outsideUri.substring(0, outsideUri.length() - 1) : outsideUri);
    }

    private static String html(TransformResult result) {
        return new String(result.getHtmlContent(), StandardCharsets.UTF_8);
    }

    private static void assumeProbeVariable() {
        String value = System.getenv(PROBE_VAR);
        assumeTrue(value != null && !value.isEmpty(), PROBE_VAR + " test görevinde tanımlanır");
    }
}

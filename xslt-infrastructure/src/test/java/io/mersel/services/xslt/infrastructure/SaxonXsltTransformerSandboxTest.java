package io.mersel.services.xslt.infrastructure;

import io.mersel.services.xslt.application.enums.TransformType;
import io.mersel.services.xslt.application.interfaces.IXsltTransformer.TransformException;
import io.mersel.services.xslt.application.models.TransformRequest;
import io.mersel.services.xslt.application.models.TransformResult;
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
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * XSLT sandbox testleri.
 * <p>
 * Her saldırı üç dönüşüm yolunda denenir: kullanıcının yüklediği XSLT, belgeye gömülü XSLT ve
 * varsayılan şablon. Özel/gömülü XSLT engellendiğinde servis varsayılan şablona düşer ve hatayı
 * {@code customXsltError} alanında (HTTP'de {@code X-Xslt-Custom-Error}) bildirir; varsayılan şablonun
 * kendisi engellenirse {@link TransformException} fırlar (HTTP 422).
 */
@DisplayName("SaxonXsltTransformer sandbox")
class SaxonXsltTransformerSandboxTest {

    private static final String SECRET = "SANDBOX-GIZLI-ICERIK-7f3a";
    private static final String DEFAULT_OUTPUT = "VARSAYILAN-SABLON";
    private static final String DEFAULT_TEMPLATE_PATH = "default_transformers/eInvoice_Base.xslt";

    private static final String UBL_NAMESPACES = """
            xmlns="urn:oasis:names:specification:ubl:schema:xsd:Invoice-2" \
            xmlns:cac="urn:oasis:names:specification:ubl:schema:xsd:CommonAggregateComponents-2" \
            xmlns:cbc="urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2\"""";

    enum TemplatePath { CUSTOM, EMBEDDED, DEFAULT }

    @TempDir
    Path tempDir;

    private String secretTextUri;
    private String secretXmlUri;
    private String secretJsonUri;
    private String leakDtdUri;
    private String dirUri;

    @BeforeEach
    void setUp() throws IOException {
        secretTextUri = Files.writeString(tempDir.resolve("secret.txt"), SECRET).toUri().toString();
        secretXmlUri = Files.writeString(tempDir.resolve("secret.xml"), "<s>" + SECRET + "</s>").toUri().toString();
        secretJsonUri = Files.writeString(tempDir.resolve("secret.json"), "\"" + SECRET + "\"").toUri().toString();
        leakDtdUri = Files.writeString(tempDir.resolve("leak.dtd"),
                "<!ATTLIST Invoice leak CDATA \"" + SECRET + "\">").toUri().toString();
        dirUri = tempDir.toUri().toString();
    }

    // ── Ortam değişkenleri ─────────────────────────────────────────

    @ParameterizedTest(name = "{0}")
    @EnumSource(TemplatePath.class)
    @DisplayName("environment-variable() her zaman boş döner")
    void environmentVariableIsAlwaysEmpty(TemplatePath path) throws Exception {
        String env = System.getenv("PATH");
        assumeTrue(env != null && !env.isEmpty(), "PATH ortam değişkeni tanımlı olmalı");

        // Değerin kendisi asla çıktıya yazılmaz; yalnızca uzunluğu ve sayısı ölçülür.
        var result = transform(path, stylesheet("""
                <p>ENV-LEN=<xsl:value-of select="string-length(environment-variable('PATH'))"/></p>
                <p>ENV-COUNT=<xsl:value-of select="count(available-environment-variables())"/></p>"""));

        assertOwnTemplateUsed(path, result);
        assertThat(html(result)).contains("ENV-LEN=0", "ENV-COUNT=0");
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(TemplatePath.class)
    @DisplayName("system-property() JVM özelliklerini döndürmez, XSLT özellikleri çalışır")
    void systemPropertyDoesNotExposeJvmProperties(TemplatePath path) throws Exception {
        var result = transform(path, stylesheet("""
                <p>HOME-LEN=<xsl:value-of select="string-length(system-property('user.home'))"/></p>
                <p>XSL=<xsl:value-of select="system-property('xsl:version')"/></p>"""));

        assertOwnTemplateUsed(path, result);
        assertThat(html(result)).contains("HOME-LEN=0", "XSL=3.0");
    }

    // ── Dosya ve ağ okuma ──────────────────────────────────────────

    static Stream<Arguments> fileReadingExpressions() {
        var expressions = Stream.of(
                Arguments.of("unparsed-text", "unparsed-text('{TXT}')", SECRET),
                Arguments.of("unparsed-text-lines", "string-join(unparsed-text-lines('{TXT}'), ' ')", SECRET),
                Arguments.of("json-doc", "string(json-doc('{JSON}'))", SECRET),
                Arguments.of("collection",
                        "string-join(for $d in collection('{DIR}?select=*.xml') return string($d), ' ')", SECRET),
                Arguments.of("uri-collection", "string-join(uri-collection('{DIR}'), ' ')", "secret.txt"),
                Arguments.of("doc", "string(doc('{XML}'))", SECRET),
                Arguments.of("unparsed-text http", "unparsed-text('http://127.0.0.1:9/sandbox')", "sandbox-http"));
        return expressions.flatMap(args -> Stream.of(TemplatePath.values())
                .map(path -> Arguments.of(path, args.get()[0], args.get()[1], args.get()[2])));
    }

    @ParameterizedTest(name = "{0} / {1}")
    @MethodSource("fileReadingExpressions")
    @DisplayName("Dosya/ağ okuyan fonksiyonlar engellenir")
    void fileReadingFunctionsAreBlocked(TemplatePath path, String name, String expression, String forbidden)
            throws Exception {
        String xslt = stylesheet("<p>OUT=[<xsl:value-of select=\"" + withUris(expression) + "\"/>]</p>");

        assertBlocked(path, xslt, forbidden, "Güvenlik");
    }

    @Test
    @DisplayName("Engelleme nedeni X-Xslt-Custom-Error header'ına yazılabilir (ISO-8859-1)")
    void blockedReasonIsHeaderSafe() throws Exception {
        // Tomcat, ISO-8859-1 dışı karakter (ı, ş, ğ, uzun tire) içeren header'ı yanıta hiç yazmıyor.
        var result = transform(TemplatePath.CUSTOM, stylesheet(
                "<p><xsl:value-of select=\"unparsed-text('" + secretTextUri + "')\"/></p>"));

        assertThat(result.getCustomXsltError())
                .contains("Güvenlik", "XSLT sandbox")
                .matches("[\\x20-\\xFF]+");
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(TemplatePath.class)
    @DisplayName("unparsed-text-available() hata vermeden false döner")
    void unparsedTextAvailableReturnsFalse(TemplatePath path) throws Exception {
        var result = transform(path, stylesheet(
                "<p>AVAIL=<xsl:value-of select=\"unparsed-text-available('" + secretTextUri + "')\"/></p>"));

        assertOwnTemplateUsed(path, result);
        assertThat(html(result)).contains("AVAIL=false");
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(TemplatePath.class)
    @DisplayName("/etc/hostname içeriği dönmez")
    void etcHostnameIsNotReturned(TemplatePath path) throws Exception {
        var hostnameFile = Path.of("/etc/hostname");
        assumeTrue(Files.isReadable(hostnameFile), "/etc/hostname yalnızca Linux'ta bulunur");
        String hostname = Files.readString(hostnameFile).strip();
        // Çok kısa bir değer HTML içinde tesadüfen geçebilir; anlamlı bir karşılaştırma için uzunluk şartı.
        assumeTrue(hostname.length() >= 6, "hostname karşılaştırma için çok kısa");

        String xslt = stylesheet("<p>HOST=[<xsl:value-of select=\"unparsed-text('file:///etc/hostname')\"/>]</p>");

        assertBlocked(path, xslt, hostname, "Güvenlik");
    }

    // ── Dosya yazma ────────────────────────────────────────────────

    @ParameterizedTest(name = "{0}")
    @EnumSource(TemplatePath.class)
    @DisplayName("xsl:result-document dosya yazamaz (sabit href)")
    void resultDocumentCannotWriteWithLiteralHref(TemplatePath path) throws Exception {
        Path target = tempDir.resolve("written-literal.txt");
        String xslt = stylesheet("""
                <xsl:result-document href="%s" method="text">%s</xsl:result-document><p>RD</p>"""
                .formatted(target.toUri(), SECRET));

        assertBlocked(path, xslt, SECRET, null);
        assertThat(target).doesNotExist();
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(TemplatePath.class)
    @DisplayName("xsl:result-document dosya yazamaz (çalışma zamanında hesaplanan href)")
    void resultDocumentCannotWriteWithComputedHref(TemplatePath path) throws Exception {
        Path target = tempDir.resolve("written-avt.txt");
        String xslt = stylesheet("""
                <xsl:variable name="target" select="'%s'"/>
                <xsl:result-document href="{$target}" method="text">%s</xsl:result-document><p>RD</p>"""
                .formatted(target.toUri(), SECRET));

        assertBlocked(path, xslt, SECRET, null);
        assertThat(target).doesNotExist();
    }

    // ── XXE ────────────────────────────────────────────────────────

    @ParameterizedTest(name = "{0}")
    @EnumSource(value = TemplatePath.class, names = {"CUSTOM", "DEFAULT"})
    @DisplayName("Girdi XML'indeki harici varlık çözülmez")
    void externalEntityInDocumentIsNotResolved(TemplatePath path) throws Exception {
        String document = invoiceWithDoctype(
                "<!DOCTYPE Invoice [<!ENTITY xxe SYSTEM \"" + secretTextUri + "\">]>", "&xxe;");

        var result = transform(path, ECHO_DOCUMENT, document);

        assertOwnTemplateUsed(path, result);
        assertThat(html(result)).contains("NOTE=[]").doesNotContain(SECRET);
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(value = TemplatePath.class, names = {"CUSTOM", "DEFAULT"})
    @DisplayName("Girdi XML'indeki harici DTD yüklenmez")
    void externalDtdIsNotLoaded(TemplatePath path) throws Exception {
        String document = invoiceWithDoctype("<!DOCTYPE Invoice SYSTEM \"" + leakDtdUri + "\">", "not");

        var result = transform(path, ECHO_DOCUMENT, document);

        assertOwnTemplateUsed(path, result);
        assertThat(html(result)).contains("LEAK=[]").doesNotContain(SECRET);
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(value = TemplatePath.class, names = {"CUSTOM", "DEFAULT"})
    @DisplayName("Girdi XML'indeki harici parametre varlığı yüklenmez")
    void externalParameterEntityIsNotLoaded(TemplatePath path) throws Exception {
        String document = invoiceWithDoctype(
                "<!DOCTYPE Invoice [<!ENTITY % ext SYSTEM \"" + leakDtdUri + "\"> %ext;]>", "not");

        var result = transform(path, ECHO_DOCUMENT, document);

        assertOwnTemplateUsed(path, result);
        assertThat(html(result)).contains("LEAK=[]").doesNotContain(SECRET);
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(TemplatePath.class)
    @DisplayName("XSLT içindeki harici varlık çözülmez")
    void externalEntityInStylesheetIsNotResolved(TemplatePath path) throws Exception {
        String xslt = """
                <?xml version="1.0" encoding="UTF-8"?>
                <!DOCTYPE xsl:stylesheet [<!ENTITY xxe SYSTEM "%s">]>
                <xsl:stylesheet version="3.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
                    <xsl:template match="/">
                        <html><head></head><body><p>XSL=[&xxe;]</p></body></html>
                    </xsl:template>
                </xsl:stylesheet>""".formatted(secretTextUri);

        var result = transform(path, xslt);

        assertOwnTemplateUsed(path, result);
        assertThat(html(result)).contains("XSL=[]").doesNotContain(SECRET);
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(TemplatePath.class)
    @DisplayName("parse-xml() harici varlık çözmez")
    void parseXmlDoesNotResolveExternalEntities(TemplatePath path) throws Exception {
        String inner = "&lt;!DOCTYPE x [&lt;!ENTITY e SYSTEM &quot;" + secretTextUri
                + "&quot;&gt;]&gt;&lt;x&gt;&amp;e;&lt;/x&gt;";
        var result = transform(path, stylesheet(
                "<p>PX=[<xsl:value-of select=\"parse-xml('" + inner + "')\"/>]</p>"));

        assertOwnTemplateUsed(path, result);
        assertThat(html(result)).contains("PX=[]").doesNotContain(SECRET);
    }

    // ── Normal dönüşümler ──────────────────────────────────────────

    @ParameterizedTest(name = "{0}")
    @EnumSource(value = TemplatePath.class, names = {"CUSTOM", "DEFAULT"})
    @DisplayName("Girdi XML'indeki iç DTD varlıkları çalışmaya devam eder")
    void internalEntitiesInDocumentStillWork(TemplatePath path) throws Exception {
        String document = invoiceWithDoctype(
                "<!DOCTYPE Invoice [<!ENTITY ic \"dahili-varlik\">]>", "&ic;");

        var result = transform(path, ECHO_DOCUMENT, document);

        assertOwnTemplateUsed(path, result);
        assertThat(html(result)).contains("NOTE=[dahili-varlik]");
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(TemplatePath.class)
    @DisplayName("xsl:result-document href olmadan ana çıktıya yazabilir")
    void resultDocumentWithoutHrefWritesPrincipalOutput(TemplatePath path) throws Exception {
        var result = transform(path, """
                <xsl:stylesheet version="3.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
                    <xsl:template match="/">
                        <xsl:result-document><html><head></head><body><p>ANA-CIKTI</p></body></html></xsl:result-document>
                    </xsl:template>
                </xsl:stylesheet>""");

        assertOwnTemplateUsed(path, result);
        assertThat(html(result)).contains("ANA-CIKTI");
    }

    // ── Yeni Saxon yapılandırması kurabilen fonksiyonlar ───────────

    @ParameterizedTest(name = "{0}")
    @EnumSource(TemplatePath.class)
    @DisplayName("transform() ve load-xquery-module() sandbox'ta yoktur")
    void configurationCreatingFunctionsAreUnavailable(TemplatePath path) throws Exception {
        var result = transform(path, stylesheet("""
                <p>FA=[<xsl:value-of select="function-available('transform', 1), function-available('load-xquery-module', 1), function-available('load-xquery-module', 2)"/>]</p>
                <p>LOOKUP=[<xsl:value-of select="exists(function-lookup(QName('http://www.w3.org/2005/xpath-functions', 'transform'), 1))"/>]</p>
                <p>CONTROL=[<xsl:value-of select="function-available('parse-xml', 1)"/>]</p>"""));

        assertOwnTemplateUsed(path, result);
        assertThat(html(result)).contains("FA=[false false false]", "LOOKUP=[false]", "CONTROL=[true]");
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(TemplatePath.class)
    @DisplayName("transform() ile yeni yapılandırmada dosya okunamaz")
    void transformWithVendorConfigurationIsBlocked(TemplatePath path) {
        assertBlocked(path, stylesheet(nestedTransformReading("transform")), SECRET, null);
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(TemplatePath.class)
    @DisplayName("function-lookup ile transform() çağrılamaz")
    void transformThroughFunctionLookupIsBlocked(TemplatePath path) {
        assertBlocked(path, stylesheet(nestedTransformReading(
                "function-lookup(QName('http://www.w3.org/2005/xpath-functions', 'transform'), 1)")), SECRET, null);
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(TemplatePath.class)
    @DisplayName("İsimli fonksiyon referansıyla transform() çağrılamaz")
    void transformThroughNamedFunctionReferenceIsBlocked(TemplatePath path) {
        assertBlocked(path, stylesheet(nestedTransformReading("transform#1")), SECRET, null);
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(TemplatePath.class)
    @DisplayName("Derleme zamanı (static) ifadede transform() çağrılamaz")
    void transformInStaticExpressionIsBlocked(TemplatePath path) {
        // XPath dizge sabiti içinde: ' iki katına çıkar; nitelik değeri içinde: " ve < kaçışlanır.
        String innerXslt = innerStylesheetReadingSecret().replace("'", "''").replace("\"", "&quot;").replace("<", "&lt;");
        String config = CONFIGURATION_XML.replace("\"", "&quot;").replace("<", "&lt;");
        String xslt = """
                <xsl:stylesheet version="3.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
                    <xsl:param name="leak" static="yes" select="string(transform(map{'stylesheet-node': parse-xml('%s'), 'source-node': parse-xml('&lt;x/&gt;'), 'vendor-options': map{QName('http://saxon.sf.net/', 'configuration'): parse-xml('%s')}})?output)"/>
                    <xsl:template match="/"><html><head></head><body><p>OUT=[<xsl:value-of select="$leak"/>]</p></body></html></xsl:template>
                </xsl:stylesheet>""".formatted(innerXslt, config);

        assertBlocked(path, xslt, SECRET, null);
    }

    // ── Kontrol: sandbox olmadan aynı girdiler gerçekten sızdırıyor ─────

    /**
     * Sandbox testlerinin boşa geçmediğini gösterir: aynı ifadeler ve belgeler sandbox'sız bir
     * Saxon {@link Processor} ile çalıştırıldığında gizli içerik gerçekten okunur/yazılır.
     */
    @Nested
    @DisplayName("Kontrol: sandbox'sız Saxon")
    class WithoutSandbox {

        static Stream<Arguments> expressions() {
            return fileReadingExpressions()
                    .filter(args -> args.get()[0] == TemplatePath.CUSTOM)
                    .filter(args -> !((String) args.get()[1]).contains("http"))
                    .map(args -> Arguments.of(args.get()[1], args.get()[2], args.get()[3]));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("expressions")
        @DisplayName("Dosya okuyan fonksiyonlar gizli içeriği döndürür")
        void fileReadingFunctionsLeak(String name, String expression, String forbidden) throws Exception {
            String xslt = stylesheet("<p>OUT=[<xsl:value-of select=\"" + withUris(expression) + "\"/>]</p>");

            assertThat(transformWithoutSandbox(xslt, invoice(""))).contains(forbidden);
        }

        @Test
        @DisplayName("Ortam değişkeni okunur")
        void environmentVariableLeaks() throws Exception {
            assumeTrue(System.getenv("PATH") != null && !System.getenv("PATH").isEmpty());
            String xslt = stylesheet(
                    "<p>ENV-LEN=<xsl:value-of select=\"string-length(environment-variable('PATH')) > 0\"/></p>");

            assertThat(transformWithoutSandbox(xslt, invoice(""))).contains("ENV-LEN=true");
        }

        @Test
        @DisplayName("xsl:result-document dosya yazar")
        void resultDocumentWrites() throws Exception {
            Path target = tempDir.resolve("control-written.txt");
            String xslt = stylesheet("""
                    <xsl:result-document href="%s" method="text">%s</xsl:result-document><p>RD</p>"""
                    .formatted(target.toUri(), SECRET));

            transformWithoutSandbox(xslt, invoice(""));

            assertThat(target).hasContent(SECRET);
        }

        @Test
        @DisplayName("Girdi XML'indeki harici varlık çözülür")
        void externalEntityResolves() throws Exception {
            String document = invoiceWithDoctype(
                    "<!DOCTYPE Invoice [<!ENTITY xxe SYSTEM \"" + secretTextUri + "\">]>", "&xxe;");

            assertThat(transformWithoutSandbox(ECHO_DOCUMENT, document)).contains(SECRET);
        }

        @Test
        @DisplayName("parse-xml() harici varlık çözer")
        void parseXmlResolves() throws Exception {
            String inner = "&lt;!DOCTYPE x [&lt;!ENTITY e SYSTEM &quot;" + secretTextUri
                    + "&quot;&gt;]&gt;&lt;x&gt;&amp;e;&lt;/x&gt;";
            String xslt = stylesheet("<p>PX=[<xsl:value-of select=\"parse-xml('" + inner + "')\"/>]</p>");

            assertThat(transformWithoutSandbox(xslt, invoice(""))).contains(SECRET);
        }

        private String transformWithoutSandbox(String xslt, String xml) throws Exception {
            var processor = new Processor(false);
            var executable = processor.newXsltCompiler().compile(new StreamSource(new StringReader(xslt)));
            var out = new StringWriter();
            executable.load30().transform(new StreamSource(new StringReader(xml)), processor.newSerializer(out));
            return out.toString();
        }
    }

    // ── Yardımcılar ────────────────────────────────────────────────

    private static final String ECHO_DOCUMENT = """
            <xsl:stylesheet version="3.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                xmlns:inv="urn:oasis:names:specification:ubl:schema:xsd:Invoice-2"
                xmlns:cbc="urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2">
                <xsl:template match="/">
                    <html><head></head><body>
                        <p>NOTE=[<xsl:value-of select="/inv:Invoice/cbc:Note"/>]</p>
                        <p>LEAK=[<xsl:value-of select="/inv:Invoice/@leak"/>]</p>
                    </body></html>
                </xsl:template>
            </xsl:stylesheet>""";

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

    /** Saxon'un {@code transform()} seçeneğiyle sıfırdan kurulan yapılandırma. */
    private static final String CONFIGURATION_XML =
            "<configuration xmlns=\"http://saxon.sf.net/ns/configuration\" edition=\"HE\"/>";

    private String innerStylesheetReadingSecret() {
        return "<xsl:stylesheet version=\"3.0\" xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\">"
                + "<xsl:template match=\"/\"><r><xsl:value-of select=\"unparsed-text('" + secretTextUri + "')\"/></r>"
                + "</xsl:template></xsl:stylesheet>";
    }

    /**
     * Verilen fonksiyonla, yeni bir Saxon yapılandırması isteyerek gizli dosyayı okuyan bir iç stylesheet
     * çalıştıran gövde. Fonksiyon ifadesi {@code transform} ya da ona dönen bir ifade olabilir.
     */
    private String nestedTransformReading(String function) {
        return """
                <xsl:variable name="inner"><![CDATA[%s]]></xsl:variable>
                <xsl:variable name="cfg"><![CDATA[%s]]></xsl:variable>
                <p>OUT=[<xsl:value-of select="%s(map{'stylesheet-node': parse-xml(string($inner)), 'source-node': parse-xml('&lt;x/&gt;'), 'vendor-options': map{QName('http://saxon.sf.net/', 'configuration'): parse-xml(string($cfg))}})?output"/>]</p>"""
                .formatted(innerStylesheetReadingSecret(), CONFIGURATION_XML, function);
    }

    private String withUris(String expression) {
        return expression
                .replace("{TXT}", secretTextUri)
                .replace("{XML}", secretXmlUri)
                .replace("{JSON}", secretJsonUri)
                .replace("{DIR}", dirUri);
    }

    private static String invoice(String extraContent) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<Invoice " + UBL_NAMESPACES + ">"
                + "<cbc:ID>INV001</cbc:ID>" + extraContent + "</Invoice>";
    }

    private static String invoiceWithDoctype(String doctype, String noteContent) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" + doctype + "\n<Invoice " + UBL_NAMESPACES + ">"
                + "<cbc:ID>INV001</cbc:ID><cbc:Note>" + noteContent + "</cbc:Note></Invoice>";
    }

    private static String embeddedInvoice(String xslt) {
        String encoded = Base64.getEncoder().encodeToString(xslt.getBytes(StandardCharsets.UTF_8));
        return invoice("<cac:AdditionalDocumentReference><cac:Attachment>"
                + "<cbc:EmbeddedDocumentBinaryObject filename=\"ornek.xslt\" encodingCode=\"Base64\">"
                + encoded + "</cbc:EmbeddedDocumentBinaryObject></cac:Attachment></cac:AdditionalDocumentReference>");
    }

    private TransformResult transform(TemplatePath path, String xslt) throws TransformException {
        return transform(path, xslt, path == TemplatePath.EMBEDDED ? embeddedInvoice(xslt) : invoice(""));
    }

    /**
     * XSLT'yi istenen yoldan çalıştırır. Özel ve gömülü yolda varsayılan şablon sabit bir çıktı üretir;
     * varsayılan yolda XSLT'nin kendisi varsayılan şablon olarak yüklenir.
     */
    private TransformResult transform(TemplatePath path, String xslt, String document) throws TransformException {
        var request = new TransformRequest();
        request.setTransformType(TransformType.INVOICE);
        request.setDocument(document.getBytes(StandardCharsets.UTF_8));

        return switch (path) {
            case CUSTOM -> {
                request.setTransformer(xslt.getBytes(StandardCharsets.UTF_8));
                yield transformerWithDefault(stylesheet("<p>" + DEFAULT_OUTPUT + "</p>")).transform(request);
            }
            case EMBEDDED -> {
                request.setUseEmbeddedXslt(true);
                yield transformerWithDefault(stylesheet("<p>" + DEFAULT_OUTPUT + "</p>")).transform(request);
            }
            case DEFAULT -> transformerWithDefault(xslt).transform(request);
        };
    }

    private static SaxonXsltTransformer transformerWithDefault(String defaultXslt) {
        var assetManager = mock(AssetManager.class);
        when(assetManager.assetExists(anyString()))
                .thenAnswer(invocation -> DEFAULT_TEMPLATE_PATH.equals(invocation.getArgument(0)));
        try {
            when(assetManager.getAssetStream(DEFAULT_TEMPLATE_PATH))
                    .thenAnswer(invocation -> new ByteArrayInputStream(defaultXslt.getBytes(StandardCharsets.UTF_8)));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        var transformer = new SaxonXsltTransformer(assetManager, new WatermarkService(assetManager), new HtmlSanitizer(),
                new EmbeddedXsltExtractor(), new XsltMetrics(new SimpleMeterRegistry()));
        transformer.reload();
        return transformer;
    }

    private static void assertOwnTemplateUsed(TemplatePath path, TransformResult result) {
        switch (path) {
            case CUSTOM -> {
                assertThat(result.getCustomXsltError()).isNull();
                assertThat(result.isDefaultXslUsed()).isFalse();
            }
            case EMBEDDED -> {
                assertThat(result.getCustomXsltError()).isNull();
                assertThat(result.isEmbeddedXsltUsed()).isTrue();
            }
            case DEFAULT -> assertThat(result.isDefaultXslUsed()).isTrue();
        }
        assertThat(html(result)).doesNotContain(DEFAULT_OUTPUT);
    }

    /**
     * Özel/gömülü XSLT engellenince varsayılan şablona düşülür ve hata bildirilir; varsayılan şablon
     * engellenince {@link TransformException} fırlar. Her durumda yasaklı içerik dışarı çıkmaz.
     */
    private void assertBlocked(TemplatePath path, String xslt, String forbidden, String expectedError) {
        if (path == TemplatePath.DEFAULT) {
            assertThatThrownBy(() -> transform(path, xslt))
                    .isInstanceOf(TransformException.class)
                    .satisfies(e -> assertThat(e.getMessage()).doesNotContain(forbidden));
            return;
        }

        TransformResult result;
        try {
            result = transform(path, xslt);
        } catch (TransformException e) {
            throw new AssertionError("Varsayılan şablona düşülmesi bekleniyordu", e);
        }
        assertThat(result.isDefaultXslUsed()).isTrue();
        assertThat(result.isEmbeddedXsltUsed()).isFalse();
        assertThat(result.getCustomXsltError()).isNotBlank().doesNotContain(forbidden);
        if (expectedError != null) {
            assertThat(result.getCustomXsltError()).contains(expectedError);
        }
        assertThat(html(result)).contains(DEFAULT_OUTPUT).doesNotContain(forbidden);
    }

    private static String html(TransformResult result) {
        return new String(result.getHtmlContent(), StandardCharsets.UTF_8);
    }
}

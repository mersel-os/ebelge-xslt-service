package io.mersel.services.xslt.infrastructure;

import com.sun.net.httpserver.HttpServer;
import io.mersel.services.xslt.application.enums.SchematronValidationType;
import io.mersel.services.xslt.application.models.SchematronError;
import io.mersel.services.xslt.infrastructure.diagnostics.XsltMetrics;
import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.XsltExecutable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.xml.transform.stream.StreamSource;
import java.io.ByteArrayInputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.lang.reflect.Field;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Schematron doğrulama yolunda kullanıcı belgesindeki DOCTYPE'ın harici kaynaklara
 * (dosya, ağ) ulaşamadığını doğrular.
 * <p>
 * Test şablonu belgedeki {@code ID} değerini hata mesajına yazar; gerçek Schematron'lardaki
 * {@code <sch:value-of>} ve profil kurallarındaki {@code {{xpath}}} yer tutucuları da aynı yolla
 * belge içeriğini yanıta taşır. Çözülen bir harici varlık bu yüzden doğrudan yanıta sızar.
 */
@DisplayName("SaxonSchematronValidator XXE")
class SaxonSchematronValidatorXxeTest {

    private static final SchematronValidationType TYPE = SchematronValidationType.UBLTR_MAIN;

    /** Belgedeki ID değerini köşeli parantez içinde hata mesajına yazar; değer boşsa "[]" döner. */
    private static final String ECHO_ID_XSLT = """
            <xsl:stylesheet version="2.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
                <xsl:template match="/">
                    <Errors>
                        <Error ruleId="ECHO" test="ID">[<xsl:value-of select="/*/ID"/>]</Error>
                    </Errors>
                </xsl:template>
            </xsl:stylesheet>
            """;

    private static final String ECHO_BASE_URI_XSLT = """
            <xsl:stylesheet version="2.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
                <xsl:template match="/">
                    <Errors>
                        <Error ruleId="BASE">[<xsl:value-of select="base-uri(/)"/>]</Error>
                    </Errors>
                </xsl:template>
            </xsl:stylesheet>
            """;

    @TempDir
    Path tempDir;

    private final String secret = "XXE-SECRET-" + UUID.randomUUID();
    private final AtomicInteger httpHits = new AtomicInteger();

    private SaxonSchematronValidator validator;
    private XsltExecutable echoId;
    private Path secretFile;
    private Path secretDtd;
    private HttpServer httpServer;

    @BeforeEach
    void setUp() throws Exception {
        validator = new SaxonSchematronValidator(
                mock(AssetManager.class), mock(SchematronRuntimeCompiler.class), mock(XsltMetrics.class));
        validator.init();

        echoId = compile(ECHO_ID_XSLT);
        useCompiledSchematron(echoId);

        secretFile = Files.writeString(tempDir.resolve("secret.txt"), secret);
        secretDtd = Files.writeString(tempDir.resolve("secret.dtd"),
                "<!ENTITY leak \"" + secret + "\">");

        httpServer = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        httpServer.createContext("/", exchange -> {
            httpHits.incrementAndGet();
            byte[] body = secret.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        httpServer.start();
    }

    @AfterEach
    void tearDown() {
        httpServer.stop(0);
    }

    @Nested
    @DisplayName("Harici kaynaklar engellenir")
    class ExternalResourcesBlocked {

        @Test
        @DisplayName("file:// genel varlığı okunmaz")
        void external_general_entity_file() {
            byte[] doc = xml("""
                    <!DOCTYPE Invoice [<!ENTITY xxe SYSTEM "%s">]>
                    <Invoice><ID>&xxe;</ID></Invoice>
                    """.formatted(secretFile.toUri()));

            List<SchematronError> errors = validate(doc, null);

            assertNoLeak(errors);
            assertThat(messages(errors)).containsExactly("[]");
        }

        @Test
        @DisplayName("Göreli varlık, sourceFileName'e göre çözülüp okunmaz")
        void relative_entity_against_source_file_name() {
            byte[] doc = xml("""
                    <!DOCTYPE Invoice [<!ENTITY xxe SYSTEM "secret.txt">]>
                    <Invoice><ID>&xxe;</ID></Invoice>
                    """);

            List<SchematronError> errors = validate(doc, tempDir.resolve("fatura.xml").toUri().toString());

            assertNoLeak(errors);
            assertThat(messages(errors)).containsExactly("[]");
        }

        @Test
        @DisplayName("Harici parametre varlığı yüklenmez")
        void external_parameter_entity() {
            byte[] doc = xml("""
                    <!DOCTYPE Invoice [<!ENTITY %% ext SYSTEM "%s"> %%ext;]>
                    <Invoice><ID>&leak;</ID></Invoice>
                    """.formatted(secretDtd.toUri()));

            assertNoLeak(validate(doc, null));
        }

        @Test
        @DisplayName("Harici DTD alt kümesi yüklenmez")
        void external_dtd_subset() {
            byte[] doc = xml("""
                    <!DOCTYPE Invoice SYSTEM "%s">
                    <Invoice><ID>&leak;</ID></Invoice>
                    """.formatted(secretDtd.toUri()));

            assertNoLeak(validate(doc, null));
        }

        @Test
        @DisplayName("http:// varlığı için istek atılmaz")
        void external_entity_http() {
            byte[] doc = xml("""
                    <!DOCTYPE Invoice [<!ENTITY xxe SYSTEM "%s">]>
                    <Invoice><ID>&xxe;</ID></Invoice>
                    """.formatted(httpUrl("/xxe")));

            List<SchematronError> errors = validate(doc, null);

            assertNoLeak(errors);
            assertThat(httpHits).as("harici varlık için yapılan HTTP isteği").hasValue(0);
        }

        @Test
        @DisplayName("http:// harici DTD için istek atılmaz")
        void external_dtd_http() {
            byte[] doc = xml("""
                    <!DOCTYPE Invoice SYSTEM "%s">
                    <Invoice><ID>INV-1</ID></Invoice>
                    """.formatted(httpUrl("/ext.dtd")));

            validate(doc, null);

            assertThat(httpHits).as("harici DTD için yapılan HTTP isteği").hasValue(0);
        }
    }

    @Nested
    @DisplayName("Mevcut davranış korunur")
    class ExistingBehaviourKept {

        @Test
        @DisplayName("İç DTD alt kümesindeki varlıklar genişletilir")
        void internal_entity_is_expanded() {
            byte[] doc = xml("""
                    <!DOCTYPE Invoice [<!ENTITY id "INV-İÇ-001">]>
                    <Invoice><ID>&id;</ID></Invoice>
                    """);

            assertThat(messages(validate(doc, null))).containsExactly("[INV-İÇ-001]");
        }

        @Test
        @DisplayName("Varlık genişleme sınırı aşılınca doğrulama hata ile biter")
        void entity_expansion_is_bounded() {
            var dtd = new StringBuilder("<!DOCTYPE Invoice [<!ENTITY e0 \"lol\">");
            for (int i = 1; i <= 10; i++) {
                dtd.append("<!ENTITY e").append(i).append(" \"")
                        .append(("&e" + (i - 1) + ";").repeat(10)).append("\">");
            }
            dtd.append("]>");
            byte[] doc = xml(dtd + "<Invoice><ID>&e10;</ID></Invoice>");

            List<SchematronError> errors = validate(doc, null);

            assertThat(errors).singleElement()
                    .satisfies(error -> assertThat(error.message()).startsWith("Schematron doğrulama hatası:"));
        }

        @Test
        @DisplayName("Düz belge değişmeden doğrulanır")
        void plain_document() {
            byte[] doc = xml("<Invoice><ID>INV-1</ID></Invoice>");

            assertThat(messages(validate(doc, null))).containsExactly("[INV-1]");
        }

        @Test
        @DisplayName("sourceFileName belgenin base-uri() değeri olarak kalır")
        void source_file_name_is_base_uri() throws Exception {
            XsltExecutable echoBaseUri = compile(ECHO_BASE_URI_XSLT);
            useCompiledSchematron(echoBaseUri);
            byte[] doc = xml("<Invoice><ID>INV-1</ID></Invoice>");

            for (String fileName : List.of("fatura.xml", "123_20240101_Y_000001.xml",
                    tempDir.resolve("defter.xml").toUri().toString())) {
                String expected = "[" + unprotectedBaseUri(echoBaseUri, doc, fileName) + "]";

                assertThat(messages(validate(doc, fileName)))
                        .as("sourceFileName=%s", fileName)
                        .containsExactly(expected);
            }
        }
    }

    @Nested
    @DisplayName("Korumasız kontrol")
    class UnprotectedControl {

        @Test
        @DisplayName("Düz StreamSource ile aynı yük dosyayı okur (yük geçerli)")
        void plain_stream_source_reads_file() throws Exception {
            byte[] doc = xml("""
                    <!DOCTYPE Invoice [<!ENTITY xxe SYSTEM "%s">]>
                    <Invoice><ID>&xxe;</ID></Invoice>
                    """.formatted(secretFile.toUri()));

            assertThat(transformUnprotected(echoId, doc, null)).contains(secret);
        }

        @Test
        @DisplayName("Düz StreamSource ile aynı yük HTTP isteği atar (yük geçerli)")
        void plain_stream_source_calls_http() throws Exception {
            byte[] doc = xml("""
                    <!DOCTYPE Invoice [<!ENTITY xxe SYSTEM "%s">]>
                    <Invoice><ID>&xxe;</ID></Invoice>
                    """.formatted(httpUrl("/xxe")));

            assertThat(transformUnprotected(echoId, doc, null)).contains(secret);
            assertThat(httpHits).hasValue(1);
        }
    }

    // ── Yardımcılar ─────────────────────────────────────────────────

    private List<SchematronError> validate(byte[] doc, String sourceFileName) {
        return validator.validate(doc, TYPE, sourceFileName, List.of(), null, Map.of());
    }

    private void assertNoLeak(List<SchematronError> errors) {
        assertThat(errors).allSatisfy(error -> assertThat(error.message())
                .as("harici kaynak içeriği yanıta taşınmamalı")
                .doesNotContain(secret));
    }

    private static List<String> messages(List<SchematronError> errors) {
        return errors.stream().map(SchematronError::message).toList();
    }

    private String httpUrl(String path) {
        return "http://" + httpServer.getAddress().getHostString() + ":" + httpServer.getAddress().getPort() + path;
    }

    private static byte[] xml(String body) {
        return ("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" + body).getBytes(StandardCharsets.UTF_8);
    }

    private static XsltExecutable compile(String xslt) throws SaxonApiException {
        return new Processor(false).newXsltCompiler().compile(new StreamSource(new StringReader(xslt)));
    }

    /** Düzeltmeden önceki ayrıştırma yolu: varsayılan ayrıştırıcı ve düz {@link StreamSource}. */
    private static String transformUnprotected(XsltExecutable executable, byte[] doc, String systemId)
            throws SaxonApiException {
        var source = new StreamSource(new ByteArrayInputStream(doc));
        if (systemId != null) {
            source.setSystemId(systemId);
        }
        var out = new StringWriter();
        executable.load30().transform(source, executable.getProcessor().newSerializer(out));
        return out.toString();
    }

    private static String unprotectedBaseUri(XsltExecutable echoBaseUri, byte[] doc, String systemId)
            throws SaxonApiException {
        String output = transformUnprotected(echoBaseUri, doc, systemId);
        int start = output.indexOf('[');
        int end = output.lastIndexOf(']');
        return output.substring(start + 1, end);
    }

    private void useCompiledSchematron(XsltExecutable executable) throws Exception {
        Field field = SaxonSchematronValidator.class.getDeclaredField("compiledSchematrons");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        var current = new HashMap<>((Map<SchematronValidationType, XsltExecutable>) field.get(validator));
        current.put(TYPE, executable);
        field.set(validator, Map.copyOf(current));
    }
}

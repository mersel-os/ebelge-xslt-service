package io.mersel.services.xslt.infrastructure;

import com.sun.net.httpserver.HttpServer;
import io.mersel.services.xslt.application.enums.SchemaValidationType;
import io.mersel.services.xslt.infrastructure.diagnostics.XsltMetrics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXParseException;

import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import java.io.ByteArrayInputStream;
import java.io.StringReader;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * XSD doğrulama yolunda kullanıcı belgesindeki DOCTYPE'ın harici kaynaklara ulaşamadığını doğrular.
 * <p>
 * Şemadaki {@code ID} alanı tamsayıdır; tamsayı olmayan değer hata mesajına yazılır
 * ({@code "<değer>" değeri tamsayı formatına uygun değil}). Çözülen bir harici varlık bu yüzden
 * doğrudan yanıta sızar.
 */
@DisplayName("JaxpSchemaValidator XXE")
class JaxpSchemaValidatorXxeTest {

    private static final SchemaValidationType TYPE = SchemaValidationType.INVOICE;

    private static final String XSD = """
            <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                <xs:element name="Invoice">
                    <xs:complexType>
                        <xs:sequence>
                            <xs:element name="ID" type="xs:integer"/>
                        </xs:sequence>
                    </xs:complexType>
                </xs:element>
            </xs:schema>
            """;

    @TempDir
    Path tempDir;

    private final String secret = "XXE-SECRET-" + UUID.randomUUID();
    private final AtomicInteger httpHits = new AtomicInteger();

    private Schema schema;
    private JaxpSchemaValidator validator;
    private Path secretFile;
    private HttpServer httpServer;

    @BeforeEach
    void setUp() throws Exception {
        schema = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI)
                .newSchema(new StreamSource(new StringReader(XSD)));
        validator = newValidator(schema);
        secretFile = Files.writeString(tempDir.resolve("secret.txt"), secret);

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

    @Test
    @DisplayName("file:// genel varlığı okunmaz")
    void external_general_entity_file() {
        List<String> errors = validator.validate(xxeDocument(secretFile.toUri().toString()), TYPE);

        assertThat(errors).isNotEmpty().noneMatch(error -> error.contains(secret));
    }

    @Test
    @DisplayName("http:// varlığı için istek atılmaz")
    void external_general_entity_http() {
        List<String> errors = validator.validate(xxeDocument(httpUrl("/xxe")), TYPE);

        assertThat(errors).noneMatch(error -> error.contains(secret));
        assertThat(httpHits).hasValue(0);
    }

    @Test
    @DisplayName("İç DTD varlığının değeri hata mesajına yazılır (sızıntı yolu açık)")
    void internal_entity_value_reaches_error_message() {
        byte[] doc = xml("""
                <!DOCTYPE Invoice [<!ENTITY id "ICERIDEN">]>
                <Invoice><ID>&id;</ID></Invoice>
                """);

        assertThat(validator.validate(doc, TYPE)).anyMatch(error -> error.contains("ICERIDEN"));
    }

    @Test
    @DisplayName("Korumasız kontrol: özellik verilmemiş Validator aynı yükte dosyayı okur (yük geçerli)")
    void unprotected_validator_reads_file() throws Exception {
        var messages = new ArrayList<String>();
        var plainValidator = schema.newValidator();
        plainValidator.setErrorHandler(new ErrorHandler() {
            @Override
            public void warning(SAXParseException e) {
            }

            @Override
            public void error(SAXParseException e) {
                messages.add(e.getMessage());
            }

            @Override
            public void fatalError(SAXParseException e) {
                messages.add(e.getMessage());
            }
        });

        plainValidator.validate(new StreamSource(new ByteArrayInputStream(
                xxeDocument(secretFile.toUri().toString()))));

        assertThat(messages).anyMatch(message -> message.contains(secret));
    }

    // ── Yardımcılar ─────────────────────────────────────────────────

    private byte[] xxeDocument(String entityUri) {
        return xml("""
                <!DOCTYPE Invoice [<!ENTITY xxe SYSTEM "%s">]>
                <Invoice><ID>&xxe;</ID></Invoice>
                """.formatted(entityUri));
    }

    private String httpUrl(String path) {
        return "http://" + httpServer.getAddress().getHostString() + ":" + httpServer.getAddress().getPort() + path;
    }

    private static byte[] xml(String body) {
        return ("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" + body).getBytes(StandardCharsets.UTF_8);
    }

    private static JaxpSchemaValidator newValidator(Schema schema) throws Exception {
        var validator = new JaxpSchemaValidator(mock(AssetManager.class), mock(XsltMetrics.class));
        setField(validator, "xsdOverrideCacheMaxSize", 50);
        setField(validator, "xsdOverrideCacheTtlHours", 1);
        Method init = JaxpSchemaValidator.class.getDeclaredMethod("init");
        init.setAccessible(true);
        init.invoke(validator);
        setField(validator, "compiledSchemas", Map.of(TYPE, schema));
        return validator;
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = JaxpSchemaValidator.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}

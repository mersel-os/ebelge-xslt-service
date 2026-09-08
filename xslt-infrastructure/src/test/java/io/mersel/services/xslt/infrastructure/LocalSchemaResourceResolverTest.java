package io.mersel.services.xslt.infrastructure;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.ls.LSInput;

import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.SchemaFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

@DisplayName("LocalSchemaResourceResolver")
class LocalSchemaResourceResolverTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("goreceli_import_ek_arama_dizininden_cozulur")
    void goreceli_import_ortak_dizinden_cozulur() throws Exception {
        Path specializedDir = Files.createDirectories(tempDir.resolve("edoviz"));
        Path sharedDir = Files.createDirectories(tempDir.resolve("earchive"));
        Path sharedSchema = sharedDir.resolve("XAdES.xsd");
        Files.writeString(sharedSchema, "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\"/>");

        var resolver = new LocalSchemaResourceResolver(specializedDir, sharedDir);
        LSInput resolved = resolver.resolveResource(
                "http://www.w3.org/2001/XMLSchema",
                "http://uri.etsi.org/01903/v1.3.2#",
                null,
                "XAdES.xsd",
                specializedDir.resolve("eArsiv.xsd").toUri().toString());

        assertThat(resolved).isNotNull();
        assertThat(resolved.getSystemId()).isEqualTo(sharedSchema.toUri().toString());
        try (var stream = resolved.getByteStream()) {
            assertThat(stream).isNotNull();
        }
    }

    @Test
    @DisplayName("goreceli_import_once_base_uri_ile_cozulur — aynı adlı başka XSD seçilmez")
    void goreceli_import_once_base_uri_ile_cozulur() throws Exception {
        Path schemaRoot = Files.createDirectories(tempDir.resolve("schema"));
        Path importingDir = Files.createDirectories(schemaRoot.resolve("reports/current"));
        Path expected = Files.createDirectories(importingDir.resolve("common")).resolve("types.xsd");
        Path wrong = Files.createDirectories(schemaRoot.resolve("other/common")).resolve("types.xsd");
        Files.writeString(expected, "expected");
        Files.writeString(wrong, "wrong");

        var resolver = new LocalSchemaResourceResolver(schemaRoot);
        LSInput resolved = resolver.resolveResource(
                "http://www.w3.org/2001/XMLSchema", null, null,
                "common/types.xsd", importingDir.resolve("main.xsd").toUri().toString());

        assertThat(resolved).isNotNull();
        assertThat(resolved.getSystemId()).isEqualTo(expected.toUri().toString());
    }

    @Test
    @DisplayName("ust_dizine_goreceli_import_base_uri_ile_cozulur")
    void ust_dizine_goreceli_import_base_uri_ile_cozulur() throws Exception {
        Path schemaRoot = Files.createDirectories(tempDir.resolve("schema"));
        Path importingDir = Files.createDirectories(schemaRoot.resolve("maindoc"));
        Path expected = Files.createDirectories(schemaRoot.resolve("common")).resolve("types.xsd");
        Files.writeString(expected, "expected");

        var resolver = new LocalSchemaResourceResolver(importingDir);
        LSInput resolved = resolver.resolveResource(
                "http://www.w3.org/2001/XMLSchema", null, null,
                "../common/types.xsd", importingDir.resolve("main.xsd").toUri().toString());

        assertThat(resolved).isNotNull();
        assertThat(resolved.getSystemId()).isEqualTo(expected.toUri().toString());
    }

    @Test
    @DisplayName("goreceli_import_tam_suffix_ile_cozulur — yalnızca dosya adı kullanılmaz")
    void goreceli_import_tam_suffix_ile_cozulur() throws Exception {
        Path primaryDir = Files.createDirectories(tempDir.resolve("primary"));
        Path sharedDir = Files.createDirectories(tempDir.resolve("shared"));
        Path wrong = Files.createDirectories(sharedDir.resolve("legacy")).resolve("types.xsd");
        Path expected = Files.createDirectories(sharedDir.resolve("common")).resolve("types.xsd");
        Files.writeString(wrong, "wrong");
        Files.writeString(expected, "expected");

        var resolver = new LocalSchemaResourceResolver(primaryDir, sharedDir);
        LSInput resolved = resolver.resolveResource(
                "http://www.w3.org/2001/XMLSchema", null, null,
                "common/types.xsd", primaryDir.resolve("main.xsd").toUri().toString());

        assertThat(resolved).isNotNull();
        assertThat(resolved.getSystemId()).isEqualTo(expected.toUri().toString());
    }

    @Test
    @DisplayName("bozuk_http_referansi_null_doner — geçersiz URI şema derlemesini kırmamalı")
    void bozuk_http_referansi_null_doner() {
        var resolver = new LocalSchemaResourceResolver(tempDir);

        LSInput resolved = resolver.resolveResource(
                "http://www.w3.org/2001/XMLSchema", null, null,
                "http://example.org/schema/xmldsig core.xsd", null);

        assertThat(resolved).isNull();
    }

    @Test
    @DisplayName("import_edilen_xsd_kendi_encoding_bildirimini_korur — sabit UTF-8 dayatılmamalı")
    void import_edilen_xsd_kendi_encoding_bildirimini_korur() throws Exception {
        Path schemaDir = Files.createDirectories(tempDir.resolve("schema"));
        Files.write(schemaDir.resolve("types.xsd"), """
                <?xml version="1.0" encoding="windows-1254"?>
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                  <xs:simpleType name="FirmaTipi">
                    <xs:restriction base="xs:string">
                      <xs:enumeration value="Şirket"/>
                    </xs:restriction>
                  </xs:simpleType>
                </xs:schema>
                """.getBytes(Charset.forName("windows-1254")));

        Path mainXsd = schemaDir.resolve("main.xsd");
        Files.writeString(mainXsd, """
                <?xml version="1.0" encoding="UTF-8"?>
                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                  <xs:include schemaLocation="types.xsd"/>
                  <xs:element name="Firma" type="FirmaTipi"/>
                </xs:schema>
                """, StandardCharsets.UTF_8);

        SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
        factory.setResourceResolver(new LocalSchemaResourceResolver(schemaDir));
        var validator = factory.newSchema(new StreamSource(mainXsd.toFile())).newValidator();

        assertThatCode(() -> validator.validate(new StreamSource(new ByteArrayInputStream(
                "<Firma>Şirket</Firma>".getBytes(StandardCharsets.UTF_8)))))
                .doesNotThrowAnyException();
    }
}

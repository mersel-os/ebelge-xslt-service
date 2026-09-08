package io.mersel.services.xslt.infrastructure;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * EmbeddedXsltExtractor birim testleri.
 */
@DisplayName("EmbeddedXsltExtractor")
class EmbeddedXsltExtractorTest {

    private EmbeddedXsltExtractor extractor;

    @BeforeEach
    void setUp() {
        extractor = new EmbeddedXsltExtractor();
    }

    @Test
    @DisplayName("UBL belgesinden .xslt uzantılı gömülü XSLT çıkarmalı")
    void shouldExtractEmbeddedXsltFromUblDocument() {
        String xsltContent = """
                <?xml version="1.0" encoding="UTF-8"?>
                <xsl:stylesheet version="2.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
                    <xsl:template match="/"><html><body>Test</body></html></xsl:template>
                </xsl:stylesheet>""";

        String base64Xslt = Base64.getEncoder().encodeToString(xsltContent.getBytes(StandardCharsets.UTF_8));

        String ublXml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <Invoice xmlns="urn:oasis:names:specification:ubl:schema:xsd:Invoice-2"
                         xmlns:cac="urn:oasis:names:specification:ubl:schema:xsd:CommonAggregateComponents-2"
                         xmlns:cbc="urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2">
                    <cbc:ID>TEST001</cbc:ID>
                    <cac:AdditionalDocumentReference>
                        <cbc:ID>ref-001</cbc:ID>
                        <cac:Attachment>
                            <cbc:EmbeddedDocumentBinaryObject characterSetCode="UTF-8"
                                encodingCode="Base64"
                                filename="test-transform.xslt"
                                mimeCode="application/xml">%s</cbc:EmbeddedDocumentBinaryObject>
                        </cac:Attachment>
                    </cac:AdditionalDocumentReference>
                </Invoice>""".formatted(base64Xslt);

        byte[] result = extractor.extract(ublXml.getBytes(StandardCharsets.UTF_8));

        assertThat(result).isNotNull();
        String extracted = new String(result, StandardCharsets.UTF_8);
        assertThat(extracted).contains("xsl:stylesheet");
        assertThat(extracted).contains("xsl:template");
    }

    @Test
    @DisplayName(".xsl uzantılı gömülü XSLT de çıkarmalı")
    void shouldExtractXslExtensionToo() {
        String xsltContent = "<xsl:stylesheet version=\"2.0\" xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\"/>";
        String base64Xslt = Base64.getEncoder().encodeToString(xsltContent.getBytes(StandardCharsets.UTF_8));

        String ublXml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <Invoice xmlns="urn:oasis:names:specification:ubl:schema:xsd:Invoice-2"
                         xmlns:cac="urn:oasis:names:specification:ubl:schema:xsd:CommonAggregateComponents-2"
                         xmlns:cbc="urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2">
                    <cac:AdditionalDocumentReference>
                        <cac:Attachment>
                            <cbc:EmbeddedDocumentBinaryObject filename="style.xsl"
                                encodingCode="Base64">%s</cbc:EmbeddedDocumentBinaryObject>
                        </cac:Attachment>
                    </cac:AdditionalDocumentReference>
                </Invoice>""".formatted(base64Xslt);

        byte[] result = extractor.extract(ublXml.getBytes(StandardCharsets.UTF_8));
        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("Dahili entity tanımlayan DOCTYPE'lı gömülü XSLT'yi çıkarmalı")
    void shouldExtractEmbeddedXsltWithInternalDoctype() {
        String xsltContent = """
                <?xml version="1.0" encoding="UTF-8"?>
                <!DOCTYPE xsl:stylesheet [<!ENTITY nbsp "&#160;">]>
                <xsl:stylesheet version="2.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
                    <xsl:template match="/"><html><body>&nbsp;</body></html></xsl:template>
                </xsl:stylesheet>""";
        String base64Xslt = Base64.getEncoder().encodeToString(
                xsltContent.getBytes(StandardCharsets.UTF_8));
        String ublXml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <Invoice xmlns="urn:oasis:names:specification:ubl:schema:xsd:Invoice-2"
                         xmlns:cac="urn:oasis:names:specification:ubl:schema:xsd:CommonAggregateComponents-2"
                         xmlns:cbc="urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2">
                    <cac:AdditionalDocumentReference>
                        <cac:Attachment>
                            <cbc:EmbeddedDocumentBinaryObject filename="doctype.xslt"
                                encodingCode="Base64">%s</cbc:EmbeddedDocumentBinaryObject>
                        </cac:Attachment>
                    </cac:AdditionalDocumentReference>
                </Invoice>""".formatted(base64Xslt);

        byte[] result = extractor.extract(ublXml.getBytes(StandardCharsets.UTF_8));

        assertThat(result).isNotNull();
        assertThat(new String(result, StandardCharsets.UTF_8))
                .contains("<!DOCTYPE xsl:stylesheet")
                .contains("&nbsp;");
    }

    @Test
    @DisplayName("UBL taşıyıcı belgede DOCTYPE kullanımını reddetmeli")
    void shouldRejectDoctypeInUblContainer() {
        String ublXml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <!DOCTYPE Invoice [<!ENTITY value "test">]>
                <Invoice xmlns="urn:oasis:names:specification:ubl:schema:xsd:Invoice-2"
                         xmlns:cac="urn:oasis:names:specification:ubl:schema:xsd:CommonAggregateComponents-2"
                         xmlns:cbc="urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2">
                    <cbc:ID>&value;</cbc:ID>
                </Invoice>""";

        assertThat(extractor.extract(ublXml.getBytes(StandardCharsets.UTF_8))).isNull();
    }

    @Test
    @DisplayName("Yalnızca harici DTD'de tanımlanan entity'ye bağımlı XSLT'yi reddetmeli")
    void shouldRejectXsltDependingOnExternalDtdEntity() {
        String xsltContent = """
                <?xml version="1.0" encoding="UTF-8"?>
                <!DOCTYPE xsl:stylesheet SYSTEM "file:///definitely-not-present/external.dtd">
                <xsl:stylesheet version="2.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
                    <xsl:template match="/"><html><body>&external;</body></html></xsl:template>
                </xsl:stylesheet>""";
        String base64Xslt = Base64.getEncoder().encodeToString(
                xsltContent.getBytes(StandardCharsets.UTF_8));
        String ublXml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <Invoice xmlns="urn:oasis:names:specification:ubl:schema:xsd:Invoice-2"
                         xmlns:cac="urn:oasis:names:specification:ubl:schema:xsd:CommonAggregateComponents-2"
                         xmlns:cbc="urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2">
                    <cac:AdditionalDocumentReference>
                        <cac:Attachment>
                            <cbc:EmbeddedDocumentBinaryObject filename="external-dtd.xslt"
                                encodingCode="Base64">%s</cbc:EmbeddedDocumentBinaryObject>
                        </cac:Attachment>
                    </cac:AdditionalDocumentReference>
                </Invoice>""".formatted(base64Xslt);

        assertThat(extractor.extract(ublXml.getBytes(StandardCharsets.UTF_8))).isNull();
    }

    @Test
    @DisplayName("Gömülü XSLT olmayan belgede null dönmeli")
    void shouldReturnNullWhenNoEmbeddedXslt() {
        String ublXml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <Invoice xmlns="urn:oasis:names:specification:ubl:schema:xsd:Invoice-2"
                         xmlns:cac="urn:oasis:names:specification:ubl:schema:xsd:CommonAggregateComponents-2"
                         xmlns:cbc="urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2">
                    <cbc:ID>TEST001</cbc:ID>
                </Invoice>""";

        byte[] result = extractor.extract(ublXml.getBytes(StandardCharsets.UTF_8));
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("PDF eki olan ama XSLT olmayan belgede null dönmeli")
    void shouldIgnoreNonXsltAttachments() {
        String base64Pdf = Base64.getEncoder().encodeToString("fake-pdf-content".getBytes());

        String ublXml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <Invoice xmlns="urn:oasis:names:specification:ubl:schema:xsd:Invoice-2"
                         xmlns:cac="urn:oasis:names:specification:ubl:schema:xsd:CommonAggregateComponents-2"
                         xmlns:cbc="urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2">
                    <cac:AdditionalDocumentReference>
                        <cac:Attachment>
                            <cbc:EmbeddedDocumentBinaryObject filename="document.pdf"
                                mimeCode="application/pdf">%s</cbc:EmbeddedDocumentBinaryObject>
                        </cac:Attachment>
                    </cac:AdditionalDocumentReference>
                </Invoice>""".formatted(base64Pdf);

        byte[] result = extractor.extract(ublXml.getBytes(StandardCharsets.UTF_8));
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("XSLT adlı placeholder içeriği geçersizse null dönmeli")
    void shouldIgnoreInvalidXsltPlaceholder() {
        String placeholder = Base64.getEncoder().encodeToString("BASE64".getBytes(StandardCharsets.UTF_8));
        String ublXml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <CreditNote xmlns="urn:oasis:names:specification:ubl:schema:xsd:CreditNote-2"
                            xmlns:cac="urn:oasis:names:specification:ubl:schema:xsd:CommonAggregateComponents-2"
                            xmlns:cbc="urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2">
                    <cac:AdditionalDocumentReference>
                        <cac:Attachment>
                            <cbc:EmbeddedDocumentBinaryObject filename="application.xslt"
                                encodingCode="Base64">%s</cbc:EmbeddedDocumentBinaryObject>
                        </cac:Attachment>
                    </cac:AdditionalDocumentReference>
                </CreditNote>""".formatted(placeholder);

        assertThat(extractor.extract(ublXml.getBytes(StandardCharsets.UTF_8))).isNull();
    }

    @Test
    @DisplayName("Bozuk XML'de null dönmeli (exception fırlatmamalı)")
    void shouldReturnNullForMalformedXml() {
        byte[] result = extractor.extract("this is not xml".getBytes());
        assertThat(result).isNull();
    }
    @Test
    @DisplayName("Kısa biçimli XSLT namespace'e göre tanınmalı; sıradan HTML reddedilmeli")
    void shouldRecognizeOnlyNamespaceQualifiedSimplifiedStylesheets() {
        String[] candidates = {
                "<html xmlns:t='http://www.w3.org/1999/XSL/Transform' t:version='2.0'/>",
                "<html version='2.0'/>",
                "<html xmlns:t='urn:other' t:version='2.0'/>",
                "<html xmlns:t='http://www.w3.org/1999/XSL/Transform' t:version=' '/>"
        };
        for (int i = 0; i < candidates.length; i++) {
            String xml = """
                    <Invoice xmlns:cac="urn:oasis:names:specification:ubl:schema:xsd:CommonAggregateComponents-2"
                             xmlns:cbc="urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2">
                        <cac:AdditionalDocumentReference><cac:Attachment>
                            <cbc:EmbeddedDocumentBinaryObject filename="style.xslt">%s</cbc:EmbeddedDocumentBinaryObject>
                        </cac:Attachment></cac:AdditionalDocumentReference>
                    </Invoice>""".formatted(Base64.getEncoder().encodeToString(
                            candidates[i].getBytes(StandardCharsets.UTF_8)));
            byte[] result = extractor.extract(xml.getBytes(StandardCharsets.UTF_8));
            if (i == 0) {
                assertThat(result).isEqualTo(candidates[i].getBytes(StandardCharsets.UTF_8));
            } else {
                assertThat(result).as(candidates[i]).isNull();
            }
        }
    }

}

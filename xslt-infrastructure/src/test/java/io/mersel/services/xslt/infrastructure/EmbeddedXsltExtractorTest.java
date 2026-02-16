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
    @DisplayName("Bozuk XML'de null dönmeli (exception fırlatmamalı)")
    void shouldReturnNullForMalformedXml() {
        byte[] result = extractor.extract("this is not xml".getBytes());
        assertThat(result).isNull();
    }
}

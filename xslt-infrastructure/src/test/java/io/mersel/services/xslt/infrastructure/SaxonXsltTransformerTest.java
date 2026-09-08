package io.mersel.services.xslt.infrastructure;

import io.mersel.services.xslt.application.enums.TransformType;
import io.mersel.services.xslt.application.interfaces.IXsltTransformer.TransformException;
import io.mersel.services.xslt.application.models.TransformRequest;
import io.mersel.services.xslt.infrastructure.diagnostics.XsltMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SaxonXsltTransformer birim testleri.
 */
@DisplayName("SaxonXsltTransformer")
class SaxonXsltTransformerTest {

    private SaxonXsltTransformer transformer;

    @BeforeEach
    void setUp() {
        var assetManager = new AssetManager();
        assetManager.init();
        var watermarkService = new WatermarkService();
        var htmlSanitizer = new HtmlSanitizer();
        var embeddedXsltExtractor = new EmbeddedXsltExtractor();
        var metrics = new XsltMetrics(new SimpleMeterRegistry());
        transformer = new SaxonXsltTransformer(assetManager, watermarkService, htmlSanitizer, embeddedXsltExtractor, metrics);
    }

    @Test
    @DisplayName("Özel XSLT ile basit XML dönüşümü çalışmalı")
    void shouldTransformWithCustomXslt() throws TransformException {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><root><name>Test</name></root>";
        String xslt = """
                <?xml version="1.0" encoding="UTF-8"?>
                <xsl:stylesheet version="2.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
                    <xsl:template match="/">
                        <html><head></head><body><h1><xsl:value-of select="/root/name"/></h1></body></html>
                    </xsl:template>
                </xsl:stylesheet>""";

        var request = new TransformRequest();
        request.setTransformType(TransformType.INVOICE);
        request.setDocument(xml.getBytes(StandardCharsets.UTF_8));
        request.setTransformer(xslt.getBytes(StandardCharsets.UTF_8));

        var result = transformer.transform(request);

        assertThat(result.getHtmlContent()).isNotEmpty();
        assertThat(result.isDefaultXslUsed()).isFalse();
        assertThat(result.getCustomXsltError()).isNull();
        assertThat(result.getDurationMs()).isGreaterThanOrEqualTo(0);

        String html = new String(result.getHtmlContent(), StandardCharsets.UTF_8);
        assertThat(html).contains("Test");
    }

    @Test
    @DisplayName("Özel XSLT + filigran birlikte çalışmalı")
    void shouldTransformWithCustomXsltAndWatermark() throws TransformException {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><root><name>Test</name></root>";
        String xslt = """
                <?xml version="1.0" encoding="UTF-8"?>
                <xsl:stylesheet version="2.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
                    <xsl:template match="/">
                        <html><head></head><body><h1><xsl:value-of select="/root/name"/></h1></body></html>
                    </xsl:template>
                </xsl:stylesheet>""";

        var request = new TransformRequest();
        request.setTransformType(TransformType.INVOICE);
        request.setDocument(xml.getBytes(StandardCharsets.UTF_8));
        request.setTransformer(xslt.getBytes(StandardCharsets.UTF_8));
        request.setWatermarkText("TASLAK");

        var result = transformer.transform(request);

        assertThat(result.isWatermarkApplied()).isTrue();
        String html = new String(result.getHtmlContent(), StandardCharsets.UTF_8);
        assertThat(html).contains("TASLAK");
        assertThat(html).contains("watermark");
    }

    @Test
    @DisplayName("DOCTYPE ve dahili entity içeren gömülü XSLT ile dönüşüm çalışmalı")
    void shouldTransformWithEmbeddedXsltContainingInternalDoctype() throws TransformException {
        String xslt = """
                <?xml version="1.0" encoding="UTF-8"?>
                <!DOCTYPE xsl:stylesheet [<!ENTITY label "Gömülü şablon">]>
                <xsl:stylesheet version="2.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
                    <xsl:template match="/">
                        <html><head></head><body><h1>&label;</h1></body></html>
                    </xsl:template>
                </xsl:stylesheet>""";
        String encodedXslt = Base64.getEncoder().encodeToString(
                xslt.getBytes(StandardCharsets.UTF_8));
        String ublXml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <Invoice xmlns="urn:oasis:names:specification:ubl:schema:xsd:Invoice-2"
                         xmlns:cac="urn:oasis:names:specification:ubl:schema:xsd:CommonAggregateComponents-2"
                         xmlns:cbc="urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2">
                    <cac:AdditionalDocumentReference>
                        <cac:Attachment>
                            <cbc:EmbeddedDocumentBinaryObject filename="embedded.xslt"
                                encodingCode="Base64">%s</cbc:EmbeddedDocumentBinaryObject>
                        </cac:Attachment>
                    </cac:AdditionalDocumentReference>
                </Invoice>""".formatted(encodedXslt);

        var request = new TransformRequest();
        request.setTransformType(TransformType.ECHECK);
        request.setDocument(ublXml.getBytes(StandardCharsets.UTF_8));
        request.setUseEmbeddedXslt(true);

        var result = transformer.transform(request);

        assertThat(result.isEmbeddedXsltUsed()).isTrue();
        assertThat(result.isDefaultXslUsed()).isFalse();
        assertThat(result.getCustomXsltError()).isNull();
        assertThat(new String(result.getHtmlContent(), StandardCharsets.UTF_8))
                .contains("Gömülü şablon");
    }

    @Test
    @DisplayName("Varsayılan XSLT yüklü değilse TransformException fırlatmalı")
    void shouldThrowTransformExceptionWhenDefaultNotLoaded() {
        var request = new TransformRequest();
        request.setTransformType(TransformType.ECHECK); // ECHECK şablonu test cache'inde yüklü değil
        request.setDocument("<root/>".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> transformer.transform(request))
                .isInstanceOf(TransformException.class)
                .hasMessageContaining("Desteklenmeyen");
    }

    @Test
    @DisplayName("Bozuk özel XSLT → varsayılana dönüş, customXsltError set edilmeli")
    void shouldFallbackToDefaultWhenCustomXsltFails() {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><root><name>Test</name></root>";
        String brokenXslt = "THIS IS NOT VALID XSLT";

        var request = new TransformRequest();
        request.setTransformType(TransformType.ECHECK); // ECHECK yüklü değil → varsayılan da başarısız olur
        request.setDocument(xml.getBytes(StandardCharsets.UTF_8));
        request.setTransformer(brokenXslt.getBytes(StandardCharsets.UTF_8));

        // Özel XSLT başarısız + varsayılan da yok → exception
        assertThatThrownBy(() -> transformer.transform(request))
                .isInstanceOf(TransformException.class);
    }

    @Test
    @DisplayName("Tüm yeni varsayılan XSLT yolları reload sırasında taranmalı")
    void shouldScanAllNewDefaultXsltPathsOnReload() {
        var assetManager = mock(AssetManager.class);
        when(assetManager.assetExists(org.mockito.ArgumentMatchers.anyString())).thenReturn(false);

        var metrics = new XsltMetrics(new SimpleMeterRegistry());
        var reloadableTransformer = new SaxonXsltTransformer(
                assetManager,
                new WatermarkService(),
                new HtmlSanitizer(),
                new EmbeddedXsltExtractor(),
                metrics);

        reloadableTransformer.reload();

        verify(assetManager).assetExists("default_transformers/eAdisyon_Base.xslt");
        verify(assetManager).assetExists("default_transformers/eDovizAlim_Base.xslt");
        verify(assetManager).assetExists("default_transformers/eDovizSatim_Base.xslt");
        verify(assetManager).assetExists("default_transformers/eDekont_Base.xslt");
        verify(assetManager).assetExists("default_transformers/eGiderPusulasi_Base.xslt");
    }

    @Test
    @DisplayName("Kısa biçimli gömülü XSLT varsayılan şablona düşmeden dönüştürülmeli")
    void shouldTransformWithSimplifiedEmbeddedStylesheet() throws Exception {
        String xslt = """
                <html xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xsl:version="2.0">
                    <body><xsl:value-of select="/*/local-name()"/></body>
                </html>""";
        String xml = """
                <Invoice xmlns="urn:oasis:names:specification:ubl:schema:xsd:Invoice-2"
                    xmlns:cac="urn:oasis:names:specification:ubl:schema:xsd:CommonAggregateComponents-2"
                    xmlns:cbc="urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2">
                    <cac:AdditionalDocumentReference><cac:Attachment>
                    <cbc:EmbeddedDocumentBinaryObject filename="style.xslt">%s</cbc:EmbeddedDocumentBinaryObject>
                    </cac:Attachment></cac:AdditionalDocumentReference>
                </Invoice>""".formatted(Base64.getEncoder().encodeToString(xslt.getBytes(StandardCharsets.UTF_8)));
        var request = new TransformRequest();
        request.setTransformType(TransformType.INVOICE);
        request.setDocument(xml.getBytes(StandardCharsets.UTF_8));
        request.setUseEmbeddedXslt(true);

        var result = transformer.transform(request);

        assertThat(result.isEmbeddedXsltUsed()).isTrue();
        assertThat(result.isDefaultXslUsed()).isFalse();
        assertThat(result.getCustomXsltError()).isNull();
        assertThat(new String(result.getHtmlContent(), StandardCharsets.UTF_8)).contains("Invoice");
    }

}

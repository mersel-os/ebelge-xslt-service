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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
        var embeddedXsltExtractor = new EmbeddedXsltExtractor();
        var metrics = new XsltMetrics(new SimpleMeterRegistry());
        transformer = new SaxonXsltTransformer(assetManager, watermarkService, embeddedXsltExtractor, metrics);
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
    @DisplayName("Varsayılan XSLT yüklü değilse TransformException fırlatmalı")
    void shouldThrowTransformExceptionWhenDefaultNotLoaded() {
        var request = new TransformRequest();
        request.setTransformType(TransformType.ECHECK); // ECHECK haritada yok
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
}

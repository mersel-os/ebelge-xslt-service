package io.mersel.services.xslt.infrastructure.diagnostics;

import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.XsltCompiler;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import javax.xml.transform.stream.StreamSource;
import java.io.StringReader;

/**
 * Saxon HE motoru sağlık kontrolü.
 * <p>
 * Saxon motorunun çalışır durumda olduğunu basit bir XSLT derleme ile doğrular.
 */
@Component
public class SaxonHealthCheck implements HealthIndicator {

    private static final String TEST_XSLT = """
            <?xml version="1.0" encoding="UTF-8"?>
            <xsl:stylesheet version="2.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
                <xsl:template match="/">
                    <result>OK</result>
                </xsl:template>
            </xsl:stylesheet>""";

    /** Tekrar tekrar oluşturmamak için sınıf seviyesinde tutulan Processor */
    private final Processor processor = new Processor(false);

    @Override
    public Health health() {
        try {
            XsltCompiler compiler = processor.newXsltCompiler();
            compiler.compile(new StreamSource(new StringReader(TEST_XSLT)));

            return Health.up()
                    .withDetail("engine", "Saxon HE")
                    .withDetail("version", net.sf.saxon.Version.getProductVersion())
                    .withDetail("xsltVersion", "3.0")
                    .build();

        } catch (Exception e) {
            return Health.down()
                    .withDetail("engine", "Saxon HE")
                    .withDetail("error", e.getMessage())
                    .build();
        }
    }
}

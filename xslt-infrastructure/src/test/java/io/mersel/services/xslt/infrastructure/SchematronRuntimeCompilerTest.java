package io.mersel.services.xslt.infrastructure;

import io.mersel.services.xslt.infrastructure.diagnostics.XsltMetrics;
import net.sf.saxon.s9api.SaxonApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * SchematronRuntimeCompiler birim testleri.
 * <p>
 * 3 adımlı ISO Schematron pipeline, xsl:variable→xsl:param post-processing
 * ve allow-foreign (xsl:function) desteğini doğrular.
 */
@DisplayName("SchematronRuntimeCompiler")
class SchematronRuntimeCompilerTest {

    private SchematronRuntimeCompiler compiler;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() throws Exception {
        var metrics = new XsltMetrics(new io.micrometer.core.instrument.simple.SimpleMeterRegistry());
        compiler = new SchematronRuntimeCompiler(metrics);
        // @PostConstruct init() - pipeline XSL'leri classpath'ten yükle
        Method init = SchematronRuntimeCompiler.class.getDeclaredMethod("init");
        init.setAccessible(true);
        init.invoke(compiler);
    }

    @Test
    @DisplayName("Basit Schematron XML derlenebilmeli")
    void basit_schematron_xml_derle() throws Exception {
        String sch = """
                <?xml version="1.0" encoding="UTF-8"?>
                <schema xmlns="http://purl.oclc.org/dsdl/schematron" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
                  <pattern id="test-pattern">
                    <rule context="//element">
                      <assert test="@attr" id="TestRule">Element must have attr</assert>
                    </rule>
                  </pattern>
                </schema>
                """;
        Path schPath = tempDir.resolve("minimal.sch");
        Files.writeString(schPath, sch);

        var result = compiler.compileAndReturn(schPath);

        assertThat(result).isNotNull();
        assertThat(result.generatedXslt()).isNotNull();
        assertThat(result.generatedXslt().length).isGreaterThan(0);
        assertThat(result.executable()).isNotNull();
    }

    @Test
    @DisplayName("Gömülü xsl:function ile Schematron derlenebilmeli (allow-foreign)")
    void embedded_xsl_function_allow_foreign() throws Exception {
        String sch = """
                <?xml version="1.0" encoding="UTF-8"?>
                <schema xmlns="http://purl.oclc.org/dsdl/schematron"
                        xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                        xmlns:localFunctions="http://example.com/local">
                  <xsl:function name="localFunctions:myFunc">
                    <xsl:param name="input"/>
                    <xsl:value-of select="$input"/>
                  </xsl:function>
                  <pattern id="test">
                    <rule context="//root">
                      <assert test="true()" id="AlwaysPass">OK</assert>
                    </rule>
                  </pattern>
                </schema>
                """;
        Path schPath = tempDir.resolve("with-function.sch");
        Files.writeString(schPath, sch);

        var result = compiler.compileAndReturn(schPath);

        assertThat(result).isNotNull();
        assertThat(result.generatedXslt()).isNotNull();
        assertThat(result.executable()).isNotNull();
    }

    @Test
    @DisplayName("postProcessVariablesToParams: xsl:variable → xsl:param dönüşümü")
    void postProcess_variable_to_param() throws Exception {
        String xslt = """
                <?xml version="1.0" encoding="UTF-8"?>
                <xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="2.0">
                  <xsl:variable name="type" select="'efatura'"/>
                  <xsl:template match="/"/>
                </xsl:stylesheet>
                """;

        var method = SchematronRuntimeCompiler.class.getDeclaredMethod(
                "postProcessVariablesToParams", byte[].class);
        method.setAccessible(true);
        byte[] input = xslt.getBytes(StandardCharsets.UTF_8);
        byte[] output = (byte[]) method.invoke(compiler, input);

        String result = new String(output, StandardCharsets.UTF_8);
        assertThat(result).contains("<xsl:param name=\"type\" select=\"'efatura'\"/>");
        assertThat(result).doesNotContain("<xsl:variable name=\"type\" select=\"'efatura'\"/>");
    }

    @Test
    @DisplayName("Bozuk Schematron NPE yerine anlamlı hata fırlatmalı")
    void bozuk_schematron_graceful_failure() throws Exception {
        String invalidXml = "this is not valid xml at all <<<";
        Path schPath = tempDir.resolve("broken.sch");
        Files.writeString(schPath, invalidXml);

        assertThatThrownBy(() -> compiler.compileAndReturn(schPath))
                .isInstanceOf(SaxonApiException.class)
                .hasMessageNotContaining("NullPointerException");
    }

    @Test
    @DisplayName("compileAndReturn: generatedXslt UTF-8 decode edildiğinde <?xml ile başlamalı")
    void compileAndReturn_xslt_bytes() throws Exception {
        String sch = """
                <?xml version="1.0" encoding="UTF-8"?>
                <schema xmlns="http://purl.oclc.org/dsdl/schematron" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
                  <pattern id="p1">
                    <rule context="//doc">
                      <assert test="true()" id="A1">OK</assert>
                    </rule>
                  </pattern>
                </schema>
                """;
        Path schPath = tempDir.resolve("valid.sch");
        Files.writeString(schPath, sch);

        var result = compiler.compileAndReturn(schPath);

        assertThat(result.generatedXslt()).isNotNull();
        String decoded = new String(result.generatedXslt(), StandardCharsets.UTF_8);
        assertThat(decoded).startsWith("<?xml");
    }

    @Test
    @DisplayName("Derlenen çıktı 3 adımlı pipeline işaretleri içermeli")
    void pipeline_3_adim_sirasi() throws Exception {
        String sch = """
                <?xml version="1.0" encoding="UTF-8"?>
                <schema xmlns="http://purl.oclc.org/dsdl/schematron" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
                  <pattern id="p1">
                    <rule context="//root">
                      <assert test="@id" id="HasId">Must have id</assert>
                    </rule>
                  </pattern>
                </schema>
                """;
        Path schPath = tempDir.resolve("pattern.sch");
        Files.writeString(schPath, sch);

        var result = compiler.compileAndReturn(schPath);
        String xslt = new String(result.generatedXslt(), StandardCharsets.UTF_8);

        assertThat(xslt).contains("<xsl:template");
        assertThat(xslt).contains("xsl:");
        assertThat(xslt).contains("match=");
    }
}

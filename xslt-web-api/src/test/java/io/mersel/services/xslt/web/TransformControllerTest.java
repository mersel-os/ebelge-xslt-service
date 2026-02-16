package io.mersel.services.xslt.web;

import io.mersel.services.xslt.application.interfaces.IXsltTransformer;
import io.mersel.services.xslt.application.interfaces.IXsltTransformer.TransformException;
import io.mersel.services.xslt.application.models.TransformResult;
import io.mersel.services.xslt.infrastructure.diagnostics.XsltMetrics;
import io.mersel.services.xslt.web.controllers.TransformController;
import io.mersel.services.xslt.web.infrastructure.GlobalExceptionHandler;
import io.mersel.services.xslt.web.infrastructure.XsltHeaders;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * TransformController birim testleri — yeni HTTP-native response formatı.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("POST /v1/transform")
class TransformControllerTest {

    private MockMvc mockMvc;

    @Mock
    private IXsltTransformer xsltTransformer;

    @Mock
    private XsltMetrics xsltMetrics;

    @InjectMocks
    private TransformController transformController;

    @BeforeEach
    void setUp() throws Exception {
        // @Value alanlarını reflection ile set et (@InjectMocks bunları inject etmez)
        var sizeField = TransformController.class.getDeclaredField("maxDocumentSizeMb");
        sizeField.setAccessible(true);
        sizeField.setInt(transformController, 100);

        mockMvc = MockMvcBuilders
                .standaloneSetup(transformController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("200 OK + text/html body + metadata header'lar dönmeli")
    void shouldReturnHtmlWithMetadataHeaders() throws Exception {
        var result = TransformResult.builder()
                .htmlContent("<html><body>Fatura</body></html>".getBytes())
                .defaultXslUsed(true)
                .watermarkApplied(false)
                .durationMs(145)
                .build();

        when(xsltTransformer.transform(any())).thenReturn(result);

        var xmlFile = new MockMultipartFile("document", "test.xml", "text/xml",
                "<Invoice/>".getBytes());

        mockMvc.perform(multipart("/v1/transform")
                        .file(xmlFile)
                        .param("transformType", "INVOICE"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string("<html><body>Fatura</body></html>"))
                .andExpect(header().string(XsltHeaders.DEFAULT_USED, "true"))
                .andExpect(header().string(XsltHeaders.WATERMARK_APPLIED, "false"))
                .andExpect(header().string(XsltHeaders.DURATION_MS, "145"))
                .andExpect(header().exists(XsltHeaders.OUTPUT_SIZE));
    }

    @Test
    @DisplayName("Özel XSLT başarısız → X-Xslt-Custom-Error header'ı dönmeli")
    void shouldReturnCustomErrorHeaderOnFallback() throws Exception {
        var result = TransformResult.builder()
                .htmlContent("<html><body>Default</body></html>".getBytes())
                .defaultXslUsed(true)
                .customXsltError("XSLT compilation failed at line 42")
                .watermarkApplied(false)
                .durationMs(230)
                .build();

        when(xsltTransformer.transform(any())).thenReturn(result);

        var xmlFile = new MockMultipartFile("document", "test.xml", "text/xml",
                "<Invoice/>".getBytes());
        var xsltFile = new MockMultipartFile("transformer", "custom.xslt", "text/xml",
                "<xsl:stylesheet/>".getBytes());

        mockMvc.perform(multipart("/v1/transform")
                        .file(xmlFile)
                        .file(xsltFile)
                        .param("transformType", "INVOICE"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(header().string(XsltHeaders.DEFAULT_USED, "true"))
                .andExpect(header().string(XsltHeaders.CUSTOM_ERROR, "XSLT compilation failed at line 42"));
    }

    @Test
    @DisplayName("Filigran uygulandığında X-Xslt-Watermark-Applied: true dönmeli")
    void shouldIndicateWatermarkApplied() throws Exception {
        var result = TransformResult.builder()
                .htmlContent("<html><body>Filigranlı</body></html>".getBytes())
                .defaultXslUsed(false)
                .watermarkApplied(true)
                .durationMs(180)
                .build();

        when(xsltTransformer.transform(any())).thenReturn(result);

        var xmlFile = new MockMultipartFile("document", "test.xml", "text/xml",
                "<Invoice/>".getBytes());

        mockMvc.perform(multipart("/v1/transform")
                        .file(xmlFile)
                        .param("transformType", "INVOICE")
                        .param("watermarkText", "TASLAK"))
                .andExpect(status().isOk())
                .andExpect(header().string(XsltHeaders.WATERMARK_APPLIED, "true"));
    }

    @Test
    @DisplayName("400 Bad Request — boş belge için ProblemDetail dönmeli")
    void shouldReturn400ForEmptyDocument() throws Exception {
        var emptyFile = new MockMultipartFile("document", "empty.xml", "text/xml", new byte[0]);

        mockMvc.perform(multipart("/v1/transform")
                        .file(emptyFile)
                        .param("transformType", "INVOICE"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Geçersiz İstek"))
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    @DisplayName("400 Bad Request — geçersiz dönüşüm tipi için ProblemDetail dönmeli")
    void shouldReturn400ForInvalidTransformType() throws Exception {
        var xmlFile = new MockMultipartFile("document", "test.xml", "text/xml",
                "<Invoice/>".getBytes());

        mockMvc.perform(multipart("/v1/transform")
                        .file(xmlFile)
                        .param("transformType", "INVALID_TYPE"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("Geçersiz dönüşüm tipi")));
    }

    @Test
    @DisplayName("422 Unprocessable Entity — dönüşüm başarısız için ProblemDetail dönmeli")
    void shouldReturn422ForTransformFailure() throws Exception {
        when(xsltTransformer.transform(any()))
                .thenThrow(new TransformException("XML belgesi dönüştürülemedi: malformed content"));

        var xmlFile = new MockMultipartFile("document", "test.xml", "text/xml",
                "<Invoice/>".getBytes());

        mockMvc.perform(multipart("/v1/transform")
                        .file(xmlFile)
                        .param("transformType", "INVOICE"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Dönüşüm Başarısız"))
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("malformed content")));
    }

    // ── Gömülü (Embedded) XSLT Testleri ──────────────────────────────

    @Test
    @DisplayName("useEmbeddedXslt=true → X-Xslt-Embedded-Used: true header'ı dönmeli")
    void shouldReturnEmbeddedUsedHeader() throws Exception {
        var result = TransformResult.builder()
                .htmlContent("<html><body>Embedded</body></html>".getBytes())
                .defaultXslUsed(false)
                .embeddedXsltUsed(true)
                .watermarkApplied(false)
                .durationMs(200)
                .build();

        when(xsltTransformer.transform(any())).thenReturn(result);

        var xmlFile = new MockMultipartFile("document", "fatura.xml", "text/xml",
                "<Invoice/>".getBytes());

        mockMvc.perform(multipart("/v1/transform")
                        .file(xmlFile)
                        .param("transformType", "INVOICE")
                        .param("useEmbeddedXslt", "true"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(header().string(XsltHeaders.EMBEDDED_USED, "true"))
                .andExpect(header().string(XsltHeaders.DEFAULT_USED, "false"));
    }

    @Test
    @DisplayName("useEmbeddedXslt=true, gömülü XSLT bulunamadı → varsayılan + X-Xslt-Embedded-Used: false")
    void shouldFallbackToDefaultWhenNoEmbeddedXslt() throws Exception {
        var result = TransformResult.builder()
                .htmlContent("<html><body>Default</body></html>".getBytes())
                .defaultXslUsed(true)
                .embeddedXsltUsed(false)
                .watermarkApplied(false)
                .durationMs(150)
                .build();

        when(xsltTransformer.transform(any())).thenReturn(result);

        var xmlFile = new MockMultipartFile("document", "fatura.xml", "text/xml",
                "<Invoice/>".getBytes());

        mockMvc.perform(multipart("/v1/transform")
                        .file(xmlFile)
                        .param("transformType", "INVOICE")
                        .param("useEmbeddedXslt", "true"))
                .andExpect(status().isOk())
                .andExpect(header().string(XsltHeaders.EMBEDDED_USED, "false"))
                .andExpect(header().string(XsltHeaders.DEFAULT_USED, "true"));
    }
}

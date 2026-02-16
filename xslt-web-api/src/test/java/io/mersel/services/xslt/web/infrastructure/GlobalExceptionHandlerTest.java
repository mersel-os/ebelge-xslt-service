package io.mersel.services.xslt.web.infrastructure;

import io.mersel.services.xslt.application.interfaces.IXsltTransformer.TransformException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * GlobalExceptionHandler birim testleri.
 * <p>
 * Tüm exception handler'ların doğru HTTP durum kodu, ProblemDetail formatı
 * ve anlamlı hata mesajları döndüğünü doğrular.
 */
@DisplayName("GlobalExceptionHandler")
class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
    }

    @Test
    @DisplayName("handleTransformException — returns 422 with transform-failed type")
    void handleTransformException() {
        var ex = new TransformException("XML belgesi dönüştürülemedi");

        var problem = handler.handleTransformException(ex);

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY.value());
        assertThat(problem.getTitle()).isEqualTo("Dönüşüm Başarısız");
        assertThat(problem.getDetail()).contains("XML belgesi dönüştürülemedi");
        assertThat(problem.getType()).hasPath("/xslt/errors/transform-failed");
    }

    @Test
    @DisplayName("handleMaxUploadSize — returns 413 with payload-too-large type")
    void handleMaxUploadSize() {
        var ex = new MaxUploadSizeExceededException(1024);

        var problem = handler.handleMaxUploadSize(ex);

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE.value());
        assertThat(problem.getTitle()).isEqualTo("Dosya Boyutu Aşımı");
        assertThat(problem.getType()).hasPath("/xslt/errors/payload-too-large");
    }

    @Test
    @DisplayName("handleIllegalArgument — returns 400 with bad-request type")
    void handleIllegalArgument() {
        var ex = new IllegalArgumentException("Geçersiz dönüşüm tipi: INVALID");

        var problem = handler.handleIllegalArgument(ex);

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(problem.getTitle()).isEqualTo("Geçersiz İstek");
        assertThat(problem.getDetail()).contains("Geçersiz dönüşüm tipi: INVALID");
        assertThat(problem.getType()).hasPath("/xslt/errors/bad-request");
    }

    @Test
    @DisplayName("handleGenericException — returns 500 with generic message (no stack leak)")
    void handleGenericException() {
        var ex = new RuntimeException("NullPointerException at line 42");

        var problem = handler.handleGenericException(ex);

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.value());
        assertThat(problem.getTitle()).isEqualTo("Sunucu Hatası");
        // Detay mesajında internal hata bilgisi sızdırılmamalı
        assertThat(problem.getDetail()).doesNotContain("NullPointerException");
        assertThat(problem.getDetail()).contains("Beklenmeyen bir hata oluştu");
        assertThat(problem.getType()).hasPath("/xslt/errors/internal-error");
    }

    @Test
    @DisplayName("tüm handler'lar RFC 7807 type URI formatında — mersel.io base URI")
    void problem_detail_type_uri_formati() {
        var p1 = handler.handleTransformException(new TransformException("test"));
        var p2 = handler.handleIllegalArgument(new IllegalArgumentException("test"));
        var p3 = handler.handleGenericException(new RuntimeException("test"));

        assertThat(p1.getType().toString()).startsWith("https://mersel.io/xslt/errors/");
        assertThat(p2.getType().toString()).startsWith("https://mersel.io/xslt/errors/");
        assertThat(p3.getType().toString()).startsWith("https://mersel.io/xslt/errors/");
    }
}

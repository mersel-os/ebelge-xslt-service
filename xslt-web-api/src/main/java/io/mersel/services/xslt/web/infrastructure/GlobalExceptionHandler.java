package io.mersel.services.xslt.web.infrastructure;

import io.mersel.services.xslt.application.interfaces.IXsltTransformer.TransformException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.BindException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.net.URI;
import java.util.stream.Collectors;

/**
 * Global hata yöneticisi — RFC 7807 Problem Details.
 * <p>
 * Tüm controller'lardan çıkan istisnaları tutarlı bir JSON formatta döner:
 * <pre>
 * {
 *   "type": "https://mersel.io/xslt/errors/transform-failed",
 *   "title": "Dönüşüm Başarısız",
 *   "status": 422,
 *   "detail": "XML belgesi dönüştürülemedi: ..."
 * }
 * </pre>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String ERROR_BASE_URI = "https://mersel.io/xslt/errors/";

    /**
     * XSLT dönüşüm tamamen başarısız → 422 Unprocessable Entity.
     */
    @ExceptionHandler(TransformException.class)
    public ProblemDetail handleTransformException(TransformException ex) {
        log.warn("Transform hatası: {}", ex.getMessage());
        var problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
        problem.setType(URI.create(ERROR_BASE_URI + "transform-failed"));
        problem.setTitle("Dönüşüm Başarısız");
        return problem;
    }

    /**
     * Dosya boyutu aşımı → 413 Payload Too Large.
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ProblemDetail handleMaxUploadSize(MaxUploadSizeExceededException ex) {
        log.warn("Dosya boyutu aşımı: {}", ex.getMessage());
        var problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.PAYLOAD_TOO_LARGE,
                "Yüklenen dosya boyutu izin verilen sınırı aşıyor");
        problem.setType(URI.create(ERROR_BASE_URI + "payload-too-large"));
        problem.setTitle("Dosya Boyutu Aşımı");
        return problem;
    }

    /**
     * Genel istek hatası → 400 Bad Request.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleIllegalArgument(IllegalArgumentException ex) {
        log.warn("Geçersiz parametre: {}", ex.getMessage());
        var problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, ex.getMessage());
        problem.setType(URI.create(ERROR_BASE_URI + "bad-request"));
        problem.setTitle("Geçersiz İstek");
        return problem;
    }

    /**
     * Bean Validation hatası → 400 Bad Request.
     * Jakarta @Valid / @NotBlank / @Size gibi annotation hataları.
     */
    @ExceptionHandler(BindException.class)
    public ProblemDetail handleBindException(BindException ex) {
        String detail = ex.getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining("; "));
        log.warn("Doğrulama hatası: {}", detail);
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
        problem.setType(URI.create(ERROR_BASE_URI + "validation-error"));
        problem.setTitle("Doğrulama Hatası");
        return problem;
    }

    /**
     * Beklenmeyen hata → 500 Internal Server Error.
     */
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleGenericException(Exception ex) {
        log.error("Beklenmeyen hata: {}", ex.getMessage(), ex);
        var problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Beklenmeyen bir hata oluştu. Lütfen tekrar deneyin.");
        problem.setType(URI.create(ERROR_BASE_URI + "internal-error"));
        problem.setTitle("Sunucu Hatası");
        return problem;
    }
}

package io.mersel.services.xslt.web.controllers;

import io.mersel.services.xslt.application.enums.TransformType;
import io.mersel.services.xslt.application.interfaces.IXsltTransformer;
import io.mersel.services.xslt.application.interfaces.IXsltTransformer.TransformException;
import io.mersel.services.xslt.application.models.TransformRequest;
import io.mersel.services.xslt.application.models.TransformResult;
import io.mersel.services.xslt.infrastructure.diagnostics.XsltMetrics;
import io.mersel.services.xslt.web.dto.TransformRequestDto;
import io.mersel.services.xslt.web.infrastructure.XsltHeaders;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * XSLT dönüşüm endpoint'i.
 * <p>
 * Başarılı dönüşümde {@code text/html} body ile birlikte metadata'yı
 * {@code X-Xslt-*} response header'larında döner.
 * <p>
 * Hata durumunda RFC 7807 {@code application/problem+json} formatında yanıt döner.
 *
 * <h3>Başarılı Yanıt Örneği</h3>
 * <pre>
 * HTTP/1.1 200 OK
 * Content-Type: text/html; charset=utf-8
 * X-Xslt-Default-Used: false
 * X-Xslt-Duration-Ms: 145
 * X-Xslt-Watermark-Applied: true
 * X-Xslt-Output-Size: 45678
 *
 * &lt;html&gt;...&lt;/html&gt;
 * </pre>
 *
 * <h3>Özel XSLT Başarısız → Varsayılana Dönüş</h3>
 * <pre>
 * HTTP/1.1 200 OK
 * Content-Type: text/html; charset=utf-8
 * X-Xslt-Default-Used: true
 * X-Xslt-Custom-Error: XSLT compilation failed at line 42
 * X-Xslt-Duration-Ms: 230
 * </pre>
 */
@RestController
@RequestMapping("/v1")
@Tag(name = "Transform", description = "XSLT dönüşüm işlemleri (XML → HTML)")
public class TransformController {

    private static final Logger log = LoggerFactory.getLogger(TransformController.class);
    private static final MediaType TEXT_HTML_UTF8 = new MediaType("text", "html", StandardCharsets.UTF_8);

    @Value("${xslt.limits.max-document-size-mb:${XSLT_MAX_DOCUMENT_SIZE_MB:100}}")
    private int maxDocumentSizeMb;

    private final IXsltTransformer xsltTransformer;
    private final XsltMetrics xsltMetrics;

    public TransformController(IXsltTransformer xsltTransformer, XsltMetrics xsltMetrics) {
        this.xsltTransformer = xsltTransformer;
        this.xsltMetrics = xsltMetrics;
    }

    @Operation(
            summary = "XSLT Dönüşüm",
            description = """
                    XML belgesini XSLT şablonu ile HTML'e dönüştürür.
                    
                    **Başarılı yanıt:** `200 OK` + `text/html` body + `X-Xslt-*` metadata header'ları.
                    
                    **Dönüşüm Tipleri:** INVOICE, ARCHIVE_INVOICE, DESPATCH_ADVICE, RECEIPT_ADVICE, EMM, ECHECK
                    
                    **XSLT Seçim Önceliği:**
                    1. `transformer` dosyası yüklendiyse → onu kullan
                    2. `useEmbeddedXslt=true` ve belgede gömülü XSLT varsa → belgeden çıkar ve kullan
                    3. Hiçbiri yoksa → varsayılan XSLT şablonu
                    
                    Gömülü XSLT, UBL-TR belgelerindeki `AdditionalDocumentReference/EmbeddedDocumentBinaryObject`
                    içinden `.xslt` uzantılı dosya olarak çıkarılır. Başarısız olursa varsayılana geri dönülür.
                    """,
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "Dönüşüm başarılı — ham HTML içerik",
                            content = @Content(mediaType = "text/html"),
                            headers = {
                                    @Header(name = "X-Xslt-Default-Used", description = "Varsayılan XSLT kullanıldı mı", schema = @Schema(type = "boolean")),
                                    @Header(name = "X-Xslt-Embedded-Used", description = "Belgeden çıkarılan gömülü XSLT kullanıldı mı", schema = @Schema(type = "boolean")),
                                    @Header(name = "X-Xslt-Custom-Error", description = "Özel/gömülü XSLT hata mesajı (varsa)", schema = @Schema(type = "string")),
                                    @Header(name = "X-Xslt-Duration-Ms", description = "İşlem süresi (ms)", schema = @Schema(type = "integer")),
                                    @Header(name = "X-Xslt-Watermark-Applied", description = "Filigran uygulandı mı", schema = @Schema(type = "boolean")),
                                    @Header(name = "X-Xslt-Output-Size", description = "Çıktı boyutu (byte)", schema = @Schema(type = "integer"))
                            }
                    ),
                    @ApiResponse(responseCode = "400", description = "Geçersiz istek (eksik dosya, geçersiz tip)", content = @Content(mediaType = "application/problem+json")),
                    @ApiResponse(responseCode = "422", description = "Dönüşüm başarısız (XML dönüştürülemedi)", content = @Content(mediaType = "application/problem+json"))
            }
    )
    @PostMapping(value = "/transform", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<byte[]> transform(
            @ModelAttribute @Valid TransformRequestDto requestDto) throws IOException, TransformException {

        // ── Girdi doğrulama ────────────────────────────────────────────
        if (requestDto.getDocument() == null || requestDto.getDocument().isEmpty()) {
            throw new IllegalArgumentException("XML belgesi boş olamaz");
        }
        if (requestDto.getDocument().getSize() > maxDocumentSizeMb * 1024L * 1024L) {
            throw new IllegalArgumentException(
                    "Belge boyutu çok büyük: " + (requestDto.getDocument().getSize() / (1024 * 1024))
                            + " MB. Maksimum izin verilen: " + maxDocumentSizeMb + " MB");
        }

        TransformType transformType;
        try {
            transformType = TransformType.valueOf(requestDto.getTransformType());
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new IllegalArgumentException(
                    "Geçersiz dönüşüm tipi: " + requestDto.getTransformType()
                            + ". Geçerli değerler: INVOICE, ARCHIVE_INVOICE, DESPATCH_ADVICE, RECEIPT_ADVICE, EMM, ECHECK");
        }

        boolean hasCustomXslt = requestDto.getTransformer() != null && !requestDto.getTransformer().isEmpty();
        if (hasCustomXslt && requestDto.getTransformer().getSize() > 10L * 1024 * 1024) {
            throw new IllegalArgumentException(
                    "XSLT şablonu çok büyük: " + (requestDto.getTransformer().getSize() / (1024 * 1024))
                            + " MB. Maksimum izin verilen: 10 MB");
        }
        boolean useEmbedded = Boolean.TRUE.equals(requestDto.getUseEmbeddedXslt());

        log.info("Dönüşüm isteği — Tip: {}, Özel XSLT: {}, Gömülü XSLT: {}, Filigran: {}",
                transformType, hasCustomXslt, useEmbedded, requestDto.getWatermarkText() != null);

        // ── İstek modeli oluştur ───────────────────────────────────────
        var request = new TransformRequest();
        request.setTransformType(transformType);
        request.setDocument(requestDto.getDocument().getBytes());
        request.setWatermarkText(requestDto.getWatermarkText());
        request.setUseEmbeddedXslt(useEmbedded);

        if (hasCustomXslt) {
            request.setTransformer(requestDto.getTransformer().getBytes());
        }

        // ── Dönüşüm (TransformException fırlarsa GlobalExceptionHandler yakalar) ──
        TransformResult result = xsltTransformer.transform(request);

        int outputSize = result.getHtmlContent() != null ? result.getHtmlContent().length : 0;
        xsltMetrics.recordTransform(
                transformType.name(),
                hasCustomXslt,
                result.isDefaultXslUsed(),
                result.getDurationMs(),
                outputSize);

        // Gömülü XSLT kullanım metrikleri
        if (useEmbedded) {
            xsltMetrics.recordEmbeddedXslt(result.isEmbeddedXsltUsed() ? "success" : "not_found");
        }

        log.info("Dönüşüm tamamlandı — Varsayılan: {}, Gömülü: {}, Süre: {} ms, Boyut: {} byte",
                result.isDefaultXslUsed(), result.isEmbeddedXsltUsed(), result.getDurationMs(),
                outputSize);

        // ── HTTP yanıtı oluştur ────────────────────────────────────────
        var headers = new HttpHeaders();
        headers.setContentType(TEXT_HTML_UTF8);
        headers.set(XsltHeaders.DEFAULT_USED, String.valueOf(result.isDefaultXslUsed()));
        headers.set(XsltHeaders.EMBEDDED_USED, String.valueOf(result.isEmbeddedXsltUsed()));
        headers.set(XsltHeaders.DURATION_MS, String.valueOf(result.getDurationMs()));
        headers.set(XsltHeaders.WATERMARK_APPLIED, String.valueOf(result.isWatermarkApplied()));
        headers.set(XsltHeaders.OUTPUT_SIZE, String.valueOf(result.getHtmlContent().length));

        if (result.getCustomXsltError() != null) {
            // CRLF sanitize — HTTP Response Splitting koruması
            String sanitized = result.getCustomXsltError()
                    .replaceAll("[\\r\\n]", " ");
            if (sanitized.length() > 500) {
                sanitized = sanitized.substring(0, 500);
            }
            headers.set(XsltHeaders.CUSTOM_ERROR, sanitized);
        }

        return new ResponseEntity<>(result.getHtmlContent(), headers, HttpStatus.OK);
    }
}

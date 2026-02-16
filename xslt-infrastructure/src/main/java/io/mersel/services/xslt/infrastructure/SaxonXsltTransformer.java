package io.mersel.services.xslt.infrastructure;

import io.mersel.services.xslt.application.enums.TransformType;
import io.mersel.services.xslt.application.interfaces.IXsltTransformer;
import io.mersel.services.xslt.application.interfaces.Reloadable;
import io.mersel.services.xslt.application.interfaces.ReloadResult;
import io.mersel.services.xslt.application.models.TransformRequest;
import io.mersel.services.xslt.application.models.TransformResult;
import io.mersel.services.xslt.infrastructure.diagnostics.XsltMetrics;
import net.sf.saxon.s9api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.xml.transform.stream.StreamSource;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Saxon HE tabanlı XSLT dönüşüm implementasyonu.
 * <p>
 * XML belgelerini XSLT şablonları ile HTML'e dönüştürür.
 * Varsayılan şablonlar uygulama başlangıcında ön-derlenir.
 * <p>
 * {@link Reloadable} arayüzü ile hot-reload destekler.
 */
@Service
public class SaxonXsltTransformer implements IXsltTransformer, Reloadable {

    private static final Logger log = LoggerFactory.getLogger(SaxonXsltTransformer.class);

    private final AssetManager assetManager;
    private final WatermarkService watermarkService;
    private final EmbeddedXsltExtractor embeddedXsltExtractor;
    private final XsltMetrics metrics;
    private final Processor processor;

    private static final Map<TransformType, String> TRANSFORM_XSL_MAP = Map.of(
            TransformType.INVOICE, "default_transformers/eInvoice_Base.xslt",
            TransformType.ARCHIVE_INVOICE, "default_transformers/eArchive_Base.xslt",
            TransformType.DESPATCH_ADVICE, "default_transformers/eDespatch_Base.xslt",
            TransformType.RECEIPT_ADVICE, "default_transformers/eDespatch_Answer_Base.xslt",
            TransformType.EMM, "default_transformers/eMM_Base.xslt",
            TransformType.ESMM, "default_transformers/eSMM_Base.xslt"
    );

    /**
     * Derlenmiş XSLT cache — volatile ile atomic swap.
     */
    private volatile Map<TransformType, XsltExecutable> compiledTransforms = Map.of();

    public SaxonXsltTransformer(AssetManager assetManager, WatermarkService watermarkService,
                               EmbeddedXsltExtractor embeddedXsltExtractor, XsltMetrics metrics) {
        this.assetManager = assetManager;
        this.watermarkService = watermarkService;
        this.embeddedXsltExtractor = embeddedXsltExtractor;
        this.metrics = metrics;
        this.processor = new Processor(false);
    }

    // ── Reloadable ──────────────────────────────────────────────────

    @Override
    public String getName() {
        return "XSLT Templates";
    }

    @Override
    public ReloadResult reload() {
        long startTime = System.currentTimeMillis();
        var newCache = new HashMap<TransformType, XsltExecutable>();
        var errors = new ArrayList<String>();
        var compiler = processor.newXsltCompiler();
        int missingCount = 0;

        for (var entry : TRANSFORM_XSL_MAP.entrySet()) {
            try {
                if (assetManager.assetExists(entry.getValue())) {
                    try (var is = assetManager.getAssetStream(entry.getValue())) {
                        var executable = compiler.compile(new StreamSource(is));
                        newCache.put(entry.getKey(), executable);
                        log.debug("  {} XSLT şablonu derlendi", entry.getKey());
                    }
                } else {
                    missingCount++;
                    log.info("  {} varsayılan XSLT şablonu mevcut değil: {} (kullanıcı XSLT veya gömülü XSLT kullanılabilir)",
                            entry.getKey(), entry.getValue());
                }
            } catch (Exception e) {
                String error = entry.getKey() + " XSLT derlenemedi: " + e.getMessage();
                errors.add(error);
                log.warn("  {}", error);
            }
        }

        // Atomic swap
        compiledTransforms = Map.copyOf(newCache);

        long elapsed = System.currentTimeMillis() - startTime;

        if (!errors.isEmpty() && !newCache.isEmpty()) {
            // Derleme hatası olan var, ama bazıları başarılı
            return ReloadResult.partial(getName(), newCache.size(), elapsed, errors);
        } else if (!errors.isEmpty()) {
            // Tümü derleme hatası
            return ReloadResult.failed(getName(), elapsed, String.join("; ", errors));
        } else {
            // Başarılı — dosya bulunamayanlar hata değil, bilgi niteliğinde
            return ReloadResult.success(getName(), newCache.size(), elapsed);
        }
    }

    // ── Dönüşüm ────────────────────────────────────────────────────

    @Override
    public TransformResult transform(TransformRequest request) throws TransformException {
        long startTime = System.nanoTime();

        String customXsltError = null;
        boolean defaultXslUsed = false;
        boolean embeddedXsltUsed = false;
        byte[] htmlContent;

        // ── XSLT Seçim Önceliği ───────────────────────────────────────
        //   1. Kullanıcının yüklediği XSLT dosyası (transformer)
        //   2. Belgenin içindeki gömülü XSLT (useEmbeddedXslt=true)
        //   3. Varsayılan XSLT şablonu (transformType'a göre)
        // ───────────────────────────────────────────────────────────────

        if (request.getTransformer() != null && request.getTransformer().length > 0) {
            try {
                htmlContent = transformWithCustomXslt(request.getDocument(), request.getTransformer());
                log.info("Kullanıcının yüklediği XSLT ile dönüşüm başarılı");
            } catch (Exception e) {
                customXsltError = e.getMessage();
                log.warn("Yüklenen XSLT başarısız, varsayılana dönülüyor: {}", e.getMessage());
                htmlContent = transformWithDefault(request.getDocument(), request.getTransformType());
                defaultXslUsed = true;
            }

        } else if (request.isUseEmbeddedXslt()) {
            byte[] embeddedXslt = embeddedXsltExtractor.extract(request.getDocument());

            if (embeddedXslt != null && embeddedXslt.length > 0) {
                try {
                    htmlContent = transformWithCustomXslt(request.getDocument(), embeddedXslt);
                    embeddedXsltUsed = true;
                    log.info("Belgeden çıkarılan gömülü XSLT ile dönüşüm başarılı");
                } catch (Exception e) {
                    customXsltError = "Gömülü XSLT ile dönüşüm başarısız: " + e.getMessage();
                    log.warn("Gömülü XSLT başarısız, varsayılana dönülüyor: {}", e.getMessage());
                    htmlContent = transformWithDefault(request.getDocument(), request.getTransformType());
                    defaultXslUsed = true;
                }
            } else {
                log.info("Belgede gömülü XSLT bulunamadı, varsayılan kullanılıyor");
                htmlContent = transformWithDefault(request.getDocument(), request.getTransformType());
                defaultXslUsed = true;
            }

        } else {
            htmlContent = transformWithDefault(request.getDocument(), request.getTransformType());
            defaultXslUsed = true;
        }

        // ── Filigran ───────────────────────────────────────────────────
        boolean watermarkApplied = false;
        if (request.getWatermarkText() != null && !request.getWatermarkText().isBlank()) {
            htmlContent = watermarkService.addWatermark(htmlContent, request.getWatermarkText());
            watermarkApplied = true;
        }

        long durationMs = (System.nanoTime() - startTime) / 1_000_000;

        metrics.recordTransform(
                request.getTransformType().name(),
                !defaultXslUsed,
                defaultXslUsed,
                durationMs,
                htmlContent.length
        );

        return TransformResult.builder()
                .htmlContent(htmlContent)
                .defaultXslUsed(defaultXslUsed)
                .embeddedXsltUsed(embeddedXsltUsed)
                .customXsltError(customXsltError)
                .watermarkApplied(watermarkApplied)
                .durationMs(durationMs)
                .build();
    }

    private byte[] transformWithDefault(byte[] document, TransformType transformType) throws TransformException {
        XsltExecutable executable = compiledTransforms.get(transformType);
        if (executable == null) {
            metrics.recordError("transform");
            throw new TransformException(
                    "Desteklenmeyen dönüşüm tipi veya XSLT yüklü değil: " + transformType);
        }

        try {
            var transformer = executable.load30();
            var outputStream = new ByteArrayOutputStream();
            var serializer = processor.newSerializer(outputStream);
            transformer.transform(new StreamSource(new ByteArrayInputStream(document)), serializer);
            return outputStream.toByteArray();
        } catch (SaxonApiException e) {
            metrics.recordError("transform");
            throw new TransformException("XSLT dönüşüm hatası: " + e.getMessage(), e);
        }
    }

    /**
     * Kullanıcı tarafından sağlanan XSLT ile dönüşüm.
     * <p>
     * Güvenlik: URIResolver kısıtlanmıştır — xsl:import, xsl:include ve document()
     * fonksiyonu ile harici kaynaklara (HTTP, file:// vb.) erişim engellenir (SSRF koruması).
     */
    private byte[] transformWithCustomXslt(byte[] document, byte[] xsltContent) throws SaxonApiException {
        String xsltString = new String(xsltContent, StandardCharsets.UTF_8);
        xsltString = xsltString.replace("Windows-1254", "UTF-8");

        var compiler = processor.newXsltCompiler();
        // SSRF koruması — harici URI çözümlemesini engelle
        compiler.setURIResolver((href, base) -> {
            throw new javax.xml.transform.TransformerException(
                    "Güvenlik: Harici URI çözümlemesi devre dışı — " + href);
        });
        var executable = compiler.compile(new StreamSource(
                new ByteArrayInputStream(xsltString.getBytes(StandardCharsets.UTF_8))));
        var transformer = executable.load30();
        transformer.setURIResolver((href, base) -> {
            throw new javax.xml.transform.TransformerException(
                    "Güvenlik: Harici URI çözümlemesi devre dışı — " + href);
        });

        var outputStream = new ByteArrayOutputStream();
        var serializer = processor.newSerializer(outputStream);
        transformer.transform(new StreamSource(new ByteArrayInputStream(document)), serializer);

        return outputStream.toByteArray();
    }
}

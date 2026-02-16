package io.mersel.services.xslt.infrastructure.diagnostics;

import com.github.benmanes.caffeine.cache.Cache;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * XSLT servisi özel metrikleri.
 * <p>
 * Prometheus üzerinden dışa aktarılan tüm uygulama metriklerini yönetir.
 */
@Component
public class XsltMetrics {

    public static final String METER_NAME = "xslt-service";

    private final MeterRegistry registry;

    public XsltMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    /**
     * Asset reload metrikleri kaydet.
     *
     * @param success    Tüm bileşenler başarıyla yüklendi mi
     * @param durationMs Reload süresi (milisaniye)
     */
    public void recordReload(boolean success, long durationMs) {
        Counter.builder("xslt_asset_reload_total")
                .tag("status", success ? "success" : "failure")
                .description("Asset reload sayısı")
                .register(registry)
                .increment();

        Timer.builder("xslt_asset_reload_duration_seconds")
                .description("Asset reload süresi")
                .register(registry)
                .record(Duration.ofMillis(durationMs));
    }

    /**
     * GİB paket sync metrikleri kaydet.
     *
     * @param success    Sync başarılı mı
     * @param durationMs Sync süresi (milisaniye)
     */
    public void recordSync(boolean success, long durationMs) {
        Counter.builder("xslt_gib_sync_total")
                .tag("status", success ? "success" : "failure")
                .description("GİB paket sync sayısı")
                .register(registry)
                .increment();

        Timer.builder("xslt_gib_sync_duration_seconds")
                .description("GİB paket sync süresi")
                .register(registry)
                .record(Duration.ofMillis(durationMs));
    }

    /**
     * Validation isteği sayısını kaydet.
     *
     * @param schemaType     XSD şema tipi (INVOICE, DESPATCH_ADVICE vb.)
     * @param schematronType Schematron tipi (UBLTR_MAIN, EDEFTER_KEBIR vb.)
     */
    public void recordValidation(String schemaType, String schematronType) {
        Counter.builder("xslt_validation_total")
                .tag("schema_type", schemaType)
                .tag("schematron_type", schematronType)
                .description("Validation istek sayısı")
                .register(registry)
                .increment();
    }

    /**
     * Validation hata sayılarını kaydet.
     *
     * @param schemaErrors     XSD doğrulama hata sayısı
     * @param schematronErrors Schematron doğrulama hata sayısı
     */
    public void recordValidationErrors(int schemaErrors, int schematronErrors) {
        if (schemaErrors > 0) {
            Counter.builder("xslt_validation_errors_total")
                    .tag("type", "schema")
                    .description("Validation hata sayısı")
                    .register(registry)
                    .increment(schemaErrors);
        }
        if (schematronErrors > 0) {
            Counter.builder("xslt_validation_errors_total")
                    .tag("type", "schematron")
                    .description("Validation hata sayısı")
                    .register(registry)
                    .increment(schematronErrors);
        }
    }

    /**
     * Transform isteği sayısını kaydet.
     */
    public void recordTransform(String transformType) {
        Counter.builder("xslt_transform_total")
                .tag("transform_type", transformType)
                .description("Transform istek sayısı")
                .register(registry)
                .increment();
    }

    /**
     * XSD override cache boyutu için gauge kaydeder.
     * Gauge her scrape'de güncel cache boyutunu (estimated size) raporlar.
     *
     * @param cache Override schema cache (Caffeine)
     */
    public void registerXsdOverrideCacheSizeGauge(Cache<?, ?> cache) {
        Gauge.builder("xslt_xsd_override_cache_size", cache, c -> (double) c.estimatedSize())
                .description("Önbelleğe alınmış XSD override sayısı")
                .register(registry);
    }

    /**
     * Doğrulama metrikleri kaydet.
     *
     * @param type         "schema" veya "schematron"
     * @param documentType Belge tipi (INVOICE, DESPATCH_ADVICE vb.)
     * @param result       "valid", "invalid" veya "error"
     * @param durationMs   İşlem süresi (milisaniye)
     */
    public void recordValidation(String type, String documentType, String result, long durationMs) {
        Counter.builder("xslt_validations_total")
                .tag("type", type)
                .tag("document_type", documentType)
                .tag("result", result)
                .description("Toplam doğrulama sayısı")
                .register(registry)
                .increment();

        Timer.builder("xslt_validation_duration")
                .tag("type", type)
                .tag("document_type", documentType)
                .description("Doğrulama süresi")
                .register(registry)
                .record(Duration.ofMillis(durationMs));
    }

    /**
     * Dönüşüm metrikleri kaydet.
     *
     * @param transformType  Dönüşüm tipi
     * @param customXslt     Özel XSLT kullanıldı mı
     * @param defaultUsed    Varsayılan XSLT kullanıldı mı
     * @param durationMs     İşlem süresi (milisaniye)
     * @param outputBytes    Çıktı boyutu (byte)
     */
    public void recordTransform(String transformType, boolean customXslt, boolean defaultUsed, long durationMs, int outputBytes) {
        Counter.builder("xslt_transforms_total")
                .tag("transform_type", transformType)
                .tag("custom_xslt", String.valueOf(customXslt))
                .tag("default_used", String.valueOf(defaultUsed))
                .description("Toplam dönüşüm sayısı")
                .register(registry)
                .increment();

        Timer.builder("xslt_transform_duration")
                .tag("transform_type", transformType)
                .description("Dönüşüm süresi")
                .register(registry)
                .record(Duration.ofMillis(durationMs));

        registry.summary("xslt_transform_output_bytes", "transform_type", transformType)
                .record(outputBytes);
    }

    /**
     * Schematron derleme metrikleri kaydet.
     */
    public void recordSchematronCompilation(long durationMs) {
        Counter.builder("xslt_schematron_compilations_total")
                .description("Toplam schematron derleme sayısı")
                .register(registry)
                .increment();

        Timer.builder("xslt_schematron_compilation_duration")
                .description("Schematron derleme süresi")
                .register(registry)
                .record(Duration.ofMillis(durationMs));
    }

    /**
     * Hata metrikleri kaydet.
     */
    public void recordError(String operation) {
        Counter.builder("xslt_errors_total")
                .tag("operation", operation)
                .description("Toplam hata sayısı")
                .register(registry)
                .increment();
    }

    /**
     * Rate limit aşımı metrikleri kaydet.
     *
     * @param endpoint "validate" veya "transform"
     */
    public void recordRateLimitExceeded(String endpoint) {
        Counter.builder("xslt_rate_limit_exceeded_total")
                .tag("endpoint", endpoint)
                .description("Rate limit aşım sayısı")
                .register(registry)
                .increment();
    }

    /**
     * Doğrulama profili kullanım metrikleri kaydet.
     *
     * @param profile          Profil adı (veya "none")
     * @param suppressedCount  Bastırılan hata sayısı
     */
    public void recordProfileUsage(String profile, int suppressedCount) {
        Counter.builder("xslt_profile_usage_total")
                .tag("profile", profile)
                .description("Doğrulama profili kullanım sayısı")
                .register(registry)
                .increment();

        if (suppressedCount > 0) {
            Counter.builder("xslt_suppressed_errors_total")
                    .tag("profile", profile)
                    .description("Bastırılan hata sayısı")
                    .register(registry)
                    .increment(suppressedCount);
        }
    }

    /**
     * Belge tipi bazlı doğrulama dağılımı metrikleri kaydet.
     *
     * @param documentType Algılanan belge tipi (INVOICE, DESPATCH_ADVICE vb.)
     * @param valid        Doğrulama başarılı mı
     */
    public void recordDocumentTypeValidation(String documentType, boolean valid) {
        Counter.builder("xslt_document_type_validations_total")
                .tag("document_type", documentType)
                .tag("result", valid ? "valid" : "invalid")
                .description("Belge tipine göre doğrulama sayısı")
                .register(registry)
                .increment();
    }

    /**
     * Giriş denemesi metrikleri kaydet.
     *
     * @param result "success", "failure" veya "blocked"
     */
    public void recordLogin(String result) {
        Counter.builder("xslt_auth_login_total")
                .tag("result", result)
                .description("Giriş denemesi sayısı")
                .register(registry)
                .increment();
    }

    /**
     * Gömülü XSLT kullanım metrikleri kaydet.
     *
     * @param result "success", "failure" veya "not_found"
     */
    public void recordEmbeddedXslt(String result) {
        Counter.builder("xslt_embedded_xslt_total")
                .tag("result", result)
                .description("Gömülü XSLT kullanım sayısı")
                .register(registry)
                .increment();
    }
}

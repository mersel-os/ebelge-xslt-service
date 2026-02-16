package io.mersel.services.xslt.web.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

import io.mersel.services.xslt.infrastructure.diagnostics.XsltMetrics;
import io.mersel.services.xslt.web.dto.ErrorResponse;
import io.mersel.services.xslt.web.infrastructure.JsonResponseWriter;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * IP bazlı rate limiting.
 * <p>
 * Her endpoint grubu için ayrı limit tanımlanabilir.
 * Limitler environment variable'lardan yapılandırılır.
 * <p>
 * <strong>Güvenlik:</strong> İstemci IP çözümlemesi {@code behind-proxy} ayarına bağlıdır.
 * <ul>
 *   <li>{@code behind-proxy=false} (varsayılan): Sadece TCP bağlantısının uzak adresi
 *       ({@code request.getRemoteAddr()}) kullanılır. {@code X-Forwarded-For} ve
 *       {@code X-Real-IP} header'ları <strong>tamamen yok sayılır</strong>.
 *       Bu, istemcilerin sahte header göndererek rate limiting'i atlatmasını engeller.</li>
 *   <li>{@code behind-proxy=true}: Reverse proxy (nginx, Traefik, ALB vb.) arkasında
 *       çalışırken etkinleştirin. Sırasıyla {@code X-Forwarded-For} (ilk IP),
 *       {@code X-Real-IP}, son çare olarak {@code remoteAddr} kullanılır.
 *       <strong>Bu modu yalnızca güvenilir bir proxy arkasındayken açın!</strong></li>
 * </ul>
 * <p>
 * Env:
 * <ul>
 *   <li>{@code XSLT_RATE_LIMIT_ENABLED} — rate limiting açık/kapalı (varsayılan: true)</li>
 *   <li>{@code XSLT_RATE_LIMIT_VALIDATE} — /v1/validate için dakikada max istek (varsayılan: 30)</li>
 *   <li>{@code XSLT_RATE_LIMIT_TRANSFORM} — /v1/transform için dakikada max istek (varsayılan: 20)</li>
 *   <li>{@code XSLT_RATE_LIMIT_BEHIND_PROXY} — reverse proxy arkasında mı? (varsayılan: false)</li>
 * </ul>
 */
@Configuration
public class RateLimitConfig {

    private static final Logger log = LoggerFactory.getLogger(RateLimitConfig.class);

    @Value("${xslt.rate-limit.enabled:${XSLT_RATE_LIMIT_ENABLED:true}}")
    private boolean enabled;

    @Value("${xslt.rate-limit.validate:${XSLT_RATE_LIMIT_VALIDATE:30}}")
    private int validateLimit;

    @Value("${xslt.rate-limit.transform:${XSLT_RATE_LIMIT_TRANSFORM:20}}")
    private int transformLimit;

    @Value("${xslt.rate-limit.behind-proxy:${XSLT_RATE_LIMIT_BEHIND_PROXY:false}}")
    private boolean behindProxy;

    @Bean
    FilterRegistrationBean<RateLimitFilter> rateLimitFilter(XsltMetrics xsltMetrics) {
        var filter = new RateLimitFilter(enabled, validateLimit, transformLimit, behindProxy, xsltMetrics);
        var bean = new FilterRegistrationBean<>(filter);
        bean.addUrlPatterns("/v1/validate", "/v1/transform");
        bean.setOrder(Ordered.HIGHEST_PRECEDENCE + 1);

        if (enabled) {
            log.info("Rate limiting aktif — validate: {}/dk, transform: {}/dk, proxy modu: {} (IP bazlı)",
                    validateLimit, transformLimit, behindProxy ? "AÇIK" : "KAPALI");
            if (!behindProxy) {
                log.info("  → X-Forwarded-For/X-Real-IP header'ları yok sayılıyor (XSLT_RATE_LIMIT_BEHIND_PROXY=false)");
            }
        } else {
            log.info("Rate limiting devre dışı (XSLT_RATE_LIMIT_ENABLED=false)");
        }

        return bean;
    }

    /**
     * IP bazlı rate limiting filter.
     * Caffeine cache ile dakika penceresi uygulanır.
     * Limit aşıldığında 429 Too Many Requests döner.
     * <p>
     * IP çözümlemesi {@code behindProxy} bayrağına göre değişir:
     * <ul>
     *   <li>{@code false}: Yalnızca {@code request.getRemoteAddr()} — header spoofing koruması</li>
     *   <li>{@code true}: {@code X-Forwarded-For} → {@code X-Real-IP} → {@code remoteAddr} sırası</li>
     * </ul>
     */
    static class RateLimitFilter extends OncePerRequestFilter {

        private static final Logger filterLog = LoggerFactory.getLogger(RateLimitFilter.class);

        private final boolean enabled;
        private final int validateLimit;
        private final int transformLimit;
        private final boolean behindProxy;
        private final XsltMetrics xsltMetrics;

        private final Cache<String, AtomicInteger> validateCounts;
        private final Cache<String, AtomicInteger> transformCounts;

        RateLimitFilter(boolean enabled, int validateLimit, int transformLimit,
                        boolean behindProxy, XsltMetrics xsltMetrics) {
            this.enabled = enabled;
            this.validateLimit = validateLimit;
            this.transformLimit = transformLimit;
            this.behindProxy = behindProxy;
            this.xsltMetrics = xsltMetrics;
            this.validateCounts = buildCache();
            this.transformCounts = buildCache();
        }

        private static Cache<String, AtomicInteger> buildCache() {
            return Caffeine.newBuilder()
                    .maximumSize(50_000)
                    .expireAfterWrite(Duration.ofMinutes(1))
                    .build();
        }

        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                       FilterChain filterChain) throws ServletException, IOException {
            if (!enabled) {
                filterChain.doFilter(request, response);
                return;
            }

            String clientIp = resolveClientIp(request);
            String uri = request.getRequestURI();

            Cache<String, AtomicInteger> counts;
            int limit;
            if (uri.startsWith("/v1/validate")) {
                counts = validateCounts;
                limit = validateLimit;
            } else if (uri.startsWith("/v1/transform")) {
                counts = transformCounts;
                limit = transformLimit;
            } else {
                filterChain.doFilter(request, response);
                return;
            }

            AtomicInteger counter = counts.get(clientIp, k -> new AtomicInteger(0));
            int current = counter.incrementAndGet();

            response.setHeader("X-RateLimit-Limit", String.valueOf(limit));
            response.setHeader("X-RateLimit-Remaining", String.valueOf(Math.max(0, limit - current)));

            if (current > limit) {
                String endpoint = uri.startsWith("/v1/validate") ? "validate" : "transform";
                xsltMetrics.recordRateLimitExceeded(endpoint);
                JsonResponseWriter.write(response, HttpStatus.TOO_MANY_REQUESTS.value(),
                        new ErrorResponse("Rate limit exceeded",
                                "Dakika başına maksimum " + limit + " istek. Lütfen bekleyin."));
                return;
            }

            filterChain.doFilter(request, response);
        }

        /**
         * İstemci IP adresini çözümler.
         * <p>
         * {@code behindProxy=false} (varsayılan): TCP bağlantısının uzak adresi kullanılır.
         * Header'lar tamamen yok sayılır — saldırganın sahte X-Forwarded-For ile
         * rate limiting'i atlatması engellenir.
         * <p>
         * {@code behindProxy=true}: Güvenilir reverse proxy arkasında çalışırken
         * X-Forwarded-For (ilk IP) → X-Real-IP → remoteAddr sırasıyla çözümlenir.
         */
        private String resolveClientIp(HttpServletRequest request) {
            if (!behindProxy) {
                return request.getRemoteAddr();
            }

            String xff = request.getHeader("X-Forwarded-For");
            if (xff != null && !xff.isBlank()) {
                String clientIp = xff.split(",")[0].trim();
                if (filterLog.isTraceEnabled()) {
                    filterLog.trace("Proxy modu: X-Forwarded-For → {}", clientIp);
                }
                return clientIp;
            }

            String realIp = request.getHeader("X-Real-IP");
            if (realIp != null && !realIp.isBlank()) {
                if (filterLog.isTraceEnabled()) {
                    filterLog.trace("Proxy modu: X-Real-IP → {}", realIp.trim());
                }
                return realIp.trim();
            }

            return request.getRemoteAddr();
        }
    }
}

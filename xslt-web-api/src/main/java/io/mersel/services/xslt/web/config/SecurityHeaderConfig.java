package io.mersel.services.xslt.web.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Güvenlik response header'larını ekler.
 * <p>
 * Spring Security kullanılmadığından CSP, X-Frame-Options ve
 * X-Content-Type-Options basit bir servlet filter ile eklenir.
 * <p>
 * CSP politikası {@code xslt.security.csp} property'si ile özelleştirilebilir.
 */
@Configuration
public class SecurityHeaderConfig {

    private static final String DEFAULT_CSP =
            "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; font-src 'self'; connect-src 'self'";

    @Value("${xslt.security.csp:" + DEFAULT_CSP + "}")
    private String contentSecurityPolicy;

    @Bean
    FilterRegistrationBean<SecurityHeaderFilter> securityHeaderFilter() {
        var filter = new SecurityHeaderFilter(contentSecurityPolicy);
        var bean = new FilterRegistrationBean<>(filter);
        bean.addUrlPatterns("/*");
        bean.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return bean;
    }

    /**
     * Scalar API docs sayfası için gevşetilmiş CSP.
     * Scalar CDN (cdn.jsdelivr.net) ve Google Fonts kaynaklarını kullanır.
     */
    private static final String SCALAR_CSP =
            "default-src 'self'; "
                    + "script-src 'self' 'unsafe-inline' https://cdn.jsdelivr.net; "
                    + "style-src 'self' 'unsafe-inline' https://cdn.jsdelivr.net https://fonts.googleapis.com; "
                    + "font-src 'self' https://fonts.gstatic.com data:; "
                    + "img-src 'self' data: blob:; "
                    + "worker-src 'self' blob:; "
                    + "connect-src 'self' https://cdn.jsdelivr.net";

    /**
     * Güvenlik header'larını tüm yanıtlara ekler:
     * <ul>
     *   <li>Content-Security-Policy (Scalar sayfası için gevşetilmiş)</li>
     *   <li>X-Frame-Options: SAMEORIGIN</li>
     *   <li>X-Content-Type-Options: nosniff</li>
     * </ul>
     */
    static class SecurityHeaderFilter extends OncePerRequestFilter {

        private final String csp;

        SecurityHeaderFilter(String csp) {
            this.csp = csp;
        }

        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                       FilterChain filterChain) throws ServletException, IOException {
            String effectiveCsp = isScalarPage(request) ? SCALAR_CSP : csp;
            response.setHeader("Content-Security-Policy", effectiveCsp);
            response.setHeader("X-Frame-Options", "SAMEORIGIN");
            response.setHeader("X-Content-Type-Options", "nosniff");
            response.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");
            response.setHeader("Permissions-Policy", "camera=(), microphone=(), geolocation=()");
            filterChain.doFilter(request, response);
        }

        private boolean isScalarPage(HttpServletRequest request) {
            String uri = request.getRequestURI();
            return uri != null && (uri.equals("/scalar.html") || uri.startsWith("/scalar"));
        }
    }
}

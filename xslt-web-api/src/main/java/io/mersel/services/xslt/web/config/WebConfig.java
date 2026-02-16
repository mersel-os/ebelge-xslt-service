package io.mersel.services.xslt.web.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC yapılandırması.
 * <p>
 * CORS ve admin endpoint'leri için token tabanlı kimlik doğrulama
 * interceptor'ını yapılandırır.
 * <p>
 * CORS: Varsayılan olarak yalnızca aynı origin'e izin verir.
 * Production'da {@code XSLT_CORS_ALLOWED_ORIGINS} ile belirli origin'ler tanımlanabilir.
 * Geliştirme modunda {@code *} kullanılabilir.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final AdminAuthInterceptor adminAuthInterceptor;

    @Value("${xslt.cors.allowed-origins:}")
    private String allowedOriginsConfig;

    public WebConfig(AdminAuthInterceptor adminAuthInterceptor) {
        this.adminAuthInterceptor = adminAuthInterceptor;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        String[] origins;
        if (allowedOriginsConfig != null && !allowedOriginsConfig.isBlank()) {
            origins = allowedOriginsConfig.split(",");
        } else {
            // Yapılandırılmamışsa — sadece same-origin (SPA modunda sorun olmaz)
            origins = new String[0];
        }

        var mapping = registry.addMapping("/**")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .maxAge(3600);

        if (origins.length > 0) {
            mapping.allowedOrigins(origins).allowCredentials(true);
        }
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(adminAuthInterceptor)
                .addPathPatterns("/v1/admin/**");
    }
}

package io.mersel.services.xslt.web.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.IOException;

/**
 * SPA (Single Page Application) yönlendirme yapılandırması.
 * <p>
 * Statik dosya bulunamayan istekleri {@code index.html}'e yönlendirir,
 * böylece React Router client-side routing düzgün çalışır.
 */
@Configuration
public class SpaWebConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .resourceChain(true)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(String resourcePath, Resource location) throws IOException {
                        Resource resource = location.createRelative(resourcePath);
                        // Serve the resource if it exists, otherwise fall back to index.html
                        if (resource.exists() && resource.isReadable()) {
                            return resource;
                        }
                        // Don't intercept API paths
                        if (resourcePath.startsWith("v1/") || resourcePath.startsWith("v3/")
                                || resourcePath.startsWith("actuator/") || resourcePath.equals("scalar.html")) {
                            return null;
                        }
                        Resource indexHtml = new ClassPathResource("/static/index.html");
                        return indexHtml.exists() ? indexHtml : null;
                    }
                });
    }
}

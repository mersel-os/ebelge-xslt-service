package io.mersel.services.xslt.infrastructure.config;

import io.mersel.services.xslt.infrastructure.XsltSandboxPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Files;

/**
 * Altyapı katmanı Spring yapılandırması.
 * <p>
 * Bu katmandaki tüm bileşenleri (Saxon, JAXP, Metrics) otomatik tarar.
 * GİB sync ve XSLT sandbox yapılandırma özelliklerini etkinleştirir.
 */
@Configuration
@ComponentScan(basePackages = "io.mersel.services.xslt.infrastructure")
@EnableConfigurationProperties({GibSyncProperties.class, XsltSandboxProperties.class})
public class InfrastructureConfig {

    private static final Logger log = LoggerFactory.getLogger(InfrastructureConfig.class);

    /**
     * Varsayılan şablonlar ve Schematron için sandbox istisnaları. Geçersiz değerde uygulama başlamaz.
     */
    @Bean
    public XsltSandboxPolicy xsltSandboxPolicy(XsltSandboxProperties properties) {
        var policy = XsltSandboxPolicy.of(properties.getResourceDirs());
        if (policy.isStrict()) {
            log.info("XSLT sandbox: istisna yok (dosya erişimi kapalı)");
            return policy;
        }
        log.info("XSLT sandbox istisnası (varsayılan şablonlar ve Schematron): okunabilir dizinler={}",
                policy.resourceDirs());
        for (var dir : policy.resourceDirs()) {
            if (!Files.isDirectory(dir)) {
                log.warn("XSLT sandbox: okunabilir dizin henüz yok: {} (oluşturulunca kullanılır)", dir);
            }
        }
        return policy;
    }
}

package io.mersel.services.xslt.infrastructure.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * Altyapı katmanı Spring yapılandırması.
 * <p>
 * Bu katmandaki tüm bileşenleri (Saxon, JAXP, Metrics) otomatik tarar.
 * GİB sync yapılandırma özelliklerini etkinleştirir.
 */
@Configuration
@ComponentScan(basePackages = "io.mersel.services.xslt.infrastructure")
@EnableConfigurationProperties(GibSyncProperties.class)
public class InfrastructureConfig {
}

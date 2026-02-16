package io.mersel.services.xslt.web;

import io.mersel.services.xslt.infrastructure.config.InfrastructureConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

/**
 * MERSEL XSLT Service - Ana uygulama giriş noktası.
 * <p>
 * Saxon HE motoru ile XML Schema/Schematron doğrulama ve XSLT dönüşüm servisi.
 */
@SpringBootApplication
@Import(InfrastructureConfig.class)
public class XsltServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(XsltServiceApplication.class, args);
    }
}

package io.mersel.services.xslt.web.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * OpenAPI / Scalar dokümantasyon yapılandırması.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI xsltServiceOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("MERSEL XSLT Service API")
                        .description("""
                                Saxon HE motoru ile XML Schema/Schematron doğrulama ve XSLT dönüşüm servisi.
                                
                                ## Özellikler
                                - **XML Schema (XSD) Doğrulama** — UBL 2.1 belgelerini XSD şemalarına göre doğrular
                                - **Schematron Doğrulama** — İş kurallarını Schematron kurallarına göre doğrular
                                - **XSLT Dönüşüm** — XML belgelerini HTML'e dönüştürür (özel veya varsayılan XSLT ile)
                                - **Filigran Desteği** — Dönüştürülmüş HTML'e filigran ekler
                                - **Schematron Derleme** — ISO Schematron dosyalarından XSLT derleme
                                """)
                        .version("1.0.0")
                        .license(new License()
                                .name("MIT")
                                .url("https://github.com/mersel-os/ebelge-xslt-service/blob/main/LICENSE"))
                        .contact(new Contact()
                                .name("Mersel")
                                .url("https://mersel.io")))
                .servers(List.of(
                        new Server().url("/").description("Yerel sunucu")
                ));
    }
}

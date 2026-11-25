package io.mersel.services.xslt.web.infrastructure;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServer;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Gömülü Tomcat üzerinden gerçek HTTP yanıtı: MockMvc header'ı kodlamadığı için
 * ISO-8859-1 dışı karakterli header'ın Tomcat'te düşmesini yalnız bu test görür.
 */
@DisplayName("HeaderValueSanitizer — gömülü Tomcat")
class HeaderValueSanitizerTomcatTest {

    private static final String MESSAGE =
            "Gömülü XSLT ile dönüşüm başarısız: Fatura numarası boş — ğ/ş/ı kontrolü";

    private WebServer server;

    @BeforeEach
    void startTomcat() {
        var factory = new TomcatServletWebServerFactory(0);
        server = factory.getWebServer(context -> context
                .addServlet("headers", new HttpServlet() {
                    @Override
                    protected void doGet(HttpServletRequest req, HttpServletResponse resp) {
                        resp.setHeader("X-Raw", MESSAGE);
                        resp.setHeader("X-Sanitized", HeaderValueSanitizer.sanitize(MESSAGE, 500));
                        resp.setStatus(200);
                    }
                })
                .addMapping("/"));
        server.start();
    }

    @AfterEach
    void stopTomcat() {
        if (server != null) {
            server.stop();
        }
    }

    @Test
    @DisplayName("Ham Türkçe değer düşer, sadeleştirilmiş değer yanıta aynen ulaşır")
    void sanitizedHeaderSurvivesTomcat() throws Exception {
        HttpResponse<Void> response;
        try (var client = HttpClient.newHttpClient()) {
            var request = HttpRequest.newBuilder(URI.create("http://localhost:" + server.getPort() + "/")).build();
            response = client.send(request, HttpResponse.BodyHandlers.discarding());
        }

        assertThat(response.statusCode()).isEqualTo(200);
        // Hatanın kendisi: Tomcat ISO-8859-1'e kodlayamadığı header'ı atar
        assertThat(response.headers().firstValue("X-Raw")).isEmpty();
        // JDK HttpClient header baytlarını ISO-8859-1 olarak okur (.NET ve tarayıcı gibi)
        assertThat(response.headers().firstValue("X-Sanitized"))
                .hasValue("Gömülü XSLT ile dönüsüm basarisiz: Fatura numarasi bos - g/s/i kontrolü");
    }
}

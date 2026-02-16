package io.mersel.services.xslt.web.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;

import java.io.IOException;

/**
 * Servlet filter ve interceptor'larda JSON yanıt yazmak için yardımcı sınıf.
 * <p>
 * Spring MVC'nin {@code ResponseEntity} serileştirmesi filter/interceptor katmanında
 * kullanılamadığı için, bu utility Jackson {@link ObjectMapper} ile herhangi bir
 * Java nesnesini doğrudan {@code HttpServletResponse}'a yazar.
 * <p>
 * Elle JSON string oluşturmak yerine bu metot kullanılmalıdır:
 * <pre>
 * // Kötü: response.getWriter().write("{\"error\":\"...\"}");
 * // İyi:  JsonResponseWriter.write(response, 429, new ErrorResponse("Rate limit exceeded", "..."));
 * </pre>
 */
public final class JsonResponseWriter {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private JsonResponseWriter() {
    }

    /**
     * Verilen nesneyi JSON olarak HTTP yanıtına yazar.
     *
     * @param response HTTP yanıtı
     * @param status   HTTP durum kodu (ör: 401, 429)
     * @param body     Serileştirilecek nesne (record, Map, POJO vb.)
     * @throws IOException yazma hatası
     */
    public static void write(HttpServletResponse response, int status, Object body) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        MAPPER.writeValue(response.getOutputStream(), body);
    }
}

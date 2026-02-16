package io.mersel.services.xslt.web.config;

import io.mersel.services.xslt.web.dto.ErrorResponse;
import io.mersel.services.xslt.web.infrastructure.JsonResponseWriter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * {@code /v1/admin/**} endpoint'lerini Bearer token ile koruyan interceptor.
 * <p>
 * Her admin isteğinde {@code Authorization: Bearer <token>} header'ını kontrol eder.
 * Geçerli token yoksa tarayıcı native dialog'u tetiklemeden JSON 401 döner.
 * <p>
 * CORS preflight (OPTIONS) istekleri otomatik geçirilir.
 */
@Component
public class AdminAuthInterceptor implements HandlerInterceptor {

    private final AuthService authService;

    public AdminAuthInterceptor(AuthService authService) {
        this.authService = authService;
    }

    /** Auth gerektirmeyen admin GET endpoint'leri — herkes okuyabilir. */
    private static final java.util.Set<String> PUBLIC_READ_PATHS = java.util.Set.of(
            "/v1/admin/profiles",
            "/v1/admin/packages",
            "/v1/admin/auto-generated"
    );

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // CORS preflight'ı geçir
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        // Belirli GET endpoint'leri herkese açık (profil listesi vb.)
        if ("GET".equalsIgnoreCase(request.getMethod()) && PUBLIC_READ_PATHS.contains(request.getRequestURI())) {
            return true;
        }

        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            String username = authService.validateToken(token);
            if (username != null) {
                request.setAttribute("adminUsername", username);
                return true;
            }
        }

        JsonResponseWriter.write(response, HttpStatus.UNAUTHORIZED.value(),
                new ErrorResponse("Unauthorized", "Geçerli bir token gereklidir. POST /v1/auth/login ile giriş yapın."));
        return false;
    }
}

package io.mersel.services.xslt.web.controllers;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.mersel.services.xslt.web.config.AuthService;
import io.mersel.services.xslt.web.dto.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Token tabanlı kimlik doğrulama endpoint'leri.
 * <p>
 * Admin paneli ve profil yönetimi gibi korunan bölümler için
 * giriş/çıkış ve token doğrulama işlemlerini sağlar.
 * <p>
 * Akış:
 * <ol>
 *   <li>{@code POST /v1/auth/login} — kullanıcı adı ve parola ile giriş, token alır</li>
 *   <li>Korunan endpoint'lere {@code Authorization: Bearer <token>} ile istek atar</li>
 *   <li>{@code POST /v1/auth/logout} — token'ı geçersiz kılar</li>
 * </ol>
 */
@RestController
@RequestMapping("/v1/auth")
@Tag(name = "Auth", description = "Kimlik doğrulama (token tabanlı)")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * Kullanıcı adı ve parola ile giriş yapar, Bearer token döner.
     */
    @PostMapping("/login")
    @Operation(
            summary = "Giriş yap",
            description = "Kullanıcı adı ve parola doğrulanır. Başarılıysa Bearer token döner. "
                    + "Bu token, korunan endpoint'lere erişim için Authorization header'ında kullanılır."
    )
    @ApiResponse(responseCode = "200", description = "Giriş başarılı",
            content = @Content(schema = @Schema(implementation = LoginResponse.class)))
    @ApiResponse(responseCode = "401", description = "Geçersiz kimlik bilgileri",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        String token = authService.login(request.username(), request.password());

        if (token != null) {
            return ResponseEntity.ok(new LoginResponse(token, request.username(), "Giris basarili."));
        }

        return ResponseEntity.status(401).body(
                new ErrorResponse("Unauthorized", "Gecersiz kullanici adi veya parola."));
    }

    /**
     * Mevcut token'ı geçersiz kılar (çıkış).
     */
    @PostMapping("/logout")
    @Operation(
            summary = "Çıkış yap",
            description = "Mevcut Bearer token'ı geçersiz kılar."
    )
    @ApiResponse(responseCode = "200", description = "Çıkış başarılı",
            content = @Content(schema = @Schema(implementation = MessageResponse.class)))
    public ResponseEntity<MessageResponse> logout(
            @RequestHeader(value = "Authorization", required = false) String authHeader) {

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            authService.logout(authHeader.substring(7));
        }

        return ResponseEntity.ok(new MessageResponse("Cikis yapildi."));
    }

    /**
     * Mevcut token'ın geçerliliğini kontrol eder.
     */
    @GetMapping("/check")
    @Operation(
            summary = "Token doğrulama",
            description = "Gönderilen Bearer token'ın geçerliliğini kontrol eder. "
                    + "Geçerliyse kullanıcı bilgisini döner."
    )
    @ApiResponse(responseCode = "200", description = "Token geçerli",
            content = @Content(schema = @Schema(implementation = AuthCheckResponse.class)))
    @ApiResponse(responseCode = "401", description = "Geçersiz veya süresi dolmuş token",
            content = @Content(schema = @Schema(implementation = AuthCheckResponse.class)))
    public ResponseEntity<AuthCheckResponse> check(
            @RequestHeader(value = "Authorization", required = false) String authHeader) {

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String username = authService.validateToken(authHeader.substring(7));
            if (username != null) {
                return ResponseEntity.ok(new AuthCheckResponse(true, username, null));
            }
        }

        return ResponseEntity.status(401).body(
                new AuthCheckResponse(false, null, "Gecersiz veya suresi dolmus token."));
    }

    // ── Request / Response DTO'ları ──────────────────────────────────

    public record LoginRequest(String username, String password) {}

    @Schema(description = "Başarılı giriş yanıtı")
    public record LoginResponse(
            @Schema(description = "Bearer token", example = "eyJhbGciOi...") String token,
            @Schema(description = "Kullanıcı adı", example = "admin") String username,
            @Schema(description = "Bilgi mesajı") String message
    ) {}

    @Schema(description = "Genel bilgi mesajı yanıtı")
    public record MessageResponse(
            @Schema(description = "Bilgi mesajı") String message
    ) {}

    @Schema(description = "Token doğrulama yanıtı")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record AuthCheckResponse(
            @Schema(description = "Token geçerli mi?") boolean authenticated,
            @Schema(description = "Kullanıcı adı (geçerliyse)") String username,
            @Schema(description = "Hata mesajı (geçersizse)") String message
    ) {}
}

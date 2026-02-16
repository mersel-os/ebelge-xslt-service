package io.mersel.services.xslt.web.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.mersel.services.xslt.infrastructure.diagnostics.XsltMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Basit in-memory token tabanlı kimlik doğrulama servisi.
 * <p>
 * Kullanıcı adı ve parola environment variable'lardan okunur.
 * Başarılı giriş sonrası UUID token üretilir ve bellekte saklanır.
 * Token belirli bir süre sonra otomatik geçerliliğini yitirir.
 * <p>
 * Env:
 * <ul>
 *   <li>{@code XSLT_ADMIN_USERNAME} — varsayılan: admin</li>
 *   <li>{@code XSLT_ADMIN_PASSWORD} — varsayılan: changeme</li>
 *   <li>{@code XSLT_ADMIN_TOKEN_EXPIRY_HOURS} — varsayılan: 24</li>
 * </ul>
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private record TokenInfo(String username, Instant expiresAt) {}

    private Cache<String, TokenInfo> tokens;

    /** Başarısız giriş denemelerini izlemek için cache (brute-force koruması) */
    private Cache<String, Integer> failedAttempts;

    private static final int MAX_LOGIN_ATTEMPTS = 5;

    private final XsltMetrics xsltMetrics;

    public AuthService(XsltMetrics xsltMetrics) {
        this.xsltMetrics = xsltMetrics;
    }

    @Value("${spring.profiles.active:}")
    private String activeProfiles;

    @Value("${xslt.admin.username:admin}")
    private String adminUsername;

    @Value("${xslt.admin.password:changeme}")
    private String adminPassword;

    @Value("${xslt.admin.token-expiry-hours:24}")
    private int tokenExpiryHours;

    @Value("${xslt.admin.token-cache-max-size:10000}")
    private int tokenCacheMaxSize;

    @PostConstruct
    void init() {
        tokens = Caffeine.newBuilder()
                .maximumSize(tokenCacheMaxSize)
                .expireAfterWrite(Duration.ofHours(tokenExpiryHours))
                .build();

        failedAttempts = Caffeine.newBuilder()
                .maximumSize(10_000)
                .expireAfterWrite(Duration.ofMinutes(15))
                .build();

        if ("changeme".equals(adminPassword)) {
            log.warn("╔══════════════════════════════════════════════════════════════╗");
            log.warn("║  UYARI: XSLT_ADMIN_PASSWORD varsayılan değer (changeme)!   ║");
            log.warn("║  Production ortamında mutlaka değiştirin.                   ║");
            log.warn("╚══════════════════════════════════════════════════════════════╝");
        }
        if ("admin".equals(adminUsername)) {
            log.warn("  XSLT_ADMIN_USERNAME varsayılan değer (admin). Değiştirmeniz önerilir.");
        }

        if (activeProfiles != null && activeProfiles.contains("prod")) {
            if ("changeme".equals(adminPassword)) {
                throw new IllegalStateException(
                        "XSLT_ADMIN_PASSWORD must be changed from default value in production! "
                                + "Set the XSLT_ADMIN_PASSWORD environment variable.");
            }
            if ("admin".equals(adminUsername)) {
                log.error("Production ortamında varsayılan kullanıcı adı (admin) kullanılıyor! "
                        + "XSLT_ADMIN_USERNAME environment variable'ını değiştirmeniz önerilir.");
            }
        }
    }

    /**
     * Kullanıcı adı ve parolayı doğrular, başarılıysa yeni token döner.
     *
     * @return token veya null (başarısız giriş)
     */
    public String login(String username, String password) {
        // Null/blank kontrolü — Caffeine null key kabul etmez
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            return null;
        }

        // Brute-force koruması — aşırı deneme varsa reddet
        Integer attempts = failedAttempts.getIfPresent(username);
        if (attempts != null && attempts >= MAX_LOGIN_ATTEMPTS) {
            log.warn("Çok fazla başarısız giriş denemesi ({}), geçici olarak engellendi: {}", attempts, username);
            xsltMetrics.recordLogin("blocked");
            return null;
        }

        if (adminUsername.equals(username)
                && java.security.MessageDigest.isEqual(
                    adminPassword.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                    password.getBytes(java.nio.charset.StandardCharsets.UTF_8))) {
            failedAttempts.invalidate(username); // Başarılı giriş — sayacı sıfırla
            String token = UUID.randomUUID().toString();
            Instant expiresAt = Instant.now().plusSeconds(tokenExpiryHours * 3600L);
            tokens.put(token, new TokenInfo(username, expiresAt));

            xsltMetrics.recordLogin("success");
            log.info("Admin girişi başarılı: {}", username);
            return token;
        }

        failedAttempts.put(username, (attempts != null ? attempts : 0) + 1);
        xsltMetrics.recordLogin("failure");
        log.warn("Başarısız giriş denemesi: {}", username);
        return null;
    }

    /**
     * Bearer token'ı doğrular. Geçerliyse kullanıcı adını döner,
     * geçersiz veya süresi dolmuşsa null döner.
     */
    public String validateToken(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }

        TokenInfo info = tokens.getIfPresent(token);
        if (info == null) {
            return null;
        }

        if (info.expiresAt().isAfter(Instant.now())) {
            return info.username();
        }

        tokens.invalidate(token);
        return null;
    }

    /**
     * Token'ı geçersiz kılar (çıkış).
     */
    public void logout(String token) {
        if (token != null) {
            tokens.invalidate(token);
            log.info("Token geçersiz kılındı (çıkış)");
        }
    }
}

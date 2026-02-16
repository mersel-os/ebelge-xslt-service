package io.mersel.services.xslt.web.config;

import io.mersel.services.xslt.infrastructure.diagnostics.XsltMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AuthService birim testleri.
 * <p>
 * Private alanlar reflection ile set edilir; @Value injection simüle edilir.
 * Caffeine cache @PostConstruct init() ile oluşturulur.
 */
@DisplayName("AuthService")
class AuthServiceTest {

    private AuthService authService;

    @BeforeEach
    void setUp() throws Exception {
        authService = new AuthService(new XsltMetrics(new SimpleMeterRegistry()));
        setPrivateField("adminUsername", "admin");
        setPrivateField("adminPassword", "changeme");
        setPrivateField("tokenExpiryHours", 24);
        setPrivateField("tokenCacheMaxSize", 10000);
        setPrivateField("activeProfiles", "");
        // @PostConstruct tetikle — Caffeine cache'i oluşturur
        invokeInit();
    }

    private void setPrivateField(String fieldName, Object value) throws Exception {
        Field field = AuthService.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(authService, value);
    }

    private void invokeInit() throws Exception {
        Method init = AuthService.class.getDeclaredMethod("init");
        init.setAccessible(true);
        init.invoke(authService);
    }

    @Test
    @DisplayName("basarili_login_token_uretir — correct credentials return non-null UUID token")
    void basarili_login_token_uretir() {
        String token = authService.login("admin", "changeme");

        assertThat(token).isNotNull();
        assertThat(UUID.fromString(token)).isNotNull();
    }

    @Test
    @DisplayName("basarisiz_login_null_doner — wrong credentials return null")
    void basarisiz_login_null_doner() {
        assertThat(authService.login("admin", "wrong")).isNull();
        assertThat(authService.login("wrong", "changeme")).isNull();
        assertThat(authService.login("wrong", "wrong")).isNull();
    }

    @Test
    @DisplayName("token_dogrulama_basarili — valid token returns username")
    void token_dogrulama_basarili() {
        String token = authService.login("admin", "changeme");
        assertThat(token).isNotNull();

        String username = authService.validateToken(token);
        assertThat(username).isEqualTo("admin");
    }

    @Test
    @DisplayName("token_suresi_dolmus_red — expired token returns null")
    void token_suresi_dolmus_red() throws Exception {
        // tokenExpiryHours=0 ile yeniden init et — Caffeine TTL=0 saat
        setPrivateField("tokenExpiryHours", 0);
        invokeInit();

        String token = authService.login("admin", "changeme");
        assertThat(token).isNotNull();

        // Caffeine TTL=0 ile token hemen expire olacak
        // Kısa bir bekleme ekle Caffeine'in evict etmesi için
        Thread.sleep(50);

        String username = authService.validateToken(token);
        // tokenExpiryHours=0 → token anında expire olmuş olmalı
        // Not: Caffeine TTL=0 ile hemen expire etmeyebilir ama
        // validateToken() kendi Instant kontrolü de yapar
        assertThat(username).isNull();
    }

    @Test
    @DisplayName("gecersiz_token_red — random UUID returns null")
    void gecersiz_token_red() {
        String randomToken = UUID.randomUUID().toString();
        String username = authService.validateToken(randomToken);
        assertThat(username).isNull();
    }

    @Test
    @DisplayName("logout_token_gecersiz_kilma — after logout, same token returns null")
    void logout_token_gecersiz_kilma() {
        String token = authService.login("admin", "changeme");
        assertThat(authService.validateToken(token)).isEqualTo("admin");

        authService.logout(token);
        assertThat(authService.validateToken(token)).isNull();
    }

    @Test
    @DisplayName("concurrent_token_temizligi — multiple tokens, only valid one works")
    void concurrent_token_temizligi() {
        // Birden fazla token oluştur
        String token1 = authService.login("admin", "changeme");
        String token2 = authService.login("admin", "changeme");

        // Her ikisi de geçerli olmalı
        assertThat(authService.validateToken(token1)).isEqualTo("admin");
        assertThat(authService.validateToken(token2)).isEqualTo("admin");

        // Birini logout et
        authService.logout(token1);

        // Token1 artık geçersiz, token2 hala geçerli
        assertThat(authService.validateToken(token1)).isNull();
        assertThat(authService.validateToken(token2)).isEqualTo("admin");
    }
}

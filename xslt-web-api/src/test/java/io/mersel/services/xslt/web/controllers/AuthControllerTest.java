package io.mersel.services.xslt.web.controllers;

import io.mersel.services.xslt.web.config.AuthService;
import io.mersel.services.xslt.web.dto.ErrorResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * AuthController birim testleri.
 * <p>
 * Login, logout ve token check endpoint'lerinin doğru HTTP yanıtları
 * döndüğünü ve AuthService ile doğru etkileşimde bulunduğunu test eder.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AuthController")
class AuthControllerTest {

    @Mock
    private AuthService authService;

    private AuthController controller;

    @BeforeEach
    void setUp() {
        controller = new AuthController(authService);
    }

    // ── Login ────────────────────────────────────────────────

    @Test
    @DisplayName("login_basarili — valid credentials return 200 + token")
    void login_basarili() {
        when(authService.login("admin", "secret")).thenReturn("test-token-uuid");

        var request = new AuthController.LoginRequest("admin", "secret");
        var response = controller.login(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        var body = (AuthController.LoginResponse) response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.token()).isEqualTo("test-token-uuid");
        assertThat(body.username()).isEqualTo("admin");
        assertThat(body.message()).isNotNull();
    }

    @Test
    @DisplayName("login_basarisiz — invalid credentials return 401")
    void login_basarisiz() {
        when(authService.login("admin", "wrong")).thenReturn(null);

        var request = new AuthController.LoginRequest("admin", "wrong");
        var response = controller.login(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        var body = (ErrorResponse) response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.error()).isEqualTo("Unauthorized");
    }

    // ── Logout ───────────────────────────────────────────────

    @Test
    @DisplayName("logout_bearer_token — valid bearer header calls authService.logout")
    void logout_bearer_token() {
        var response = controller.logout("Bearer my-token-123");

        verify(authService).logout("my-token-123");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).isEqualTo("Cikis yapildi.");
    }

    @Test
    @DisplayName("logout_bearer_yok — missing auth header returns 200 without calling logout")
    void logout_bearer_yok() {
        var response = controller.logout(null);

        verify(authService, never()).logout(any());
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("logout_yanlis_format — non-Bearer header returns 200 without calling logout")
    void logout_yanlis_format() {
        var response = controller.logout("Basic dXNlcjpwYXNz");

        verify(authService, never()).logout(any());
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    // ── Check ────────────────────────────────────────────────

    @Test
    @DisplayName("check_gecerli_token — valid token returns 200 + authenticated=true")
    void check_gecerli_token() {
        when(authService.validateToken("valid-token")).thenReturn("admin");

        var response = controller.check("Bearer valid-token");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().authenticated()).isTrue();
        assertThat(response.getBody().username()).isEqualTo("admin");
    }

    @Test
    @DisplayName("check_gecersiz_token — invalid token returns 401")
    void check_gecersiz_token() {
        when(authService.validateToken("invalid-token")).thenReturn(null);

        var response = controller.check("Bearer invalid-token");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().authenticated()).isFalse();
    }

    @Test
    @DisplayName("check_header_yok — missing auth header returns 401")
    void check_header_yok() {
        var response = controller.check(null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().authenticated()).isFalse();
    }

    @Test
    @DisplayName("check_bos_bearer — empty bearer returns 401")
    void check_bos_bearer() {
        var response = controller.check("Bearer ");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}

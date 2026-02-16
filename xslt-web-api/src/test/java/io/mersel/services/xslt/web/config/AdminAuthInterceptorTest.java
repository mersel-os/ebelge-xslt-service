package io.mersel.services.xslt.web.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * AdminAuthInterceptor birim testleri.
 * <p>
 * HandlerInterceptor.preHandle davranışını MockHttpServletRequest/Response ile test eder.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AdminAuthInterceptor")
class AdminAuthInterceptorTest {

    @Mock
    private AuthService authService;

    private AdminAuthInterceptor interceptor;

    @BeforeEach
    void setUp() {
        interceptor = new AdminAuthInterceptor(authService);
    }

    @Test
    @DisplayName("public_get_profiles_auth_gerektirmez — GET /v1/admin/profiles passes without auth")
    void public_get_profiles_auth_gerektirmez() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/v1/admin/profiles");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean result = interceptor.preHandle(request, response, null);

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("post_profiles_auth_gerektirir — PUT/POST on profiles without auth returns 401")
    void post_profiles_auth_gerektirir() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/v1/admin/profiles");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean result = interceptor.preHandle(request, response, null);

        assertThat(result).isFalse();
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentType()).contains("application/json");
        assertThat(response.getContentAsString()).contains("Unauthorized");
    }

    @Test
    @DisplayName("gecerli_bearer_token_gecir — Valid bearer token passes through")
    void gecerli_bearer_token_gecir() throws Exception {
        String token = "valid-token-123";
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/v1/admin/profiles");
        request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(authService.validateToken(eq(token))).thenReturn("admin");

        boolean result = interceptor.preHandle(request, response, null);

        assertThat(result).isTrue();
        assertThat(request.getAttribute("adminUsername")).isEqualTo("admin");
    }

    @Test
    @DisplayName("options_preflight_gecir — OPTIONS always passes")
    void options_preflight_gecir() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", "/v1/admin/profiles");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean result = interceptor.preHandle(request, response, null);

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("eksik_header_401 — Missing Authorization header returns 401 JSON")
    void eksik_header_401() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/v1/admin/settings");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean result = interceptor.preHandle(request, response, null);

        assertThat(result).isFalse();
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentType()).contains("application/json");
        assertThat(response.getContentAsString())
                .contains("Unauthorized")
                .contains("token gereklidir")
                .contains("POST /v1/auth/login");
    }
}

package io.mersel.services.xslt.web.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SecurityHeaderConfig birim testleri.
 * <p>
 * Güvenlik başlıklarının (CSP, X-Frame-Options, X-Content-Type-Options)
 * tüm yanıtlara doğru şekilde eklendiğini test eder.
 */
@DisplayName("SecurityHeaderConfig")
class SecurityHeaderConfigTest {

    private static final String CUSTOM_CSP = "default-src 'self'; script-src 'self'";

    @Test
    @DisplayName("filter_tum_guvenlik_basliklarini_ekler — CSP, X-Frame-Options, nosniff")
    void filter_tum_guvenlik_basliklarini_ekler() throws Exception {
        var filter = new SecurityHeaderConfig.SecurityHeaderFilter(CUSTOM_CSP);
        var request = new MockHttpServletRequest();
        var response = new MockHttpServletResponse();
        var chain = new MockFilterChain();

        filter.doFilterInternal(request, response, chain);

        assertThat(response.getHeader("Content-Security-Policy")).isEqualTo(CUSTOM_CSP);
        assertThat(response.getHeader("X-Frame-Options")).isEqualTo("SAMEORIGIN");
        assertThat(response.getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
    }

    @Test
    @DisplayName("filter_varsayilan_csp — default CSP policy applied")
    void filter_varsayilan_csp() throws Exception {
        var defaultCsp = "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; font-src 'self'; connect-src 'self'";
        var filter = new SecurityHeaderConfig.SecurityHeaderFilter(defaultCsp);
        var request = new MockHttpServletRequest();
        var response = new MockHttpServletResponse();
        var chain = new MockFilterChain();

        filter.doFilterInternal(request, response, chain);

        assertThat(response.getHeader("Content-Security-Policy")).contains("default-src 'self'");
        assertThat(response.getHeader("Content-Security-Policy")).contains("script-src 'self'");
    }

    @Test
    @DisplayName("filter_chain_devam_eder — filter chain continues after headers set")
    void filter_chain_devam_eder() throws Exception {
        var filter = new SecurityHeaderConfig.SecurityHeaderFilter(CUSTOM_CSP);
        var request = new MockHttpServletRequest();
        var response = new MockHttpServletResponse();
        var chain = new MockFilterChain();

        filter.doFilterInternal(request, response, chain);

        // FilterChain.doFilter çağrıldığını doğrula
        assertThat(chain.getRequest()).isNotNull();
        assertThat(chain.getResponse()).isNotNull();
    }

    @Test
    @DisplayName("x_content_type_options_nosniff — MIME sniffing koruması mevcut")
    void x_content_type_options_nosniff() throws Exception {
        var filter = new SecurityHeaderConfig.SecurityHeaderFilter(CUSTOM_CSP);
        var request = new MockHttpServletRequest();
        var response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, new MockFilterChain());

        assertThat(response.getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
    }

    @Test
    @DisplayName("scalar_sayfasi_gevsek_csp — Scalar CDN kaynaklarına izin verir")
    void scalar_sayfasi_gevsek_csp() throws Exception {
        var filter = new SecurityHeaderConfig.SecurityHeaderFilter(CUSTOM_CSP);
        var request = new MockHttpServletRequest();
        request.setRequestURI("/scalar.html");
        var response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, new MockFilterChain());

        String csp = response.getHeader("Content-Security-Policy");
        assertThat(csp).contains("cdn.jsdelivr.net");
        assertThat(csp).contains("'unsafe-inline'");
        assertThat(csp).contains("fonts.googleapis.com");
        assertThat(csp).contains("fonts.gstatic.com");
    }

    @Test
    @DisplayName("normal_sayfa_strict_csp — Scalar dışı sayfalarda strict CSP uygulanır")
    void normal_sayfa_strict_csp() throws Exception {
        var filter = new SecurityHeaderConfig.SecurityHeaderFilter(CUSTOM_CSP);
        var request = new MockHttpServletRequest();
        request.setRequestURI("/v1/validate");
        var response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, new MockFilterChain());

        assertThat(response.getHeader("Content-Security-Policy")).isEqualTo(CUSTOM_CSP);
    }
}

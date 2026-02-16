package io.mersel.services.xslt.infrastructure;

import io.mersel.services.xslt.infrastructure.diagnostics.SaxonHealthCheck;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Status;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SaxonHealthCheck birim testleri.
 */
@DisplayName("SaxonHealthCheck")
class SaxonHealthCheckTest {

    @Test
    @DisplayName("Saxon HE sağlık kontrolü UP dönmeli")
    void shouldReturnUpWhenSaxonIsWorking() {
        var healthCheck = new SaxonHealthCheck();
        var health = healthCheck.health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsKey("engine");
        assertThat(health.getDetails().get("engine")).isEqualTo("Saxon HE");
        assertThat(health.getDetails()).containsKey("version");
        assertThat(health.getDetails()).containsKey("xsltVersion");
    }
}

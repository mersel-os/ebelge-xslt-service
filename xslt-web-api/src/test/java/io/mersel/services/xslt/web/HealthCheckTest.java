package io.mersel.services.xslt.web;

import io.mersel.services.xslt.infrastructure.diagnostics.SaxonHealthCheck;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Status;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Health check birim testleri.
 */
@DisplayName("Health Check")
class HealthCheckTest {

    @Test
    @DisplayName("Saxon health check UP dönmeli")
    void shouldReturnHealthUp() {
        var healthCheck = new SaxonHealthCheck();
        var health = healthCheck.health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("engine", "Saxon HE");
        assertThat(health.getDetails()).containsKey("version");
    }
}

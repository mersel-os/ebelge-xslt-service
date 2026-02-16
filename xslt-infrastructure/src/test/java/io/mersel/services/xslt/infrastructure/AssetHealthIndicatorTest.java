package io.mersel.services.xslt.infrastructure;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.actuate.health.Status;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * AssetHealthIndicator birim testleri.
 * <p>
 * Farklı asset durumlarına göre doğru Health status döndüğünü test eder:
 * UP (dizin erişilebilir), UP + warning (asset yüklenmemiş), DOWN (not configured), DOWN (not accessible).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AssetHealthIndicator")
class AssetHealthIndicatorTest {

    @Mock
    private AssetManager assetManager;

    @Mock
    private JaxpSchemaValidator jaxpSchemaValidator;

    @Mock
    private SaxonSchematronValidator saxonSchematronValidator;

    @Test
    @DisplayName("health_up — configured, accessible, schemas loaded → UP")
    void health_up(@TempDir Path tempDir) {
        when(assetManager.isConfigured()).thenReturn(true);
        when(assetManager.getExternalDir()).thenReturn(tempDir);
        when(jaxpSchemaValidator.getLoadedCount()).thenReturn(4);
        when(saxonSchematronValidator.getLoadedCount()).thenReturn(8);

        var indicator = new AssetHealthIndicator(assetManager, jaxpSchemaValidator, saxonSchematronValidator);
        var health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("asset_directory", "accessible");
        assertThat(health.getDetails()).containsEntry("xsd_loaded", 4);
        assertThat(health.getDetails()).containsEntry("schematron_loaded", 8);
    }

    @Test
    @DisplayName("health_down_not_configured — asset directory not configured → DOWN")
    void health_down_not_configured() {
        when(assetManager.isConfigured()).thenReturn(false);
        when(jaxpSchemaValidator.getLoadedCount()).thenReturn(0);
        when(saxonSchematronValidator.getLoadedCount()).thenReturn(0);

        var indicator = new AssetHealthIndicator(assetManager, jaxpSchemaValidator, saxonSchematronValidator);
        var health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("asset_directory", "not_configured");
    }

    @Test
    @DisplayName("health_down_not_accessible — directory path null → DOWN")
    void health_down_not_accessible() {
        when(assetManager.isConfigured()).thenReturn(true);
        when(assetManager.getExternalDir()).thenReturn(null);
        when(jaxpSchemaValidator.getLoadedCount()).thenReturn(0);
        when(saxonSchematronValidator.getLoadedCount()).thenReturn(0);

        var indicator = new AssetHealthIndicator(assetManager, jaxpSchemaValidator, saxonSchematronValidator);
        var health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("asset_directory", "not_accessible");
    }

    @Test
    @DisplayName("health_up_warning_no_xsd — accessible but no XSD loaded → UP with warning")
    void health_up_warning_no_xsd(@TempDir Path tempDir) {
        when(assetManager.isConfigured()).thenReturn(true);
        when(assetManager.getExternalDir()).thenReturn(tempDir);
        when(jaxpSchemaValidator.getLoadedCount()).thenReturn(0);
        when(saxonSchematronValidator.getLoadedCount()).thenReturn(5);

        var indicator = new AssetHealthIndicator(assetManager, jaxpSchemaValidator, saxonSchematronValidator);
        var health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("asset_directory", "accessible");
        assertThat(health.getDetails()).containsEntry("xsd_loaded", 0);
        assertThat(health.getDetails()).containsKey("warning");
    }

    @Test
    @DisplayName("health_up_warning_no_schematron — accessible but no Schematron loaded → UP with warning")
    void health_up_warning_no_schematron(@TempDir Path tempDir) {
        when(assetManager.isConfigured()).thenReturn(true);
        when(assetManager.getExternalDir()).thenReturn(tempDir);
        when(jaxpSchemaValidator.getLoadedCount()).thenReturn(4);
        when(saxonSchematronValidator.getLoadedCount()).thenReturn(0);

        var indicator = new AssetHealthIndicator(assetManager, jaxpSchemaValidator, saxonSchematronValidator);
        var health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("asset_directory", "accessible");
        assertThat(health.getDetails()).containsEntry("schematron_loaded", 0);
        assertThat(health.getDetails()).containsKey("warning");
    }

    @Test
    @DisplayName("health_up_warning_no_assets — accessible but nothing loaded → UP with warning")
    void health_up_warning_no_assets(@TempDir Path tempDir) {
        when(assetManager.isConfigured()).thenReturn(true);
        when(assetManager.getExternalDir()).thenReturn(tempDir);
        when(jaxpSchemaValidator.getLoadedCount()).thenReturn(0);
        when(saxonSchematronValidator.getLoadedCount()).thenReturn(0);

        var indicator = new AssetHealthIndicator(assetManager, jaxpSchemaValidator, saxonSchematronValidator);
        var health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("asset_directory", "accessible");
        assertThat(health.getDetails()).containsKey("warning");
    }
}

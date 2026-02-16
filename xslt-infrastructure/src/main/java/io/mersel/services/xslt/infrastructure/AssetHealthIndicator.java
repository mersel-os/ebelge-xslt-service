package io.mersel.services.xslt.infrastructure;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.nio.file.Files;

/**
 * Asset dizini ve derlenmiş şema/kural durumunu kontrol eden sağlık göstergesi.
 * <p>
 * Kontroller:
 * <ul>
 *   <li>Asset dizini yapılandırılmış ve erişilebilir mi? → DOWN değilse UP</li>
 *   <li>XSD/Schematron yüklü mü? → Detay bilgisi olarak raporlanır (UP durumunu etkilemez)</li>
 * </ul>
 * <p>
 * Asset'lerin henüz yüklenmemiş olması (ilk kurulum, sync öncesi) servisi DOWN yapmaz.
 * Servis çalışır durumdaysa ve asset dizini erişilebilirse UP döner.
 * GIB paket sync ile asset'ler yüklendikten sonra detaylar güncellenir.
 */
@Component
public class AssetHealthIndicator implements HealthIndicator {

    private final AssetManager assetManager;
    private final JaxpSchemaValidator jaxpSchemaValidator;
    private final SaxonSchematronValidator saxonSchematronValidator;

    public AssetHealthIndicator(AssetManager assetManager,
                                JaxpSchemaValidator jaxpSchemaValidator,
                                SaxonSchematronValidator saxonSchematronValidator) {
        this.assetManager = assetManager;
        this.jaxpSchemaValidator = jaxpSchemaValidator;
        this.saxonSchematronValidator = saxonSchematronValidator;
    }

    @Override
    public Health health() {
        int xsdCount = jaxpSchemaValidator.getLoadedCount();
        int schematronCount = saxonSchematronValidator.getLoadedCount();

        // 1. Asset dizini yapılandırılmamışsa — DOWN
        if (!assetManager.isConfigured()) {
            return Health.down()
                    .withDetail("asset_directory", "not_configured")
                    .withDetail("xsd_loaded", xsdCount)
                    .withDetail("schematron_loaded", schematronCount)
                    .build();
        }

        // 2. Asset dizini erişilemiyorsa — DOWN
        var externalDir = assetManager.getExternalDir();
        if (externalDir == null || !Files.isDirectory(externalDir)) {
            return Health.down()
                    .withDetail("asset_directory", "not_accessible")
                    .withDetail("path", externalDir != null ? externalDir.toString() : "null")
                    .withDetail("xsd_loaded", xsdCount)
                    .withDetail("schematron_loaded", schematronCount)
                    .build();
        }

        // 3. Dizin erişilebilir — UP (asset yükleme durumu detay olarak raporlanır)
        var builder = Health.up()
                .withDetail("asset_directory", "accessible")
                .withDetail("xsd_loaded", xsdCount)
                .withDetail("schematron_loaded", schematronCount);

        if (xsdCount < 1 || schematronCount < 1) {
            builder.withDetail("warning", "Asset'ler henüz yüklenmemiş. GIB paket sync çalıştırın: POST /v1/admin/packages/sync");
        }

        return builder.build();
    }
}

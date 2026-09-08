package io.mersel.services.xslt.infrastructure;

import io.mersel.services.xslt.application.enums.SchemaValidationType;
import io.mersel.services.xslt.application.interfaces.ReloadResult;
import io.mersel.services.xslt.infrastructure.diagnostics.XsltMetrics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * JaxpSchemaValidator.reload() birim testleri.
 * <p>
 * Ayrı dağıtılan GİB ürün paketlerinin (e-Döviz, e-Dekont, e-Gider Pusulası)
 * sync edilmemiş olması hata sayılmamalı; mevcut kurulumların sağlık durumu
 * yeni tipler eklendiği için bozulmamalı.
 */
@DisplayName("JaxpSchemaValidatorReload")
class JaxpSchemaValidatorReloadTest {

    private static final String EDOVIZ_XSD = "validator/earchive-edoviz/schema/eArsiv.xsd";

    @TempDir
    Path tempDir;

    private AssetManager assetManager;

    @BeforeEach
    void setUp() throws IOException {
        assetManager = mock(AssetManager.class);
        when(assetManager.getExternalDir()).thenReturn(tempDir);
        when(assetManager.assetExists(anyString())).thenReturn(false);
        doThrow(new IOException("asset bulunamadı"))
                .when(assetManager).resolveAssetOnDisk(anyString());
    }

    private JaxpSchemaValidator createValidator() {
        JaxpSchemaValidator validator = new JaxpSchemaValidator(assetManager, mock(XsltMetrics.class));
        try {
            var maxSizeField = JaxpSchemaValidator.class.getDeclaredField("xsdOverrideCacheMaxSize");
            maxSizeField.setAccessible(true);
            maxSizeField.setInt(validator, 50);

            var ttlField = JaxpSchemaValidator.class.getDeclaredField("xsdOverrideCacheTtlHours");
            ttlField.setAccessible(true);
            ttlField.setInt(validator, 1);

            Method initMethod = JaxpSchemaValidator.class.getDeclaredMethod("init");
            initMethod.setAccessible(true);
            initMethod.invoke(validator);
        } catch (Exception e) {
            throw new RuntimeException("Validator init failed", e);
        }
        return validator;
    }

    @Test
    @DisplayName("urun_paketi_sync_edilmemisse_hata_uretmez — eksik ürün şeması reload'u kirletmemeli")
    void urun_paketi_sync_edilmemisse_hata_uretmez() {
        ReloadResult result = createValidator().reload();

        assertThat(reportedErrors(result))
                .as("temel paketlerin eksikliği hâlâ hata olarak raporlanmalı")
                .contains(SchemaValidationType.INVOICE.name())
                .as("ayrı dağıtılan ürün paketleri hata üretmemeli")
                .doesNotContain(SchemaValidationType.EARCHIVE_REPORT_EDOVIZ.name())
                .doesNotContain(SchemaValidationType.EARCHIVE_REPORT_EDEKONT.name())
                .doesNotContain(SchemaValidationType.EARCHIVE_REPORT_EGIDER_PUSULASI.name());
    }

    @Test
    @DisplayName("urun_semasi_bozuksa_hata_uretir — atlama yalnızca dosya yokluğunda geçerli")
    void urun_semasi_bozuksa_hata_uretir() throws Exception {
        Path brokenXsd = tempDir.resolve("eArsiv.xsd");
        Files.writeString(brokenXsd, "<bu-bir-sema-degil/>");
        when(assetManager.assetExists(EDOVIZ_XSD)).thenReturn(true);
        doReturn(brokenXsd).when(assetManager).resolveAssetOnDisk(EDOVIZ_XSD);

        ReloadResult result = createValidator().reload();

        assertThat(reportedErrors(result)).contains(SchemaValidationType.EARCHIVE_REPORT_EDOVIZ.name());
    }

    /**
     * ReloadResult, tümü başarısız olduğunda hataları tek bir metinde birleştirir.
     * İddiaların bundan etkilenmemesi için hepsini düz metne indirger.
     */
    private static String reportedErrors(ReloadResult result) {
        return String.join(" | ", result.errors());
    }
}

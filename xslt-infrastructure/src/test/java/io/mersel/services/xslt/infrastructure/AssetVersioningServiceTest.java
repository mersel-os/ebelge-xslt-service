package io.mersel.services.xslt.infrastructure;

import io.mersel.services.xslt.application.interfaces.IGibPackageSyncService;
import io.mersel.services.xslt.application.models.GibPackageDefinition;
import io.mersel.services.xslt.application.models.GibPackageDefinition.FileExtraction;
import io.mersel.services.xslt.application.models.PackageSyncResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("AssetVersioningService")
class AssetVersioningServiceTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("sabit_hedefli_paket_onayi_paylasilan_dizini_korur")
    void sabit_hedefli_paket_onayi_paylasilan_dizini_korur() throws Exception {
        IGibPackageSyncService syncService = mock(IGibPackageSyncService.class);
        AssetRegistry assetRegistry = mock(AssetRegistry.class);
        SuppressionImpactAnalyzer suppressionAnalyzer = mock(SuppressionImpactAnalyzer.class);

        GibPackageDefinition pkg = new GibPackageDefinition(
                "edoviz", "e-Döviz", "https://example.test/edoviz.rar",
                List.of(
                        new FileExtraction("alim.xslt", "default_transformers/", "eDovizAlim_Base.xslt"),
                        new FileExtraction("satim.xslt", "default_transformers/", "eDovizSatim_Base.xslt")
                ),
                "test");
        when(syncService.getAvailablePackages()).thenReturn(List.of(pkg));
        when(syncService.syncPackageToTarget(eq("edoviz"), any(Path.class)))
                .thenAnswer(invocation -> {
                    Path stagingBase = invocation.getArgument(1);
                    Path transformers = Files.createDirectories(
                            stagingBase.resolve("default_transformers"));
                    Files.writeString(transformers.resolve("eDovizAlim_Base.xslt"), "new-alim");
                    Files.writeString(transformers.resolve("eDovizSatim_Base.xslt"), "new-satim");
                    return PackageSyncResult.success(
                            "edoviz", "e-Döviz", 2,
                            List.of(
                                    "default_transformers/eDovizAlim_Base.xslt",
                                    "default_transformers/eDovizSatim_Base.xslt"),
                            10);
                });

        var service = new AssetVersioningService(
                syncService, new AssetDiffService(), mock(AssetManager.class),
                assetRegistry, suppressionAnalyzer);
        ReflectionTestUtils.setField(service, "externalPath", tempDir.toString());

        Path sharedDir = Files.createDirectories(tempDir.resolve("default_transformers"));
        Path existingInvoice = sharedDir.resolve("eInvoice_Base.xslt");
        Files.writeString(existingInvoice, "existing-invoice");

        var preview = service.syncToStaging("edoviz");

        assertThat(preview.fileDiffs())
                .extracting(diff -> diff.path())
                .containsExactlyInAnyOrder(
                        "default_transformers/eDovizAlim_Base.xslt",
                        "default_transformers/eDovizSatim_Base.xslt");

        service.approvePending("edoviz");

        assertThat(existingInvoice).exists().hasContent("existing-invoice");
        assertThat(sharedDir.resolve("eDovizAlim_Base.xslt")).hasContent("new-alim");
        assertThat(sharedDir.resolve("eDovizSatim_Base.xslt")).hasContent("new-satim");
        verify(assetRegistry).reload();
    }

    @Test
    @DisplayName("preview'de iki tarafta da eksik sabit dosya hata vermeli")
    void previewde_iki_tarafta_da_eksik_sabit_dosya_hata_vermeli() {
        IGibPackageSyncService syncService = mock(IGibPackageSyncService.class);
        GibPackageDefinition pkg = new GibPackageDefinition(
                "test-package", "Test Paket", "https://example.test/package.zip",
                List.of(new FileExtraction(
                        "missing.xslt", "default_transformers/", "missing.xslt")),
                "test");
        when(syncService.getAvailablePackages()).thenReturn(List.of(pkg));
        when(syncService.syncPackageToTarget(eq("test-package"), any(Path.class)))
                .thenReturn(PackageSyncResult.success(
                        "test-package", "Test Paket", 0, List.of(), 10));

        var service = new AssetVersioningService(
                syncService, new AssetDiffService(), mock(AssetManager.class),
                mock(AssetRegistry.class), mock(SuppressionImpactAnalyzer.class));
        ReflectionTestUtils.setField(service, "externalPath", tempDir.toString());

        assertThatThrownBy(() -> service.syncToStaging("test-package"))
                .isInstanceOf(java.io.IOException.class)
                .hasMessageContaining("live veya staging dizininde bulunamadı")
                .hasMessageContaining("default_transformers/missing.xslt");
        assertThat(service.getPendingPreview("test-package")).isNull();
        assertThat(tempDir.resolve("history/staging/test-package")).doesNotExist();
    }
}

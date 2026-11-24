package io.mersel.services.xslt.infrastructure;

import io.mersel.services.xslt.application.interfaces.IGibPackageSyncService;
import io.mersel.services.xslt.infrastructure.config.GibSyncProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("GibAutoSyncStartupListener")
class GibAutoSyncStartupListenerTest {

    private final IGibPackageSyncService syncService = mock(IGibPackageSyncService.class);
    private final AssetManager assetManager = mock(AssetManager.class);

    @Test
    @DisplayName("ozel_assetler_varken_gib_dizini_bossa_ilk_sync_calisir")
    void ozel_assetler_varken_gib_dizini_bossa_ilk_sync_calisir() {
        // Seed/volume ile gelen custom-document-types.yml, default_transformers vb. ilk sync'i engellememeli.
        when(assetManager.isEmpty()).thenReturn(false);
        when(assetManager.isEmpty(GibAutoSyncStartupListener.GIB_PACKAGE_ROOT)).thenReturn(true);
        when(syncService.syncAll()).thenReturn(List.of());

        listener(true).onApplicationReady();

        verify(syncService, timeout(5000)).syncAll();
    }

    @Test
    @DisplayName("gib_dizini_doluysa_sync_atlanir")
    void gib_dizini_doluysa_sync_atlanir() {
        when(assetManager.isEmpty(GibAutoSyncStartupListener.GIB_PACKAGE_ROOT)).thenReturn(false);

        listener(true).onApplicationReady();

        verify(syncService, never()).syncAll();
    }

    @Test
    @DisplayName("otomatik_sync_kapaliysa_hic_bakilmaz")
    void otomatik_sync_kapaliysa_hic_bakilmaz() {
        listener(false).onApplicationReady();

        verify(syncService, never()).syncAll();
        verify(assetManager, never()).isEmpty(GibAutoSyncStartupListener.GIB_PACKAGE_ROOT);
    }

    private GibAutoSyncStartupListener listener(boolean autoSync) {
        var properties = new GibSyncProperties();
        properties.setEnabled(true);
        properties.setAutoSyncOnStartup(autoSync);
        return new GibAutoSyncStartupListener(syncService, assetManager, properties);
    }
}

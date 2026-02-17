package io.mersel.services.xslt.web.controllers;

import io.mersel.services.xslt.application.interfaces.IGibPackageSyncService;
import io.mersel.services.xslt.application.interfaces.IValidationProfileService;
import io.mersel.services.xslt.application.interfaces.ReloadResult;
import io.mersel.services.xslt.application.models.PackageSyncResult;
import io.mersel.services.xslt.application.models.ValidationProfile;
import io.mersel.services.xslt.infrastructure.AssetManager;
import io.mersel.services.xslt.infrastructure.AssetRegistry;
import io.mersel.services.xslt.web.config.AdminAuthInterceptor;
import io.mersel.services.xslt.web.config.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * AdminController birim testleri.
 * <p>
 * MockMvc standalone kurulumu ile AdminAuthInterceptor dahil edilerek
 * hem endpoint mantığı hem auth davranışı test edilir.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AdminController")
class AdminControllerTest {

    /** Interceptor olmadan — salt controller mantığı testleri için */
    private MockMvc mockMvc;

    /** Interceptor ile — auth testleri için */
    private MockMvc mockMvcWithAuth;

    @Mock
    private AssetRegistry assetRegistry;

    @Mock
    private AssetManager assetManager;

    @Mock
    private IValidationProfileService profileService;

    @Mock
    private IGibPackageSyncService gibSyncService;

    @Mock
    private AuthService authService;

    @InjectMocks
    private AdminController adminController;

    @BeforeEach
    void setUp() {
        // Interceptor'sız — sadece controller mantığı
        mockMvc = MockMvcBuilders.standaloneSetup(adminController).build();

        // Interceptor'lu — auth davranışı testleri
        AdminAuthInterceptor interceptor = new AdminAuthInterceptor(authService);
        mockMvcWithAuth = MockMvcBuilders.standaloneSetup(adminController)
                .addInterceptors(interceptor)
                .build();
    }

    // ── Test 1 ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("reload_assets_basarili — POST /v1/admin/assets/reload reload sonuçlarıyla 200 dönmeli")
    void reload_assets_basarili() throws Exception {
        var results = List.of(
                ReloadResult.success("XSD Schemas", 12, 150),
                ReloadResult.success("Schematron Rules", 8, 250)
        );
        when(assetRegistry.reload()).thenReturn(results);

        mockMvc.perform(post("/v1/admin/assets/reload"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.durationMs").value(400))
                .andExpect(jsonPath("$.components.length()").value(2))
                .andExpect(jsonPath("$.components[0].name").value("XSD Schemas"))
                .andExpect(jsonPath("$.components[0].status").value("OK"))
                .andExpect(jsonPath("$.components[0].count").value(12))
                .andExpect(jsonPath("$.components[1].name").value("Schematron Rules"))
                .andExpect(jsonPath("$.components[1].count").value(8))
                .andExpect(jsonPath("$.reloadedAt").exists());
    }

    // ── Test 2 ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("list_profiles_auth_gerektirmez — GET /v1/admin/profiles auth olmadan 200 dönmeli")
    void list_profiles_auth_gerektirmez() throws Exception {
        var profile = new ValidationProfile(
                "unsigned", "İmzasız belge profili", null,
                List.of(new ValidationProfile.SuppressionRule("ruleId", ".*Signature.*", null, "İmza kurallarını bastır")),
                Map.of(),
                Map.of()
        );
        when(profileService.getAvailableProfiles()).thenReturn(Map.of("unsigned", profile));

        mockMvcWithAuth.perform(get("/v1/admin/profiles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profileCount").value(1))
                .andExpect(jsonPath("$.profiles.unsigned").exists())
                .andExpect(jsonPath("$.profiles.unsigned.description").value("İmzasız belge profili"))
                .andExpect(jsonPath("$.profiles.unsigned.suppressionCount").value(1));
    }

    // ── Test 3 ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("save_profile_auth_gerektirir — PUT /v1/admin/profiles/{name} auth yoksa 401 dönmeli")
    void save_profile_auth_gerektirir() throws Exception {
        String requestBody = """
                {
                    "description": "Test profili",
                    "suppressions": [
                        {"match": "ruleId", "pattern": "BR-01"}
                    ]
                }
                """;

        mockMvcWithAuth.perform(put("/v1/admin/profiles/test-profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isUnauthorized());
    }

    // ── Test 4 ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("delete_profile_auth_gerektirir — DELETE /v1/admin/profiles/{name} auth yoksa 401 dönmeli")
    void delete_profile_auth_gerektirir() throws Exception {
        mockMvcWithAuth.perform(delete("/v1/admin/profiles/test-profile"))
                .andExpect(status().isUnauthorized());
    }

    // ── Test 5 ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("list_auto_generated_basarili — GET /v1/admin/auto-generated dosya listesi dönmeli")
    void list_auto_generated_basarili() throws Exception {
        when(assetManager.listFiles("auto-generated/schematron"))
                .thenReturn(List.of("UBLTR_MAIN.xsl", "EDEFTER_KEBIR.xsl"));
        when(assetManager.listFiles("auto-generated/schema-overrides"))
                .thenReturn(List.of("INVOICE_override.xsd"));
        when(assetManager.listFiles("auto-generated/schematron-rules"))
                .thenReturn(List.of("my-company_UBLTR_MAIN.xsl"));

        mockMvc.perform(get("/v1/admin/auto-generated"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(4))
                .andExpect(jsonPath("$.schematron.count").value(2))
                .andExpect(jsonPath("$.schematron.files[0]").value("UBLTR_MAIN.xsl"))
                .andExpect(jsonPath("$.schematron.files[1]").value("EDEFTER_KEBIR.xsl"))
                .andExpect(jsonPath("$.schemaOverrides.count").value(1))
                .andExpect(jsonPath("$.schemaOverrides.files[0]").value("INVOICE_override.xsd"))
                .andExpect(jsonPath("$.schematronRules.count").value(1))
                .andExpect(jsonPath("$.schematronRules.files[0]").value("my-company_UBLTR_MAIN.xsl"));
    }

    // ── Test 6 ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("gib_sync_basarili — POST /v1/admin/packages/sync sync sonucu dönmeli")
    void gib_sync_basarili() throws Exception {
        when(gibSyncService.isEnabled()).thenReturn(true);
        when(gibSyncService.getCurrentAssetSource()).thenReturn("synced");

        var syncResults = List.of(
                PackageSyncResult.success("efatura", "e-Fatura Paketi", 15, List.of("schema1.xsd", "schema2.xsd"), 500),
                PackageSyncResult.success("ubltr-xsd", "UBL-TR XSD", 8, List.of("UBL-Invoice.xsd"), 300)
        );
        when(gibSyncService.syncAll()).thenReturn(syncResults);

        mockMvc.perform(post("/v1/admin/packages/sync"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.successCount").value(2))
                .andExpect(jsonPath("$.totalCount").value(2))
                .andExpect(jsonPath("$.totalDurationMs").value(800))
                .andExpect(jsonPath("$.currentAssetSource").value("synced"))
                .andExpect(jsonPath("$.packages.length()").value(2))
                .andExpect(jsonPath("$.packages[0].packageId").value("efatura"))
                .andExpect(jsonPath("$.packages[0].success").value(true))
                .andExpect(jsonPath("$.packages[0].filesExtracted").value(15))
                .andExpect(jsonPath("$.syncedAt").exists());
    }
}

package io.mersel.services.xslt.infrastructure;

import io.mersel.services.xslt.application.interfaces.ReloadResult;
import io.mersel.services.xslt.application.models.SchematronError;
import io.mersel.services.xslt.application.models.SuppressionResult;
import io.mersel.services.xslt.application.models.ValidationProfile;
import io.mersel.services.xslt.application.models.XsdOverride;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * ValidationProfileRegistry birim testleri.
 * <p>
 * JUnit 5 + @TempDir ile YAML dosya yönetimi, mock AssetManager ile
 * profillerin yüklenmesi ve bastırma mantığının doğrulanması.
 */
@DisplayName("ValidationProfileRegistry")
class ValidationProfileRegistryTest {

    private static final String PROFILES_ASSET_PATH = "validation-profiles.yml";

    @TempDir
    Path tempDir;

    private AssetManager assetManager;
    private Path profilesFile;

    @BeforeEach
    void setUp() throws IOException {
        assetManager = mock(AssetManager.class);
        profilesFile = tempDir.resolve(PROFILES_ASSET_PATH);

        when(assetManager.getExternalDir()).thenReturn(tempDir);
        when(assetManager.assetExists(PROFILES_ASSET_PATH))
                .thenAnswer(inv -> Files.exists(profilesFile));
        when(assetManager.getAssetStream(PROFILES_ASSET_PATH))
                .thenAnswer(inv -> {
                    if (!Files.exists(profilesFile)) {
                        throw new IOException("Asset dosyası bulunamadı: " + PROFILES_ASSET_PATH);
                    }
                    return new FileInputStream(profilesFile.toFile());
                });
    }

    private void writeProfiles(String yaml) throws IOException {
        Files.createDirectories(profilesFile.getParent());
        Files.writeString(profilesFile, yaml);
    }

    private ValidationProfileRegistry createAndReload() {
        ValidationProfileRegistry registry = new ValidationProfileRegistry(assetManager);
        registry.reload();
        return registry;
    }

    // ── ruleId / test / text match modes ────────────────────────────────

    @Test
    @DisplayName("1. ruleId ile basturma")
    void ruleId_ile_basturma() throws IOException {
        writeProfiles("""
            profiles:
              test-profile:
                suppressions:
                  - match: ruleId
                    pattern: "InvoiceIDCheck"
            """);

        ValidationProfileRegistry registry = createAndReload();
        List<SchematronError> errors = List.of(
                new SchematronError("InvoiceIDCheck", "some-test", "Fatura ID format hatası")
        );

        SuppressionResult result = registry.applySchematronSuppressions(
                errors, "test-profile", null, Set.of("INVOICE"));

        assertThat(result.suppressedErrors()).hasSize(1);
        assertThat(result.suppressedErrors().get(0).ruleId()).isEqualTo("InvoiceIDCheck");
        assertThat(result.activeErrors()).isEmpty();
    }

    @Test
    @DisplayName("2. ruleId eslesmezse basturma yok")
    void ruleId_eslesmezse_basturma_yok() throws IOException {
        writeProfiles("""
            profiles:
              test-profile:
                suppressions:
                  - match: ruleId
                    pattern: "InvoiceIDCheck"
            """);

        ValidationProfileRegistry registry = createAndReload();
        List<SchematronError> errors = List.of(
                new SchematronError("OtherRule", "other-test", "Başka hata")
        );

        SuppressionResult result = registry.applySchematronSuppressions(
                errors, "test-profile", null, Set.of("INVOICE"));

        assertThat(result.suppressedErrors()).isEmpty();
        assertThat(result.activeErrors()).hasSize(1);
        assertThat(result.activeErrors().get(0).ruleId()).isEqualTo("OtherRule");
    }

    @Test
    @DisplayName("3. test ile basturma")
    void test_ile_basturma() throws IOException {
        writeProfiles("""
            profiles:
              test-profile:
                suppressions:
                  - match: test
                    pattern: 'matches\\(cbc:ID,.*\\)'
            """);

        ValidationProfileRegistry registry = createAndReload();
        String xpathTest = "matches(cbc:ID,'^[A-Z0-9]{3}20')";
        List<SchematronError> errors = List.of(
                new SchematronError("SomeRule", xpathTest, "Fatura ID format hatası")
        );

        SuppressionResult result = registry.applySchematronSuppressions(
                errors, "test-profile", null, Set.of("INVOICE"));

        assertThat(result.suppressedErrors()).hasSize(1);
        assertThat(result.activeErrors()).isEmpty();
    }

    @Test
    @DisplayName("4. text ile basturma")
    void text_ile_basturma() throws IOException {
        writeProfiles("""
            profiles:
              test-profile:
                suppressions:
                  - match: text
                    pattern: ".*imza.*kontrol.*"
            """);

        ValidationProfileRegistry registry = createAndReload();
        List<SchematronError> errors = List.of(
                new SchematronError(null, null, "Dijital imza kontrolü başarısız")
        );

        SuppressionResult result = registry.applySchematronSuppressions(
                errors, "test-profile", null, Set.of("INVOICE"));

        assertThat(result.suppressedErrors()).hasSize(1);
        assertThat(result.activeErrors()).isEmpty();
    }

    @Test
    @DisplayName("5. regex pattern destegi")
    void regex_pattern_destegi() throws IOException {
        writeProfiles("""
            profiles:
              test-profile:
                suppressions:
                  - match: ruleId
                    pattern: "Invoice.*Check"
            """);

        ValidationProfileRegistry registry = createAndReload();
        List<SchematronError> errors = List.of(
                new SchematronError("InvoiceIDCheck", null, "Fatura ID hatası"),
                new SchematronError("InvoiceDateCheck", null, "Fatura tarih hatası")
        );

        SuppressionResult result = registry.applySchematronSuppressions(
                errors, "test-profile", null, Set.of("INVOICE"));

        assertThat(result.suppressedErrors()).hasSize(2);
        assertThat(result.activeErrors()).isEmpty();
    }

    // ── scope filtering ────────────────────────────────────────────────

    @Test
    @DisplayName("6. scope filtreleme INVOICE")
    void scope_filtreleme_INVOICE() throws IOException {
        writeProfiles("""
            profiles:
              test-profile:
                suppressions:
                  - match: ruleId
                    pattern: "InvoiceIDCheck"
                    scope: [INVOICE]
            """);

        ValidationProfileRegistry registry = createAndReload();
        List<SchematronError> errors = List.of(
                new SchematronError("InvoiceIDCheck", null, "Fatura ID hatası")
        );

        SuppressionResult result = registry.applySchematronSuppressions(
                errors, "test-profile", null, Set.of("INVOICE"));

        assertThat(result.suppressedErrors()).hasSize(1);
    }

    @Test
    @DisplayName("7. scope filtreleme DESPATCH - uygulanmaz")
    void scope_filtreleme_DESPATCH() throws IOException {
        writeProfiles("""
            profiles:
              test-profile:
                suppressions:
                  - match: ruleId
                    pattern: "InvoiceIDCheck"
                    scope: [INVOICE]
            """);

        ValidationProfileRegistry registry = createAndReload();
        List<SchematronError> errors = List.of(
                new SchematronError("InvoiceIDCheck", null, "Fatura ID hatası")
        );

        SuppressionResult result = registry.applySchematronSuppressions(
                errors, "test-profile", null, Set.of("DESPATCH_ADVICE"));

        assertThat(result.suppressedErrors()).isEmpty();
        assertThat(result.activeErrors()).hasSize(1);
    }

    @Test
    @DisplayName("8. scope bos global kural")
    void scope_bos_global_kural() throws IOException {
        writeProfiles("""
            profiles:
              test-profile:
                suppressions:
                  - match: ruleId
                    pattern: "GlobalCheck"
            """);

        ValidationProfileRegistry registry = createAndReload();
        List<SchematronError> errors = List.of(
                new SchematronError("GlobalCheck", null, "Global hata")
        );

        SuppressionResult resultInvoice = registry.applySchematronSuppressions(
                errors, "test-profile", null, Set.of("INVOICE"));
        SuppressionResult resultDespatch = registry.applySchematronSuppressions(
                errors, "test-profile", null, Set.of("DESPATCH_ADVICE"));

        assertThat(resultInvoice.suppressedErrors()).hasSize(1);
        assertThat(resultDespatch.suppressedErrors()).hasSize(1);
    }

    // ── profile inheritance ─────────────────────────────────────────────

    @Test
    @DisplayName("9. profil mirasi extends")
    void profil_mirasi_extends() throws IOException {
        writeProfiles("""
            profiles:
              parent:
                suppressions:
                  - match: ruleId
                    pattern: "ParentRule"
              child:
                extends: parent
                suppressions:
                  - match: ruleId
                    pattern: "ChildRule"
            """);

        ValidationProfileRegistry registry = createAndReload();
        List<SchematronError> errors = List.of(
                new SchematronError("ParentRule", null, "Parent hata"),
                new SchematronError("ChildRule", null, "Child hata")
        );

        SuppressionResult result = registry.applySchematronSuppressions(
                errors, "child", null, Set.of("INVOICE"));

        assertThat(result.suppressedErrors()).hasSize(2);
        assertThat(result.activeErrors()).isEmpty();
    }

    @Test
    @DisplayName("10. profil mirasi xsd override ezme")
    void profil_mirasi_xsd_override_ezme() throws IOException {
        writeProfiles("""
            profiles:
              parent:
                xsd-overrides:
                  INVOICE:
                    - element: "cac:Signature"
                      minOccurs: "0"
              child:
                extends: parent
                xsd-overrides:
                  INVOICE:
                    - element: "cac:Signature"
                      minOccurs: "1"
            """);

        ValidationProfileRegistry registry = createAndReload();

        List<XsdOverride> overrides = registry.resolveXsdOverrides("child", "INVOICE");

        assertThat(overrides).hasSize(1);
        assertThat(overrides.get(0).element()).isEqualTo("cac:Signature");
        assertThat(overrides.get(0).minOccurs()).isEqualTo("1");
    }

    @Test
    @DisplayName("11. dongusel miras tespit")
    void dongusel_miras_tespit() throws IOException {
        writeProfiles("""
            profiles:
              profileA:
                extends: profileB
                suppressions:
                  - match: ruleId
                    pattern: "RuleA"
              profileB:
                extends: profileA
                suppressions:
                  - match: ruleId
                    pattern: "RuleB"
            """);

        ValidationProfileRegistry registry = new ValidationProfileRegistry(assetManager);
        ReloadResult result = registry.reload();

        assertThat(result.status()).isEqualTo(ReloadResult.Status.PARTIAL);
        assertThat(result.errors()).isNotEmpty();
        assertThat(result.errors()).anyMatch(s -> s.contains("Döngüsel"));
    }

    @Test
    @DisplayName("12. olmayan parent profil")
    void olmayan_parent_profil() throws IOException {
        writeProfiles("""
            profiles:
              child:
                extends: nonExistentParent
                suppressions:
                  - match: ruleId
                    pattern: "SomeRule"
            """);

        ValidationProfileRegistry registry = new ValidationProfileRegistry(assetManager);
        ReloadResult result = registry.reload();

        assertThat(result.status()).isEqualTo(ReloadResult.Status.PARTIAL);
        assertThat(result.errors()).isNotEmpty();
        assertThat(result.errors()).anyMatch(s -> s.contains("bulunamadı"));
    }

    // ── ad-hoc suppressions ─────────────────────────────────────────────

    @Test
    @DisplayName("13. ad hoc suppression merge")
    void ad_hoc_suppression_merge() throws IOException {
        writeProfiles("""
            profiles:
              test-profile:
                suppressions:
                  - match: ruleId
                    pattern: "ProfileRule"
            """);

        ValidationProfileRegistry registry = createAndReload();
        List<SchematronError> errors = List.of(
                new SchematronError("ProfileRule", null, "Profil kuralı"),
                new SchematronError("AdHocRule", null, "Ad-hoc kuralı")
        );

        SuppressionResult result = registry.applySchematronSuppressions(
                errors, "test-profile", List.of("AdHocRule"), Set.of("INVOICE"));

        assertThat(result.suppressedErrors()).hasSize(2);
        assertThat(result.activeErrors()).isEmpty();
    }

    @Test
    @DisplayName("14. ad hoc suppression profil olmadan")
    void ad_hoc_suppression_profil_olmadan() throws IOException {
        writeProfiles("""
            profiles:
              empty: {}
            """);

        ValidationProfileRegistry registry = createAndReload();
        List<SchematronError> errors = List.of(
                new SchematronError("AdHocRule", null, "Ad-hoc kuralı")
        );

        SuppressionResult result = registry.applySchematronSuppressions(
                errors, null, List.of("AdHocRule"), Set.of("INVOICE"));

        assertThat(result.suppressedErrors()).hasSize(1);
        assertThat(result.activeErrors()).isEmpty();
    }

    // ── edge cases ──────────────────────────────────────────────────────

    @Test
    @DisplayName("15. bos error listesi")
    void bos_error_listesi() throws IOException {
        writeProfiles("""
            profiles:
              test-profile:
                suppressions:
                  - match: ruleId
                    pattern: ".*"
            """);

        ValidationProfileRegistry registry = createAndReload();

        SuppressionResult result = registry.applySchematronSuppressions(
                List.of(), "test-profile", null, Set.of("INVOICE"));

        assertThat(result.activeErrors()).isEmpty();
        assertThat(result.suppressedErrors()).isEmpty();
    }

    @Test
    @DisplayName("16. null profil adi")
    void null_profil_adi() throws IOException {
        writeProfiles("""
            profiles:
              test-profile:
                suppressions:
                  - match: ruleId
                    pattern: "InvoiceIDCheck"
            """);

        ValidationProfileRegistry registry = createAndReload();
        List<SchematronError> errors = List.of(
                new SchematronError("InvoiceIDCheck", null, "Hata")
        );

        SuppressionResult result = registry.applySchematronSuppressions(
                errors, null, null, Set.of("INVOICE"));
        SuppressionResult resultEmpty = registry.applySchematronSuppressions(
                errors, "   ", null, Set.of("INVOICE"));

        assertThat(result.suppressedErrors()).isEmpty();
        assertThat(resultEmpty.suppressedErrors()).isEmpty();
    }

    @Test
    @DisplayName("17. xsd suppression text modu")
    void xsd_suppression_text_modu() throws IOException {
        writeProfiles("""
            profiles:
              test-profile:
                suppressions:
                  - match: text
                    pattern: ".*cac:Signature.*"
            """);

        ValidationProfileRegistry registry = createAndReload();
        List<String> xsdErrors = List.of(
                "Element 'cac:Signature' is not allowed in this context",
                "Element 'cbc:ID' is required"
        );

        List<String> result = registry.applyXsdSuppressions(
                xsdErrors, "test-profile", null, Set.of("INVOICE"));

        assertThat(result).hasSize(1);
        assertThat(result.get(0)).contains("cbc:ID");
    }

    // ── CRUD operations ────────────────────────────────────────────────

    @Test
    @DisplayName("18. profil kaydet ve yukle")
    void profil_kaydet_ve_yukle() throws IOException {
        writeProfiles("""
            profiles: {}
            """);

        ValidationProfileRegistry registry = new ValidationProfileRegistry(assetManager);
        registry.reload();

        ValidationProfile profile = new ValidationProfile(
                "saved-profile",
                "Test description",
                null,
                List.of(new ValidationProfile.SuppressionRule("ruleId", "SavedRule", null, null)),
                null
        );

        registry.saveProfile(profile);

        registry.reload();

        Optional<ValidationProfile> loaded = registry.getProfile("saved-profile");
        assertThat(loaded).isPresent();
        assertThat(loaded.get().name()).isEqualTo("saved-profile");
        assertThat(loaded.get().suppressions()).hasSize(1);
        assertThat(loaded.get().suppressions().get(0).pattern()).isEqualTo("SavedRule");

        List<SchematronError> errors = List.of(new SchematronError("SavedRule", null, "Hata"));
        SuppressionResult result = registry.applySchematronSuppressions(
                errors, "saved-profile", null, Set.of("INVOICE"));
        assertThat(result.suppressedErrors()).hasSize(1);
    }

    @Test
    @DisplayName("19. profil sil")
    void profil_sil() throws IOException {
        writeProfiles("""
            profiles:
              to-delete:
                suppressions:
                  - match: ruleId
                    pattern: "ToDeleteRule"
            """);

        ValidationProfileRegistry registry = createAndReload();

        List<SchematronError> errors = List.of(new SchematronError("ToDeleteRule", null, "Hata"));
        SuppressionResult beforeDelete = registry.applySchematronSuppressions(
                errors, "to-delete", null, Set.of("INVOICE"));
        assertThat(beforeDelete.suppressedErrors()).hasSize(1);

        boolean deleted = registry.deleteProfile("to-delete");
        assertThat(deleted).isTrue();

        SuppressionResult afterDelete = registry.applySchematronSuppressions(
                errors, "to-delete", null, Set.of("INVOICE"));
        assertThat(afterDelete.suppressedErrors()).isEmpty();
        assertThat(afterDelete.activeErrors()).hasSize(1);
    }
}

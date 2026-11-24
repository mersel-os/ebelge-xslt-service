package io.mersel.services.xslt.infrastructure;

import io.mersel.services.xslt.application.enums.SchematronValidationType;
import io.mersel.services.xslt.application.interfaces.ReloadResult;
import io.mersel.services.xslt.application.models.CustomDocumentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("CustomDocumentTypeRegistry")
class CustomDocumentTypeRegistryTest {

    @TempDir
    Path assetDir;

    private CustomDocumentTypeRegistry registry;

    @BeforeEach
    void setUp() throws Exception {
        AssetManager assetManager = new AssetManager();
        Field field = AssetManager.class.getDeclaredField("externalPath");
        field.setAccessible(true);
        field.set(assetManager, assetDir.toString());
        assetManager.init();
        registry = new CustomDocumentTypeRegistry(assetManager);
    }

    @Test
    @DisplayName("dosya_yoksa_tanim_yok_ve_basarili")
    void dosya_yoksa_tanim_yok_ve_basarili() {
        ReloadResult result = registry.reload();

        assertThat(result.status()).isEqualTo(ReloadResult.Status.OK);
        assertThat(result.loadedCount()).isZero();
        assertThat(registry.getAll()).isEmpty();
    }

    @Test
    @DisplayName("gecerli_tanimlar_yuklenir_ve_kokle_bulunur")
    void gecerli_tanimlar_yuklenir_ve_kokle_bulunur() throws Exception {
        write("""
                document-types:
                  - id: SAMPLE_VOUCHER
                    description: Örnek belge
                    root-namespace: "urn:example:sample:voucher-1"
                    root-element: Voucher
                    xsd: custom/sample/maindoc/Sample-Voucher-1.0.xsd
                    schematron: UBLTR_MAIN
                  - id: NO_NAMESPACE_DOC
                    root-namespace: ""
                    root-element: Document
                    xsd: custom/plain/Document.xsd
                """);

        ReloadResult result = registry.reload();

        assertThat(result.status()).isEqualTo(ReloadResult.Status.OK);
        assertThat(result.loadedCount()).isEqualTo(2);
        assertThat(registry.findByRoot("urn:example:sample:voucher-1", "Voucher"))
                .contains(new CustomDocumentType("SAMPLE_VOUCHER", "Örnek belge",
                        "urn:example:sample:voucher-1", "Voucher",
                        "custom/sample/maindoc/Sample-Voucher-1.0.xsd", SchematronValidationType.UBLTR_MAIN));
        assertThat(registry.findByRoot(null, "Document")).map(CustomDocumentType::id).contains("NO_NAMESPACE_DOC");
        assertThat(registry.findByRoot("", "Document")).map(CustomDocumentType::schematronType).isEmpty();
        assertThat(registry.findByRoot("urn:example:sample:voucher-1", "Other")).isEmpty();
        assertThat(registry.findById("SAMPLE_VOUCHER")).isPresent();
    }

    @Test
    @DisplayName("hatali_tanimlar_atlanir_gecerliler_yuklenir")
    void hatali_tanimlar_atlanir_gecerliler_yuklenir() throws Exception {
        write("""
                document-types:
                  - id: VALID_ONE
                    root-namespace: "urn:example:a"
                    root-element: A
                    xsd: custom/a/A.xsd
                  - id: lower_case
                    root-namespace: "urn:example:b"
                    root-element: B
                    xsd: custom/b/B.xsd
                  - id: INVOICE
                    root-namespace: "urn:example:c"
                    root-element: C
                    xsd: custom/c/C.xsd
                  - id: ESCAPES
                    root-namespace: "urn:example:d"
                    root-element: D
                    xsd: ../outside/D.xsd
                  - id: ABSOLUTE
                    root-namespace: "urn:example:e"
                    root-element: E
                    xsd: /etc/E.xsd
                  - id: BAD_SCHEMATRON
                    root-namespace: "urn:example:f"
                    root-element: F
                    xsd: custom/f/F.xsd
                    schematron: NOT_A_TYPE
                  - id: TYPO_KEY
                    root-namespace: "urn:example:g"
                    root-element: G
                    xsd: custom/g/G.xsd
                    schmatron: UBLTR_MAIN
                  - id: VALID_ONE
                    root-namespace: "urn:example:h"
                    root-element: H
                    xsd: custom/h/H.xsd
                  - id: SAME_ROOT
                    root-namespace: "urn:example:a"
                    root-element: A
                    xsd: custom/a2/A.xsd
                  - id: PREFIXED_ROOT
                    root-namespace: "urn:example:i"
                    root-element: "p:I"
                    xsd: custom/i/I.xsd
                  - id: GENERATED_DIR
                    root-namespace: "urn:example:j"
                    root-element: J
                    xsd: auto-generated/J.xsd
                """);

        ReloadResult result = registry.reload();

        assertThat(result.status()).isEqualTo(ReloadResult.Status.PARTIAL);
        assertThat(registry.getAll()).extracting(CustomDocumentType::id).containsExactly("VALID_ONE");
        assertThat(String.join("\n", result.errors()))
                .contains("document-types[1]: 'id' zorunlu")
                .contains("(INVOICE): yerleşik bir tip adıyla çakışıyor")
                .contains("(ESCAPES): 'xsd' asset dizininin dışına çıkamaz")
                .contains("(ABSOLUTE): 'xsd' asset dizinine göreli olmalı")
                .contains("(BAD_SCHEMATRON): bilinmeyen 'schematron' değeri 'NOT_A_TYPE'")
                .contains("document-types[6]: bilinmeyen alan(lar) [schmatron]")
                .contains("'VALID_ONE' adı birden fazla kez tanımlı")
                .contains("{urn:example:a}A kökü başka bir tanımda kullanılıyor")
                .contains("(PREFIXED_ROOT): 'root-element' zorunlu ve önek (prefix) içermemeli")
                .contains("(GENERATED_DIR): 'xsd' auto-generated dizininde olamaz");
        assertThat(result.errors()).hasSize(10);
    }

    @Test
    @DisplayName("bozuk_yaml_onceki_tanimlari_korur")
    void bozuk_yaml_onceki_tanimlari_korur() throws Exception {
        write("""
                document-types:
                  - id: KEPT
                    root-namespace: "urn:example:kept"
                    root-element: Kept
                    xsd: custom/kept/Kept.xsd
                """);
        registry.reload();

        write("document-types: [ { id: BROKEN");
        ReloadResult result = registry.reload();

        assertThat(result.status()).isEqualTo(ReloadResult.Status.FAILED);
        assertThat(result.errors()).singleElement().asString().contains("önceki tanımlar korunuyor");
        assertThat(registry.getAll()).extracting(CustomDocumentType::id).containsExactly("KEPT");
    }

    @Test
    @DisplayName("dosya_silinirse_tanimlar_kaldirilir")
    void dosya_silinirse_tanimlar_kaldirilir() throws Exception {
        write("""
                document-types:
                  - id: REMOVED
                    root-namespace: "urn:example:removed"
                    root-element: Removed
                    xsd: custom/removed/Removed.xsd
                """);
        registry.reload();
        Files.delete(assetDir.resolve(CustomDocumentTypeRegistry.ASSET_PATH));

        registry.reload();

        assertThat(registry.getAll()).isEmpty();
    }

    private void write(String yaml) throws Exception {
        Files.writeString(assetDir.resolve(CustomDocumentTypeRegistry.ASSET_PATH), yaml);
    }
}

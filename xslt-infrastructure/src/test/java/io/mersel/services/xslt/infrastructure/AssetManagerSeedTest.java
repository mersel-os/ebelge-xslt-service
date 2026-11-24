package io.mersel.services.xslt.infrastructure;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code xslt.assets.seed-path}: imaja gömülü veya salt-okunur bağlanan asset'lerin
 * açılışta asset dizinine kopyalanması ve GİB ilk sync kararının buna etkilenmemesi.
 */
@DisplayName("AssetManager — seed")
class AssetManagerSeedTest {

    @TempDir
    Path assetDir;

    @TempDir
    Path seedDir;

    @Test
    @DisplayName("seed_dosyalari_ayni_yollarla_kopyalanir_ve_uzerine_yazar")
    void seed_dosyalari_ayni_yollarla_kopyalanir_ve_uzerine_yazar() throws Exception {
        write(seedDir, "custom-document-types.yml", "document-types: []");
        write(seedDir, "custom/sample/maindoc/Sample.xsd", "<seed/>");
        write(seedDir, "default_transformers/eInvoice_Base.xslt", "<seed-xslt/>");
        write(assetDir, "default_transformers/eInvoice_Base.xslt", "<eski/>");
        write(assetDir, "validator/ubl-tr-package/schema/maindoc/UBL-Invoice-2.1.xsd", "<gib/>");

        AssetManager manager = start(assetDir, seedDir);

        assertThat(Files.readString(assetDir.resolve("custom/sample/maindoc/Sample.xsd"))).isEqualTo("<seed/>");
        assertThat(Files.readString(assetDir.resolve("default_transformers/eInvoice_Base.xslt")))
                .as("seed kaynak kabul edilir").isEqualTo("<seed-xslt/>");
        assertThat(Files.readString(assetDir.resolve("validator/ubl-tr-package/schema/maindoc/UBL-Invoice-2.1.xsd")))
                .as("seed'de olmayan dosyaya dokunulmaz").isEqualTo("<gib/>");
        assertThat(manager.assetExists("custom-document-types.yml")).isTrue();
    }

    @Test
    @DisplayName("gizli_ve_auto_generated_yollar_atlanir")
    void gizli_ve_auto_generated_yollar_atlanir() throws Exception {
        // Kubernetes ConfigMap/Secret bağlamaları gerçek dosyaları ..<zaman damgası>/ altında tutar.
        write(seedDir, "..2026_11_24_00_00_00.000000/custom-document-types.yml", "gizli");
        write(seedDir, ".git/config", "gizli");
        write(seedDir, "auto-generated/schematron/x.xsl", "uretilmis");
        write(seedDir, "custom-document-types.yml", "document-types: []");

        AssetManager manager = start(assetDir, seedDir);

        assertThat(manager.seedFrom(seedDir)).isEqualTo(1);
        assertThat(assetDir.resolve("..2026_11_24_00_00_00.000000")).doesNotExist();
        assertThat(assetDir.resolve(".git")).doesNotExist();
        assertThat(assetDir.resolve("auto-generated/schematron/x.xsl")).doesNotExist();
    }

    @Test
    @DisplayName("seed_dizini_yoksa_acilis_bozulmaz")
    void seed_dizini_yoksa_acilis_bozulmaz() throws Exception {
        AssetManager manager = start(assetDir, assetDir.resolve("yok"));

        assertThat(manager.isConfigured()).isTrue();
        assertThat(manager.isEmpty()).isTrue();
    }

    @Test
    @DisplayName("alt_dizin_bosluk_kontrolu_yalniz_o_dizine_bakar")
    void alt_dizin_bosluk_kontrolu_yalniz_o_dizine_bakar() throws Exception {
        write(seedDir, "custom-document-types.yml", "document-types: []");
        AssetManager manager = start(assetDir, seedDir);

        assertThat(manager.isEmpty()).isFalse();
        assertThat(manager.isEmpty(GibAutoSyncStartupListener.GIB_PACKAGE_ROOT))
                .as("özel asset'ler GİB ilk sync kararını etkilemez").isTrue();
        assertThat(manager.isEmpty("../")).isTrue();

        write(assetDir, "validator/eledger/schema/edefter.xsd", "<gib/>");
        assertThat(manager.isEmpty(GibAutoSyncStartupListener.GIB_PACKAGE_ROOT)).isFalse();
    }

    @Test
    @DisplayName("sembolik_bagli_paket_dizini_dolu_sayilir — validator/ bağlantısı izlenir")
    void sembolik_bagli_paket_dizini_dolu_sayilir() throws Exception {
        write(seedDir, "eledger/schema/edefter.xsd", "<gib/>");
        Files.createSymbolicLink(assetDir.resolve(GibAutoSyncStartupListener.GIB_PACKAGE_ROOT), seedDir);
        AssetManager manager = start(assetDir, assetDir.resolve("seed-yok"));

        assertThat(manager.isEmpty(GibAutoSyncStartupListener.GIB_PACKAGE_ROOT))
                .as("bağlantının arkasındaki paketler ilk kurulum sayılmamalı").isFalse();
    }

    @Test
    @DisplayName("sembolik_bagli_bos_paket_dizini_bos_sayilir")
    void sembolik_bagli_bos_paket_dizini_bos_sayilir() throws Exception {
        Files.createSymbolicLink(assetDir.resolve(GibAutoSyncStartupListener.GIB_PACKAGE_ROOT), seedDir);
        AssetManager manager = start(assetDir, assetDir.resolve("seed-yok"));

        assertThat(manager.isEmpty(GibAutoSyncStartupListener.GIB_PACKAGE_ROOT)).isTrue();
    }

    @Test
    @DisplayName("seed_baglanti_dongusu_acilisi_durdurmaz — döngü atlanır, diğer dosyalar kopyalanır")
    void seed_baglanti_dongusu_acilisi_durdurmaz() throws Exception {
        write(seedDir, "custom-document-types.yml", "document-types: []");
        write(seedDir, "custom/sample/Sample.xsd", "<seed/>");
        Files.createSymbolicLink(seedDir.resolve("custom/sample/dongu"), seedDir);

        start(assetDir, seedDir);

        assertThat(assetDir.resolve("custom-document-types.yml")).exists();
        assertThat(assetDir.resolve("custom/sample/Sample.xsd")).hasContent("<seed/>");
        assertThat(assetDir.resolve("custom/sample/dongu")).doesNotExist();
    }

    @Test
    @DisplayName("seed_asset_dizinini_iceremez — üst dizin seed olursa kopyalama yapılmaz")
    void seed_asset_dizinini_iceremez() throws Exception {
        Path nestedAssets = seedDir.resolve("assets");
        write(seedDir, "custom-document-types.yml", "document-types: []");
        Files.createDirectories(nestedAssets);

        AssetManager manager = start(nestedAssets, seedDir);

        assertThat(manager.isConfigured()).isTrue();
        assertThat(nestedAssets.resolve("assets")).as("iç içe kopya oluşmamalı").doesNotExist();
        assertThat(nestedAssets.resolve("custom-document-types.yml")).doesNotExist();
    }

    @Test
    @DisplayName("seed_asset_dizininin_kendisi_olamaz")
    void seed_asset_dizininin_kendisi_olamaz() throws Exception {
        write(assetDir, "validation-profiles.yml", "profiles: {}");

        AssetManager manager = start(assetDir, assetDir);

        assertThat(manager.seedFrom(assetDir)).isZero();
        assertThat(assetDir.resolve("validation-profiles.yml")).hasContent("profiles: {}");
    }

    private static AssetManager start(Path assetDir, Path seedDir) throws Exception {
        AssetManager manager = new AssetManager();
        set(manager, "externalPath", assetDir.toString());
        set(manager, "seedPath", seedDir.toString());
        manager.init();
        return manager;
    }

    private static void set(AssetManager manager, String name, String value) throws Exception {
        Field field = AssetManager.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(manager, value);
    }

    private static void write(Path root, String relative, String content) throws Exception {
        Path file = root.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }
}

package io.mersel.services.xslt.infrastructure;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * AssetManager birim testleri.
 * <p>
 * {@code @TempDir} ile geçici dizin oluşturulur, reflection ile
 * {@code externalPath} field'ı set edilip {@code init()} çağrılır.
 */
@DisplayName("AssetManager")
class AssetManagerTest {

    @TempDir
    Path tempDir;

    private AssetManager assetManager;

    @BeforeEach
    void setUp() throws Exception {
        assetManager = new AssetManager();
        setExternalPath(tempDir.toString());
        callInit();
    }

    // ── getAssetStream ──────────────────────────────────────────────────

    @Test
    @DisplayName("getAssetStream_basarili — Dosya varsa InputStream ile doğru içerik okunmalı")
    void getAssetStream_basarili() throws Exception {
        Path file = tempDir.resolve("test.xsd");
        Files.writeString(file, "<schema>test</schema>");

        try (InputStream is = assetManager.getAssetStream("test.xsd")) {
            String content = new String(is.readAllBytes());
            assertThat(content).isEqualTo("<schema>test</schema>");
        }
    }

    @Test
    @DisplayName("getAssetStream_dosya_yok_exception — Dosya yoksa IOException fırlatmalı")
    void getAssetStream_dosya_yok_exception() {
        assertThatThrownBy(() -> assetManager.getAssetStream("nonexistent.xsd"))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("bulunamadı");
    }

    @Test
    @DisplayName("getAssetStream_path_traversal_engeli — Path traversal SecurityException fırlatmalı")
    void getAssetStream_path_traversal_engeli() {
        assertThatThrownBy(() -> assetManager.getAssetStream("../../etc/passwd"))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("Path traversal");
    }

    // ── assetExists ─────────────────────────────────────────────────────

    @Test
    @DisplayName("assetExists_var — Mevcut dosya için true dönmeli")
    void assetExists_var() throws Exception {
        Path file = tempDir.resolve("existing.xml");
        Files.writeString(file, "<data/>");

        assertThat(assetManager.assetExists("existing.xml")).isTrue();
    }

    @Test
    @DisplayName("assetExists_yok — Mevcut olmayan dosya için false dönmeli")
    void assetExists_yok() {
        assertThat(assetManager.assetExists("nonexistent.xml")).isFalse();
    }

    @Test
    @DisplayName("assetExists_path_traversal_false — Path traversal için false dönmeli (exception değil)")
    void assetExists_path_traversal_false() {
        assertThat(assetManager.assetExists("../../etc/passwd")).isFalse();
    }

    // ── resolveAssetOnDisk ──────────────────────────────────────────────

    @Test
    @DisplayName("resolveAssetOnDisk_basarili — Mevcut dosya için doğru Path dönmeli")
    void resolveAssetOnDisk_basarili() throws Exception {
        Path subDir = tempDir.resolve("validator/ubl-tr-package/schema");
        Files.createDirectories(subDir);
        Path file = subDir.resolve("invoice.xsd");
        Files.writeString(file, "<xsd/>");

        Path resolved = assetManager.resolveAssetOnDisk("validator/ubl-tr-package/schema/invoice.xsd");

        assertThat(resolved).isEqualTo(file);
        assertThat(Files.exists(resolved)).isTrue();
    }

    @Test
    @DisplayName("resolveAssetOnDisk_path_traversal_engeli — Path traversal SecurityException fırlatmalı")
    void resolveAssetOnDisk_path_traversal_engeli() {
        assertThatThrownBy(() -> assetManager.resolveAssetOnDisk("../../etc/passwd"))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("Path traversal");
    }

    // ── listFiles ───────────────────────────────────────────────────────

    @Test
    @DisplayName("listFiles_basarili — Dizindeki dosyaları listelemeli")
    void listFiles_basarili() throws Exception {
        Path subDir = tempDir.resolve("validator/ubl-tr-package/schema");
        Files.createDirectories(subDir);
        Files.writeString(subDir.resolve("a.xsd"), "aaa");
        Files.writeString(subDir.resolve("b.xsd"), "bbb");
        Files.writeString(subDir.resolve("c.xml"), "ccc");

        var files = assetManager.listFiles("validator/ubl-tr-package/schema");

        assertThat(files).containsExactly("a.xsd", "b.xsd", "c.xml");
    }

    @Test
    @DisplayName("listFiles_path_traversal_bos_liste — Path traversal için boş liste dönmeli")
    void listFiles_path_traversal_bos_liste() {
        var files = assetManager.listFiles("../../etc");

        assertThat(files).isEmpty();
    }

    // ── readAssetBytes ──────────────────────────────────────────────────

    @Test
    @DisplayName("readAssetBytes_basarili — Dosya içeriğini byte dizisi olarak okumalı")
    void readAssetBytes_basarili() throws Exception {
        byte[] expected = "Merhaba Dünya".getBytes();
        Path file = tempDir.resolve("content.txt");
        Files.write(file, expected);

        byte[] actual = assetManager.readAssetBytes("content.txt");

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    @DisplayName("readAssetBytes_path_traversal_engeli — Path traversal SecurityException fırlatmalı")
    void readAssetBytes_path_traversal_engeli() {
        assertThatThrownBy(() -> assetManager.readAssetBytes("../../etc/passwd"))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("Path traversal");
    }

    // ── writeAutoGenerated ──────────────────────────────────────────────

    @Test
    @DisplayName("writeAutoGenerated_basarili — Auto-generated dizinine dosya yazmalı")
    void writeAutoGenerated_basarili() throws Exception {
        byte[] content = "<xsl:stylesheet/>".getBytes();

        assetManager.writeAutoGenerated("schematron", "UBLTR_MAIN.xsl", content);

        Path expectedFile = tempDir.resolve("auto-generated/schematron/UBLTR_MAIN.xsl");
        assertThat(Files.exists(expectedFile)).isTrue();
        assertThat(Files.readAllBytes(expectedFile)).isEqualTo(content);
    }

    @Test
    @DisplayName("writeAutoGenerated_path_traversal_engeli — Dosya adında ../ SecurityException fırlatmalı")
    void writeAutoGenerated_path_traversal_engeli() {
        byte[] content = "malicious".getBytes();

        assertThatThrownBy(() -> assetManager.writeAutoGenerated("schematron", "../../../evil.txt", content))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("Path traversal");
    }

    // ── isConfigured ────────────────────────────────────────────────────

    @Test
    @DisplayName("isConfigured_true_ayarli — External path ayarlandığında true dönmeli")
    void isConfigured_true_ayarli() {
        assertThat(assetManager.isConfigured()).isTrue();
        assertThat(assetManager.getExternalDir()).isNotNull();
    }

    @Test
    @DisplayName("isConfigured_false_ayarsiz — External path ayarlanmadığında false dönmeli")
    void isConfigured_false_ayarsiz() throws Exception {
        AssetManager unconfigured = new AssetManager();
        setExternalPath(unconfigured, "");
        callInit(unconfigured);

        assertThat(unconfigured.isConfigured()).isFalse();
        assertThat(unconfigured.getExternalDir()).isNull();
    }

    // ── Yardımcı Metotlar ────────────────────────────────────────────────

    /**
     * Reflection ile varsayılan AssetManager üzerinde externalPath set eder.
     */
    private void setExternalPath(String path) throws Exception {
        setExternalPath(assetManager, path);
    }

    /**
     * Reflection ile belirtilen AssetManager üzerinde externalPath set eder.
     */
    private void setExternalPath(AssetManager manager, String path) throws Exception {
        Field field = AssetManager.class.getDeclaredField("externalPath");
        field.setAccessible(true);
        field.set(manager, path);
    }

    /**
     * Reflection ile varsayılan AssetManager.init() çağırır.
     */
    private void callInit() throws Exception {
        callInit(assetManager);
    }

    /**
     * Reflection ile belirtilen AssetManager.init() çağırır.
     */
    private void callInit(AssetManager manager) throws Exception {
        java.lang.reflect.Method initMethod = AssetManager.class.getDeclaredMethod("init");
        initMethod.setAccessible(true);
        initMethod.invoke(manager);
    }
}

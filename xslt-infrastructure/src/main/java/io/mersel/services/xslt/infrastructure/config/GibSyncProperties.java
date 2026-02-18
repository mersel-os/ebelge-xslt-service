package io.mersel.services.xslt.infrastructure.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * GİB paket sync yapılandırma özellikleri.
 * <p>
 * {@code validation-assets.gib.sync} prefix'i altındaki değerleri okur.
 * <ul>
 *   <li>{@code enabled} — Sync özelliğini aç/kapa</li>
 *   <li>{@code auto-sync-on-startup} — İlk kurulumda (asset dizini boşken) otomatik sync (varsayılan: true)</li>
 *   <li>{@code target-path} — İndirilen dosyaların yazılacağı dizin (boşsa xslt.assets.external-path kullanılır)</li>
 *   <li>{@code connect-timeout-ms} — HTTP bağlantı zaman aşımı (pozitif olmalı)</li>
 *   <li>{@code read-timeout-ms} — HTTP okuma zaman aşımı (pozitif olmalı)</li>
 * </ul>
 */
@ConfigurationProperties(prefix = "validation-assets.gib.sync")
public class GibSyncProperties {

    private static final Logger log = LoggerFactory.getLogger(GibSyncProperties.class);

    private boolean enabled = true;
    private boolean autoSyncOnStartup = true;
    private String targetPath = "";
    private String baseUrlOverride = "";  // For testing: override host in download URLs (e.g. http://localhost:8089)
    private int connectTimeoutMs = 10000;
    private int readTimeoutMs = 60000;

    @PostConstruct
    void validate() {
        if (connectTimeoutMs <= 0) {
            log.warn("connect-timeout-ms değeri pozitif olmalı (verilen: {}), varsayılan 10000 ms kullanılıyor", connectTimeoutMs);
            connectTimeoutMs = 10000;
        }
        if (readTimeoutMs <= 0) {
            log.warn("read-timeout-ms değeri pozitif olmalı (verilen: {}), varsayılan 60000 ms kullanılıyor", readTimeoutMs);
            readTimeoutMs = 60000;
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isAutoSyncOnStartup() {
        return autoSyncOnStartup;
    }

    public void setAutoSyncOnStartup(boolean autoSyncOnStartup) {
        this.autoSyncOnStartup = autoSyncOnStartup;
    }

    public String getTargetPath() {
        return targetPath;
    }

    public void setTargetPath(String targetPath) {
        this.targetPath = targetPath;
    }

    public String getBaseUrlOverride() {
        return baseUrlOverride;
    }

    public void setBaseUrlOverride(String baseUrlOverride) {
        this.baseUrlOverride = baseUrlOverride;
    }

    public int getConnectTimeoutMs() {
        return connectTimeoutMs;
    }

    public void setConnectTimeoutMs(int connectTimeoutMs) {
        this.connectTimeoutMs = connectTimeoutMs;
    }

    public int getReadTimeoutMs() {
        return readTimeoutMs;
    }

    public void setReadTimeoutMs(int readTimeoutMs) {
        this.readTimeoutMs = readTimeoutMs;
    }
}

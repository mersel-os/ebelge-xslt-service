package io.mersel.services.xslt.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * XSLT sandbox istisnaları ({@code xslt.sandbox} prefix'i).
 * <p>
 * Varsayılan olarak boştur: sandbox'lı XSLT ve Schematron hiçbir dosyayı okuyamaz. İstisnalar
 * yalnızca operatörün yönettiği içeriğe uygulanır (varsayılan XSLT şablonları ve Schematron, özel
 * kurallar dahil); istekle gelen ve belgeye gömülü XSLT her zaman tam kısıtlı çalışır.
 * <ul>
 *   <li>{@code resource-dirs} — İçindeki dosyaların {@code doc()}, {@code document()},
 *       {@code unparsed-text*()}, {@code json-doc()} ve {@code xsl:include}/{@code xsl:import} ile
 *       okunabileceği mutlak dizinler (env: {@code XSLT_SANDBOX_RESOURCE_DIRS}, virgülle ayrılmış).</li>
 * </ul>
 * Ortam değişkenleri ve JVM özellikleri için istisna yoktur; ortama bağlı bir değer gerekiyorsa
 * başlangıçta izinli dizine bir dosya olarak yazılabilir. Değerler yalnızca uygulama başlarken
 * okunur; yönetici API'si bunları değiştiremez.
 */
@ConfigurationProperties(prefix = "xslt.sandbox")
public class XsltSandboxProperties {

    private List<String> resourceDirs = new ArrayList<>();

    public List<String> getResourceDirs() {
        return resourceDirs;
    }

    public void setResourceDirs(List<String> resourceDirs) {
        this.resourceDirs = resourceDirs != null ? resourceDirs : new ArrayList<>();
    }
}

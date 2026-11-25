package io.mersel.services.xslt.infrastructure;

import io.mersel.services.xslt.infrastructure.config.InfrastructureConfig;
import io.mersel.services.xslt.infrastructure.config.XsltSandboxProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * {@link XsltSandboxPolicy} birim testleri: yapılandırma okuma ve yol kontrolü.
 */
@DisplayName("XsltSandboxPolicy")
class XsltSandboxPolicyTest {

    @TempDir
    Path tempDir;

    private Path allowed;
    private Path outside;

    @BeforeEach
    void setUp() throws Exception {
        allowed = Files.createDirectories(tempDir.resolve("allowed"));
        outside = Files.createDirectories(tempDir.resolve("outside"));
        Files.writeString(allowed.resolve("codes.xml"), "<codes/>");
        Files.createDirectories(allowed.resolve("sub"));
        Files.writeString(allowed.resolve("sub/inner.xml"), "<i/>");
        Files.writeString(outside.resolve("secret.txt"), "gizli");
    }

    // ── Yapılandırma ───────────────────────────────────────────────

    @Test
    @DisplayName("Boş ve boşluklu girdiler istisnasız politika verir")
    void emptyValuesGiveStrictPolicy() {
        assertThat(XsltSandboxPolicy.of(List.of())).isSameAs(XsltSandboxPolicy.STRICT);
        assertThat(XsltSandboxPolicy.of(List.of(" ", ""))).isSameAs(XsltSandboxPolicy.STRICT);
        assertThat(XsltSandboxPolicy.of(null).isStrict()).isTrue();
    }

    @Test
    @DisplayName("Göreli dizin ve kök dizin reddedilir")
    void relativeAndRootDirectoriesAreRejected() {
        assertThatThrownBy(() -> XsltSandboxPolicy.of(List.of("assets/resources")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("mutlak");
        assertThatThrownBy(() -> XsltSandboxPolicy.of(List.of("/")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("kök");
    }

    @Test
    @DisplayName("Virgülle ayrılmış ortam değişkeni değeri listeye bağlanır")
    void propertiesBindFromCommaSeparatedValues() {
        var source = new MapConfigurationPropertySource(Map.of(
                "xslt.sandbox.resource-dirs", allowed + ", " + outside));
        var properties = new Binder(source).bind("xslt.sandbox", XsltSandboxProperties.class).get();

        var policy = new InfrastructureConfig().xsltSandboxPolicy(properties);

        assertThat(policy.resourceDirs()).containsExactly(allowed, outside);
    }

    @Test
    @DisplayName("Boş yapılandırma değeri istisnasız politika verir")
    void emptyPropertyValuesGiveStrictPolicy() {
        var source = new MapConfigurationPropertySource(Map.of("xslt.sandbox.resource-dirs", ""));
        var properties = new Binder(source).bindOrCreate("xslt.sandbox", XsltSandboxProperties.class);

        assertThat(new InfrastructureConfig().xsltSandboxPolicy(properties).isStrict()).isTrue();
        assertThat(new InfrastructureConfig().xsltSandboxPolicy(new XsltSandboxProperties()).isStrict()).isTrue();
    }

    // ── Dosya kontrolü ─────────────────────────────────────────────

    @Test
    @DisplayName("İzinli dizindeki ve alt dizinlerdeki dosyalar okunabilir")
    void filesInsideAllowedDirectoryAreAllowed() {
        var policy = policy();

        assertThat(policy.isResourceAllowed(allowed.resolve("codes.xml").toUri().toString())).isTrue();
        assertThat(policy.isResourceAllowed(allowed.resolve("sub/inner.xml").toUri().toString())).isTrue();
    }

    @Test
    @DisplayName("Dizin dışı, .. ile kaçış, dizinin kendisi ve olmayan dosya reddedilir")
    void pathsOutsideAllowedDirectoryAreRejected() {
        var policy = policy();

        assertThat(policy.isResourceAllowed(outside.resolve("secret.txt").toUri().toString())).isFalse();
        assertThat(policy.isResourceAllowed("file://" + allowed + "/../outside/secret.txt")).isFalse();
        assertThat(policy.isResourceAllowed(allowed.toUri().toString())).isFalse();
        assertThat(policy.isResourceAllowed(allowed.resolve("missing.xml").toUri().toString())).isFalse();
    }

    @Test
    @DisplayName("Benzer önekli kardeş dizin izinli sayılmaz")
    void siblingDirectoryWithSamePrefixIsRejected() throws Exception {
        Path sibling = Files.createDirectories(tempDir.resolve("allowed-other"));
        Files.writeString(sibling.resolve("x.xml"), "<x/>");

        assertThat(policy().isResourceAllowed(sibling.resolve("x.xml").toUri().toString())).isFalse();
    }

    @Test
    @DisplayName("Gerçek yolu kök dizin olan dizin reddedilir")
    void directoryResolvingToRootIsRejected() throws Exception {
        Path rootLink = tempDir.resolve("root-link");
        try {
            Files.createSymbolicLink(rootLink, Path.of("/"));
        } catch (UnsupportedOperationException | java.io.IOException e) {
            assumeTrue(false, "Sembolik bağ oluşturulamıyor");
        }

        assertThatThrownBy(() -> XsltSandboxPolicy.of(List.of(rootLink.toString())))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("kök");
    }

    @Test
    @DisplayName("Sonradan köke giden sembolik bağ olarak oluşturulan dizin hiçbir dosyaya izin vermez")
    void directoryLaterLinkedToRootAllowsNothing() throws Exception {
        Path later = tempDir.resolve("later-root");
        var policy = XsltSandboxPolicy.of(List.of(later.toString()));
        try {
            Files.createSymbolicLink(later, Path.of("/"));
        } catch (UnsupportedOperationException | java.io.IOException e) {
            assumeTrue(false, "Sembolik bağ oluşturulamıyor");
        }

        assertThat(policy.isResourceAllowed(outside.resolve("secret.txt").toUri().toString())).isFalse();
    }

    @Test
    @DisplayName("İzin verilen dosyanın gerçek yolu döner")
    void allowedRealPathIsTheResolvedFile() throws Exception {
        Path viaDots = allowed.resolve("sub/../codes.xml");

        assertThat(policy().allowedRealPath(viaDots.toUri().toString()))
                .isEqualTo(allowed.resolve("codes.xml").toRealPath());
    }

    @Test
    @DisplayName("Dizin dışını gösteren sembolik bağ reddedilir")
    void symlinkPointingOutsideIsRejected() throws Exception {
        Path link = allowed.resolve("link.txt");
        try {
            Files.createSymbolicLink(link, outside.resolve("secret.txt"));
        } catch (UnsupportedOperationException | java.io.IOException e) {
            assumeTrue(false, "Sembolik bağ oluşturulamıyor");
        }

        assertThat(policy().isResourceAllowed(link.toUri().toString())).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://127.0.0.1:9/codes.xml", "https://example.com/a.xml", "jar:file:/a.jar!/x.xml",
            "ftp://example.com/a.xml", "not a uri", "codes.xml"})
    @DisplayName("file: dışı ve göreli URI'ler reddedilir")
    void nonFileUrisAreRejected(String uri) {
        assertThat(policy().isResourceAllowed(uri)).isFalse();
    }

    @Test
    @DisplayName("Sorgu, parça ya da sunucu adı içeren file: URI'si reddedilir")
    void fileUrisWithQueryFragmentOrHostAreRejected() {
        String file = allowed.resolve("codes.xml").toUri().toString();

        assertThat(policy().isResourceAllowed(file + "?x=1")).isFalse();
        assertThat(policy().isResourceAllowed(file + "#frag")).isFalse();
        assertThat(policy().isResourceAllowed("file://remote-host" + allowed.resolve("codes.xml"))).isFalse();
    }

    @Test
    @DisplayName("Başlangıçta olmayan dizin sonradan oluşturulunca kullanılır")
    void directoryCreatedLaterIsUsed() throws Exception {
        Path later = tempDir.resolve("later");
        var policy = XsltSandboxPolicy.of(List.of(later.toString()));
        Path file = later.resolve("added.xml");

        assertThat(policy.isResourceAllowed(file.toUri().toString())).isFalse();

        Files.createDirectories(later);
        Files.writeString(file, "<added/>");

        assertThat(policy.isResourceAllowed(file.toUri().toString())).isTrue();
    }

    @Test
    @DisplayName("İstisnasız politika hiçbir dosyaya izin vermez")
    void strictPolicyAllowsNothing() {
        assertThat(XsltSandboxPolicy.STRICT.isResourceAllowed(allowed.resolve("codes.xml").toUri().toString()))
                .isFalse();
    }

    private XsltSandboxPolicy policy() {
        return XsltSandboxPolicy.of(List.of(allowed.toString()));
    }
}

package io.mersel.services.xslt.infrastructure;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

/**
 * {@link XsltSandbox} için operatörün açtığı istisnalar.
 * <p>
 * {@link #STRICT} hiçbir istisna içermez. Bir politika yalnızca yapılandırılmış dizinlerin altındaki
 * <b>var olan, normal</b> dosyaların mutlak {@code file:} URI'siyle okunmasına izin verebilir. Yol,
 * sembolik bağlar ve {@code ..} çözüldükten sonra ({@link Path#toRealPath}) dizinin içinde kalmalıdır;
 * dosya bu gerçek yoldan açılır. Gerçek yolu kök dizin olan bir dizin (ör. köke giden sembolik bağ)
 * hiçbir dosyaya izin vermez.
 * Dizin listeleme ({@code collection()}), ağ, yazma, ortam değişkenleri ve JVM özellikleri kapalı kalır.
 * <p>
 * Dizinler her kontrolde yeniden çözülür; uygulama çalışırken dizine eklenen dosyalar
 * yeniden başlatma gerekmeden okunabilir.
 */
public final class XsltSandboxPolicy {

    /** İstisnasız politika: hiçbir dosya okunamaz. */
    public static final XsltSandboxPolicy STRICT = new XsltSandboxPolicy(List.of());

    private final List<Path> resourceDirs;

    private XsltSandboxPolicy(List<Path> resourceDirs) {
        this.resourceDirs = List.copyOf(resourceDirs);
    }

    /**
     * Yapılandırma değerlerinden politika oluşturur. Boş ve yalnızca boşluk içeren girdiler atlanır.
     *
     * @throws IllegalArgumentException dizin mutlak değilse, kök dizinse ya da gerçek yolu kök dizinse
     */
    public static XsltSandboxPolicy of(Collection<String> resourceDirs) {
        var dirs = new LinkedHashSet<Path>();
        for (String value : nonBlank(resourceDirs)) {
            Path dir = Path.of(value).normalize();
            if (!dir.isAbsolute()) {
                throw new IllegalArgumentException("xslt.sandbox.resource-dirs mutlak yol olmalı: " + value);
            }
            if (dir.getParent() == null || isRootAfterResolving(dir)) {
                throw new IllegalArgumentException("xslt.sandbox.resource-dirs kök dizin olamaz: " + value);
            }
            dirs.add(dir);
        }

        return dirs.isEmpty() ? STRICT : new XsltSandboxPolicy(List.copyOf(dirs));
    }

    /** Var olan ve sembolik bağlar çözülünce kök dizine (ör. {@code /proc/self/root}) çıkan dizin. */
    private static boolean isRootAfterResolving(Path dir) {
        try {
            return Files.exists(dir) && dir.toRealPath().getParent() == null;
        } catch (Exception e) {
            return false;
        }
    }

    private static List<String> nonBlank(Collection<String> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream().filter(Objects::nonNull).map(String::strip).filter(v -> !v.isEmpty()).toList();
    }

    public List<Path> resourceDirs() {
        return resourceDirs;
    }

    public boolean isStrict() {
        return resourceDirs.isEmpty();
    }

    boolean isResourceAllowed(String uri) {
        return allowedRealPath(uri) != null;
    }

    /**
     * URI, izinli dizinlerden birinin altındaki var olan bir normal dosyayı gösteriyorsa o dosyanın
     * gerçek yolunu, aksi halde {@code null} döner. Dosya bu yoldan açılmalıdır; böylece kontrol ile
     * açma arasında sembolik bağ değişse bile dizin dışına çıkılmaz. Ayrıştırılamayan, göreli,
     * {@code file:} dışı, sorgu/parça içeren ya da sunucu adı taşıyan URI'ler reddedilir.
     */
    Path allowedRealPath(String uri) {
        if (resourceDirs.isEmpty() || uri == null) {
            return null;
        }
        try {
            var parsed = new URI(uri);
            if (!"file".equalsIgnoreCase(parsed.getScheme())) {
                return null;
            }
            // Path.of sorgu, parça ya da sunucu adı içeren URI'yi reddeder.
            Path real = Path.of(parsed).toRealPath();
            if (!Files.isRegularFile(real)) {
                return null;
            }
            for (Path dir : resourceDirs) {
                if (!Files.isDirectory(dir)) {
                    continue;
                }
                Path realDir = dir.toRealPath();
                if (realDir.getParent() != null && real.startsWith(realDir)) {
                    return real;
                }
            }
            return null;
        } catch (Exception e) {
            // Geçersiz URI, olmayan dosya, erişim hatası: reddet.
            return null;
        }
    }

    @Override
    public String toString() {
        return "XsltSandboxPolicy{resourceDirs=" + resourceDirs + "}";
    }
}

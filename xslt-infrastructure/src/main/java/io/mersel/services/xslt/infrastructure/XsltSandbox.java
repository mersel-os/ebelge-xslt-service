package io.mersel.services.xslt.infrastructure;

import net.sf.saxon.Configuration;
import net.sf.saxon.functions.FunctionLibrary;
import net.sf.saxon.functions.FunctionLibraryList;
import net.sf.saxon.functions.registry.BuiltInFunctionSet;
import net.sf.saxon.functions.registry.UseWhen30FunctionSet;
import net.sf.saxon.functions.registry.VendorFunctionSetHE;
import net.sf.saxon.functions.registry.XSLT30FunctionSet;
import net.sf.saxon.lib.ActiveSource;
import net.sf.saxon.lib.AugmentedSource;
import net.sf.saxon.lib.EnvironmentVariableResolver;
import net.sf.saxon.lib.Feature;
import net.sf.saxon.lib.ParseOptions;
import net.sf.saxon.lib.ResourceRequest;
import net.sf.saxon.lib.ResourceResolver;
import net.sf.saxon.lib.SourceResolver;
import net.sf.saxon.om.NamespaceUri;
import net.sf.saxon.lib.StandardUnparsedTextResolver;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.SaxonApiUncheckedException;
import net.sf.saxon.s9api.Xslt30Transformer;
import net.sf.saxon.s9api.XsltCompiler;
import net.sf.saxon.s9api.XsltExecutable;
import net.sf.saxon.trans.SymbolicName;
import net.sf.saxon.trans.XPathException;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

import javax.xml.XMLConstants;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.transform.Source;
import javax.xml.transform.sax.SAXSource;
import javax.xml.transform.stream.StreamSource;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Güvenilmeyen XSLT ve XML ile çalışan Saxon dönüşümleri için sandbox.
 * <p>
 * Saxon varsayılan ayarlarıyla bir XSLT; {@code environment-variable()} ile ortam değişkenlerini,
 * {@code system-property()} ile JVM özelliklerini, {@code unparsed-text*()}, {@code json-doc()},
 * {@code collection()} ve {@code uri-collection()} ile dosya sistemini ya da ağı okuyabilir ve
 * {@code xsl:result-document} ile diske yazabilir. Varsayılan XML ayrıştırıcısı da DOCTYPE içindeki
 * harici varlıkları çözer (XXE). Bu sınıf, kullanıcının yüklediği XSLT, belgeye gömülü XSLT,
 * varsayılan şablon yollarında ve Schematron doğrulamasında (yönetici API'siyle eklenen özel kurallar
 * dahil, derlenmiş Schematron XSLT'leri) bu kapıları kapatır (istisnasız politikada):
 * <ul>
 *   <li>İzinli protokol listesi boştur ({@link Feature#ALLOWED_PROTOCOLS}); {@code doc()},
 *       {@code xsl:include}, {@code xsl:source-document}, {@code fn:transform} gibi okumalar hiçbir
 *       şemayla dışarı çıkamaz. Yapılandırma, derleyici ve transformer düzeyindeki resource resolver da her isteği reddeder.</li>
 *   <li>{@code environment-variable()} her zaman boş döner; {@code available-environment-variables()} boştur.</li>
 *   <li>Harici fonksiyonlar kapalıdır ({@link Feature#ALLOW_EXTERNAL_FUNCTIONS}): {@code system-property()}
 *       JVM özelliklerini döndürmez, {@code href} içeren {@code xsl:result-document} derlenmez.
 *       Transformer düzeyindeki result-document handler da her hedefi reddeder.</li>
 *   <li>{@code unparsed-text*()}, {@code json-doc()}, {@code collection()} ve {@code uri-collection()}
 *       {@code FODC0002} hatasıyla reddedilir ({@code unparsed-text-available()} {@code false} döner).</li>
 *   <li>Girdi XML'i ve XSLT, harici DTD yüklemeyen ve harici varlık çözmeyen bir ayrıştırıcıyla okunur.
 *       Aynı ayarlar Saxon'un kendi açtığı ayrıştırıcılara da ({@code parse-xml()} vb.) uygulanır.
 *       İç DTD alt kümesindeki varlıklar (GİB şablonlarındaki {@code &nbsp;} tanımları gibi)
 *       çalışmaya devam eder; genişleme sınırları {@link XMLConstants#FEATURE_SECURE_PROCESSING} ile gelir.</li>
 *   <li>{@code fn:transform} ve {@code fn:load-xquery-module} yoktur ({@link #UNAVAILABLE_FUNCTIONS}):
 *       çağrı sırasında sıfırdan yeni bir Saxon yapılandırması kurabildikleri için bu kısıtları taşımazlar.
 *       Derleme zamanı ifadeleri, {@code function-lookup()} ve isimli fonksiyon referansları da onları bulamaz.</li>
 *   <li>{@code saxon:doc()} yoktur ({@link #UNAVAILABLE_VENDOR_FUNCTIONS}): belgeyi resource resolver'a
 *       ve izinli protokol listesine uğramadan açar. {@code xsl:evaluate}, {@code use-when} ve
 *       {@code static} parametreler de onu bulamaz. Diğer Saxon fonksiyonları çalışır.</li>
 *   <li>Saxon'un bir URI'den kendisi açacağı kaynaklar (akışı olmayan, yalnızca system id taşıyan
 *       {@link StreamSource}/{@link SAXSource}) source resolver düzeyinde aynı kapıdan geçer.</li>
 * </ul>
 * Bu ayarların kapsamı yalnızca {@link #newProcessor()} ile oluşturulan {@link Processor}'dür;
 * diğer Saxon kullanıcılarını etkilemez. Schematron pipeline adımları ({@code sch:include} için dosya
 * okuyan) bu yüzden ayrı, kısıtsız bir işlemcide çalışır; bkz. {@link SchematronRuntimeCompiler}.
 * <p>
 * Operatör, {@link #newProcessor(XsltSandboxPolicy)} ile belirli dizinlerdeki dosyaları açabilir
 * ({@code xslt.sandbox.resource-dirs}); ortam değişkenleri ve JVM özellikleri her zaman kapalıdır.
 * Bu durumda da izinli protokol listesi boştur: her okuma yapılandırma, derleyici ve transformer
 * düzeyindeki aynı kapıdan geçer ve izinli dosyayı kapı, kontrol ettiği gerçek yoldan kendisi açar.
 * Dizin dışındaki dosyalar, ağ, dizin listeleme ve yazma yine kapalıdır. Politika yalnızca operatörün yönettiği içeriği (varsayılan şablonlar, Schematron)
 * derleyen işlemcilere verilir.
 */
final class XsltSandbox {

    private static final String EXTERNAL_GENERAL_ENTITIES =
            "http://xml.org/sax/features/external-general-entities";
    private static final String EXTERNAL_PARAMETER_ENTITIES =
            "http://xml.org/sax/features/external-parameter-entities";
    private static final String LOAD_EXTERNAL_DTD =
            "http://apache.org/xml/features/nonvalidating/load-external-dtd";

    /**
     * Hem kendi ayrıştırıcımıza hem Saxon'un açtığı ayrıştırıcılara uygulanan özellikler.
     * <p>
     * JDK ayrıştırıcısında {@code FEATURE_SECURE_PROCESSING=true}, {@code accessExternalDTD} ve
     * {@code accessExternalSchema} değerlerini de boşa çeker. Bu değerler ayrıca property olarak
     * verilmez: Saxon 12.5'te {@code ParseOptions.withParserProperty} özellik haritasını property
     * haritasına kopyaladığı için her ayrıştırmada "does not recognize the property" uyarısı basılıyor.
     */
    private static final Map<String, Boolean> SECURE_PARSER_FEATURES = Map.of(
            XMLConstants.FEATURE_SECURE_PROCESSING, true,
            EXTERNAL_GENERAL_ENTITIES, false,
            EXTERNAL_PARAMETER_ENTITIES, false,
            LOAD_EXTERNAL_DTD, false);

    /**
     * Sandbox'ta bulunmayan yerleşik fonksiyonlar. İkisi de bir seçenekle (ör. {@code vendor-options}
     * içinde Saxon yapılandırması) yeni bir {@link Configuration} kurabilir; o yapılandırma bu sınıfın
     * hiçbir kısıtını taşımaz.
     */
    static final Set<String> UNAVAILABLE_FUNCTIONS = Set.of("transform", "load-xquery-module");

    /**
     * Sandbox'ta bulunmayan Saxon fonksiyonları ({@code http://saxon.sf.net/} ad alanı, her arity).
     * {@code saxon:doc()} belgeyi Saxon'un source resolver'ıyla doğrudan açar; resource resolver'dan
     * ve izinli protokol listesinden geçmediği için dosya sistemini ve ağı okuyabilir.
     */
    static final Set<String> UNAVAILABLE_VENDOR_FUNCTIONS = Set.of("doc");

    private static final EnvironmentVariableResolver NO_ENVIRONMENT = new EnvironmentVariableResolver() {
        @Override
        public Set<String> getAvailableEnvironmentVariables() {
            return Set.of();
        }

        @Override
        public String getEnvironmentVariable(String name) {
            return null;
        }
    };

    /** Resolver {@code null} dönerse Saxon standart çözümlemeye düşer; bu yüzden reddetmek için hata fırlatılır. */
    private static final ResourceResolver DENY_RESOURCE = XsltSandbox::denyResource;

    private XsltSandbox() {
    }

    /**
     * İstisnasız ({@link XsltSandboxPolicy#STRICT}) sandbox ayarlarıyla yeni bir Saxon HE
     * {@link Processor} oluşturur.
     */
    static Processor newProcessor() {
        return newProcessor(XsltSandboxPolicy.STRICT);
    }

    /**
     * Sandbox ayarları ve verilen politikanın istisnalarıyla yeni bir Saxon HE {@link Processor} oluşturur.
     */
    static Processor newProcessor(XsltSandboxPolicy policy) {
        var gate = new ResourceGate(policy);

        var processor = new Processor(new SandboxConfiguration());
        // İzinli dosyaları kapı kendisi açar; Saxon'un standart çözümlemesi hiçbir protokolü kullanamaz.
        processor.setConfigurationProperty(Feature.ALLOWED_PROTOCOLS, "");
        // Kapalı kalmalı: açılırsa system-property() JVM özelliklerini döndürür ve href'li
        // xsl:result-document derlenir. Bu yüzden ortam değişkenleri için istisna yoktur.
        processor.setConfigurationProperty(Feature.ALLOW_EXTERNAL_FUNCTIONS, false);
        processor.setConfigurationProperty(Feature.ENVIRONMENT_VARIABLE_RESOLVER, NO_ENVIRONMENT);

        var config = processor.getUnderlyingConfiguration();
        config.setResourceResolver(gate);
        config.setSourceResolver(new GatedSourceResolver(gate, config.getSourceResolver()));
        config.setUnparsedTextURIResolver((uri, encoding, configuration) -> {
            String target = String.valueOf(uri);
            Path real = policy.allowedRealPath(target);
            if (real == null) {
                throw blocked(target);
            }
            return StandardUnparsedTextResolver.getReaderFromStreamSource(
                    new StreamSource(open(real, target), target), encoding, configuration, false);
        });
        config.setCollectionFinder((context, collectionUri) -> {
            throw blocked(String.valueOf(collectionUri));
        });

        ParseOptions parseOptions = config.getParseOptions();
        for (var feature : SECURE_PARSER_FEATURES.entrySet()) {
            parseOptions = parseOptions.withParserFeature(feature.getKey(), feature.getValue());
        }
        config.setParseOptions(parseOptions);

        return processor;
    }

    /**
     * {@code xsl:import}/{@code xsl:include} çözümlemesi işlemcinin politikasına bağlı (istisnasız
     * işlemcide tamamen kapalı) bir derleyici döner.
     */
    static XsltCompiler newCompiler(Processor processor) {
        var compiler = processor.newXsltCompiler();
        compiler.setResourceResolver(gateOf(processor.getUnderlyingConfiguration()));
        return compiler;
    }

    /**
     * {@code doc()}/{@code document()} çözümlemesi derleyen işlemcinin politikasına bağlı ve
     * {@code xsl:result-document} çıktısı kapalı bir transformer döner. Sandbox dışında derlenmiş bir
     * executable için çözümleme tamamen kapalıdır.
     */
    static Xslt30Transformer load(XsltExecutable executable) {
        var transformer = executable.load30();
        transformer.setResourceResolver(gateOf(executable.getProcessor().getUnderlyingConfiguration()));
        transformer.setResultDocumentHandler(uri -> {
            throw new SaxonApiUncheckedException(new SaxonApiException(blocked(String.valueOf(uri))));
        });
        return transformer;
    }

    /**
     * İçeriği harici DTD ve varlık çözmeyen bir ayrıştırıcıyla okuyacak kaynak döner.
     */
    static Source secureSource(byte[] content) {
        return secureSource(new ByteArrayInputStream(content));
    }

    /**
     * İçeriği harici DTD ve varlık çözmeyen bir ayrıştırıcıyla okuyacak kaynak döner.
     */
    static Source secureSource(InputStream content) {
        return new SAXSource(newSecureXmlReader(), new InputSource(content));
    }

    private static XMLReader newSecureXmlReader() {
        try {
            var factory = SAXParserFactory.newDefaultInstance();
            factory.setNamespaceAware(true);
            factory.setXIncludeAware(false);
            for (var feature : SECURE_PARSER_FEATURES.entrySet()) {
                factory.setFeature(feature.getKey(), feature.getValue());
            }
            var reader = factory.newSAXParser().getXMLReader();
            // Yukarıdaki özelliklerle çağrılmaması gerekir; çağrılırsa sessizce boş dönmek yerine reddet.
            reader.setEntityResolver((publicId, systemId) -> {
                throw new SAXException("Güvenlik: Harici entity engellendi (XSLT sandbox): " + systemId);
            });
            return reader;
        } catch (ParserConfigurationException | SAXException e) {
            throw new IllegalStateException("Güvenli XML ayrıştırıcısı oluşturulamadı", e);
        }
    }

    private static Source denyResource(ResourceRequest request) throws XPathException {
        throw blocked(request.uri != null ? request.uri : request.relativeUri);
    }

    private static ResourceResolver gateOf(Configuration config) {
        return config.getResourceResolver() instanceof ResourceGate gate ? gate : DENY_RESOURCE;
    }

    /**
     * Politikanın izin verdiği dosyayı kontrol edilen gerçek yoldan kendisi açar; XML olarak okunacak
     * kaynaklar harici DTD/varlık çözmeyen ayrıştırıcıyla okunur. Diğer her isteği reddeder.
     */
    private record ResourceGate(XsltSandboxPolicy policy) implements ResourceResolver {
        @Override
        public Source resolve(ResourceRequest request) throws XPathException {
            Path real = policy.allowedRealPath(request.uri);
            if (real == null) {
                return denyResource(request);
            }
            InputStream content = open(real, request.uri);
            if (ResourceRequest.TEXT_NATURE.equals(request.nature)
                    || ResourceRequest.BINARY_NATURE.equals(request.nature)) {
                return new StreamSource(content, request.uri);
            }
            var input = new InputSource(content);
            input.setSystemId(request.uri);
            return new SAXSource(newSecureXmlReader(), input);
        }
    }

    /**
     * Saxon'un bir URI'den kendisi açacağı kaynakları {@link ResourceGate}'ten geçiren source resolver.
     * <p>
     * Saxon bazı yollarda (ör. {@code saxon:doc()}) resource resolver'ı atlayıp yalnızca system id taşıyan
     * bir kaynağı doğrudan bu resolver'a verir. Böyle bir kaynak, diğer okumalar gibi politikaya göre ya
     * kapının açtığı izinli dosyaya çevrilir ya da reddedilir. Akış taşıyan kaynaklar (yüklenen belge,
     * kapının döndürdüğü kaynaklar) ve diğer kaynak tipleri önceki resolver'a değişmeden gider.
     */
    private record GatedSourceResolver(ResourceGate gate, SourceResolver next) implements SourceResolver {
        @Override
        public ActiveSource resolveSource(Source source, Configuration config) throws XPathException {
            return next.resolveSource(throughGate(source), config);
        }

        private Source throughGate(Source source) throws XPathException {
            // Saxon, AugmentedSource içindeki kaynağı bu resolver'a uğramadan çözer; içi burada denetlenir.
            if (source instanceof AugmentedSource augmented) {
                Source contained = augmented.getContainedSource();
                Source gated = throughGate(contained);
                return gated == contained ? source : new AugmentedSource(gated, augmented.getParseOptions());
            }
            String uri = uriOnlySystemId(source);
            if (uri == null) {
                return source;
            }
            var request = new ResourceRequest();
            request.uri = uri;
            request.nature = ResourceRequest.XML_NATURE;
            request.purpose = ResourceRequest.ANY_PURPOSE;
            return gate.resolve(request);
        }
    }

    /**
     * Kaynak bayt ya da karakter akışı taşımıyorsa (okumak için system id'sindeki URI'yi açmak
     * gerekiyorsa) o system id'yi, aksi halde {@code null} döner.
     */
    static String uriOnlySystemId(Source source) {
        if (source instanceof StreamSource stream) {
            return stream.getInputStream() == null && stream.getReader() == null ? stream.getSystemId() : null;
        }
        if (source instanceof SAXSource sax) {
            InputSource input = sax.getInputSource();
            if (input == null) {
                return sax.getSystemId();
            }
            return input.getByteStream() == null && input.getCharacterStream() == null ? input.getSystemId() : null;
        }
        return null;
    }

    private static InputStream open(Path real, String target) throws XPathException {
        try {
            return Files.newInputStream(real);
        } catch (IOException e) {
            throw blocked(target);
        }
    }

    /**
     * {@link #UNAVAILABLE_FUNCTIONS} içindeki fonksiyonları XSLT, XPath ve derleme zamanı
     * ({@code use-when}, {@code static}) fonksiyon setlerinden, {@link #UNAVAILABLE_VENDOR_FUNCTIONS}
     * içindekileri Saxon fonksiyon setinden çıkaran Saxon HE yapılandırması.
     * Saxon'un fonksiyon çözümlemesi (çağrı, {@code function-available()}, {@code function-lookup()},
     * isimli fonksiyon referansı) bu setlerin {@code getFunctionDetails} metodundan geçer.
     * Saxon ad alanındaki fonksiyonlar her bağlamda (stylesheet, {@code use-when}, {@code xsl:evaluate},
     * {@code function-lookup()}) {@link #makeBuiltInExtensionLibraryList} listesinden bulunur.
     */
    private static final class SandboxConfiguration extends Configuration {

        /** Saxon'un HE fonksiyon seti, {@link #UNAVAILABLE_VENDOR_FUNCTIONS} hariç. */
        private static final BuiltInFunctionSet VENDOR_FUNCTIONS =
                new RestrictedFunctionSet(VendorFunctionSetHE.getInstance(), UNAVAILABLE_VENDOR_FUNCTIONS);

        private final Map<Integer, BuiltInFunctionSet> xpathFunctionSets = new ConcurrentHashMap<>();
        private final Map<Integer, BuiltInFunctionSet> xsltFunctionSets = new ConcurrentHashMap<>();
        private final Map<Integer, UseWhen30FunctionSet> useWhenFunctionSets = new ConcurrentHashMap<>();

        @Override
        public BuiltInFunctionSet getXPathFunctionSet(int version) {
            BuiltInFunctionSet base = super.getXPathFunctionSet(version);
            if (!containsAny(base, UNAVAILABLE_FUNCTIONS)) {
                return base;
            }
            return xpathFunctionSets.computeIfAbsent(version,
                    v -> new RestrictedFunctionSet(base, UNAVAILABLE_FUNCTIONS));
        }

        @Override
        public BuiltInFunctionSet getXSLTFunctionSet(int version) {
            BuiltInFunctionSet base = super.getXSLTFunctionSet(version);
            if (!containsAny(base, UNAVAILABLE_FUNCTIONS)) {
                return base;
            }
            if (base instanceof XSLT30FunctionSet) {
                return xsltFunctionSets.computeIfAbsent(version, v -> new RestrictedXslt30FunctionSet());
            }
            return xsltFunctionSets.computeIfAbsent(version,
                    v -> new RestrictedFunctionSet(base, UNAVAILABLE_FUNCTIONS));
        }

        @Override
        public synchronized UseWhen30FunctionSet getUseWhenFunctionLibrary(int version) {
            return useWhenFunctionSets.computeIfAbsent(version, RestrictedUseWhenFunctionSet::new);
        }

        /**
         * Saxon'un eklediği fonksiyon kütüphaneleri (Saxon, math, map, array, EXSLT); Saxon seti
         * kısıtlı kopyasıyla değiştirilir. {@link Configuration} sonucu sürüm başına önbelleğe alır.
         * Kısıtlı fonksiyonlardan biri listede hâlâ bulunuyorsa (ör. Saxon sürümü değişip set başka
         * bir sınıftan gelirse) yapılandırma açık kalmak yerine hata verir.
         */
        @Override
        protected FunctionLibraryList makeBuiltInExtensionLibraryList(int version) {
            var restricted = new FunctionLibraryList();
            for (FunctionLibrary library : super.makeBuiltInExtensionLibraryList(version).getLibraryList()) {
                restricted.addFunctionLibrary(library instanceof VendorFunctionSetHE ? VENDOR_FUNCTIONS : library);
            }
            for (String name : UNAVAILABLE_VENDOR_FUNCTIONS) {
                for (int arity = 0; arity <= MAX_CHECKED_ARITY; arity++) {
                    var function = new SymbolicName.F(new StructuredQName("saxon", NamespaceUri.SAXON, name), arity);
                    if (restricted.isAvailable(function, version)) {
                        throw new IllegalStateException(
                                "XSLT sandbox: saxon:" + name + "#" + arity + " fonksiyon listesinden çıkarılamadı");
                    }
                }
            }
            return restricted;
        }

        @Override
        public BuiltInFunctionSet getVendorFunctionSet() {
            return VENDOR_FUNCTIONS;
        }

        private static boolean containsAny(BuiltInFunctionSet set, Set<String> names) {
            if (set == null) {
                return false;
            }
            for (String name : names) {
                for (int arity = 0; arity <= MAX_CHECKED_ARITY; arity++) {
                    if (set.getFunctionDetails(name, arity) != null) {
                        return true;
                    }
                }
            }
            return false;
        }
    }

    /** Kısıtlı fonksiyonların var olup olmadığı denetlenirken bakılan en büyük arity. */
    private static final int MAX_CHECKED_ARITY = 3;

    /** Saxon'un XSLT 3.0 fonksiyon seti, {@link #UNAVAILABLE_FUNCTIONS} hariç. */
    private static final class RestrictedXslt30FunctionSet extends XSLT30FunctionSet {
        @Override
        public Entry getFunctionDetails(String name, int arity) {
            return UNAVAILABLE_FUNCTIONS.contains(name) ? null : super.getFunctionDetails(name, arity);
        }
    }

    /** Saxon'un derleme zamanı ({@code use-when}) fonksiyon seti, {@link #UNAVAILABLE_FUNCTIONS} hariç. */
    private static final class RestrictedUseWhenFunctionSet extends UseWhen30FunctionSet {
        RestrictedUseWhenFunctionSet(int version) {
            super(version);
        }

        @Override
        public Entry getFunctionDetails(String name, int arity) {
            return UNAVAILABLE_FUNCTIONS.contains(name) ? null : super.getFunctionDetails(name, arity);
        }
    }

    /**
     * Verilen yerleşik fonksiyon setinin kopyası, {@code hidden} içindeki adlar (her arity) hariç.
     * Saxon'un {@link BuiltInFunctionSet} alt sınıfları ({@link VendorFunctionSetHE} dahil) yalnızca
     * ad alanını ve öneki değiştirir; ikisi de temel setten alınır.
     */
    private static final class RestrictedFunctionSet extends BuiltInFunctionSet {
        private final BuiltInFunctionSet base;
        private final Set<String> hidden;

        RestrictedFunctionSet(BuiltInFunctionSet base, Set<String> hidden) {
            this.base = base;
            this.hidden = hidden;
            importFunctionSet(base);
        }

        @Override
        public Entry getFunctionDetails(String name, int arity) {
            return hidden.contains(name) ? null : super.getFunctionDetails(name, arity);
        }

        @Override
        public NamespaceUri getNamespace() {
            return base.getNamespace();
        }

        @Override
        public String getConventionalPrefix() {
            return base.getConventionalPrefix();
        }
    }

    /**
     * Resolver içinden Saxon'a ait hata tipiyle reddedilir; {@code unparsed-text-available()} gibi
     * fonksiyonlar bu sayede hata yerine {@code false} döner.
     * <p>
     * Mesaj {@code X-Xslt-Custom-Error} header'ına taşındığı için ISO-8859-1 dışı karakter
     * (ı, ş, ğ, uzun tire) içermez: Tomcat bu karakterleri içeren header'ı yanıta hiç yazmıyor.
     */
    private static XPathException blocked(String target) {
        return new XPathException("Güvenlik: Harici kaynak engellendi (XSLT sandbox): " + target, "FODC0002");
    }
}

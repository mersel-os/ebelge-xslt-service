package io.mersel.services.xslt.infrastructure;

import net.sf.saxon.Configuration;
import net.sf.saxon.functions.registry.BuiltInFunctionSet;
import net.sf.saxon.functions.registry.UseWhen30FunctionSet;
import net.sf.saxon.functions.registry.XSLT30FunctionSet;
import net.sf.saxon.lib.EnvironmentVariableResolver;
import net.sf.saxon.lib.Feature;
import net.sf.saxon.lib.ParseOptions;
import net.sf.saxon.lib.ResourceRequest;
import net.sf.saxon.lib.ResourceResolver;
import net.sf.saxon.om.NamespaceUri;
import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.SaxonApiUncheckedException;
import net.sf.saxon.s9api.Xslt30Transformer;
import net.sf.saxon.s9api.XsltCompiler;
import net.sf.saxon.s9api.XsltExecutable;
import net.sf.saxon.trans.XPathException;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

import javax.xml.XMLConstants;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.transform.Source;
import javax.xml.transform.sax.SAXSource;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
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
 * harici varlıkları çözer (XXE). Bu sınıf, kullanıcının yüklediği XSLT, belgeye gömülü XSLT ve
 * varsayılan şablon yollarının hepsinde bu kapıları kapatır:
 * <ul>
 *   <li>İzinli protokol listesi boştur ({@link Feature#ALLOWED_PROTOCOLS}); {@code doc()},
 *       {@code xsl:include}, {@code xsl:source-document}, {@code fn:transform} gibi okumalar hiçbir
 *       şemayla dışarı çıkamaz. Derleyici ve transformer düzeyindeki resource resolver da her isteği reddeder.</li>
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
 * </ul>
 * Bu ayarların kapsamı yalnızca {@link #newProcessor()} ile oluşturulan {@link Processor}'dür;
 * diğer Saxon kullanıcılarını (Schematron doğrulama vb.) etkilemez.
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
     * Sandbox ayarları uygulanmış yeni bir Saxon HE {@link Processor} oluşturur.
     */
    static Processor newProcessor() {
        var processor = new Processor(new SandboxConfiguration());
        processor.setConfigurationProperty(Feature.ALLOWED_PROTOCOLS, "");
        processor.setConfigurationProperty(Feature.ALLOW_EXTERNAL_FUNCTIONS, false);
        processor.setConfigurationProperty(Feature.ENVIRONMENT_VARIABLE_RESOLVER, NO_ENVIRONMENT);

        var config = processor.getUnderlyingConfiguration();
        config.setUnparsedTextURIResolver((uri, encoding, configuration) -> {
            throw blocked(String.valueOf(uri));
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
     * {@code xsl:import}/{@code xsl:include} çözümlemesi kapalı bir derleyici döner.
     */
    static XsltCompiler newCompiler(Processor processor) {
        var compiler = processor.newXsltCompiler();
        compiler.setResourceResolver(DENY_RESOURCE);
        return compiler;
    }

    /**
     * {@code doc()}/{@code document()} çözümlemesi ve {@code xsl:result-document} çıktısı kapalı
     * bir transformer döner.
     */
    static Xslt30Transformer load(XsltExecutable executable) {
        var transformer = executable.load30();
        transformer.setResourceResolver(DENY_RESOURCE);
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

    /**
     * {@link #UNAVAILABLE_FUNCTIONS} içindeki fonksiyonları XSLT, XPath ve derleme zamanı
     * ({@code use-when}, {@code static}) fonksiyon setlerinden çıkaran Saxon HE yapılandırması.
     * Saxon'un fonksiyon çözümlemesi (çağrı, {@code function-available()}, {@code function-lookup()},
     * isimli fonksiyon referansı) bu setlerin {@code getFunctionDetails} metodundan geçer.
     */
    private static final class SandboxConfiguration extends Configuration {

        private final Map<Integer, BuiltInFunctionSet> xpathFunctionSets = new ConcurrentHashMap<>();
        private final Map<Integer, BuiltInFunctionSet> xsltFunctionSets = new ConcurrentHashMap<>();
        private final Map<Integer, UseWhen30FunctionSet> useWhenFunctionSets = new ConcurrentHashMap<>();

        @Override
        public BuiltInFunctionSet getXPathFunctionSet(int version) {
            BuiltInFunctionSet base = super.getXPathFunctionSet(version);
            if (!containsUnavailableFunction(base)) {
                return base;
            }
            return xpathFunctionSets.computeIfAbsent(version, v -> new RestrictedFunctionSet(base));
        }

        @Override
        public BuiltInFunctionSet getXSLTFunctionSet(int version) {
            BuiltInFunctionSet base = super.getXSLTFunctionSet(version);
            if (!containsUnavailableFunction(base)) {
                return base;
            }
            if (base instanceof XSLT30FunctionSet) {
                return xsltFunctionSets.computeIfAbsent(version, v -> new RestrictedXslt30FunctionSet());
            }
            return xsltFunctionSets.computeIfAbsent(version, v -> new RestrictedFunctionSet(base));
        }

        @Override
        public synchronized UseWhen30FunctionSet getUseWhenFunctionLibrary(int version) {
            return useWhenFunctionSets.computeIfAbsent(version, RestrictedUseWhenFunctionSet::new);
        }

        private static boolean containsUnavailableFunction(BuiltInFunctionSet set) {
            if (set == null) {
                return false;
            }
            for (String name : UNAVAILABLE_FUNCTIONS) {
                for (int arity = 0; arity <= 3; arity++) {
                    if (set.getFunctionDetails(name, arity) != null) {
                        return true;
                    }
                }
            }
            return false;
        }
    }

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

    /** Verilen yerleşik fonksiyon setinin kopyası, {@link #UNAVAILABLE_FUNCTIONS} hariç. */
    private static final class RestrictedFunctionSet extends BuiltInFunctionSet {
        private final BuiltInFunctionSet base;

        RestrictedFunctionSet(BuiltInFunctionSet base) {
            this.base = base;
            importFunctionSet(base);
        }

        @Override
        public Entry getFunctionDetails(String name, int arity) {
            return UNAVAILABLE_FUNCTIONS.contains(name) ? null : super.getFunctionDetails(name, arity);
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

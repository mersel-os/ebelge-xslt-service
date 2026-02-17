package io.mersel.services.xslt.infrastructure;

import io.mersel.services.xslt.infrastructure.diagnostics.XsltMetrics;
import net.sf.saxon.s9api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import javax.xml.transform.ErrorListener;
import javax.xml.transform.TransformerException;
import javax.xml.transform.stream.StreamSource;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * ISO Schematron XML → XSLT runtime derleyici.
 * <p>
 * GİB'in ubl-tr-package içindeki Schematron XML kaynak dosyalarını
 * 3 adımlı ISO Schematron pipeline ile çalıştırılabilir XSLT'ye derler.
 * <p>
 * Pipeline adımları:
 * <ol>
 *   <li><b>Dispatcher</b> — Schematron XML → ara XSLT</li>
 *   <li><b>Abstract</b>   — Abstract şablonlar çözülür</li>
 *   <li><b>Message</b>    — Son XSLT üretilir (XSLT 2.0 + xmlns:xs)</li>
 * </ol>
 * <p>
 * Pipeline XSL dosyaları classpath'teki {@code schematron-pipeline/} dizininden yüklenir.
 * Default şablon XSLT 2.0 üretecek şekilde yapılandırılmıştır — bu sayede
 * {@code xs:date()}, {@code xs:integer()} gibi XPath 2.0 fonksiyonları
 * doğrudan desteklenir (post-processing gerekmez).
 */
@Component
public class SchematronRuntimeCompiler {

    private static final Logger log = LoggerFactory.getLogger(SchematronRuntimeCompiler.class);

    /**
     * Derleme sonucu — hem derlenmiş XSLT executable hem de pipeline çıktısı (raw XSLT bytes).
     */
    public record CompileResult(XsltExecutable executable, byte[] generatedXslt) {}


    private static final String PIPELINE_BASE = "schematron-pipeline";
    private static final String DISPATCHER_XSL = PIPELINE_BASE + "/schematronDispatcher.xsl";
    private static final String ABSTRACT_XSL = PIPELINE_BASE + "/iso-schematron-abstract.xsl";
    private static final String MESSAGE_XSL = PIPELINE_BASE + "/iso-schematron-message.xsl";

    private final Processor processor;
    private final XsltMetrics metrics;

    // Ön-derlenmiş pipeline XSLT'leri (startup'da bir kez derlenir)
    private XsltExecutable dispatcherExecutable;
    private XsltExecutable abstractExecutable;
    private XsltExecutable messageExecutable;

    public SchematronRuntimeCompiler(XsltMetrics metrics) {
        this.processor = new Processor(false);
        this.metrics = metrics;
    }

    @PostConstruct
    void init() throws Exception {
        log.info("ISO Schematron pipeline XSL'leri ön-derleniyor...");

        var compiler = processor.newXsltCompiler();

        // Pipeline dosyalarını classpath'ten derle — uygulama ömrü boyunca bir kez
        dispatcherExecutable = compilePipelineXsl(compiler, DISPATCHER_XSL);
        abstractExecutable = compilePipelineXsl(compiler, ABSTRACT_XSL);
        messageExecutable = compilePipelineXsl(compiler, MESSAGE_XSL);

        log.info("ISO Schematron pipeline hazır (3 adım)");
    }

    /**
     * Schematron XML kaynağını 3 adımlı pipeline ile XSLT'ye derler.
     *
     * @param schematronSource Schematron XML kaynak dosyasının InputStream'i
     * @return Derlenmiş Schematron XSLT (Saxon XsltExecutable)
     * @throws SaxonApiException Derleme hatası
     */
    public XsltExecutable compile(InputStream schematronSource) throws SaxonApiException {
        return compileWithOutput(new StreamSource(schematronSource)).executable();
    }

    /**
     * Disk üzerindeki Schematron dosyasını 3 adımlı pipeline ile XSLT'ye derler.
     * <p>
     * {@code sch:include} gibi göreceli referanslar dosyanın bulunduğu dizine göre
     * otomatik çözümlenir. Bu yöntem {@link #compile(InputStream)} yerine tercih
     * edilmelidir — özellikle include içeren Schematron'lar için (ör: ubl-tr-package).
     *
     * @param sourceFile Schematron XML dosyasının disk üzerindeki yolu
     * @return Derlenmiş Schematron XSLT (Saxon XsltExecutable)
     * @throws SaxonApiException Derleme hatası
     */
    public XsltExecutable compile(Path sourceFile) throws SaxonApiException {
        return compileWithOutput(new StreamSource(sourceFile.toFile())).executable();
    }

    /**
     * Disk üzerindeki Schematron dosyasını derler ve pipeline XSLT çıktısını da döndürür.
     * <p>
     * Çıktı, auto-generated dizinine yazılmak üzere kullanılabilir.
     *
     * @param sourceFile Schematron XML dosyasının disk üzerindeki yolu
     * @return CompileResult — executable + generated XSLT bytes
     * @throws SaxonApiException Derleme hatası
     */
    public CompileResult compileAndReturn(Path sourceFile) throws SaxonApiException {
        log.debug("Schematron derleme + çıktı (Path): {}", sourceFile);
        return compileWithOutput(new StreamSource(sourceFile.toFile()));
    }

    /**
     * Bellekteki (modifiye edilmiş) Schematron byte'larını derler ve pipeline XSLT çıktısını döndürür.
     * <p>
     * {@code baseUri} parametresi, {@code sch:include} gibi göreceli referansların
     * çözümlenmesi için kullanılır. Orijinal Schematron dosyasının URI'si verilmelidir.
     *
     * @param modifiedSource Modifiye edilmiş Schematron XML içeriği
     * @param baseUri        Göreceli referanslar için base URI (orijinal dosyanın URI'si)
     * @return CompileResult — executable + generated XSLT bytes
     * @throws SaxonApiException Derleme hatası
     */
    public CompileResult compileAndReturn(byte[] modifiedSource, URI baseUri) throws SaxonApiException {
        log.debug("Schematron derleme + çıktı (in-memory, baseUri={})", baseUri);
        var source = new StreamSource(new ByteArrayInputStream(modifiedSource));
        source.setSystemId(baseUri.toString());
        return compileWithOutput(source);
    }

    /**
     * StreamSource'dan Schematron derlemesi yapar ve pipeline XSLT çıktısını döndürür.
     */
    private CompileResult compileWithOutput(StreamSource source) throws SaxonApiException {
        long startTime = System.currentTimeMillis();

        // ── Adım 1/3: Dispatcher ────────────────────────────────────
        var dispatcherTransformer = dispatcherExecutable.load();

        var schDoc = processor.newDocumentBuilder().build(source);
        dispatcherTransformer.setInitialContextNode(schDoc);
        dispatcherTransformer.setParameter(new QName("", "phase"), new XdmAtomicValue("#ALL"));
        dispatcherTransformer.setParameter(new QName("", "generate-paths"), new XdmAtomicValue("true"));

        var step1Out = new ByteArrayOutputStream();
        dispatcherTransformer.setDestination(processor.newSerializer(step1Out));
        dispatcherTransformer.transform();

        // ── Adım 2/3: Abstract ──────────────────────────────────────
        var abstractTransformer = abstractExecutable.load();

        var abstractInput = processor.newDocumentBuilder().build(
                new StreamSource(new ByteArrayInputStream(step1Out.toByteArray())));
        abstractTransformer.setInitialContextNode(abstractInput);

        var step2Out = new ByteArrayOutputStream();
        abstractTransformer.setDestination(processor.newSerializer(step2Out));
        abstractTransformer.transform();

        // ── Adım 3/3: Message ───────────────────────────────────────
        var messageTransformer = messageExecutable.load();

        var finalDoc = processor.newDocumentBuilder().build(
                new StreamSource(new ByteArrayInputStream(step2Out.toByteArray())));
        messageTransformer.setInitialContextNode(finalDoc);
        // allow-foreign=true: xsl:function, xsl:key gibi gömülü XSLT elemanlarını çıktıya aktar
        // GİB e-Defter SCH dosyaları (edefter_kebir.sch vb.) custom xsl:function içerir
        messageTransformer.setParameter(new QName("", "allow-foreign"), new XdmAtomicValue("true"));

        var finalOut = new ByteArrayOutputStream();
        messageTransformer.setDestination(processor.newSerializer(finalOut));
        messageTransformer.transform();

        // ── Post-process: xsl:variable → xsl:param dönüşümü ─────────
        // ISO pipeline Schematron'daki global variable'ları xsl:variable olarak üretir.
        // Ancak Saxon'da dışarıdan parametre set edilebilmesi için bunların xsl:param olması gerekir.
        // Örn: UBL-TR Main Schematron'daki "type" değişkeni (efatura/earchive) runtime'da belirlenir.
        byte[] processedXslt = postProcessVariablesToParams(finalOut.toByteArray());

        // ── Sonucu XSLT olarak derle ────────────────────────────────
        // ISO pipeline artık doğrudan XSLT 2.0 + xmlns:xs üretiyor (iso_schematron_skeleton.xsl)
        var xsltCompiler = processor.newXsltCompiler();

        // Saxon hatalarını detaylı yakala
        List<String> compilationErrors = new ArrayList<>();
        xsltCompiler.setErrorReporter(error -> {
            String msg = error.getMessage();
            if (error.getLocation() != null && error.getLocation().getLineNumber() > 0) {
                msg = String.format("Satır %d: %s", error.getLocation().getLineNumber(), msg);
            }
            compilationErrors.add(msg);
            log.warn("Saxon XSLT derleme uyarısı: {}", msg);
        });

        XsltExecutable compiled;
        try {
            compiled = xsltCompiler.compile(
                    new StreamSource(new ByteArrayInputStream(processedXslt)));
        } catch (SaxonApiException e) {
            // Derleme hataları varsa detaylı mesaj oluştur
            if (!compilationErrors.isEmpty()) {
                String detail = String.join("; ", compilationErrors);
                throw new SaxonApiException(
                        "Schematron XSLT derleme hatası (" + compilationErrors.size() + " hata): " + detail, e);
            }
            throw e;
        }

        if (!compilationErrors.isEmpty()) {
            log.warn("Schematron XSLT derlemesi {} uyarı ile tamamlandı: {}",
                    compilationErrors.size(), String.join("; ", compilationErrors));
        }

        long elapsed = System.currentTimeMillis() - startTime;
        metrics.recordSchematronCompilation(elapsed);
        log.info("Schematron XML → XSLT derleme tamamlandı ({} ms)", elapsed);

        return new CompileResult(compiled, processedXslt);
    }

    // ── Post-Processing ─────────────────────────────────────────────

    /**
     * Dışarıdan set edilmesi gereken bilinen xsl:variable'ları xsl:param'a dönüştürür.
     * <p>
     * ISO Schematron pipeline, Schematron kaynağındaki global variable'ları
     * {@code <xsl:variable>} olarak üretir. Ancak Saxon'da dışarıdan
     * {@code setStylesheetParameters()} ile değer verilebilmesi için
     * bunların {@code <xsl:param>} olması gerekir.
     * <p>
     * Bilinen dönüştürülecek variable'lar:
     * <ul>
     *   <li>{@code type} — UBL-TR Main Schematron belge tipi (efatura/earchive)</li>
     * </ul>
     */
    private byte[] postProcessVariablesToParams(byte[] xsltBytes) {
        String xslt = new String(xsltBytes, java.nio.charset.StandardCharsets.UTF_8);

        // <xsl:variable name="type" select="..."/> → <xsl:param name="type" select="..."/>
        // Sadece top-level (stylesheet çocuğu) variable'lar etkilenir.
        // Regex: name="type" olan xsl:variable satırını xsl:param'a çevir.
        String original = xslt;
        xslt = xslt.replaceAll(
                "<xsl:variable(\\s+name\\s*=\\s*\"type\")",
                "<xsl:param$1"
        );

        if (!xslt.equals(original)) {
            log.debug("Post-process: <xsl:variable name=\"type\"> → <xsl:param name=\"type\"> dönüştürüldü");
        }

        return xslt.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    /**
     * Pipeline XSL dosyasını classpath'ten derler.
     */
    private XsltExecutable compilePipelineXsl(XsltCompiler compiler, String classpathLocation) throws Exception {
        var resource = new ClassPathResource(classpathLocation);
        if (!resource.exists()) {
            throw new IllegalStateException("Pipeline XSL dosyası bulunamadı: " + classpathLocation);
        }

        // Saxon'ın xsl:include/xsl:import çözümlemesi için systemId gerekli
        try (var is = resource.getInputStream()) {
            var source = new StreamSource(is);
            source.setSystemId(resource.getURL().toExternalForm());
            return compiler.compile(source);
        }
    }
}

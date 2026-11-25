package io.mersel.services.xslt.infrastructure;

import io.mersel.services.xslt.application.enums.DocumentType;
import io.mersel.services.xslt.application.enums.SchemaValidationType;
import io.mersel.services.xslt.application.enums.SchematronValidationType;
import io.mersel.services.xslt.application.interfaces.ICustomDocumentTypeRegistry;
import io.mersel.services.xslt.application.interfaces.Reloadable;
import io.mersel.services.xslt.application.interfaces.ReloadResult;
import io.mersel.services.xslt.application.models.CustomDocumentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Asset dizinindeki {@code custom-document-types.yml} dosyasından özel belge türlerini yükler.
 * <p>
 * Servisi çalıştıran taraf, servisin tanımadığı bir kök elementi kod değiştirmeden
 * doğrulatabilir: kök namespace + yerel ad, asset dizinindeki ana XSD ve opsiyonel olarak
 * yerleşik bir Schematron tipi tanımlanır. Dosya ve XSD'ler volume, ConfigMap, init container
 * veya {@code XSLT_ASSETS_SEED_PATH} ile başlangıçta; dosya bırakılarak ya da
 * {@code POST /v1/admin/assets/reload} ile çalışırken eklenebilir.
 * <p>
 * XSD derlemesi {@link JaxpSchemaValidator} tarafından yapılır; bu yüzden bu bileşen
 * reload sırasında ondan önce çalışır. Dosya okunamıyorsa ya da yapısı hatalıysa (kök eşleme değil,
 * {@code document-types} liste değil) önceki tanımlar korunur; tek tek hatalı girdiler raporlanıp atlanır.
 * Kökü yerleşik tespit kurallarınca zaten tanınan tanımlar hiç uygulanmayacağı için reddedilir.
 */
@Service
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CustomDocumentTypeRegistry implements ICustomDocumentTypeRegistry, Reloadable {

    private static final Logger log = LoggerFactory.getLogger(CustomDocumentTypeRegistry.class);

    static final String ASSET_PATH = "custom-document-types.yml";

    private static final Pattern ID_PATTERN = Pattern.compile("^[A-Z][A-Z0-9_]{0,63}$");

    private static final Set<String> ALLOWED_KEYS = Set.of(
            "id", "description", "root-namespace", "root-element", "xsd", "schematron");

    /** Yerleşik tip adları: yanıt, profil scope'u ve xsd-overrides anahtarlarında çakışmaması için. */
    private static final Set<String> RESERVED_IDS = Stream.of(
                    Arrays.stream(DocumentType.values()).map(Enum::name),
                    Arrays.stream(SchemaValidationType.values()).map(Enum::name),
                    Arrays.stream(SchematronValidationType.values()).map(Enum::name))
            .flatMap(s -> s)
            .collect(Collectors.toUnmodifiableSet());

    private final AssetManager assetManager;

    private volatile Map<String, CustomDocumentType> definitions = Map.of();

    public CustomDocumentTypeRegistry(AssetManager assetManager) {
        this.assetManager = assetManager;
    }

    // ── ICustomDocumentTypeRegistry ─────────────────────────────────

    @Override
    public Optional<CustomDocumentType> findByRoot(String namespace, String localName) {
        String ns = namespace != null ? namespace : "";
        return definitions.values().stream()
                .filter(d -> d.rootNamespace().equals(ns) && d.rootElement().equals(localName))
                .findFirst();
    }

    @Override
    public Optional<CustomDocumentType> findById(String id) {
        return Optional.ofNullable(definitions.get(id));
    }

    @Override
    public List<CustomDocumentType> getAll() {
        return List.copyOf(definitions.values());
    }

    // ── Reloadable ──────────────────────────────────────────────────

    @Override
    public String getName() {
        return "Custom Document Types";
    }

    @Override
    public ReloadResult reload() {
        long startTime = System.currentTimeMillis();

        if (!assetManager.assetExists(ASSET_PATH)) {
            definitions = Map.of();
            return ReloadResult.success(getName(), 0, System.currentTimeMillis() - startTime);
        }

        Object root;
        try (InputStream in = assetManager.getAssetStream(ASSET_PATH)) {
            root = new Yaml(new SafeConstructor(new LoaderOptions())).load(in);
        } catch (Exception e) {
            // Yarım kalmış bir düzenleme çalışan doğrulamayı bozmasın: önceki tanımlar korunur.
            return ReloadResult.failed(getName(), System.currentTimeMillis() - startTime,
                    ASSET_PATH + " okunamadı, önceki tanımlar korunuyor: " + e.getMessage());
        }

        String structureError = structureError(root);
        if (structureError != null) {
            // Sözdizimi hatası gibi: dosyanın tamamı yorumlanamıyor, çalışan doğrulama bozulmasın.
            return ReloadResult.failed(getName(), System.currentTimeMillis() - startTime,
                    ASSET_PATH + ": " + structureError + "; önceki tanımlar korunuyor");
        }

        var errors = new ArrayList<String>();
        var loaded = new LinkedHashMap<String, CustomDocumentType>();
        var roots = new HashSet<String>();

        List<?> entries = documentTypeEntries(root);
        for (int i = 0; i < entries.size(); i++) {
            String label = "document-types[" + i + "]";
            CustomDocumentType def = parseEntry(entries.get(i), label, errors);
            if (def == null) {
                continue;
            }
            if (loaded.containsKey(def.id())) {
                errors.add(label + ": '" + def.id() + "' adı birden fazla kez tanımlı");
                continue;
            }
            if (!roots.add(def.rootNamespace() + "|" + def.rootElement())) {
                errors.add(label + ": {" + def.rootNamespace() + "}" + def.rootElement()
                        + " kökü başka bir tanımda kullanılıyor");
                continue;
            }
            loaded.put(def.id(), def);
            log.info("  Özel belge türü: {} → {} (XSD: {}, Schematron: {})", def.id(),
                    "{" + def.rootNamespace() + "}" + def.rootElement(), def.xsdPath(),
                    def.schematronType() != null ? def.schematronType() : "yok");
        }

        definitions = Map.copyOf(loaded);
        long elapsed = System.currentTimeMillis() - startTime;

        if (errors.isEmpty()) {
            return ReloadResult.success(getName(), loaded.size(), elapsed);
        }
        if (!loaded.isEmpty()) {
            return ReloadResult.partial(getName(), loaded.size(), elapsed, errors);
        }
        return ReloadResult.failed(getName(), elapsed, String.join("; ", errors));
    }

    // ── Ayrıştırma ──────────────────────────────────────────────────

    /**
     * Dosya düzeyindeki yapı hatası; yoksa {@code null}. Boş dosya ve {@code document-types}
     * anahtarı olmayan eşleme hata değildir (tanım yok).
     */
    private static String structureError(Object root) {
        if (root == null) {
            return null;
        }
        if (!(root instanceof Map<?, ?> map)) {
            return "kök bir eşleme (map) olmalı";
        }
        Object list = map.get("document-types");
        if (list != null && !(list instanceof List<?>)) {
            return "'document-types' bir liste olmalı";
        }
        return null;
    }

    /** Yapısı {@link #structureError(Object)} ile doğrulanmış kökten girdiler. */
    private static List<?> documentTypeEntries(Object root) {
        if (root instanceof Map<?, ?> map && map.get("document-types") instanceof List<?> entries) {
            return entries;
        }
        return List.of();
    }

    private static CustomDocumentType parseEntry(Object entry, String label, List<String> errors) {
        if (!(entry instanceof Map<?, ?> map)) {
            errors.add(label + ": eşleme (map) olmalı");
            return null;
        }

        var unknownKeys = map.keySet().stream()
                .map(String::valueOf)
                .filter(k -> !ALLOWED_KEYS.contains(k))
                .sorted()
                .toList();
        if (!unknownKeys.isEmpty()) {
            errors.add(label + ": bilinmeyen alan(lar) " + unknownKeys + "; izin verilenler: "
                    + ALLOWED_KEYS.stream().sorted().toList());
            return null;
        }

        String id = text(map, "id");
        String description = text(map, "description");
        String rootNamespace = text(map, "root-namespace");
        String rootElement = text(map, "root-element");
        String xsd = text(map, "xsd");
        String schematron = text(map, "schematron");

        if (id == null || !ID_PATTERN.matcher(id).matches()) {
            errors.add(label + ": 'id' zorunlu ve " + ID_PATTERN.pattern() + " biçiminde olmalı");
            return null;
        }
        String named = label + " (" + id + ")";
        if (RESERVED_IDS.contains(id)) {
            errors.add(named + ": yerleşik bir tip adıyla çakışıyor");
            return null;
        }
        if (rootNamespace == null) {
            errors.add(named + ": 'root-namespace' zorunlu (namespace'siz kök için \"\" yazın)");
            return null;
        }
        if (rootElement == null || rootElement.isBlank() || rootElement.contains(":")) {
            errors.add(named + ": 'root-element' zorunlu ve önek (prefix) içermemeli");
            return null;
        }
        if (DocumentTypeDetector.isBuiltInRoot(rootNamespace, rootElement.strip())) {
            // Yerleşik tespit önce çalışır: bu kökteki belgeler hiçbir zaman bu tanıma ulaşmaz.
            errors.add(named + ": {" + rootNamespace + "}" + rootElement.strip()
                    + " kökü yerleşik belge türü tespitiyle işleniyor; bu tanım hiç uygulanmaz");
            return null;
        }
        String xsdError = validateXsdPath(xsd);
        if (xsdError != null) {
            errors.add(named + ": " + xsdError);
            return null;
        }

        SchematronValidationType schematronType = null;
        if (schematron != null && !schematron.isBlank()) {
            try {
                schematronType = SchematronValidationType.valueOf(schematron.strip());
            } catch (IllegalArgumentException e) {
                errors.add(named + ": bilinmeyen 'schematron' değeri '" + schematron + "'; geçerli değerler: "
                        + Arrays.toString(SchematronValidationType.values()));
                return null;
            }
        }

        return new CustomDocumentType(id, description, rootNamespace, rootElement.strip(),
                xsd.strip(), schematronType);
    }

    private static String validateXsdPath(String xsd) {
        if (xsd == null || xsd.isBlank()) {
            return "'xsd' zorunlu (asset dizinine göreli yol)";
        }
        String path = xsd.strip().replace('\\', '/');
        if (path.startsWith("/") || path.matches("^[A-Za-z]:.*") || path.contains("://")) {
            return "'xsd' asset dizinine göreli olmalı: " + xsd;
        }
        for (String segment : path.split("/")) {
            if (segment.equals("..")) {
                return "'xsd' asset dizininin dışına çıkamaz: " + xsd;
            }
        }
        if (path.startsWith("auto-generated/")) {
            return "'xsd' auto-generated dizininde olamaz: " + xsd;
        }
        return null;
    }

    private static String text(Map<?, ?> map, String key) {
        Object value = map.get(key);
        return value != null ? String.valueOf(value) : null;
    }
}

package io.mersel.services.xslt.infrastructure;

import io.mersel.services.xslt.application.interfaces.IValidationProfileService;
import io.mersel.services.xslt.application.interfaces.Reloadable;
import io.mersel.services.xslt.application.interfaces.ReloadResult;
import io.mersel.services.xslt.application.models.SchematronError;
import io.mersel.services.xslt.application.models.SuppressionResult;
import io.mersel.services.xslt.application.models.ValidationProfile;
import io.mersel.services.xslt.application.models.ValidationProfile.SuppressionRule;
import io.mersel.services.xslt.application.models.XsdOverride;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Doğrulama profilleri kayıt defteri ve bastırma motoru.
 * <p>
 * YAML konfigürasyonundan profilleri yükler, regex pattern'ları derler,
 * kalıtım ({@code extends}) çözümler ve {@link Reloadable} arayüzü ile
 * hot-reload destekler.
 * <p>
 * Bastırma kuralları opsiyonel {@code scope} desteği ile belirli belge
 * tiplerine kısıtlanabilir. Scope belirtilmezse kural tüm tiplere uygulanır.
 * <p>
 * Profil dosyası ({@code validation-profiles.yml}) external asset dizininden okunur.
 */
@Service
public class ValidationProfileRegistry implements IValidationProfileService, Reloadable {

    private static final Logger log = LoggerFactory.getLogger(ValidationProfileRegistry.class);
    private static final String PROFILES_ASSET_PATH = "validation-profiles.yml";

    private final AssetManager assetManager;

    /** Profil verileri — tek volatile reference ile atomik swap. */
    private record ProfileData(Map<String, ValidationProfile> profiles, Map<String, List<CompiledRule>> rules) {}
    private volatile ProfileData profileData = new ProfileData(Map.of(), Map.of());

    public ValidationProfileRegistry(AssetManager assetManager) {
        this.assetManager = assetManager;
    }

    // ── Reloadable ──────────────────────────────────────────────────

    @Override
    public String getName() {
        return "Validation Profiles";
    }

    @Override
    public ReloadResult reload() {
        long startTime = System.currentTimeMillis();

        try {
            Map<String, RawProfile> rawProfiles = loadYaml();

            if (rawProfiles.isEmpty()) {
                long elapsed = System.currentTimeMillis() - startTime;
                profileData = new ProfileData(Map.of(), Map.of());
                return ReloadResult.success(getName(), 0, elapsed);
            }

            // Kalıtımı çöz ve profilleri oluştur
            Map<String, ValidationProfile> resolved = new LinkedHashMap<>();
            Map<String, List<CompiledRule>> compiled = new LinkedHashMap<>();
            var errors = new ArrayList<String>();

            for (var entry : rawProfiles.entrySet()) {
                try {
                    var profile = resolveProfile(entry.getKey(), entry.getValue(), rawProfiles, new HashSet<>());
                    resolved.put(entry.getKey(), profile);
                    compiled.put(entry.getKey(), compileRules(profile.suppressions()));
                    int xsdOvrCount = profile.xsdOverrides() != null
                            ? profile.xsdOverrides().values().stream().mapToInt(List::size).sum() : 0;
                    log.debug("  Profil yüklendi: {} ({} bastırma kuralı, {} XSD override)",
                            entry.getKey(), profile.suppressions().size(), xsdOvrCount);
                } catch (Exception e) {
                    errors.add(entry.getKey() + ": " + e.getMessage());
                    log.warn("  Profil çözümleme hatası: {} - {}", entry.getKey(), e.getMessage());
                }
            }

            // Atomic swap — tek holder obje ile her iki map birden güncellenir
            profileData = new ProfileData(Map.copyOf(resolved), Map.copyOf(compiled));

            long elapsed = System.currentTimeMillis() - startTime;

            if (errors.isEmpty()) {
                return ReloadResult.success(getName(), resolved.size(), elapsed);
            } else {
                return ReloadResult.partial(getName(), resolved.size(), elapsed, errors);
            }

        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - startTime;
            log.error("Profil yükleme hatası: {}", e.getMessage());
            return ReloadResult.failed(getName(), elapsed, e.getMessage());
        }
    }

    // ── IValidationProfileService ───────────────────────────────────

    @Override
    public Optional<ValidationProfile> getProfile(String profileName) {
        if (profileName == null || profileName.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(profileData.profiles().get(profileName));
    }

    @Override
    public Map<String, ValidationProfile> getAvailableProfiles() {
        return profileData.profiles();
    }

    @Override
    public SuppressionResult applySchematronSuppressions(
            List<SchematronError> rawErrors,
            String profileName,
            List<String> additionalSuppressions,
            Set<String> activeTypes) {

        if (rawErrors == null || rawErrors.isEmpty()) {
            return new SuppressionResult(List.of(), List.of(), profileName, 0);
        }

        // Profil ve ad-hoc kuralları birleştir, scope'a göre filtrele
        List<CompiledRule> allRules = gatherRules(profileName, additionalSuppressions, activeTypes);

        if (allRules.isEmpty()) {
            return new SuppressionResult(List.copyOf(rawErrors), List.of(), profileName, 0);
        }

        var active = new ArrayList<SchematronError>();
        var suppressed = new ArrayList<SchematronError>();

        for (var error : rawErrors) {
            if (isSchematronErrorSuppressed(error, allRules)) {
                suppressed.add(error);
            } else {
                active.add(error);
            }
        }

        return new SuppressionResult(
                List.copyOf(active),
                List.copyOf(suppressed),
                profileName,
                suppressed.size()
        );
    }

    @Override
    public List<String> applyXsdSuppressions(
            List<String> rawErrors,
            String profileName,
            List<String> additionalSuppressions,
            Set<String> activeTypes) {

        if (rawErrors == null || rawErrors.isEmpty()) {
            return List.of();
        }

        // XSD hataları sadece text modunda bastırılabilir, scope'a göre filtrele
        List<CompiledRule> textRules = gatherRules(profileName, additionalSuppressions, activeTypes).stream()
                .filter(r -> "text".equals(r.matchMode))
                .toList();

        if (textRules.isEmpty()) {
            return rawErrors;
        }

        return rawErrors.stream()
                .filter(error -> textRules.stream().noneMatch(rule -> rule.pattern.matcher(error).matches()))
                .toList();
    }

    // ── XSD Overrides ────────────────────────────────────────────────

    @Override
    public List<XsdOverride> resolveXsdOverrides(String profileName, String schemaType) {
        if (profileName == null || profileName.isBlank() || schemaType == null || schemaType.isBlank()) {
            return List.of();
        }
        ValidationProfile profile = profileData.profiles().get(profileName);
        if (profile == null || profile.xsdOverrides() == null) {
            return List.of();
        }
        List<XsdOverride> overrides = profile.xsdOverrides().get(schemaType);
        return overrides != null ? overrides : List.of();
    }

    // ── Profile CRUD (YAML write-back) ────────────────────────────

    @Override
    public void saveProfile(ValidationProfile profile) throws IOException {
        Map<String, Object> root = loadRawYaml();
        @SuppressWarnings("unchecked")
        Map<String, Object> profiles = (Map<String, Object>) root.computeIfAbsent("profiles", k -> new LinkedHashMap<>());

        // Profili YAML map'e dönüştür
        var profileMap = new LinkedHashMap<String, Object>();
        if (profile.description() != null && !profile.description().isBlank()) {
            profileMap.put("description", profile.description());
        }
        if (profile.extendsProfile() != null && !profile.extendsProfile().isBlank()) {
            profileMap.put("extends", profile.extendsProfile());
        }
        if (profile.suppressions() != null && !profile.suppressions().isEmpty()) {
            var suppressionsList = new ArrayList<Map<String, Object>>();
            for (var rule : profile.suppressions()) {
                var ruleMap = new LinkedHashMap<String, Object>();
                ruleMap.put("match", rule.match() != null ? rule.match() : "ruleId");
                ruleMap.put("pattern", rule.pattern());
                if (rule.scope() != null && !rule.scope().isEmpty()) {
                    ruleMap.put("scope", new ArrayList<>(rule.scope()));
                }
                if (rule.description() != null && !rule.description().isBlank()) {
                    ruleMap.put("description", rule.description());
                }
                suppressionsList.add(ruleMap);
            }
            profileMap.put("suppressions", suppressionsList);
        }

        // XSD override'ları yaz
        if (profile.xsdOverrides() != null && !profile.xsdOverrides().isEmpty()) {
            var xsdOverridesMap = new LinkedHashMap<String, Object>();
            for (var entry : profile.xsdOverrides().entrySet()) {
                var overridesList = new ArrayList<Map<String, Object>>();
                for (var ovr : entry.getValue()) {
                    var ovrMap = new LinkedHashMap<String, Object>();
                    ovrMap.put("element", ovr.element());
                    if (ovr.minOccurs() != null) {
                        ovrMap.put("minOccurs", ovr.minOccurs());
                    }
                    if (ovr.maxOccurs() != null) {
                        ovrMap.put("maxOccurs", ovr.maxOccurs());
                    }
                    overridesList.add(ovrMap);
                }
                xsdOverridesMap.put(entry.getKey(), overridesList);
            }
            profileMap.put("xsd-overrides", xsdOverridesMap);
        }

        profiles.put(profile.name(), profileMap);
        writeYaml(root);
        reload();

        log.info("Profil kaydedildi: {} ({} bastırma kuralı)", profile.name(),
                profile.suppressions() != null ? profile.suppressions().size() : 0);
    }

    @Override
    public boolean deleteProfile(String profileName) throws IOException {
        Map<String, Object> root = loadRawYaml();
        @SuppressWarnings("unchecked")
        Map<String, Object> profiles = (Map<String, Object>) root.get("profiles");

        if (profiles == null || !profiles.containsKey(profileName)) {
            return false;
        }

        profiles.remove(profileName);
        writeYaml(root);
        reload();

        log.info("Profil silindi: {}", profileName);
        return true;
    }

    /**
     * Ham YAML dosyasını Map olarak okur (write-back işlemleri için).
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> loadRawYaml() throws IOException {
        if (!assetManager.assetExists(PROFILES_ASSET_PATH)) {
            // Dosya yoksa boş yapı oluştur
            var root = new LinkedHashMap<String, Object>();
            root.put("profiles", new LinkedHashMap<>());
            return root;
        }

        try (InputStream is = assetManager.getAssetStream(PROFILES_ASSET_PATH)) {
            Yaml yaml = new Yaml(new org.yaml.snakeyaml.constructor.SafeConstructor(new org.yaml.snakeyaml.LoaderOptions()));
            Map<String, Object> root = yaml.load(is);
            if (root == null) {
                root = new LinkedHashMap<>();
            }
            if (!root.containsKey("profiles")) {
                root.put("profiles", new LinkedHashMap<>());
            }
            return root;
        }
    }

    /**
     * YAML yapısını dosyaya yazar.
     */
    private void writeYaml(Map<String, Object> root) throws IOException {
        Path externalDir = assetManager.getExternalDir();
        if (externalDir == null) {
            throw new IOException("Asset dizini yapılandırılmamış. " +
                    "xslt.assets.external-path ayarlayın (env: XSLT_ASSETS_EXTERNAL_PATH).");
        }

        Path targetFile = externalDir.resolve(PROFILES_ASSET_PATH);
        Files.createDirectories(targetFile.getParent());

        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);
        options.setIndent(2);
        options.setIndicatorIndent(0);

        Yaml yaml = new Yaml(options);
        String yamlContent = yaml.dump(root);

        Files.writeString(targetFile, yamlContent, StandardCharsets.UTF_8);
        log.debug("Profil YAML dosyası yazıldı: {}", targetFile);
    }

    // ── YAML Loading ────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private Map<String, RawProfile> loadYaml() {
        Map<String, RawProfile> result = new LinkedHashMap<>();

        try (InputStream is = assetManager.getAssetStream(PROFILES_ASSET_PATH)) {
            Yaml yaml = new Yaml(new org.yaml.snakeyaml.constructor.SafeConstructor(new org.yaml.snakeyaml.LoaderOptions()));
            Map<String, Object> root = yaml.load(is);

            if (root == null || !root.containsKey("profiles")) {
                log.warn("validation-profiles.yml 'profiles' anahtarı bulunamadı");
                return result;
            }

            Map<String, Object> profiles = (Map<String, Object>) root.get("profiles");
            for (var entry : profiles.entrySet()) {
                Map<String, Object> profileMap = (Map<String, Object>) entry.getValue();
                result.put(entry.getKey(), parseRawProfile(entry.getKey(), profileMap));
            }
        } catch (Exception e) {
            log.warn("validation-profiles.yml yükleme hatası: {}", e.getMessage());
        }

        return result;
    }

    @SuppressWarnings("unchecked")
    private RawProfile parseRawProfile(String name, Map<String, Object> map) {
        String description = (String) map.getOrDefault("description", "");
        String extendsProfile = (String) map.get("extends");

        List<SuppressionRule> rules = new ArrayList<>();
        Object suppressionsObj = map.get("suppressions");
        if (suppressionsObj instanceof List<?> suppressionsList) {
            for (var item : suppressionsList) {
                if (item instanceof Map<?, ?> ruleMap) {
                    Map<String, Object> typedMap = (Map<String, Object>) ruleMap;

                    // Scope alanını parse et (opsiyonel, null veya List<String>)
                    List<String> scope = null;
                    Object scopeObj = typedMap.get("scope");
                    if (scopeObj instanceof List<?> scopeList) {
                        scope = scopeList.stream()
                                .filter(Objects::nonNull)
                                .map(Object::toString)
                                .map(String::strip)
                                .filter(s -> !s.isEmpty())
                                .toList();
                        if (scope.isEmpty()) {
                            scope = null;
                        }
                    } else if (scopeObj instanceof String scopeStr && !scopeStr.isBlank()) {
                        // Tek değer de kabul et: scope: INVOICE
                        scope = List.of(scopeStr.strip());
                    }

                    rules.add(new SuppressionRule(
                            String.valueOf(typedMap.getOrDefault("match", "ruleId")),
                            String.valueOf(typedMap.get("pattern")),
                            scope,
                            typedMap.get("description") != null ? String.valueOf(typedMap.get("description")) : null
                    ));
                }
            }
        }

        // xsd-overrides alanını parse et
        Map<String, List<XsdOverride>> xsdOverrides = new LinkedHashMap<>();
        Object xsdOverridesObj = map.get("xsd-overrides");
        if (xsdOverridesObj instanceof Map<?, ?> xsdOverridesMap) {
            for (var xsdEntry : xsdOverridesMap.entrySet()) {
                String schemaType = String.valueOf(xsdEntry.getKey()).strip();
                if (xsdEntry.getValue() instanceof List<?> overrideList) {
                    var overrides = new ArrayList<XsdOverride>();
                    for (var overrideItem : overrideList) {
                        if (overrideItem instanceof Map<?, ?> overrideMap) {
                            Map<String, Object> typedOverrideMap = (Map<String, Object>) overrideMap;
                            String element = typedOverrideMap.get("element") != null
                                    ? String.valueOf(typedOverrideMap.get("element")).strip() : null;
                            String minOccurs = typedOverrideMap.get("minOccurs") != null
                                    ? String.valueOf(typedOverrideMap.get("minOccurs")).strip() : null;
                            String maxOccurs = typedOverrideMap.get("maxOccurs") != null
                                    ? String.valueOf(typedOverrideMap.get("maxOccurs")).strip() : null;
                            if (element != null && !element.isEmpty()) {
                                overrides.add(new XsdOverride(element, minOccurs, maxOccurs));
                            }
                        }
                    }
                    if (!overrides.isEmpty()) {
                        xsdOverrides.put(schemaType, List.copyOf(overrides));
                    }
                }
            }
        }

        return new RawProfile(name, description, extendsProfile, rules, xsdOverrides);
    }

    // ── Profile Resolution (Inheritance) ────────────────────────────

    private ValidationProfile resolveProfile(String name, RawProfile raw,
                                              Map<String, RawProfile> allRaw,
                                              Set<String> visited) {
        if (visited.contains(name)) {
            throw new IllegalStateException("Döngüsel profil kalıtımı tespit edildi: " + name);
        }
        visited.add(name);

        List<SuppressionRule> allSuppressions = new ArrayList<>();
        // XSD overrides: üst profil + kendi overrides
        // Alt profildeki override'lar aynı element için üst profildekini ezer
        Map<String, Map<String, XsdOverride>> mergedOverrides = new LinkedHashMap<>();

        // Miras alınan profildeki kuralları ekle
        if (raw.extendsProfile != null && !raw.extendsProfile.isBlank()) {
            RawProfile parent = allRaw.get(raw.extendsProfile);
            if (parent == null) {
                throw new IllegalStateException(
                        "Profil '" + name + "' miras aldığı profil bulunamadı: " + raw.extendsProfile);
            }
            ValidationProfile parentProfile = resolveProfile(raw.extendsProfile, parent, allRaw, visited);
            allSuppressions.addAll(parentProfile.suppressions());

            // Üst profil xsd overrides
            for (var entry : parentProfile.xsdOverrides().entrySet()) {
                var elementMap = mergedOverrides.computeIfAbsent(entry.getKey(), k -> new LinkedHashMap<>());
                for (var ovr : entry.getValue()) {
                    elementMap.put(ovr.element(), ovr);
                }
            }
        }

        // Kendi kurallarını ekle
        allSuppressions.addAll(raw.suppressions);

        // Kendi xsd overrides — aynı element üst profildekini ezer
        for (var entry : raw.xsdOverrides.entrySet()) {
            var elementMap = mergedOverrides.computeIfAbsent(entry.getKey(), k -> new LinkedHashMap<>());
            for (var ovr : entry.getValue()) {
                elementMap.put(ovr.element(), ovr);
            }
        }

        // Map<String, Map<String, XsdOverride>> → Map<String, List<XsdOverride>>
        Map<String, List<XsdOverride>> finalOverrides = new LinkedHashMap<>();
        for (var entry : mergedOverrides.entrySet()) {
            finalOverrides.put(entry.getKey(), List.copyOf(entry.getValue().values()));
        }

        return new ValidationProfile(name, raw.description, raw.extendsProfile,
                List.copyOf(allSuppressions), Map.copyOf(finalOverrides));
    }

    // ── Rule Compilation ────────────────────────────────────────────

    /** Tam eşleşme (equals) modları — Pattern.quote() ile derlenir, regex gerekmez. */
    private static final Set<String> EQUALS_MODES = Set.of("testEquals", "ruleIdEquals");

    private List<CompiledRule> compileRules(List<SuppressionRule> rules) {
        return rules.stream()
                .filter(rule -> rule.pattern() != null && !rule.pattern().isBlank())
                .map(rule -> {
                    try {
                        String matchMode = rule.match() != null ? rule.match() : "ruleId";
                        // Equals modlarında pattern literal olarak derlenir (regex escape)
                        Pattern compiled = EQUALS_MODES.contains(matchMode)
                                ? Pattern.compile(Pattern.quote(rule.pattern()))
                                : Pattern.compile(rule.pattern());
                        return new CompiledRule(
                                matchMode,
                                compiled,
                                rule.scope() != null ? Set.copyOf(rule.scope()) : Set.of(),
                                rule.description()
                        );
                    } catch (java.util.regex.PatternSyntaxException e) {
                        log.warn("Geçersiz regex pattern atlanıyor: {} — {}", rule.pattern(), e.getMessage());
                        return null;
                    }
                })
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    // ── Suppression Logic ───────────────────────────────────────────

    /**
     * Profil ve ad-hoc bastırma kurallarını birleştirir ve scope'a göre filtreler.
     * <p>
     * Scope'suz kurallar her zaman dahil edilir. Scope'lu kurallar yalnızca
     * {@code activeTypes} kümesindeki tiplerden en az biriyle eşleşirse dahil edilir.
     */
    private List<CompiledRule> gatherRules(String profileName, List<String> additionalSuppressions, Set<String> activeTypes) {
        var rules = new ArrayList<CompiledRule>();

        // Profil kurallarını ekle (scope'a göre filtrele)
        if (profileName != null && !profileName.isBlank()) {
            List<CompiledRule> profileRules = profileData.rules().get(profileName);
            if (profileRules != null) {
                for (var rule : profileRules) {
                    if (isRuleInScope(rule, activeTypes)) {
                        rules.add(rule);
                    }
                }
            } else {
                log.warn("Profil bulunamadı: {}", profileName);
            }
        }

        // Ad-hoc bastırma kurallarını ekle (scope yok, tüm tiplere uygulanır).
        // Prefix desteği:
        //   "test:EXPRESSION"  → match: test,   exact-equal suppression (XPath test ifadesi)
        //   "text:PATTERN"     → match: text,   regex suppression (hata mesajı)
        //   "RULE_ID"          → match: ruleId, exact-equal suppression (varsayılan)
        if (additionalSuppressions != null) {
            for (String entry : additionalSuppressions) {
                if (entry == null || entry.isBlank()) continue;
                String stripped = entry.strip();

                String matchMode;
                String patternStr;

                if (stripped.startsWith("test:")) {
                    matchMode = "test";
                    patternStr = stripped.substring("test:".length()).strip();
                } else if (stripped.startsWith("text:")) {
                    matchMode = "text";
                    patternStr = stripped.substring("text:".length()).strip();
                } else {
                    matchMode = "ruleId";
                    patternStr = stripped;
                }

                if (!patternStr.isEmpty()) {
                    // test ve ruleId modlarında tam eşleşme (Pattern.quote),
                    // text modunda regex olarak derlenir
                    Pattern compiled = "text".equals(matchMode)
                            ? compilePatternSafe(patternStr)
                            : Pattern.compile(Pattern.quote(patternStr));
                    if (compiled != null) {
                        rules.add(new CompiledRule(matchMode, compiled, Set.of(), "Ad-hoc suppression"));
                    }
                }
            }
        }

        return rules;
    }

    /**
     * Regex pattern'ı güvenli şekilde derler. Geçersiz regex'te null döner.
     */
    private Pattern compilePatternSafe(String regex) {
        try {
            return Pattern.compile(regex);
        } catch (java.util.regex.PatternSyntaxException e) {
            log.warn("Geçersiz ad-hoc regex pattern atlanıyor: {} — {}", regex, e.getMessage());
            return null;
        }
    }

    /**
     * Kuralın mevcut doğrulama bağlamına uygun olup olmadığını kontrol eder.
     * <p>
     * Scope boşsa (global kural) her zaman true döner.
     * Scope doluysa, activeTypes ile kesişim olmalıdır.
     */
    private boolean isRuleInScope(CompiledRule rule, Set<String> activeTypes) {
        // Scope yok = global kural, her zaman uygulanır
        if (rule.scope.isEmpty()) {
            return true;
        }
        // activeTypes null/boş ise global kurallar dışında hiçbiri uygulanmaz
        if (activeTypes == null || activeTypes.isEmpty()) {
            return false;
        }
        // Scope ile activeTypes arasında kesişim var mı?
        return rule.scope.stream().anyMatch(activeTypes::contains);
    }

    /**
     * Schematron hatasının herhangi bir kuralla eşleşip eşleşmediğini kontrol eder.
     * <p>
     * Desteklenen match modları:
     * <ul>
     *   <li>{@code ruleId}       — rule/pattern ID regex eşleşmesi</li>
     *   <li>{@code ruleIdEquals}  — rule/pattern ID tam eşleşme</li>
     *   <li>{@code test}          — XPath test ifadesi regex eşleşmesi</li>
     *   <li>{@code testEquals}    — XPath test ifadesi tam eşleşme</li>
     *   <li>{@code text}          — Hata mesajı regex eşleşmesi</li>
     * </ul>
     */
    private boolean isSchematronErrorSuppressed(SchematronError error, List<CompiledRule> rules) {
        for (var rule : rules) {
            String target = switch (rule.matchMode) {
                case "ruleId", "ruleIdEquals" -> error.ruleId();
                case "test", "testEquals" -> error.test();
                case "text" -> error.message();
                default -> error.message();
            };

            if (target != null && rule.pattern.matcher(target).matches()) {
                return true;
            }
        }
        return false;
    }

    // ── Internal Records ────────────────────────────────────────────

    private record RawProfile(
            String name,
            String description,
            String extendsProfile,
            List<SuppressionRule> suppressions,
            Map<String, List<XsdOverride>> xsdOverrides
    ) {
    }

    private record CompiledRule(
            String matchMode,
            Pattern pattern,
            Set<String> scope,
            String description
    ) {
    }
}

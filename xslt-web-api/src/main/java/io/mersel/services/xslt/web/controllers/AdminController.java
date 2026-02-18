package io.mersel.services.xslt.web.controllers;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.mersel.services.xslt.application.enums.TransformType;
import io.mersel.services.xslt.application.interfaces.IAssetVersioningService;
import io.mersel.services.xslt.application.interfaces.IGibPackageSyncService;
import io.mersel.services.xslt.application.interfaces.IValidationProfileService;
import io.mersel.services.xslt.application.interfaces.ReloadResult;
import io.mersel.services.xslt.application.models.*;
import io.mersel.services.xslt.application.models.ValidationProfile.SuppressionRule;
import io.mersel.services.xslt.infrastructure.AssetManager;
import io.mersel.services.xslt.infrastructure.AssetRegistry;
import io.mersel.services.xslt.web.dto.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Asset yönetimi, doğrulama profilleri ve GİB paket sync API endpoint'leri.
 * <p>
 * XSD şemaları, Schematron kuralları, XSLT şablonları ve doğrulama profillerinin
 * çalışma zamanında yönetilmesini sağlar. GİB resmi paketlerinin otomatik
 * indirilip güncellenmesini destekler.
 */
@RestController
@RequestMapping("/v1/admin")
@Tag(name = "Admin", description = "Asset yönetimi, yeniden yükleme, profil yönetimi ve GİB paket sync")
public class AdminController {

    private static final Logger log = LoggerFactory.getLogger(AdminController.class);

    /** Profil adı kuralı: küçük harf, rakam, tire ve alt çizgi; 1-64 karakter */
    private static final Pattern PROFILE_NAME_PATTERN = Pattern.compile("^[a-z0-9_-]{1,64}$");

    /** Suppression pattern için maksimum uzunluk (ReDoS koruması) */
    private static final int MAX_SUPPRESSION_PATTERN_LENGTH = 500;

    /** TransformType → default_transformers dizinindeki dosya adı eşlemesi */
    private static final Map<TransformType, String> TRANSFORM_XSL_MAP = Map.of(
            TransformType.INVOICE, "default_transformers/eInvoice_Base.xslt",
            TransformType.ARCHIVE_INVOICE, "default_transformers/eArchive_Base.xslt",
            TransformType.DESPATCH_ADVICE, "default_transformers/eDespatch_Base.xslt",
            TransformType.RECEIPT_ADVICE, "default_transformers/eDespatch_Answer_Base.xslt",
            TransformType.EMM, "default_transformers/eMM_Base.xslt",
            TransformType.ESMM, "default_transformers/eSMM_Base.xslt"
    );

    /** TransformType için kullanıcı dostu etiketler */
    private static final Map<TransformType, String> TRANSFORM_TYPE_LABELS = Map.of(
            TransformType.INVOICE, "e-Fatura",
            TransformType.ARCHIVE_INVOICE, "e-Arşiv Fatura",
            TransformType.DESPATCH_ADVICE, "e-İrsaliye",
            TransformType.RECEIPT_ADVICE, "e-İrsaliye Yanıt",
            TransformType.EMM, "e-Müstahsil Makbuzu",
            TransformType.ESMM, "e-Serbest Meslek Makbuzu"
    );

    private final AssetRegistry assetRegistry;
    private final AssetManager assetManager;
    private final IValidationProfileService profileService;
    private final IGibPackageSyncService gibSyncService;
    private final IAssetVersioningService versioningService;
    private final io.mersel.services.xslt.infrastructure.GibAutoSyncStartupListener autoSyncListener;

    public AdminController(AssetRegistry assetRegistry,
                           AssetManager assetManager,
                           IValidationProfileService profileService,
                           IGibPackageSyncService gibSyncService,
                           IAssetVersioningService versioningService,
                           io.mersel.services.xslt.infrastructure.GibAutoSyncStartupListener autoSyncListener) {
        this.assetRegistry = assetRegistry;
        this.assetManager = assetManager;
        this.profileService = profileService;
        this.gibSyncService = gibSyncService;
        this.versioningService = versioningService;
        this.autoSyncListener = autoSyncListener;
    }

    /**
     * Tüm asset'leri (XSD, Schematron, XSLT, Profiller) yeniden yükler.
     * <p>
     * External dizindeki dosyalar değiştiğinde veya yeni dosyalar
     * eklendiğinde bu endpoint ile servis yeniden başlatılmadan
     * güncel asset'lere geçiş yapılabilir.
     */
    @PostMapping("/assets/reload")
    @Operation(
            summary = "Asset'leri yeniden yükle",
            description = "Tüm XSD şemalarını, Schematron kurallarını, XSLT şablonlarını ve doğrulama profillerini yeniden derler ve yükler. "
                    + "Yeniden yükleme sırasında mevcut istekler eski cache ile çalışmaya devam eder (zero-downtime)."
    )
    public ResponseEntity<ReloadAssetsResponse> reloadAssets() {
        List<ReloadResult> results = assetRegistry.reload();

        long totalDuration = results.stream().mapToLong(ReloadResult::durationMs).sum();
        var components = results.stream()
                .map(r -> new ReloadComponentDto(
                        r.componentName(), r.status().name(), r.loadedCount(), r.durationMs(), r.errors()))
                .toList();

        return ResponseEntity.ok(new ReloadAssetsResponse(
                Instant.now().toString(), totalDuration, components));
    }

    /**
     * Mevcut doğrulama profillerini listeler.
     * <p>
     * Her profilin adı, açıklaması, miras aldığı profil ve bastırma kurallarını döndürür.
     */
    @GetMapping("/profiles")
    @Operation(
            summary = "Doğrulama profillerini listele",
            description = "Tüm mevcut doğrulama profillerini listeler. Her profil, belirli Schematron/XSD hatalarının "
                    + "bastırılması için önceden tanımlı kurallar içerir. Profiller 'profile' parametresi ile doğrulama "
                    + "isteğinde kullanılabilir."
    )
    public ResponseEntity<ProfileListResponse> listProfiles() {
        Map<String, ValidationProfile> profiles = profileService.getAvailableProfiles();

        var profileList = new LinkedHashMap<String, ProfileDetailDto>();
        for (var entry : profiles.entrySet()) {
            var profile = entry.getValue();
            var suppressions = profile.suppressions().stream()
                    .map(s -> new SuppressionRuleDto(s.match(), s.pattern(),
                            (s.scope() != null && !s.scope().isEmpty()) ? s.scope() : null,
                            s.description()))
                    .toList();

            Map<String, List<XsdOverrideDto>> xsdOverrides = null;
            if (profile.xsdOverrides() != null && !profile.xsdOverrides().isEmpty()) {
                xsdOverrides = new LinkedHashMap<>();
                for (var ovrEntry : profile.xsdOverrides().entrySet()) {
                    xsdOverrides.put(ovrEntry.getKey(), ovrEntry.getValue().stream()
                            .map(ovr -> new XsdOverrideDto(ovr.element(), ovr.minOccurs(), ovr.maxOccurs()))
                            .toList());
                }
            }

            Map<String, List<SchematronRuleDto>> schematronRules = null;
            if (profile.schematronRules() != null && !profile.schematronRules().isEmpty()) {
                schematronRules = new LinkedHashMap<>();
                for (var schEntry : profile.schematronRules().entrySet()) {
                    schematronRules.put(schEntry.getKey(), schEntry.getValue().stream()
                            .map(r -> new SchematronRuleDto(r.context(), r.test(), r.message(), r.id()))
                            .toList());
                }
            }

            profileList.put(entry.getKey(), new ProfileDetailDto(
                    profile.description(), profile.extendsProfile(),
                    profile.suppressions().size(), suppressions, xsdOverrides, schematronRules));
        }

        return ResponseEntity.ok(new ProfileListResponse(profiles.size(), profileList));
    }

    /**
     * Profil oluşturur veya günceller.
     * <p>
     * Profil YAML dosyasına kalıcı olarak yazılır ve tüm profiller yeniden yüklenir.
     */
    @PutMapping("/profiles/{name}")
    @Operation(
            summary = "Profil kaydet (oluştur/güncelle)",
            description = "Belirtilen profili oluşturur veya günceller. "
                    + "Değişiklik YAML dosyasına kalıcı olarak yazılır ve profiller otomatik yeniden yüklenir."
    )
    public ResponseEntity<?> saveProfile(
            @PathVariable String name,
            @RequestBody ProfileRequest request) {

        if (!PROFILE_NAME_PATTERN.matcher(name).matches()) {
            return ResponseEntity.badRequest().body(new ErrorResponse(
                    "Geçersiz profil adı",
                    "Profil adı sadece küçük harf (a-z), rakam (0-9), tire (-) ve alt çizgi (_) içerebilir. Uzunluk: 1-64 karakter."));
        }

        try {
            if (request.suppressions() != null) {
                for (var s : request.suppressions()) {
                    if (s.pattern() != null && s.pattern().length() > MAX_SUPPRESSION_PATTERN_LENGTH) {
                        return ResponseEntity.badRequest().body(new ErrorResponse(
                                "Geçersiz bastırma kuralı",
                                "Pattern uzunluğu " + MAX_SUPPRESSION_PATTERN_LENGTH + " karakteri aşamaz."));
                    }
                }
            }

            List<SuppressionRule> suppressions = List.of();
            if (request.suppressions() != null) {
                suppressions = request.suppressions().stream()
                        .map(s -> new SuppressionRule(
                                s.match() != null ? s.match() : "ruleId",
                                s.pattern(),
                                s.scope(),
                                s.description()
                        ))
                        .toList();
            }

            Map<String, List<XsdOverride>> xsdOverrides = new LinkedHashMap<>();
            if (request.xsdOverrides() != null) {
                for (var entry : request.xsdOverrides().entrySet()) {
                    if (entry.getValue() != null && !entry.getValue().isEmpty()) {
                        xsdOverrides.put(entry.getKey(), entry.getValue().stream()
                                .map(ovr -> new XsdOverride(ovr.element(), ovr.minOccurs(), ovr.maxOccurs()))
                                .toList());
                    }
                }
            }

            // Schematron özel kurallarını dönüştür
            var schematronRules = new java.util.LinkedHashMap<String, List<io.mersel.services.xslt.application.models.SchematronCustomAssertion>>();
            if (request.schematronRules() != null) {
                for (var entry : request.schematronRules().entrySet()) {
                    if (entry.getValue() != null && !entry.getValue().isEmpty()) {
                        schematronRules.put(entry.getKey(), entry.getValue().stream()
                                .map(r -> new io.mersel.services.xslt.application.models.SchematronCustomAssertion(
                                        r.context(), r.test(), r.message(), r.id()))
                                .toList());
                    }
                }
            }

            var profile = new ValidationProfile(
                    name,
                    request.description(),
                    request.extendsProfile(),
                    suppressions,
                    xsdOverrides,
                    schematronRules
            );

            profileService.saveProfile(profile);
            assetRegistry.reload();

            int xsdOvrCount = xsdOverrides.values().stream().mapToInt(List::size).sum();
            return ResponseEntity.ok(new ProfileSaveResponse(
                    "Profil kaydedildi: " + name, name, suppressions.size(), xsdOvrCount));
        } catch (IOException e) {
            log.error("Profil kaydetme hatası: {} — {}", name, e.getMessage());
            return ResponseEntity.internalServerError().body(
                    new ErrorResponse("Profil kaydedilemedi", e.getMessage()));
        }
    }

    /**
     * Profili siler.
     * <p>
     * YAML dosyasından kalıcı olarak kaldırır ve profilleri yeniden yükler.
     */
    @DeleteMapping("/profiles/{name}")
    @Operation(
            summary = "Profil sil",
            description = "Belirtilen profili YAML dosyasından kalıcı olarak siler. "
                    + "Diğer profillerin bu profili miras alması durumunda dikkatli olunmalıdır."
    )
    public ResponseEntity<?> deleteProfile(@PathVariable String name) {
        if (!PROFILE_NAME_PATTERN.matcher(name).matches()) {
            return ResponseEntity.badRequest().body(new ErrorResponse(
                    "Geçersiz profil adı",
                    "Profil adı sadece küçük harf (a-z), rakam (0-9), tire (-) ve alt çizgi (_) içerebilir."));
        }

        try {
            boolean deleted = profileService.deleteProfile(name);

            if (deleted) {
                return ResponseEntity.ok(new ProfileDeleteResponse("Profil silindi: " + name, name));
            } else {
                return ResponseEntity.status(404).body(new ErrorResponse(
                        "Profil bulunamadı", "Silinecek profil bulunamadı: " + name));
            }
        } catch (IOException e) {
            log.error("Profil silme hatası: {} — {}", name, e.getMessage());
            return ResponseEntity.internalServerError().body(
                    new ErrorResponse("Profil silinemedi", e.getMessage()));
        }
    }

    /**
     * Profil oluşturma/güncelleme isteği DTO'su.
     */
    public record ProfileRequest(
            String description,
            String extendsProfile,
            List<SuppressionRuleRequest> suppressions,
            Map<String, List<XsdOverrideRequest>> xsdOverrides,
            Map<String, List<SchematronRuleRequest>> schematronRules
    ) {
    }

    /**
     * Bastırma kuralı isteği DTO'su.
     */
    public record SuppressionRuleRequest(
            String match,
            String pattern,
            List<String> scope,
            String description
    ) {
    }

    /**
     * XSD override isteği DTO'su.
     */
    public record XsdOverrideRequest(
            String element,
            String minOccurs,
            String maxOccurs
    ) {
    }

    /**
     * Özel Schematron kuralı isteği DTO'su.
     */
    public record SchematronRuleRequest(
            String context,
            String test,
            String message,
            String id
    ) {
    }

    // ── Global Schematron Kuralları ────────────────────────────────

    /**
     * Global özel Schematron kurallarını döndürür.
     * <p>
     * Bu kurallar profil bağımsızdır ve her doğrulama isteğinde otomatik olarak aktiftir.
     */
    @GetMapping("/schematron-rules")
    @Operation(
            summary = "Global Schematron kurallarını listele",
            description = "Profil bağımsız, her doğrulama isteğinde otomatik uygulanan global özel Schematron kurallarını döndürür. "
                    + "Bu kurallar YAML dosyasının top-level 'schematron-rules' bölümünde tanımlıdır."
    )
    public ResponseEntity<SchematronRulesResponse> getGlobalSchematronRules() {
        Map<String, List<SchematronCustomAssertion>> rules = profileService.getGlobalSchematronRules();

        var rulesDto = new LinkedHashMap<String, List<SchematronRuleDto>>();
        int totalCount = 0;
        for (var entry : rules.entrySet()) {
            rulesDto.put(entry.getKey(), entry.getValue().stream()
                    .map(r -> new SchematronRuleDto(r.context(), r.test(), r.message(), r.id()))
                    .toList());
            totalCount += entry.getValue().size();
        }

        return ResponseEntity.ok(new SchematronRulesResponse(rulesDto, totalCount));
    }

    /**
     * Global özel Schematron kurallarını kaydeder (tam değiştirme).
     * <p>
     * Mevcut tüm global kuralları değiştirir, YAML dosyasına yazar ve reload tetikler.
     */
    @PutMapping("/schematron-rules")
    @Operation(
            summary = "Global Schematron kurallarını kaydet",
            description = "Global özel Schematron kurallarını kaydeder. Mevcut tüm global kuralları değiştirir. "
                    + "Kayıt sonrası Schematron'lar yeni kurallarla otomatik olarak yeniden derlenir."
    )
    public ResponseEntity<?> saveGlobalSchematronRules(@RequestBody SaveSchematronRulesRequest request) {
        try {
            var rules = new LinkedHashMap<String, List<SchematronCustomAssertion>>();
            if (request.rules() != null) {
                for (var entry : request.rules().entrySet()) {
                    if (entry.getValue() != null && !entry.getValue().isEmpty()) {
                        rules.put(entry.getKey(), entry.getValue().stream()
                                .map(r -> new SchematronCustomAssertion(r.context(), r.test(), r.message(), r.id()))
                                .toList());
                    }
                }
            }

            profileService.saveGlobalSchematronRules(rules);
            assetRegistry.reload();

            int totalCount = rules.values().stream().mapToInt(List::size).sum();
            return ResponseEntity.ok(new SchematronRulesSaveResponse(
                    "Global Schematron kuralları kaydedildi", rules.size(), totalCount));
        } catch (IOException e) {
            log.error("Global Schematron kural kaydetme hatası: {}", e.getMessage());
            return ResponseEntity.internalServerError().body(
                    new ErrorResponse("Global Schematron kuralları kaydedilemedi", e.getMessage()));
        }
    }

    // ── Auto-Generated Dosyalar ────────────────────────────────────

    /**
     * Derleme/dönüşüm sonrası üretilen auto-generated dosyaları listeler.
     * <p>
     * Pipeline'dan geçen Schematron XSLT'leri ve override edilmiş XSD'leri içerir.
     */
    @GetMapping("/auto-generated")
    @Operation(
            summary = "Auto-generated dosyaları listele",
            description = "Pipeline'dan geçip dönüşüme uğrayan Schematron XSLT çıktılarını "
                    + "ve XSD override sonucu oluşan derlenmiş dosyaları listeler. "
                    + "Bu dosyalar xslt-assets/auto-generated/ dizininde saklanır."
    )
    public ResponseEntity<AutoGeneratedResponse> listAutoGenerated() {
        var schematronFiles = assetManager.listFiles("auto-generated/schematron");
        var schemaOverrideFiles = assetManager.listFiles("auto-generated/schema-overrides");
        var schematronRuleFiles = assetManager.listFiles("auto-generated/schematron-rules");

        return ResponseEntity.ok(new AutoGeneratedResponse(
                new FileGroupDto("auto-generated/schematron", schematronFiles, schematronFiles.size()),
                new FileGroupDto("auto-generated/schema-overrides", schemaOverrideFiles, schemaOverrideFiles.size()),
                new FileGroupDto("auto-generated/schematron-rules", schematronRuleFiles, schematronRuleFiles.size()),
                schematronFiles.size() + schemaOverrideFiles.size() + schematronRuleFiles.size()));
    }

    // ── Varsayılan XSLT Şablonları ─────────────────────────────────

    /**
     * Tüm belge tipleri için varsayılan XSLT şablonlarının durumunu listeler.
     */
    @GetMapping("/default-xslt")
    @Operation(
            summary = "Varsayılan XSLT şablonlarını listele",
            description = "Her belge tipi (e-Fatura, e-Arşiv, e-İrsaliye vb.) için tanımlı "
                    + "varsayılan XSLT şablonlarının mevcut durumunu, dosya boyutunu ve "
                    + "son değiştirilme tarihini döndürür."
    )
    public ResponseEntity<DefaultXsltListResponse> listDefaultXsltTemplates() {
        var templates = new ArrayList<DefaultXsltTemplateDto>();

        for (var entry : TRANSFORM_XSL_MAP.entrySet()) {
            var type = entry.getKey();
            var relativePath = entry.getValue();
            boolean exists = assetManager.assetExists(relativePath);
            long size = exists ? assetManager.getAssetSize(relativePath) : 0;
            long lastModified = exists ? assetManager.getAssetLastModified(relativePath) : 0;
            String fileName = relativePath.substring(relativePath.lastIndexOf('/') + 1);

            templates.add(new DefaultXsltTemplateDto(
                    type.name(),
                    TRANSFORM_TYPE_LABELS.getOrDefault(type, type.name()),
                    fileName,
                    relativePath,
                    exists,
                    size,
                    lastModified > 0 ? Instant.ofEpochMilli(lastModified).toString() : null
            ));
        }

        templates.sort(Comparator.comparing(DefaultXsltTemplateDto::transformType));
        return ResponseEntity.ok(new DefaultXsltListResponse(templates.size(), templates));
    }

    /**
     * Belirli bir belge tipi için varsayılan XSLT şablonunun içeriğini döndürür.
     */
    @GetMapping("/default-xslt/{transformType}")
    @Operation(
            summary = "Varsayılan XSLT şablon içeriğini getir",
            description = "Belirtilen belge tipi için varsayılan XSLT şablonunun metin içeriğini döndürür."
    )
    public ResponseEntity<?> getDefaultXsltContent(@PathVariable String transformType) {
        try {
            var type = TransformType.valueOf(transformType);
            var relativePath = TRANSFORM_XSL_MAP.get(type);
            if (relativePath == null) {
                return ResponseEntity.badRequest().body(
                        new ErrorResponse("Desteklenmeyen belge tipi", transformType));
            }

            if (!assetManager.assetExists(relativePath)) {
                return ResponseEntity.ok(new DefaultXsltContentResponse(
                        type.name(), relativePath, false, null, 0));
            }

            var content = new String(assetManager.readAssetBytes(relativePath), java.nio.charset.StandardCharsets.UTF_8);
            return ResponseEntity.ok(new DefaultXsltContentResponse(
                    type.name(), relativePath, true, content, content.length()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(
                    new ErrorResponse("Geçersiz belge tipi", transformType));
        } catch (IOException e) {
            log.error("XSLT şablon okuma hatası: {} — {}", transformType, e.getMessage());
            return ResponseEntity.internalServerError().body(
                    new ErrorResponse("XSLT şablon okunamadı", e.getMessage()));
        }
    }

    /**
     * Belirli bir belge tipi için varsayılan XSLT şablonunu günceller veya oluşturur.
     * <p>
     * İçerik multipart/form-data (dosya yükleme) veya düz metin olarak gönderilebilir.
     * Kayıt sonrası XSLT şablonları otomatik olarak yeniden derlenir.
     */
    @PutMapping(value = "/default-xslt/{transformType}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "Varsayılan XSLT şablonunu yükle/güncelle (dosya)",
            description = "Belirtilen belge tipi için varsayılan XSLT şablonunu dosya olarak yükler. "
                    + "Kayıt sonrası şablon otomatik olarak derlenir ve aktif hale gelir."
    )
    public ResponseEntity<?> uploadDefaultXslt(
            @PathVariable String transformType,
            @RequestParam("file") MultipartFile file) {
        try {
            var type = TransformType.valueOf(transformType);
            var relativePath = TRANSFORM_XSL_MAP.get(type);
            if (relativePath == null) {
                return ResponseEntity.badRequest().body(
                        new ErrorResponse("Desteklenmeyen belge tipi", transformType));
            }

            byte[] content = file.getBytes();
            if (content.length == 0) {
                return ResponseEntity.badRequest().body(
                        new ErrorResponse("Boş dosya", "XSLT dosyası boş olamaz"));
            }

            assetManager.writeAsset(relativePath, content);
            assetRegistry.reload();

            log.info("Varsayılan XSLT şablonu güncellendi: {} ({} bytes)", transformType, content.length);
            return ResponseEntity.ok(new DefaultXsltSaveResponse(
                    "XSLT şablonu kaydedildi", type.name(),
                    TRANSFORM_TYPE_LABELS.getOrDefault(type, type.name()),
                    content.length));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(
                    new ErrorResponse("Geçersiz belge tipi", transformType));
        } catch (IOException e) {
            log.error("XSLT şablon kaydetme hatası: {} — {}", transformType, e.getMessage());
            return ResponseEntity.internalServerError().body(
                    new ErrorResponse("XSLT şablon kaydedilemedi", e.getMessage()));
        }
    }

    /**
     * Belirli bir belge tipi için varsayılan XSLT şablonunu metin olarak günceller.
     */
    @PutMapping(value = "/default-xslt/{transformType}/content", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Varsayılan XSLT şablonunu güncelle (metin)",
            description = "Belirtilen belge tipi için varsayılan XSLT şablonunu düz metin içeriği ile günceller. "
                    + "Kayıt sonrası şablon otomatik olarak derlenir ve aktif hale gelir."
    )
    public ResponseEntity<?> updateDefaultXsltContent(
            @PathVariable String transformType,
            @RequestBody DefaultXsltContentRequest request) {
        try {
            var type = TransformType.valueOf(transformType);
            var relativePath = TRANSFORM_XSL_MAP.get(type);
            if (relativePath == null) {
                return ResponseEntity.badRequest().body(
                        new ErrorResponse("Desteklenmeyen belge tipi", transformType));
            }

            if (request.content() == null || request.content().isBlank()) {
                return ResponseEntity.badRequest().body(
                        new ErrorResponse("Boş içerik", "XSLT içeriği boş olamaz"));
            }

            byte[] bytes = request.content().getBytes(java.nio.charset.StandardCharsets.UTF_8);
            assetManager.writeAsset(relativePath, bytes);
            assetRegistry.reload();

            log.info("Varsayılan XSLT şablonu güncellendi (metin): {} ({} bytes)", transformType, bytes.length);
            return ResponseEntity.ok(new DefaultXsltSaveResponse(
                    "XSLT şablonu kaydedildi", type.name(),
                    TRANSFORM_TYPE_LABELS.getOrDefault(type, type.name()),
                    bytes.length));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(
                    new ErrorResponse("Geçersiz belge tipi", transformType));
        } catch (IOException e) {
            log.error("XSLT şablon kaydetme hatası: {} — {}", transformType, e.getMessage());
            return ResponseEntity.internalServerError().body(
                    new ErrorResponse("XSLT şablon kaydedilemedi", e.getMessage()));
        }
    }

    /**
     * Belirli bir belge tipi için varsayılan XSLT şablonunu siler.
     */
    @DeleteMapping("/default-xslt/{transformType}")
    @Operation(
            summary = "Varsayılan XSLT şablonunu sil",
            description = "Belirtilen belge tipi için varsayılan XSLT şablonunu siler. "
                    + "Silindikten sonra bu belge tipi için varsayılan dönüşüm yapılamaz."
    )
    public ResponseEntity<?> deleteDefaultXslt(@PathVariable String transformType) {
        try {
            var type = TransformType.valueOf(transformType);
            var relativePath = TRANSFORM_XSL_MAP.get(type);
            if (relativePath == null) {
                return ResponseEntity.badRequest().body(
                        new ErrorResponse("Desteklenmeyen belge tipi", transformType));
            }

            boolean deleted = assetManager.deleteAsset(relativePath);
            if (!deleted) {
                return ResponseEntity.notFound().build();
            }

            assetRegistry.reload();
            log.info("Varsayılan XSLT şablonu silindi: {}", transformType);
            return ResponseEntity.ok(new DefaultXsltDeleteResponse(
                    "XSLT şablonu silindi", type.name(),
                    TRANSFORM_TYPE_LABELS.getOrDefault(type, type.name())));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(
                    new ErrorResponse("Geçersiz belge tipi", transformType));
        } catch (IOException e) {
            log.error("XSLT şablon silme hatası: {} — {}", transformType, e.getMessage());
            return ResponseEntity.internalServerError().body(
                    new ErrorResponse("XSLT şablon silinemedi", e.getMessage()));
        }
    }

    // ── GİB Paket Sync ─────────────────────────────────────────────

    /**
     * GİB resmi paketlerini sync eder (indir, çıkart, asset dizinine yerleştir).
     * <p>
     * Opsiyonel {@code package} parametresi ile belirli bir paket sync edilebilir.
     * Parametre verilmezse tüm paketler sync edilir.
     * <p>
     * Sync devre dışıysa ({@code validation-assets.gib.sync.enabled=false}),
     * durumu açıklayan bir 200 OK yanıtı döner.
     */
    @PostMapping("/packages/sync")
    @Operation(
            summary = "GİB paketlerini sync et",
            description = "GİB resmi web sitesinden e-Fatura, UBL-TR XSD, e-Arşiv ve e-Defter "
                    + "paketlerini indirir, ZIP'ten çıkartır ve asset dizinine yerleştirir. "
                    + "İsteğe bağlı 'package' parametresi ile tek paket sync edilebilir. "
                    + "Sync sonrası asset'ler otomatik yeniden yüklenir."
    )
    public ResponseEntity<?> syncPackages(
            @RequestParam(value = "package", required = false) String packageId) {

        if (!gibSyncService.isEnabled()) {
            return ResponseEntity.ok(new SyncDisabledResponse(false,
                    "GIB paket sync ozelligi devre disi. Etkinlestirmek icin VALIDATION_ASSETS_GIB_SYNC_ENABLED=true ayarlayin.",
                    gibSyncService.getCurrentAssetSource()));
        }

        log.info("GİB paket sync isteği alındı — paket: {}", packageId != null ? packageId : "tümü");

        List<PackageSyncResult> results;
        if (packageId != null && !packageId.isBlank()) {
            results = List.of(gibSyncService.syncPackage(packageId));
        } else {
            results = gibSyncService.syncAll();
        }

        long successCount = results.stream().filter(PackageSyncResult::success).count();
        long totalDuration = results.stream().mapToLong(PackageSyncResult::durationMs).sum();

        var packages = results.stream()
                .map(r -> new SyncPackageResultDto(r.packageId(), r.displayName(), r.success(),
                        r.filesExtracted(), r.extractedFiles(), r.durationMs(), r.error()))
                .toList();

        return ResponseEntity.ok(new SyncResponse(true, Instant.now().toString(), totalDuration,
                successCount, results.size(), gibSyncService.getCurrentAssetSource(), packages));
    }

    /**
     * Mevcut GİB paket tanımlarını listeler.
     * <p>
     * Her paketin kimliği, adı, indirme URL'i ve dosya eşleştirme kurallarını döndürür.
     */
    @GetMapping("/packages")
    @Operation(
            summary = "GİB paket tanımlarını listele",
            description = "Sync edilebilecek GİB paketlerinin listesini döndürür. "
                    + "Her paket için kimlik, indirme URL'i ve dosya eşleştirme kuralları görüntülenir."
    )
    public ResponseEntity<PackageListResponse> listPackages() {
        var packages = gibSyncService.getAvailablePackages();

        var packageList = packages.stream()
                .map(pkg -> {
                    var fileMapping = pkg.fileMapping().stream()
                            .map(fm -> new FileMappingDto(fm.zipPathPattern(), fm.targetDir()))
                            .toList();

                    var fileTrees = new LinkedHashMap<String, Object>();
                    int totalFileCount = 0;
                    for (var fm : pkg.fileMapping()) {
                        var tree = assetManager.listFileTree(fm.targetDir());
                        if (!tree.isEmpty()) {
                            fileTrees.put(fm.targetDir(), tree);
                            totalFileCount += countTreeFiles(tree);
                        }
                    }

                    return new PackageDetailDto(pkg.id(), pkg.displayName(), pkg.downloadUrl(),
                            pkg.description(), fileMapping, fileTrees, totalFileCount);
                })
                .toList();

        return ResponseEntity.ok(new PackageListResponse(
                gibSyncService.isEnabled(), gibSyncService.getCurrentAssetSource(),
                packages.size(), autoSyncListener.isInitialSyncInProgress(), packageList));
    }

    /** Tree yapısındaki toplam dosya sayısını recursive sayar. */
    @SuppressWarnings("unchecked")
    private int countTreeFiles(List<Map<String, Object>> nodes) {
        int count = 0;
        for (var node : nodes) {
            if ("file".equals(node.get("type"))) {
                count++;
            } else if ("directory".equals(node.get("type"))) {
                var children = (List<Map<String, Object>>) node.get("children");
                if (children != null) count += countTreeFiles(children);
            }
        }
        return count;
    }

    // ── Asset Versioning ──────────────────────────────────────────────

    /**
     * GİB paketini staging alanına indirir ve diff önizlemesi döndürür.
     * <p>
     * Live asset'lere dokunmaz. Kullanıcı değişiklikleri inceledikten sonra
     * {@code POST /asset-versions/pending/{packageId}/approve} ile onaylayabilir.
     */
    @PostMapping("/packages/sync-preview")
    @Operation(
            summary = "GİB paketini staging'e indir (önizleme)",
            description = "GİB paketini staging alanına indirir, live ile karşılaştırır ve "
                    + "diff önizlemesi döndürür. Live asset'ler değişmez. Onay gerektirir."
    )
    public ResponseEntity<?> syncPreview(
            @RequestParam(value = "package", required = false) String packageId) {

        if (!gibSyncService.isEnabled()) {
            return ResponseEntity.ok(new SyncDisabledResponse(false,
                    "GIB paket sync ozelligi devre disi.",
                    gibSyncService.getCurrentAssetSource()));
        }

        try {
            List<SyncPreview> previews;
            if (packageId != null && !packageId.isBlank()) {
                previews = List.of(versioningService.syncToStaging(packageId));
            } else {
                previews = versioningService.syncAllToStaging();
            }

            var previewDtos = previews.stream().map(this::toSyncPreviewDto).toList();

            return ResponseEntity.ok(new SyncPreviewResponse(
                    true, Instant.now().toString(), previewDtos.size(), previewDtos));

        } catch (IOException e) {
            log.error("Sync preview başarısız: {}", e.getMessage());
            return ResponseEntity.internalServerError()
                    .body(new ErrorResponse("sync_preview_failed", "Sync preview başarısız: " + e.getMessage()));
        }
    }

    /**
     * Tüm versiyon geçmişini döndürür.
     */
    @GetMapping("/asset-versions")
    @Operation(
            summary = "Asset versiyon geçmişi",
            description = "GİB paket sync geçmişini listeler. Opsiyonel packageId filtresi."
    )
    public ResponseEntity<AssetVersionListResponse> listAssetVersions(
            @RequestParam(value = "packageId", required = false) String packageId) {

        List<AssetVersion> versions;
        if (packageId != null && !packageId.isBlank()) {
            versions = versioningService.listVersions(packageId);
        } else {
            versions = versioningService.listVersions();
        }

        var versionDtos = versions.stream().map(this::toAssetVersionDto).toList();
        return ResponseEntity.ok(new AssetVersionListResponse(versionDtos.size(), versionDtos));
    }

    /**
     * Tüm pending staging önizlemelerini döndürür.
     */
    @GetMapping("/asset-versions/pending")
    @Operation(
            summary = "Pending staging önizlemeleri",
            description = "Onay bekleyen GİB paket staging'lerini ve diff özetlerini döndürür."
    )
    public ResponseEntity<PendingPreviewsResponse> getPendingPreviews() {
        List<SyncPreview> previews = versioningService.getAllPendingPreviews();
        var previewDtos = previews.stream().map(this::toSyncPreviewDto).toList();
        return ResponseEntity.ok(new PendingPreviewsResponse(previewDtos.size(), previewDtos));
    }

    /**
     * Pending staging'deki tek bir dosyanın detaylı diff'ini döndürür.
     */
    @GetMapping("/asset-versions/pending/{packageId}/file-diff")
    @Operation(
            summary = "Pending dosya diff detayı",
            description = "Staging'deki tek bir dosyanın unified diff detayını döndürür."
    )
    public ResponseEntity<?> getPendingFileDiff(
            @PathVariable String packageId,
            @RequestParam("path") String filePath) {
        try {
            FileDiffDetail detail = versioningService.getPendingFileDiff(packageId, filePath);
            return ResponseEntity.ok(toFileDiffDetailDto(detail));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(404)
                    .body(new ErrorResponse("not_found", e.getMessage()));
        } catch (IOException e) {
            return ResponseEntity.internalServerError()
                    .body(new ErrorResponse("diff_failed", "Diff hesaplanamadı: " + e.getMessage()));
        }
    }

    /**
     * Pending staging'i onaylar ve live'a uygular.
     */
    @PostMapping("/asset-versions/pending/{packageId}/approve")
    @Operation(
            summary = "Pending staging'i onayla",
            description = "Staging'deki dosyaları live'a uygular, mevcut live dosyaları "
                    + "history'ye snapshot olarak kaydetir ve asset'leri yeniden yükler."
    )
    public ResponseEntity<?> approvePending(@PathVariable String packageId) {
        try {
            AssetVersion version = versioningService.approvePending(packageId);
            return ResponseEntity.ok(new ApproveResponse(
                    "Versiyon onaylandı ve live'a uygulandı",
                    toAssetVersionDto(version)));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(404)
                    .body(new ErrorResponse("not_found", e.getMessage()));
        } catch (IOException e) {
            return ResponseEntity.internalServerError()
                    .body(new ErrorResponse("approve_failed", "Onaylama başarısız: " + e.getMessage()));
        }
    }

    /**
     * Pending staging'i reddeder ve temizler.
     */
    @DeleteMapping("/asset-versions/pending/{packageId}")
    @Operation(
            summary = "Pending staging'i reddet",
            description = "Staging alanını temizler, live asset'ler değişmez."
    )
    public ResponseEntity<?> rejectPending(@PathVariable String packageId) {
        try {
            versioningService.rejectPending(packageId);
            return ResponseEntity.ok(new RejectResponse(
                    "Staging reddedildi ve temizlendi", packageId));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(404)
                    .body(new ErrorResponse("not_found", e.getMessage()));
        } catch (IOException e) {
            return ResponseEntity.internalServerError()
                    .body(new ErrorResponse("reject_failed", "Reddetme başarısız: " + e.getMessage()));
        }
    }

    /**
     * Geçmiş bir versiyonun dosya bazında diff özetini döndürür.
     */
    @GetMapping("/asset-versions/{versionId}/diff")
    @Operation(
            summary = "Geçmiş versiyon dosya diff özeti",
            description = "Geçmiş bir versiyon snapshot'ındaki dosya değişikliklerinin özetini döndürür."
    )
    public ResponseEntity<?> getVersionDiffSummary(@PathVariable String versionId) {
        try {
            List<FileDiffSummary> diffs = versioningService.getVersionDiff(versionId);
            var diffDtos = diffs.stream()
                    .map(d -> new FileDiffSummaryDto(d.path(), d.status().name(), d.oldSize(), d.newSize()))
                    .toList();
            return ResponseEntity.ok(new VersionDiffResponse(versionId, diffDtos.size(), diffDtos));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(404)
                    .body(new ErrorResponse("not_found", e.getMessage()));
        } catch (IOException e) {
            return ResponseEntity.internalServerError()
                    .body(new ErrorResponse("diff_failed", "Diff hesaplanamadı: " + e.getMessage()));
        }
    }

    /**
     * Geçmiş bir versiyondaki dosyanın diff detayını döndürür.
     */
    @GetMapping("/asset-versions/{versionId}/file-diff")
    @Operation(
            summary = "Geçmiş versiyon dosya diff detayı",
            description = "Geçmiş bir versiyon snapshot'ındaki dosyanın unified diff'ini döndürür."
    )
    public ResponseEntity<?> getVersionFileDiff(
            @PathVariable String versionId,
            @RequestParam("path") String filePath) {
        try {
            FileDiffDetail detail = versioningService.getFileDiff(versionId, filePath);
            return ResponseEntity.ok(toFileDiffDetailDto(detail));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(404)
                    .body(new ErrorResponse("not_found", e.getMessage()));
        } catch (IOException e) {
            return ResponseEntity.internalServerError()
                    .body(new ErrorResponse("diff_failed", "Diff hesaplanamadı: " + e.getMessage()));
        }
    }

    // ── Versioning DTO Mappers ──────────────────────────────────────

    private SyncPreviewDto toSyncPreviewDto(SyncPreview preview) {
        var fileDiffs = preview.fileDiffs().stream()
                .map(d -> new FileDiffSummaryDto(d.path(), d.status().name(), d.oldSize(), d.newSize()))
                .toList();
        var warnings = preview.warnings().stream()
                .map(w -> new SuppressionWarningDto(w.ruleId(), w.profileName(), w.pattern(),
                        w.severity().name(), w.message()))
                .toList();
        return new SyncPreviewDto(
                preview.packageId(),
                toAssetVersionDto(preview.version()),
                fileDiffs,
                warnings,
                preview.version().filesSummary().added(),
                preview.version().filesSummary().removed(),
                preview.version().filesSummary().modified(),
                preview.version().filesSummary().unchanged());
    }

    private AssetVersionDto toAssetVersionDto(AssetVersion v) {
        return new AssetVersionDto(
                v.id(), v.packageId(), v.displayName(),
                v.timestamp() != null ? v.timestamp().toString() : null,
                v.status().name(),
                v.filesSummary() != null ? new FilesSummaryDto(
                        v.filesSummary().added(), v.filesSummary().removed(),
                        v.filesSummary().modified(), v.filesSummary().unchanged()) : null,
                v.appliedAt() != null ? v.appliedAt().toString() : null,
                v.rejectedAt() != null ? v.rejectedAt().toString() : null,
                v.durationMs());
    }

    private FileDiffDetailDto toFileDiffDetailDto(FileDiffDetail d) {
        return new FileDiffDetailDto(d.path(), d.status().name(), d.unifiedDiff(),
                d.oldContent(), d.newContent(), d.isBinary());
    }

    // ── Response DTO'ları ────────────────────────────────────────────

    record ReloadComponentDto(String name, String status, int count, long durationMs, List<String> errors) {}
    record ReloadAssetsResponse(String reloadedAt, long durationMs, List<ReloadComponentDto> components) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record SuppressionRuleDto(String match, String pattern, List<String> scope, String description) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record XsdOverrideDto(String element, String minOccurs, String maxOccurs) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record SchematronRuleDto(String context, String test, String message, String id) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record ProfileDetailDto(String description, String extendsProfile, int suppressionCount,
                            List<SuppressionRuleDto> suppressions,
                            Map<String, List<XsdOverrideDto>> xsdOverrides,
                            Map<String, List<SchematronRuleDto>> schematronRules) {}

    record ProfileListResponse(int profileCount, Map<String, ProfileDetailDto> profiles) {}

    record ProfileSaveResponse(String message, String profile, int suppressionCount, int xsdOverrideCount) {}
    record ProfileDeleteResponse(String message, String profile) {}

    record SchematronRulesResponse(Map<String, List<SchematronRuleDto>> rules, int totalCount) {}
    record SaveSchematronRulesRequest(Map<String, List<SchematronRuleRequest>> rules) {}
    record SchematronRulesSaveResponse(String message, int typeCount, int totalRuleCount) {}

    record FileGroupDto(String directory, List<String> files, int count) {}
    record AutoGeneratedResponse(FileGroupDto schematron, FileGroupDto schemaOverrides, FileGroupDto schematronRules, int totalCount) {}

    record SyncDisabledResponse(boolean enabled, String message, String currentAssetSource) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record SyncPackageResultDto(String packageId, String displayName, boolean success,
                                int filesExtracted, List<String> extractedFiles, long durationMs,
                                String error) {}

    record SyncResponse(boolean enabled, String syncedAt, long totalDurationMs,
                        long successCount, int totalCount, String currentAssetSource,
                        List<SyncPackageResultDto> packages) {}

    record FileMappingDto(String zipPathPattern, String targetDir) {}
    record PackageDetailDto(String id, String displayName, String downloadUrl, String description,
                            List<FileMappingDto> fileMapping, Map<String, Object> fileTrees,
                            int totalLoadedFileCount) {}
    record PackageListResponse(boolean enabled, String currentAssetSource, int packageCount,
                               boolean initialSyncInProgress, List<PackageDetailDto> packages) {}

    // ── Asset Versioning DTO'ları ───────────────────────────────────

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record AssetVersionDto(String id, String packageId, String displayName,
                           String timestamp, String status, FilesSummaryDto filesSummary,
                           String appliedAt, String rejectedAt, long durationMs) {}

    record FilesSummaryDto(int added, int removed, int modified, int unchanged) {}

    record FileDiffSummaryDto(String path, String status, long oldSize, long newSize) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record FileDiffDetailDto(String path, String status, String unifiedDiff,
                             String oldContent, String newContent, boolean isBinary) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record SuppressionWarningDto(String ruleId, String profileName, String pattern,
                                 String severity, String message) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record SyncPreviewDto(String packageId, AssetVersionDto version,
                          List<FileDiffSummaryDto> fileDiffs, List<SuppressionWarningDto> warnings,
                          int addedCount, int removedCount, int modifiedCount, int unchangedCount) {}

    record SyncPreviewResponse(boolean enabled, String syncedAt, int packageCount,
                               List<SyncPreviewDto> previews) {}

    record AssetVersionListResponse(int versionCount, List<AssetVersionDto> versions) {}
    record PendingPreviewsResponse(int pendingCount, List<SyncPreviewDto> previews) {}
    record ApproveResponse(String message, AssetVersionDto version) {}
    record RejectResponse(String message, String packageId) {}
    record VersionDiffResponse(String versionId, int fileCount, List<FileDiffSummaryDto> files) {}

    // ── Default XSLT DTO'ları ────────────────────────────────────────

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record DefaultXsltTemplateDto(String transformType, String label, String fileName,
                                   String relativePath, boolean exists, long size,
                                   String lastModified) {}

    record DefaultXsltListResponse(int count, List<DefaultXsltTemplateDto> templates) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record DefaultXsltContentResponse(String transformType, String path, boolean exists,
                                       String content, long size) {}

    record DefaultXsltContentRequest(String content) {}

    record DefaultXsltSaveResponse(String message, String transformType, String label, long size) {}
    record DefaultXsltDeleteResponse(String message, String transformType, String label) {}
}

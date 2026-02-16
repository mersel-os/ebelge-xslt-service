package io.mersel.services.xslt.web.controllers;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.mersel.services.xslt.application.interfaces.IGibPackageSyncService;
import io.mersel.services.xslt.application.interfaces.IValidationProfileService;
import io.mersel.services.xslt.application.interfaces.ReloadResult;
import io.mersel.services.xslt.application.models.PackageSyncResult;
import io.mersel.services.xslt.application.models.ValidationProfile;
import io.mersel.services.xslt.application.models.ValidationProfile.SuppressionRule;
import io.mersel.services.xslt.application.models.XsdOverride;
import io.mersel.services.xslt.infrastructure.AssetManager;
import io.mersel.services.xslt.infrastructure.AssetRegistry;
import io.mersel.services.xslt.web.dto.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

    private final AssetRegistry assetRegistry;
    private final AssetManager assetManager;
    private final IValidationProfileService profileService;
    private final IGibPackageSyncService gibSyncService;

    public AdminController(AssetRegistry assetRegistry,
                           AssetManager assetManager,
                           IValidationProfileService profileService,
                           IGibPackageSyncService gibSyncService) {
        this.assetRegistry = assetRegistry;
        this.assetManager = assetManager;
        this.profileService = profileService;
        this.gibSyncService = gibSyncService;
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

            profileList.put(entry.getKey(), new ProfileDetailDto(
                    profile.description(), profile.extendsProfile(),
                    profile.suppressions().size(), suppressions, xsdOverrides));
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

            var profile = new ValidationProfile(
                    name,
                    request.description(),
                    request.extendsProfile(),
                    suppressions,
                    xsdOverrides
            );

            profileService.saveProfile(profile);

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
            Map<String, List<XsdOverrideRequest>> xsdOverrides
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

        return ResponseEntity.ok(new AutoGeneratedResponse(
                new FileGroupDto("auto-generated/schematron", schematronFiles, schematronFiles.size()),
                new FileGroupDto("auto-generated/schema-overrides", schemaOverrideFiles, schemaOverrideFiles.size()),
                schematronFiles.size() + schemaOverrideFiles.size()));
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
                packages.size(), packageList));
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

    // ── Response DTO'ları ────────────────────────────────────────────

    record ReloadComponentDto(String name, String status, int count, long durationMs, List<String> errors) {}
    record ReloadAssetsResponse(String reloadedAt, long durationMs, List<ReloadComponentDto> components) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record SuppressionRuleDto(String match, String pattern, List<String> scope, String description) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record XsdOverrideDto(String element, String minOccurs, String maxOccurs) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record ProfileDetailDto(String description, String extendsProfile, int suppressionCount,
                            List<SuppressionRuleDto> suppressions,
                            Map<String, List<XsdOverrideDto>> xsdOverrides) {}

    record ProfileListResponse(int profileCount, Map<String, ProfileDetailDto> profiles) {}

    record ProfileSaveResponse(String message, String profile, int suppressionCount, int xsdOverrideCount) {}
    record ProfileDeleteResponse(String message, String profile) {}

    record FileGroupDto(String directory, List<String> files, int count) {}
    record AutoGeneratedResponse(FileGroupDto schematron, FileGroupDto schemaOverrides, int totalCount) {}

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
                               List<PackageDetailDto> packages) {}
}

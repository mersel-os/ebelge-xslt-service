package io.mersel.services.xslt.infrastructure;

import io.mersel.services.xslt.application.interfaces.IValidationProfileService;
import io.mersel.services.xslt.application.models.FileDiffSummary;
import io.mersel.services.xslt.application.models.FileDiffSummary.FileChangeStatus;
import io.mersel.services.xslt.application.models.SuppressionWarning;
import io.mersel.services.xslt.application.models.ValidationProfile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.*;
import java.util.regex.Pattern;

/**
 * GİB Schematron dosyalarındaki rule ID değişikliklerinin
 * mevcut suppression kurallarına etkisini analiz eder.
 * <p>
 * Kaldırılan veya yeniden adlandırılan rule ID'leri tespit eder ve
 * bunlara bağlı suppression'lar için uyarı üretir.
 */
@Component
public class SuppressionImpactAnalyzer {

    private static final Logger log = LoggerFactory.getLogger(SuppressionImpactAnalyzer.class);

    private static final Set<String> SCHEMATRON_EXTENSIONS = Set.of(".xml", ".sch");

    private final AssetDiffService diffService;
    private final IValidationProfileService profileService;

    public SuppressionImpactAnalyzer(AssetDiffService diffService, IValidationProfileService profileService) {
        this.diffService = diffService;
        this.profileService = profileService;
    }

    /**
     * İki dizin arasındaki Schematron değişikliklerinin
     * mevcut suppression'lara etkisini analiz eder.
     *
     * @param liveDir    Mevcut live asset dizini
     * @param stagingDir Staging'deki yeni dosyalar dizini
     * @param fileDiffs  Dosya bazında değişiklik listesi
     * @return Suppression uyarıları listesi
     */
    public List<SuppressionWarning> analyzeImpact(Path liveDir, Path stagingDir,
                                                   List<FileDiffSummary> fileDiffs) {
        var warnings = new ArrayList<SuppressionWarning>();

        // Sadece değişen veya silinen Schematron dosyalarını incele
        List<FileDiffSummary> schematronChanges = fileDiffs.stream()
                .filter(d -> d.status() == FileChangeStatus.MODIFIED || d.status() == FileChangeStatus.REMOVED)
                .filter(d -> isSchematronFile(d.path()))
                .toList();

        if (schematronChanges.isEmpty()) {
            return warnings;
        }

        // Tüm profilleri yükle (suppression kurallarını almak için)
        List<ValidationProfile> profiles = loadAllProfiles();
        if (profiles.isEmpty()) {
            return warnings;
        }

        // ruleId match modundaki suppression pattern'lerini topla
        var ruleIdSuppressions = new ArrayList<RuleIdSuppression>();
        for (var profile : profiles) {
            if (profile.suppressions() == null) continue;
            for (var rule : profile.suppressions()) {
                if ("ruleId".equals(rule.match()) || "ruleIdEquals".equals(rule.match())) {
                    ruleIdSuppressions.add(new RuleIdSuppression(
                            profile.name(), rule.pattern(), rule.description()));
                }
            }
        }

        // Global suppression'ları da dahil et
        var globalSuppressions = loadGlobalSuppressions();
        ruleIdSuppressions.addAll(globalSuppressions);

        if (ruleIdSuppressions.isEmpty()) {
            return warnings;
        }

        // Her değişen Schematron dosyası için rule ID diff hesapla
        for (var change : schematronChanges) {
            Path oldFile = liveDir != null ? liveDir.resolve(change.path()) : null;
            Path newFile = stagingDir != null ? stagingDir.resolve(change.path()) : null;

            AssetDiffService.RuleIdDiff ruleIdDiff = diffService.computeRuleIdDiff(oldFile, newFile);
            if (!ruleIdDiff.hasChanges()) continue;

            // Kaldırılan ID'leri suppression'larla karşılaştır
            for (String removedId : ruleIdDiff.removed()) {
                for (var suppression : ruleIdSuppressions) {
                    if (matchesPattern(suppression.pattern(), removedId)) {
                        // Olası yeni ID var mı kontrol et (benzer isim arama)
                        String possibleNewId = findSimilarId(removedId, ruleIdDiff.added());
                        if (possibleNewId != null) {
                            warnings.add(SuppressionWarning.possiblyRenamed(
                                    removedId, suppression.profileName(),
                                    suppression.pattern(), possibleNewId));
                        } else {
                            warnings.add(SuppressionWarning.removed(
                                    removedId, suppression.profileName(), suppression.pattern()));
                        }
                    }
                }
            }
        }

        return warnings;
    }

    // ── Internal ────────────────────────────────────────────────────

    private boolean isSchematronFile(String path) {
        String lower = path.toLowerCase();
        return SCHEMATRON_EXTENSIONS.stream().anyMatch(lower::endsWith);
    }

    private boolean matchesPattern(String pattern, String ruleId) {
        try {
            return Pattern.compile(pattern).matcher(ruleId).matches();
        } catch (Exception e) {
            return pattern.equals(ruleId);
        }
    }

    /**
     * Kaldırılan ID'ye benzer yeni bir ID bulmaya çalışır (olası yeniden adlandırma).
     * Basit Levenshtein benzerliği kullanır.
     */
    private String findSimilarId(String removedId, Set<String> addedIds) {
        if (addedIds.isEmpty()) return null;

        String bestMatch = null;
        int bestDistance = Integer.MAX_VALUE;
        int threshold = Math.max(3, removedId.length() / 3);

        for (String addedId : addedIds) {
            int distance = levenshteinDistance(removedId.toLowerCase(), addedId.toLowerCase());
            if (distance < bestDistance && distance <= threshold) {
                bestDistance = distance;
                bestMatch = addedId;
            }
        }

        return bestMatch;
    }

    private int levenshteinDistance(String a, String b) {
        int[][] dp = new int[a.length() + 1][b.length() + 1];
        for (int i = 0; i <= a.length(); i++) dp[i][0] = i;
        for (int j = 0; j <= b.length(); j++) dp[0][j] = j;

        for (int i = 1; i <= a.length(); i++) {
            for (int j = 1; j <= b.length(); j++) {
                int cost = (a.charAt(i - 1) == b.charAt(j - 1)) ? 0 : 1;
                dp[i][j] = Math.min(Math.min(
                        dp[i - 1][j] + 1,
                        dp[i][j - 1] + 1),
                        dp[i - 1][j - 1] + cost);
            }
        }
        return dp[a.length()][b.length()];
    }

    private List<ValidationProfile> loadAllProfiles() {
        try {
            return new ArrayList<>(profileService.getAvailableProfiles().values());
        } catch (Exception e) {
            log.warn("Profiller yüklenemedi: {}", e.getMessage());
            return List.of();
        }
    }

    private List<RuleIdSuppression> loadGlobalSuppressions() {
        // Global Schematron kuralları profil bazlı değil ama suppression'lar
        // profil-bazlı olduğu için burada sadece profillerdeki kuralları döneriz
        return List.of();
    }

    private record RuleIdSuppression(String profileName, String pattern, String description) {
    }
}

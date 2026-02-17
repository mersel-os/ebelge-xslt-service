package io.mersel.services.xslt.application.models;

/**
 * Sync sonucunda etkilenecek suppression kuralı için uyarı.
 * <p>
 * Bir Schematron dosyasındaki rule ID değiştiğinde veya kaldırıldığında,
 * bu ID'ye bağlı suppression kuralları geçersiz hale gelir. Bu uyarı
 * admin kullanıcıya hangi suppressionların etkileneceğini bildirir.
 *
 * @param ruleId       Etkilenen rule ID (eski Schematron'daki ID)
 * @param profileName  Bu suppression'ın tanımlı olduğu profil adı ("global" ise global kural)
 * @param pattern      Suppression pattern'i (regex)
 * @param severity     Uyarı seviyesi
 * @param message      Kullanıcıya gösterilecek açıklama mesajı
 */
public record SuppressionWarning(
        String ruleId,
        String profileName,
        String pattern,
        WarningSeverity severity,
        String message
) {

    public enum WarningSeverity {
        /** Rule ID tamamen kaldırılmış — suppression artık etkisiz */
        CRITICAL,
        /** Rule ID yeniden adlandırılmış olabilir — manual kontrol gerekli */
        WARNING,
        /** Bilgilendirme — ufak değişiklik algılandı */
        INFO
    }

    /**
     * Kaldırılan rule ID için kritik uyarı oluşturur.
     */
    public static SuppressionWarning removed(String ruleId, String profileName, String pattern) {
        return new SuppressionWarning(ruleId, profileName, pattern, WarningSeverity.CRITICAL,
                String.format("'%s' profilindeki '%s' suppression'ı, '%s' rule ID'sine bağlıdır. " +
                        "Bu ID yeni versiyonda KALDIRILMIŞTIR. Suppression artık etkisiz olacaktır.",
                        profileName, pattern, ruleId));
    }

    /**
     * Yeniden adlandırılmış olabilecek rule ID için uyarı oluşturur.
     */
    public static SuppressionWarning possiblyRenamed(String ruleId, String profileName,
                                                      String pattern, String possibleNewId) {
        return new SuppressionWarning(ruleId, profileName, pattern, WarningSeverity.WARNING,
                String.format("'%s' profilindeki '%s' suppression'ı, '%s' rule ID'sine bağlıdır. " +
                        "Bu ID yeni versiyonda değişmiş olabilir (olası yeni ID: '%s'). Manuel kontrol önerilir.",
                        profileName, pattern, ruleId, possibleNewId));
    }
}

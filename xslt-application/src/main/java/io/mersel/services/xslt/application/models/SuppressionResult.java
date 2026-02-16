package io.mersel.services.xslt.application.models;

import java.util.List;

/**
 * Bastırma (suppression) işleminin sonucu.
 * <p>
 * Doğrulama sonrası profil uygulandıktan sonra aktif ve bastırılmış
 * hataları ayrıştırarak şeffaf raporlama sağlar.
 *
 * @param activeErrors     Bastırılmayan (aktif) hatalar
 * @param suppressedErrors Profil tarafından bastırılan hatalar
 * @param profileName      Uygulanan profil adı ({@code null} ise profil uygulanmamıştır)
 * @param suppressedCount  Bastırılan hata sayısı
 */
public record SuppressionResult(
        List<SchematronError> activeErrors,
        List<SchematronError> suppressedErrors,
        String profileName,
        int suppressedCount
) {
}

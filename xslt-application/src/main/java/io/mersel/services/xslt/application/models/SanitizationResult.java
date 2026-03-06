package io.mersel.services.xslt.application.models;

import java.util.List;

/**
 * HTML sanitization sonucu.
 * <p>
 * {@code HtmlSanitizer} tarafından üretilir. Temizlenmiş HTML içeriği,
 * izin verilen script hash'leri ve kaldırılan script bilgilerini taşır.
 * <p>
 * {@code allowedScriptHashes} listesi, dinamik CSP header'ı oluşturmak
 * için {@code TransformController} tarafından kullanılır:
 * {@code script-src 'sha256-xxx' 'sha256-yyy'}
 *
 * @param sanitizedHtml       Temizlenmiş HTML içeriği (UTF-8 byte dizisi)
 * @param allowedScriptHashes İzin verilen script'lerin Base64-encoded SHA-256 hash'leri
 * @param removedScriptCount  Kaldırılan script sayısı
 * @param removalReasons      Kaldırılan her script için sebep açıklaması
 */
public record SanitizationResult(
        byte[] sanitizedHtml,
        List<String> allowedScriptHashes,
        int removedScriptCount,
        List<String> removalReasons
) {
}

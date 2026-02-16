package io.mersel.services.xslt.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Standart hata yanıtı modeli.
 * <p>
 * Filter, interceptor ve controller'lardaki tüm hata yanıtları için
 * tutarlı JSON formatı sağlar. Elle JSON string yazmak yerine bu record
 * kullanılarak Jackson ile serileştirilir.
 * <p>
 * Örnek çıktı:
 * <pre>
 * {"error": "Unauthorized", "message": "Geçerli bir token gereklidir."}
 * </pre>
 *
 * @param error   Hata kategorisi (ör: "Unauthorized", "Rate limit exceeded")
 * @param message Kullanıcıya gösterilecek açıklayıcı mesaj
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(String error, String message) {
}

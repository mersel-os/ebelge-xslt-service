package io.mersel.services.xslt.web.infrastructure;

import java.text.Normalizer;

/**
 * Serbest metni (hata mesajı, ihlal açıklaması) HTTP response header değeri
 * olarak güvenle yazılabilir hale getirir.
 * <p>
 * Tomcat header değerlerini ISO-8859-1 ile kodlar; bu aralığın dışında tek bir
 * karakter ("ı", "ş", "ğ", "—" gibi) bulunan header'ı yanıta hiç yazmaz.
 * Bu sınıf değeri ISO-8859-1 içine indirir:
 * <ul>
 *   <li>CR, LF ve diğer kontrol karakterleri boşluk olur (HTTP Response Splitting koruması)</li>
 *   <li>ISO-8859-1 karakterleri olduğu gibi kalır ("ü", "ç", "ö", "Ü", "Ç", "Ö")</li>
 *   <li>"ı" → "i"; "ş", "ğ", "İ" gibi aksanlı harfler aksansız temel harfe indirilir</li>
 *   <li>Uzun/kısa tire "-", tipografik tırnaklar düz tırnak, "…" "..." olur</li>
 *   <li>Karşılığı olmayan her karakter (emoji gibi) tek bir "?" olur</li>
 * </ul>
 * Değer yüzde-kodlanmaz: .NET istemcisi ve web arayüzü header'ı düz metin olarak
 * okuyup baytları ISO-8859-1 olarak çözüyor, ek bir çözme adımı yok.
 */
public final class HeaderValueSanitizer {

    private HeaderValueSanitizer() {
    }

    /**
     * Değeri ISO-8859-1 ile kodlanabilir, kontrol karakteri içermeyen bir metne çevirir
     * ve en fazla {@code maxLength} karaktere keser.
     *
     * @param value     ham değer; {@code null} ise {@code null} döner
     * @param maxLength sonuç için azami karakter sayısı
     * @return header'a yazılabilir değer
     */
    public static String sanitize(String value, int maxLength) {
        if (value == null) {
            return null;
        }

        // Ayrışık yazılmış harfleri ("s" + U+0327) tek karaktere ("ş") birleştir
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFC);

        var out = new StringBuilder(normalized.length());
        normalized.codePoints().forEach(cp -> appendSafe(out, cp));

        return out.length() > maxLength ? out.substring(0, maxLength) : out.toString();
    }

    private static void appendSafe(StringBuilder out, int cp) {
        if (Character.isISOControl(cp)) {
            out.append(' ');
            return;
        }
        if (cp <= 0xFF) {
            out.append((char) cp);
            return;
        }
        // U+2028, U+2009, U+202F gibi boşluk ve satır ayırıcıları
        if (Character.isWhitespace(cp) || Character.isSpaceChar(cp)) {
            out.append(' ');
            return;
        }

        String replacement = replacementFor(cp);
        if (replacement != null) {
            out.append(replacement);
            return;
        }

        char base = baseLetter(cp);
        out.append(base != 0 ? base : '?');
    }

    /** Ayrıştırma ile temel harfe inmeyen karakterler için sabit karşılıklar. */
    private static String replacementFor(int cp) {
        return switch (cp) {
            case '\u0131' -> "i"; // noktasız i: ayrıştırma ile "i"ye inmez
            case '\u2010', '\u2011', '\u2012', '\u2013', '\u2014', '\u2015', '\u2212' -> "-"; // tireler, eksi
            case '\u2018', '\u2019', '\u201A', '\u201B' -> "'"; // tek tırnaklar
            case '\u201C', '\u201D', '\u201E', '\u201F' -> "\""; // çift tırnaklar
            case '\u2026' -> "..."; // üç nokta
            default -> null;
        };
    }

    /**
     * Karakteri kanonik olarak ayrıştırıp (NFD) temel harfi ISO-8859-1 içindeyse ve
     * geri kalanı yalnızca birleşik işaretlerden oluşuyorsa temel harfi döner
     * ("ş" → "s", "ğ" → "g", "İ" → "I"); aksi halde 0.
     */
    private static char baseLetter(int cp) {
        String decomposed = Normalizer.normalize(new String(Character.toChars(cp)), Normalizer.Form.NFD);
        char base = decomposed.charAt(0);
        if (decomposed.length() < 2 || base > 0xFF || !Character.isLetter(base)) {
            return 0;
        }
        for (int i = 1; i < decomposed.length(); i++) {
            if (Character.getType(decomposed.charAt(i)) != Character.NON_SPACING_MARK) {
                return 0;
            }
        }
        return base;
    }
}

package io.mersel.services.xslt.infrastructure;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * JAXP/Xerces XSD doğrulama hata mesajlarını kullanıcı dostu biçime dönüştürür.
 * <p>
 * Xerces hataları namespace URI'lar ve teknik kodlar içerir (örn: {@code cvc-complex-type.2.4.a}).
 * Bu sınıf bu hataları parse ederek:
 * <ul>
 *   <li>Namespace URI'larını temizler (sadece element adını gösterir)</li>
 *   <li>Türkçe açıklama üretir</li>
 *   <li>Beklenen element'leri listeler</li>
 *   <li>Çözüm önerisi sunar</li>
 * </ul>
 * Dönüşüm başarısızsa orijinal mesaj olduğu gibi döner.
 */
final class XsdErrorHumanizer {

    private XsdErrorHumanizer() {}

    // ── Namespace temizleme ──────────────────────────────────────────

    /**
     * Xerces Clark notasyonu: {"namespace-uri":LocalName}
     * Ayrıca listelerde: {"ns":A, "ns":B, "ns":C}
     *
     * Strateji:
     *   1) "namespace-uri": prefix'lerini sil → sadece element adları kalır
     *   2) Kalan boş süslü parantezleri temizle
     */
    private static final Pattern NS_QUOTED_PREFIX = Pattern.compile("\"[^\"]+\":");

    /**
     * Tüm namespace prefix/URI referanslarını temizleyerek sadece element adlarını bırakır.
     * <p>
     * Xerces hata mesajları Clark notasyonu kullanır:
     * <pre>{"urn:oasis:names:...:CommonBasicComponents-2":LineCountNumeric}</pre>
     * Bu metot bunu {@code LineCountNumeric} olarak sadeleştirir.
     */
    static String stripNamespaces(String msg) {
        if (msg == null) return msg;

        // Adım 1: "namespace-uri": prefix'lerini kaldır
        // {"urn:oasis:...:cbc-2":LineCountNumeric} → {LineCountNumeric}
        // {"ns":A, "ns":B} → {A, B}
        msg = NS_QUOTED_PREFIX.matcher(msg).replaceAll("");

        // Adım 2: Kalan süslü parantezleri temizle
        // {LineCountNumeric} → LineCountNumeric
        // {A, B} → A, B
        msg = msg.replace("{", "").replace("}", "");

        // Adım 3: Fazla boşlukları temizle
        msg = msg.replaceAll("  +", " ");

        return msg;
    }

    // ── Error code patterns ──────────────────────────────────────────

    /**
     * cvc-complex-type.2.4.a: Invalid content ... starting with element 'X'. One of 'A, B, C' is expected.
     * → Element yanlış yerde / sıralama hatası
     */
    private static final Pattern CVC_2_4_A = Pattern.compile(
            "cvc-complex-type\\.2\\.4\\.a:\\s*Invalid content was found starting with element '([^']+)'\\." +
            "\\s*One of '([^']+)' is expected\\."
    );

    /**
     * cvc-complex-type.2.4.b: The content of element 'X' is not complete. One of 'A, B' is expected.
     * → Zorunlu element eksik
     */
    private static final Pattern CVC_2_4_B = Pattern.compile(
            "cvc-complex-type\\.2\\.4\\.b:\\s*The content of element '([^']+)' is not complete\\." +
            "\\s*One of '([^']+)' is expected\\."
    );

    /**
     * cvc-type.3.1.3: The value 'X' of element 'Y' is not valid.
     * → Değer geçersiz
     */
    private static final Pattern CVC_TYPE_3_1_3 = Pattern.compile(
            "cvc-type\\.3\\.1\\.3:\\s*The value '([^']*)' of element '([^']+)' is not valid\\."
    );

    /**
     * cvc-datatype-valid.1.2.1: 'X' is not a valid value for 'Y'.
     * → Veri tipi uyumsuzluğu
     */
    private static final Pattern CVC_DATATYPE = Pattern.compile(
            "cvc-datatype-valid\\.1\\.2\\.1:\\s*'([^']*)' is not a valid value for '([^']+)'\\."
    );

    /**
     * cvc-enumeration-valid: Value 'X' is not facet-valid with respect to enumeration '[A, B, C]'.
     * → İzin verilen değerler dışında
     */
    private static final Pattern CVC_ENUM = Pattern.compile(
            "cvc-enumeration-valid:\\s*Value '([^']*)' is not facet-valid with respect to enumeration '\\[([^\\]]+)\\]'\\."
    );

    /**
     * cvc-minLength-valid / cvc-maxLength-valid
     */
    private static final Pattern CVC_LENGTH = Pattern.compile(
            "cvc-(min|max)Length-valid:\\s*Value '([^']*)' with length = '(\\d+)' is not facet-valid with respect to (min|max)Length '(\\d+)'"
    );

    /**
     * cvc-complex-type.2.2: Element 'X' must have no element [children], and the value must be valid.
     * → Sadece text bekleniyor ama alt element var
     */
    private static final Pattern CVC_2_2 = Pattern.compile(
            "cvc-complex-type\\.2\\.2:\\s*Element '([^']+)' must have no element \\[children\\]"
    );

    /**
     * cvc-complex-type.2.3: Element 'X' cannot have character [children]
     * → Text içerik beklenmiyor
     */
    private static final Pattern CVC_2_3 = Pattern.compile(
            "cvc-complex-type\\.2\\.3:\\s*Element '([^']+)' cannot have character"
    );

    /**
     * cvc-complex-type.3.2.2: Attribute 'X' is not allowed to appear in element 'Y'.
     */
    private static final Pattern CVC_ATTR_NOT_ALLOWED = Pattern.compile(
            "cvc-complex-type\\.3\\.2\\.2:\\s*Attribute '([^']+)' is not allowed to appear in element '([^']+)'\\."
    );

    // ── Humanize ─────────────────────────────────────────────────────

    /**
     * Xerces hata mesajını kullanıcı dostu formata dönüştürür.
     *
     * @param line   satır numarası (0 veya negatifse gösterilmez)
     * @param column sütun numarası
     * @param rawMsg Xerces ham hata mesajı
     * @return Kullanıcı dostu hata mesajı
     */
    static String humanize(int line, int column, String rawMsg) {
        if (rawMsg == null || rawMsg.isBlank()) {
            return rawMsg;
        }

        // Önce namespace'leri temizle
        String cleaned = stripNamespaces(rawMsg);

        String friendly = tryHumanize(cleaned);
        if (friendly != null) {
            var sb = new StringBuilder();
            if (line > 0) {
                sb.append("Satır ").append(line);
                if (column > 0) sb.append(", Sütun ").append(column);
                sb.append(": ");
            }
            sb.append(friendly);
            return sb.toString();
        }

        // Dönüşüm yapılamadıysa namespace'leri temizlenmiş haliyle dön
        if (line > 0) {
            return String.format("Satır %d, Sütun %d: %s", line, column, cleaned);
        }
        return cleaned;
    }

    /**
     * Bilinen error code pattern'lerini Türkçe açıklamaya dönüştürür.
     * Eşleşme yoksa {@code null} döner.
     */
    private static String tryHumanize(String msg) {
        Matcher m;

        // ── cvc-complex-type.2.4.a — Element yanlış yerde ──
        m = CVC_2_4_A.matcher(msg);
        if (m.find()) {
            String found = stripQuotesAndNs(m.group(1));
            List<String> expected = parseElementList(m.group(2));
            var sb = new StringBuilder();
            sb.append("\"").append(found).append("\" elementi bu konumda geçersiz.");
            if (!expected.isEmpty()) {
                sb.append(" Bu noktada beklenen: ").append(formatList(expected)).append(".");
            }
            sb.append(" → Element sırasını kontrol edin veya \"").append(found).append("\"'i doğru konuma taşıyın.");
            return sb.toString();
        }

        // ── cvc-complex-type.2.4.b — Zorunlu element eksik ──
        m = CVC_2_4_B.matcher(msg);
        if (m.find()) {
            String parent = stripQuotesAndNs(m.group(1));
            List<String> expected = parseElementList(m.group(2));
            var sb = new StringBuilder();
            sb.append("\"").append(parent).append("\" elementinin içeriği eksik.");
            if (!expected.isEmpty()) {
                sb.append(" Zorunlu element(ler): ").append(formatList(expected)).append(".");
            }
            sb.append(" → Eksik zorunlu element(ler)i ekleyin.");
            return sb.toString();
        }

        // ── cvc-type.3.1.3 — Değer geçersiz ──
        m = CVC_TYPE_3_1_3.matcher(msg);
        if (m.find()) {
            String value = m.group(1);
            String element = stripQuotesAndNs(m.group(2));
            if (value.isEmpty()) {
                return "\"" + element + "\" elementinin değeri boş olamaz.";
            }
            return "\"" + element + "\" elementinin değeri geçersiz: \"" + truncate(value, 50) + "\".";
        }

        // ── cvc-datatype-valid — Veri tipi uyumsuz ──
        m = CVC_DATATYPE.matcher(msg);
        if (m.find()) {
            String value = m.group(1);
            String type = m.group(2);
            String friendlyType = friendlyTypeName(type);
            return "\"" + truncate(value, 50) + "\" değeri " + friendlyType + " formatına uygun değil.";
        }

        // ── cvc-enumeration-valid — İzin verilen değerler ──
        m = CVC_ENUM.matcher(msg);
        if (m.find()) {
            String value = m.group(1);
            String allowed = m.group(2);
            return "\"" + value + "\" değeri geçersiz. İzin verilen değerler: " + allowed + ".";
        }

        // ── cvc-min/maxLength — Uzunluk kısıtı ──
        m = CVC_LENGTH.matcher(msg);
        if (m.find()) {
            String minMax = m.group(1);
            String value = m.group(2);
            String actualLen = m.group(3);
            String limitLen = m.group(5);
            if ("min".equals(minMax)) {
                return "Değer çok kısa (uzunluk: " + actualLen + ", minimum: " + limitLen + "): \"" + truncate(value, 40) + "\".";
            } else {
                return "Değer çok uzun (uzunluk: " + actualLen + ", maksimum: " + limitLen + "): \"" + truncate(value, 40) + "\".";
            }
        }

        // ── cvc-complex-type.2.2 — Sadece text bekleniyor ama alt element var ──
        m = CVC_2_2.matcher(msg);
        if (m.find()) {
            String element = stripQuotesAndNs(m.group(1));
            return "\"" + element + "\" elementi alt element içeremez, sadece metin değeri almalıdır." +
                    " → Alt element'leri kaldırın ve geçerli bir metin değeri girin.";
        }

        // ── cvc-complex-type.2.3 — Text içerik beklenmiyor ──
        m = CVC_2_3.matcher(msg);
        if (m.find()) {
            String element = stripQuotesAndNs(m.group(1));
            return "\"" + element + "\" elementi doğrudan metin içeremez. İçerik alt element olarak verilmelidir.";
        }

        // ── Attribute not allowed ──
        m = CVC_ATTR_NOT_ALLOWED.matcher(msg);
        if (m.find()) {
            String attr = m.group(1);
            String element = stripQuotesAndNs(m.group(2));
            return "\"" + element + "\" elementinde \"" + attr + "\" niteliği kullanılamaz.";
        }

        return null; // Bilinmeyen pattern
    }

    // ── Yardımcı metotlar ────────────────────────────────────────────

    /**
     * Kalan namespace kalıntılarını temizler (savunma amaçlı).
     * stripNamespaces() zaten ana temizliği yapar, bu metot ek güvenlik sağlar.
     * "ns:Element" → "Element", "{ns}Element" → "Element", tırnakları siler.
     */
    private static String stripQuotesAndNs(String s) {
        if (s == null) return "";
        // Clark notasyonu kalıntısı: {"ns":Name} → Name
        s = NS_QUOTED_PREFIX.matcher(s).replaceAll("");
        s = s.replace("{", "").replace("}", "");
        // prefix:LocalName → LocalName (klasik namespace prefix)
        int colon = s.lastIndexOf(':');
        if (colon >= 0) s = s.substring(colon + 1);
        return s.replace("\"", "").replace("'", "").trim();
    }

    /**
     * Virgülle ayrılmış element listesini parse eder.
     * "'ns:A', 'ns:B', 'ns:C'" → ["A", "B", "C"]
     */
    private static List<String> parseElementList(String raw) {
        if (raw == null || raw.isBlank()) return List.of();
        var result = new ArrayList<String>();
        for (String part : raw.split(",")) {
            String cleaned = stripQuotesAndNs(part.trim().replace("'", ""));
            if (!cleaned.isBlank()) {
                result.add(cleaned);
            }
        }
        return result;
    }

    /**
     * Element listesini okunabilir formatta birleştirir.
     * 3'ten fazlaysa ilk 3'ü gösterir + "ve N tane daha".
     */
    private static String formatList(List<String> items) {
        if (items.isEmpty()) return "";
        if (items.size() <= 3) {
            return String.join(", ", items);
        }
        return items.get(0) + ", " + items.get(1) + ", " + items.get(2)
                + " ve " + (items.size() - 3) + " tane daha";
    }

    /**
     * XSD tip adını kullanıcı dostu isme dönüştürür.
     */
    private static String friendlyTypeName(String xsdType) {
        if (xsdType == null) return "beklenen tip";
        return switch (xsdType.toLowerCase()) {
            case "date", "datetype" -> "tarih (YYYY-MM-DD)";
            case "datetime", "datetimetype" -> "tarih-saat (YYYY-MM-DDThh:mm:ss)";
            case "decimal", "decimaltype" -> "ondalıklı sayı";
            case "integer", "integertype", "int" -> "tam sayı";
            case "boolean", "booleantype" -> "mantıksal (true/false)";
            case "anyuri", "anyuritype" -> "URI/URL";
            case "positiveinteger" -> "pozitif tam sayı";
            case "nonnegativeinteger" -> "sıfır veya pozitif tam sayı";
            case "gyearmonth" -> "yıl-ay (YYYY-MM)";
            case "gyear" -> "yıl (YYYY)";
            case "time", "timetype" -> "saat (hh:mm:ss)";
            case "token", "normalizedstring" -> "metin";
            default -> "\"" + xsdType + "\"";
        };
    }

    /**
     * Uzun metinleri kırpar.
     */
    private static String truncate(String s, int max) {
        if (s == null) return "";
        if (s.length() <= max) return s;
        return s.substring(0, max) + "…";
    }
}

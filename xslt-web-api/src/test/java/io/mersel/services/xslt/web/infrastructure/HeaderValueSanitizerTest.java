package io.mersel.services.xslt.web.infrastructure;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("HeaderValueSanitizer")
class HeaderValueSanitizerTest {

    private static boolean isLatin1(String value) {
        return StandardCharsets.ISO_8859_1.newEncoder().canEncode(value);
    }

    @Test
    @DisplayName("null değer null döner")
    void nullStaysNull() {
        assertThat(HeaderValueSanitizer.sanitize(null, 500)).isNull();
    }

    @Test
    @DisplayName("ASCII ve ISO-8859-1 metin değişmeden kalır")
    void asciiAndLatin1AreUnchanged() {
        assertThat(HeaderValueSanitizer.sanitize("XSLT compilation failed at line 42", 500))
                .isEqualTo("XSLT compilation failed at line 42");
        assertThat(HeaderValueSanitizer.sanitize("Gömülü XSLT çözümü: ÜÇÖ üçö", 500))
                .isEqualTo("Gömülü XSLT çözümü: ÜÇÖ üçö");
    }

    @Test
    @DisplayName("ISO-8859-1 dışındaki Türkçe harfler ASCII karşılığına iner")
    void turkishLettersOutsideLatin1AreFolded() {
        assertThat(HeaderValueSanitizer.sanitize("ı İ ş Ş ğ Ğ", 500)).isEqualTo("i I s S g G");
        assertThat(HeaderValueSanitizer.sanitize("Gömülü XSLT ile dönüşüm başarısız: Fatura numarası boş", 500))
                .isEqualTo("Gömülü XSLT ile dönüsüm basarisiz: Fatura numarasi bos");
    }

    @Test
    @DisplayName("Ayrışık yazılmış harfler önce birleştirilir")
    void decomposedInputIsComposedFirst() {
        // "s" + birleşik çengel, "u" + birleşik iki nokta
        assertThat(HeaderValueSanitizer.sanitize("başarısız ü", 500))
                .isEqualTo("basarisiz ü");
    }

    @ParameterizedTest(name = "[{index}] {0} → {1}")
    @CsvSource(delimiter = '|', quoteCharacter = '`', value = {
            "a — b      | a - b",
            "a – b      | a - b",
            "2−1        | 2-1",
            "‘tek’      | 'tek'",
            "“çift”     | `\"çift\"`",
            "devamı…    | devami...",
    })
    @DisplayName("Tipografik noktalama ASCII karşılığına iner")
    void typographicPunctuationIsFolded(String input, String expected) {
        assertThat(HeaderValueSanitizer.sanitize(input, 500)).isEqualTo(expected);
    }

    @Test
    @DisplayName("CR, LF ve diğer kontrol karakterleri boşluk olur")
    void controlCharactersBecomeSpaces() {
        String result = HeaderValueSanitizer.sanitize(
                "satır1\r\nX-Injected: evet\tson\u0000\u007F\u0085\u2028", 500);

        assertThat(result).isEqualTo("satir1  X-Injected: evet son    ");
        assertThat(result).doesNotContain("\r", "\n");
    }

    @Test
    @DisplayName("Karşılığı olmayan karakter tek '?' olur, vekil çift bölünmez")
    void unmappableCharactersBecomeSingleQuestionMark() {
        assertThat(HeaderValueSanitizer.sanitize("hata 😀 son", 500)).isEqualTo("hata ? son");
        assertThat(HeaderValueSanitizer.sanitize("错误 €", 500)).isEqualTo("?? ?");
    }

    @Test
    @DisplayName("Kesme dönüşümden sonra yapılır")
    void truncatesAfterSanitizing() {
        assertThat(HeaderValueSanitizer.sanitize("abc…", 5)).isEqualTo("abc..");
        assertThat(HeaderValueSanitizer.sanitize("😀😀😀", 2)).isEqualTo("??");
        assertThat(HeaderValueSanitizer.sanitize("ş".repeat(600), 500)).hasSize(500);
    }

    @Test
    @DisplayName("Sonuç her zaman ISO-8859-1 ile kodlanabilir ve kontrol karakteri içermez")
    void resultIsAlwaysLatin1WithoutControls() {
        var sb = new StringBuilder();
        for (int cp = 0; cp < 0x3000; cp++) {
            if (!Character.isSurrogate((char) cp)) {
                sb.appendCodePoint(cp);
            }
        }
        sb.append("😀𝔘\uD800"); // emoji, matematik harf, eşlenmemiş vekil

        String result = HeaderValueSanitizer.sanitize(sb.toString(), Integer.MAX_VALUE);

        assertThat(isLatin1(result)).isTrue();
        assertThat(result.chars()).noneMatch(Character::isISOControl);
    }
}

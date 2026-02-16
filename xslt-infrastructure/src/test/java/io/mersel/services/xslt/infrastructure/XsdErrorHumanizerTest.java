package io.mersel.services.xslt.infrastructure;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * XsdErrorHumanizer birim testleri.
 * <p>
 * Xerces XSD doğrulama hata mesajlarının Türkçe kullanıcı dostu formata
 * dönüşümünü test eder. Sınıf package-private olduğundan testler aynı pakette.
 */
@DisplayName("XsdErrorHumanizer")
class XsdErrorHumanizerTest {

    @Nested
    @DisplayName("humanize — Xerces hata dönüşümleri")
    class HumanizeTests {

        @Test
        @DisplayName("cvc-complex-type.2.4.a: Element yanlış yerde (Clark notasyonu)")
        void cvc_2_4_a_element_yanlis_yerde() {
            String input = """
                cvc-complex-type.2.4.a: Invalid content was found starting with element '{"urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2":LineCountNumeric}'.
                One of '{"urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2":Note, "urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2":DocumentCurrencyCode}' is expected.""";

            String result = XsdErrorHumanizer.humanize(0, 0, input);

            assertThat(result).contains("LineCountNumeric");
            assertThat(result).contains("Note");
            assertThat(result).contains("DocumentCurrencyCode");
            assertThat(result).contains("bu konumda geçersiz");
        }

        @Test
        @DisplayName("cvc-complex-type.2.4.b: Zorunlu element eksik")
        void cvc_2_4_b_zorunlu_element_eksik() {
            String input = """
                cvc-complex-type.2.4.b: The content of element '{"urn:oasis:names:specification:ubl:schema:xsd:CommonAggregateComponents-2":Invoice}' is not complete.
                One of '{"urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2":ID, "urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2":IssueDate}' is expected.""";

            String result = XsdErrorHumanizer.humanize(0, 0, input);

            assertThat(result).contains("Invoice");
            assertThat(result).contains("içeriği eksik");
            assertThat(result).contains("ID");
            assertThat(result).contains("IssueDate");
        }

        @Test
        @DisplayName("cvc-type.3.1.3: Geçersiz değer")
        void cvc_type_3_1_3_gecersiz_deger() {
            String input = "cvc-type.3.1.3: The value 'abc' of element 'Amount' is not valid.";

            String result = XsdErrorHumanizer.humanize(0, 0, input);

            assertThat(result).contains("Amount");
            assertThat(result).contains("geçersiz");
            assertThat(result).contains("abc");
        }

        @Test
        @DisplayName("cvc-type.3.1.3: Boş değer")
        void cvc_type_3_1_3_bos_deger() {
            String input = "cvc-type.3.1.3: The value '' of element 'ID' is not valid.";

            String result = XsdErrorHumanizer.humanize(0, 0, input);

            assertThat(result).contains("ID");
            assertThat(result).contains("boş olamaz");
        }

        @Test
        @DisplayName("cvc-datatype-valid: Tarih formatı")
        void cvc_datatype_tarih_formati() {
            String input = "cvc-datatype-valid.1.2.1: 'abc' is not a valid value for 'date'.";

            String result = XsdErrorHumanizer.humanize(0, 0, input);

            assertThat(result).contains("tarih (YYYY-MM-DD)");
        }

        @Test
        @DisplayName("cvc-enumeration-valid: İzin verilen değerler")
        void cvc_enumeration_izin_verilen() {
            String input = "cvc-enumeration-valid: Value 'XYZ' is not facet-valid with respect to enumeration '[TRY, USD, EUR]'.";

            String result = XsdErrorHumanizer.humanize(0, 0, input);

            assertThat(result).contains("XYZ");
            assertThat(result).contains("geçersiz");
            assertThat(result).contains("TRY, USD, EUR");
        }

        @Test
        @DisplayName("cvc-minLength-valid: Değer çok kısa")
        void cvc_minLength_kisa() {
            String input = "cvc-minLength-valid: Value 'AB' with length = '2' is not facet-valid with respect to minLength '10' for type 'someType'.";

            String result = XsdErrorHumanizer.humanize(0, 0, input);

            assertThat(result).contains("çok kısa");
            assertThat(result).contains("2");
            assertThat(result).contains("10");
        }

        @Test
        @DisplayName("cvc-maxLength-valid: Değer çok uzun")
        void cvc_maxLength_uzun() {
            String input = "cvc-maxLength-valid: Value 'ABCDEFGHIJK' with length = '11' is not facet-valid with respect to maxLength '5' for type 'someType'.";

            String result = XsdErrorHumanizer.humanize(0, 0, input);

            assertThat(result).contains("çok uzun");
            assertThat(result).contains("11");
            assertThat(result).contains("5");
        }

        @Test
        @DisplayName("cvc-complex-type.2.2: Alt element içeremez (e-Defter entriesType)")
        void cvc_2_2_alt_element_iceremez() {
            String input = "cvc-complex-type.2.2: Element 'gl-cor:entriesType' must have no element [children], and the value must be valid.";

            String result = XsdErrorHumanizer.humanize(39, 79, input);

            assertThat(result)
                    .startsWith("Satır 39, Sütun 79: ")
                    .contains("entriesType")
                    .contains("alt element içeremez")
                    .contains("sadece metin değeri");
        }

        @Test
        @DisplayName("cvc-complex-type.2.2: Namespace prefix temizlenmeli")
        void cvc_2_2_namespace_temizlik() {
            String input = "cvc-complex-type.2.2: Element '{\"http://www.xbrl.org/int/gl/cor/2006-10-25\":entriesType}' must have no element [children], and the value must be valid.";

            String result = XsdErrorHumanizer.humanize(0, 0, input);

            assertThat(result)
                    .contains("entriesType")
                    .contains("alt element içeremez")
                    .doesNotContain("xbrl.org");
        }

        @Test
        @DisplayName("cvc-complex-type.2.3: Metin içerik beklenmiyor")
        void cvc_2_3_text_icerik() {
            String input = "cvc-complex-type.2.3: Element 'Invoice' cannot have character [children]";

            String result = XsdErrorHumanizer.humanize(0, 0, input);

            assertThat(result).contains("Invoice");
            assertThat(result).contains("metin içeremez");
        }

        @Test
        @DisplayName("cvc-complex-type.3.2.2: Nitelik kullanılamaz")
        void cvc_attr_not_allowed() {
            String input = "cvc-complex-type.3.2.2: Attribute 'foo' is not allowed to appear in element 'Invoice'.";

            String result = XsdErrorHumanizer.humanize(0, 0, input);

            assertThat(result).contains("Invoice");
            assertThat(result).contains("foo");
            assertThat(result).contains("kullanılamaz");
        }

        @Test
        @DisplayName("Bilinmeyen pattern: Namespace temizliği yapılır")
        void bilinmeyen_pattern_namespace_temizlik() {
            String input = "some-unknown-error: Element '{\"urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2\":Amount}' had an issue.";

            String result = XsdErrorHumanizer.humanize(0, 0, input);

            assertThat(result).doesNotContain("urn:oasis");
            assertThat(result).contains("Amount");
        }

        @Test
        @DisplayName("Null ve boş input olduğu gibi döner")
        void null_ve_bos_input() {
            assertThat(XsdErrorHumanizer.humanize(0, 0, null)).isNull();
            assertThat(XsdErrorHumanizer.humanize(0, 0, "")).isEmpty();
            assertThat(XsdErrorHumanizer.humanize(0, 0, "   ")).isEqualTo("   ");
        }

        @Test
        @DisplayName("Uzun element listesi kısaltılır (ilk 3 + 've N tane daha')")
        void uzun_element_listesi_truncate() {
            String input = "cvc-complex-type.2.4.a: Invalid content was found starting with element 'Wrong'."
                    + " One of 'Alpha, Beta, Gamma, Delta, Epsilon' is expected.";

            String result = XsdErrorHumanizer.humanize(0, 0, input);

            assertThat(result).contains("ve 2 tane daha");
        }

        @Test
        @DisplayName("Satır ve sütun numarası eklenir")
        void satir_sutun_numarasi_eklenir() {
            String input = "cvc-type.3.1.3: The value 'x' of element 'ID' is not valid.";
            int line = 28;
            int column = 27;

            String result = XsdErrorHumanizer.humanize(line, column, input);

            assertThat(result).startsWith("Satır 28, Sütun 27: ");
        }
    }

    @Nested
    @DisplayName("stripNamespaces — Namespace temizleme")
    class StripNamespacesTests {

        @Test
        @DisplayName("Clark notasyonu namespace'leri kaldırır")
        void clark_notation_namespace_stripping() {
            String input = "Element '{\"urn:oasis:names:specification:ubl:schema:xsd:CommonBasicComponents-2\":Amount}' had an issue.";

            String result = XsdErrorHumanizer.stripNamespaces(input);

            assertThat(result).doesNotContain("urn:oasis");
            assertThat(result).contains("Amount");
            assertThat(result).doesNotContain("\"");
        }

        @Test
        @DisplayName("null input null döner")
        void null_returns_null() {
            assertThat(XsdErrorHumanizer.stripNamespaces(null)).isNull();
        }
    }
}

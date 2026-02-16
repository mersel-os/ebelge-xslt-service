package io.mersel.services.xslt.infrastructure;

import io.mersel.services.xslt.application.enums.DocumentType;
import io.mersel.services.xslt.application.interfaces.DocumentTypeDetectionException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * DocumentTypeDetector birim testleri.
 * <p>
 * 12 belge türü için XML byte içeriğinden doğru tespit yapıldığını,
 * hatalı/tanınmayan XML'ler için uygun exception fırlatıldığını test eder.
 */
@DisplayName("DocumentTypeDetector")
class DocumentTypeDetectorTest {

    private final DocumentTypeDetector detector = new DocumentTypeDetector();

    // ── UBL-TR Belge Türleri ──────────────────────────────────────────

    @Nested
    @DisplayName("UBL-TR Belgeleri")
    class UblTrDocuments {

        @Test
        @DisplayName("Invoice namespace → INVOICE")
        void detect_invoice() throws Exception {
            byte[] xml = xml("""
                    <Invoice xmlns="urn:oasis:names:specification:ubl:schema:xsd:Invoice-2"
                             xmlns:cac="urn:oasis:names:specification:ubl:schema:xsd:CommonAggregateComponents-2">
                        <cac:ID>INV-001</cac:ID>
                    </Invoice>
                    """);

            assertThat(detector.detect(xml)).isEqualTo(DocumentType.INVOICE);
        }

        @Test
        @DisplayName("CreditNote namespace → CREDIT_NOTE")
        void detect_creditNote() throws Exception {
            byte[] xml = xml("""
                    <CreditNote xmlns="urn:oasis:names:specification:ubl:schema:xsd:CreditNote-2">
                        <ID>CN-001</ID>
                    </CreditNote>
                    """);

            assertThat(detector.detect(xml)).isEqualTo(DocumentType.CREDIT_NOTE);
        }

        @Test
        @DisplayName("DespatchAdvice namespace → DESPATCH_ADVICE")
        void detect_despatchAdvice() throws Exception {
            byte[] xml = xml("""
                    <DespatchAdvice xmlns="urn:oasis:names:specification:ubl:schema:xsd:DespatchAdvice-2">
                        <ID>DA-001</ID>
                    </DespatchAdvice>
                    """);

            assertThat(detector.detect(xml)).isEqualTo(DocumentType.DESPATCH_ADVICE);
        }

        @Test
        @DisplayName("ReceiptAdvice namespace → RECEIPT_ADVICE")
        void detect_receiptAdvice() throws Exception {
            byte[] xml = xml("""
                    <ReceiptAdvice xmlns="urn:oasis:names:specification:ubl:schema:xsd:ReceiptAdvice-2">
                        <ID>RA-001</ID>
                    </ReceiptAdvice>
                    """);

            assertThat(detector.detect(xml)).isEqualTo(DocumentType.RECEIPT_ADVICE);
        }

        @Test
        @DisplayName("ApplicationResponse namespace → APPLICATION_RESPONSE")
        void detect_applicationResponse() throws Exception {
            byte[] xml = xml("""
                    <ApplicationResponse xmlns="urn:oasis:names:specification:ubl:schema:xsd:ApplicationResponse-2">
                        <ID>AR-001</ID>
                    </ApplicationResponse>
                    """);

            assertThat(detector.detect(xml)).isEqualTo(DocumentType.APPLICATION_RESPONSE);
        }
    }

    // ── e-Arşiv ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("e-Arşiv Belgeleri")
    class EArchiveDocuments {

        @Test
        @DisplayName("e-Arşiv namespace → EARCHIVE_REPORT")
        void detect_earchiveReport() throws Exception {
            byte[] xml = xml("""
                    <EarsivRapor xmlns="http://earsiv.efatura.gov.tr">
                        <RaporNo>R-001</RaporNo>
                    </EarsivRapor>
                    """);

            assertThat(detector.detect(xml)).isEqualTo(DocumentType.EARCHIVE_REPORT);
        }
    }

    // ── e-Defter ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("e-Defter Belgeleri")
    class EDefterDocuments {

        @Test
        @DisplayName("edefter:defterRaporu → EDEFTER_RAPOR")
        void detect_defterRaporu() throws Exception {
            byte[] xml = xml("""
                    <edefter:defterRaporu xmlns:edefter="http://www.edefter.gov.tr">
                        <edefter:raporNo>DR-001</edefter:raporNo>
                    </edefter:defterRaporu>
                    """);

            assertThat(detector.detect(xml)).isEqualTo(DocumentType.EDEFTER_RAPOR);
        }

        @Test
        @DisplayName("edefter:berat + journal_context → EDEFTER_BERAT")
        void detect_defterBerat_journalContext() throws Exception {
            byte[] xml = xml("""
                    <edefter:berat xmlns:edefter="http://www.edefter.gov.tr"
                                   xmlns:xbrli="http://www.xbrl.org/2003/instance">
                        <xbrli:xbrl>
                            <xbrli:context id="journal_context">
                                <xbrli:entity><xbrli:identifier scheme="http://www.edefter.gov.tr">1234567808</xbrli:identifier></xbrli:entity>
                                <xbrli:period><xbrli:instant>2026-01-01</xbrli:instant></xbrli:period>
                            </xbrli:context>
                        </xbrli:xbrl>
                    </edefter:berat>
                    """);

            assertThat(detector.detect(xml)).isEqualTo(DocumentType.EDEFTER_BERAT);
        }

        @Test
        @DisplayName("edefter:berat + ledger_context → EDEFTER_BERAT")
        void detect_defterBerat_ledgerContext() throws Exception {
            byte[] xml = xml("""
                    <edefter:berat xmlns:edefter="http://www.edefter.gov.tr"
                                   xmlns:xbrli="http://www.xbrl.org/2003/instance">
                        <xbrli:xbrl>
                            <xbrli:context id="ledger_context">
                                <xbrli:entity><xbrli:identifier scheme="http://www.edefter.gov.tr">1234567808</xbrli:identifier></xbrli:entity>
                                <xbrli:period><xbrli:instant>2026-01-01</xbrli:instant></xbrli:period>
                            </xbrli:context>
                        </xbrli:xbrl>
                    </edefter:berat>
                    """);

            assertThat(detector.detect(xml)).isEqualTo(DocumentType.EDEFTER_BERAT);
        }

        @Test
        @DisplayName("edefter:berat + assets_context → ENVANTER_BERAT (GIB workaround)")
        void detect_envanterBerat_viaEdefterPrefix() throws Exception {
            byte[] xml = xml("""
                    <edefter:berat xmlns:edefter="http://www.edefter.gov.tr"
                                   xmlns:xbrli="http://www.xbrl.org/2003/instance">
                        <xbrli:xbrl>
                            <xbrli:context id="assets_context">
                                <xbrli:entity><xbrli:identifier scheme="http://www.gib.gov.tr">1234567808</xbrli:identifier></xbrli:entity>
                                <xbrli:period><xbrli:instant>2026-01-01</xbrli:instant></xbrli:period>
                            </xbrli:context>
                        </xbrli:xbrl>
                    </edefter:berat>
                    """);

            assertThat(detector.detect(xml)).isEqualTo(DocumentType.ENVANTER_BERAT);
        }

        @Test
        @DisplayName("edefter:defter + journal_context → EDEFTER_YEVMIYE")
        void detect_defterYevmiye() throws Exception {
            byte[] xml = xml("""
                    <edefter:defter xmlns:edefter="http://www.edefter.gov.tr"
                                    xmlns:xbrli="http://www.xbrl.org/2003/instance">
                        <xbrli:context id="journal_context">
                            <xbrli:entity><xbrli:identifier scheme="http://www.edefter.gov.tr">1234567808</xbrli:identifier></xbrli:entity>
                            <xbrli:period><xbrli:startDate>2018-04-01</xbrli:startDate></xbrli:period>
                        </xbrli:context>
                    </edefter:defter>
                    """);

            assertThat(detector.detect(xml)).isEqualTo(DocumentType.EDEFTER_YEVMIYE);
        }

        @Test
        @DisplayName("edefter:defter + ledger_context → EDEFTER_KEBIR")
        void detect_defterKebir() throws Exception {
            byte[] xml = xml("""
                    <edefter:defter xmlns:edefter="http://www.edefter.gov.tr"
                                    xmlns:xbrli="http://www.xbrl.org/2003/instance">
                        <xbrli:context id="ledger_context">
                            <xbrli:entity><xbrli:identifier scheme="http://www.edefter.gov.tr">1234567808</xbrli:identifier></xbrli:entity>
                            <xbrli:period><xbrli:startDate>2018-04-01</xbrli:startDate></xbrli:period>
                        </xbrli:context>
                    </edefter:defter>
                    """);

            assertThat(detector.detect(xml)).isEqualTo(DocumentType.EDEFTER_KEBIR);
        }
    }

    // ── e-Envanter ──────────────────────────────────────────────────

    @Nested
    @DisplayName("e-Envanter Belgeleri")
    class EnvanterDocuments {

        @Test
        @DisplayName("envanter:defter → ENVANTER_DEFTER")
        void detect_envanterDefter() throws Exception {
            byte[] xml = xml("""
                    <envanter:defter xmlns:envanter="http://www.edefter.gov.tr">
                        <envanter:envanterNo>E-001</envanter:envanterNo>
                    </envanter:defter>
                    """);

            assertThat(detector.detect(xml)).isEqualTo(DocumentType.ENVANTER_DEFTER);
        }

        @Test
        @DisplayName("envanter:berat → ENVANTER_BERAT")
        void detect_envanterBerat() throws Exception {
            byte[] xml = xml("""
                    <envanter:berat xmlns:envanter="http://www.edefter.gov.tr">
                        <envanter:beratNo>EB-001</envanter:beratNo>
                    </envanter:berat>
                    """);

            assertThat(detector.detect(xml)).isEqualTo(DocumentType.ENVANTER_BERAT);
        }
    }

    // ── Hata Durumları ──────────────────────────────────────────────

    @Nested
    @DisplayName("Hata Durumları")
    class ErrorCases {

        @Test
        @DisplayName("Boş içerik → DocumentTypeDetectionException")
        void detect_emptyContent_throwsException() {
            assertThatThrownBy(() -> detector.detect(new byte[0]))
                    .isInstanceOf(DocumentTypeDetectionException.class)
                    .hasMessageContaining("boş");
        }

        @Test
        @DisplayName("null içerik → DocumentTypeDetectionException")
        void detect_nullContent_throwsException() {
            assertThatThrownBy(() -> detector.detect(null))
                    .isInstanceOf(DocumentTypeDetectionException.class)
                    .hasMessageContaining("boş");
        }

        @Test
        @DisplayName("Geçersiz XML → DocumentTypeDetectionException")
        void detect_invalidXml_throwsException() {
            byte[] invalidXml = "bu xml değil".getBytes(StandardCharsets.UTF_8);

            assertThatThrownBy(() -> detector.detect(invalidXml))
                    .isInstanceOf(DocumentTypeDetectionException.class)
                    .hasMessageContaining("parse hatası");
        }

        @Test
        @DisplayName("Tanınmayan namespace → DocumentTypeDetectionException")
        void detect_unknownNamespace_throwsException() {
            byte[] xml = xml("""
                    <root xmlns="http://unknown.namespace.example.com">
                        <child>test</child>
                    </root>
                    """);

            assertThatThrownBy(() -> detector.detect(xml))
                    .isInstanceOf(DocumentTypeDetectionException.class)
                    .hasMessageContaining("tespit edilemedi");
        }

        @Test
        @DisplayName("edefter:defter + bilinmeyen context → DocumentTypeDetectionException")
        void detect_unknownContextId_throwsException() {
            byte[] xml = xml("""
                    <edefter:defter xmlns:edefter="http://www.edefter.gov.tr"
                                    xmlns:xbrli="http://www.xbrl.org/2003/instance">
                        <xbrli:context id="unknown_context">
                            <xbrli:entity><xbrli:identifier>123</xbrli:identifier></xbrli:entity>
                        </xbrli:context>
                    </edefter:defter>
                    """);

            assertThatThrownBy(() -> detector.detect(xml))
                    .isInstanceOf(DocumentTypeDetectionException.class)
                    .hasMessageContaining("tespit edilemedi");
        }
    }

    // ── Helper ──────────────────────────────────────────────────────

    private static byte[] xml(String content) {
        return ("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" + content)
                .getBytes(StandardCharsets.UTF_8);
    }
}

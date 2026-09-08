package io.mersel.services.xslt.infrastructure;

import io.mersel.services.xslt.application.enums.DocumentType;
import io.mersel.services.xslt.application.enums.SchemaValidationType;
import io.mersel.services.xslt.application.enums.SchematronValidationType;
import io.mersel.services.xslt.application.models.DocumentTypeMapping;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("DocumentTypeMapping")
class DocumentTypeMappingTest {

    @Test
    @DisplayName("Tüm belge türleri bir XSD tipi ve dosya yoluna sahip olmalı")
    void allDocumentTypesShouldHaveSchemaMappings() {
        assertThat(DocumentTypeMapping.SCHEMA_MAP).containsKeys(DocumentType.values());
        assertThat(DocumentTypeMapping.XSD_PATH_MAP).containsKeys(DocumentType.values());
    }

    @Test
    @DisplayName("CreditNote belgeleri UBL CreditNote XSD'siyle eşleşmeli")
    void shouldMapCreditNotesToUblCreditNoteSchema() {
        assertThat(DocumentTypeMapping.SCHEMA_MAP)
                .containsEntry(DocumentType.CREDIT_NOTE, SchemaValidationType.CREDIT_NOTE);

        assertThat(DocumentTypeMapping.XSD_PATH_MAP)
                .containsEntry(DocumentType.CREDIT_NOTE,
                        "validator/ubl-tr-package/schema/maindoc/UBL-CreditNote-2.1.xsd");
        assertThat(DocumentTypeMapping.SCHEMATRON_MAP)
                .containsEntry(DocumentType.CREDIT_NOTE, SchematronValidationType.UBLTR_MAIN);
    }

    @Test
    @DisplayName("Farklı paketlerin eArsivRaporu şemaları ayrı dizinlerde tutulmalı")
    void earchiveReportFamiliesShouldUseSeparateSchemaDirectories() {
        var reportSchemaPaths = DocumentTypeMapping.XSD_PATH_MAP.entrySet().stream()
                .filter(entry -> entry.getKey().name().startsWith("EARCHIVE_REPORT"))
                .filter(entry -> entry.getKey() != DocumentType.EARCHIVE_REPORT_EADISYON)
                .map(Map.Entry::getValue)
                .toList();

        // GİB, eArsivRaporu kökünü her üründe farklı bir şemayla yayınlıyor ve dosya adı
        // hepsinde eArsiv.xsd. Aynı dizini paylaşan iki aile birbirinin şemasını ezer.
        assertThat(reportSchemaPaths).hasSize(4).doesNotHaveDuplicates();
        assertThat(reportSchemaPaths.stream().map(path -> path.substring(0, path.lastIndexOf('/'))))
                .doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("Schematron göndermeyen rapor aileleri Schematron adımını atlamalı")
    void reportFamiliesWithoutSchematronShouldBeAbsentFromSchematronMap() {
        // e-Adisyon için genel Schematron uyumsuz; diğer iki özel pakette dosya yok.
        assertThat(DocumentTypeMapping.SCHEMATRON_MAP)
                .doesNotContainKeys(DocumentType.EARCHIVE_REPORT_EADISYON, DocumentType.EARCHIVE_REPORT_EDEKONT,
                        DocumentType.EARCHIVE_REPORT_EGIDER_PUSULASI);
        assertThat(DocumentTypeMapping.getSchematronFileName(DocumentType.EARCHIVE_REPORT_EDEKONT)).isNull();

        assertThat(DocumentTypeMapping.SCHEMATRON_MAP)
                .containsEntry(DocumentType.EARCHIVE_REPORT_EDOVIZ,
                        SchematronValidationType.EARCHIVE_REPORT_EDOVIZ);
    }
}

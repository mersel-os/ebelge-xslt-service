package io.mersel.services.xslt.infrastructure;

import io.mersel.services.xslt.application.models.FileDiffDetail;
import io.mersel.services.xslt.application.models.FileDiffSummary;
import io.mersel.services.xslt.application.models.FileDiffSummary.FileChangeStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class AssetDiffServiceTest {

    private AssetDiffService diffService;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        diffService = new AssetDiffService();
    }

    // ── Directory Diff ──────────────────────────────────────────────

    @Test
    void computeDirectoryDiff_ShouldDetectAddedFiles() throws IOException {
        Path oldDir = tempDir.resolve("old");
        Path newDir = tempDir.resolve("new");
        Files.createDirectories(oldDir);
        Files.createDirectories(newDir);

        Files.writeString(newDir.resolve("added.xml"), "<new>content</new>");

        List<FileDiffSummary> diffs = diffService.computeDirectoryDiff(oldDir, newDir);

        assertThat(diffs).hasSize(1);
        assertThat(diffs.getFirst().status()).isEqualTo(FileChangeStatus.ADDED);
        assertThat(diffs.getFirst().path()).isEqualTo("added.xml");
    }

    @Test
    void computeDirectoryDiff_ShouldDetectRemovedFiles() throws IOException {
        Path oldDir = tempDir.resolve("old");
        Path newDir = tempDir.resolve("new");
        Files.createDirectories(oldDir);
        Files.createDirectories(newDir);

        Files.writeString(oldDir.resolve("removed.xml"), "<old>content</old>");

        List<FileDiffSummary> diffs = diffService.computeDirectoryDiff(oldDir, newDir);

        assertThat(diffs).hasSize(1);
        assertThat(diffs.getFirst().status()).isEqualTo(FileChangeStatus.REMOVED);
    }

    @Test
    void computeDirectoryDiff_ShouldDetectModifiedFiles() throws IOException {
        Path oldDir = tempDir.resolve("old");
        Path newDir = tempDir.resolve("new");
        Files.createDirectories(oldDir);
        Files.createDirectories(newDir);

        Files.writeString(oldDir.resolve("file.xml"), "<root>old</root>");
        Files.writeString(newDir.resolve("file.xml"), "<root>new</root>");

        List<FileDiffSummary> diffs = diffService.computeDirectoryDiff(oldDir, newDir);

        assertThat(diffs).hasSize(1);
        assertThat(diffs.getFirst().status()).isEqualTo(FileChangeStatus.MODIFIED);
    }

    @Test
    void computeDirectoryDiff_ShouldDetectUnchangedFiles() throws IOException {
        Path oldDir = tempDir.resolve("old");
        Path newDir = tempDir.resolve("new");
        Files.createDirectories(oldDir);
        Files.createDirectories(newDir);

        String content = "<root>same</root>";
        Files.writeString(oldDir.resolve("file.xml"), content);
        Files.writeString(newDir.resolve("file.xml"), content);

        List<FileDiffSummary> diffs = diffService.computeDirectoryDiff(oldDir, newDir);

        assertThat(diffs).hasSize(1);
        assertThat(diffs.getFirst().status()).isEqualTo(FileChangeStatus.UNCHANGED);
    }

    @Test
    void computeDirectoryDiff_ShouldHandleSubdirectories() throws IOException {
        Path oldDir = tempDir.resolve("old");
        Path newDir = tempDir.resolve("new");
        Files.createDirectories(oldDir.resolve("sub"));
        Files.createDirectories(newDir.resolve("sub"));

        Files.writeString(oldDir.resolve("sub/file.xml"), "<old/>");
        Files.writeString(newDir.resolve("sub/file.xml"), "<new/>");
        Files.writeString(newDir.resolve("sub/added.xml"), "<added/>");

        List<FileDiffSummary> diffs = diffService.computeDirectoryDiff(oldDir, newDir);

        assertThat(diffs).hasSize(2);
        assertThat(diffs).extracting(FileDiffSummary::status)
                .containsExactlyInAnyOrder(FileChangeStatus.MODIFIED, FileChangeStatus.ADDED);
    }

    // ── File Diff ───────────────────────────────────────────────────

    @Test
    void computeFileDiff_ShouldProduceUnifiedDiff() throws IOException {
        Path oldFile = tempDir.resolve("old.xml");
        Path newFile = tempDir.resolve("new.xml");
        Files.writeString(oldFile, "<root>\n  <old>value</old>\n</root>\n");
        Files.writeString(newFile, "<root>\n  <new>value</new>\n</root>\n");

        FileDiffDetail detail = diffService.computeFileDiff(oldFile, newFile, "test.xml");

        assertThat(detail.status()).isEqualTo(FileChangeStatus.MODIFIED);
        assertThat(detail.unifiedDiff()).contains("-  <old>value</old>");
        assertThat(detail.unifiedDiff()).contains("+  <new>value</new>");
        assertThat(detail.isBinary()).isFalse();
    }

    @Test
    void computeFileDiff_ShouldHandleAddedFile() throws IOException {
        Path newFile = tempDir.resolve("new.xml");
        Files.writeString(newFile, "<root>new</root>");

        FileDiffDetail detail = diffService.computeFileDiff(null, newFile, "new.xml");

        assertThat(detail.status()).isEqualTo(FileChangeStatus.ADDED);
        assertThat(detail.newContent()).contains("<root>new</root>");
    }

    @Test
    void computeFileDiff_ShouldHandleRemovedFile() throws IOException {
        Path oldFile = tempDir.resolve("old.xml");
        Files.writeString(oldFile, "<root>old</root>");

        FileDiffDetail detail = diffService.computeFileDiff(oldFile, null, "old.xml");

        assertThat(detail.status()).isEqualTo(FileChangeStatus.REMOVED);
        assertThat(detail.oldContent()).contains("<root>old</root>");
    }

    // ── Schematron Rule ID Extraction ───────────────────────────────

    @Test
    void extractSchematronRuleIds_ShouldExtractPatternAndAssertIds() throws IOException {
        Path schFile = tempDir.resolve("test.xml");
        Files.writeString(schFile, """
                <?xml version="1.0" encoding="UTF-8"?>
                <schema xmlns="http://purl.oclc.org/dml/schematron">
                  <pattern id="InvoicePattern">
                    <rule context="inv:Invoice" id="InvoiceRule">
                      <assert test="cbc:ID" id="InvoiceIDCheck">ID is required</assert>
                    </rule>
                  </pattern>
                  <pattern id="LinePattern">
                    <rule context="cac:InvoiceLine">
                      <assert test="cbc:LineID" id="LineIDCheck">Line ID required</assert>
                    </rule>
                  </pattern>
                </schema>
                """);

        Set<String> ids = diffService.extractSchematronRuleIds(schFile);

        assertThat(ids).containsExactlyInAnyOrder(
                "InvoicePattern", "InvoiceRule", "InvoiceIDCheck",
                "LinePattern", "LineIDCheck");
    }

    @Test
    void extractSchematronRuleIds_ShouldReturnEmptyForNonExistentFile() {
        Set<String> ids = diffService.extractSchematronRuleIds(tempDir.resolve("nonexistent.xml"));
        assertThat(ids).isEmpty();
    }

    @Test
    void extractSchematronRuleIds_ShouldReturnEmptyForNull() {
        Set<String> ids = diffService.extractSchematronRuleIds(null);
        assertThat(ids).isEmpty();
    }

    // ── Rule ID Diff ────────────────────────────────────────────────

    @Test
    void computeRuleIdDiff_ShouldDetectRemovedIds() throws IOException {
        Path oldFile = tempDir.resolve("old.xml");
        Path newFile = tempDir.resolve("new.xml");
        Files.writeString(oldFile, """
                <schema xmlns="http://purl.oclc.org/dml/schematron">
                  <pattern id="OldPattern"><rule context="/"><assert test="true()" id="OldAssert">ok</assert></rule></pattern>
                  <pattern id="CommonPattern"><rule context="/"><assert test="true()" id="CommonAssert">ok</assert></rule></pattern>
                </schema>
                """);
        Files.writeString(newFile, """
                <schema xmlns="http://purl.oclc.org/dml/schematron">
                  <pattern id="CommonPattern"><rule context="/"><assert test="true()" id="CommonAssert">ok</assert></rule></pattern>
                  <pattern id="NewPattern"><rule context="/"><assert test="true()" id="NewAssert">ok</assert></rule></pattern>
                </schema>
                """);

        AssetDiffService.RuleIdDiff diff = diffService.computeRuleIdDiff(oldFile, newFile);

        assertThat(diff.removed()).containsExactlyInAnyOrder("OldPattern", "OldAssert");
        assertThat(diff.added()).containsExactlyInAnyOrder("NewPattern", "NewAssert");
        assertThat(diff.retained()).containsExactlyInAnyOrder("CommonPattern", "CommonAssert");
        assertThat(diff.hasChanges()).isTrue();
    }

    @Test
    void computeRuleIdDiff_ShouldReportNoChangesForIdenticalFiles() throws IOException {
        Path file = tempDir.resolve("same.xml");
        Files.writeString(file, """
                <schema xmlns="http://purl.oclc.org/dml/schematron">
                  <pattern id="P1"><rule context="/"><assert test="true()" id="A1">ok</assert></rule></pattern>
                </schema>
                """);

        AssetDiffService.RuleIdDiff diff = diffService.computeRuleIdDiff(file, file);

        assertThat(diff.hasChanges()).isFalse();
        assertThat(diff.retained()).containsExactlyInAnyOrder("P1", "A1");
    }
}

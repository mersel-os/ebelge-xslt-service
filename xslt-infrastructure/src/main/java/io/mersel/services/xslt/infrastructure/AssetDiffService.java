package io.mersel.services.xslt.infrastructure;

import com.github.difflib.DiffUtils;
import com.github.difflib.UnifiedDiffUtils;
import com.github.difflib.patch.Patch;
import io.mersel.services.xslt.application.models.FileDiffDetail;
import io.mersel.services.xslt.application.models.FileDiffSummary;
import io.mersel.services.xslt.application.models.FileDiffSummary.FileChangeStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

/**
 * İki dizin veya dosya arasında diff hesaplayan servis.
 * <p>
 * {@code java-diff-utils} kütüphanesi ile satır bazında unified diff üretir.
 * Ayrıca Schematron dosyalarından rule ID'leri çıkartarak
 * suppression etki analizi yapılmasına olanak tanır.
 */
@Service
public class AssetDiffService {

    private static final Logger log = LoggerFactory.getLogger(AssetDiffService.class);

    private static final int CONTEXT_LINES = 3;
    private static final long MAX_DIFF_FILE_SIZE = 10 * 1024 * 1024; // 10 MB

    // ── Directory Diff ──────────────────────────────────────────────

    /**
     * İki dizin arasındaki dosya bazında değişiklikleri hesaplar.
     *
     * @param oldDir Eski dizin (mevcut live asset'ler)
     * @param newDir Yeni dizin (staging'deki dosyalar)
     * @return Dosya bazında değişiklik özetleri
     */
    public List<FileDiffSummary> computeDirectoryDiff(Path oldDir, Path newDir) throws IOException {
        var result = new ArrayList<FileDiffSummary>();

        Set<String> oldFiles = collectRelativeFiles(oldDir);
        Set<String> newFiles = collectRelativeFiles(newDir);

        // Added files (yeni dizinde var, eski dizinde yok)
        for (String path : newFiles) {
            if (!oldFiles.contains(path)) {
                long newSize = Files.size(newDir.resolve(path));
                result.add(new FileDiffSummary(path, FileChangeStatus.ADDED, -1, newSize));
            }
        }

        // Removed files (eski dizinde var, yeni dizinde yok)
        for (String path : oldFiles) {
            if (!newFiles.contains(path)) {
                long oldSize = Files.size(oldDir.resolve(path));
                result.add(new FileDiffSummary(path, FileChangeStatus.REMOVED, oldSize, -1));
            }
        }

        // Modified or Unchanged (her iki dizinde de var)
        for (String path : oldFiles) {
            if (newFiles.contains(path)) {
                Path oldFile = oldDir.resolve(path);
                Path newFile = newDir.resolve(path);
                long oldSize = Files.size(oldFile);
                long newSize = Files.size(newFile);

                if (filesAreEqual(oldFile, newFile)) {
                    result.add(new FileDiffSummary(path, FileChangeStatus.UNCHANGED, oldSize, newSize));
                } else {
                    result.add(new FileDiffSummary(path, FileChangeStatus.MODIFIED, oldSize, newSize));
                }
            }
        }

        result.sort(Comparator.comparing(FileDiffSummary::path));
        return result;
    }

    // ── File Diff ───────────────────────────────────────────────────

    /**
     * İki dosya arasında unified diff üretir.
     *
     * @param oldFile Eski dosya (null ise ADDED)
     * @param newFile Yeni dosya (null ise REMOVED)
     * @param relativePath Diff çıktısında gösterilecek göreceli yol
     * @return Detaylı diff bilgisi
     */
    public FileDiffDetail computeFileDiff(Path oldFile, Path newFile, String relativePath) throws IOException {
        boolean oldExists = oldFile != null && Files.isRegularFile(oldFile);
        boolean newExists = newFile != null && Files.isRegularFile(newFile);

        if (!oldExists && !newExists) {
            return FileDiffDetail.text(relativePath, FileChangeStatus.UNCHANGED, "", "", "");
        }

        // Binary check
        if ((oldExists && isBinaryFile(oldFile)) || (newExists && isBinaryFile(newFile))) {
            FileChangeStatus status = !oldExists ? FileChangeStatus.ADDED
                    : !newExists ? FileChangeStatus.REMOVED : FileChangeStatus.MODIFIED;
            return FileDiffDetail.binary(relativePath, status);
        }

        // File size check
        if ((oldExists && Files.size(oldFile) > MAX_DIFF_FILE_SIZE)
                || (newExists && Files.size(newFile) > MAX_DIFF_FILE_SIZE)) {
            FileChangeStatus status = !oldExists ? FileChangeStatus.ADDED
                    : !newExists ? FileChangeStatus.REMOVED : FileChangeStatus.MODIFIED;
            return FileDiffDetail.binary(relativePath, status);
        }

        List<String> oldLines = oldExists ? Files.readAllLines(oldFile, StandardCharsets.UTF_8) : List.of();
        List<String> newLines = newExists ? Files.readAllLines(newFile, StandardCharsets.UTF_8) : List.of();

        String oldContent = oldExists ? Files.readString(oldFile, StandardCharsets.UTF_8) : "";
        String newContent = newExists ? Files.readString(newFile, StandardCharsets.UTF_8) : "";

        FileChangeStatus status;
        if (!oldExists) {
            status = FileChangeStatus.ADDED;
        } else if (!newExists) {
            status = FileChangeStatus.REMOVED;
        } else if (oldContent.equals(newContent)) {
            status = FileChangeStatus.UNCHANGED;
        } else {
            status = FileChangeStatus.MODIFIED;
        }

        // Compute unified diff
        Patch<String> patch = DiffUtils.diff(oldLines, newLines);
        List<String> unifiedDiff = UnifiedDiffUtils.generateUnifiedDiff(
                "a/" + relativePath, "b/" + relativePath,
                oldLines, patch, CONTEXT_LINES);
        String diffText = String.join("\n", unifiedDiff);

        return FileDiffDetail.text(relativePath, status, diffText, oldContent, newContent);
    }

    // ── Schematron Rule ID Extraction ───────────────────────────────

    /**
     * Schematron dosyasından tüm rule ID'leri çıkartır.
     * <p>
     * {@code sch:pattern}, {@code sch:rule} ve {@code sch:assert} elementlerinin
     * {@code id} attribute'larını toplar.
     *
     * @param schematronFile Schematron XML dosyası
     * @return Bulunan tüm ID'ler (sıralı, unique)
     */
    public Set<String> extractSchematronRuleIds(Path schematronFile) {
        var ids = new TreeSet<String>();

        if (schematronFile == null || !Files.isRegularFile(schematronFile)) {
            return ids;
        }

        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(schematronFile.toFile());

            extractIdsFromElements(doc, "pattern", ids);
            extractIdsFromElements(doc, "rule", ids);
            extractIdsFromElements(doc, "assert", ids);
            extractIdsFromElements(doc, "report", ids);

        } catch (Exception e) {
            log.warn("Schematron rule ID'leri çıkartılamadı: {} — {}", schematronFile, e.getMessage());
        }

        return ids;
    }

    /**
     * İki Schematron dosyası arasındaki rule ID değişikliklerini hesaplar.
     *
     * @param oldFile Eski Schematron dosyası
     * @param newFile Yeni Schematron dosyası
     * @return Kaldırılan, eklenen ve korunan ID setleri
     */
    public RuleIdDiff computeRuleIdDiff(Path oldFile, Path newFile) {
        Set<String> oldIds = extractSchematronRuleIds(oldFile);
        Set<String> newIds = extractSchematronRuleIds(newFile);

        Set<String> removed = new TreeSet<>(oldIds);
        removed.removeAll(newIds);

        Set<String> added = new TreeSet<>(newIds);
        added.removeAll(oldIds);

        Set<String> retained = new TreeSet<>(oldIds);
        retained.retainAll(newIds);

        return new RuleIdDiff(removed, added, retained);
    }

    /**
     * İki Schematron seti arasındaki rule ID değişiklikleri.
     */
    public record RuleIdDiff(
            Set<String> removed,
            Set<String> added,
            Set<String> retained
    ) {
        public boolean hasChanges() {
            return !removed.isEmpty() || !added.isEmpty();
        }
    }

    // ── Internal ────────────────────────────────────────────────────

    private Set<String> collectRelativeFiles(Path dir) throws IOException {
        if (dir == null || !Files.isDirectory(dir)) {
            return Set.of();
        }
        var files = new TreeSet<String>();
        try (Stream<Path> stream = Files.walk(dir)) {
            stream.filter(Files::isRegularFile)
                    .forEach(path -> files.add(dir.relativize(path).toString().replace('\\', '/')));
        }
        return files;
    }

    private boolean filesAreEqual(Path a, Path b) throws IOException {
        long sizeA = Files.size(a);
        long sizeB = Files.size(b);
        if (sizeA != sizeB) return false;
        return Arrays.equals(Files.readAllBytes(a), Files.readAllBytes(b));
    }

    private boolean isBinaryFile(Path file) {
        try {
            byte[] bytes = Files.readAllBytes(file);
            int checkLength = Math.min(bytes.length, 8192);
            for (int i = 0; i < checkLength; i++) {
                byte b = bytes[i];
                if (b == 0) return true;
            }
            return false;
        } catch (IOException e) {
            return true;
        }
    }

    private void extractIdsFromElements(Document doc, String localName, Set<String> ids) {
        NodeList nodes = doc.getElementsByTagNameNS("*", localName);
        for (int i = 0; i < nodes.getLength(); i++) {
            Element el = (Element) nodes.item(i);
            String id = el.getAttribute("id");
            if (id != null && !id.isBlank()) {
                ids.add(id);
            }
        }
    }
}

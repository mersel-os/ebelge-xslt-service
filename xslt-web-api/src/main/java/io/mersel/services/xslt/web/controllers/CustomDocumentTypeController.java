package io.mersel.services.xslt.web.controllers;

import io.mersel.services.xslt.application.interfaces.ICustomDocumentTypeRegistry;
import io.mersel.services.xslt.application.models.CustomDocumentType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Comparator;
import java.util.List;

/**
 * Asset dizinindeki {@code custom-document-types.yml} ile yüklenen özel belge türlerini listeler.
 * <p>
 * Yönetim arayüzü bu listeyi profil düzenleyicide bastırma kapsamı ve XSD override tipi
 * seçeneği olarak gösterir. Liste her istekte o anki tanımlardan üretilir; tanım dosyası
 * değişip yeniden yüklendiğinde ek bir işlem gerekmez.
 */
@RestController
@RequestMapping("/v1/admin/custom-document-types")
@Tag(name = "Admin", description = "Asset yönetimi, yeniden yükleme, profil yönetimi ve GİB paket sync")
public class CustomDocumentTypeController {

    private final ICustomDocumentTypeRegistry registry;

    public CustomDocumentTypeController(ICustomDocumentTypeRegistry registry) {
        this.registry = registry;
    }

    @GetMapping
    @Operation(
            summary = "Özel belge türlerini listele",
            description = "custom-document-types.yml dosyasından yüklenmiş türleri döner. "
                    + "Tür adları profil bastırma kapsamında ve xsd-overrides anahtarı olarak kullanılabilir."
    )
    public ResponseEntity<CustomDocumentTypesResponse> list() {
        var types = registry.getAll().stream()
                .sorted(Comparator.comparing(CustomDocumentType::id))
                .map(CustomDocumentTypeDto::from)
                .toList();
        return ResponseEntity.ok(new CustomDocumentTypesResponse(types));
    }

    public record CustomDocumentTypesResponse(List<CustomDocumentTypeDto> types) {
    }

    /**
     * @param schematron Uygulanan yerleşik Schematron tipinin adı; Schematron atlanıyorsa {@code null}
     */
    public record CustomDocumentTypeDto(
            String id,
            String description,
            String rootNamespace,
            String rootElement,
            String xsd,
            String schematron
    ) {
        static CustomDocumentTypeDto from(CustomDocumentType type) {
            return new CustomDocumentTypeDto(
                    type.id(),
                    type.description(),
                    type.rootNamespace(),
                    type.rootElement(),
                    type.xsdPath(),
                    type.schematronType() != null ? type.schematronType().name() : null);
        }
    }
}

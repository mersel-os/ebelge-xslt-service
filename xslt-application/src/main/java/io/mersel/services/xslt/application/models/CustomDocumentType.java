package io.mersel.services.xslt.application.models;

import io.mersel.services.xslt.application.enums.SchematronValidationType;

/**
 * Servisi çalıştıran tarafından tanımlanan, yerleşik olmayan bir belge türü.
 * <p>
 * Tanımlar asset dizinindeki {@code custom-document-types.yml} dosyasından okunur.
 * Kök elementi yerleşik tespit kurallarıyla tanınmayan bir belge, namespace ve yerel adı
 * bir tanımla eşleşirse bu türle doğrulanır.
 *
 * @param id             Türün adı; yanıtta {@code detectedDocumentType}, profil {@code scope} ve
 *                       {@code xsd-overrides} anahtarı olarak kullanılır
 * @param description    Opsiyonel açıklama
 * @param rootNamespace  Kök elementin namespace URI'si
 * @param rootElement    Kök elementin yerel adı (prefix'siz)
 * @param xsdPath        Ana XSD'nin asset dizinine göreli yolu; göreceli {@code xs:import} /
 *                       {@code xs:include} referansları bu dosyanın konumuna göre çözülür
 * @param schematronType Uygulanacak yerleşik Schematron tipi; {@code null} ise Schematron atlanır
 */
public record CustomDocumentType(
        String id,
        String description,
        String rootNamespace,
        String rootElement,
        String xsdPath,
        SchematronValidationType schematronType
) {
}

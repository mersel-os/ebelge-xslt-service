package io.mersel.services.xslt.application.interfaces;

import io.mersel.services.xslt.application.models.CustomDocumentType;

import java.util.List;
import java.util.Optional;

/**
 * Asset dizininden yüklenen özel belge türü tanımları.
 * <p>
 * Yerleşik tespit kuralları önceliklidir: bir tanım yalnızca kök elementi servis
 * tarafından tanınmayan belgelerde devreye girer.
 */
public interface ICustomDocumentTypeRegistry {

    /**
     * Kök elementin namespace ve yerel adıyla eşleşen tanımı döndürür.
     */
    Optional<CustomDocumentType> findByRoot(String namespace, String localName);

    /**
     * Adıyla tanımı döndürür.
     */
    Optional<CustomDocumentType> findById(String id);

    /**
     * Yüklü tüm tanımlar.
     */
    List<CustomDocumentType> getAll();
}

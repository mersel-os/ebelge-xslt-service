package io.mersel.services.xslt.application.interfaces;

/**
 * XML belge türü tespit edilemediğinde fırlatılan istisna.
 * <p>
 * Tanınmayan namespace, geçersiz XML yapısı veya eksik bilgi
 * durumlarında kullanılır. Kök element okunabildiyse namespace ve yerel adı
 * taşınır; özel belge türü tanımları bu bilgiyle eşleştirilir.
 */
public class DocumentTypeDetectionException extends Exception {

    private final String rootNamespace;
    private final String rootLocalName;

    public DocumentTypeDetectionException(String message) {
        this(message, null, null);
    }

    public DocumentTypeDetectionException(String message, Throwable cause) {
        super(message, cause);
        this.rootNamespace = null;
        this.rootLocalName = null;
    }

    /**
     * Kök element okunduğu halde yerleşik bir türle eşleşmediğinde kullanılır.
     */
    public DocumentTypeDetectionException(String message, String rootNamespace, String rootLocalName) {
        super(message);
        this.rootNamespace = rootNamespace;
        this.rootLocalName = rootLocalName;
    }

    /**
     * Kök elementin namespace URI'si; kök okunamadıysa {@code null}.
     */
    public String getRootNamespace() {
        return rootNamespace;
    }

    /**
     * Kök elementin yerel adı; kök okunamadıysa {@code null}.
     */
    public String getRootLocalName() {
        return rootLocalName;
    }
}

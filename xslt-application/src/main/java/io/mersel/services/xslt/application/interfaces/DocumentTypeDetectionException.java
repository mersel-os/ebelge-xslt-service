package io.mersel.services.xslt.application.interfaces;

/**
 * XML belge türü tespit edilemediğinde fırlatılan istisna.
 * <p>
 * Tanınmayan namespace, geçersiz XML yapısı veya eksik bilgi
 * durumlarında kullanılır.
 */
public class DocumentTypeDetectionException extends Exception {

    public DocumentTypeDetectionException(String message) {
        super(message);
    }

    public DocumentTypeDetectionException(String message, Throwable cause) {
        super(message, cause);
    }
}

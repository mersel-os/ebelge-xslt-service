package io.mersel.services.xslt.application.interfaces;

import io.mersel.services.xslt.application.enums.DocumentType;

/**
 * XML belge içeriğinden belge türünü otomatik tespit eden servis arayüzü.
 * <p>
 * Namespace URI, root element adı ve namespace prefix bilgilerini kullanarak
 * belge türünü ({@link DocumentType}) belirler.
 * e-Defter yevmiye/kebir ayrımı için {@code xbrli:context id} attribute'ı da incelenir.
 */
public interface IDocumentTypeDetector {

    /**
     * XML byte içeriğinden belge türünü tespit eder.
     *
     * @param xmlContent doğrulanacak XML belgesinin byte dizisi
     * @return tespit edilen belge türü
     * @throws DocumentTypeDetectionException belge türü tespit edilemezse
     */
    DocumentType detect(byte[] xmlContent) throws DocumentTypeDetectionException;
}

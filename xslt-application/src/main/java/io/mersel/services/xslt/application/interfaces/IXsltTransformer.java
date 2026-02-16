package io.mersel.services.xslt.application.interfaces;

import io.mersel.services.xslt.application.models.TransformRequest;
import io.mersel.services.xslt.application.models.TransformResult;

/**
 * XSLT dönüşüm servisi arayüzü.
 * <p>
 * XML belgelerini XSLT şablonları ile HTML'e dönüştürür.
 */
public interface IXsltTransformer {

    /**
     * XML belgesini belirtilen XSLT şablonu ile HTML'e dönüştürür.
     * <p>
     * Kullanıcı özel XSLT sağladıysa önce o denenir. Başarısız olursa
     * varsayılan şablona otomatik geri dönülür ve {@link TransformResult#getCustomXsltError()}
     * ile bilgilendirilir.
     *
     * @param request Dönüşüm isteği (belge, tip, opsiyonel özel XSLT, filigran metni)
     * @return Dönüşüm sonucu (HTML içeriği + metadata)
     * @throws TransformException Dönüşüm tamamen başarısız olduğunda (hiçbir şablon çalışmadı)
     */
    TransformResult transform(TransformRequest request) throws TransformException;

    /**
     * Dönüşüm başarısız olduğunda fırlatılan istisna.
     * Controller bu istisnayı {@code 422 Unprocessable Entity} olarak çevirir.
     */
    class TransformException extends Exception {
        public TransformException(String message) {
            super(message);
        }

        public TransformException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}

package io.mersel.services.xslt.application.models;

/**
 * Genel servis yanıt zarfı.
 * <p>
 * Validation endpoint'i gibi yapısal JSON yanıtlar için kullanılır.
 *
 * @param <T> Sonuç tipi
 */
public class XsltServiceResponse<T> {

    private String errorMessage;
    private T result;

    public XsltServiceResponse() {
    }

    public XsltServiceResponse(T result) {
        this.result = result;
    }

    public XsltServiceResponse(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public static <T> XsltServiceResponse<T> success(T result) {
        return new XsltServiceResponse<>(result);
    }

    public static <T> XsltServiceResponse<T> error(String errorMessage) {
        return new XsltServiceResponse<>(errorMessage);
    }

    public boolean hasError() {
        return errorMessage != null && !errorMessage.isBlank();
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public T getResult() {
        return result;
    }

    public void setResult(T result) {
        this.result = result;
    }
}

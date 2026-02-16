package io.mersel.services.xslt.application.models;

/**
 * Tek bir XSD element override tanımı.
 * <p>
 * Orijinal GIB XSD dosyalarındaki {@code minOccurs} / {@code maxOccurs}
 * değerlerini profil bazlı olarak değiştirmek için kullanılır.
 * <p>
 * Eşleşme, XSD dosyasındaki {@code <xsd:element ref="...">} node'unun
 * {@code ref} attribute değerine göre yapılır (örn: "cac:Signature").
 *
 * @param element   XSD element ref değeri (örn: "cac:Signature", "cbc:UUID")
 * @param minOccurs Yeni minOccurs değeri (null ise değiştirilmez)
 * @param maxOccurs Yeni maxOccurs değeri (null ise değiştirilmez)
 */
public record XsdOverride(
        String element,
        String minOccurs,
        String maxOccurs
) {
}

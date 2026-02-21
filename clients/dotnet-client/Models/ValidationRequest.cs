namespace MERSEL.Services.XsltService.Client.Models;

public sealed class ValidationRequest
{
    public byte[] Source { get; set; } = Array.Empty<byte>();

    public string? Profile { get; set; }

    public IReadOnlyCollection<string> Suppressions { get; set; } = Array.Empty<string>();

    /// <summary>
    /// Schematron XSLT parametreleri. Doğrulama sırasında Schematron XSLT'sine xsl:param olarak geçirilir.
    /// Özel şematron kurallarında $parametre_adi şeklinde tanımlanan değişkenleri doldurmak için kullanılır.
    /// </summary>
    public IReadOnlyCollection<SchematronParameter> Parameters { get; set; } = Array.Empty<SchematronParameter>();
}

/// <summary>
/// Schematron doğrulama parametresi (key/value çifti).
/// </summary>
public sealed class SchematronParameter
{
    public string Key { get; set; } = string.Empty;

    public string Value { get; set; } = string.Empty;

    public SchematronParameter() { }

    public SchematronParameter(string key, string value)
    {
        Key = key;
        Value = value;
    }
}

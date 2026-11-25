namespace MERSEL.Services.XsltService.Client.Models;

public sealed class TransformResponse
{
    public string HtmlContent { get; set; } = string.Empty;

    public bool DefaultXsltUsed { get; set; }

    public bool EmbeddedXsltUsed { get; set; }

    /// <summary>
    /// Özel veya gömülü XSLT başarısız olup varsayılan şablona dönüldüğünde hata mesajı
    /// (<c>X-Xslt-Custom-Error</c>). Hata yoksa <c>null</c>.
    /// Sunucu değeri en fazla 500 karaktere keser ve ISO-8859-1'e indirir:
    /// ü/ç/ö korunur; ı/ş/ğ/İ "i"/"s"/"g"/"I", uzun tire "-" olarak gelir.
    /// </summary>
    public string? CustomXsltError { get; set; }

    public int DurationMs { get; set; }

    public bool WatermarkApplied { get; set; }

    public int OutputSize { get; set; }

    /// <summary>
    /// Sanitization sırasında kaldırılan script sayısı.
    /// 0 ise hiçbir script güvenlik nedeniyle kaldırılmamış demektir.
    /// </summary>
    public int ScriptsRemoved { get; set; }

    /// <summary>
    /// Tespit edilen güvenlik ihlalleri listesi.
    /// Boşsa ihlal yok demektir. XSLT kaynağının güvenilirliğini
    /// değerlendirmek için kullanılabilir.
    /// Örnek: ["cookie access", "fetch API network call"]
    /// Metin <see cref="CustomXsltError"/> ile aynı ISO-8859-1 kurallarıyla gelir
    /// (örn. "Harici script kaynagi engellendi: ...").
    /// </summary>
    public IReadOnlyList<string> SecurityViolations { get; set; } = Array.Empty<string>();

    /// <summary>
    /// Herhangi bir güvenlik ihlali tespit edilip edilmediğini gösterir.
    /// </summary>
    public bool HasSecurityViolations => SecurityViolations.Count > 0;
}

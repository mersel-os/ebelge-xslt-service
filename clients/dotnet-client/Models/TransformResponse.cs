namespace MERSEL.Services.XsltService.Client.Models;

public sealed class TransformResponse
{
    public string HtmlContent { get; set; } = string.Empty;

    public bool DefaultXsltUsed { get; set; }

    public bool EmbeddedXsltUsed { get; set; }

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
    /// </summary>
    public IReadOnlyList<string> SecurityViolations { get; set; } = Array.Empty<string>();

    /// <summary>
    /// Herhangi bir güvenlik ihlali tespit edilip edilmediğini gösterir.
    /// </summary>
    public bool HasSecurityViolations => SecurityViolations.Count > 0;
}

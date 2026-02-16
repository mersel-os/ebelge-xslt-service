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
}

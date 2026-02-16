using MERSEL.Services.XsltService.Client.Enums;

namespace MERSEL.Services.XsltService.Client.Models;

public sealed class TransformRequest
{
    public TransformType TransformType { get; set; }

    public byte[] Document { get; set; } = Array.Empty<byte>();

    public byte[]? Transformer { get; set; }

    public string? WatermarkText { get; set; }

    public bool? UseEmbeddedXslt { get; set; }
}

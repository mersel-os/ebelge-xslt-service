namespace MERSEL.Services.XsltService.Client.Models;

public sealed class ValidationRequest
{
    public byte[] Source { get; set; } = Array.Empty<byte>();

    public string? UblTrMainSchematronType { get; set; }

    public string? Profile { get; set; }

    public IReadOnlyCollection<string> Suppressions { get; set; } = Array.Empty<string>();
}

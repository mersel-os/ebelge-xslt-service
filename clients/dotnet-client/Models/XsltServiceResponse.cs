using System.Text.Json.Serialization;

namespace MERSEL.Services.XsltService.Client.Models;

public sealed class XsltServiceResponse<T>
{
    [JsonPropertyName("errorMessage")]
    public string? ErrorMessage { get; set; }

    [JsonPropertyName("result")]
    public T? Result { get; set; }
}

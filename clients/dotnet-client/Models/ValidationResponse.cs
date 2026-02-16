using System.Text.Json.Serialization;
using System.Text.Json;

namespace MERSEL.Services.XsltService.Client.Models;

public sealed class ValidationResponse
{
    [JsonPropertyName("detectedDocumentType")]
    public string? DetectedDocumentType { get; set; }

    [JsonPropertyName("appliedXsd")]
    public string? AppliedXsd { get; set; }

    [JsonPropertyName("appliedSchematron")]
    public string? AppliedSchematron { get; set; }

    [JsonPropertyName("appliedXsdPath")]
    public string? AppliedXsdPath { get; set; }

    [JsonPropertyName("appliedSchematronPath")]
    public string? AppliedSchematronPath { get; set; }

    [JsonPropertyName("validSchema")]
    public bool ValidSchema { get; set; }

    [JsonPropertyName("validSchematron")]
    public bool ValidSchematron { get; set; }

    [JsonPropertyName("schemaValidationErrors")]
    public List<string> SchemaValidationErrors { get; set; } = new();

    [JsonPropertyName("schematronValidationErrors")]
    public List<SchematronValidationError> SchematronValidationErrors { get; set; } = new();

    [JsonPropertyName("suppressionInfo")]
    public Dictionary<string, JsonElement>? SuppressionInfo { get; set; }
}

public sealed class SchematronValidationError
{
    [JsonPropertyName("ruleId")]
    public string? RuleId { get; set; }

    [JsonPropertyName("test")]
    public string? Test { get; set; }

    [JsonPropertyName("message")]
    public string? Message { get; set; }
}

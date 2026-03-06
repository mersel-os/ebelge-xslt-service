# MERSEL.Services.XsltService.Client

`MERSEL.Services.XsltService` mikroservisinin `validate` ve `transform` endpointlerini HTTP ile çağıran .NET istemci paketidir.

## Install

```bash
dotnet add package MERSEL.Services.XsltService.Client
```

## DI Registration

```csharp
// appsettings.json -> Services:XsltService:BaseUrl
builder.Services.AddXsltServiceClient(builder.Configuration);

// or direct URL
builder.Services.AddXsltServiceClient("http://localhost:8080");
```

## appsettings.json

```json
{
  "Services": {
    "XsltService": {
      "BaseUrl": "http://localhost:8080"
    }
  }
}
```

## Usage

```csharp
using MERSEL.Services.XsltService.Client.Interfaces;
using MERSEL.Services.XsltService.Client.Models;

public sealed class InvoiceService(IXsltServiceClient xsltClient)
{
    public async Task<ValidationResponse?> ValidateAsync(byte[] xml, CancellationToken ct)
    {
        var response = await xsltClient.ValidateAsync(new ValidationRequest
        {
            Source = xml,
            Profile = "unsigned",
            Suppressions = new[] { "InvoiceIDCheck" },
            Parameters = new[] { new SchematronParameter("type", "TEMELFATURA") }
        }, ct);

        return response.Result;
    }

    public async Task<string> TransformAsync(byte[] xml, CancellationToken ct)
    {
        var transformed = await xsltClient.TransformAsync(new TransformRequest
        {
            Document = xml,
            TransformType = TransformType.INVOICE,
            UseEmbeddedXslt = true
        }, ct);

        return transformed.HtmlContent;
    }
}
```

## Security Violation Detection

Transform yanıtlarında sunucu tarafında otomatik HTML sanitization uygulanır. Exfiltration riski taşıyan scriptler kaldırılır ve detayları response header'larından okunur.

```csharp
var result = await xsltClient.TransformAsync(new TransformRequest
{
    Document = xml,
    TransformType = TransformType.INVOICE
}, ct);

// Güvenlik ihlali var mı?
if (result.HasSecurityViolations)
{
    logger.LogWarning(
        "{Count} script kaldırıldı. İhlaller: {Violations}",
        result.ScriptsRemoved,
        string.Join("; ", result.SecurityViolations));
}
```

| Property | Tip | Açıklama |
|----------|-----|----------|
| `ScriptsRemoved` | `int` | Kaldırılan script sayısı |
| `SecurityViolations` | `IReadOnlyList<string>` | İhlal detay listesi |
| `HasSecurityViolations` | `bool` | `SecurityViolations.Count > 0` |

Olası violation mesajları: `cookie access`, `domain manipulation`, `localStorage access`, `sessionStorage access`, `redirect/exfiltration via location`, `window.open exfiltration`, `XHR network call`, `fetch API network call`, `sendBeacon exfiltration`, `WebSocket connection`, `cross-origin messaging`, `dynamic code execution (eval)`, `dynamic code execution (Function constructor)`, `dynamic module import`, `UI blocking dialog (alert)`, `UI blocking dialog (confirm)`, `UI blocking dialog (prompt)`.

## Targets

- `net6.0`
- `net7.0`
- `net8.0`
- `net9.0`

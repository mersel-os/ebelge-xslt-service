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
            UblTrMainSchematronType = "efatura",
            Profile = "unsigned",
            Suppressions = new[] { "InvoiceIDCheck" }
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

## Targets

- `net6.0`
- `net7.0`
- `net8.0`
- `net9.0`

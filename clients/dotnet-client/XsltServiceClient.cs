using System.Net.Http.Headers;
using System.Net.Http.Json;
using MERSEL.Services.XsltService.Client.Interfaces;
using MERSEL.Services.XsltService.Client.Models;

namespace MERSEL.Services.XsltService.Client;

public sealed class XsltServiceClient : IXsltServiceClient
{
    private readonly HttpClient _httpClient;

    public XsltServiceClient(IHttpClientFactory httpClientFactory)
    {
        _httpClient = httpClientFactory.CreateClient("XsltService");
    }

    public async Task<XsltServiceResponse<ValidationResponse>> ValidateAsync(ValidationRequest request, CancellationToken ct = default)
    {
        if (request.Source.Length == 0)
        {
            throw new ArgumentException("Validation source cannot be empty.", nameof(request));
        }

        using var form = new MultipartFormDataContent();

        if (!string.IsNullOrWhiteSpace(request.UblTrMainSchematronType))
        {
            form.Add(new StringContent(request.UblTrMainSchematronType), "ublTrMainSchematronType");
        }

        if (!string.IsNullOrWhiteSpace(request.Profile))
        {
            form.Add(new StringContent(request.Profile), "profile");
        }

        var suppressions = request.Suppressions
            .Where(static x => !string.IsNullOrWhiteSpace(x))
            .ToArray();

        if (suppressions.Length > 0)
        {
            form.Add(new StringContent(string.Join(",", suppressions)), "suppressions");
        }

        using var source = new ByteArrayContent(request.Source);
        source.Headers.ContentType = MediaTypeHeaderValue.Parse("application/xml");
        form.Add(source, "source", $"{Guid.NewGuid()}.xml");

        using var response = await _httpClient.PostAsync("/v1/validate", form, ct).ConfigureAwait(false);
        response.EnsureSuccessStatusCode();

        return await response.Content.ReadFromJsonAsync<XsltServiceResponse<ValidationResponse>>(cancellationToken: ct).ConfigureAwait(false)
               ?? new XsltServiceResponse<ValidationResponse>
               {
                   ErrorMessage = "Failed to deserialize validate response."
               };
    }

    public async Task<TransformResponse> TransformAsync(TransformRequest request, CancellationToken ct = default)
    {
        if (request.Document.Length == 0)
        {
            throw new ArgumentException("Transform document cannot be empty.", nameof(request));
        }

        using var form = new MultipartFormDataContent();
        form.Add(new StringContent(request.TransformType.ToString()), "transformType");

        if (!string.IsNullOrWhiteSpace(request.WatermarkText))
        {
            form.Add(new StringContent(request.WatermarkText), "watermarkText");
        }

        if (request.UseEmbeddedXslt.HasValue)
        {
            form.Add(
                new StringContent(request.UseEmbeddedXslt.Value.ToString().ToLowerInvariant()),
                "useEmbeddedXslt");
        }

        using var document = new ByteArrayContent(request.Document);
        document.Headers.ContentType = MediaTypeHeaderValue.Parse("application/xml");
        form.Add(document, "document", $"{Guid.NewGuid()}.xml");

        if (request.Transformer is not null && request.Transformer.Length > 0)
        {
            using var transformer = new ByteArrayContent(request.Transformer);
            transformer.Headers.ContentType = MediaTypeHeaderValue.Parse("application/xml");
            form.Add(transformer, "transformer", $"{Guid.NewGuid()}.xslt");
        }

        using var response = await _httpClient.PostAsync("/v1/transform", form, ct).ConfigureAwait(false);
        response.EnsureSuccessStatusCode();

        return new TransformResponse
        {
            HtmlContent = await response.Content.ReadAsStringAsync(ct).ConfigureAwait(false),
            DefaultXsltUsed = ReadBoolHeader(response, "X-Xslt-Default-Used"),
            EmbeddedXsltUsed = ReadBoolHeader(response, "X-Xslt-Embedded-Used"),
            CustomXsltError = ReadStringHeader(response, "X-Xslt-Custom-Error"),
            DurationMs = ReadIntHeader(response, "X-Xslt-Duration-Ms"),
            WatermarkApplied = ReadBoolHeader(response, "X-Xslt-Watermark-Applied"),
            OutputSize = ReadIntHeader(response, "X-Xslt-Output-Size")
        };
    }

    public XsltServiceResponse<ValidationResponse> Validate(ValidationRequest request)
    {
        return ValidateAsync(request).ConfigureAwait(false).GetAwaiter().GetResult();
    }

    public TransformResponse Transform(TransformRequest request)
    {
        return TransformAsync(request).ConfigureAwait(false).GetAwaiter().GetResult();
    }

    private static bool ReadBoolHeader(HttpResponseMessage response, string headerName)
    {
        var raw = ReadStringHeader(response, headerName);
        return bool.TryParse(raw, out var parsed) && parsed;
    }

    private static int ReadIntHeader(HttpResponseMessage response, string headerName)
    {
        var raw = ReadStringHeader(response, headerName);
        return int.TryParse(raw, out var parsed) ? parsed : 0;
    }

    private static string? ReadStringHeader(HttpResponseMessage response, string headerName)
    {
        if (!response.Headers.TryGetValues(headerName, out var values))
        {
            return null;
        }

        return values.FirstOrDefault();
    }
}

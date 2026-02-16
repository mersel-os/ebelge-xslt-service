using MERSEL.Services.XsltService.Client.Models;

namespace MERSEL.Services.XsltService.Client.Interfaces;

public interface IXsltServiceClient
{
    Task<XsltServiceResponse<ValidationResponse>> ValidateAsync(ValidationRequest request, CancellationToken ct = default);

    Task<TransformResponse> TransformAsync(TransformRequest request, CancellationToken ct = default);

    XsltServiceResponse<ValidationResponse> Validate(ValidationRequest request);

    TransformResponse Transform(TransformRequest request);
}

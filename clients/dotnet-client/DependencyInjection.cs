using MERSEL.Services.XsltService.Client.Interfaces;
using Microsoft.Extensions.Configuration;
using Microsoft.Extensions.DependencyInjection;

namespace MERSEL.Services.XsltService.Client;

public static class DependencyInjection
{
    public static IServiceCollection AddXsltServiceClient(
        this IServiceCollection services,
        IConfiguration configuration)
    {
        var baseUrl = configuration["Services:XsltService:BaseUrl"]
                      ?? "http://localhost:8080";

        return services.AddXsltServiceClient(baseUrl);
    }

    public static IServiceCollection AddXsltServiceClient(
        this IServiceCollection services,
        string baseUrl)
    {
        services.AddHttpClient("XsltService", client =>
        {
            client.BaseAddress = new Uri(baseUrl);
            client.Timeout = TimeSpan.FromMinutes(5);
        });

        services.AddTransient<IXsltServiceClient, XsltServiceClient>();

        return services;
    }
}

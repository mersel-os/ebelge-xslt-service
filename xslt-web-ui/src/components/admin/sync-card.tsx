import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Download, Loader2, CheckCircle2, XCircle, Info } from "lucide-react";
import { useSyncPackages } from "@/api/hooks";
import { toast } from "sonner";

export function SyncCard() {
  const syncMutation = useSyncPackages();

  const handleSync = (packageId?: string) => {
    syncMutation.mutate(packageId, {
      onSuccess: (data) => {
        if (!data.enabled) {
          toast.info("GİB sync devre dışı", { description: data.message });
        } else {
          toast.success("GİB paketleri sync edildi", {
            description: `${data.successCount}/${data.totalCount} başarılı, ${data.totalDurationMs} ms`,
          });
        }
      },
      onError: (error) => {
        toast.error("Sync başarısız", {
          description:
            error instanceof Error ? error.message : "Bilinmeyen hata",
        });
      },
    });
  };

  const result = syncMutation.data;

  return (
    <div className="rounded-xl border bg-card shadow-sm overflow-hidden">
      <div className="p-6 pb-5">
        <div className="flex items-start justify-between gap-4">
          <div>
            <div className="flex h-10 w-10 items-center justify-center rounded-lg bg-primary/10 text-primary mb-3">
              <Download className="h-5 w-5" />
            </div>
            <h3 className="text-base font-bold">GİB Paket Sync</h3>
            <p className="text-xs text-muted-foreground mt-1">
              GİB resmi paketlerini indir ve yerleştir
            </p>
          </div>
          <Button
            onClick={() => handleSync()}
            disabled={syncMutation.isPending}
            size="sm"
            className="h-9 rounded-lg shrink-0 shadow-sm"
          >
            {syncMutation.isPending ? (
              <Loader2 className="mr-2 h-4 w-4 animate-spin" />
            ) : (
              <Download className="mr-2 h-4 w-4" />
            )}
            Sync Et
          </Button>
        </div>
      </div>

      {result && (
        <div className="border-t bg-muted/20 p-5">
          {!result.enabled ? (
            <div className="flex items-start gap-3 rounded-lg bg-card border p-4">
              <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-primary/10">
                <Info className="h-4 w-4 text-primary" />
              </div>
              <div className="text-xs space-y-1">
                <p className="font-bold">GİB sync devre dışı</p>
                <p className="text-muted-foreground">{result.message}</p>
                {result.currentAssetSource && (
                  <p className="text-muted-foreground">
                    Asset kaynağı:{" "}
                    <code className="font-mono text-foreground bg-muted px-1.5 py-0.5 rounded-md">
                      {result.currentAssetSource}
                    </code>
                  </p>
                )}
              </div>
            </div>
          ) : (
            <div className="space-y-3">
              <div className="flex items-center gap-3 text-xs text-muted-foreground">
                <Badge variant="outline" className="rounded-lg font-mono">
                  {result.successCount}/{result.totalCount}
                </Badge>
                <Badge variant="outline" className="rounded-lg font-mono">
                  {result.totalDurationMs}ms
                </Badge>
              </div>
              <div className="space-y-2">
                {result.packages?.map((pkg) => (
                  <div
                    key={pkg.packageId}
                    className="flex items-center justify-between rounded-lg bg-card border px-4 py-3"
                  >
                    <div className="flex items-center gap-3 min-w-0">
                      {pkg.success ? (
                        <CheckCircle2 className="h-4 w-4 text-success shrink-0" />
                      ) : (
                        <XCircle className="h-4 w-4 text-destructive shrink-0" />
                      )}
                      <div className="min-w-0">
                        <span className="text-sm font-medium block truncate">
                          {pkg.displayName}
                        </span>
                        {pkg.error && (
                          <span className="text-[10px] text-destructive block mt-0.5">
                            {pkg.error}
                          </span>
                        )}
                      </div>
                    </div>
                    <div className="flex items-center gap-2 shrink-0">
                      <Badge
                        variant="secondary"
                        className="text-[10px] font-mono rounded-md"
                      >
                        {pkg.filesExtracted}
                      </Badge>
                      <span className="text-[10px] text-muted-foreground font-mono tabular-nums">
                        {pkg.durationMs}ms
                      </span>
                    </div>
                  </div>
                ))}
              </div>
            </div>
          )}
        </div>
      )}
    </div>
  );
}

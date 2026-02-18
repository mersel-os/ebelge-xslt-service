import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { RefreshCw, Loader2, CheckCircle2, XCircle } from "lucide-react";
import { useReloadAssets } from "@/api/hooks";
import { toast } from "sonner";

export function ReloadCard() {
  const reloadMutation = useReloadAssets();

  const handleReload = () => {
    reloadMutation.mutate(undefined, {
      onSuccess: (data) => {
        toast.success("Asset'ler yeniden yüklendi", {
          description: `${data.components.length} bileşen, ${data.durationMs} ms`,
        });
      },
      onError: (error) => {
        toast.error("Yeniden yükleme başarısız", {
          description: error instanceof Error ? error.message : "Bilinmeyen hata",
        });
      },
    });
  };

  const result = reloadMutation.data;

  return (
    <div className="rounded-xl border bg-card shadow-xs overflow-hidden">
      <div className="p-5">
        <div className="flex items-start justify-between gap-4">
          <div className="flex items-center gap-3">
            <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-muted text-foreground/70">
              <RefreshCw className="h-4 w-4" />
            </div>
            <div>
              <h3 className="text-sm font-semibold">Asset Reload</h3>
              <p className="text-xs text-muted-foreground mt-0.5">
                XSD, Schematron, XSLT ve profilleri yeniden yükle
              </p>
            </div>
          </div>
          <Button
            onClick={handleReload}
            disabled={reloadMutation.isPending}
            size="sm"
            className="h-8 rounded-lg shrink-0"
          >
            {reloadMutation.isPending ? (
              <Loader2 className="mr-2 h-3.5 w-3.5 animate-spin" />
            ) : (
              <RefreshCw className="mr-2 h-3.5 w-3.5" />
            )}
            Yükle
          </Button>
        </div>
      </div>

      {result && (
        <div className="border-t bg-muted/20 p-4 space-y-3">
          <div className="flex items-center gap-3 text-xs text-muted-foreground">
            <Badge variant="outline" className="rounded-md font-mono">
              {result.durationMs}ms
            </Badge>
            <span className="text-[10px] font-mono">{result.reloadedAt}</span>
          </div>

          <div className="space-y-1.5">
            {result.components.map((comp) => (
              <div
                key={comp.name}
                className="flex items-center justify-between rounded-lg bg-card border px-4 py-2.5 transition-colors hover:bg-muted/30"
              >
                <div className="flex items-center gap-3">
                  {comp.status === "OK" || comp.status === "SUCCESS" ? (
                    <CheckCircle2 className="h-4 w-4 text-success shrink-0" />
                  ) : comp.status === "PARTIAL" ? (
                    <CheckCircle2 className="h-4 w-4 text-warning shrink-0" />
                  ) : (
                    <XCircle className="h-4 w-4 text-destructive shrink-0" />
                  )}
                  <span className="text-sm font-medium">{comp.name}</span>
                </div>
                <div className="flex items-center gap-2">
                  <Badge variant="secondary" className="text-[10px] font-mono rounded-md">
                    {comp.count}
                  </Badge>
                  <span className="text-[10px] text-muted-foreground font-mono tabular-nums">
                    {comp.durationMs}ms
                  </span>
                </div>
              </div>
            ))}
          </div>
        </div>
      )}
    </div>
  );
}

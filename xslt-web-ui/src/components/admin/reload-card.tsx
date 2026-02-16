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
          description:
            error instanceof Error ? error.message : "Bilinmeyen hata",
        });
      },
    });
  };

  const result = reloadMutation.data;

  return (
    <div className="rounded-xl border bg-card shadow-sm overflow-hidden">
      <div className="p-6 pb-5">
        <div className="flex items-start justify-between gap-4">
          <div>
            <div className="flex h-10 w-10 items-center justify-center rounded-lg bg-primary/10 text-primary mb-3">
              <RefreshCw className="h-5 w-5" />
            </div>
            <h3 className="text-base font-bold">Asset Reload</h3>
            <p className="text-xs text-muted-foreground mt-1">
              XSD, Schematron, XSLT ve profilleri yeniden yükle
            </p>
          </div>
          <Button
            onClick={handleReload}
            disabled={reloadMutation.isPending}
            size="sm"
            className="h-9 rounded-lg shrink-0 shadow-sm"
          >
            {reloadMutation.isPending ? (
              <Loader2 className="mr-2 h-4 w-4 animate-spin" />
            ) : (
              <RefreshCw className="mr-2 h-4 w-4" />
            )}
            Yükle
          </Button>
        </div>
      </div>

      {result && (
        <div className="border-t bg-muted/20 p-5 space-y-3">
          <div className="flex items-center gap-3 text-xs text-muted-foreground">
            <Badge variant="outline" className="rounded-lg font-mono">
              {result.durationMs}ms
            </Badge>
            <span className="text-[10px] font-mono">{result.reloadedAt}</span>
          </div>

          <div className="space-y-2">
            {result.components.map((comp) => (
              <div
                key={comp.name}
                className="flex items-center justify-between rounded-lg bg-card border px-4 py-3"
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
                  <Badge
                    variant="secondary"
                    className="text-[10px] font-mono rounded-md"
                  >
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

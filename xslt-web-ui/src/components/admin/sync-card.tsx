import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import {
  Download,
  Loader2,
  CheckCircle2,
  Info,
  Eye,
} from "lucide-react";
import { useSyncPreview, usePendingPreviews } from "@/api/hooks";
import { toast } from "sonner";
import { SyncPreviewCard } from "./sync-preview-card";

export function SyncCard() {
  const syncPreviewMutation = useSyncPreview();
  const { data: pendingData, refetch: refetchPending } = usePendingPreviews();

  const handleSyncPreview = (packageId?: string) => {
    syncPreviewMutation.mutate(packageId, {
      onSuccess: (data) => {
        if (!data.enabled) {
          toast.info("GİB sync devre dışı");
        } else {
          toast.success("GİB paketleri staging'e indirildi", {
            description: `${data.packageCount} paket incelemeye hazır`,
          });
          refetchPending();
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

  const pendingPreviews = pendingData?.previews ?? [];
  const hasPending = pendingPreviews.length > 0;

  return (
    <div className="space-y-4">
      {/* Sync trigger card */}
      <div className="rounded-xl border bg-card shadow-xs overflow-hidden">
        <div className="p-5">
          <div className="flex items-start justify-between gap-4">
            <div className="flex items-center gap-3">
              <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-muted text-foreground/70">
                <Download className="h-4 w-4" />
              </div>
              <div>
                <h3 className="text-sm font-semibold">GİB Paket Sync</h3>
                <p className="text-xs text-muted-foreground mt-0.5 leading-relaxed">
                  GİB resmi paketlerini staging'e indir, değişiklikleri incele ve onayla
                </p>
              </div>
            </div>
            <div className="flex items-center gap-2 shrink-0">
              {hasPending && (
                <Badge
                  variant="outline"
                  className="rounded-md px-2.5 py-0.5 text-amber-600 border-amber-200 bg-amber-50 dark:text-amber-400 dark:border-amber-800 dark:bg-amber-950/30 text-[10px]"
                >
                  <Eye className="mr-1 h-3 w-3" />
                  {pendingPreviews.length} beklemede
                </Badge>
              )}
              <Button
                onClick={() => handleSyncPreview()}
                disabled={syncPreviewMutation.isPending}
                size="sm"
                className="h-8 rounded-lg"
              >
                {syncPreviewMutation.isPending ? (
                  <Loader2 className="mr-2 h-3.5 w-3.5 animate-spin" />
                ) : (
                  <Download className="mr-2 h-3.5 w-3.5" />
                )}
                Sync Önizle
              </Button>
            </div>
          </div>
        </div>

        {/* Result: disabled */}
        {syncPreviewMutation.data && !syncPreviewMutation.data.enabled && (
          <div className="border-t bg-muted/20 p-4">
            <div className="flex items-start gap-3 rounded-lg bg-card border p-3">
              <div className="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg bg-muted">
                <Info className="h-4 w-4 text-foreground/70" />
              </div>
              <div className="text-xs space-y-0.5">
                <p className="font-medium">GİB sync devre dışı</p>
                <p className="text-muted-foreground">
                  VALIDATION_ASSETS_GIB_SYNC_ENABLED=true ayarlayın
                </p>
              </div>
            </div>
          </div>
        )}

        {/* Result: previews */}
        {syncPreviewMutation.data?.enabled &&
          syncPreviewMutation.data.previews.length > 0 && (
            <div className="border-t bg-muted/20 p-4">
              <div className="space-y-2">
                {syncPreviewMutation.data.previews.map((p) => (
                  <div
                    key={p.packageId}
                    className="flex items-center justify-between rounded-lg bg-card border px-4 py-2.5 transition-colors hover:bg-muted/30"
                  >
                    <div className="flex items-center gap-3 min-w-0">
                      <CheckCircle2 className="h-4 w-4 text-success shrink-0" />
                      <div className="min-w-0">
                        <span className="text-sm font-medium block truncate">
                          {p.version.displayName}
                        </span>
                        <span className="text-[10px] text-muted-foreground block mt-0.5">
                          +{p.addedCount} -{p.removedCount} ~{p.modifiedCount}{" "}
                          {p.warnings.length > 0 && (
                            <span className="text-amber-500">
                              ({p.warnings.length} uyarı)
                            </span>
                          )}
                        </span>
                      </div>
                    </div>
                    <Badge variant="outline" className="rounded-md font-mono text-[10px]">
                      {p.version.durationMs}ms
                    </Badge>
                  </div>
                ))}
              </div>
            </div>
          )}
      </div>

      {/* Pending preview cards */}
      {pendingPreviews.map((preview) => (
        <SyncPreviewCard
          key={preview.packageId}
          preview={preview}
          onResolved={() => refetchPending()}
        />
      ))}
    </div>
  );
}

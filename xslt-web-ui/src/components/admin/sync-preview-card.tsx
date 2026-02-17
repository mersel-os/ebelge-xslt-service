import { useState } from "react";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import {
  CheckCircle2,
  XCircle,
  AlertTriangle,
  Loader2,
  Plus,
  Minus,
  FileEdit,
  FileText,
  ShieldAlert,
  Info,
} from "lucide-react";
import { useApprovePending, useRejectPending, usePendingFileDiff } from "@/api/hooks";
import { toast } from "sonner";
import { DiffModal } from "./diff-modal";
import type { SyncPreview, FileDiffSummary, SuppressionWarning } from "@/api/types";

interface SyncPreviewCardProps {
  preview: SyncPreview;
  onResolved?: () => void;
}

export function SyncPreviewCard({ preview, onResolved }: SyncPreviewCardProps) {
  const approveMutation = useApprovePending();
  const rejectMutation = useRejectPending();

  const handleApprove = () => {
    approveMutation.mutate(preview.packageId, {
      onSuccess: () => {
        toast.success("Versiyon onaylandı", {
          description: `${preview.version.displayName} live'a uygulandı`,
        });
        onResolved?.();
      },
      onError: (error) => {
        toast.error("Onaylama başarısız", {
          description: error instanceof Error ? error.message : "Bilinmeyen hata",
        });
      },
    });
  };

  const handleReject = () => {
    rejectMutation.mutate(preview.packageId, {
      onSuccess: () => {
        toast.info("Staging reddedildi", {
          description: `${preview.version.displayName} staging'i temizlendi`,
        });
        onResolved?.();
      },
      onError: (error) => {
        toast.error("Reddetme başarısız", {
          description: error instanceof Error ? error.message : "Bilinmeyen hata",
        });
      },
    });
  };

  const changedFiles = preview.fileDiffs.filter(
    (d) => d.status !== "UNCHANGED"
  );
  const hasWarnings = preview.warnings.length > 0;
  const hasCriticalWarnings = preview.warnings.some(
    (w) => w.severity === "CRITICAL"
  );
  const isProcessing = approveMutation.isPending || rejectMutation.isPending;

  return (
    <div className="rounded-xl border bg-card shadow-sm overflow-hidden">
      {/* Header */}
      <div className="p-6 pb-4">
        <div className="flex items-start justify-between gap-4">
          <div>
            <div className="flex items-center gap-2 mb-2">
              <Badge
                variant="outline"
                className="rounded-lg font-mono text-[10px]"
              >
                {preview.version.id}
              </Badge>
              <Badge variant="secondary" className="rounded-lg text-[10px]">
                {preview.version.displayName}
              </Badge>
              <Badge
                variant="outline"
                className="rounded-lg font-mono text-[10px]"
              >
                {preview.version.durationMs}ms
              </Badge>
            </div>
            <h3 className="text-base font-bold">
              Onay Bekleyen Güncelleme
            </h3>
            <p className="text-xs text-muted-foreground mt-1">
              GİB'den indirilen dosyalar staging'de. İnceleyip onaylayın veya
              reddedin.
            </p>
          </div>
          <div className="flex gap-2 shrink-0">
            <Button
              size="sm"
              variant="outline"
              onClick={handleReject}
              disabled={isProcessing}
              className="h-9 rounded-lg"
            >
              {rejectMutation.isPending ? (
                <Loader2 className="mr-2 h-4 w-4 animate-spin" />
              ) : (
                <XCircle className="mr-2 h-4 w-4" />
              )}
              Reddet
            </Button>
            <Button
              size="sm"
              onClick={handleApprove}
              disabled={isProcessing}
              className="h-9 rounded-lg shadow-sm"
            >
              {approveMutation.isPending ? (
                <Loader2 className="mr-2 h-4 w-4 animate-spin" />
              ) : (
                <CheckCircle2 className="mr-2 h-4 w-4" />
              )}
              Onayla
            </Button>
          </div>
        </div>

        {/* Summary badges */}
        <div className="flex items-center gap-2 mt-3">
          {preview.addedCount > 0 && (
            <Badge
              variant="outline"
              className="rounded-lg px-3 py-1 text-emerald-600 border-emerald-300 bg-emerald-50 dark:text-emerald-400 dark:border-emerald-800 dark:bg-emerald-950/30 gap-1"
            >
              <Plus className="h-3 w-3" />
              {preview.addedCount} eklenen
            </Badge>
          )}
          {preview.removedCount > 0 && (
            <Badge
              variant="outline"
              className="rounded-lg px-3 py-1 text-red-600 border-red-300 bg-red-50 dark:text-red-400 dark:border-red-800 dark:bg-red-950/30 gap-1"
            >
              <Minus className="h-3 w-3" />
              {preview.removedCount} silinen
            </Badge>
          )}
          {preview.modifiedCount > 0 && (
            <Badge
              variant="outline"
              className="rounded-lg px-3 py-1 text-amber-600 border-amber-300 bg-amber-50 dark:text-amber-400 dark:border-amber-800 dark:bg-amber-950/30 gap-1"
            >
              <FileEdit className="h-3 w-3" />
              {preview.modifiedCount} değişen
            </Badge>
          )}
          {preview.unchangedCount > 0 && (
            <Badge
              variant="outline"
              className="rounded-lg text-muted-foreground gap-1"
            >
              <FileText className="h-3 w-3" />
              {preview.unchangedCount} aynı
            </Badge>
          )}
        </div>
      </div>

      {/* Suppression Warnings */}
      {hasWarnings && (
        <div className="border-t px-6 py-4 bg-amber-50/50 dark:bg-amber-950/10">
          <div className="flex items-center gap-2 mb-3">
            <ShieldAlert
              className={`h-4 w-4 ${hasCriticalWarnings ? "text-red-500" : "text-amber-500"}`}
            />
            <span className="text-sm font-semibold">
              Suppression Etki Uyarıları ({preview.warnings.length})
            </span>
          </div>
          <div className="space-y-2">
            {preview.warnings.map((warning, idx) => (
              <WarningItem key={idx} warning={warning} />
            ))}
          </div>
        </div>
      )}

      {/* File list */}
      {changedFiles.length > 0 && (
        <div className="border-t">
          <div className="px-6 py-3 bg-muted/20">
            <span className="text-xs font-semibold text-muted-foreground">
              Değişen Dosyalar ({changedFiles.length})
            </span>
          </div>
          <div className="divide-y">
            {changedFiles.map((file) => (
              <FileItem
                key={file.path}
                file={file}
                packageId={preview.packageId}
              />
            ))}
          </div>
        </div>
      )}
    </div>
  );
}

function WarningItem({ warning }: { warning: SuppressionWarning }) {
  const isCritical = warning.severity === "CRITICAL";
  return (
    <div
      className={`flex items-start gap-3 rounded-lg border p-3 text-xs ${
        isCritical
          ? "bg-red-50 border-red-200 dark:bg-red-950/20 dark:border-red-900"
          : "bg-amber-50 border-amber-200 dark:bg-amber-950/20 dark:border-amber-900"
      }`}
    >
      {isCritical ? (
        <AlertTriangle className="h-4 w-4 text-red-500 shrink-0 mt-0.5" />
      ) : (
        <Info className="h-4 w-4 text-amber-500 shrink-0 mt-0.5" />
      )}
      <div>
        <p className="font-medium mb-1">
          <code className="bg-muted px-1 py-0.5 rounded text-[10px] font-mono">
            {warning.ruleId}
          </code>
          {" "}
          <span className="text-muted-foreground">
            ({warning.profileName} profili)
          </span>
        </p>
        <p className="text-muted-foreground">{warning.message}</p>
      </div>
    </div>
  );
}

function FileItem({
  file,
  packageId,
}: {
  file: FileDiffSummary;
  packageId: string;
}) {
  const [isOpen, setIsOpen] = useState(false);
  const diffQuery = usePendingFileDiff(
    isOpen ? packageId : "",
    isOpen ? file.path : ""
  );

  return (
    <>
      <button
        onClick={() => setIsOpen(true)}
        className="w-full flex items-center gap-3 px-6 py-2.5 hover:bg-muted/30 transition-colors text-left"
      >
        <FileStatusIcon status={file.status} />
        <span className="text-xs font-mono flex-1 truncate">{file.path}</span>
        <span className="text-[10px] text-muted-foreground font-mono tabular-nums">
          {formatSize(file.status === "REMOVED" ? file.oldSize : file.newSize)}
        </span>
      </button>

      <DiffModal
        open={isOpen}
        onOpenChange={setIsOpen}
        filePath={file.path}
        fileStatus={file.status}
        fileSize={file.status === "REMOVED" ? file.oldSize : file.newSize}
        isLoading={diffQuery.isLoading}
        data={diffQuery.data ?? undefined}
        error={diffQuery.isError}
      />
    </>
  );
}

function FileStatusIcon({
  status,
}: {
  status: FileDiffSummary["status"];
}) {
  switch (status) {
    case "ADDED":
      return <Plus className="h-3.5 w-3.5 text-emerald-500 shrink-0" />;
    case "REMOVED":
      return <Minus className="h-3.5 w-3.5 text-red-500 shrink-0" />;
    case "MODIFIED":
      return <FileEdit className="h-3.5 w-3.5 text-amber-500 shrink-0" />;
    default:
      return <FileText className="h-3.5 w-3.5 text-muted-foreground shrink-0" />;
  }
}

function formatSize(bytes: number): string {
  if (bytes < 0) return "";
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

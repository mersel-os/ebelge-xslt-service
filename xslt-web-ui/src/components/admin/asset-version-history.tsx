import { useState } from "react";
import { Badge } from "@/components/ui/badge";
import {
  History,
  ChevronRight,
  CheckCircle2,
  XCircle,
  Clock,
  Plus,
  Minus,
  FileEdit,
  FileText,
  Loader2,
} from "lucide-react";
import { useAssetVersions, useVersionDiffSummary, useVersionFileDiff } from "@/api/hooks";
import { DiffModal } from "./diff-modal";
import type { AssetVersion, FileDiffSummary } from "@/api/types";

export function AssetVersionHistory() {
  const { data, isLoading } = useAssetVersions();
  const [expandedVersion, setExpandedVersion] = useState<string | null>(null);

  if (isLoading) {
    return (
      <div className="rounded-xl border bg-card shadow-sm p-6">
        <div className="flex items-center gap-3">
          <Loader2 className="h-5 w-5 animate-spin text-muted-foreground" />
          <span className="text-sm text-muted-foreground">
            Versiyon geçmişi yükleniyor...
          </span>
        </div>
      </div>
    );
  }

  const versions = data?.versions ?? [];

  if (versions.length === 0) {
    return (
      <div className="rounded-xl border bg-card shadow-sm overflow-hidden">
        <div className="p-6">
          <div className="flex h-10 w-10 items-center justify-center rounded-lg bg-primary/10 text-primary mb-3">
            <History className="h-5 w-5" />
          </div>
          <h3 className="text-base font-bold">Versiyon Geçmişi</h3>
          <p className="text-xs text-muted-foreground mt-1">
            Henüz GİB sync geçmişi bulunmuyor. İlk sync sonrası burada
            görünecektir.
          </p>
        </div>
      </div>
    );
  }

  return (
    <div className="rounded-xl border bg-card shadow-sm overflow-hidden">
      <div className="p-6 pb-4">
        <div className="flex h-10 w-10 items-center justify-center rounded-lg bg-primary/10 text-primary mb-3">
          <History className="h-5 w-5" />
        </div>
        <h3 className="text-base font-bold">Versiyon Geçmişi</h3>
        <p className="text-xs text-muted-foreground mt-1">
          GİB paket sync geçmişi ve değişiklikler
        </p>
      </div>

      <div className="border-t divide-y">
        {versions.map((version) => (
          <VersionItem
            key={version.id}
            version={version}
            isExpanded={expandedVersion === version.id}
            onToggle={() =>
              setExpandedVersion(
                expandedVersion === version.id ? null : version.id
              )
            }
          />
        ))}
      </div>
    </div>
  );
}

function VersionItem({
  version,
  isExpanded,
  onToggle,
}: {
  version: AssetVersion;
  isExpanded: boolean;
  onToggle: () => void;
}) {
  return (
    <div>
      <button
        onClick={onToggle}
        className="w-full flex items-center gap-3 px-6 py-3 hover:bg-muted/30 transition-colors text-left"
      >
        <ChevronRight
          className={`h-3.5 w-3.5 text-muted-foreground transition-transform shrink-0 ${
            isExpanded ? "rotate-90" : ""
          }`}
        />
        <StatusIcon status={version.status} />
        <div className="flex-1 min-w-0">
          <div className="flex items-center gap-2 mb-0.5">
            <span className="text-sm font-medium truncate">
              {version.displayName}
            </span>
            <Badge
              variant="outline"
              className="rounded-md font-mono text-[10px] px-2.5 py-0.5 shrink-0"
            >
              {version.id}
            </Badge>
          </div>
          <div className="flex items-center gap-3 text-[10px] text-muted-foreground">
            <span>{formatTimestamp(version.timestamp)}</span>
            {version.filesSummary && (
              <span className="flex items-center gap-1.5">
                {version.filesSummary.added > 0 && (
                  <span className="flex items-center gap-0.5 text-emerald-600">
                    <Plus className="h-2.5 w-2.5" />
                    {version.filesSummary.added}
                  </span>
                )}
                {version.filesSummary.removed > 0 && (
                  <span className="flex items-center gap-0.5 text-red-600">
                    <Minus className="h-2.5 w-2.5" />
                    {version.filesSummary.removed}
                  </span>
                )}
                {version.filesSummary.modified > 0 && (
                  <span className="flex items-center gap-0.5 text-amber-600">
                    <FileEdit className="h-2.5 w-2.5" />
                    {version.filesSummary.modified}
                  </span>
                )}
              </span>
            )}
            <Badge
              variant="outline"
              className="rounded-md font-mono text-[9px] px-2.5 py-0.5 shrink-0"
            >
              {version.durationMs}ms
            </Badge>
          </div>
        </div>
        <VersionStatusBadge status={version.status} />
      </button>

      {isExpanded && <VersionDetail version={version} />}
    </div>
  );
}

function VersionDetail({ version }: { version: AssetVersion }) {
  const { data, isLoading } = useVersionDiffSummary(version.id);
  const [showUnchanged, setShowUnchanged] = useState(false);

  const allFiles = data?.files ?? [];
  const changedFiles = allFiles.filter((f) => f.status !== "UNCHANGED");
  const unchangedCount = allFiles.length - changedFiles.length;
  const displayFiles = showUnchanged ? allFiles : changedFiles;

  return (
    <div className="px-6 pb-4 pl-14">
      {/* Meta */}
      <div className="rounded-lg border bg-muted/20 text-xs mb-3">
        <div className="px-4 py-2.5 grid grid-cols-2 gap-x-6 gap-y-1.5">
          <div>
            <span className="text-muted-foreground">Paket:</span>{" "}
            <span className="font-medium">{version.packageId}</span>
          </div>
          <div>
            <span className="text-muted-foreground">Durum:</span>{" "}
            <VersionStatusBadge status={version.status} />
          </div>
          {version.appliedAt && (
            <div>
              <span className="text-muted-foreground">Onaylanma:</span>{" "}
              <span className="font-medium">
                {formatTimestamp(version.appliedAt)}
              </span>
            </div>
          )}
          {version.rejectedAt && (
            <div>
              <span className="text-muted-foreground">Reddedilme:</span>{" "}
              <span className="font-medium">
                {formatTimestamp(version.rejectedAt)}
              </span>
            </div>
          )}
        </div>
      </div>

      {/* File list */}
      {isLoading && (
        <div className="flex items-center gap-2 py-4 text-xs text-muted-foreground">
          <Loader2 className="h-3.5 w-3.5 animate-spin" />
          Dosya farkları yükleniyor...
        </div>
      )}

      {!isLoading && allFiles.length > 0 && (
        <div className="rounded-lg border overflow-hidden divide-y">
          {/* Header */}
          <div className="px-4 py-2 bg-muted/20 flex items-center justify-between">
            <span className="text-[10px] font-semibold text-muted-foreground">
              Değişiklikler ({changedFiles.length})
              {unchangedCount > 0 && (
                <span className="font-normal ml-2">
                  + {unchangedCount} aynı
                </span>
              )}
            </span>
            {unchangedCount > 0 && (
              <button
                onClick={() => setShowUnchanged(!showUnchanged)}
                className="text-[10px] text-primary hover:underline"
              >
                {showUnchanged
                  ? "Sadece değişenleri göster"
                  : `Tümünü göster (+${unchangedCount})`}
              </button>
            )}
          </div>

          {changedFiles.length === 0 && (
            <div className="px-4 py-3 text-xs text-muted-foreground">
              Bu versiyonda içerik değişikliği yok.
            </div>
          )}

          {displayFiles.map((file) => (
            <VersionFileItem
              key={file.path}
              file={file}
              versionId={version.id}
            />
          ))}
        </div>
      )}

      {!isLoading && allFiles.length === 0 && (
        <div className="rounded-lg border bg-muted/10 px-4 py-3 text-xs text-muted-foreground">
          Diff bilgisi bulunamadı.
        </div>
      )}
    </div>
  );
}

function VersionFileItem({
  file,
  versionId,
}: {
  file: FileDiffSummary;
  versionId: string;
}) {
  const [isOpen, setIsOpen] = useState(false);
  const { data, isLoading, isError } = useVersionFileDiff(
    isOpen ? versionId : "",
    isOpen ? file.path : ""
  );

  return (
    <>
      <button
        onClick={() => setIsOpen(true)}
        className="w-full flex items-center gap-3 px-4 py-2 hover:bg-muted/30 transition-colors text-left"
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
        isLoading={isLoading}
        data={data ?? undefined}
        error={isError}
      />
    </>
  );
}

function FileStatusIcon({ status }: { status: FileDiffSummary["status"] }) {
  switch (status) {
    case "ADDED":
      return <Plus className="h-3 w-3 text-emerald-500 shrink-0" />;
    case "REMOVED":
      return <Minus className="h-3 w-3 text-red-500 shrink-0" />;
    case "MODIFIED":
      return <FileEdit className="h-3 w-3 text-amber-500 shrink-0" />;
    default:
      return <FileText className="h-3 w-3 text-muted-foreground shrink-0" />;
  }
}

function StatusIcon({ status }: { status: AssetVersion["status"] }) {
  switch (status) {
    case "APPLIED":
      return <CheckCircle2 className="h-4 w-4 text-emerald-500 shrink-0" />;
    case "REJECTED":
      return <XCircle className="h-4 w-4 text-red-500 shrink-0" />;
    case "PENDING":
      return <Clock className="h-4 w-4 text-amber-500 shrink-0" />;
  }
}

function VersionStatusBadge({ status }: { status: AssetVersion["status"] }) {
  switch (status) {
    case "APPLIED":
      return (
        <Badge
          variant="outline"
          className="rounded-lg text-[10px] px-3 py-1 text-emerald-600 border-emerald-300 bg-emerald-50 dark:text-emerald-400 dark:border-emerald-800 dark:bg-emerald-950/30"
        >
          Uygulandı
        </Badge>
      );
    case "REJECTED":
      return (
        <Badge
          variant="outline"
          className="rounded-lg text-[10px] px-3 py-1 text-red-600 border-red-300 bg-red-50 dark:text-red-400 dark:border-red-800 dark:bg-red-950/30"
        >
          Reddedildi
        </Badge>
      );
    case "PENDING":
      return (
        <Badge
          variant="outline"
          className="rounded-lg text-[10px] px-3 py-1 text-amber-600 border-amber-300 bg-amber-50 dark:text-amber-400 dark:border-amber-800 dark:bg-amber-950/30"
        >
          Beklemede
        </Badge>
      );
  }
}

function formatTimestamp(ts: string): string {
  try {
    const date = new Date(ts);
    return date.toLocaleString("tr-TR", {
      day: "2-digit",
      month: "2-digit",
      year: "numeric",
      hour: "2-digit",
      minute: "2-digit",
    });
  } catch {
    return ts;
  }
}

function formatSize(bytes: number): string {
  if (bytes < 0) return "";
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

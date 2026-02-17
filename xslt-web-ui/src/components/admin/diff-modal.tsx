import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
} from "@/components/ui/dialog";
import { Badge } from "@/components/ui/badge";
import { Loader2, Plus, Minus, FileEdit, FileText } from "lucide-react";
import { FileDiffViewer } from "./file-diff-viewer";
import type { FileDiffSummary } from "@/api/types";

interface DiffModalProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  filePath: string;
  fileStatus: FileDiffSummary["status"];
  fileSize: number;
  isLoading: boolean;
  data?: {
    unifiedDiff?: string;
    oldContent?: string;
    newContent?: string;
    isBinary?: boolean;
  };
  error?: boolean;
}

export function DiffModal({
  open,
  onOpenChange,
  filePath,
  fileStatus,
  fileSize,
  isLoading,
  data,
  error,
}: DiffModalProps) {
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-[95vw] h-[90vh] flex flex-col p-0 gap-0">
        {/* Header */}
        <DialogHeader className="px-6 py-4 border-b shrink-0 pr-14">
          <div className="flex items-center gap-3">
            <FileStatusIcon status={fileStatus} />
            <DialogTitle className="font-mono text-sm truncate flex-1">
              {filePath}
            </DialogTitle>
            <StatusBadge status={fileStatus} />
            <Badge
              variant="outline"
              className="rounded-md font-mono text-[10px] px-3 py-1 shrink-0"
            >
              {formatSize(fileSize)}
            </Badge>
          </div>
          <DialogDescription className="sr-only">
            Dosya değişiklik detayları
          </DialogDescription>
        </DialogHeader>

        {/* Content */}
        <div className="flex-1 overflow-auto p-4">
          {isLoading && (
            <div className="flex items-center justify-center h-full">
              <div className="flex items-center gap-3 text-muted-foreground">
                <Loader2 className="h-5 w-5 animate-spin" />
                <span className="text-sm">Diff yükleniyor...</span>
              </div>
            </div>
          )}

          {error && (
            <div className="flex items-center justify-center h-full">
              <span className="text-sm text-destructive">
                Diff yüklenemedi
              </span>
            </div>
          )}

          {data && (
            <FileDiffViewer
              unifiedDiff={data.unifiedDiff || ""}
              oldContent={data.oldContent}
              newContent={data.newContent}
              isBinary={data.isBinary}
            />
          )}
        </div>
      </DialogContent>
    </Dialog>
  );
}

function FileStatusIcon({ status }: { status: FileDiffSummary["status"] }) {
  switch (status) {
    case "ADDED":
      return <Plus className="h-4 w-4 text-emerald-500 shrink-0" />;
    case "REMOVED":
      return <Minus className="h-4 w-4 text-red-500 shrink-0" />;
    case "MODIFIED":
      return <FileEdit className="h-4 w-4 text-amber-500 shrink-0" />;
    default:
      return <FileText className="h-4 w-4 text-muted-foreground shrink-0" />;
  }
}

function StatusBadge({ status }: { status: FileDiffSummary["status"] }) {
  switch (status) {
    case "ADDED":
      return (
        <Badge
          variant="outline"
          className="rounded-md text-[10px] px-3 py-1 text-emerald-600 border-emerald-300 bg-emerald-50 dark:text-emerald-400 dark:border-emerald-800 dark:bg-emerald-950/30"
        >
          Eklendi
        </Badge>
      );
    case "REMOVED":
      return (
        <Badge
          variant="outline"
          className="rounded-md text-[10px] px-3 py-1 text-red-600 border-red-300 bg-red-50 dark:text-red-400 dark:border-red-800 dark:bg-red-950/30"
        >
          Silindi
        </Badge>
      );
    case "MODIFIED":
      return (
        <Badge
          variant="outline"
          className="rounded-md text-[10px] px-3 py-1 text-amber-600 border-amber-300 bg-amber-50 dark:text-amber-400 dark:border-amber-800 dark:bg-amber-950/30"
        >
          Değişti
        </Badge>
      );
    default:
      return null;
  }
}

function formatSize(bytes: number): string {
  if (bytes < 0) return "";
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

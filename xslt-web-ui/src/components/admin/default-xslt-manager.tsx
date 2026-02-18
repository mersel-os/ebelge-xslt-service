import { useState, useRef } from "react";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import {
  FileCode2,
  Upload,
  Trash2,
  Eye,
  Pencil,
  Save,
  X,
  CheckCircle2,
  AlertCircle,
  Loader2,
} from "lucide-react";
import {
  useDefaultXsltTemplates,
  useDefaultXsltContent,
  useUploadDefaultXslt,
  useSaveDefaultXsltContent,
  useDeleteDefaultXslt,
} from "@/api/hooks";
import type { DefaultXsltTemplate } from "@/api/types";
import { toast } from "sonner";
import { cn } from "@/lib/utils";

export function DefaultXsltManager() {
  const { data, isLoading, isError } = useDefaultXsltTemplates();
  const templates = data?.templates ?? [];

  if (isLoading) {
    return (
      <div className="rounded-xl border bg-card shadow-xs p-8">
        <div className="flex items-center justify-center gap-3 text-muted-foreground">
          <Loader2 className="h-5 w-5 animate-spin" />
          <span className="text-sm">XSLT şablonları yükleniyor...</span>
        </div>
      </div>
    );
  }

  if (isError) {
    return (
      <div className="rounded-xl border bg-card shadow-xs p-8">
        <div className="flex items-center justify-center gap-3 text-destructive">
          <AlertCircle className="h-5 w-5" />
          <span className="text-sm">XSLT şablonları yüklenirken hata oluştu</span>
        </div>
      </div>
    );
  }

  return (
    <div className="rounded-xl border bg-card shadow-xs overflow-hidden">
      <div className="p-5">
        <div className="flex items-start justify-between gap-4">
          <div className="flex items-center gap-3">
            <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-muted text-foreground/70">
              <FileCode2 className="h-4 w-4" />
            </div>
            <div>
              <h3 className="text-sm font-semibold">Varsayılan XSLT Şablonları</h3>
              <p className="text-xs text-muted-foreground mt-0.5 leading-relaxed">
                Her belge tipi için dönüşümde kullanılacak varsayılan XSLT şablonlarını yönetin.
              </p>
            </div>
          </div>
          <Badge variant="outline" className="shrink-0 rounded-md text-xs px-2.5 py-1">
            {templates.filter((t) => t.exists).length} / {templates.length} aktif
          </Badge>
        </div>
      </div>

      <div className="border-t divide-y">
        {templates.map((template) => (
          <TemplateRow key={template.transformType} template={template} />
        ))}
      </div>
    </div>
  );
}

function TemplateRow({ template }: { template: DefaultXsltTemplate }) {
  const [viewOpen, setViewOpen] = useState(false);
  const [editOpen, setEditOpen] = useState(false);
  const fileInputRef = useRef<HTMLInputElement>(null);

  const uploadMutation = useUploadDefaultXslt();
  const deleteMutation = useDeleteDefaultXslt();

  const handleFileUpload = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;

    uploadMutation.mutate(
      { transformType: template.transformType, file },
      {
        onSuccess: (data) => {
          toast.success("XSLT şablonu yüklendi", {
            description: `${data.label} — ${formatSize(data.size)}`,
          });
        },
        onError: (error) => {
          toast.error("Yükleme başarısız", {
            description: error instanceof Error ? error.message : "Bilinmeyen hata",
          });
        },
      }
    );

    if (fileInputRef.current) fileInputRef.current.value = "";
  };

  const handleDelete = () => {
    if (!confirm(`"${template.label}" XSLT şablonunu silmek istediğinize emin misiniz?`)) return;

    deleteMutation.mutate(template.transformType, {
      onSuccess: () => {
        toast.success("XSLT şablonu silindi", { description: template.label });
      },
      onError: (error) => {
        toast.error("Silme başarısız", {
          description: error instanceof Error ? error.message : "Bilinmeyen hata",
        });
      },
    });
  };

  const isProcessing = uploadMutation.isPending || deleteMutation.isPending;

  return (
    <>
      <div className="flex items-center gap-4 px-5 py-3.5 hover:bg-muted/30 transition-colors">
        {/* Status */}
        <div
          className={cn(
            "flex h-8 w-8 shrink-0 items-center justify-center rounded-lg",
            template.exists
              ? "bg-success/10 text-success"
              : "bg-warning/10 text-warning"
          )}
        >
          {template.exists ? <CheckCircle2 className="h-4 w-4" /> : <AlertCircle className="h-4 w-4" />}
        </div>

        {/* Info */}
        <div className="flex-1 min-w-0">
          <div className="flex items-center gap-2">
            <span className="text-sm font-medium truncate">{template.label}</span>
            <Badge variant="outline" className="rounded-md text-[10px] px-1.5 py-0 font-mono shrink-0">
              {template.transformType}
            </Badge>
          </div>
          <div className="flex items-center gap-3 mt-0.5">
            <span className="text-xs text-muted-foreground font-mono truncate">{template.fileName}</span>
            {template.exists && (
              <>
                <span className="text-xs text-muted-foreground tabular-nums">{formatSize(template.size)}</span>
                {template.lastModified && (
                  <span className="text-xs text-muted-foreground tabular-nums">{formatDate(template.lastModified)}</span>
                )}
              </>
            )}
            {!template.exists && (
              <span className="text-xs text-warning">Şablon yok</span>
            )}
          </div>
        </div>

        {/* Actions */}
        <div className="flex items-center gap-1 shrink-0">
          {template.exists && (
            <>
              <Button variant="ghost" size="sm" className="h-8 w-8 p-0 rounded-lg" title="Görüntüle" onClick={() => setViewOpen(true)}>
                <Eye className="h-3.5 w-3.5" />
              </Button>
              <Button variant="ghost" size="sm" className="h-8 w-8 p-0 rounded-lg" title="Düzenle" onClick={() => setEditOpen(true)}>
                <Pencil className="h-3.5 w-3.5" />
              </Button>
            </>
          )}
          <Button
            variant="ghost"
            size="sm"
            className="h-8 w-8 p-0 rounded-lg"
            title={template.exists ? "Değiştir" : "Yükle"}
            disabled={isProcessing}
            onClick={() => fileInputRef.current?.click()}
          >
            {uploadMutation.isPending ? <Loader2 className="h-3.5 w-3.5 animate-spin" /> : <Upload className="h-3.5 w-3.5" />}
          </Button>
          {template.exists && (
            <Button
              variant="ghost"
              size="sm"
              className="h-8 w-8 p-0 rounded-lg text-destructive hover:text-destructive"
              title="Sil"
              disabled={isProcessing}
              onClick={handleDelete}
            >
              {deleteMutation.isPending ? <Loader2 className="h-3.5 w-3.5 animate-spin" /> : <Trash2 className="h-3.5 w-3.5" />}
            </Button>
          )}
        </div>

        <input ref={fileInputRef} type="file" accept=".xslt,.xsl,.xml" className="hidden" onChange={handleFileUpload} />
      </div>

      {viewOpen && (
        <XsltViewModal open={viewOpen} onOpenChange={setViewOpen} transformType={template.transformType} label={template.label} />
      )}
      {editOpen && (
        <XsltEditModal open={editOpen} onOpenChange={setEditOpen} transformType={template.transformType} label={template.label} />
      )}
    </>
  );
}

function XsltViewModal({
  open, onOpenChange, transformType, label,
}: { open: boolean; onOpenChange: (open: boolean) => void; transformType: string; label: string; }) {
  const { data, isLoading } = useDefaultXsltContent(open ? transformType : "");

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-[90vw] h-[85vh] flex flex-col p-0 rounded-xl">
        <DialogHeader className="px-5 py-3 border-b shrink-0 pr-14">
          <div className="flex items-center gap-3">
            <FileCode2 className="h-4 w-4 text-foreground/70 shrink-0" />
            <DialogTitle className="text-sm font-medium truncate">{label} — XSLT Şablonu</DialogTitle>
            <Badge variant="outline" className="rounded-md text-[10px] px-2 py-0 font-mono shrink-0">{transformType}</Badge>
          </div>
          <DialogDescription className="sr-only">XSLT şablon içeriği</DialogDescription>
        </DialogHeader>
        <div className="flex-1 overflow-auto p-0 min-h-0">
          {isLoading ? (
            <div className="flex items-center justify-center h-full">
              <Loader2 className="h-5 w-5 animate-spin text-muted-foreground" />
            </div>
          ) : data?.content ? (
            <pre className="text-xs font-mono leading-relaxed p-5 whitespace-pre-wrap break-all">{data.content}</pre>
          ) : (
            <div className="flex items-center justify-center h-full text-muted-foreground text-sm">İçerik bulunamadı</div>
          )}
        </div>
      </DialogContent>
    </Dialog>
  );
}

function XsltEditModal({
  open, onOpenChange, transformType, label,
}: { open: boolean; onOpenChange: (open: boolean) => void; transformType: string; label: string; }) {
  const { data, isLoading } = useDefaultXsltContent(open ? transformType : "");
  const saveMutation = useSaveDefaultXsltContent();
  const [content, setContent] = useState<string | null>(null);
  const [initialized, setInitialized] = useState(false);

  if (data?.content && !initialized) {
    setContent(data.content);
    setInitialized(true);
  }

  const handleSave = () => {
    if (!content) return;
    saveMutation.mutate(
      { transformType, content },
      {
        onSuccess: (res) => {
          toast.success("XSLT şablonu kaydedildi", { description: `${res.label} — ${formatSize(res.size)}` });
          onOpenChange(false);
        },
        onError: (error) => {
          toast.error("Kaydetme başarısız", { description: error instanceof Error ? error.message : "Bilinmeyen hata" });
        },
      }
    );
  };

  const hasChanges = content !== null && content !== (data?.content ?? "");

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-[90vw] h-[85vh] flex flex-col p-0 rounded-xl">
        <DialogHeader className="px-5 py-3 border-b shrink-0 pr-14">
          <div className="flex items-center gap-3">
            <Pencil className="h-4 w-4 text-foreground/70 shrink-0" />
            <DialogTitle className="text-sm font-medium truncate">{label} — XSLT Düzenle</DialogTitle>
            <Badge variant="outline" className="rounded-md text-[10px] px-2 py-0 font-mono shrink-0">{transformType}</Badge>
            {hasChanges && (
              <Badge className="rounded-md text-[10px] px-2 py-0 bg-amber-100 text-amber-700 dark:bg-amber-950/30 dark:text-amber-400 border-amber-200 dark:border-amber-700">
                Değişiklik var
              </Badge>
            )}
          </div>
          <DialogDescription className="sr-only">XSLT şablonunu düzenle</DialogDescription>
        </DialogHeader>
        <div className="flex-1 overflow-hidden min-h-0">
          {isLoading ? (
            <div className="flex items-center justify-center h-full">
              <Loader2 className="h-5 w-5 animate-spin text-muted-foreground" />
            </div>
          ) : (
            <textarea
              className="w-full h-full resize-none border-0 bg-transparent text-xs font-mono leading-relaxed p-5 outline-none"
              value={content ?? data?.content ?? ""}
              onChange={(e) => setContent(e.target.value)}
              spellCheck={false}
            />
          )}
        </div>
        <div className="flex items-center justify-end gap-2 px-5 py-3 border-t shrink-0">
          <Button variant="ghost" size="sm" className="rounded-lg" onClick={() => onOpenChange(false)}>
            <X className="h-3.5 w-3.5 mr-1.5" />
            İptal
          </Button>
          <Button size="sm" className="rounded-lg" disabled={!hasChanges || saveMutation.isPending} onClick={handleSave}>
            {saveMutation.isPending ? <Loader2 className="h-3.5 w-3.5 mr-1.5 animate-spin" /> : <Save className="h-3.5 w-3.5 mr-1.5" />}
            Kaydet
          </Button>
        </div>
      </DialogContent>
    </Dialog>
  );
}

function formatSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

function formatDate(isoString: string): string {
  try {
    const date = new Date(isoString);
    return date.toLocaleDateString("tr-TR", {
      day: "2-digit",
      month: "2-digit",
      year: "numeric",
      hour: "2-digit",
      minute: "2-digit",
    });
  } catch {
    return isoString;
  }
}

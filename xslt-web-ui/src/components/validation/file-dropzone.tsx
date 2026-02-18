import { useCallback } from "react";
import { useDropzone } from "react-dropzone";
import { Upload, FileText, X } from "lucide-react";
import { toast } from "sonner";
import { cn } from "@/lib/utils";
import { Button } from "@/components/ui/button";

interface FileDropzoneProps {
  file: File | null;
  onFileChange: (file: File | null) => void;
  accept?: string;
  label?: string;
}

export function FileDropzone({
  file,
  onFileChange,
  accept = ".xml",
  label = "XML dosyasını sürükleyip bırakın",
}: FileDropzoneProps) {
  const MAX_FILE_SIZE = 100 * 1024 * 1024;

  const onDrop = useCallback(
    (acceptedFiles: File[]) => {
      if (acceptedFiles.length > 0) {
        if (acceptedFiles[0].size > MAX_FILE_SIZE) {
          toast.error("Dosya boyutu çok büyük (max 100 MB)");
          return;
        }
        onFileChange(acceptedFiles[0]);
      }
    },
    [onFileChange]
  );

  const extensions = accept.split(",").map((ext) => ext.trim());
  const { getRootProps, getInputProps, isDragActive } = useDropzone({
    onDrop,
    accept: {
      "text/xml": extensions,
      "application/xml": extensions,
      ...(extensions.some((e) => [".xslt", ".xsl"].includes(e))
        ? { "application/xslt+xml": extensions }
        : {}),
    },
    multiple: false,
  });

  if (file) {
    return (
      <div className="flex items-center gap-3 rounded-xl border border-border bg-muted p-3 animate-fade-in">
        <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-lg bg-muted border border-border">
          <FileText className="h-4 w-4 text-foreground/70" />
        </div>
        <div className="min-w-0 flex-1">
          <p className="truncate text-sm font-medium text-foreground">{file.name}</p>
          <p className="text-xs text-muted-foreground">
            {(file.size / 1024).toFixed(1)} KB · {file.name.split(".").pop()?.toUpperCase() ?? "XML"}
          </p>
        </div>
        <Button
          variant="ghost"
          size="icon-xs"
          className="shrink-0 text-muted-foreground/70 hover:text-red-400"
          onClick={() => onFileChange(null)}
          aria-label="Dosyayı kaldır"
        >
          <X className="h-3.5 w-3.5" />
        </Button>
      </div>
    );
  }

  return (
    <div
      {...getRootProps()}
      role="button"
      aria-label={label}
      className={cn(
        "group flex cursor-pointer flex-col items-center justify-center rounded-2xl border border-border p-10 transition-all duration-300",
        isDragActive
          ? "bg-accent"
          : "bg-muted/50 hover:bg-muted"
      )}
    >
      <input {...getInputProps()} />
      <div
        className={cn(
          "mb-4 flex h-12 w-12 items-center justify-center rounded-2xl transition-all duration-300",
          isDragActive
            ? "bg-foreground/10 text-foreground"
            : "bg-muted text-muted-foreground/70 group-hover:bg-foreground/10 group-hover:text-foreground"
        )}
      >
        <Upload className="h-5 w-5" />
      </div>
      <p className={cn("text-sm font-medium transition-colors", isDragActive ? "text-foreground" : "text-muted-foreground")}>
        {label}
      </p>
      <p className="mt-1.5 text-xs text-muted-foreground/70">veya dosya seçmek için tıklayın</p>
    </div>
  );
}

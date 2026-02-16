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
  const MAX_FILE_SIZE = 100 * 1024 * 1024; // 100 MB

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
      <div className="flex items-center gap-4 rounded-xl border bg-muted/30 p-4 transition-all animate-scale-in">
        <div className="flex h-12 w-12 shrink-0 items-center justify-center rounded-lg bg-primary/10 text-primary">
          <FileText className="h-5 w-5" />
        </div>
        <div className="flex-1 min-w-0">
          <p className="text-sm font-semibold truncate">{file.name}</p>
          <p className="text-xs text-muted-foreground mt-0.5">
            {(file.size / 1024).toFixed(1)} KB &middot; {file.name.split('.').pop()?.toUpperCase() ?? 'XML'}
          </p>
        </div>
        <Button
          variant="ghost"
          size="icon"
          className="h-9 w-9 rounded-lg shrink-0 text-muted-foreground hover:text-destructive hover:bg-destructive/10"
          onClick={() => onFileChange(null)}
          aria-label="Dosyayı kaldır"
        >
          <X className="h-4 w-4" />
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
        "group relative flex flex-col items-center justify-center rounded-xl border-2 border-dashed p-10 cursor-pointer transition-all duration-300",
        isDragActive
          ? "border-primary bg-primary/5 scale-[1.02]"
          : "border-border hover:border-primary/30 hover:bg-muted/30"
      )}
    >
      <input {...getInputProps()} />

      <div
        className={cn(
          "flex h-14 w-14 items-center justify-center rounded-xl transition-all duration-300 mb-4",
          isDragActive
            ? "bg-primary/15 text-primary scale-110"
            : "bg-muted text-muted-foreground group-hover:bg-primary/10 group-hover:text-primary"
        )}
      >
        <Upload className="h-6 w-6" />
      </div>

      <p
        className={cn(
          "text-sm font-medium transition-colors",
          isDragActive ? "text-primary" : "text-foreground/70"
        )}
      >
        {label}
      </p>
      <p className="text-xs text-muted-foreground/50 mt-1.5">
        veya dosya seçmek için tıklayın
      </p>
    </div>
  );
}

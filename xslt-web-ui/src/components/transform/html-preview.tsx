import { useState, useCallback, useRef, useEffect } from "react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import {
  Clock,
  FileText,
  AlertTriangle,
  ShieldAlert,
  Eye,
  Code2,
  Printer,
  Download,
  Copy,
  Check,
} from "lucide-react";
import { toast } from "sonner";
import type { TransformMeta } from "@/api/types";

interface HtmlPreviewProps {
  html: string;
  meta: TransformMeta;
}

export function HtmlPreview({ html, meta }: HtmlPreviewProps) {
  const [copied, setCopied] = useState(false);
  const copyTimerRef = useRef<ReturnType<typeof setTimeout>>(undefined);

  useEffect(() => () => clearTimeout(copyTimerRef.current), []);

  const sanitizedHtml = html;

  const handlePrint = useCallback(() => {
    try {
      const printWindow = window.open("", "_blank");
      if (!printWindow) { toast.error("Popup engelleyici aktif."); return; }
      printWindow.document.write(sanitizedHtml);
      const style = printWindow.document.createElement("style");
      style.textContent = "@page { margin: 15mm; } body { margin: 0; }";
      printWindow.document.head.appendChild(style);
      printWindow.document.close();
      printWindow.focus();
      printWindow.print();
      printWindow.onafterprint = () => printWindow.close();
    } catch { toast.error("Yazdırma başarısız."); }
  }, [sanitizedHtml]);

  const handleDownload = useCallback(() => {
    const blob = new Blob([sanitizedHtml], { type: "text/html;charset=utf-8" });
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url; a.download = "preview.html";
    document.body.appendChild(a); a.click();
    document.body.removeChild(a); URL.revokeObjectURL(url);
  }, [sanitizedHtml]);

  const handleCopy = useCallback(async () => {
    try {
      await navigator.clipboard.writeText(html);
      setCopied(true);
      clearTimeout(copyTimerRef.current);
      copyTimerRef.current = setTimeout(() => setCopied(false), 2000);
    } catch { toast.error("Kopyalama başarısız."); }
  }, [html]);

  return (
    <div className="space-y-5">
      {/* Meta */}
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="flex flex-wrap gap-2">
          <Badge variant="outline" className="gap-1.5 text-xs"><Clock className="h-3 w-3" />{meta.durationMs} ms</Badge>
          <Badge variant="outline" className="gap-1.5 text-xs"><FileText className="h-3 w-3" />{(meta.outputSize / 1024).toFixed(1)} KB</Badge>
          {meta.defaultUsed && <Badge variant="secondary" className="text-xs">Varsayılan</Badge>}
          {meta.embeddedUsed && <Badge variant="secondary" className="text-xs">Gömülü</Badge>}
          {meta.watermarkApplied && <Badge variant="secondary" className="text-xs">Filigranlı</Badge>}
          {meta.customError && <Badge variant="destructive" className="gap-1.5 text-xs"><AlertTriangle className="h-3 w-3" />{meta.customError}</Badge>}
        </div>
        <div className="flex items-center gap-1.5">
          <Button variant="outline" size="xs" onClick={handlePrint}><Printer className="h-3.5 w-3.5" /><span className="hidden sm:inline">Yazdır</span></Button>
          <Button variant="outline" size="xs" onClick={handleDownload}><Download className="h-3.5 w-3.5" /><span className="hidden sm:inline">İndir</span></Button>
        </div>
      </div>

      {/* Security violation warning */}
      {meta.securityViolations.length > 0 && (
        <div className="rounded-xl border-2 border-red-400 bg-red-100 p-4 dark:border-red-500/60 dark:bg-red-950/50" role="alert">
          <div className="flex items-start gap-3">
            <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-red-500 shadow-lg shadow-red-500/25">
              <ShieldAlert className="h-5 w-5 text-white" />
            </div>
            <div className="min-w-0 flex-1">
              <p className="text-sm font-bold text-red-800 dark:text-red-300">
                Güvenlik İhlali Tespit Edildi
              </p>
              <p className="mt-1 text-xs font-medium text-red-700 dark:text-red-400">
                XSLT şablonunda tehlikeli {meta.scriptsRemoved} script tespit edilip kaldırıldı.
                Bu scriptler veri sızdırma veya kullanıcı deneyimini bozma riski taşıyordu.
              </p>
              <div className="mt-3 flex flex-wrap gap-1.5">
                {meta.securityViolations.map((v) => (
                  <span
                    key={v}
                    className="inline-flex items-center rounded-md bg-red-600 px-2.5 py-1 font-mono text-[11px] font-semibold text-white shadow-sm dark:bg-red-500"
                  >
                    {v}
                  </span>
                ))}
              </div>
            </div>
          </div>
        </div>
      )}

      {/* Browser frame */}
      <Tabs defaultValue="preview" className="w-full">
        <div className="overflow-hidden rounded-xl border border-border">
          <div className="flex items-center justify-between border-b border-border bg-muted/60 px-4 py-2">
            <div className="flex items-center gap-3">
              <div className="flex gap-1.5">
                <div className="h-2.5 w-2.5 rounded-full bg-accent" />
                <div className="h-2.5 w-2.5 rounded-full bg-accent" />
                <div className="h-2.5 w-2.5 rounded-full bg-accent" />
              </div>
              <TabsList className="h-7 rounded-lg bg-muted p-0.5">
                <TabsTrigger value="preview" className="h-6 gap-1.5 rounded-md px-2.5 text-[11px]"><Eye className="h-3 w-3" />Önizleme</TabsTrigger>
                <TabsTrigger value="source" className="h-6 gap-1.5 rounded-md px-2.5 text-[11px]"><Code2 className="h-3 w-3" />HTML</TabsTrigger>
              </TabsList>
            </div>
            <span className="hidden font-mono text-[11px] text-muted-foreground/60 sm:block">preview.html</span>
          </div>

          <TabsContent value="preview" className="mt-0">
            <iframe srcDoc={sanitizedHtml} title="HTML Preview" className="w-full bg-white" style={{ height: "600px" }} sandbox="allow-scripts allow-same-origin" />
          </TabsContent>

          <TabsContent value="source" className="relative mt-0">
            <div className="absolute right-3 top-3 z-10">
              <Button variant="secondary" size="xs" onClick={handleCopy}>
                {copied ? <><Check className="h-3 w-3 text-emerald-400" /> Kopyalandı</> : <><Copy className="h-3 w-3" /> Kopyala</>}
              </Button>
            </div>
            <pre className="overflow-auto bg-zinc-100 text-zinc-700 dark:bg-black/50 dark:text-zinc-400 p-5 font-mono text-xs leading-relaxed" style={{ height: "600px", tabSize: 2 }}>
              <code>{html}</code>
            </pre>
          </TabsContent>
        </div>
      </Tabs>
    </div>
  );
}

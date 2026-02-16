import { useState, useCallback, useMemo, useRef, useEffect } from "react";
import DOMPurify from "dompurify";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import {
  Clock,
  FileText,
  AlertTriangle,
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

  // XSS koruma — API'den gelen HTML'i sanitize et
  const sanitizedHtml = useMemo(
    () =>
      DOMPurify.sanitize(html, {
        WHOLE_DOCUMENT: true,
        ADD_TAGS: ["style", "link"],
        ADD_ATTR: ["target", "rel"],
      }),
    [html]
  );

  const handlePrint = useCallback(() => {
    try {
      const printWindow = window.open("", "_blank");
      if (!printWindow) {
        toast.error("Yazdırma penceresi açılamadı. Popup engelleyiciyi kontrol edin.");
        return;
      }
      printWindow.document.write(sanitizedHtml);
      // Varsayılan sayfa kenar boşlukları ekle
      const style = printWindow.document.createElement("style");
      style.textContent = "@page { margin: 15mm; } body { margin: 0; }";
      printWindow.document.head.appendChild(style);
      printWindow.document.close();
      printWindow.focus();
      printWindow.print();
      // Kullanıcı yazdırmayı bitirdikten sonra pencereyi kapat
      printWindow.onafterprint = () => printWindow.close();
    } catch (err) {
      console.error("Print failed:", err);
      toast.error("Yazdırma işlemi başarısız oldu.");
    }
  }, [sanitizedHtml]);

  const handleDownloadHtml = useCallback(() => {
    const blob = new Blob([sanitizedHtml], { type: "text/html;charset=utf-8" });
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url;
    a.download = "preview.html";
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    URL.revokeObjectURL(url);
  }, [sanitizedHtml]);

  const handleCopy = useCallback(async () => {
    try {
      await navigator.clipboard.writeText(html); // raw HTML for copy
      setCopied(true);
      clearTimeout(copyTimerRef.current);
      copyTimerRef.current = setTimeout(() => setCopied(false), 2000);
    } catch (err) {
      console.error("Clipboard write failed:", err);
      toast.error("Kopyalama başarısız. Tarayıcı izinlerini kontrol edin.");
    }
  }, [html]);

  return (
    <div className="space-y-4 animate-scale-in">
      {/* ── Meta ── */}
      <div className="rounded-xl border bg-card p-5">
        <div className="flex items-center justify-between">
          <div>
            <h3 className="text-[11px] font-semibold uppercase tracking-wider text-muted-foreground mb-3">
              Dönüşüm Bilgisi
            </h3>
            <div className="flex flex-wrap gap-2">
              <Badge
                variant="outline"
                className="gap-1.5 px-3 py-1.5 rounded-lg"
              >
                <Clock className="h-3 w-3" />
                {meta.durationMs} ms
              </Badge>
              <Badge
                variant="outline"
                className="gap-1.5 px-3 py-1.5 rounded-lg"
              >
                <FileText className="h-3 w-3" />
                {(meta.outputSize / 1024).toFixed(1)} KB
              </Badge>
              {meta.defaultUsed && (
                <Badge variant="secondary" className="px-3 py-1.5 rounded-lg">
                  Varsayılan XSLT
                </Badge>
              )}
              {meta.embeddedUsed && (
                <Badge variant="secondary" className="px-3 py-1.5 rounded-lg">
                  Gömülü XSLT
                </Badge>
              )}
              {meta.watermarkApplied && (
                <Badge variant="secondary" className="px-3 py-1.5 rounded-lg">
                  Filigranlı
                </Badge>
              )}
              {meta.customError && (
                <Badge
                  variant="destructive"
                  className="gap-1.5 px-3 py-1.5 rounded-lg"
                >
                  <AlertTriangle className="h-3 w-3" />
                  {meta.customError}
                </Badge>
              )}
            </div>
          </div>

          {/* ── Actions ── */}
          <div className="flex items-center gap-1.5 shrink-0 ml-4">
            <Button
              variant="outline"
              size="sm"
              className="h-8 gap-1.5 rounded-lg text-xs"
              onClick={handlePrint}
              title="Yazdır / PDF"
            >
              <Printer className="h-3.5 w-3.5" />
              <span className="hidden sm:inline">Yazdır</span>
            </Button>
            <Button
              variant="outline"
              size="sm"
              className="h-8 gap-1.5 rounded-lg text-xs"
              onClick={handleDownloadHtml}
              title="HTML İndir"
            >
              <Download className="h-3.5 w-3.5" />
              <span className="hidden sm:inline">İndir</span>
            </Button>
          </div>
        </div>
      </div>

      {/* ── Tabbed Preview ── */}
      <Tabs defaultValue="preview" className="w-full">
        <div className="rounded-xl border overflow-hidden">
          {/* ── Tab Header ── */}
          <div className="px-5 py-3 border-b bg-card flex items-center justify-between">
            <div className="flex items-center gap-3">
              <div className="flex gap-1.5">
                <div className="h-2.5 w-2.5 rounded-full bg-destructive/50" />
                <div className="h-2.5 w-2.5 rounded-full bg-warning/50" />
                <div className="h-2.5 w-2.5 rounded-full bg-success/50" />
              </div>
              <TabsList className="h-8 bg-muted/50 rounded-lg p-0.5">
                <TabsTrigger
                  value="preview"
                  className="h-7 gap-1.5 text-[11px] px-3 rounded-md data-[state=active]:shadow-sm"
                >
                  <Eye className="h-3 w-3" />
                  Önizleme
                </TabsTrigger>
                <TabsTrigger
                  value="source"
                  className="h-7 gap-1.5 text-[11px] px-3 rounded-md data-[state=active]:shadow-sm"
                >
                  <Code2 className="h-3 w-3" />
                  HTML
                </TabsTrigger>
              </TabsList>
            </div>

            <span className="text-[11px] text-muted-foreground font-mono hidden sm:block">
              preview.html
            </span>
          </div>

          {/* ── Preview Tab ── */}
          <TabsContent value="preview" className="mt-0">
            <iframe
              srcDoc={sanitizedHtml}
              title="HTML Preview"
              className="w-full bg-white"
              style={{ height: "600px" }}
              sandbox="allow-same-origin"
            />
          </TabsContent>

          {/* ── Source Tab ── */}
          <TabsContent value="source" className="mt-0 relative">
            <div className="absolute top-3 right-3 z-10">
              <Button
                variant="secondary"
                size="sm"
                className="h-7 gap-1.5 text-[11px] rounded-md shadow-sm"
                onClick={handleCopy}
              >
                {copied ? (
                  <>
                    <Check className="h-3 w-3 text-success" />
                    Kopyalandı
                  </>
                ) : (
                  <>
                    <Copy className="h-3 w-3" />
                    Kopyala
                  </>
                )}
              </Button>
            </div>
            <pre className="overflow-auto bg-zinc-950 text-zinc-300 p-5 text-xs font-mono leading-relaxed" style={{ height: "600px", tabSize: 2 }}>
              <code>{html}</code>
            </pre>
          </TabsContent>
        </div>
      </Tabs>
    </div>
  );
}

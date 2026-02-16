import {
  Accordion,
  AccordionContent,
  AccordionItem,
  AccordionTrigger,
} from "@/components/ui/accordion";
import { Badge } from "@/components/ui/badge";
import {
  Package,
  ExternalLink,
  FileText,
  CheckCircle2,
  AlertCircle,
} from "lucide-react";
import { Skeleton } from "@/components/ui/skeleton";
import { usePackages } from "@/api/hooks";
import { FileTree } from "./file-tree";

export function PackageList() {
  const { data, isLoading, error } = usePackages();

  return (
    <div className="rounded-xl border bg-card shadow-sm overflow-hidden">
      <div className="p-6 pb-5">
        <div className="flex items-start justify-between gap-4">
          <div>
            <div className="flex h-10 w-10 items-center justify-center rounded-lg bg-primary/10 text-primary mb-3">
              <Package className="h-5 w-5" />
            </div>
            <h3 className="text-base font-bold">GİB Paket Tanımları</h3>
            <p className="text-xs text-muted-foreground mt-1">
              Sync edilebilecek resmi GİB paketleri
            </p>
          </div>
          {data && (
            <div className="flex items-center gap-2 shrink-0">
              <Badge
                variant={data.enabled ? "default" : "secondary"}
                className="rounded-lg"
              >
                {data.enabled ? "Aktif" : "Devre Dışı"}
              </Badge>
              <Badge variant="outline" className="font-mono rounded-lg">
                {data.packageCount} paket
              </Badge>
            </div>
          )}
        </div>
      </div>

      <div className="border-t bg-muted/20 p-5">
        {error ? (
          <div className="rounded-lg border border-dashed border-destructive/30 bg-destructive/5 p-4 text-xs text-destructive flex items-start gap-2">
            <AlertCircle className="h-3.5 w-3.5 shrink-0 mt-0.5" />
            <span>Paket bilgileri yüklenirken hata oluştu.</span>
          </div>
        ) : isLoading ? (
          <div className="space-y-4 p-5">
            <div className="flex items-center gap-3">
              <Skeleton className="h-8 w-8 rounded-lg shrink-0" />
              <div className="space-y-2 flex-1">
                <Skeleton className="h-4 w-2/5" />
                <Skeleton className="h-3 w-4/5" />
              </div>
            </div>
            {[1, 2, 3].map((i) => (
              <div
                key={i}
                className="flex items-center gap-3 p-3 rounded-lg border bg-card"
              >
                <Skeleton className="h-8 w-8 rounded-lg shrink-0" />
                <div className="space-y-2 flex-1">
                  <Skeleton className="h-4 w-2/5" />
                  <Skeleton className="h-3 w-3/5" />
                </div>
              </div>
            ))}
          </div>
        ) : data?.packages.length === 0 ? (
          <p className="text-sm text-muted-foreground text-center py-12">
            Tanımlı paket bulunamadı.
          </p>
        ) : (
          <Accordion type="single" collapsible className="w-full space-y-2">
            {data?.packages.map((pkg) => (
              <AccordionItem
                key={pkg.id}
                value={pkg.id}
                className="border rounded-lg bg-card overflow-hidden"
              >
                <AccordionTrigger className="text-sm px-4 py-3 hover:no-underline hover:bg-muted/20">
                  <div className="flex items-center gap-3 min-w-0 flex-1">
                    <div className="flex h-8 w-8 items-center justify-center rounded-lg bg-primary/10 shrink-0">
                      <Package className="h-3.5 w-3.5 text-primary" />
                    </div>
                    <div className="text-left min-w-0 flex-1">
                      <span className="font-bold block">
                        {pkg.displayName}
                      </span>
                      <span className="text-[10px] text-muted-foreground font-mono">
                        {pkg.id}
                      </span>
                    </div>
                    <div className="shrink-0 mr-2">
                      {pkg.totalLoadedFileCount > 0 ? (
                        <Badge
                          variant="outline"
                          className="gap-1 rounded-lg text-[10px] border-success/30 text-success bg-success/5"
                        >
                          <CheckCircle2 className="h-3 w-3" />
                          {pkg.totalLoadedFileCount} dosya
                        </Badge>
                      ) : (
                        <Badge
                          variant="outline"
                          className="gap-1 rounded-lg text-[10px] border-warning/30 text-warning bg-warning/5"
                        >
                          <AlertCircle className="h-3 w-3" />
                          Boş
                        </Badge>
                      )}
                    </div>
                  </div>
                </AccordionTrigger>
                <AccordionContent>
                  <div className="space-y-4 text-xs px-4 pb-4 pl-[60px]">
                    <p className="text-muted-foreground leading-relaxed">
                      {pkg.description}
                    </p>

                    {/* ── Yüklü Dosyalar (Tree) ── */}
                    {pkg.totalLoadedFileCount > 0 && pkg.fileTrees && (
                      <div>
                        <p className="font-bold mb-2 text-xs flex items-center gap-1.5">
                          <FileText className="h-3.5 w-3.5 text-success" />
                          Yüklü Dosyalar
                          <Badge
                            variant="secondary"
                            className="text-[10px] ml-1 rounded-md"
                          >
                            {pkg.totalLoadedFileCount}
                          </Badge>
                        </p>
                        <div className="space-y-3">
                          {Object.entries(pkg.fileTrees).map(([dir, tree]) => (
                            <div
                              key={dir}
                              className="rounded-lg border bg-muted/10 p-3"
                            >
                              <FileTree
                                nodes={tree}
                                rootLabel={
                                  dir.endsWith("/") ? dir : `${dir}/`
                                }
                                defaultExpanded
                              />
                            </div>
                          ))}
                        </div>
                      </div>
                    )}

                    {pkg.totalLoadedFileCount === 0 && (
                      <div className="rounded-lg border border-dashed border-warning/30 bg-warning/5 p-3 text-[11px] text-warning flex items-start gap-2">
                        <AlertCircle className="h-3.5 w-3.5 shrink-0 mt-0.5" />
                        <span>
                          Bu paket için henüz dosya yüklenmemiş. GİB paket
                          sync'i çalıştırın veya dosyaları manuel olarak
                          kopyalayın.
                        </span>
                      </div>
                    )}

                    {/* ── İndirme URL ── */}
                    <div className="flex items-center gap-1.5">
                      <ExternalLink className="h-3 w-3 text-muted-foreground shrink-0" />
                      <a
                        href={pkg.downloadUrl}
                        target="_blank"
                        rel="noopener noreferrer"
                        className="text-primary hover:underline font-mono break-all"
                      >
                        {pkg.downloadUrl}
                      </a>
                    </div>

                    {/* ── Dosya Eşlemesi ── */}
                    <div>
                      <p className="font-bold mb-2 text-xs">Dosya Eşlemesi</p>
                      <div className="space-y-1.5">
                        {pkg.fileMapping.map((fm) => (
                          <div
                            key={`fm-${fm.zipPathPattern}-${fm.targetDir}`}
                            className="flex items-center gap-2 font-mono rounded-lg bg-muted/40 px-3 py-2"
                          >
                            <span className="text-muted-foreground truncate text-[11px]">
                              {fm.zipPathPattern}
                            </span>
                            <span className="text-muted-foreground/40 shrink-0">
                              &rarr;
                            </span>
                            <span className="truncate text-[11px]">
                              {fm.targetDir}
                            </span>
                          </div>
                        ))}
                      </div>
                    </div>
                  </div>
                </AccordionContent>
              </AccordionItem>
            ))}
          </Accordion>
        )}

        {data?.currentAssetSource && (
          <div className="mt-4 text-xs text-muted-foreground border-t pt-4">
            Asset kaynağı:{" "}
            <code className="font-mono text-foreground bg-muted px-2 py-0.5 rounded-lg">
              {data.currentAssetSource}
            </code>
          </div>
        )}
      </div>
    </div>
  );
}

import { TransformForm } from "@/components/transform/transform-form";
import { HtmlPreview } from "@/components/transform/html-preview";
import { useTransform } from "@/api/hooks";
import { toast } from "sonner";
import { AlertCircle, FileCode2 } from "lucide-react";
import { Button } from "@/components/ui/button";

export default function TransformPage() {
  const transformMutation = useTransform();

  const handleTransform = (
    params: Parameters<typeof transformMutation.mutate>[0]
  ) => {
    transformMutation.mutate(params, {
      onError: (error) => {
        toast.error("Dönüşüm isteği başarısız", {
          description:
            error instanceof Error ? error.message : "Bilinmeyen hata",
        });
      },
    });
  };

  return (
    <div className="space-y-6">
      {/* ── Form (full-width, 2-col internally) ── */}
      <div className="rounded-xl border bg-card shadow-sm p-6 sm:p-7">
        <div className="flex items-center gap-2 mb-6">
          <div className="h-2 w-2 rounded-full bg-primary" />
          <h2 className="text-sm font-semibold uppercase tracking-wider text-muted-foreground">
            Parametreler
          </h2>
        </div>
        <TransformForm
          onSubmit={handleTransform}
          isLoading={transformMutation.isPending}
        />
      </div>

      {/* ── Preview (below) ── */}
      <div aria-live="polite" aria-atomic="true">
      {transformMutation.isError ? (
        <div className="rounded-xl border border-destructive/30 bg-destructive/5 flex flex-col items-center justify-center py-12 px-8" role="alert">
          <div className="relative mb-5">
            <div className="relative flex h-16 w-16 items-center justify-center rounded-xl bg-destructive/10 border border-destructive/20">
              <AlertCircle className="h-7 w-7 text-destructive" />
            </div>
          </div>
          <p className="text-base font-semibold text-destructive">
            Dönüşüm başarısız
          </p>
          <p className="text-sm text-muted-foreground mt-1.5 text-center max-w-sm leading-relaxed">
            {transformMutation.error instanceof Error
              ? transformMutation.error.message
              : "Bilinmeyen bir hata oluştu. Lütfen tekrar deneyin."}
          </p>
          <Button
            variant="outline"
            size="sm"
            className="mt-4"
            onClick={() => transformMutation.reset()}
          >
            Temizle
          </Button>
        </div>
      ) : transformMutation.data ? (
        <HtmlPreview
          html={transformMutation.data.html}
          meta={transformMutation.data.meta}
        />
      ) : (
        <div className="rounded-xl border border-dashed flex flex-col items-center justify-center py-16 px-8">
          <div className="relative mb-6">
            <div className="absolute inset-0 rounded-full bg-primary/10 blur-2xl scale-150" />
            <div className="relative flex h-20 w-20 items-center justify-center rounded-xl bg-gradient-to-br from-primary/15 to-primary/5 border border-primary/10">
              <FileCode2 className="h-8 w-8 text-primary/60" />
            </div>
          </div>
          <p className="text-base font-semibold text-foreground/80">
            Önizleme bekleniyor
          </p>
          <p className="text-sm text-muted-foreground mt-1.5 text-center max-w-[260px] leading-relaxed">
            XML dosyanızı yükleyip dönüşümü başlatın.
          </p>
        </div>
      )}
      </div>
    </div>
  );
}

import { ValidationForm } from "@/components/validation/validation-form";
import { ValidationResult } from "@/components/validation/validation-result";
import { useValidate } from "@/api/hooks";
import { toast } from "sonner";

export default function ValidatePage() {
  const validateMutation = useValidate();

  const handleValidate = (
    params: Parameters<typeof validateMutation.mutate>[0]
  ) => {
    validateMutation.mutate(params, {
      onSuccess: (data) => {
        if (data.errorMessage) {
          toast.error("Doğrulama hatası", {
            description: data.errorMessage,
          });
        }
      },
      onError: (error) => {
        toast.error("Doğrulama isteği başarısız", {
          description:
            error instanceof Error ? error.message : "Bilinmeyen hata",
        });
      },
    });
  };

  const result = validateMutation.data?.result;

  return (
    <div className="space-y-8">
      {/* ── Form Card (full width) ── */}
      <div className="rounded-xl border bg-card shadow-sm p-6 sm:p-7">
        <div className="flex items-center gap-2 mb-6">
          <div className="h-2 w-2 rounded-full bg-primary" />
          <h2 className="text-sm font-semibold uppercase tracking-wider text-muted-foreground">
            Parametreler
          </h2>
        </div>
        <ValidationForm
          onSubmit={handleValidate}
          isLoading={validateMutation.isPending}
        />
      </div>

      {/* ── Results ── */}
      <div aria-live="polite" aria-atomic="true">
        {result && <ValidationResult result={result} />}
      </div>
    </div>
  );
}

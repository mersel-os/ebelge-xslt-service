import { TransformForm } from "@/components/transform/transform-form";
import { HtmlPreview } from "@/components/transform/html-preview";
import { useTransform } from "@/api/hooks";
import { toast } from "sonner";
import { AlertCircle } from "lucide-react";
import { Button } from "@/components/ui/button";
import { motion } from "framer-motion";

const fade = {
  hidden: { opacity: 0, y: 20 },
  show: (i: number) => ({
    opacity: 1,
    y: 0,
    transition: { duration: 0.5, delay: i * 0.1, ease: [0.16, 1, 0.3, 1] as const },
  }),
};

export default function TransformPage() {
  const transformMutation = useTransform();

  const handleTransform = (
    params: Parameters<typeof transformMutation.mutate>[0]
  ) => {
    transformMutation.mutate(params, {
      onError: (error) => {
        toast.error("Dönüşüm isteği başarısız", {
          description: error instanceof Error ? error.message : "Bilinmeyen hata",
        });
      },
    });
  };

  return (
    <div className="space-y-8">
      {/* Form -- full width */}
      <motion.div
        variants={fade}
        custom={0}
        initial="hidden"
        animate="show"
        className="glass p-8"
      >
        <TransformForm
          onSubmit={handleTransform}
          isLoading={transformMutation.isPending}
        />
      </motion.div>

      {/* Preview -- full width */}
      {transformMutation.isError ? (
        <motion.div
          variants={fade}
          custom={1}
          initial="hidden"
          animate="show"
          className="glass p-8"
          role="alert"
        >
          <div className="flex flex-col items-center justify-center py-12 text-center">
            <div className="mb-5 flex h-14 w-14 items-center justify-center rounded-2xl bg-red-500/10">
              <AlertCircle className="h-6 w-6 text-red-600 dark:text-red-400" />
            </div>
            <p className="text-sm font-semibold text-red-600 dark:text-red-400">Dönüşüm başarısız</p>
            <p className="mt-2 max-w-sm text-xs text-muted-foreground">
              {transformMutation.error instanceof Error
                ? transformMutation.error.message
                : "Bilinmeyen bir hata oluştu."}
            </p>
            <Button variant="outline" size="sm" className="mt-5" onClick={() => transformMutation.reset()}>
              Temizle
            </Button>
          </div>
        </motion.div>
      ) : transformMutation.data ? (
        <motion.div
          variants={fade}
          custom={1}
          initial="hidden"
          animate="show"
          className="glass p-8"
        >
          <HtmlPreview html={transformMutation.data.html} meta={transformMutation.data.meta} />
        </motion.div>
      ) : null}
    </div>
  );
}

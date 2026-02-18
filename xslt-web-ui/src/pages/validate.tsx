import { ValidationForm } from "@/components/validation/validation-form";
import { ValidationResult } from "@/components/validation/validation-result";
import { useValidate } from "@/api/hooks";
import { toast } from "sonner";
import { motion } from "framer-motion";

const fade = {
  hidden: { opacity: 0, y: 20 },
  show: (i: number) => ({
    opacity: 1,
    y: 0,
    transition: { duration: 0.5, delay: i * 0.1, ease: [0.16, 1, 0.3, 1] },
  }),
};

export default function ValidatePage() {
  const validateMutation = useValidate();

  const handleValidate = (
    params: Parameters<typeof validateMutation.mutate>[0]
  ) => {
    validateMutation.mutate(params, {
      onSuccess: (data) => {
        if (data.errorMessage) {
          toast.error("Doğrulama hatası", { description: data.errorMessage });
        }
      },
      onError: (error) => {
        toast.error("Doğrulama isteği başarısız", {
          description: error instanceof Error ? error.message : "Bilinmeyen hata",
        });
      },
    });
  };

  const result = validateMutation.data?.result;

  return (
    <div className="space-y-8">
      <motion.div
        variants={fade}
        custom={0}
        initial="hidden"
        animate="show"
        className="glass p-8"
      >
        <ValidationForm
          onSubmit={handleValidate}
          isLoading={validateMutation.isPending}
        />
      </motion.div>

      {result && (
        <motion.div
          variants={fade}
          custom={0}
          initial="hidden"
          animate="show"
          className="glass p-8"
          aria-live="polite"
        >
          <ValidationResult result={result} />
        </motion.div>
      )}
    </div>
  );
}

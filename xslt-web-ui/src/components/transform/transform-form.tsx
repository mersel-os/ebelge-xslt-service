import { useState } from "react";
import { Button } from "@/components/ui/button";
import { FormField } from "@/components/ui/form-field";
import { Input } from "@/components/ui/input";
import { Switch } from "@/components/ui/switch";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { FileDropzone } from "@/components/validation/file-dropzone";
import { Loader2, Sparkles } from "lucide-react";
import { TRANSFORM_TYPES, TRANSFORM_TYPE_LABELS } from "@/api/types";

interface TransformFormProps {
  onSubmit: (params: {
    document: File;
    transformType: string;
    watermarkText?: string;
    transformer?: File;
    useEmbeddedXslt?: boolean;
  }) => void;
  isLoading: boolean;
}

export function TransformForm({ onSubmit, isLoading }: TransformFormProps) {
  const [file, setFile] = useState<File | null>(null);
  const [transformType, setTransformType] = useState("INVOICE");
  const [watermarkText, setWatermarkText] = useState("");
  const [customXslt, setCustomXslt] = useState<File | null>(null);
  const [useEmbedded, setUseEmbedded] = useState(true);

  const handleSubmit = () => {
    if (!file) return;
    onSubmit({
      document: file,
      transformType,
      watermarkText: watermarkText || undefined,
      transformer: customXslt || undefined,
      useEmbeddedXslt: useEmbedded,
    });
  };

  return (
    <form onSubmit={(e) => { e.preventDefault(); handleSubmit(); }} className="space-y-5">
      {/* ── Dropzones: equal height side by side ── */}
      <div className="grid gap-5 lg:grid-cols-2">
        <FileDropzone
          file={file}
          onFileChange={setFile}
          label="Dönüştürülecek XML dosyası"
        />
        <FileDropzone
          file={customXslt}
          onFileChange={setCustomXslt}
          accept=".xslt,.xsl"
          label="Özel XSLT dosyası (opsiyonel)"
        />
      </div>

      {/* ── Settings row ── */}
      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
        <FormField
          label="Varsayılan Dönüşüm Şablonu"
          description="Hata olması durumunda kullanılacak"
        >
          <Select value={transformType} onValueChange={setTransformType}>
            <SelectTrigger className="h-10 rounded-lg">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              {TRANSFORM_TYPES.map((t) => (
                <SelectItem key={t} value={t}>
                  {TRANSFORM_TYPE_LABELS[t]}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </FormField>

        <FormField label="Filigran Metni (opsiyonel)" description="Dönüştürülmüş HTML'e filigran eklenir.">
          <Input
            value={watermarkText}
            onChange={(e) => setWatermarkText(e.target.value)}
            placeholder="örnek: TASLAK"
            className="h-10 rounded-lg"
          />
        </FormField>

        <FormField label="Gömülü XSLT" description="UBL-TR belgelerindeki gömülü XSLT şablonunu kullan">
          <div className="flex items-center justify-between rounded-lg border h-10 px-3">
            <span className="text-sm text-muted-foreground">
              Belgedeki şablonu kullan
            </span>
            <Switch checked={useEmbedded} onCheckedChange={setUseEmbedded} />
          </div>
        </FormField>
      </div>

      {/* ── Submit ── */}
      <Button
        type="submit"
        disabled={!file || isLoading}
        className="w-full h-12 rounded-lg text-sm font-bold shadow-lg shadow-primary/20 hover:shadow-primary/30 transition-shadow"
        size="lg"
      >
        {isLoading ? (
          <>
            <Loader2 className="mr-2 h-4 w-4 animate-spin" />
            Dönüştürülüyor...
          </>
        ) : (
          <>
            <Sparkles className="mr-2 h-4 w-4" />
            Dönüştür
          </>
        )}
      </Button>
    </form>
  );
}

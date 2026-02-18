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
    <form onSubmit={(e) => { e.preventDefault(); handleSubmit(); }} className="space-y-6">
      {/* Dropzones side by side */}
      <div className="grid gap-5 md:grid-cols-2">
        <FileDropzone file={file} onFileChange={setFile} label="Dönüştürülecek XML dosyası" />
        <FileDropzone file={customXslt} onFileChange={setCustomXslt} accept=".xslt,.xsl" label="Özel XSLT dosyası (opsiyonel)" />
      </div>

      {/* Settings row */}
      <div className="grid gap-5 md:grid-cols-3">
        <FormField label="Dönüşüm Şablonu">
          <Select value={transformType} onValueChange={setTransformType}>
            <SelectTrigger>
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              {TRANSFORM_TYPES.map((t) => (
                <SelectItem key={t} value={t}>{TRANSFORM_TYPE_LABELS[t]}</SelectItem>
              ))}
            </SelectContent>
          </Select>
        </FormField>

        <FormField label="Filigran (opsiyonel)">
          <Input
            value={watermarkText}
            onChange={(e) => setWatermarkText(e.target.value)}
            placeholder="örnek: TASLAK"
          />
        </FormField>

        <div className="flex items-end">
          <div className="flex w-full items-center justify-between rounded-xl border border-border bg-muted px-4 py-3">
            <div>
              <p className="text-sm font-medium text-foreground">Gömülü XSLT</p>
              <p className="text-[11px] text-muted-foreground">Belgedeki şablonu kullan</p>
            </div>
            <Switch checked={useEmbedded} onCheckedChange={setUseEmbedded} />
          </div>
        </div>
      </div>

      {/* Submit */}
      <Button
        type="submit"
        disabled={!file || isLoading}
        className="h-12 w-full glow-primary-hover md:w-auto md:min-w-[200px]"
        size="lg"
      >
        {isLoading ? (
          <><Loader2 className="mr-2 h-4 w-4 animate-spin" /> Dönüştürülüyor...</>
        ) : (
          <><Sparkles className="mr-2 h-4 w-4" /> Dönüştür</>
        )}
      </Button>
    </form>
  );
}

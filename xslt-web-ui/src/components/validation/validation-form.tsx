import { useState, useMemo, useCallback } from "react";
import { Button } from "@/components/ui/button";
import { FormField } from "@/components/ui/form-field";
import { Input } from "@/components/ui/input";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { FileDropzone } from "./file-dropzone";
import { Loader2, Sparkles, Plus, Trash2, ChevronDown, Variable } from "lucide-react";
import { useProfiles, type SchematronParam } from "@/api/hooks";
import { useAuth } from "@/hooks/use-auth";
import { cn } from "@/lib/utils";

interface ValidationFormProps {
  onSubmit: (params: {
    source: File;
    profile?: string;
    parameters?: SchematronParam[];
  }) => void;
  isLoading: boolean;
}

export function ValidationForm({ onSubmit, isLoading }: ValidationFormProps) {
  const [file, setFile] = useState<File | null>(null);
  const [profile, setProfile] = useState("");
  const [params, setParams] = useState<SchematronParam[]>([]);
  const [paramsOpen, setParamsOpen] = useState(false);

  const { authenticated } = useAuth();
  const { data: profilesData } = useProfiles({ enabled: authenticated });
  const profileNames = useMemo(
    () => (profilesData ? Object.keys(profilesData.profiles) : []),
    [profilesData]
  );

  const filledParamCount = useMemo(
    () => params.filter((p) => p.key.trim() !== "").length,
    [params]
  );

  const addParam = useCallback(() => {
    setParams((prev) => [...prev, { key: "", value: "" }]);
    setParamsOpen(true);
  }, []);

  const removeParam = useCallback((index: number) => {
    setParams((prev) => prev.filter((_, i) => i !== index));
  }, []);

  const updateParam = useCallback(
    (index: number, field: "key" | "value", val: string) => {
      setParams((prev) =>
        prev.map((p, i) => (i === index ? { ...p, [field]: val } : p))
      );
    },
    []
  );

  const handleSubmit = () => {
    if (!file) return;
    const validParams = params.filter((p) => p.key.trim() !== "");
    onSubmit({
      source: file,
      profile: profile && profile !== "none" ? profile : undefined,
      parameters: validParams.length > 0 ? validParams : undefined,
    });
  };

  return (
    <form onSubmit={(e) => { e.preventDefault(); handleSubmit(); }} className="space-y-6">
      <FileDropzone file={file} onFileChange={setFile} label="Doğrulanacak XML dosyası" />

      <FormField label="Profil">
        <Select value={profile} onValueChange={setProfile}>
          <SelectTrigger>
            <SelectValue placeholder="Opsiyonel" />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="none">Profil yok</SelectItem>
            {profileNames.map((p) => (
              <SelectItem key={p} value={p}>{p}</SelectItem>
            ))}
          </SelectContent>
        </Select>
      </FormField>

      {/* Schematron Parametreleri */}
      <div className="rounded-lg border border-border/50 bg-muted/30">
        <button
          type="button"
          onClick={() => {
            if (params.length === 0) addParam();
            else setParamsOpen((o) => !o);
          }}
          className="flex w-full items-center justify-between px-4 py-3 text-sm font-medium text-muted-foreground hover:text-foreground transition-colors"
        >
          <span className="flex items-center gap-2">
            <Variable className="h-4 w-4" />
            Schematron Parametreleri
            {filledParamCount > 0 && (
              <span className="inline-flex items-center rounded-md bg-primary/10 px-2 py-0.5 text-xs font-medium text-primary">
                {filledParamCount}
              </span>
            )}
          </span>
          <ChevronDown
            className={cn(
              "h-4 w-4 transition-transform duration-200",
              paramsOpen && "rotate-180"
            )}
          />
        </button>

        {paramsOpen && params.length > 0 && (
          <div className="border-t border-border/50 px-4 pb-4 pt-3 space-y-3">
            <div className="grid grid-cols-[1fr_1fr_auto] gap-2 text-xs font-medium text-muted-foreground px-1">
              <span>Parametre Adı ($variable)</span>
              <span>Değer</span>
              <span className="w-8" />
            </div>

            {params.map((param, i) => (
              <div key={i} className="grid grid-cols-[1fr_1fr_auto] gap-2 items-center">
                <Input
                  placeholder="ornek_parametre"
                  value={param.key}
                  onChange={(e) => updateParam(i, "key", e.target.value)}
                  className="h-9 font-mono text-sm"
                />
                <Input
                  placeholder="değer"
                  value={param.value}
                  onChange={(e) => updateParam(i, "value", e.target.value)}
                  className="h-9 text-sm"
                />
                <Button
                  type="button"
                  variant="ghost"
                  size="icon-xs"
                  onClick={() => removeParam(i)}
                  className="text-muted-foreground hover:text-destructive"
                >
                  <Trash2 className="h-3.5 w-3.5" />
                </Button>
              </div>
            ))}

            <Button
              type="button"
              variant="outline"
              size="sm"
              onClick={addParam}
              className="w-full mt-1"
            >
              <Plus className="h-3.5 w-3.5 mr-1.5" />
              Parametre Ekle
            </Button>
          </div>
        )}
      </div>

      <Button
        type="submit"
        disabled={!file || isLoading}
        className="h-12 w-full glow-primary-hover"
        size="lg"
      >
        {isLoading ? (
          <><Loader2 className="mr-2 h-4 w-4 animate-spin" /> Doğrulanıyor...</>
        ) : (
          <><Sparkles className="mr-2 h-4 w-4" /> Doğrula</>
        )}
      </Button>
    </form>
  );
}

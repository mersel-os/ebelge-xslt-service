import { useState } from "react";
import { Button } from "@/components/ui/button";
import { FormField } from "@/components/ui/form-field";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { FileDropzone } from "./file-dropzone";
import { Loader2, Sparkles } from "lucide-react";
import { useProfiles } from "@/api/hooks";
import { useAuth } from "@/hooks/use-auth";
import { useMemo } from "react";

interface ValidationFormProps {
  onSubmit: (params: {
    source: File;
    ublTrMainSchematronType?: string;
    profile?: string;
  }) => void;
  isLoading: boolean;
}

const UBLTR_MAIN_SUBTYPES = [
  { value: "efatura", label: "E-Fatura" },
  { value: "earchive", label: "E-Arşiv Fatura" },
];

export function ValidationForm({ onSubmit, isLoading }: ValidationFormProps) {
  const [file, setFile] = useState<File | null>(null);
  const [ublTrSubType, setUblTrSubType] = useState("");
  const [profile, setProfile] = useState("");

  const { authenticated } = useAuth();
  const { data: profilesData } = useProfiles({ enabled: authenticated });
  const profileNames = useMemo(
    () => (profilesData ? Object.keys(profilesData.profiles) : []),
    [profilesData]
  );

  const handleSubmit = () => {
    if (!file) return;
    onSubmit({
      source: file,
      ublTrMainSchematronType: ublTrSubType || undefined,
      profile: profile && profile !== "none" ? profile : undefined,
    });
  };

  return (
    <form onSubmit={(e) => { e.preventDefault(); handleSubmit(); }} className="space-y-5">
      <FileDropzone
        file={file}
        onFileChange={setFile}
        label="Doğrulanacak XML dosyası"
      />

      {/* Optional fields in responsive grid */}
      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-2">
        <FormField label="UBL-TR Fatura Alt Tipi">
          <Select value={ublTrSubType} onValueChange={setUblTrSubType}>
            <SelectTrigger>
              <SelectValue placeholder="Otomatik (opsiyonel)" />
            </SelectTrigger>
            <SelectContent>
              {UBLTR_MAIN_SUBTYPES.map((t) => (
                <SelectItem key={t.value} value={t.value}>{t.label}</SelectItem>
              ))}
            </SelectContent>
          </Select>
        </FormField>

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

      </div>

      <Button
        type="submit"
        disabled={!file || isLoading}
        className="w-full h-12 rounded-lg text-sm font-bold shadow-lg shadow-primary/20 hover:shadow-primary/30 transition-shadow"
        size="lg"
      >
        {isLoading ? (
          <>
            <Loader2 className="mr-2 h-4 w-4 animate-spin" />
            Doğrulanıyor...
          </>
        ) : (
          <>
            <Sparkles className="mr-2 h-4 w-4" />
            Doğrula
          </>
        )}
      </Button>
    </form>
  );
}

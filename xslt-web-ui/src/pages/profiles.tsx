import { useState, useMemo, memo } from "react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  Shield,
  ArrowRight,
  Plus,
  FileCode,
  ScrollText,
  Info,
  ListChecks,
} from "lucide-react";
import { Skeleton } from "@/components/ui/skeleton";
import { toast } from "sonner";
import { useProfiles, useSaveProfile, useDeleteProfile } from "@/api/hooks";
import { useQueryClient } from "@tanstack/react-query";
import { ProfileEditor } from "@/components/admin/profile-editor";
import type { ProfileInfo } from "@/api/types";
import { motion } from "framer-motion";

const ProfileCard = memo(function ProfileCard({
  name,
  info,
  onSelect,
  index,
}: {
  name: string;
  info: ProfileInfo;
  onSelect: () => void;
  index: number;
}) {
  return (
    <motion.button
      onClick={onSelect}
      initial={{ opacity: 0, y: 16 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.4, delay: index * 0.05, ease: [0.16, 1, 0.3, 1] as const }}
      className="glass group p-6 text-left transition-all duration-300 hover:bg-accent hover:border-border"
    >
      <div className="mb-3 flex items-center gap-3">
        <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-xl bg-muted border border-border">
          <Shield className="h-4 w-4 text-foreground/70" />
        </div>
        <span className="truncate text-sm font-semibold text-foreground">{name}</span>
      </div>
      <p className="mb-4 line-clamp-2 min-h-8 text-xs leading-relaxed text-muted-foreground">
        {info.description || "Açıklama yok"}
      </p>
      <div className="flex flex-wrap items-center gap-1.5">
        <Badge variant="secondary" className="text-[10px] font-mono">
          {info.suppressionCount} kural
        </Badge>
        {info.xsdOverrides && Object.keys(info.xsdOverrides).length > 0 && (
          <Badge variant="outline" className="gap-1 text-[10px] font-mono">
            <FileCode className="h-2.5 w-2.5" />
            {Object.values(info.xsdOverrides).flat().length} XSD
          </Badge>
        )}
        {info.schematronRules && Object.keys(info.schematronRules).length > 0 && (
          <Badge variant="outline" className="gap-1 text-[10px] font-mono">
            <ScrollText className="h-2.5 w-2.5" />
            {Object.values(info.schematronRules).flat().length} ek kural
          </Badge>
        )}
        {info.extends && (
          <Badge variant="outline" className="gap-1 text-[10px] text-muted-foreground">
            <ArrowRight className="h-2.5 w-2.5" />
            {info.extends}
          </Badge>
        )}
      </div>
    </motion.button>
  );
});

export default function ProfilesPage() {
  const queryClient = useQueryClient();
  const { data, isLoading, error } = useProfiles();
  const saveProfile = useSaveProfile();
  const deleteProfile = useDeleteProfile();

  const [editorOpen, setEditorOpen] = useState(false);
  const [editingProfile, setEditingProfile] = useState<{ name: string; info: ProfileInfo } | null>(null);

  const profiles = data?.profiles;
  const profileNames = useMemo(() => (profiles ? Object.keys(profiles) : []), [profiles]);

  const handleCreate = () => { setEditingProfile(null); setEditorOpen(true); };
  const handleEdit = (name: string, info: ProfileInfo) => { setEditingProfile({ name, info }); setEditorOpen(true); };

  const handleSave = (formData: {
    name: string;
    description: string;
    extendsProfile: string;
    suppressions: { match: string; pattern: string; scope: string[]; description: string }[];
    xsdOverrides: Record<string, { element: string; minOccurs?: string; maxOccurs?: string }[]>;
    schematronRules: Record<string, { context: string; test: string; message: string; id?: string }[]>;
  }) => {
    saveProfile.mutate(
      {
        name: formData.name,
        description: formData.description,
        extendsProfile: formData.extendsProfile || undefined,
        suppressions: formData.suppressions.map((s) => ({
          match: s.match, pattern: s.pattern,
          scope: s.scope.length > 0 ? s.scope : undefined,
          description: s.description || undefined,
        })),
        xsdOverrides: formData.xsdOverrides,
        schematronRules: formData.schematronRules,
      },
      {
        onSuccess: () => {
          toast.success(editingProfile ? `"${formData.name}" güncellendi.` : `"${formData.name}" oluşturuldu.`);
          setEditorOpen(false);
          queryClient.invalidateQueries({ queryKey: ["profiles"] });
        },
        onError: () => toast.error("Profil kaydedilirken hata oluştu."),
      }
    );
  };

  const handleDelete = (name: string) => {
    if (!window.confirm(`"${name}" profilini silmek istediğinizden emin misiniz?`)) return;
    deleteProfile.mutate(name, {
      onSuccess: () => { toast.success(`"${name}" silindi.`); setEditorOpen(false); queryClient.invalidateQueries({ queryKey: ["profiles"] }); },
      onError: () => toast.error("Profil silinirken hata oluştu."),
    });
  };

  if (isLoading) {
    return (
      <div className="grid gap-5 sm:grid-cols-2 lg:grid-cols-3">
        {[1, 2, 3, 4, 5, 6].map((i) => (
          <div key={i} className="glass p-6 space-y-3">
            <div className="flex items-center gap-3">
              <Skeleton className="h-9 w-9 shrink-0 rounded-xl" />
              <Skeleton className="h-4 w-2/5" />
            </div>
            <Skeleton className="h-3 w-4/5" />
            <Skeleton className="h-3 w-3/5" />
          </div>
        ))}
      </div>
    );
  }

  if (error) {
    return (
      <div className="glass flex min-h-[300px] items-center justify-center">
        <p className="text-sm text-red-600 dark:text-red-400">Profiller yüklenirken hata oluştu.</p>
      </div>
    );
  }

  return (
    <>
      {/* Top bar */}
      <div className="mb-8 flex items-center justify-between">
        <p className="text-sm text-muted-foreground">
          <span className="font-semibold text-foreground/80">{profileNames.length}</span> profil tanımlı
        </p>
        <Button size="sm" onClick={handleCreate}>
          <Plus className="mr-1.5 h-3.5 w-3.5" /> Yeni Profil
        </Button>
      </div>

      {/* Info cards */}
      <div className="mb-8 grid gap-5 lg:grid-cols-3">
        <div className="glass flex items-start gap-4 p-5 lg:col-span-2">
          <div className="mt-0.5 flex h-9 w-9 shrink-0 items-center justify-center rounded-xl bg-muted border border-border">
            <Info className="h-4 w-4 text-foreground/70" />
          </div>
          <div className="min-w-0">
            <p className="text-sm font-medium text-foreground/80">Profil nedir?</p>
            <p className="mt-1 text-xs leading-relaxed text-muted-foreground/80">
              Profiller, XML doğrulama sırasında belirli Schematron veya XSD hatalarını
              bastırmanızı sağlar. Örneğin, imzasız bir belgeyi test ederken
              GİB'in zorunlu kıldığı imza kontrollerini geçici olarak devre dışı bırakabilirsiniz.
            </p>
          </div>
        </div>

        <div className="glass flex items-start gap-4 p-5">
          <div className="mt-0.5 flex h-9 w-9 shrink-0 items-center justify-center rounded-xl bg-emerald-500/10 border border-emerald-500/20">
            <ListChecks className="h-4 w-4 text-emerald-600 dark:text-emerald-300" />
          </div>
          <div className="min-w-0">
            <p className="text-xs font-medium text-foreground/80">Özel profil</p>
            <p className="mt-1 text-[11px] leading-relaxed text-muted-foreground/80">
              <code className="rounded bg-muted px-1 py-0.5 font-mono text-[10px] text-muted-foreground">extends</code> ile
              miras alarak kendi kurallarınızı ekleyin.
            </p>
          </div>
        </div>
      </div>

      {/* Grid */}
      <div className="grid gap-5 sm:grid-cols-2 lg:grid-cols-3">
        {Object.entries(profiles ?? {}).map(([name, info], index) => (
          <ProfileCard key={name} name={name} info={info} onSelect={() => handleEdit(name, info)} index={index} />
        ))}

        {profileNames.length === 0 && (
          <div className="col-span-full flex min-h-[300px] flex-col items-center justify-center rounded-2xl border border-dashed border-border">
            <div className="mb-4 flex h-14 w-14 items-center justify-center rounded-2xl bg-muted border border-border">
              <Shield className="h-6 w-6 text-muted-foreground/60" />
            </div>
            <p className="text-sm font-medium text-muted-foreground">Henüz profil yok</p>
            <p className="mt-1 text-xs text-muted-foreground/60">İlk profilinizi oluşturarak başlayın</p>
            <Button size="sm" className="mt-5" onClick={handleCreate}>
              <Plus className="mr-1.5 h-3.5 w-3.5" /> Profil Oluştur
            </Button>
          </div>
        )}
      </div>

      <ProfileEditor
        open={editorOpen}
        onOpenChange={setEditorOpen}
        editingProfile={editingProfile}
        existingProfileNames={profileNames}
        onSave={handleSave}
        saving={saveProfile.isPending}
        onDelete={handleDelete}
        deleting={deleteProfile.isPending}
      />
    </>
  );
}

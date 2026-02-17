import { useState, useMemo, memo } from "react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import {
  Shield,
  ArrowRight,
  Loader2,
  Plus,
  Pencil,
  Trash2,
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

const ProfileCard = memo(function ProfileCard({
  name,
  info,
  onSelect,
}: {
  name: string;
  info: ProfileInfo;
  onSelect: () => void;
}) {
  return (
    <button
      onClick={onSelect}
      className="group text-left rounded-xl border bg-card p-6 transition-all duration-200 hover:border-primary/30 hover:shadow-lg hover:shadow-primary/5 hover:-translate-y-0.5"
    >
      <div className="flex items-center gap-3 mb-3">
        <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-lg bg-gradient-to-br from-primary/15 to-primary/5 text-primary transition-transform group-hover:scale-105">
          <Shield className="h-5 w-5" />
        </div>
        <span className="text-base font-bold truncate">{name}</span>
      </div>
      <p className="text-xs text-muted-foreground line-clamp-2 mb-4 min-h-[2rem] leading-relaxed">
        {info.description || "Açıklama yok"}
      </p>
      <div className="flex items-center gap-2 flex-wrap">
        <Badge variant="secondary" className="text-[10px] font-mono rounded-lg">
          {info.suppressionCount} kural
        </Badge>
        {info.xsdOverrides && Object.keys(info.xsdOverrides).length > 0 && (
          <Badge
            variant="outline"
            className="text-[10px] gap-1 font-mono rounded-lg"
          >
            <FileCode className="h-2.5 w-2.5" />
            {Object.values(info.xsdOverrides).flat().length} XSD
          </Badge>
        )}
        {info.schematronRules && Object.keys(info.schematronRules).length > 0 && (
          <Badge
            variant="outline"
            className="text-[10px] gap-1 font-mono rounded-lg"
          >
            <ScrollText className="h-2.5 w-2.5" />
            {Object.values(info.schematronRules).flat().length} ek kural
          </Badge>
        )}
        {info.extends && (
          <Badge
            variant="outline"
            className="text-[10px] gap-1 text-muted-foreground rounded-lg"
          >
            <ArrowRight className="h-2.5 w-2.5" />
            {info.extends}
          </Badge>
        )}
      </div>
    </button>
  );
});

export default function ProfilesPage() {
  const queryClient = useQueryClient();
  const { data, isLoading, error } = useProfiles();
  const saveProfile = useSaveProfile();
  const deleteProfile = useDeleteProfile();

  const [selectedProfile, setSelectedProfile] = useState<{
    name: string;
    info: ProfileInfo;
  } | null>(null);

  const [editorOpen, setEditorOpen] = useState(false);
  const [editingProfile, setEditingProfile] = useState<{
    name: string;
    info: ProfileInfo;
  } | null>(null);

  const profiles = data?.profiles;
  const profileNames = useMemo(() => (profiles ? Object.keys(profiles) : []), [profiles]);

  const handleCreate = () => {
    setEditingProfile(null);
    setEditorOpen(true);
  };

  const handleEdit = (name: string, info: ProfileInfo) => {
    setEditingProfile({ name, info });
    setSelectedProfile(null);
    setEditorOpen(true);
  };

  const handleSave = (formData: {
    name: string;
    description: string;
    extendsProfile: string;
    suppressions: {
      match: string;
      pattern: string;
      scope: string[];
      description: string;
    }[];
    xsdOverrides: Record<
      string,
      { element: string; minOccurs?: string; maxOccurs?: string }[]
    >;
    schematronRules: Record<
      string,
      { context: string; test: string; message: string; id?: string }[]
    >;
  }) => {
    saveProfile.mutate(
      {
        name: formData.name,
        description: formData.description,
        extendsProfile: formData.extendsProfile || undefined,
        suppressions: formData.suppressions.map((s) => ({
          match: s.match,
          pattern: s.pattern,
          scope: s.scope.length > 0 ? s.scope : undefined,
          description: s.description || undefined,
        })),
        xsdOverrides: formData.xsdOverrides,
        schematronRules: formData.schematronRules,
      },
      {
        onSuccess: () => {
          toast.success(
            editingProfile
              ? `"${formData.name}" profili güncellendi.`
              : `"${formData.name}" profili oluşturuldu.`
          );
          setEditorOpen(false);
          queryClient.invalidateQueries({ queryKey: ["profiles"] });
        },
        onError: () => {
          toast.error("Profil kaydedilirken hata oluştu.");
        },
      }
    );
  };

  const handleDelete = (name: string) => {
    if (!window.confirm(`"${name}" profilini silmek istediğinizden emin misiniz?`)) {
      return;
    }
    deleteProfile.mutate(name, {
      onSuccess: () => {
        toast.success(`"${name}" profili silindi.`);
        setSelectedProfile(null);
        queryClient.invalidateQueries({ queryKey: ["profiles"] });
      },
      onError: () => {
        toast.error("Profil silinirken hata oluştu.");
      },
    });
  };

  if (isLoading) {
    return (
      <div className="space-y-6">
        <div className="flex items-center justify-between">
          <Skeleton className="h-5 w-32" />
          <Skeleton className="h-9 w-28 rounded-lg" />
        </div>
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {[1, 2, 3, 4, 5, 6].map((i) => (
            <div
              key={i}
              className="rounded-xl border bg-card p-6 space-y-3"
            >
              <div className="flex items-center gap-3">
                <Skeleton className="h-10 w-10 rounded-lg shrink-0" />
                <Skeleton className="h-5 w-2/5" />
              </div>
              <div className="space-y-2">
                <Skeleton className="h-4 w-4/5" />
                <Skeleton className="h-4 w-3/5" />
              </div>
              <div className="flex gap-2">
                <Skeleton className="h-5 w-16 rounded-lg" />
                <Skeleton className="h-5 w-12 rounded-lg" />
              </div>
            </div>
          ))}
        </div>
      </div>
    );
  }

  if (error) {
    return (
      <div className="flex items-center justify-center min-h-[300px] rounded-xl border">
        <p className="text-sm text-destructive">
          Profiller yüklenirken hata oluştu. API servisinin çalıştığından emin
          olun.
        </p>
      </div>
    );
  }

  return (
    <>
      {/* ── Action Bar ── */}
      <div className="flex items-center justify-between mb-6">
        <div className="text-sm text-muted-foreground">
          <span className="font-bold text-foreground">{profileNames.length}</span>{" "}
          profil tanımlı
        </div>
        <Button
          size="sm"
          onClick={handleCreate}
          className="h-9 rounded-lg shadow-sm"
        >
          <Plus className="mr-1.5 h-4 w-4" />
          Yeni Profil
        </Button>
      </div>

      {/* ── Info Guide ── */}
      <div className="mb-6 space-y-3">
        <div className="flex items-start gap-3 rounded-xl border border-primary/15 bg-primary/3 p-4">
          <div className="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg bg-primary/10 text-primary mt-0.5">
            <Info className="h-4 w-4" />
          </div>
          <div className="space-y-1 min-w-0">
            <p className="text-sm font-semibold text-foreground">Profil nedir?</p>
            <p className="text-xs leading-relaxed text-muted-foreground">
              Profiller, XML doğrulama sırasında belirli Schematron veya XSD hatalarını
              bastırmanızı (suppress) sağlar. Örneğin, imzasız bir belgeyi test ederken
              GİB'in zorunlu kıldığı imza kontrollerini geçici olarak devre dışı bırakabilirsiniz.
            </p>
          </div>
        </div>

        <div className="grid gap-3 sm:grid-cols-1">
          <div className="flex items-start gap-3 rounded-xl border bg-card/50 p-4">
            <div className="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg bg-emerald-500/10 text-emerald-500 mt-0.5">
              <ListChecks className="h-4 w-4" />
            </div>
            <div className="space-y-1 min-w-0">
              <p className="text-xs font-semibold text-foreground">Özel profil oluşturun</p>
              <p className="text-[11px] leading-relaxed text-muted-foreground">
                Mevcut bir profili miras alarak (<code className="text-[10px] px-1 py-0.5 rounded bg-muted font-mono">extends</code>)
                kendi kurumunuza özel kurallar ekleyebilirsiniz. Doğrulamada{" "}
                <code className="text-[10px] px-1 py-0.5 rounded bg-muted font-mono">profile=isim</code> parametresi
                ile aktif edin.
              </p>
            </div>
          </div>
        </div>
      </div>

      {/* ── Profile Grid ── */}
      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
        {Object.entries(profiles ?? {}).map(([name, info]) => (
          <ProfileCard
            key={name}
            name={name}
            info={info}
            onSelect={() => setSelectedProfile({ name, info })}
          />
        ))}

        {profileNames.length === 0 && (
          <div className="col-span-full flex flex-col items-center justify-center min-h-[300px] rounded-xl border border-dashed">
            <div className="relative mb-5">
              <div className="absolute inset-0 rounded-full bg-primary/10 blur-2xl scale-150" />
              <div className="relative flex h-16 w-16 items-center justify-center rounded-xl bg-gradient-to-br from-primary/15 to-primary/5 border border-primary/10">
                <Shield className="h-7 w-7 text-primary/60" />
              </div>
            </div>
            <p className="text-base font-bold text-foreground/80">
              Henüz profil yok
            </p>
            <p className="text-sm text-muted-foreground mt-1">
              İlk profilinizi oluşturarak başlayın
            </p>
            <Button
              size="sm"
              className="mt-4 rounded-lg shadow-sm"
              onClick={handleCreate}
            >
              <Plus className="mr-1.5 h-4 w-4" />
              Profil Oluştur
            </Button>
          </div>
        )}
      </div>

      {/* ── Detail Dialog ── */}
      <Dialog
        open={selectedProfile !== null}
        onOpenChange={(open) => !open && setSelectedProfile(null)}
      >
        {selectedProfile && (
          <DialogContent className="max-w-2xl max-h-[85vh] overflow-y-auto">
            <DialogHeader>
              <DialogTitle className="flex items-center gap-3">
                <div className="flex h-9 w-9 items-center justify-center rounded-lg bg-gradient-to-br from-primary/15 to-primary/5 text-primary">
                  <Shield className="h-4 w-4" />
                </div>
                {selectedProfile.name}
              </DialogTitle>
              <DialogDescription>
                {selectedProfile.info.description}
              </DialogDescription>
            </DialogHeader>

            {selectedProfile.info.extends && (
              <p className="text-sm text-muted-foreground">
                Miras:{" "}
                <Badge variant="outline" className="ml-1 font-mono text-xs rounded-lg">
                  {selectedProfile.info.extends}
                </Badge>
              </p>
            )}

            {/* Suppression rules */}
            <div>
              <h4 className="text-[11px] font-semibold uppercase tracking-wider text-muted-foreground mb-3">
                Bastırma Kuralları
              </h4>
              <div className="space-y-2">
                {selectedProfile.info.suppressions.length === 0 ? (
                  <div className="text-center text-muted-foreground py-8 text-xs rounded-xl border border-dashed">
                    Bastırma kuralı yok
                  </div>
                ) : (
                  selectedProfile.info.suppressions.map((rule, i) => (
                    <div
                      key={`rule-${rule.match}-${rule.pattern || i}`}
                      className="rounded-lg border bg-muted/20 px-4 py-3 space-y-1.5"
                    >
                      <div className="flex items-center gap-2 flex-wrap">
                        <Badge
                          variant="secondary"
                          className="text-[10px] font-mono rounded-md"
                        >
                          {rule.match}
                        </Badge>
                        <code className="text-[11px] font-mono text-foreground/80">
                          {rule.pattern}
                        </code>
                      </div>
                      <div className="flex items-center gap-2 text-[10px] text-muted-foreground">
                        {rule.scope && rule.scope.length > 0 ? (
                          <div className="flex gap-1">
                            {rule.scope.map((s) => (
                              <Badge
                                key={s}
                                variant="outline"
                                className="text-[9px] px-1.5 py-0 font-mono rounded-md"
                              >
                                {s}
                              </Badge>
                            ))}
                          </div>
                        ) : (
                          <span className="italic">Tüm kapsamlar</span>
                        )}
                        {rule.description && (
                          <>
                            <span>&middot;</span>
                            <span>{rule.description}</span>
                          </>
                        )}
                      </div>
                    </div>
                  ))
                )}
              </div>
            </div>

            {/* XSD Overrides */}
            {selectedProfile.info.xsdOverrides &&
              Object.keys(selectedProfile.info.xsdOverrides).length > 0 && (
                <div>
                  <h4 className="text-[11px] font-semibold uppercase tracking-wider text-muted-foreground mb-3 flex items-center gap-1.5">
                    <FileCode className="h-3.5 w-3.5" />
                    XSD Override
                  </h4>
                  <div className="space-y-2">
                    {Object.entries(selectedProfile.info.xsdOverrides).flatMap(
                      ([schemaType, overrides]) =>
                        overrides.map((ovr, i) => (
                          <div
                            key={`ovr-${schemaType}-${ovr.element || i}`}
                            className="rounded-lg border bg-muted/20 px-4 py-3 flex items-center gap-4 text-xs"
                          >
                            <Badge
                              variant="secondary"
                              className="text-[10px] font-mono rounded-md shrink-0"
                            >
                              {schemaType}
                            </Badge>
                            <code className="font-mono text-[11px] flex-1 truncate">
                              {ovr.element}
                            </code>
                            <div className="flex gap-3 shrink-0 text-muted-foreground">
                              <span>
                                min:{" "}
                                <span className="font-mono text-foreground">
                                  {ovr.minOccurs ?? "—"}
                                </span>
                              </span>
                              <span>
                                max:{" "}
                                <span className="font-mono text-foreground">
                                  {ovr.maxOccurs ?? "—"}
                                </span>
                              </span>
                            </div>
                          </div>
                        ))
                    )}
                  </div>
                </div>
              )}

            {/* Schematron Rules */}
            {selectedProfile.info.schematronRules &&
              Object.keys(selectedProfile.info.schematronRules).length > 0 && (
                <div>
                  <h4 className="text-[11px] font-semibold uppercase tracking-wider text-muted-foreground mb-3 flex items-center gap-1.5">
                    <ScrollText className="h-3.5 w-3.5" />
                    Özel Schematron Kuralları
                  </h4>
                  <div className="space-y-2">
                    {Object.entries(selectedProfile.info.schematronRules).flatMap(
                      ([schematronType, rules]) =>
                        rules.map((rule, i) => (
                          <div
                            key={`sch-${schematronType}-${rule.id || i}`}
                            className="rounded-lg border bg-muted/20 px-4 py-3 space-y-2"
                          >
                            <div className="flex items-center gap-2 flex-wrap">
                              <Badge
                                variant="secondary"
                                className="text-[10px] font-mono rounded-md shrink-0"
                              >
                                {schematronType}
                              </Badge>
                              {rule.id && (
                                <Badge
                                  variant="outline"
                                  className="text-[10px] font-mono rounded-md"
                                >
                                  {rule.id}
                                </Badge>
                              )}
                            </div>
                            <div className="text-[11px] space-y-1">
                              <div className="flex gap-2">
                                <span className="text-muted-foreground shrink-0">context:</span>
                                <code className="font-mono text-foreground/80 break-all">
                                  {rule.context}
                                </code>
                              </div>
                              <div className="flex gap-2">
                                <span className="text-muted-foreground shrink-0">test:</span>
                                <code className="font-mono text-foreground/80 break-all">
                                  {rule.test}
                                </code>
                              </div>
                              <div className="flex gap-2">
                                <span className="text-muted-foreground shrink-0">mesaj:</span>
                                <span className="text-foreground/80">
                                  {rule.message}
                                </span>
                              </div>
                            </div>
                          </div>
                        ))
                    )}
                  </div>
                </div>
              )}

            {/* Actions */}
            <div className="flex items-center justify-end gap-2 pt-2">
              <Button
                variant="outline"
                size="sm"
                className="h-9 rounded-lg"
                onClick={() =>
                  handleEdit(selectedProfile.name, selectedProfile.info)
                }
              >
                <Pencil className="mr-1.5 h-3.5 w-3.5" />
                Düzenle
              </Button>
              <Button
                variant="destructive"
                size="sm"
                className="h-9 rounded-lg"
                onClick={() => handleDelete(selectedProfile.name)}
                disabled={deleteProfile.isPending}
              >
                {deleteProfile.isPending ? (
                  <Loader2 className="mr-1.5 h-3.5 w-3.5 animate-spin" />
                ) : (
                  <Trash2 className="mr-1.5 h-3.5 w-3.5" />
                )}
                Sil
              </Button>
            </div>
          </DialogContent>
        )}
      </Dialog>

      {/* ── Editor Sheet ── */}
      <ProfileEditor
        open={editorOpen}
        onOpenChange={setEditorOpen}
        editingProfile={editingProfile}
        existingProfileNames={profileNames}
        onSave={handleSave}
        saving={saveProfile.isPending}
      />
    </>
  );
}

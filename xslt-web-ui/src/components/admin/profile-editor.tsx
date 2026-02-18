import { useEffect, useState, useCallback } from "react";
import {
  Plus,
  Save,
  Loader2,
  AlertCircle,
  ShieldAlert,
  FileCode,
  ScrollText,
  X,
  ArrowRight,
  Layers,
  Trash2,
} from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Badge } from "@/components/ui/badge";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import type { ProfileInfo } from "@/api/types";
import { SuppressionCard, emptyRule, type RuleFormData } from "./suppression-card";
import { XsdOverrideCard, emptyXsdOverride, type XsdOverrideFormData } from "./xsd-override-card";
import { SchematronRuleCard, emptySchematronRule, type SchematronRuleFormData } from "./schematron-rule-card";
import { motion, AnimatePresence } from "framer-motion";

function EmptyState({
  icon: Icon,
  title,
  description,
  actionLabel,
  onAction,
}: {
  icon: React.ComponentType<{ className?: string }>;
  title: string;
  description: string;
  actionLabel: string;
  onAction: () => void;
}) {
  return (
    <div className="flex flex-col items-center gap-4 rounded-2xl border border-dashed border-border py-16 text-center">
      <div className="flex h-14 w-14 items-center justify-center rounded-2xl bg-muted">
        <Icon className="h-6 w-6 text-muted-foreground/60" />
      </div>
      <div>
        <p className="text-sm font-medium text-muted-foreground">{title}</p>
        <p className="mt-1 max-w-[320px] text-xs text-muted-foreground/70">
          {description}
        </p>
      </div>
      <Button variant="outline" size="sm" onClick={onAction}>
        <Plus className="mr-1.5 h-3.5 w-3.5" />
        {actionLabel}
      </Button>
    </div>
  );
}

function AddButton({ label, onClick }: { label: string; onClick: () => void }) {
  return (
    <button
      type="button"
      onClick={onClick}
      className="flex items-center justify-center gap-1.5 w-full h-10 rounded-xl border border-dashed border-border text-xs text-muted-foreground/70 hover:text-muted-foreground hover:border-border hover:bg-muted transition-all duration-200"
    >
      <Plus className="h-3.5 w-3.5" />
      {label}
    </button>
  );
}

interface ProfileEditorProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  editingProfile: { name: string; info: ProfileInfo } | null;
  existingProfileNames: string[];
  onSave: (data: {
    name: string;
    description: string;
    extendsProfile: string;
    suppressions: RuleFormData[];
    xsdOverrides: Record<
      string,
      { element: string; minOccurs?: string; maxOccurs?: string }[]
    >;
    schematronRules: Record<
      string,
      { context: string; test: string; message: string; id?: string }[]
    >;
  }) => void;
  saving: boolean;
  onDelete?: (name: string) => void;
  deleting?: boolean;
}

export function ProfileEditor({
  open,
  onOpenChange,
  editingProfile,
  existingProfileNames,
  onSave,
  saving,
  onDelete,
  deleting,
}: ProfileEditorProps) {
  const [name, setName] = useState("");
  const [description, setDescription] = useState("");
  const [extendsProfile, setExtendsProfile] = useState("");
  const [rules, setRules] = useState<RuleFormData[]>([]);
  const [xsdOverrides, setXsdOverrides] = useState<XsdOverrideFormData[]>([]);
  const [schematronRules, setSchematronRules] = useState<SchematronRuleFormData[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [activeTab, setActiveTab] = useState("suppressions");

  const isEditing = editingProfile !== null;

  useEffect(() => {
    if (!open) return;
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === "Escape") onOpenChange(false);
    };
    document.addEventListener("keydown", handleKeyDown);
    return () => document.removeEventListener("keydown", handleKeyDown);
  }, [open, onOpenChange]);

  useEffect(() => {
    if (open) {
      if (editingProfile) {
        setName(editingProfile.name);
        setDescription(editingProfile.info.description ?? "");
        setExtendsProfile(editingProfile.info.extends ?? "");
        setRules(
          editingProfile.info.suppressions.map((s, i) => ({
            id: `existing-${i}-${Date.now()}`,
            match: s.match,
            pattern: s.pattern,
            scope: s.scope ?? [],
            description: s.description ?? "",
          }))
        );
        const ovrs: XsdOverrideFormData[] = [];
        if (editingProfile.info.xsdOverrides) {
          for (const [schemaType, list] of Object.entries(editingProfile.info.xsdOverrides)) {
            for (const o of list) {
              ovrs.push({
                schemaType,
                element: o.element,
                minOccurs: o.minOccurs ?? "",
                maxOccurs: o.maxOccurs ?? "",
              });
            }
          }
        }
        setXsdOverrides(ovrs);
        const schRules: SchematronRuleFormData[] = [];
        if (editingProfile.info.schematronRules) {
          for (const [schematronType, list] of Object.entries(editingProfile.info.schematronRules)) {
            for (const r of list) {
              schRules.push({
                schematronType,
                context: r.context,
                test: r.test,
                message: r.message,
                id: r.id ?? "",
              });
            }
          }
        }
        setSchematronRules(schRules);
      } else {
        setName("");
        setDescription("");
        setExtendsProfile("");
        setRules([]);
        setXsdOverrides([]);
        setSchematronRules([]);
      }
      setError(null);
      setActiveTab("suppressions");
    }
  }, [open, editingProfile]);

  const handleSave = useCallback(() => {
    if (!name.trim()) {
      setError("Profil adı gereklidir.");
      return;
    }
    if (!isEditing && existingProfileNames.includes(name.trim())) {
      setError("Bu isimde bir profil zaten mevcut.");
      return;
    }
    for (let i = 0; i < rules.length; i++) {
      if (!rules[i].pattern.trim()) {
        setError(`Bastırma kuralı ${i + 1}: Pattern boş olamaz.`);
        setActiveTab("suppressions");
        return;
      }
    }
    for (let i = 0; i < xsdOverrides.length; i++) {
      if (!xsdOverrides[i].element.trim()) {
        setError(`XSD override ${i + 1}: Element boş olamaz.`);
        setActiveTab("xsd-overrides");
        return;
      }
      if (!xsdOverrides[i].minOccurs.trim() && !xsdOverrides[i].maxOccurs.trim()) {
        setError(`XSD override ${i + 1}: minOccurs veya maxOccurs belirtilmeli.`);
        setActiveTab("xsd-overrides");
        return;
      }
    }
    for (let i = 0; i < schematronRules.length; i++) {
      if (!schematronRules[i].context.trim()) {
        setError(`Schematron kuralı ${i + 1}: Context boş olamaz.`);
        setActiveTab("schematron-rules");
        return;
      }
      if (!schematronRules[i].test.trim()) {
        setError(`Schematron kuralı ${i + 1}: Test ifadesi boş olamaz.`);
        setActiveTab("schematron-rules");
        return;
      }
      if (!schematronRules[i].message.trim()) {
        setError(`Schematron kuralı ${i + 1}: Hata mesajı boş olamaz.`);
        setActiveTab("schematron-rules");
        return;
      }
    }
    setError(null);

    const grouped: Record<string, { element: string; minOccurs?: string; maxOccurs?: string }[]> = {};
    for (const ovr of xsdOverrides) {
      if (!ovr.element.trim()) continue;
      if (!grouped[ovr.schemaType]) grouped[ovr.schemaType] = [];
      grouped[ovr.schemaType].push({
        element: ovr.element.trim(),
        minOccurs: ovr.minOccurs.trim() || undefined,
        maxOccurs: ovr.maxOccurs.trim() || undefined,
      });
    }

    const groupedSchematron: Record<string, { context: string; test: string; message: string; id?: string }[]> = {};
    for (const sr of schematronRules) {
      if (!sr.context.trim() || !sr.test.trim() || !sr.message.trim()) continue;
      if (!groupedSchematron[sr.schematronType]) groupedSchematron[sr.schematronType] = [];
      groupedSchematron[sr.schematronType].push({
        context: sr.context.trim(),
        test: sr.test.trim(),
        message: sr.message.trim(),
        id: sr.id.trim() || undefined,
      });
    }

    onSave({
      name: name.trim(),
      description: description.trim(),
      extendsProfile: extendsProfile.trim(),
      suppressions: rules,
      xsdOverrides: grouped,
      schematronRules: groupedSchematron,
    });
  }, [name, isEditing, existingProfileNames, rules, xsdOverrides, schematronRules, description, extendsProfile, onSave]);

  const totalRules = rules.length + xsdOverrides.length + schematronRules.length;

  if (!open) return null;

  return (
    <AnimatePresence>
      {open && (
        <motion.div
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          exit={{ opacity: 0 }}
          transition={{ duration: 0.2 }}
          className="fixed inset-0 z-[200] flex items-center justify-center overflow-y-auto bg-[var(--overlay-bg)] backdrop-blur-sm px-4 pt-20 pb-8"
          onClick={(e) => {
            if (e.target === e.currentTarget) onOpenChange(false);
          }}
        >
          <motion.div
            initial={{ opacity: 0, y: 24, scale: 0.98 }}
            animate={{ opacity: 1, y: 0, scale: 1 }}
            exit={{ opacity: 0, y: 24, scale: 0.98 }}
            transition={{ duration: 0.3, ease: [0.16, 1, 0.3, 1] as const }}
            className="relative w-full max-w-3xl max-h-[calc(100vh-6rem)] flex flex-col rounded-2xl border border-border bg-[var(--overlay-panel)] backdrop-blur-2xl shadow-[0_0_80px_-20px_rgba(0,0,0,0.6)]"
          >
            {/* Header */}
            <div className="flex shrink-0 items-center justify-between border-b border-border px-8 py-6">
              <div className="flex items-center gap-4">
                <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-muted border border-border">
                  <Layers className="h-5 w-5 text-foreground/70" />
                </div>
                <div>
                  <h2 className="text-lg font-semibold text-foreground">
                    {isEditing ? editingProfile.name : "Yeni Profil"}
                  </h2>
                  <p className="text-xs text-muted-foreground/80">
                    {isEditing ? "Profil yapılandırmasını düzenleyin" : "Yeni bir doğrulama profili oluşturun"}
                  </p>
                </div>
              </div>
              <Button
                variant="ghost"
                size="sm"
                className="h-8 w-8 p-0 text-muted-foreground/70 hover:text-foreground/70"
                onClick={() => onOpenChange(false)}
                aria-label="Kapat"
              >
                <X className="h-4 w-4" />
              </Button>
            </div>

            <div className="flex-1 min-h-0 overflow-y-auto">
              <div className="px-8 py-6 space-y-8">

                {/* Error */}
                <AnimatePresence>
                  {error && (
                    <motion.div
                      initial={{ opacity: 0, height: 0 }}
                      animate={{ opacity: 1, height: "auto" }}
                      exit={{ opacity: 0, height: 0 }}
                      className="overflow-hidden"
                    >
                      <div className="flex items-start gap-3 rounded-xl border border-red-500/20 bg-red-500/5 px-4 py-3 text-sm text-red-600 dark:text-red-400">
                        <AlertCircle className="h-4 w-4 shrink-0 mt-0.5" />
                        <span>{error}</span>
                      </div>
                    </motion.div>
                  )}
                </AnimatePresence>

                {/* Profile Info */}
                <section className="space-y-5">
                  <div>
                    <h3 className="text-sm font-semibold text-foreground/80">Profil Bilgileri</h3>
                    <p className="mt-1 text-xs text-muted-foreground/70">Profilin temel ayarları.</p>
                  </div>

                  <div className="grid gap-5 sm:grid-cols-2">
                    <div className="space-y-2">
                      <Label className="text-xs font-medium text-muted-foreground">Profil Adı</Label>
                      <Input
                        value={name}
                        onChange={(e) => setName(e.target.value)}
                        placeholder="my-company"
                        disabled={isEditing}
                      />
                    </div>
                    <div className="space-y-2">
                      <Label className="text-xs font-medium text-muted-foreground">
                        <span className="flex items-center gap-1.5">
                          <ArrowRight className="h-3 w-3" />
                          Miras (extends)
                        </span>
                      </Label>
                      <Input
                        value={extendsProfile}
                        onChange={(e) => setExtendsProfile(e.target.value)}
                        placeholder="unsigned"
                      />
                      <p className="text-[10px] text-muted-foreground/50">Üst profilin tüm kurallarını miras alır.</p>
                    </div>
                  </div>

                  <div className="space-y-2">
                    <Label className="text-xs font-medium text-muted-foreground">Açıklama</Label>
                    <Input
                      value={description}
                      onChange={(e) => setDescription(e.target.value)}
                      placeholder="Bu profilin amacı ve kullanım alanı..."
                    />
                  </div>
                </section>

                {/* Divider */}
                <div className="border-t border-border" />

                {/* Tabs */}
                <Tabs value={activeTab} onValueChange={setActiveTab}>
                  <TabsList className="w-full">
                    <TabsTrigger value="suppressions" className="flex-1 gap-1.5 text-xs">
                      <ShieldAlert className="h-3.5 w-3.5" />
                      Bastırma Kuralları
                      {rules.length > 0 && (
                        <Badge variant="secondary" className="h-4 min-w-5 px-1 text-[10px] ml-1">
                          {rules.length}
                        </Badge>
                      )}
                    </TabsTrigger>
                    <TabsTrigger value="xsd-overrides" className="flex-1 gap-1.5 text-xs">
                      <FileCode className="h-3.5 w-3.5" />
                      XSD Override
                      {xsdOverrides.length > 0 && (
                        <Badge variant="secondary" className="h-4 min-w-5 px-1 text-[10px] ml-1">
                          {xsdOverrides.length}
                        </Badge>
                      )}
                    </TabsTrigger>
                    <TabsTrigger value="schematron-rules" className="flex-1 gap-1.5 text-xs">
                      <ScrollText className="h-3.5 w-3.5" />
                      Ek Kurallar
                      {schematronRules.length > 0 && (
                        <Badge variant="secondary" className="h-4 min-w-5 px-1 text-[10px] ml-1">
                          {schematronRules.length}
                        </Badge>
                      )}
                    </TabsTrigger>
                  </TabsList>

                  <TabsContent value="suppressions" className="mt-5">
                    <div className="space-y-3">
                      {rules.length === 0 ? (
                        <EmptyState
                          icon={ShieldAlert}
                          title="Bastırma kuralı yok"
                          description="Schematron veya XSD hatalarını profil bazlı bastırmak için kural ekleyin."
                          actionLabel="Kural ekle"
                          onAction={() => setRules([...rules, emptyRule()])}
                        />
                      ) : (
                        <>
                          {rules.map((rule, idx) => (
                            <SuppressionCard
                              key={rule.id}
                              rule={rule}
                              onUpdate={(field, value) =>
                                setRules(rules.map((r, i) => (i === idx ? { ...r, [field]: value } : r)))
                              }
                              onRemove={() => setRules(rules.filter((_, i) => i !== idx))}
                            />
                          ))}
                          <AddButton label="Kural ekle" onClick={() => setRules([...rules, emptyRule()])} />
                        </>
                      )}
                    </div>
                  </TabsContent>

                  <TabsContent value="xsd-overrides" className="mt-5">
                    <div className="space-y-3">
                      {xsdOverrides.length === 0 ? (
                        <EmptyState
                          icon={FileCode}
                          title="XSD override yok"
                          description="GİB orijinal XSD dosyalarındaki minOccurs/maxOccurs değerlerini profil bazlı değiştirin."
                          actionLabel="Override ekle"
                          onAction={() => setXsdOverrides([...xsdOverrides, emptyXsdOverride()])}
                        />
                      ) : (
                        <>
                          {xsdOverrides.map((ovr, idx) => (
                            <XsdOverrideCard
                              key={`ovr-${ovr.schemaType}-${ovr.element || idx}`}
                              ovr={ovr}
                              onUpdate={(field, value) =>
                                setXsdOverrides(xsdOverrides.map((o, i) => (i === idx ? { ...o, [field]: value } : o)))
                              }
                              onRemove={() => setXsdOverrides(xsdOverrides.filter((_, i) => i !== idx))}
                            />
                          ))}
                          <AddButton label="Override ekle" onClick={() => setXsdOverrides([...xsdOverrides, emptyXsdOverride()])} />
                        </>
                      )}
                    </div>
                  </TabsContent>

                  <TabsContent value="schematron-rules" className="mt-5">
                    <div className="space-y-3">
                      {schematronRules.length === 0 ? (
                        <EmptyState
                          icon={ScrollText}
                          title="Özel Schematron kuralı yok"
                          description="Derleme öncesi Schematron dosyasına ek iş kuralları (assert) ekleyin. Kurallar ISO Schematron pipeline'ından geçerek derlenir."
                          actionLabel="Kural ekle"
                          onAction={() => setSchematronRules([...schematronRules, emptySchematronRule()])}
                        />
                      ) : (
                        <>
                          {schematronRules.map((rule, idx) => (
                            <SchematronRuleCard
                              key={`sch-${rule.schematronType}-${rule.id || idx}`}
                              rule={rule}
                              onUpdate={(field, value) =>
                                setSchematronRules(schematronRules.map((r, i) => (i === idx ? { ...r, [field]: value } : r)))
                              }
                              onRemove={() => setSchematronRules(schematronRules.filter((_, i) => i !== idx))}
                            />
                          ))}
                          <AddButton label="Kural ekle" onClick={() => setSchematronRules([...schematronRules, emptySchematronRule()])} />
                        </>
                      )}
                    </div>
                  </TabsContent>
                </Tabs>
              </div>
            </div>

            {/* Footer */}
            <div className="flex shrink-0 items-center justify-between border-t border-border px-8 py-5">
              <div className="flex items-center gap-3">
                {isEditing && onDelete && (
                  <Button
                    variant="ghost"
                    size="sm"
                    className="text-red-600 dark:text-red-400 hover:text-red-500 dark:hover:text-red-300 hover:bg-red-500/10"
                    onClick={() => onDelete(editingProfile.name)}
                    disabled={deleting || saving}
                  >
                    {deleting ? (
                      <Loader2 className="mr-1.5 h-3.5 w-3.5 animate-spin" />
                    ) : (
                      <Trash2 className="mr-1.5 h-3.5 w-3.5" />
                    )}
                    Sil
                  </Button>
                )}
                {!isEditing && (
                  <p className="text-xs text-muted-foreground/60">
                    {totalRules === 0 ? "Henüz kural eklenmedi" : `${totalRules} kural tanımlı`}
                  </p>
                )}
              </div>
              <div className="flex items-center gap-3">
                <Button
                  variant="ghost"
                  onClick={() => onOpenChange(false)}
                  disabled={saving}
                  className="text-muted-foreground"
                >
                  İptal
                </Button>
                <Button onClick={handleSave} disabled={saving} className="glow-primary-hover min-w-[120px]">
                  {saving ? (
                    <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                  ) : (
                    <Save className="mr-2 h-4 w-4" />
                  )}
                  {isEditing ? "Kaydet" : "Oluştur"}
                </Button>
              </div>
            </div>
          </motion.div>
        </motion.div>
      )}
    </AnimatePresence>
  );
}

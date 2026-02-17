import { useEffect, useState } from "react";
import { Plus, Save, Loader2, AlertCircle, ShieldAlert, FileCode, ScrollText, X } from "lucide-react";
import { Button } from "@/components/ui/button";
import {
  Sheet,
  SheetContent,
  SheetDescription,
  SheetHeader,
  SheetTitle,
} from "@/components/ui/sheet";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Badge } from "@/components/ui/badge";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { ScrollArea } from "@/components/ui/scroll-area";
import { Separator } from "@/components/ui/separator";
import type { ProfileInfo } from "@/api/types";
import { SuppressionCard, emptyRule, type RuleFormData } from "./suppression-card";
import { XsdOverrideCard, emptyXsdOverride, type XsdOverrideFormData } from "./xsd-override-card";
import { SchematronRuleCard, emptySchematronRule, type SchematronRuleFormData } from "./schematron-rule-card";

// ── Empty State ────────────────────────────────────────────────

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
    <div className="flex flex-col items-center gap-3 rounded-lg border border-dashed py-12 text-center">
      <div className="flex h-12 w-12 items-center justify-center rounded-full bg-muted">
        <Icon className="h-5 w-5 text-muted-foreground" />
      </div>
      <div>
        <p className="text-sm font-medium">{title}</p>
        <p className="text-xs text-muted-foreground mt-0.5 max-w-[280px]">
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

// ── Main Profile Editor (Sheet) ────────────────────────────────

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
}

export function ProfileEditor({
  open,
  onOpenChange,
  editingProfile,
  existingProfileNames,
  onSave,
  saving,
}: ProfileEditorProps) {
  const [name, setName] = useState("");
  const [description, setDescription] = useState("");
  const [extendsProfile, setExtendsProfile] = useState("");
  const [rules, setRules] = useState<RuleFormData[]>([]);
  const [xsdOverrides, setXsdOverrides] = useState<XsdOverrideFormData[]>([]);
  const [schematronRules, setSchematronRules] = useState<SchematronRuleFormData[]>([]);
  const [error, setError] = useState<string | null>(null);

  const isEditing = editingProfile !== null;

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
          for (const [schemaType, list] of Object.entries(
            editingProfile.info.xsdOverrides
          )) {
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
          for (const [schematronType, list] of Object.entries(
            editingProfile.info.schematronRules
          )) {
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
    }
  }, [open, editingProfile]);

  const handleSave = () => {
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
        return;
      }
    }
    for (let i = 0; i < xsdOverrides.length; i++) {
      if (!xsdOverrides[i].element.trim()) {
        setError(`XSD override ${i + 1}: Element boş olamaz.`);
        return;
      }
      if (
        !xsdOverrides[i].minOccurs.trim() &&
        !xsdOverrides[i].maxOccurs.trim()
      ) {
        setError(
          `XSD override ${i + 1}: minOccurs veya maxOccurs belirtilmeli.`
        );
        return;
      }
    }
    for (let i = 0; i < schematronRules.length; i++) {
      if (!schematronRules[i].context.trim()) {
        setError(`Schematron kuralı ${i + 1}: Context boş olamaz.`);
        return;
      }
      if (!schematronRules[i].test.trim()) {
        setError(`Schematron kuralı ${i + 1}: Test ifadesi boş olamaz.`);
        return;
      }
      if (!schematronRules[i].message.trim()) {
        setError(`Schematron kuralı ${i + 1}: Hata mesajı boş olamaz.`);
        return;
      }
    }
    setError(null);

    const grouped: Record<
      string,
      { element: string; minOccurs?: string; maxOccurs?: string }[]
    > = {};
    for (const ovr of xsdOverrides) {
      if (!ovr.element.trim()) continue;
      if (!grouped[ovr.schemaType]) grouped[ovr.schemaType] = [];
      grouped[ovr.schemaType].push({
        element: ovr.element.trim(),
        minOccurs: ovr.minOccurs.trim() || undefined,
        maxOccurs: ovr.maxOccurs.trim() || undefined,
      });
    }

    const groupedSchematron: Record<
      string,
      { context: string; test: string; message: string; id?: string }[]
    > = {};
    for (const sr of schematronRules) {
      if (!sr.context.trim() || !sr.test.trim() || !sr.message.trim()) continue;
      if (!groupedSchematron[sr.schematronType])
        groupedSchematron[sr.schematronType] = [];
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
  };

  return (
    <Sheet open={open} onOpenChange={onOpenChange}>
      <SheetContent
        side="right"
        showCloseButton={false}
        className="sm:max-w-2xl w-full p-0 flex flex-col"
      >
        {/* ── Fixed Header ── */}
        <SheetHeader className="px-6 pt-6 pb-4 border-b shrink-0">
          <div className="flex items-center justify-between">
            <div>
              <SheetTitle className="text-lg">
                {isEditing
                  ? `${editingProfile.name}`
                  : "Yeni Profil"}
              </SheetTitle>
              <SheetDescription className="mt-0.5">
                {isEditing
                  ? "Profil yapılandırmasını düzenleyin."
                  : "Yeni bir doğrulama profili oluşturun."}
              </SheetDescription>
            </div>
        <Button
          variant="ghost"
          size="icon"
          className="h-8 w-8 shrink-0"
          onClick={() => onOpenChange(false)}
          aria-label="Kapat"
        >
          <X className="h-4 w-4" />
        </Button>
          </div>
        </SheetHeader>

        {/* ── Scrollable Body ── */}
        <ScrollArea className="flex-1">
          <div className="px-6 py-5 space-y-6">
            {/* Error */}
            {error && (
              <div className="flex items-start gap-2.5 rounded-lg border border-destructive/40 bg-destructive/5 px-4 py-3 text-sm text-destructive">
                <AlertCircle className="h-4 w-4 shrink-0 mt-0.5" />
                <span>{error}</span>
              </div>
            )}

            {/* ── Section: Profile Info ── */}
            <section className="space-y-4">
              <div>
                <h3 className="text-sm font-semibold">Profil Bilgileri</h3>
                <p className="text-xs text-muted-foreground mt-0.5">
                  Profilin temel ayarları.
                </p>
              </div>

              <div className="grid gap-4 sm:grid-cols-2">
                <div className="space-y-1.5">
                  <Label className="text-xs font-medium">Profil Adı</Label>
                  <Input
                    value={name}
                    onChange={(e) => setName(e.target.value)}
                    placeholder="my-company"
                    disabled={isEditing}
                    className="h-9"
                  />
                </div>
                <div className="space-y-1.5">
                  <Label className="text-xs font-medium">
                    Miras (extends)
                  </Label>
                  <Input
                    value={extendsProfile}
                    onChange={(e) => setExtendsProfile(e.target.value)}
                    placeholder="unsigned"
                    className="h-9"
                  />
                  <p className="text-[10px] text-muted-foreground/60">
                    Üst profilin tüm kurallarını miras alır.
                  </p>
                </div>
              </div>

              <div className="space-y-1.5">
                <Label className="text-xs font-medium">Açıklama</Label>
                <Input
                  value={description}
                  onChange={(e) => setDescription(e.target.value)}
                  placeholder="Bu profilin amacı ve kullanım alanı..."
                  className="h-9"
                />
              </div>
            </section>

            <Separator />

            {/* ── Section: Tabs ── */}
            <Tabs defaultValue="suppressions" className="w-full">
              <TabsList className="w-full">
                <TabsTrigger
                  value="suppressions"
                  className="gap-1.5 text-xs flex-1"
                >
                  <ShieldAlert className="h-3.5 w-3.5" />
                  Bastırma Kuralları
                  {rules.length > 0 && (
                    <Badge
                      variant="secondary"
                      className="h-4 min-w-[18px] px-1 text-[10px] ml-1"
                    >
                      {rules.length}
                    </Badge>
                  )}
                </TabsTrigger>
                <TabsTrigger
                  value="xsd-overrides"
                  className="gap-1.5 text-xs flex-1"
                >
                  <FileCode className="h-3.5 w-3.5" />
                  XSD Override
                  {xsdOverrides.length > 0 && (
                    <Badge
                      variant="secondary"
                      className="h-4 min-w-[18px] px-1 text-[10px] ml-1"
                    >
                      {xsdOverrides.length}
                    </Badge>
                  )}
                </TabsTrigger>
                <TabsTrigger
                  value="schematron-rules"
                  className="gap-1.5 text-xs flex-1"
                >
                  <ScrollText className="h-3.5 w-3.5" />
                  Ek Kurallar
                  {schematronRules.length > 0 && (
                    <Badge
                      variant="secondary"
                      className="h-4 min-w-[18px] px-1 text-[10px] ml-1"
                    >
                      {schematronRules.length}
                    </Badge>
                  )}
                </TabsTrigger>
              </TabsList>

              {/* ── Suppressions Tab ── */}
              <TabsContent value="suppressions" className="mt-4">
                <div className="space-y-2.5">
                  {rules.length === 0 ? (
                    <EmptyState
                      icon={ShieldAlert}
                      title="Bastırma kuralı yok"
                      description="Schematron veya XSD hatalarını profil bazlı bastırmak için kural ekleyin."
                      actionLabel="Kural ekle"
                      onAction={() =>
                        setRules([...rules, emptyRule()])
                      }
                    />
                  ) : (
                    <>
                      {rules.map((rule, idx) => (
                        <SuppressionCard
                          key={rule.id}
                          rule={rule}
                          onUpdate={(field, value) =>
                            setRules(
                              rules.map((r, i) =>
                                i === idx ? { ...r, [field]: value } : r
                              )
                            )
                          }
                          onRemove={() =>
                            setRules(rules.filter((_, i) => i !== idx))
                          }
                        />
                      ))}
                      <button
                        type="button"
                        onClick={() =>
                          setRules([...rules, emptyRule()])
                        }
                        className="flex items-center justify-center gap-1.5 w-full h-10 rounded-lg border border-dashed text-xs text-muted-foreground hover:text-foreground hover:border-foreground/20 transition-colors"
                      >
                        <Plus className="h-3.5 w-3.5" />
                        Kural ekle
                      </button>
                    </>
                  )}
                </div>
              </TabsContent>

              {/* ── XSD Overrides Tab ── */}
              <TabsContent value="xsd-overrides" className="mt-4">
                <div className="space-y-3">
                  {xsdOverrides.length === 0 ? (
                    <EmptyState
                      icon={FileCode}
                      title="XSD override yok"
                      description="GİB orijinal XSD dosyalarındaki minOccurs/maxOccurs değerlerini profil bazlı değiştirin."
                      actionLabel="Override ekle"
                      onAction={() =>
                        setXsdOverrides([
                          ...xsdOverrides,
                          emptyXsdOverride(),
                        ])
                      }
                    />
                  ) : (
                    <>
                      {xsdOverrides.map((ovr, idx) => (
                        <XsdOverrideCard
                          key={`ovr-${ovr.schemaType}-${ovr.element || idx}`}
                          ovr={ovr}
                          onUpdate={(field, value) =>
                            setXsdOverrides(
                              xsdOverrides.map((o, i) =>
                                i === idx ? { ...o, [field]: value } : o
                              )
                            )
                          }
                          onRemove={() =>
                            setXsdOverrides(
                              xsdOverrides.filter((_, i) => i !== idx)
                            )
                          }
                        />
                      ))}
                      <button
                        type="button"
                        onClick={() =>
                          setXsdOverrides([
                            ...xsdOverrides,
                            emptyXsdOverride(),
                          ])
                        }
                        className="flex items-center justify-center gap-1.5 w-full h-10 rounded-lg border border-dashed text-xs text-muted-foreground hover:text-foreground hover:border-foreground/20 transition-colors"
                      >
                        <Plus className="h-3.5 w-3.5" />
                        Override ekle
                      </button>
                    </>
                  )}
                </div>
              </TabsContent>

              {/* ── Schematron Rules Tab ── */}
              <TabsContent value="schematron-rules" className="mt-4">
                <div className="space-y-3">
                  {schematronRules.length === 0 ? (
                    <EmptyState
                      icon={ScrollText}
                      title="Özel Schematron kuralı yok"
                      description="Derleme öncesi Schematron dosyasına ek iş kuralları (assert) ekleyin. Kurallar ISO Schematron pipeline'ından geçerek derlenir."
                      actionLabel="Kural ekle"
                      onAction={() =>
                        setSchematronRules([
                          ...schematronRules,
                          emptySchematronRule(),
                        ])
                      }
                    />
                  ) : (
                    <>
                      {schematronRules.map((rule, idx) => (
                        <SchematronRuleCard
                          key={`sch-${rule.schematronType}-${rule.id || idx}`}
                          rule={rule}
                          onUpdate={(field, value) =>
                            setSchematronRules(
                              schematronRules.map((r, i) =>
                                i === idx ? { ...r, [field]: value } : r
                              )
                            )
                          }
                          onRemove={() =>
                            setSchematronRules(
                              schematronRules.filter((_, i) => i !== idx)
                            )
                          }
                        />
                      ))}
                      <button
                        type="button"
                        onClick={() =>
                          setSchematronRules([
                            ...schematronRules,
                            emptySchematronRule(),
                          ])
                        }
                        className="flex items-center justify-center gap-1.5 w-full h-10 rounded-lg border border-dashed text-xs text-muted-foreground hover:text-foreground hover:border-foreground/20 transition-colors"
                      >
                        <Plus className="h-3.5 w-3.5" />
                        Kural ekle
                      </button>
                    </>
                  )}
                </div>
              </TabsContent>
            </Tabs>
          </div>
        </ScrollArea>

        {/* ── Fixed Footer ── */}
        <div className="shrink-0 border-t bg-muted/30 px-6 py-4 flex items-center justify-end gap-3">
          <Button
            variant="outline"
            onClick={() => onOpenChange(false)}
            disabled={saving}
          >
            İptal
          </Button>
          <Button onClick={handleSave} disabled={saving}>
            {saving ? (
              <Loader2 className="mr-2 h-4 w-4 animate-spin" />
            ) : (
              <Save className="mr-2 h-4 w-4" />
            )}
            {isEditing ? "Kaydet" : "Oluştur"}
          </Button>
        </div>
      </SheetContent>
    </Sheet>
  );
}

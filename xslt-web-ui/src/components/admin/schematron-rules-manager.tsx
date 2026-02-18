import { useEffect, useRef, useState } from "react";
import {
  Plus,
  Save,
  Shield,
  AlertTriangle,
  Loader2,
  ChevronsUpDown,
} from "lucide-react";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { toast } from "sonner";
import { useSchematronRules, useSaveSchematronRules } from "@/api/hooks";
import {
  SchematronRuleCard,
  emptySchematronRule,
  type SchematronRuleFormData,
} from "./schematron-rule-card";

export function SchematronRulesManager() {
  const { data, isLoading, error } = useSchematronRules();
  const saveMutation = useSaveSchematronRules();

  const [rules, setRules] = useState<SchematronRuleFormData[]>([]);
  const [isDirty, setIsDirty] = useState(false);
  const [openSet, setOpenSet] = useState<Set<number>>(new Set());
  const nextKeyRef = useRef(0);
  const [keys, setKeys] = useState<number[]>([]);

  useEffect(() => {
    if (data?.rules) {
      const flatRules: SchematronRuleFormData[] = [];
      for (const [schematronType, ruleList] of Object.entries(data.rules)) {
        for (const rule of ruleList) {
          flatRules.push({
            schematronType,
            context: rule.context,
            test: rule.test,
            message: rule.message,
            id: rule.id ?? "",
          });
        }
      }
      setRules(flatRules);
      const newKeys = flatRules.map(() => nextKeyRef.current++);
      setKeys(newKeys);
      setOpenSet(new Set());
      setIsDirty(false);
    }
  }, [data]);

  const handleAdd = () => {
    const newKey = nextKeyRef.current++;
    setRules((prev) => [...prev, emptySchematronRule()]);
    setKeys((prev) => [...prev, newKey]);
    setOpenSet((prev) => new Set([...prev, newKey]));
    setIsDirty(true);
  };

  const toggleAll = () => {
    if (openSet.size === keys.length) {
      setOpenSet(new Set());
    } else {
      setOpenSet(new Set(keys));
    }
  };

  const handleUpdate = (index: number, field: keyof SchematronRuleFormData, value: string) => {
    setRules((prev) => {
      const next = [...prev];
      next[index] = { ...next[index], [field]: value };
      return next;
    });
    setIsDirty(true);
  };

  const handleRemove = (index: number) => {
    const removedKey = keys[index];
    setRules((prev) => prev.filter((_, i) => i !== index));
    setKeys((prev) => prev.filter((_, i) => i !== index));
    setOpenSet((prev) => {
      const next = new Set(prev);
      next.delete(removedKey);
      return next;
    });
    setIsDirty(true);
  };

  const handleSave = async () => {
    const grouped: Record<string, { context: string; test: string; message: string; id?: string }[]> = {};

    for (const rule of rules) {
      if (!rule.context.trim() || !rule.test.trim() || !rule.message.trim()) continue;
      if (!grouped[rule.schematronType]) grouped[rule.schematronType] = [];
      grouped[rule.schematronType].push({
        context: rule.context.trim(),
        test: rule.test.trim(),
        message: rule.message.trim(),
        id: rule.id.trim() || undefined,
      });
    }

    try {
      await saveMutation.mutateAsync(grouped);
      setIsDirty(false);
      toast.success("Global Schematron kuralları kaydedildi", {
        description: `${Object.values(grouped).flat().length} kural kaydedildi.`,
      });
    } catch {
      toast.error("Global Schematron kuralları kaydedilemedi.");
    }
  };

  if (isLoading) {
    return (
      <div className="rounded-xl border bg-card shadow-xs p-8">
        <div className="flex items-center justify-center gap-3 text-muted-foreground">
          <Loader2 className="h-5 w-5 animate-spin" />
          <span className="text-sm">Yükleniyor...</span>
        </div>
      </div>
    );
  }

  if (error) {
    return (
      <div className="rounded-xl border bg-card shadow-xs p-8">
        <div className="flex items-center gap-2 text-sm text-destructive">
          <AlertTriangle className="h-4 w-4" />
          Global kurallar yüklenemedi.
        </div>
      </div>
    );
  }

  return (
    <div className="rounded-xl border bg-card shadow-xs overflow-hidden">
      <div className="p-5">
        <div className="flex items-start justify-between gap-4">
          <div>
            <div className="flex items-center gap-2 mb-1">
              <Shield className="h-4 w-4 text-primary" />
              <h3 className="text-sm font-semibold">Global Schematron Kuralları</h3>
              {rules.length > 0 && (
                <Badge variant="secondary" className="text-xs rounded-md">{rules.length}</Badge>
              )}
            </div>
            <p className="text-xs text-muted-foreground leading-relaxed">
              Profil bağımsız, her doğrulama isteğinde otomatik uygulanan özel Schematron kuralları.
            </p>
          </div>
          <div className="flex items-center gap-2 shrink-0">
            {rules.length > 1 && (
              <Button variant="ghost" size="sm" onClick={toggleAll} className="gap-1.5 text-muted-foreground rounded-lg">
                <ChevronsUpDown className="h-3.5 w-3.5" />
                {openSet.size === keys.length ? "Daralt" : "Genişlet"}
              </Button>
            )}
            <Button variant="outline" size="sm" onClick={handleAdd} className="gap-1.5 rounded-lg">
              <Plus className="h-3.5 w-3.5" />
              Kural Ekle
            </Button>
            <Button size="sm" onClick={handleSave} disabled={!isDirty || saveMutation.isPending} className="gap-1.5 rounded-lg">
              {saveMutation.isPending ? <Loader2 className="h-3.5 w-3.5 animate-spin" /> : <Save className="h-3.5 w-3.5" />}
              Kaydet
            </Button>
          </div>
        </div>
      </div>

      <div className="border-t p-5 pt-4">
        {rules.length === 0 ? (
          <div className="text-center py-10 text-sm text-muted-foreground">
            <Shield className="h-8 w-8 mx-auto mb-3 opacity-15" />
            <p className="font-medium">Global Schematron kuralı tanımlanmamış.</p>
            <p className="text-xs mt-1">
              Eklenen kurallar profil seçilsin seçilmesin her doğrulamada aktif olur.
            </p>
          </div>
        ) : (
          <div className="space-y-3">
            {rules.map((rule, index) => (
              <SchematronRuleCard
                key={keys[index]}
                rule={rule}
                onUpdate={(field, value) => handleUpdate(index, field, value)}
                onRemove={() => handleRemove(index)}
                open={openSet.has(keys[index])}
                onToggle={() =>
                  setOpenSet((prev) => {
                    const next = new Set(prev);
                    if (next.has(keys[index])) next.delete(keys[index]);
                    else next.add(keys[index]);
                    return next;
                  })
                }
              />
            ))}
          </div>
        )}
      </div>
    </div>
  );
}

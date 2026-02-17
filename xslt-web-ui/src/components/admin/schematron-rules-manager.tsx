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
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card";
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

  const handleUpdate = (
    index: number,
    field: keyof SchematronRuleFormData,
    value: string
  ) => {
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
    const grouped: Record<
      string,
      { context: string; test: string; message: string; id?: string }[]
    > = {};

    for (const rule of rules) {
      if (!rule.context.trim() || !rule.test.trim() || !rule.message.trim()) {
        continue;
      }
      if (!grouped[rule.schematronType]) {
        grouped[rule.schematronType] = [];
      }
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
      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2 text-base">
            <Shield className="h-4 w-4" />
            Global Schematron Kuralları
          </CardTitle>
        </CardHeader>
        <CardContent className="flex items-center justify-center py-8 text-muted-foreground">
          <Loader2 className="h-4 w-4 animate-spin mr-2" />
          Yükleniyor...
        </CardContent>
      </Card>
    );
  }

  if (error) {
    return (
      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2 text-base">
            <Shield className="h-4 w-4" />
            Global Schematron Kuralları
          </CardTitle>
        </CardHeader>
        <CardContent>
          <div className="flex items-center gap-2 text-sm text-destructive">
            <AlertTriangle className="h-4 w-4" />
            Global kurallar yüklenemedi.
          </div>
        </CardContent>
      </Card>
    );
  }

  return (
    <Card>
      <CardHeader>
        <div className="flex items-center justify-between">
          <div className="space-y-1">
            <CardTitle className="flex items-center gap-2 text-base">
              <Shield className="h-4 w-4" />
              Global Schematron Kuralları
              {rules.length > 0 && (
                <Badge variant="secondary" className="ml-1 text-xs">
                  {rules.length}
                </Badge>
              )}
            </CardTitle>
            <CardDescription>
              Profil bağımsız, her doğrulama isteğinde otomatik uygulanan özel
              Schematron kuralları. Bu kurallar tüm profiller için her zaman
              aktiftir.
            </CardDescription>
          </div>
          <div className="flex items-center gap-2">
            {rules.length > 1 && (
              <Button
                variant="ghost"
                size="sm"
                onClick={toggleAll}
                className="gap-1.5 text-muted-foreground"
              >
                <ChevronsUpDown className="h-3.5 w-3.5" />
                {openSet.size === keys.length ? "Daralt" : "Genislet"}
              </Button>
            )}
            <Button
              variant="outline"
              size="sm"
              onClick={handleAdd}
              className="gap-1.5"
            >
              <Plus className="h-3.5 w-3.5" />
              Kural Ekle
            </Button>
            <Button
              size="sm"
              onClick={handleSave}
              disabled={!isDirty || saveMutation.isPending}
              className="gap-1.5"
            >
              {saveMutation.isPending ? (
                <Loader2 className="h-3.5 w-3.5 animate-spin" />
              ) : (
                <Save className="h-3.5 w-3.5" />
              )}
              Kaydet
            </Button>
          </div>
        </div>
      </CardHeader>
      <CardContent>
        {rules.length === 0 ? (
          <div className="text-center py-8 text-sm text-muted-foreground">
            <Shield className="h-8 w-8 mx-auto mb-2 opacity-20" />
            <p>Global Schematron kuralı tanımlanmamış.</p>
            <p className="text-xs mt-1">
              Eklenen kurallar profil seçilsin seçilmesin her doğrulamada aktif
              olur.
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
                    if (next.has(keys[index])) {
                      next.delete(keys[index]);
                    } else {
                      next.add(keys[index]);
                    }
                    return next;
                  })
                }
              />
            ))}
          </div>
        )}
      </CardContent>
    </Card>
  );
}

import { useState } from "react";
import { Trash2, ChevronRight } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { SCHEMATRON_VALIDATION_TYPES } from "@/api/types";

export interface SchematronRuleFormData {
  schematronType: string;
  context: string;
  test: string;
  message: string;
  id: string;
}

export function emptySchematronRule(): SchematronRuleFormData {
  return {
    schematronType: "UBLTR_MAIN",
    context: "",
    test: "",
    message: "",
    id: "",
  };
}

function ruleSummary(rule: SchematronRuleFormData): string {
  const id = rule.id?.trim();
  const ctx = rule.context?.trim();
  const msg = rule.message?.trim();
  if (id && msg) return `[${id}] ${msg}`;
  if (msg) return msg;
  if (id && ctx) return `[${id}] ${ctx}`;
  if (ctx) return ctx;
  return "Yeni kural";
}

export function SchematronRuleCard({
  rule,
  onUpdate,
  onRemove,
  open: controlledOpen,
  onToggle,
  defaultOpen = true,
}: {
  rule: SchematronRuleFormData;
  onUpdate: (field: keyof SchematronRuleFormData, value: string) => void;
  onRemove: () => void;
  open?: boolean;
  onToggle?: () => void;
  defaultOpen?: boolean;
}) {
  const [internalOpen, setInternalOpen] = useState(defaultOpen);
  const isControlled = controlledOpen !== undefined;
  const open = isControlled ? controlledOpen : internalOpen;
  const toggle = isControlled
    ? onToggle!
    : () => setInternalOpen((v) => !v);

  return (
    <div className="group rounded-xl border border-border bg-muted/50 transition-all duration-200 hover:border-border hover:bg-muted">
      <div
        className="flex items-center gap-2 px-4 py-2.5 cursor-pointer select-none"
        onClick={toggle}
      >
        <ChevronRight
          className={`h-3.5 w-3.5 shrink-0 text-muted-foreground/60 transition-transform duration-200 ${open ? "rotate-90" : ""}`}
        />
        <span className="text-xs font-medium text-muted-foreground shrink-0">
          {rule.schematronType}
        </span>
        <span className="text-xs text-foreground/70 truncate">
          {ruleSummary(rule)}
        </span>
        <div className="ml-auto flex items-center gap-1 shrink-0">
          <Button
            variant="ghost"
            size="sm"
            className="h-7 w-7 p-0 opacity-0 group-hover:opacity-100 transition-opacity text-red-400 hover:text-red-300 hover:bg-red-500/10"
            onClick={(e) => {
              e.stopPropagation();
              onRemove();
            }}
            aria-label="Kural sil"
          >
            <Trash2 className="h-3.5 w-3.5" />
          </Button>
        </div>
      </div>

      {open && (
        <div className="px-4 pb-4 pt-1 space-y-3 border-t border-border">
          <div className="grid gap-3 sm:grid-cols-[160px_1fr]">
            <div className="space-y-1.5">
              <Label className="text-[11px] text-muted-foreground font-medium">
                Schematron Tipi
              </Label>
              <Select
                value={rule.schematronType}
                onValueChange={(v) => onUpdate("schematronType", v)}
              >
                <SelectTrigger className="h-9 text-xs">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {SCHEMATRON_VALIDATION_TYPES.map((t) => (
                    <SelectItem key={t} value={t} className="text-xs">
                      {t}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
            <div className="space-y-1.5">
              <Label className="text-[11px] text-muted-foreground font-medium">
                Kural ID
              </Label>
              <Input
                value={rule.id}
                onChange={(e) => onUpdate("id", e.target.value)}
                placeholder="CUSTOM-001"
                className="h-9 text-sm font-mono"
              />
              <p className="text-[10px] text-muted-foreground/60">
                Bastırma referansı için benzersiz ID (opsiyonel)
              </p>
            </div>
          </div>

          <div className="space-y-1.5">
            <Label className="text-[11px] text-muted-foreground font-medium">
              Context (XPath)
            </Label>
            <Input
              value={rule.context}
              onChange={(e) => onUpdate("context", e.target.value)}
              placeholder="inv:Invoice"
              className="h-9 text-sm font-mono"
            />
            <p className="text-[10px] text-muted-foreground/60">
              Kuralın uygulanacağı XPath bağlamı
            </p>
          </div>

          <div className="space-y-1.5">
            <Label className="text-[11px] text-muted-foreground font-medium">
              Test (XPath İfadesi)
            </Label>
            <Input
              value={rule.test}
              onChange={(e) => onUpdate("test", e.target.value)}
              placeholder="cac:AccountingSupplierParty/cac:Party/cac:PostalAddress"
              className="h-9 text-sm font-mono"
            />
            <p className="text-[10px] text-muted-foreground/60">
              Doğrulanacak XPath koşulu (true = geçerli)
            </p>
          </div>

          <div className="space-y-1.5">
            <Label className="text-[11px] text-muted-foreground font-medium">
              Hata Mesajı
            </Label>
            <Textarea
              value={rule.message}
              onChange={(e) => onUpdate("message", e.target.value)}
              placeholder="Satıcı adresi zorunludur."
              className="text-sm min-h-[60px] resize-none"
              rows={2}
            />
            <p className="text-[10px] text-muted-foreground/60">
              Kural ihlal edildiğinde gösterilecek mesaj
            </p>
          </div>
        </div>
      )}
    </div>
  );
}

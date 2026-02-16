import { useState } from "react";
import { Trash2, ChevronDown, X } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Badge } from "@/components/ui/badge";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { ScopeSelect } from "./scope-select";

export interface RuleFormData {
  id: string;
  match: string;
  pattern: string;
  scope: string[];
  description: string;
}

let _ruleIdCounter = 0;
export function emptyRule(): RuleFormData {
  return { id: `rule-${Date.now()}-${_ruleIdCounter++}`, match: "ruleId", pattern: "", scope: [], description: "" };
}

const MATCH_OPTIONS = [
  { value: "ruleId", label: "ruleId", hint: "Rule/pattern ID (regex)" },
  { value: "ruleIdEquals", label: "ruleIdEquals", hint: "Rule/pattern ID (tam eşleşme)" },
  { value: "test", label: "test", hint: "XPath test ifadesi (regex)" },
  { value: "testEquals", label: "testEquals", hint: "XPath test ifadesi (tam eşleşme)" },
  { value: "text", label: "text", hint: "Hata mesajı (regex)" },
] as const;

const EQUALS_MODES = new Set(["testEquals", "ruleIdEquals"]);

export function SuppressionCard({
  rule,
  onUpdate,
  onRemove,
}: {
  rule: RuleFormData;
  onUpdate: (field: keyof RuleFormData, value: unknown) => void;
  onRemove: () => void;
}) {
  const [open, setOpen] = useState(false);

  return (
    <div className="rounded-lg border bg-card overflow-hidden transition-colors hover:border-foreground/15">
      {/* ── Summary row ── */}
      <button
        type="button"
        onClick={() => setOpen(!open)}
        className="flex items-center gap-2.5 w-full px-4 py-3 text-left cursor-pointer select-none transition-colors hover:bg-accent/30"
        aria-expanded={open}
        aria-label={open ? "Kural detaylarını gizle" : "Kural detaylarını göster"}
      >
        <Badge variant="outline" className="text-[10px] font-mono px-1.5 shrink-0">
          {rule.match}
        </Badge>

        <span className="text-sm font-mono text-foreground/80 truncate flex-1 min-w-0">
          {rule.pattern || (
            <span className="text-muted-foreground/50 font-sans italic text-xs">
              pattern girilmedi
            </span>
          )}
        </span>

        {rule.scope.length > 0 && (
          <div className="flex items-center gap-1 shrink-0">
            {rule.scope.slice(0, 2).map((s) => (
              <Badge key={s} variant="secondary" className="text-[9px] px-1 py-0 font-mono">
                {s}
              </Badge>
            ))}
            {rule.scope.length > 2 && (
              <span className="text-[10px] text-muted-foreground">
                +{rule.scope.length - 2}
              </span>
            )}
          </div>
        )}

        <ChevronDown
          className={`h-4 w-4 text-muted-foreground/50 shrink-0 transition-transform duration-200 ${
            open ? "rotate-180" : ""
          }`}
        />
      </button>

      {/* ── Expanded edit ── */}
      {open && (
        <div className="border-t bg-muted/30 px-4 py-4 space-y-4">
          <div className="grid gap-4 sm:grid-cols-[140px_1fr]">
            <div className="space-y-1.5">
              <Label className="text-[11px] text-muted-foreground font-medium">
                Eşleştirme
              </Label>
              <Select
                value={rule.match}
                onValueChange={(v) => onUpdate("match", v)}
              >
                <SelectTrigger className="h-9 text-xs">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {MATCH_OPTIONS.map((opt) => (
                    <SelectItem key={opt.value} value={opt.value}>
                      <span className="font-mono">{opt.label}</span>
                      <span className="ml-1.5 text-muted-foreground text-[10px] font-normal">
                        {opt.hint}
                      </span>
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
            <div className="space-y-1.5">
              <Label className="text-[11px] text-muted-foreground font-medium">
                {EQUALS_MODES.has(rule.match) ? "Pattern (tam eşleşme)" : "Pattern (regex)"}
              </Label>
              <Input
                value={rule.pattern}
                onChange={(e) => onUpdate("pattern", e.target.value)}
                placeholder={EQUALS_MODES.has(rule.match)
                  ? "($countKurumUnvani=1 and not($countAdiSoyadi=1))..."
                  : ".*Signature.*"
                }
                className="h-9 text-sm font-mono"
              />
            </div>
          </div>

          <div className="space-y-1.5">
            <Label className="text-[11px] text-muted-foreground font-medium">
              Açıklama
            </Label>
            <Input
              value={rule.description}
              onChange={(e) => onUpdate("description", e.target.value)}
              placeholder="Bu kuralın amacı (opsiyonel)"
              className="h-9 text-sm"
            />
          </div>

          <div className="space-y-1.5">
            <Label className="text-[11px] text-muted-foreground font-medium">
              Kapsam
            </Label>
            {/* Selected scope tags */}
            {rule.scope.length > 0 && (
              <div className="flex flex-wrap gap-1 mb-1.5">
                {rule.scope.map((s) => (
                  <Badge
                    key={s}
                    variant="secondary"
                    className="gap-0.5 pl-2 pr-1 py-0.5 text-[11px] font-mono"
                  >
                    {s}
                    <button
                      type="button"
                      onClick={() =>
                        onUpdate(
                          "scope",
                          rule.scope.filter((x) => x !== s)
                        )
                      }
                      className="ml-0.5 rounded-full p-0.5 hover:bg-foreground/10"
                      aria-label={`${s} kapsamını kaldır`}
                    >
                      <X className="h-2.5 w-2.5" />
                    </button>
                  </Badge>
                ))}
              </div>
            )}
            <ScopeSelect
              value={rule.scope}
              onChange={(v) => onUpdate("scope", v)}
            />
          </div>

          <div className="flex justify-end pt-1">
            <Button
              variant="ghost"
              size="sm"
              className="h-8 text-xs text-destructive hover:text-destructive hover:bg-destructive/10"
              onClick={onRemove}
            >
              <Trash2 className="mr-1.5 h-3.5 w-3.5" />
              Kuralı sil
            </Button>
          </div>
        </div>
      )}
    </div>
  );
}

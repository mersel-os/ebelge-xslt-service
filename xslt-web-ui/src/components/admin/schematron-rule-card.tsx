import { useState, useRef, useCallback, useEffect, useMemo, type ReactNode } from "react";
import { Trash2, ChevronRight } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
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

function AutoResizeInput({
  value,
  onChange,
  placeholder,
  className,
}: {
  value: string;
  onChange: (e: React.ChangeEvent<HTMLTextAreaElement>) => void;
  placeholder?: string;
  className?: string;
}) {
  const ref = useRef<HTMLTextAreaElement>(null);

  const resize = useCallback(() => {
    const el = ref.current;
    if (!el) return;
    el.style.height = "auto";
    el.style.height = `${Math.max(36, el.scrollHeight)}px`;
  }, []);

  useEffect(() => resize(), [value, resize]);

  return (
    <textarea
      ref={ref}
      value={value}
      onChange={(e) => {
        e.target.value = e.target.value.replace(/\n/g, "");
        onChange(e);
        resize();
      }}
      onKeyDown={(e) => {
        if (e.key === "Enter") e.preventDefault();
      }}
      placeholder={placeholder}
      rows={1}
      className={className}
      onInput={resize}
    />
  );
}

const PLACEHOLDER_SPLIT = /(\{\{.+?\}\})/g;
const PLACEHOLDER_TEST = /\{\{.+?\}\}/;

function highlightPlaceholders(text: string): ReactNode[] {
  if (!text) return [];
  const parts = text.split(PLACEHOLDER_SPLIT);
  return parts.map((part, i) =>
    PLACEHOLDER_TEST.test(part) ? (
      <mark
        key={i}
        className="rounded-sm bg-amber-400/20 text-amber-300 px-[1px] ring-1 ring-amber-400/30"
      >
        {part}
      </mark>
    ) : (
      <span key={i}>{part}</span>
    )
  );
}

function HighlightedTextarea({
  value,
  onChange,
  placeholder,
  rows = 2,
}: {
  value: string;
  onChange: (e: React.ChangeEvent<HTMLTextAreaElement>) => void;
  placeholder?: string;
  rows?: number;
}) {
  const textareaRef = useRef<HTMLTextAreaElement>(null);
  const backdropRef = useRef<HTMLDivElement>(null);

  const hasPlaceholders = useMemo(() => PLACEHOLDER_TEST.test(value), [value]);
  const highlighted = useMemo(() => highlightPlaceholders(value), [value]);

  const syncScroll = useCallback(() => {
    if (textareaRef.current && backdropRef.current) {
      backdropRef.current.scrollTop = textareaRef.current.scrollTop;
      backdropRef.current.scrollLeft = textareaRef.current.scrollLeft;
    }
  }, []);

  return (
    <div className="relative">
      {hasPlaceholders && (
        <div
          ref={backdropRef}
          aria-hidden
          className="pointer-events-none absolute inset-0 overflow-hidden whitespace-pre-wrap break-words rounded-md px-3 py-2 text-sm text-foreground leading-[1.6]"
        >
          {highlighted}
        </div>
      )}
      <textarea
        ref={textareaRef}
        value={value}
        onChange={onChange}
        onScroll={syncScroll}
        placeholder={placeholder}
        rows={rows}
        className={`relative w-full rounded-md border border-input bg-transparent px-3 py-2 text-sm shadow-xs ring-offset-background placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring min-h-[60px] resize-none leading-[1.6] ${hasPlaceholders ? "text-transparent caret-foreground" : "text-foreground"}`}
      />
    </div>
  );
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
            <AutoResizeInput
              value={rule.context}
              onChange={(e) => onUpdate("context", e.target.value)}
              placeholder="inv:Invoice"
              className="flex w-full rounded-md border border-input bg-transparent px-3 py-2 text-sm font-mono shadow-xs ring-offset-background placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring resize-none overflow-hidden leading-snug"
            />
            <p className="text-[10px] text-muted-foreground/60">
              Kuralın uygulanacağı XPath bağlamı
            </p>
          </div>

          <div className="space-y-1.5">
            <Label className="text-[11px] text-muted-foreground font-medium">
              Test (XPath İfadesi)
            </Label>
            <AutoResizeInput
              value={rule.test}
              onChange={(e) => onUpdate("test", e.target.value)}
              placeholder="cac:AccountingSupplierParty/cac:Party/cac:PostalAddress"
              className="flex w-full rounded-md border border-input bg-transparent px-3 py-2 text-sm font-mono shadow-xs ring-offset-background placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring resize-none overflow-hidden leading-snug"
            />
            <p className="text-[10px] text-muted-foreground/60">
              Doğrulanacak XPath koşulu (true = geçerli)
            </p>
          </div>

          <div className="space-y-1.5">
            <Label className="text-[11px] text-muted-foreground font-medium">
              Hata Mesajı
            </Label>
            <HighlightedTextarea
              value={rule.message}
              onChange={(e) => onUpdate("message", e.target.value)}
              placeholder="Satıcı VKN ({{cac:AccountingSupplierParty/.../cbc:ID}}) eşleşmiyor."
              rows={2}
            />
            <div className="rounded-md bg-blue-500/5 border border-blue-500/10 px-2.5 py-2 text-[10px] text-blue-400/90 leading-relaxed">
              <span className="font-semibold">Dinamik değer desteği:</span>{" "}
              Mesaj içinde <code className="rounded bg-blue-500/10 px-1 py-0.5 font-mono text-[10px]">{"{{xpath_ifadesi}}"}</code> kullanarak
              XML'deki değerleri hata mesajına yerleştirebilirsiniz.
              Örn: <code className="rounded bg-blue-500/10 px-1 py-0.5 font-mono text-[10px]">{"{{cbc:ID}}"}</code>,{" "}
              <code className="rounded bg-blue-500/10 px-1 py-0.5 font-mono text-[10px]">{"{{$sessionVkn}}"}</code>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

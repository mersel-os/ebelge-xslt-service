import { Trash2 } from "lucide-react";
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
import { SCHEMA_VALIDATION_TYPES } from "@/api/types";

export interface XsdOverrideFormData {
  schemaType: string;
  element: string;
  minOccurs: string;
  maxOccurs: string;
}

export function emptyXsdOverride(): XsdOverrideFormData {
  return { schemaType: "INVOICE", element: "", minOccurs: "0", maxOccurs: "" };
}

export function XsdOverrideCard({
  ovr,
  onUpdate,
  onRemove,
}: {
  ovr: XsdOverrideFormData;
  onUpdate: (field: keyof XsdOverrideFormData, value: string) => void;
  onRemove: () => void;
}) {
  return (
    <div className="group rounded-lg border bg-card p-4 space-y-3 transition-colors hover:border-foreground/15">
      <div className="flex items-start justify-between gap-3">
        <div className="grid gap-3 sm:grid-cols-[160px_1fr] flex-1 min-w-0">
          <div className="space-y-1.5">
            <Label className="text-[11px] text-muted-foreground font-medium">
              Schema Tipi
            </Label>
            <Select
              value={ovr.schemaType}
              onValueChange={(v) => onUpdate("schemaType", v)}
            >
              <SelectTrigger className="h-9 text-xs">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                {SCHEMA_VALIDATION_TYPES.map((t) => (
                  <SelectItem key={t} value={t} className="text-xs">
                    {t}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
          <div className="space-y-1.5">
            <Label className="text-[11px] text-muted-foreground font-medium">
              Element (ref)
            </Label>
            <Input
              value={ovr.element}
              onChange={(e) => onUpdate("element", e.target.value)}
              placeholder="cac:Signature"
              className="h-9 text-sm font-mono"
            />
          </div>
        </div>

        <Button
          variant="ghost"
          size="sm"
          className="h-8 w-8 p-0 mt-5 opacity-0 group-hover:opacity-100 focus-visible:opacity-100 transition-opacity text-destructive hover:text-destructive hover:bg-destructive/10 shrink-0"
          onClick={onRemove}
          aria-label="Override sil"
        >
          <Trash2 className="h-3.5 w-3.5" />
        </Button>
      </div>

      <div className="grid gap-3 sm:grid-cols-2">
        <div className="space-y-1.5">
          <Label className="text-[11px] text-muted-foreground font-medium">
            minOccurs
          </Label>
          <Input
            value={ovr.minOccurs}
            onChange={(e) => onUpdate("minOccurs", e.target.value)}
            placeholder="0"
            className="h-9 text-sm font-mono"
          />
          <p className="text-[10px] text-muted-foreground/60">
            Minimum tekrar sayısı (0 = opsiyonel)
          </p>
        </div>
        <div className="space-y-1.5">
          <Label className="text-[11px] text-muted-foreground font-medium">
            maxOccurs
          </Label>
          <Input
            value={ovr.maxOccurs}
            onChange={(e) => onUpdate("maxOccurs", e.target.value)}
            placeholder="unbounded"
            className="h-9 text-sm font-mono"
          />
          <p className="text-[10px] text-muted-foreground/60">
            Maksimum tekrar sayısı (boş = değiştirme)
          </p>
        </div>
      </div>
    </div>
  );
}

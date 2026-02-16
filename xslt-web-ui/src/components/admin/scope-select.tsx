import { ChevronDown } from "lucide-react";
import { Button } from "@/components/ui/button";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { Separator } from "@/components/ui/separator";
import {
  SCHEMA_VALIDATION_TYPES,
  SCHEMATRON_VALIDATION_TYPES,
} from "@/api/types";

const SCOPE_GROUPS = [
  { label: "Schema (XSD)", items: [...SCHEMA_VALIDATION_TYPES] },
  { label: "Schematron", items: [...SCHEMATRON_VALIDATION_TYPES] },
] as const;

export function ScopeSelect({
  value,
  onChange,
}: {
  value: string[];
  onChange: (v: string[]) => void;
}) {
  const toggle = (item: string) =>
    onChange(
      value.includes(item) ? value.filter((s) => s !== item) : [...value, item]
    );

  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <button
          type="button"
          className="flex items-center justify-between w-full h-9 rounded-md border border-input bg-background px-3 text-xs transition-colors hover:bg-accent/50 focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring"
          aria-label="Kapsam seç"
        >
          <span className="text-muted-foreground truncate">
            {value.length === 0
              ? "Tüm tipler (kapsam yok)"
              : `${value.length} tip seçili`}
          </span>
          <ChevronDown className="h-3.5 w-3.5 opacity-40 shrink-0 ml-2" />
        </button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="start" className="w-72 p-0">
        <div className="p-3 space-y-3">
          {SCOPE_GROUPS.map((group) => (
            <div key={group.label}>
              <p className="text-[10px] font-semibold text-muted-foreground uppercase tracking-wider mb-1.5">
                {group.label}
              </p>
              <div className="grid grid-cols-2 gap-1">
                {group.items.map((item) => {
                  const active = value.includes(item);
                  return (
                    <button
                      key={item}
                      type="button"
                      role="checkbox"
                      aria-checked={active}
                      onClick={() => toggle(item)}
                      className={`text-left text-[11px] px-2 py-1.5 rounded-md transition-colors ${
                        active
                          ? "bg-primary text-primary-foreground font-medium"
                          : "text-foreground/70 hover:bg-accent"
                      }`}
                    >
                      {item}
                    </button>
                  );
                })}
              </div>
            </div>
          ))}
        </div>
        {value.length > 0 && (
          <>
            <Separator />
            <div className="p-2">
              <Button
                variant="ghost"
                size="sm"
                className="w-full h-7 text-[11px] text-muted-foreground"
                onClick={() => onChange([])}
              >
                Tümü temizle
              </Button>
            </div>
          </>
        )}
      </DropdownMenuContent>
    </DropdownMenu>
  );
}

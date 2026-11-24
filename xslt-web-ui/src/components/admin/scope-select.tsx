import { Fragment } from "react";
import { ChevronDown } from "lucide-react";
import { Button } from "@/components/ui/button";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { Separator } from "@/components/ui/separator";
import { useCustomDocumentTypes } from "@/api/hooks";
import {
  SCHEMA_VALIDATION_TYPES,
  SCHEMATRON_VALIDATION_TYPES,
} from "@/api/types";

interface ScopeItem {
  value: string;
  title?: string;
  /** Profilde seçili ama artık tanımlı olmayan özel tür */
  missing?: boolean;
}

interface ScopeGroup {
  label: string;
  items: ScopeItem[];
}

const BUILT_IN_GROUPS: ScopeGroup[] = [
  {
    label: "Schema (XSD)",
    items: SCHEMA_VALIDATION_TYPES.map((value) => ({ value })),
  },
  {
    label: "Schematron",
    items: SCHEMATRON_VALIDATION_TYPES.map((value) => ({ value })),
  },
];

const BUILT_IN_VALUES = new Set<string>(
  BUILT_IN_GROUPS.flatMap((group) => group.items.map((item) => item.value))
);

/**
 * Asset dizininde tanımlı özel belge türleri. Profilde seçili olup tanımı kaldırılmış
 * türler de listelenir ki kullanıcı seçimi görüp kaldırabilsin.
 */
function useCustomScopeGroup(value: string[]): ScopeGroup | null {
  const { data: customTypes = [] } = useCustomDocumentTypes();
  const items: ScopeItem[] = customTypes
    .filter((type) => !BUILT_IN_VALUES.has(type.id))
    .map((type) => ({ value: type.id, title: type.description ?? undefined }));
  const known = new Set(items.map((item) => item.value));
  for (const selected of value) {
    if (!BUILT_IN_VALUES.has(selected) && !known.has(selected)) {
      items.push({
        value: selected,
        title: "Bu tür asset dizininde tanımlı değil",
        missing: true,
      });
      known.add(selected);
    }
  }
  return items.length > 0 ? { label: "Özel belge türleri", items } : null;
}

/**
 * Tip adı iki sütunlu hücreye sığmazsa alt çizgilerden sonra satır kırar;
 * önceden EARCHIVE_REPORT_EDOVIZ gibi adlar yandaki hücrenin üstüne taşıyordu.
 */
function breakableName(name: string) {
  const parts = name.split("_");
  return parts.map((part, index) => (
    <Fragment key={index}>
      {part}
      {index < parts.length - 1 && (
        <>
          _<wbr />
        </>
      )}
    </Fragment>
  ));
}

export function ScopeSelect({
  value,
  onChange,
}: {
  value: string[];
  onChange: (v: string[]) => void;
}) {
  const customGroup = useCustomScopeGroup(value);
  const groups = customGroup ? [...BUILT_IN_GROUPS, customGroup] : BUILT_IN_GROUPS;

  const toggle = (item: string) =>
    onChange(
      value.includes(item) ? value.filter((s) => s !== item) : [...value, item]
    );

  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <button
          type="button"
          className="flex items-center justify-between w-full h-9 rounded-xl glass-input px-3 text-xs transition-colors hover:bg-accent"
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
          {groups.map((group) => (
            <div key={group.label}>
              <p className="text-[10px] font-semibold text-muted-foreground uppercase tracking-wider mb-1.5">
                {group.label}
              </p>
              <div className="grid grid-cols-2 gap-1">
                {group.items.map((item) => {
                  const active = value.includes(item.value);
                  return (
                    <button
                      key={item.value}
                      type="button"
                      role="checkbox"
                      aria-checked={active}
                      title={item.title}
                      onClick={() => toggle(item.value)}
                      className={`min-w-0 break-words text-left text-[11px] px-2 py-1.5 rounded-md transition-colors ${
                        active
                          ? "bg-primary text-primary-foreground font-medium"
                          : "text-foreground/70 hover:bg-accent"
                      } ${item.missing ? "line-through decoration-1" : ""}`}
                    >
                      {breakableName(item.value)}
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

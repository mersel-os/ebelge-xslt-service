import { useState, useCallback, useMemo, useEffect, useRef, memo } from "react";
import { Virtuoso } from "react-virtuoso";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Label } from "@/components/ui/label";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import {
  Sheet,
  SheetContent,
  SheetDescription,
  SheetHeader,
  SheetTitle,
} from "@/components/ui/sheet";
import { Separator } from "@/components/ui/separator";
import {
  Tooltip,
  TooltipContent,
  TooltipTrigger,
} from "@/components/ui/tooltip";
import {
  ChevronDown,
  Copy,
  Check,
  Search,
  ShieldOff,
  Loader2,
  Plus,
  X,
} from "lucide-react";
import { cn } from "@/lib/utils";
import { toast } from "sonner";
import { useProfiles, useSaveProfile } from "@/api/hooks";
import { useAuth } from "@/hooks/use-auth";
import { useQueryClient } from "@tanstack/react-query";
import type { SchematronError, ProfileInfo } from "@/api/types";

interface ErrorListProps {
  errors: SchematronError[];
  title?: string;
  detectedDocumentType?: string;
}

export function ErrorList({ errors, title = "Hatalar", detectedDocumentType }: ErrorListProps) {
  const [filter, setFilter] = useState("");
  const [expandedIndex, setExpandedIndex] = useState<number | null>(null);

  // Filtre değiştiğinde genişletilmiş öğeyi sıfırla
  const handleFilterChange = useCallback((value: string) => {
    setFilter(value);
    setExpandedIndex(null);
  }, []);
  const [suppressDialog, setSuppressDialog] = useState<{
    open: boolean;
    error: SchematronError | null;
  }>({ open: false, error: null });

  const { authenticated } = useAuth();

  const filtered = useMemo(
    () =>
      errors.filter(
        (e) =>
          (e.ruleId?.toLowerCase() ?? "").includes(filter.toLowerCase()) ||
          (e.test?.toLowerCase() ?? "").includes(filter.toLowerCase()) ||
          e.message.toLowerCase().includes(filter.toLowerCase())
      ),
    [errors, filter]
  );

  const handleOpenSuppressDialog = useCallback((error: SchematronError) => {
    setSuppressDialog({ open: true, error });
  }, []);

  return (
    <div className="space-y-3">
      <div className="flex items-center justify-between gap-3">
        <h3 className="text-sm font-bold shrink-0 flex items-center gap-2">
          {title}
          <Badge
            variant="destructive"
            className="text-[10px] font-mono tabular-nums rounded-lg"
          >
            {errors.length}
          </Badge>
        </h3>
        {errors.length > 3 && (
          <div className="relative max-w-52">
            <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-3.5 w-3.5 text-muted-foreground/40" />
            <Input
              placeholder="Filtrele..."
              value={filter}
              onChange={(e) => handleFilterChange(e.target.value)}
              className="h-9 pl-9 text-xs rounded-lg"
            />
          </div>
        )}
      </div>

      <div className="rounded-xl border bg-card overflow-hidden divide-y" role="alert">
        {filtered.length === 0 ? (
          <div className="py-12 text-center text-sm text-muted-foreground">
            {errors.length === 0 ? "Hata bulunamadı" : "Filtre sonucu boş"}
          </div>
        ) : filtered.length > 50 ? (
          <Virtuoso
            style={{ height: "400px" }}
            totalCount={filtered.length}
            itemContent={(index) => (
              <ErrorItem
                error={filtered[index]}
                index={index}
                isExpanded={expandedIndex === index}
                onToggle={() =>
                  setExpandedIndex(expandedIndex === index ? null : index)
                }
                canSuppress={authenticated}
                onSuppressRequest={handleOpenSuppressDialog}
                variant="virtuoso"
              />
            )}
          />
        ) : (
          filtered.map((error, i) => (
            <ErrorItem
              key={`err-${error.ruleId ?? ""}-${(error.message ?? "").substring(0, 30)}-${i}`}
              error={error}
              index={i}
              isExpanded={expandedIndex === i}
              onToggle={() =>
                setExpandedIndex(expandedIndex === i ? null : i)
              }
              canSuppress={authenticated}
              onSuppressRequest={handleOpenSuppressDialog}
            />
          ))
        )}
      </div>

      {/* ── Suppress Drawer ── */}
      <SuppressToProfileDrawer
        open={suppressDialog.open}
        error={suppressDialog.error}
        detectedDocumentType={detectedDocumentType}
        onOpenChange={(open) =>
          setSuppressDialog((prev) => ({ ...prev, open }))
        }
      />
    </div>
  );
}

/* ── Error Item ── */

const ErrorItem = memo(function ErrorItem({
  error,
  index,
  isExpanded,
  onToggle,
  canSuppress,
  onSuppressRequest,
  variant,
}: {
  error: SchematronError;
  index: number;
  isExpanded: boolean;
  onToggle: () => void;
  canSuppress: boolean;
  onSuppressRequest: (error: SchematronError) => void;
  variant?: "virtuoso";
}) {
  const [copiedFull, setCopiedFull] = useState(false);
  const [copiedRuleId, setCopiedRuleId] = useState(false);
  const fullTimerRef = useRef<ReturnType<typeof setTimeout>>(undefined);
  const ruleTimerRef = useRef<ReturnType<typeof setTimeout>>(undefined);

  useEffect(() => () => {
    clearTimeout(fullTimerRef.current);
    clearTimeout(ruleTimerRef.current);
  }, []);

  const handleCopyFull = async (e: React.MouseEvent) => {
    e.stopPropagation();
    try {
      const text = [
        error.ruleId && `Rule: ${error.ruleId}`,
        error.test && `Test: ${error.test}`,
        `Message: ${error.message}`,
      ]
        .filter(Boolean)
        .join("\n");
      await navigator.clipboard.writeText(text);
      setCopiedFull(true);
      clearTimeout(fullTimerRef.current);
      fullTimerRef.current = setTimeout(() => setCopiedFull(false), 1500);
    } catch (err) {
      console.error("Clipboard write failed:", err);
      toast.error("Kopyalama başarısız. Tarayıcı izinlerini kontrol edin.");
    }
  };

  const handleCopyRuleId = async (e: React.MouseEvent) => {
    e.stopPropagation();
    if (!error.ruleId) return;
    try {
      await navigator.clipboard.writeText(error.ruleId);
      setCopiedRuleId(true);
      toast.success(`"${error.ruleId}" kopyalandı`);
      clearTimeout(ruleTimerRef.current);
      ruleTimerRef.current = setTimeout(() => setCopiedRuleId(false), 1500);
    } catch (err) {
      console.error("Clipboard write failed:", err);
      toast.error("Kopyalama başarısız. Tarayıcı izinlerini kontrol edin.");
    }
  };

  const handleSuppress = (e: React.MouseEvent) => {
    e.stopPropagation();
    onSuppressRequest(error);
  };

  return (
    <div
      className={cn(
        "group transition-colors",
        isExpanded ? "bg-muted/40" : "hover:bg-muted/20",
        variant === "virtuoso" && "border-b border-border"
      )}
    >
      <button
        onClick={onToggle}
        className="flex w-full items-start gap-3 px-5 py-3.5 text-left"
        aria-expanded={isExpanded}
        aria-label={isExpanded ? "Detayları gizle" : "Detayları göster"}
      >
        <span className="mt-0.5 shrink-0 text-[10px] font-mono text-muted-foreground/50 tabular-nums w-5 text-right">
          {index + 1}
        </span>

        {error.ruleId ? (
          <Tooltip>
            <TooltipTrigger asChild>
              <button
                type="button"
                onClick={handleCopyRuleId}
                className="shrink-0 mt-0.5 focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring rounded-md"
                aria-label={`${error.ruleId} kopyala`}
              >
                <Badge
                  variant="outline"
                  className="text-[10px] font-mono px-2 py-0.5 rounded-md bg-primary/5 border-primary/15 text-primary cursor-pointer hover:bg-primary/10 transition-colors gap-1"
                >
                  {copiedRuleId ? (
                    <Check className="h-2.5 w-2.5 text-success" />
                  ) : (
                    <Copy className="h-2.5 w-2.5 opacity-40" />
                  )}
                  {error.ruleId}
                </Badge>
              </button>
            </TooltipTrigger>
            <TooltipContent side="top" className="text-xs">
              Kural adını kopyala
            </TooltipContent>
          </Tooltip>
        ) : (
          <Badge
            variant="outline"
            className="shrink-0 mt-0.5 text-[10px] px-2 py-0.5 rounded-md text-muted-foreground/30"
          >
            —
          </Badge>
        )}

        <span className="flex-1 text-xs leading-relaxed min-w-0">
          <span className={cn(!isExpanded && "line-clamp-2")}>
            {error.message}
          </span>
        </span>

        <ChevronDown
          className={cn(
            "h-4 w-4 shrink-0 mt-0.5 text-muted-foreground/30 transition-transform duration-200",
            isExpanded && "rotate-180"
          )}
        />
      </button>

      {isExpanded && (
        <div className="px-5 pb-4 pl-14 space-y-3">
          {error.test && (
            <div>
              <span className="text-[10px] font-bold uppercase tracking-wider text-muted-foreground">
                Test
              </span>
              <pre className="mt-1.5 rounded-lg bg-muted/60 px-4 py-3 text-[11px] font-mono leading-relaxed overflow-x-auto whitespace-pre-wrap break-all">
                {error.test}
              </pre>
            </div>
          )}
          <div>
            <span className="text-[10px] font-bold uppercase tracking-wider text-muted-foreground">
              Mesaj
            </span>
            <p className="mt-1.5 text-xs leading-relaxed">{error.message}</p>
          </div>
          <div className="flex justify-end gap-1.5">
            {/* Bastırma kuralı olarak ekle (sadece admin) */}
            {canSuppress && (
              <Tooltip>
                <TooltipTrigger asChild>
                  <Button
                    variant="ghost"
                    size="sm"
                    className="h-8 gap-1.5 rounded-lg text-xs text-warning hover:text-warning hover:bg-warning/10"
                    onClick={handleSuppress}
                  >
                    <ShieldOff className="h-3.5 w-3.5" />
                    Bastır
                  </Button>
                </TooltipTrigger>
                <TooltipContent side="left">
                  Profil'e bastırma kuralı olarak ekle
                </TooltipContent>
              </Tooltip>
            )}

            {/* Tümünü kopyala */}
            <Tooltip>
              <TooltipTrigger asChild>
                <Button
                  variant="ghost"
                  size="icon"
                  className="h-8 w-8 rounded-lg"
                  onClick={handleCopyFull}
                  aria-label="Tümünü kopyala"
                >
                  {copiedFull ? (
                    <Check className="h-3.5 w-3.5 text-success" />
                  ) : (
                    <Copy className="h-3.5 w-3.5 text-muted-foreground" />
                  )}
                </Button>
              </TooltipTrigger>
              <TooltipContent side="left">Tümünü kopyala</TooltipContent>
            </Tooltip>
          </div>
        </div>
      )}
    </div>
  );
});

/* ── Match-type helpers ── */

const MATCH_OPTIONS = [
  { value: "ruleId", label: "ruleId", hint: "Rule/pattern ID (regex)", field: "ruleId" as const },
  { value: "ruleIdEquals", label: "ruleIdEquals", hint: "Rule/pattern ID (tam eşleşme)", field: "ruleId" as const },
  { value: "testEquals", label: "testEquals", hint: "XPath test (tam eşleşme)", field: "test" as const },
  { value: "test", label: "test", hint: "XPath test (regex)", field: "test" as const },
  { value: "text", label: "text", hint: "Hata mesajı (regex)", field: "message" as const },
] as const;

type MatchMode = (typeof MATCH_OPTIONS)[number]["value"];

const EQUALS_MODES = new Set<string>(["testEquals", "ruleIdEquals"]);

function isEdefter(docType?: string): boolean {
  return !!docType && (docType.startsWith("EDEFTER") || docType.startsWith("ENVANTER"));
}

function inferBestMatch(
  error: SchematronError | null,
  detectedDocumentType?: string,
): { match: MatchMode; pattern: string } {
  if (!error) return { match: "text", pattern: "" };

  // e-Defter: ruleId'ler pattern seviyesinde olduğu için çok geniş,
  // test ifadesi ile tam eşleşme çok daha isabetli
  if (isEdefter(detectedDocumentType) && error.test) {
    return { match: "testEquals", pattern: error.test };
  }

  if (error.ruleId) return { match: "ruleId", pattern: error.ruleId };
  if (error.test) return { match: "testEquals", pattern: error.test };
  return { match: "text", pattern: error.message };
}

function patternForMatch(match: MatchMode, error: SchematronError | null): string {
  if (!error) return "";
  const opt = MATCH_OPTIONS.find((o) => o.value === match);
  if (!opt) return "";
  const val = error[opt.field];
  return val ?? "";
}

/* ── Suppress to Profile Drawer ── */

function SuppressToProfileDrawer({
  open,
  error,
  detectedDocumentType,
  onOpenChange,
}: {
  open: boolean;
  error: SchematronError | null;
  detectedDocumentType?: string;
  onOpenChange: (open: boolean) => void;
}) {
  const { data: profilesData } = useProfiles({ enabled: open });
  const saveMutation = useSaveProfile();
  const queryClient = useQueryClient();

  const [mode, setMode] = useState<"existing" | "new">("existing");
  const [selectedProfile, setSelectedProfile] = useState("");
  const [newProfileName, setNewProfileName] = useState("");
  const [newProfileDesc, setNewProfileDesc] = useState("");
  const [matchType, setMatchType] = useState<MatchMode>("ruleId");
  const [pattern, setPattern] = useState("");
  const [ruleDescription, setRuleDescription] = useState("");

  // Drawer açıldığında akıllı varsayılanları belirle
  useEffect(() => {
    if (open && error) {
      const best = inferBestMatch(error, detectedDocumentType);
      setMatchType(best.match);
      setPattern(best.pattern);
      setRuleDescription(
        `Otomatik eklendi — ${error.message?.substring(0, 100) ?? ""}`
      );
      setMode("existing");
      setSelectedProfile("");
      setNewProfileName("");
      setNewProfileDesc("");
    }
  }, [open, error, detectedDocumentType]);

  // Match tipi değiştiğinde pattern'ı güncelle
  const handleMatchChange = useCallback(
    (newMatch: MatchMode) => {
      setMatchType(newMatch);
      setPattern(patternForMatch(newMatch, error));
    },
    [error]
  );

  const profileNames = profilesData
    ? Object.keys(profilesData.profiles)
    : [];

  const canSave =
    pattern.trim().length > 0 &&
    ((mode === "existing" && selectedProfile) ||
      (mode === "new" && newProfileName.trim()));

  const handleSave = () => {
    if (!pattern.trim()) return;

    const isNew = mode === "new";
    const profileName = isNew ? newProfileName.trim() : selectedProfile;

    if (!profileName) {
      toast.error(isNew ? "Profil adı girin" : "Bir profil seçin");
      return;
    }

    let existingProfile: ProfileInfo | undefined;
    if (!isNew && profilesData?.profiles) {
      existingProfile = profilesData.profiles[profileName];
    }

    const existingSuppressions = existingProfile?.suppressions ?? [];

    // Duplicate kontrolü (aynı match + pattern)
    const alreadyExists = existingSuppressions.some(
      (s) => s.match === matchType && s.pattern === pattern.trim()
    );
    if (alreadyExists) {
      toast.info("Bu kural zaten profilde mevcut");
      onOpenChange(false);
      return;
    }

    const newSuppression = {
      match: matchType,
      pattern: pattern.trim(),
      description: ruleDescription.trim() || undefined,
    };

    const existingXsdOverrides = existingProfile?.xsdOverrides
      ? Object.fromEntries(
          Object.entries(existingProfile.xsdOverrides).map(([key, rules]) => [
            key,
            rules.map((r) => ({
              element: r.element,
              minOccurs: r.minOccurs,
              maxOccurs: r.maxOccurs,
            })),
          ])
        )
      : {};

    saveMutation.mutate(
      {
        name: profileName,
        description: isNew
          ? newProfileDesc || `${profileName} profili`
          : existingProfile?.description ?? "",
        extendsProfile: existingProfile?.extends ?? undefined,
        suppressions: [
          ...existingSuppressions.map((s) => ({
            match: s.match,
            pattern: s.pattern,
            scope: s.scope,
            description: s.description,
          })),
          newSuppression,
        ],
        xsdOverrides: existingXsdOverrides,
      },
      {
        onSuccess: () => {
          const label = pattern.length > 50
            ? pattern.substring(0, 47) + "..."
            : pattern;
          toast.success(
            `"${label}" → "${profileName}" profiline eklendi`,
            {
              description: isNew
                ? "Yeni profil oluşturuldu"
                : "Mevcut profil güncellendi",
            }
          );
          queryClient.invalidateQueries({ queryKey: ["profiles"] });
          onOpenChange(false);
        },
        onError: (err) => {
          toast.error("Suppression eklenemedi", {
            description:
              err instanceof Error ? err.message : "Bilinmeyen hata",
          });
        },
      }
    );
  };

  return (
    <Sheet open={open} onOpenChange={onOpenChange}>
      <SheetContent
        side="right"
        showCloseButton={false}
        className="sm:max-w-lg w-full p-0 flex flex-col"
      >
        {/* ── Header ── */}
        <SheetHeader className="px-6 pt-6 pb-4 border-b shrink-0">
          <div className="flex items-center justify-between">
            <div>
              <SheetTitle className="flex items-center gap-2 text-base">
                <ShieldOff className="h-4.5 w-4.5 text-warning" />
                Bastırma Kuralı Ekle
              </SheetTitle>
              <SheetDescription className="mt-0.5">
                Bu hatayı bir doğrulama profiline bastırma kuralı olarak ekleyin.
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

        {/* ── Body ── */}
        <div className="flex-1 overflow-y-auto px-6 py-5 space-y-5">
          {/* ── Error Preview ── */}
          <div className="rounded-lg border bg-muted/30 p-3.5 space-y-2.5">
            <div className="flex items-center gap-2 flex-wrap">
              <span className="text-[10px] font-bold uppercase tracking-wider text-muted-foreground">
                Hata
              </span>
              {error?.ruleId && (
                <Badge
                  variant="outline"
                  className="text-[10px] font-mono px-2 py-0.5 rounded-md bg-primary/5 border-primary/15 text-primary"
                >
                  {error.ruleId}
                </Badge>
              )}
            </div>
            {error?.message && (
              <p className="text-[11px] text-muted-foreground leading-relaxed line-clamp-3">
                {error.message}
              </p>
            )}
            {error?.test && (
              <div>
                <span className="text-[9px] font-bold uppercase tracking-wider text-muted-foreground/60">
                  Test
                </span>
                <pre className="mt-1 rounded-md bg-muted/60 px-3 py-2 text-[10px] font-mono leading-relaxed overflow-x-auto whitespace-pre-wrap break-all max-h-20">
                  {error.test}
                </pre>
              </div>
            )}
          </div>

          <Separator />

          {/* ── Match Type & Pattern ── */}
          <section className="space-y-4">
            <div>
              <h4 className="text-xs font-semibold">Kural Yapılandırması</h4>
              <p className="text-[10px] text-muted-foreground mt-0.5">
                Hatanın nasıl eşleştirileceğini seçin.
              </p>
            </div>

            <div className="space-y-1.5">
              <Label className="text-[11px] text-muted-foreground font-medium">
                Eşleştirme Tipi
              </Label>
              <Select value={matchType} onValueChange={(v) => handleMatchChange(v as MatchMode)}>
                <SelectTrigger className="h-9 text-xs">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {MATCH_OPTIONS.map((opt) => {
                    const hasValue = (() => {
                      if (opt.field === "ruleId") return !!error?.ruleId;
                      if (opt.field === "test") return !!error?.test;
                      return true;
                    })();
                    return (
                      <SelectItem
                        key={opt.value}
                        value={opt.value}
                        disabled={!hasValue}
                      >
                        <span className="font-mono text-xs">{opt.label}</span>
                        <span className="ml-1.5 text-muted-foreground text-[10px]">
                          {opt.hint}
                        </span>
                      </SelectItem>
                    );
                  })}
                </SelectContent>
              </Select>
            </div>

            <div className="space-y-1.5">
              <Label className="text-[11px] text-muted-foreground font-medium">
                {EQUALS_MODES.has(matchType)
                  ? "Pattern (tam eşleşme)"
                  : "Pattern (regex)"}
              </Label>
              <Textarea
                value={pattern}
                onChange={(e) => setPattern(e.target.value)}
                placeholder={
                  EQUALS_MODES.has(matchType)
                    ? "Tam eşleşme değeri..."
                    : "Regex pattern..."
                }
                className="text-xs font-mono min-h-[60px] resize-y"
                rows={2}
              />
              {EQUALS_MODES.has(matchType) && (
                <p className="text-[10px] text-muted-foreground/60">
                  Regex escape gerekmez, birebir eşleşme yapılır.
                </p>
              )}
            </div>

            <div className="space-y-1.5">
              <Label className="text-[11px] text-muted-foreground font-medium">
                Açıklama
              </Label>
              <Textarea
                value={ruleDescription}
                onChange={(e) => setRuleDescription(e.target.value)}
                placeholder="Bu kuralın amacı (opsiyonel)"
                className="text-xs min-h-[52px] resize-y"
                rows={2}
              />
            </div>
          </section>

          <Separator />

          {/* ── Profile Selection ── */}
          <section className="space-y-4">
            <div>
              <h4 className="text-xs font-semibold">Profil Seçimi</h4>
              <p className="text-[10px] text-muted-foreground mt-0.5">
                Kuralın ekleneceği profili belirleyin.
              </p>
            </div>

            <div className="flex gap-2">
              <Button
                variant={mode === "existing" ? "default" : "outline"}
                size="sm"
                className="flex-1 h-9 rounded-lg text-xs"
                onClick={() => setMode("existing")}
              >
                Mevcut Profil
              </Button>
              <Button
                variant={mode === "new" ? "default" : "outline"}
                size="sm"
                className="flex-1 h-9 rounded-lg text-xs gap-1.5"
                onClick={() => setMode("new")}
              >
                <Plus className="h-3 w-3" />
                Yeni Profil
              </Button>
            </div>

            {mode === "existing" && (
              <div className="space-y-1.5">
                <Label className="text-[11px] text-muted-foreground font-medium">
                  Profil
                </Label>
                <Select
                  value={selectedProfile}
                  onValueChange={setSelectedProfile}
                >
                  <SelectTrigger className="h-9 text-xs rounded-lg">
                    <SelectValue placeholder="Profil seçin..." />
                  </SelectTrigger>
                  <SelectContent>
                    {profileNames.length === 0 ? (
                      <SelectItem value="__empty" disabled>
                        Profil bulunamadı
                      </SelectItem>
                    ) : (
                      profileNames.map((p) => (
                        <SelectItem key={p} value={p}>
                          <div className="flex items-center gap-2">
                            <span>{p}</span>
                            <span className="text-[10px] text-muted-foreground">
                              (
                              {profilesData?.profiles[p]?.suppressionCount ?? 0}{" "}
                              kural)
                            </span>
                          </div>
                        </SelectItem>
                      ))
                    )}
                  </SelectContent>
                </Select>
              </div>
            )}

            {mode === "new" && (
              <div className="grid gap-3 sm:grid-cols-2">
                <div className="space-y-1.5">
                  <Label className="text-[11px] text-muted-foreground font-medium">
                    Profil Adı
                  </Label>
                  <Input
                    value={newProfileName}
                    onChange={(e) => setNewProfileName(e.target.value)}
                    placeholder="örn: custom-unsigned"
                    className="h-9 text-xs rounded-lg"
                  />
                </div>
                <div className="space-y-1.5">
                  <Label className="text-[11px] text-muted-foreground font-medium">
                    Profil Açıklama
                  </Label>
                  <Input
                    value={newProfileDesc}
                    onChange={(e) => setNewProfileDesc(e.target.value)}
                    placeholder="Opsiyonel"
                    className="h-9 text-xs rounded-lg"
                  />
                </div>
              </div>
            )}
          </section>
        </div>

        {/* ── Footer ── */}
        <div className="shrink-0 border-t bg-muted/30 px-6 py-4 flex items-center justify-end gap-3">
          <Button
            variant="outline"
            size="sm"
            className="rounded-lg"
            onClick={() => onOpenChange(false)}
          >
            İptal
          </Button>
          <Button
            size="sm"
            className="rounded-lg gap-1.5"
            onClick={handleSave}
            disabled={saveMutation.isPending || !canSave}
          >
            {saveMutation.isPending ? (
              <Loader2 className="h-3.5 w-3.5 animate-spin" />
            ) : (
              <ShieldOff className="h-3.5 w-3.5" />
            )}
            Ekle
          </Button>
        </div>
      </SheetContent>
    </Sheet>
  );
}

import { Badge } from "@/components/ui/badge";
import {
  Accordion,
  AccordionContent,
  AccordionItem,
  AccordionTrigger,
} from "@/components/ui/accordion";
import {
  Tooltip,
  TooltipContent,
  TooltipProvider,
  TooltipTrigger,
} from "@/components/ui/tooltip";
import { CheckCircle2, XCircle, AlertTriangle, FileSearch } from "lucide-react";
import { ErrorList } from "./error-list";
import type { ValidationResponse } from "@/api/types";

interface ValidationResultProps {
  result: ValidationResponse;
}

export function ValidationResult({ result }: ValidationResultProps) {
  const hasXsdErrors = result.schemaValidationErrors.length > 0;
  const hasSchematronErrors = result.schematronValidationErrors.length > 0;

  return (
    <div className="space-y-6">
      {/* ── Detection Info ── */}
      {result.detectedDocumentType && (
        <div className="rounded-xl border bg-card p-5">
          <div className="flex items-center gap-3 mb-4">
            <div className="flex h-9 w-9 items-center justify-center rounded-lg bg-primary/10 text-primary">
              <FileSearch className="h-4 w-4" />
            </div>
            <div>
              <h3 className="text-sm font-bold">Tespit Bilgisi</h3>
              <p className="text-xs text-muted-foreground">
                Belge türü otomatik tespit edildi
              </p>
            </div>
          </div>
          <div className="grid gap-3 sm:grid-cols-3">
            <DetectionField
              label="Belge Türü"
              value={result.detectedDocumentType}
              isBadge
            />
            <TooltipProvider>
              <DetectionField
                label="XSD Şema"
                value={result.appliedXsd}
                tooltip={result.appliedXsdPath}
              />
              <DetectionField
                label="Schematron"
                value={result.appliedSchematron}
                tooltip={result.appliedSchematronPath}
              />
            </TooltipProvider>
          </div>
        </div>
      )}

      {/* ── Status Cards ── */}
      <div className="grid gap-4 grid-cols-2">
        <StatusCard
          title="XSD Schema"
          isValid={result.validSchema}
          errorCount={result.schemaValidationErrors.length}
        />
        <StatusCard
          title="Schematron"
          isValid={result.validSchematron}
          errorCount={result.schematronValidationErrors.length}
        />
      </div>

      {/* ── XSD Errors ── */}
      {hasXsdErrors && (
        <div className="rounded-xl border overflow-hidden" role="alert">
          <div className="flex items-center justify-between px-6 py-4 bg-destructive/5 border-b border-destructive/10">
            <h3 className="text-sm font-bold text-destructive">
              Şema Doğrulama Hataları
            </h3>
            <Badge
              variant="destructive"
              className="text-[10px] font-mono tabular-nums rounded-lg"
            >
              {result.schemaValidationErrors.length}
            </Badge>
          </div>
          <div className="divide-y bg-card">
            {result.schemaValidationErrors.map((err, i) => (
              <div
                key={`xsd-${i}-${err.slice(0, 40)}`}
                className="px-6 py-3.5 text-xs font-mono text-destructive/80 leading-relaxed break-all"
              >
                {err}
              </div>
            ))}
          </div>
        </div>
      )}

      {/* ── Schematron Errors ── */}
      {hasSchematronErrors && (
        <ErrorList
          errors={result.schematronValidationErrors}
          title="Schematron Doğrulama Hataları"
          detectedDocumentType={result.detectedDocumentType}
        />
      )}

      {/* ── Suppression Info ── */}
      {result.suppressionInfo && (
        <Accordion type="single" collapsible>
          <AccordionItem
            value="suppression"
            className="border rounded-xl overflow-hidden"
          >
            <AccordionTrigger className="text-sm px-6 py-4 hover:no-underline hover:bg-muted/30">
              <div className="flex items-center gap-3">
                <div className="flex h-9 w-9 items-center justify-center rounded-lg bg-warning/15 text-warning">
                  <AlertTriangle className="h-4 w-4" />
                </div>
                <div className="text-left">
                  <span className="font-bold block">Bastırma Bilgisi</span>
                  <span className="text-xs text-muted-foreground font-normal">
                    {result.suppressionInfo.suppressedCount} hata bastırılan
                    &middot; Profil: {result.suppressionInfo.profile}
                  </span>
                </div>
              </div>
            </AccordionTrigger>
            <AccordionContent>
              <div className="px-6 pb-4 space-y-3">
                <div className="flex gap-5 text-xs">
                  <span>
                    <span className="text-muted-foreground">Ham hata:</span>{" "}
                    <span className="font-bold">
                      {result.suppressionInfo.totalRawErrors}
                    </span>
                  </span>
                </div>
                {result.suppressionInfo.suppressedErrors.length > 0 && (
                  <ErrorList
                    errors={result.suppressionInfo.suppressedErrors}
                    title="Bastırılan Hatalar"
                    detectedDocumentType={result.detectedDocumentType}
                  />
                )}
              </div>
            </AccordionContent>
          </AccordionItem>
        </Accordion>
      )}
    </div>
  );
}

/* ── Detection Field ── */

function DetectionField({
  label,
  value,
  tooltip,
  isBadge,
}: {
  label: string;
  value: string;
  tooltip?: string;
  isBadge?: boolean;
}) {
  const content = isBadge ? (
    <Badge variant="secondary" className="text-xs font-mono">
      {value}
    </Badge>
  ) : (
    <span className="text-xs font-mono text-foreground/80">{value}</span>
  );

  return (
    <div className="space-y-1">
      <p className="text-[10px] font-semibold uppercase tracking-wider text-muted-foreground">
        {label}
      </p>
      {tooltip ? (
        <Tooltip>
          <TooltipTrigger asChild>
            <span className="cursor-help border-b border-dashed border-muted-foreground/30">
              {content}
            </span>
          </TooltipTrigger>
          <TooltipContent side="bottom" className="max-w-sm">
            <p className="text-xs font-mono break-all">{tooltip}</p>
          </TooltipContent>
        </Tooltip>
      ) : (
        content
      )}
    </div>
  );
}

/* ── Status Card ── */

function StatusCard({
  title,
  isValid,
  errorCount,
}: {
  title: string;
  isValid: boolean;
  errorCount: number;
}) {
  return (
    <div
      className={`relative rounded-xl p-6 overflow-hidden transition-all ${
        isValid
          ? "bg-success/5 border border-success/15"
          : "bg-destructive/5 border border-destructive/15"
      }`}
    >
      <div className="flex items-start justify-between">
        <div>
          <p className="text-xs font-semibold uppercase tracking-wider text-muted-foreground">
            {title}
          </p>
          <p className="mt-3">
            {isValid ? (
              <span className="text-3xl font-black text-success">Geçerli</span>
            ) : (
              <span className="text-3xl font-black text-destructive">
                {errorCount}
                <span className="text-base font-semibold ml-1.5">hata</span>
              </span>
            )}
          </p>
        </div>
        <div
          className={`flex h-12 w-12 items-center justify-center rounded-xl ${
            isValid ? "bg-success/10" : "bg-destructive/10"
          }`}
        >
          {isValid ? (
            <CheckCircle2 className="h-6 w-6 text-success" />
          ) : (
            <XCircle className="h-6 w-6 text-destructive" />
          )}
        </div>
      </div>
    </div>
  );
}

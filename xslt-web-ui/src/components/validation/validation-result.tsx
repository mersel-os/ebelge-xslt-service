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
    <div className="space-y-5">
      {/* Detection */}
      {result.detectedDocumentType && (
        <div className="rounded-xl border border-border overflow-hidden">
          <div className="flex items-center gap-3 border-b border-border bg-muted/30 px-5 py-3">
            <FileSearch className="h-4 w-4 text-muted-foreground shrink-0" />
            <span className="text-xs font-semibold text-foreground">Tespit Bilgisi</span>
          </div>
          <div className="flex flex-col sm:flex-row sm:divide-x divide-border">
            <TooltipProvider>
              <DetectionField label="Belge Türü" value={result.detectedDocumentType} isBadge />
              <DetectionField label="XSD Şema" value={result.appliedXsd} tooltip={result.appliedXsdPath} />
              <DetectionField label="Schematron" value={result.appliedSchematron} tooltip={result.appliedSchematronPath} />
            </TooltipProvider>
          </div>
        </div>
      )}

      {/* Status */}
      <div className="grid gap-4 md:grid-cols-2">
        <StatusCard title="XSD Schema" isValid={result.validSchema} errorCount={result.schemaValidationErrors.length} />
        <StatusCard title="Schematron" isValid={result.validSchematron} errorCount={result.schematronValidationErrors.length} />
      </div>

      {/* XSD errors */}
      {hasXsdErrors && (
        <div className="overflow-hidden rounded-xl border border-border" role="alert">
          <div className="flex items-center justify-between border-b border-red-500/10 bg-red-500/5 px-5 py-3">
            <h3 className="text-sm font-semibold text-red-600 dark:text-red-400">Şema Doğrulama Hataları</h3>
            <Badge variant="destructive" className="font-mono text-[10px] tabular-nums">{result.schemaValidationErrors.length}</Badge>
          </div>
          <div className="divide-y divide-border">
            {result.schemaValidationErrors.map((err, i) => (
              <div key={`xsd-${i}-${err.slice(0, 40)}`} className="break-all px-5 py-3 font-mono text-xs leading-relaxed text-red-700/80 dark:text-red-300/70 transition-colors hover:bg-muted/50">
                {err}
              </div>
            ))}
          </div>
        </div>
      )}

      {hasSchematronErrors && (
        <ErrorList errors={result.schematronValidationErrors} title="Schematron Doğrulama Hataları" detectedDocumentType={result.detectedDocumentType} />
      )}

      {/* Suppression */}
      {result.suppressionInfo && (
        <Accordion type="single" collapsible>
          <AccordionItem value="suppression" className="overflow-hidden rounded-xl border border-border">
            <AccordionTrigger className="px-5 py-3 text-sm hover:no-underline hover:bg-muted/50">
              <div className="flex items-center gap-3">
                <div className="flex h-8 w-8 items-center justify-center rounded-lg bg-amber-500/10 border border-amber-500/20">
                  <AlertTriangle className="h-4 w-4 text-amber-500 dark:text-amber-400" />
                </div>
                <div className="text-left">
                  <span className="block text-sm font-semibold text-foreground">Bastırma Bilgisi</span>
                  <span className="text-xs font-normal text-muted-foreground/80">
                    {result.suppressionInfo.suppressedCount} bastırılan · Profil: {result.suppressionInfo.profile}
                  </span>
                </div>
              </div>
            </AccordionTrigger>
            <AccordionContent>
              <div className="space-y-3 px-5 pb-4">
                <p className="text-xs text-muted-foreground/80">Ham hata: <span className="font-semibold text-muted-foreground">{result.suppressionInfo.totalRawErrors}</span></p>
                {result.suppressionInfo.suppressedErrors.length > 0 && (
                  <ErrorList errors={result.suppressionInfo.suppressedErrors} title="Bastırılan Hatalar" detectedDocumentType={result.detectedDocumentType} />
                )}
              </div>
            </AccordionContent>
          </AccordionItem>
        </Accordion>
      )}
    </div>
  );
}

function DetectionField({ label, value, tooltip, isBadge }: { label: string; value: string; tooltip?: string; isBadge?: boolean }) {
  const content = isBadge
    ? <Badge variant="secondary" className="font-mono text-xs">{value}</Badge>
    : <span className="font-mono text-xs text-muted-foreground truncate">{value}</span>;

  return (
    <div className="flex-1 px-5 py-3.5 space-y-1 min-w-0">
      <p className="text-[10px] font-medium uppercase tracking-wider text-muted-foreground/60">{label}</p>
      {tooltip ? (
        <Tooltip>
          <TooltipTrigger asChild><span className="cursor-help border-b border-dashed border-border">{content}</span></TooltipTrigger>
          <TooltipContent side="bottom" className="max-w-sm"><p className="break-all font-mono text-xs">{tooltip}</p></TooltipContent>
        </Tooltip>
      ) : content}
    </div>
  );
}

function StatusCard({ title, isValid, errorCount }: { title: string; isValid: boolean; errorCount: number }) {
  return (
    <div className={`rounded-xl border p-5 ${isValid ? "border-emerald-500/20 bg-emerald-500/5" : "border-red-500/20 bg-red-500/5"}`}>
      <div className="flex items-start justify-between">
        <div>
          <p className="text-xs font-medium uppercase tracking-wider text-muted-foreground/80">{title}</p>
          <p className="mt-2">
            {isValid
              ? <span className="text-2xl font-bold text-emerald-600 dark:text-emerald-400">Geçerli</span>
              : <span className="text-2xl font-bold text-red-600 dark:text-red-400">{errorCount}<span className="ml-1 text-sm font-medium">hata</span></span>}
          </p>
        </div>
        <div className={`flex h-10 w-10 items-center justify-center rounded-xl ${isValid ? "bg-emerald-500/10 border border-emerald-500/20" : "bg-red-500/10 border border-red-500/20"}`}>
          {isValid ? <CheckCircle2 className="h-5 w-5 text-emerald-600 dark:text-emerald-400" /> : <XCircle className="h-5 w-5 text-red-600 dark:text-red-400" />}
        </div>
      </div>
    </div>
  );
}

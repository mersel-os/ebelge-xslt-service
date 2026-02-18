import { useMemo, useState } from "react";
import { cn } from "@/lib/utils";
import { ChevronRight, Columns2, Rows3 } from "lucide-react";

type ViewMode = "unified" | "side-by-side";

interface FileDiffViewerProps {
  unifiedDiff: string;
  oldContent?: string;
  newContent?: string;
  isBinary?: boolean;
  maxCollapsedContext?: number;
}

interface DiffLine {
  type: "added" | "removed" | "context" | "header";
  content: string;
  oldLineNo?: number;
  newLineNo?: number;
}

interface SideBySidePair {
  type: "pair" | "header" | "collapse";
  left?: DiffLine;
  right?: DiffLine;
  header?: string;
  collapseIndex?: number;
  collapseCount?: number;
}

interface InlineSegment {
  text: string;
  highlighted: boolean;
}

export function FileDiffViewer({
  unifiedDiff,
  isBinary,
  maxCollapsedContext = 8,
}: FileDiffViewerProps) {
  const [viewMode, setViewMode] = useState<ViewMode>("side-by-side");
  const [expandedSections, setExpandedSections] = useState<Set<number>>(
    new Set()
  );

  const { lines, collapsibleRanges } = useMemo(
    () => parseDiff(unifiedDiff || "", maxCollapsedContext),
    [unifiedDiff, maxCollapsedContext]
  );

  if (isBinary) {
    return (
      <div className="rounded-lg border bg-muted/30 p-6 text-center text-sm text-muted-foreground">
        Binary dosya — diff gösterilemiyor
      </div>
    );
  }

  if (!unifiedDiff || unifiedDiff.trim() === "") {
    return (
      <div className="rounded-lg border bg-muted/30 p-6 text-center text-sm text-muted-foreground">
        Değişiklik yok
      </div>
    );
  }

  const toggleSection = (idx: number) => {
    setExpandedSections((prev) => {
      const next = new Set(prev);
      if (next.has(idx)) {
        next.delete(idx);
      } else {
        next.add(idx);
      }
      return next;
    });
  };

  return (
    <div className="rounded-lg border overflow-hidden text-xs">
      {/* View mode toggle */}
      <div className="flex items-center justify-end gap-1 px-3 py-1.5 bg-muted/30 border-b">
        <button
          onClick={() => setViewMode("unified")}
          className={cn(
            "inline-flex items-center gap-1 px-2 py-0.5 rounded text-[10px] font-medium transition-colors",
            viewMode === "unified"
              ? "bg-foreground text-background"
              : "text-muted-foreground hover:text-foreground hover:bg-muted"
          )}
        >
          <Rows3 className="h-3 w-3" />
          Unified
        </button>
        <button
          onClick={() => setViewMode("side-by-side")}
          className={cn(
            "inline-flex items-center gap-1 px-2 py-0.5 rounded text-[10px] font-medium transition-colors",
            viewMode === "side-by-side"
              ? "bg-foreground text-background"
              : "text-muted-foreground hover:text-foreground hover:bg-muted"
          )}
        >
          <Columns2 className="h-3 w-3" />
          Side by Side
        </button>
      </div>

      {viewMode === "unified" ? (
        <UnifiedView
          lines={lines}
          collapsibleRanges={collapsibleRanges}
          expandedSections={expandedSections}
          toggleSection={toggleSection}
        />
      ) : (
        <SideBySideView
          lines={lines}
          collapsibleRanges={collapsibleRanges}
          expandedSections={expandedSections}
          toggleSection={toggleSection}
        />
      )}
    </div>
  );
}

// ── Inline Diff (character-level highlighting) ───────────────────

function computeInlineDiff(
  oldText: string,
  newText: string
): { oldSegments: InlineSegment[]; newSegments: InlineSegment[] } {
  let prefixLen = 0;
  const minLen = Math.min(oldText.length, newText.length);
  while (prefixLen < minLen && oldText[prefixLen] === newText[prefixLen]) {
    prefixLen++;
  }

  let suffixLen = 0;
  while (
    suffixLen < minLen - prefixLen &&
    oldText[oldText.length - 1 - suffixLen] ===
      newText[newText.length - 1 - suffixLen]
  ) {
    suffixLen++;
  }

  const prefix = oldText.substring(0, prefixLen);
  const oldMiddle = oldText.substring(prefixLen, oldText.length - suffixLen);
  const newMiddle = newText.substring(prefixLen, newText.length - suffixLen);
  const suffix = suffixLen > 0 ? oldText.substring(oldText.length - suffixLen) : "";

  const oldSegments: InlineSegment[] = [];
  const newSegments: InlineSegment[] = [];

  if (prefix) {
    oldSegments.push({ text: prefix, highlighted: false });
    newSegments.push({ text: prefix, highlighted: false });
  }
  if (oldMiddle || newMiddle) {
    if (oldMiddle) oldSegments.push({ text: oldMiddle, highlighted: true });
    if (newMiddle) newSegments.push({ text: newMiddle, highlighted: true });
  }
  if (suffix) {
    oldSegments.push({ text: suffix, highlighted: false });
    newSegments.push({ text: suffix, highlighted: false });
  }

  return { oldSegments, newSegments };
}

function InlineHighlight({
  segments,
  highlightClass,
}: {
  segments: InlineSegment[];
  highlightClass: string;
}) {
  return (
    <>
      {segments.map((seg, i) =>
        seg.highlighted ? (
          <span key={i} className={highlightClass}>
            {seg.text}
          </span>
        ) : (
          <span key={i}>{seg.text}</span>
        )
      )}
    </>
  );
}

// ── Unified View ─────────────────────────────────────────────────

function UnifiedView({
  lines,
  collapsibleRanges,
  expandedSections,
  toggleSection,
}: {
  lines: DiffLine[];
  collapsibleRanges: CollapsibleRange[];
  expandedSections: Set<number>;
  toggleSection: (idx: number) => void;
}) {
  const visibleLines: (
    | DiffLine
    | { type: "collapse"; index: number; count: number }
  )[] = [];
  let i = 0;
  while (i < lines.length) {
    const range = collapsibleRanges.find((r) => r.start === i);
    if (range && !expandedSections.has(range.start)) {
      visibleLines.push({
        type: "collapse",
        index: range.start,
        count: range.end - range.start,
      });
      i = range.end;
    } else {
      visibleLines.push(lines[i]);
      i++;
    }
  }

  // Pre-compute inline diffs for consecutive removed+added pairs in the original lines
  const inlineDiffs = useMemo(() => {
    const map = new Map<number, { oldSegments: InlineSegment[]; newSegments: InlineSegment[] }>();
    for (let j = 0; j < lines.length - 1; j++) {
      if (lines[j].type === "removed" && lines[j + 1].type === "added") {
        map.set(j, computeInlineDiff(lines[j].content, lines[j + 1].content));
      }
    }
    return map;
  }, [lines]);

  // Map from visible lines to original lines index for inline diff lookup
  const lineToOrigIdx = useMemo(() => {
    const map = new Map<DiffLine, number>();
    lines.forEach((l, idx) => map.set(l, idx));
    return map;
  }, [lines]);

  return (
    <div className="overflow-x-auto">
      <table className="w-full border-collapse font-mono">
        <tbody>
          {visibleLines.map((line, idx) => {
            if ("index" in line && line.type === "collapse") {
              return (
                <tr
                  key={`c-${line.index}`}
                  className="bg-muted/40 cursor-pointer hover:bg-muted/60 transition-colors"
                  onClick={() => toggleSection(line.index)}
                >
                  <td
                    colSpan={3}
                    className="px-3 py-1.5 text-center text-muted-foreground select-none"
                  >
                    <span className="inline-flex items-center gap-1">
                      <ChevronRight className="h-3 w-3" />
                      {line.count} satır gizlendi
                    </span>
                  </td>
                </tr>
              );
            }

            const diffLine = line as DiffLine;
            if (diffLine.type === "header") {
              return (
                <tr key={`h-${idx}`} className="bg-blue-50 dark:bg-blue-950/30">
                  <td
                    colSpan={3}
                    className="px-3 py-1 text-blue-700 dark:text-blue-400 font-semibold"
                  >
                    {diffLine.content}
                  </td>
                </tr>
              );
            }

            const origIdx = lineToOrigIdx.get(diffLine);
            let inlineContent: React.ReactNode = diffLine.content;

            if (origIdx !== undefined) {
              if (diffLine.type === "removed") {
                const diff = inlineDiffs.get(origIdx);
                if (diff) {
                  inlineContent = (
                    <InlineHighlight
                      segments={diff.oldSegments}
                      highlightClass="bg-red-300/60 dark:bg-red-700/50 rounded-sm px-[1px]"
                    />
                  );
                }
              } else if (diffLine.type === "added") {
                const diff = inlineDiffs.get(origIdx - 1);
                if (diff) {
                  inlineContent = (
                    <InlineHighlight
                      segments={diff.newSegments}
                      highlightClass="bg-green-300/60 dark:bg-green-700/50 rounded-sm px-[1px]"
                    />
                  );
                }
              }
            }

            return (
              <tr
                key={idx}
                className={cn(
                  "leading-5",
                  diffLine.type === "added" &&
                    "bg-green-100 text-green-900 dark:bg-green-950/50 dark:text-green-200",
                  diffLine.type === "removed" &&
                    "bg-red-100 text-red-900 dark:bg-red-950/50 dark:text-red-200"
                )}
              >
                <td className={cn(
                  "w-10 text-right pr-2 pl-2 select-none border-r tabular-nums",
                  diffLine.type === "added"
                    ? "text-green-600 dark:text-green-500 border-green-200 dark:border-green-900/50"
                    : diffLine.type === "removed"
                      ? "text-red-600 dark:text-red-500 border-red-200 dark:border-red-900/50"
                      : "text-muted-foreground/50 border-border/30"
                )}>
                  {diffLine.oldLineNo ?? ""}
                </td>
                <td className={cn(
                  "w-10 text-right pr-2 pl-2 select-none border-r tabular-nums",
                  diffLine.type === "added"
                    ? "text-green-600 dark:text-green-500 border-green-200 dark:border-green-900/50"
                    : diffLine.type === "removed"
                      ? "text-red-600 dark:text-red-500 border-red-200 dark:border-red-900/50"
                      : "text-muted-foreground/50 border-border/30"
                )}>
                  {diffLine.newLineNo ?? ""}
                </td>
                <td className="px-3 whitespace-pre-wrap break-all">
                  <span
                    className={cn(
                      "inline-block w-4 text-center select-none mr-1 font-bold",
                      diffLine.type === "added" && "text-green-700 dark:text-green-400",
                      diffLine.type === "removed" && "text-red-700 dark:text-red-400"
                    )}
                  >
                    {diffLine.type === "added"
                      ? "+"
                      : diffLine.type === "removed"
                        ? "-"
                        : " "}
                  </span>
                  {inlineContent}
                </td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}

// ── Side-by-Side View ────────────────────────────────────────────

function SideBySideView({
  lines,
  collapsibleRanges,
  expandedSections,
  toggleSection,
}: {
  lines: DiffLine[];
  collapsibleRanges: CollapsibleRange[];
  expandedSections: Set<number>;
  toggleSection: (idx: number) => void;
}) {
  const pairs = useMemo(
    () => buildSideBySidePairs(lines, collapsibleRanges, expandedSections),
    [lines, collapsibleRanges, expandedSections]
  );

  return (
    <div className="overflow-x-auto">
      <table className="w-full border-collapse font-mono table-fixed">
        <colgroup>
          <col className="w-10" />
          <col className="w-[calc(50%-20px)]" />
          <col className="w-10" />
          <col className="w-[calc(50%-20px)]" />
        </colgroup>
        <tbody>
          {pairs.map((pair, idx) => {
            if (pair.type === "collapse") {
              return (
                <tr
                  key={`c-${pair.collapseIndex}`}
                  className="bg-muted/40 cursor-pointer hover:bg-muted/60 transition-colors"
                  onClick={() => toggleSection(pair.collapseIndex!)}
                >
                  <td
                    colSpan={4}
                    className="px-3 py-1.5 text-center text-muted-foreground select-none"
                  >
                    <span className="inline-flex items-center gap-1">
                      <ChevronRight className="h-3 w-3" />
                      {pair.collapseCount} satır gizlendi
                    </span>
                  </td>
                </tr>
              );
            }

            if (pair.type === "header") {
              return (
                <tr key={`h-${idx}`} className="bg-blue-50 dark:bg-blue-950/30">
                  <td
                    colSpan={4}
                    className="px-3 py-1 text-blue-700 dark:text-blue-400 font-semibold"
                  >
                    {pair.header}
                  </td>
                </tr>
              );
            }

            const left = pair.left;
            const right = pair.right;

            // Compute inline diff when we have a removed+added pair
            const hasInlineDiff =
              left?.type === "removed" && right?.type === "added";
            const inlineDiff = hasInlineDiff
              ? computeInlineDiff(left!.content, right!.content)
              : null;

            return (
              <tr key={idx} className="leading-5">
                {/* Left (old) */}
                <td
                  className={cn(
                    "w-10 text-right pr-2 pl-2 select-none border-r tabular-nums",
                    left?.type === "removed"
                      ? "text-red-600 dark:text-red-500 bg-red-100 dark:bg-red-950/50 border-red-200 dark:border-red-900/50"
                      : "text-muted-foreground/50 border-border/30"
                  )}
                >
                  {left?.oldLineNo ?? ""}
                </td>
                <td
                  className={cn(
                    "px-3 whitespace-pre-wrap break-all border-r overflow-hidden",
                    left?.type === "removed"
                      ? "bg-red-100 text-red-900 dark:bg-red-950/50 dark:text-red-200 border-red-200 dark:border-red-900/50"
                      : "border-border/20",
                    !left && "bg-muted/10"
                  )}
                >
                  {left && (
                    <>
                      {left.type === "removed" && (
                        <span className="inline-block w-3 text-center select-none mr-1 font-bold text-red-700 dark:text-red-400">
                          -
                        </span>
                      )}
                      {inlineDiff ? (
                        <InlineHighlight
                          segments={inlineDiff.oldSegments}
                          highlightClass="bg-red-300/60 dark:bg-red-700/50 rounded-sm px-[1px]"
                        />
                      ) : (
                        left.content
                      )}
                    </>
                  )}
                </td>

                {/* Right (new) */}
                <td
                  className={cn(
                    "w-10 text-right pr-2 pl-2 select-none border-r tabular-nums",
                    right?.type === "added"
                      ? "text-green-600 dark:text-green-500 bg-green-100 dark:bg-green-950/50 border-green-200 dark:border-green-900/50"
                      : "text-muted-foreground/50 border-border/30"
                  )}
                >
                  {right?.newLineNo ?? ""}
                </td>
                <td
                  className={cn(
                    "px-3 whitespace-pre-wrap break-all overflow-hidden",
                    right?.type === "added"
                      ? "bg-green-100 text-green-900 dark:bg-green-950/50 dark:text-green-200"
                      : "",
                    !right && "bg-muted/10"
                  )}
                >
                  {right && (
                    <>
                      {right.type === "added" && (
                        <span className="inline-block w-3 text-center select-none mr-1 font-bold text-green-700 dark:text-green-400">
                          +
                        </span>
                      )}
                      {inlineDiff ? (
                        <InlineHighlight
                          segments={inlineDiff.newSegments}
                          highlightClass="bg-green-300/60 dark:bg-green-700/50 rounded-sm px-[1px]"
                        />
                      ) : (
                        right.content
                      )}
                    </>
                  )}
                </td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}

function buildSideBySidePairs(
  lines: DiffLine[],
  collapsibleRanges: CollapsibleRange[],
  expandedSections: Set<number>
): SideBySidePair[] {
  const pairs: SideBySidePair[] = [];
  let i = 0;

  while (i < lines.length) {
    const range = collapsibleRanges.find((r) => r.start === i);
    if (range && !expandedSections.has(range.start)) {
      pairs.push({
        type: "collapse",
        collapseIndex: range.start,
        collapseCount: range.end - range.start,
      });
      i = range.end;
      continue;
    }

    const line = lines[i];

    if (line.type === "header") {
      pairs.push({ type: "header", header: line.content });
      i++;
      continue;
    }

    if (line.type === "context") {
      pairs.push({ type: "pair", left: line, right: line });
      i++;
      continue;
    }

    // Collect consecutive removed/added blocks and pair them
    const removedBlock: DiffLine[] = [];
    const addedBlock: DiffLine[] = [];

    while (i < lines.length && lines[i].type === "removed") {
      removedBlock.push(lines[i]);
      i++;
    }
    while (i < lines.length && lines[i].type === "added") {
      addedBlock.push(lines[i]);
      i++;
    }

    const maxLen = Math.max(removedBlock.length, addedBlock.length);
    for (let j = 0; j < maxLen; j++) {
      pairs.push({
        type: "pair",
        left: j < removedBlock.length ? removedBlock[j] : undefined,
        right: j < addedBlock.length ? addedBlock[j] : undefined,
      });
    }
  }

  return pairs;
}

// ── Diff Parser ──────────────────────────────────────────────────

interface CollapsibleRange {
  start: number;
  end: number;
}

function parseDiff(
  unifiedDiff: string,
  maxCollapsedContext: number
): { lines: DiffLine[]; collapsibleRanges: CollapsibleRange[] } {
  const rawLines = unifiedDiff.split("\n");
  const lines: DiffLine[] = [];

  let oldLine = 0;
  let newLine = 0;

  for (const raw of rawLines) {
    if (raw.startsWith("---") || raw.startsWith("+++")) {
      continue;
    }

    if (raw.startsWith("@@")) {
      const match = raw.match(/@@ -(\d+)(?:,\d+)? \+(\d+)(?:,\d+)? @@(.*)/);
      if (match) {
        oldLine = parseInt(match[1], 10);
        newLine = parseInt(match[2], 10);
        lines.push({
          type: "header",
          content: raw,
        });
      }
      continue;
    }

    if (raw.startsWith("+")) {
      lines.push({
        type: "added",
        content: raw.substring(1),
        newLineNo: newLine++,
      });
    } else if (raw.startsWith("-")) {
      lines.push({
        type: "removed",
        content: raw.substring(1),
        oldLineNo: oldLine++,
      });
    } else if (raw.startsWith(" ") || raw === "") {
      lines.push({
        type: "context",
        content: raw.startsWith(" ") ? raw.substring(1) : raw,
        oldLineNo: oldLine++,
        newLineNo: newLine++,
      });
    }
  }

  // Find collapsible ranges (consecutive context lines > maxCollapsedContext)
  const collapsibleRanges: CollapsibleRange[] = [];
  let contextStart = -1;

  for (let i = 0; i < lines.length; i++) {
    if (lines[i].type === "context") {
      if (contextStart === -1) contextStart = i;
    } else {
      if (contextStart !== -1) {
        const length = i - contextStart;
        if (length > maxCollapsedContext) {
          collapsibleRanges.push({
            start: contextStart + 2,
            end: i - 2,
          });
        }
        contextStart = -1;
      }
    }
  }

  if (contextStart !== -1) {
    const length = lines.length - contextStart;
    if (length > maxCollapsedContext) {
      collapsibleRanges.push({
        start: contextStart + 2,
        end: lines.length - 2,
      });
    }
  }

  return { lines, collapsibleRanges };
}

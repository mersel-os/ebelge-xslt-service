import { useState, memo } from "react";
import {
  ChevronRight,
  FileText,
  Folder,
  FolderOpen,
} from "lucide-react";
import { cn } from "@/lib/utils";
import type { PackageTreeNode } from "@/api/types";

interface FileTreeProps {
  nodes: PackageTreeNode[];
  rootLabel?: string;
  defaultExpanded?: boolean;
}

export function FileTree({ nodes, rootLabel, defaultExpanded = false }: FileTreeProps) {
  if (!nodes || nodes.length === 0) return null;

  return (
    <div className="text-[11px] font-mono">
      {rootLabel && (
        <div className="flex items-center gap-1.5 mb-1.5 text-[10px] text-muted-foreground">
          <FolderOpen className="h-3 w-3 shrink-0" />
          {rootLabel}
        </div>
      )}
      <div className="space-y-px">
        {nodes.map((node) => (
          <TreeNodeItem
            key={node.name}
            node={node}
            depth={0}
            defaultExpanded={defaultExpanded}
          />
        ))}
      </div>
    </div>
  );
}

interface TreeNodeItemProps {
  node: PackageTreeNode;
  depth: number;
  defaultExpanded: boolean;
}

const TreeNodeItem = memo(function TreeNodeItem({
  node,
  depth,
  defaultExpanded,
}: TreeNodeItemProps) {
  const [expanded, setExpanded] = useState(defaultExpanded);

  if (node.type === "file") {
    return (
      <div
        className="flex items-center gap-1.5 py-1 px-2 rounded-md hover:bg-muted/40 transition-colors group"
        style={{ paddingLeft: `${depth * 16 + 8}px` }}
      >
        <FileText className="h-3 w-3 text-muted-foreground/60 shrink-0" />
        <span className="truncate text-foreground/80">{node.name}</span>
        {node.size != null && (
          <span className="ml-auto text-[9px] text-muted-foreground/50 shrink-0 tabular-nums opacity-0 group-hover:opacity-100 transition-opacity">
            {formatSize(node.size)}
          </span>
        )}
      </div>
    );
  }

  // Directory node
  const hasChildren = node.children && node.children.length > 0;

  return (
    <div>
      <button
        type="button"
        onClick={() => setExpanded(!expanded)}
        className={cn(
          "flex items-center gap-1.5 w-full py-1 px-2 rounded-md text-left transition-colors",
          "hover:bg-muted/50",
          expanded && "bg-muted/30"
        )}
        style={{ paddingLeft: `${depth * 16 + 8}px` }}
      >
        <ChevronRight
          className={cn(
            "h-3 w-3 text-muted-foreground/50 shrink-0 transition-transform duration-150",
            expanded && "rotate-90"
          )}
        />
        {expanded ? (
          <FolderOpen className="h-3 w-3 text-foreground/60 shrink-0" />
        ) : (
          <Folder className="h-3 w-3 text-foreground/40 shrink-0" />
        )}
        <span className="truncate font-medium text-foreground/90">
          {node.name}
        </span>
        {node.fileCount != null && node.fileCount > 0 && (
          <span className="ml-auto text-[9px] text-muted-foreground/50 shrink-0 tabular-nums">
            {node.fileCount}
          </span>
        )}
      </button>
      {expanded && hasChildren && (
        <div>
          {node.children!.map((child) => (
            <TreeNodeItem
              key={child.name}
              node={child}
              depth={depth + 1}
              defaultExpanded={defaultExpanded}
            />
          ))}
        </div>
      )}
    </div>
  );
});

function formatSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

// ─── Enums ──────────────────────────────────────────────────────────

/**
 * Schema (XSD) doğrulama tipleri — profil yönetimi (admin) arayüzü için.
 * Doğrulama endpoint'inde belge türü otomatik tespit edilir.
 */
export const SCHEMA_VALIDATION_TYPES = [
  "INVOICE",
  "DESPATCH_ADVICE",
  "RECEIPT_ADVICE",
  "CREDIT_NOTE",
  "APPLICATION_RESPONSE",
  "EARCHIVE",
  "EDEFTER",
] as const;

export type SchemaValidationType = (typeof SCHEMA_VALIDATION_TYPES)[number];

/**
 * Schematron doğrulama tipleri — profil yönetimi (admin) arayüzü için.
 * Doğrulama endpoint'inde belge türü otomatik tespit edilir.
 */
export const SCHEMATRON_VALIDATION_TYPES = [
  "UBLTR_MAIN",
  "EARCHIVE_REPORT",
  "EDEFTER_YEVMIYE",
  "EDEFTER_KEBIR",
  "EDEFTER_BERAT",
  "EDEFTER_RAPOR",
  "ENVANTER_BERAT",
  "ENVANTER_DEFTER",
] as const;

export type SchematronValidationType =
  (typeof SCHEMATRON_VALIDATION_TYPES)[number];

export const TRANSFORM_TYPES = [
  "INVOICE",
  "ARCHIVE_INVOICE",
  "DESPATCH_ADVICE",
  "RECEIPT_ADVICE",
  "EMM",
  "ECHECK",
] as const;

export type TransformType = (typeof TRANSFORM_TYPES)[number];

export const TRANSFORM_TYPE_LABELS: Record<TransformType, string> = {
  INVOICE: "e-Fatura",
  ARCHIVE_INVOICE: "e-Arşiv Fatura",
  DESPATCH_ADVICE: "e-İrsaliye",
  RECEIPT_ADVICE: "e-İrsaliye Yanıt",
  EMM: "e-Müstahsil Makbuzu",
  ECHECK: "e-Adisyon",
};

// ─── Validation ─────────────────────────────────────────────────────

export interface SchematronError {
  ruleId: string | null;
  test: string | null;
  message: string;
}

export interface SuppressionInfo {
  profile: string;
  totalRawErrors: number;
  suppressedCount: number;
  suppressedErrors: SchematronError[];
}

export interface ValidationResponse {
  detectedDocumentType: string;
  appliedXsd: string;
  appliedSchematron: string;
  appliedXsdPath: string;
  appliedSchematronPath: string;
  validSchema: boolean;
  validSchematron: boolean;
  schemaValidationErrors: string[];
  schematronValidationErrors: SchematronError[];
  suppressionInfo?: SuppressionInfo;
}

export interface XsltServiceResponse<T> {
  errorMessage: string | null;
  result: T;
}

// ─── Transform ──────────────────────────────────────────────────────

export interface TransformMeta {
  defaultUsed: boolean;
  embeddedUsed: boolean;
  customError?: string;
  durationMs: number;
  watermarkApplied: boolean;
  outputSize: number;
}

export interface TransformResult {
  html: string;
  meta: TransformMeta;
}

// ─── Admin ──────────────────────────────────────────────────────────

export interface ReloadComponent {
  name: string;
  status: "OK" | "PARTIAL" | "FAILED" | "SUCCESS";
  count: number;
  durationMs: number;
  errors: string[];
}

export interface ReloadResponse {
  reloadedAt: string;
  durationMs: number;
  components: ReloadComponent[];
}

export interface SuppressionRule {
  match: string;
  pattern: string;
  scope?: string[];
  description?: string;
}

export interface XsdOverrideRule {
  element: string;
  minOccurs?: string;
  maxOccurs?: string;
}

export interface SchematronCustomRule {
  context: string;
  test: string;
  message: string;
  id?: string;
}

export interface ProfileInfo {
  description: string;
  extends?: string;
  suppressionCount: number;
  suppressions: SuppressionRule[];
  xsdOverrides?: Record<string, XsdOverrideRule[]>;
  schematronRules?: Record<string, SchematronCustomRule[]>;
}

export interface ProfilesResponse {
  profileCount: number;
  profiles: Record<string, ProfileInfo>;
}

export interface FileMapping {
  zipPathPattern: string;
  targetDir: string;
}

export interface PackageTreeNode {
  name: string;
  type: "file" | "directory";
  size?: number;
  fileCount?: number;
  children?: PackageTreeNode[];
}

export interface PackageDefinition {
  id: string;
  displayName: string;
  downloadUrl: string;
  description: string;
  fileMapping: FileMapping[];
  fileTrees?: Record<string, PackageTreeNode[]>;
  totalLoadedFileCount: number;
}

export interface PackageSyncResultItem {
  packageId: string;
  displayName: string;
  success: boolean;
  filesExtracted: number;
  extractedFiles: string[];
  durationMs: number;
  error?: string;
}

export interface SyncResponse {
  enabled: boolean;
  syncedAt?: string;
  totalDurationMs?: number;
  successCount?: number;
  totalCount?: number;
  currentAssetSource?: string;
  packages?: PackageSyncResultItem[];
  message?: string;
}

export interface PackagesResponse {
  enabled: boolean;
  currentAssetSource: string;
  packageCount: number;
  initialSyncInProgress: boolean;
  packages: PackageDefinition[];
}

// ─── Global Schematron Rules ─────────────────────────────────────────

export interface SchematronRulesResponse {
  rules: Record<string, SchematronCustomRule[]>;
  totalCount: number;
}

// ─── Asset Versioning ────────────────────────────────────────────────

export interface FilesSummary {
  added: number;
  removed: number;
  modified: number;
  unchanged: number;
}

export interface AssetVersion {
  id: string;
  packageId: string;
  displayName: string;
  timestamp: string;
  status: "PENDING" | "APPLIED" | "REJECTED";
  filesSummary: FilesSummary;
  appliedAt?: string;
  rejectedAt?: string;
  durationMs: number;
}

export interface FileDiffSummary {
  path: string;
  status: "ADDED" | "REMOVED" | "MODIFIED" | "UNCHANGED";
  oldSize: number;
  newSize: number;
}

export interface FileDiffDetail {
  path: string;
  status: "ADDED" | "REMOVED" | "MODIFIED" | "UNCHANGED";
  unifiedDiff?: string;
  oldContent?: string;
  newContent?: string;
  isBinary: boolean;
}

export interface SuppressionWarning {
  ruleId: string;
  profileName: string;
  pattern: string;
  severity: "CRITICAL" | "WARNING" | "INFO";
  message: string;
}

export interface SyncPreview {
  packageId: string;
  version: AssetVersion;
  fileDiffs: FileDiffSummary[];
  warnings: SuppressionWarning[];
  addedCount: number;
  removedCount: number;
  modifiedCount: number;
  unchangedCount: number;
}

export interface SyncPreviewResponse {
  enabled: boolean;
  syncedAt: string;
  packageCount: number;
  previews: SyncPreview[];
}

export interface AssetVersionListResponse {
  versionCount: number;
  versions: AssetVersion[];
}

export interface PendingPreviewsResponse {
  pendingCount: number;
  previews: SyncPreview[];
}

export interface ApproveResponse {
  message: string;
  version: AssetVersion;
}

export interface RejectResponse {
  message: string;
  packageId: string;
}

export interface VersionDiffResponse {
  versionId: string;
  fileCount: number;
  files: FileDiffSummary[];
}

// ─── Default XSLT Templates ─────────────────────────────────────

export interface DefaultXsltTemplate {
  transformType: string;
  label: string;
  fileName: string;
  relativePath: string;
  exists: boolean;
  size: number;
  lastModified?: string;
}

export interface DefaultXsltListResponse {
  count: number;
  templates: DefaultXsltTemplate[];
}

export interface DefaultXsltContentResponse {
  transformType: string;
  path: string;
  exists: boolean;
  content?: string;
  size: number;
}

export interface DefaultXsltSaveResponse {
  message: string;
  transformType: string;
  label: string;
  size: number;
}

// ─── Auto-Generated ─────────────────────────────────────────────────

export interface AutoGeneratedCategory {
  directory: string;
  files: string[];
  count: number;
}

export interface AutoGeneratedResponse {
  schematron: AutoGeneratedCategory;
  schemaOverrides: AutoGeneratedCategory;
  schematronRules: AutoGeneratedCategory;
  totalCount: number;
}

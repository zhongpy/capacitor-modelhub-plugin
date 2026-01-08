// src/definitions.ts

export type ModelItem = {
  key: string;           // assets/models/<key>.zip
  unpackTo: string;      // relative to models root
  checkFiles: string[];  // relative to unpackTo
  password?: string;     // AES zip password
  sha256?: string;       // NOTE: Java expects field name "sha256"
  remoteUrl?: string;
  version?: string;
};

export type EnsurePolicy = "bundleOnly" | "downloadOnly" | "bundleThenDownload";

export type CheckResult = {
  key: string;
  status: "installed" | "missing" | "corrupt";
  installedPath: string;
  hasBundledZip: boolean;
  state?: any;
};

export type EnsureResult = {
  key: string;
  ok: boolean;

  installedPath: string;
  installedVersion?: string;

  code?: string;
  message?: string;

  hasBundledZip?: boolean;
  usedSource?: "bundle" | "download" | "none";

  sha256?: string;
  zipSize?: number;
  unpackTo?: string;

  state?: any;
};

export type ProgressEvent = {
  key: string;
  phase:
    | "checking"
    | "copying"
    | "downloading"
    | "verifying"
    | "unpacking"
    | "finalizing"
    | "done"
    | "error";
  downloaded?: number;
  total?: number;
  progress?: number; // 0..1
  message?: string;
};

export interface CapacitorModelhubPluginPlugin {
  echo(options: { value: string }): Promise<{ value: string }>;
  getRoot(): Promise<{ path: string }>;
  getPath(options: { unpackTo: string }): Promise<{ path: string }>;

  check(options: { items: ModelItem[] }): Promise<{ results: CheckResult[] }>;

  ensureInstalled(options: { item: ModelItem; policy: EnsurePolicy }): Promise<EnsureResult>;
  ensureInstalledMany(options: { items: ModelItem[]; policy: EnsurePolicy }): Promise<{ results: EnsureResult[] }>;

  addListener(
    eventName: "ModelsHubProgress",
    listenerFunc: (event: ProgressEvent) => void
  ): Promise<{ remove: () => Promise<void> }>;
}

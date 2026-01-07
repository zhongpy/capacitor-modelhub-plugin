export type ModelItem = {
  key: string;                 // zip 名称（不含 .zip），对应 assets/models/<key>.zip
  unpackTo: string;            // 解压目标相对路径（相对于 models root）
  checkFiles: string[];        // 解压后必须存在的文件（相对于 unpackTo）
  password?: string;           // AES zip password
  sha256?: string;             // zip sha256（推荐）
  remoteUrl?: string;          // 无 bundled 时下载地址
  version?: string;            // installedVersion（用于 state.json）
};

export type EnsurePolicy = "bundleOnly" | "downloadOnly" | "bundleThenDownload";

export type CheckResult = {
  key: string;
  status: "installed" | "missing" | "corrupt";
  installedPath: string;       // <modelsRoot>/<unpackTo>
  hasBundledZip: boolean;      // assets/models/<key>.zip 是否存在
  state?: any;                 // state.json 中该 key 的记录（如果有）
};

export type EnsureResult = {
  key: string;
  installedPath: string;
  installedVersion?: string;
};

export type ProgressEvent = {
  key: string;
  phase: "checking" | "copying" | "downloading" | "verifying" | "unpacking" | "finalizing" | "done" | "error";
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

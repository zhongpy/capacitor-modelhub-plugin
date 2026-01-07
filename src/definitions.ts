
export type ModelItem = {
  key: string;                 // 必须与 assets/models/<key>.zip 对齐
  unpackTo: string;            // e.g. "stt/en_kroko"
  checkFiles: string[];        // e.g. ["encoder.onnx","tokens.txt"]
  password?: string;           // AES zip password
  sha256?: string;             // 可选：zip 的 sha256（推荐）
  remoteUrl?: string;          // releaseLite 走下载
};

export type CheckResult = {
  key: string;
  status: "installed" | "missing" | "corrupt";
  installedPath: string;       // <modelsRoot>/<unpackTo>
  hasBundledZip: boolean;      // assets/models/<key>.zip 是否存在
};

export type EnsurePolicy = "bundleOnly" | "downloadOnly" | "bundleThenDownload";

export type EnsureResult = {
  key: string;
  installedPath: string;
};

export type ProgressEvent = {
  key: string;
  phase: "checking" | "copying" | "downloading" | "verifying" | "unpacking" | "finalizing";
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

  addListener(
    eventName: "ModelsHubProgress",
    listenerFunc: (event: ProgressEvent) => void
  ): Promise<{ remove: () => Promise<void> }>;
}

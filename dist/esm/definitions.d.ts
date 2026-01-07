export type ModelItem = {
    key: string;
    unpackTo: string;
    checkFiles: string[];
    password?: string;
    sha256?: string;
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
    installedPath: string;
    installedVersion?: string;
};
export type ProgressEvent = {
    key: string;
    phase: "checking" | "copying" | "downloading" | "verifying" | "unpacking" | "finalizing" | "done" | "error";
    downloaded?: number;
    total?: number;
    progress?: number;
    message?: string;
};
export interface CapacitorModelhubPluginPlugin {
    echo(options: {
        value: string;
    }): Promise<{
        value: string;
    }>;
    getRoot(): Promise<{
        path: string;
    }>;
    getPath(options: {
        unpackTo: string;
    }): Promise<{
        path: string;
    }>;
    check(options: {
        items: ModelItem[];
    }): Promise<{
        results: CheckResult[];
    }>;
    ensureInstalled(options: {
        item: ModelItem;
        policy: EnsurePolicy;
    }): Promise<EnsureResult>;
    ensureInstalledMany(options: {
        items: ModelItem[];
        policy: EnsurePolicy;
    }): Promise<{
        results: EnsureResult[];
    }>;
    addListener(eventName: "ModelsHubProgress", listenerFunc: (event: ProgressEvent) => void): Promise<{
        remove: () => Promise<void>;
    }>;
}

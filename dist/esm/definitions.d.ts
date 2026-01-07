export type ModelItem = {
    key: string;
    unpackTo: string;
    checkFiles: string[];
    password?: string;
    sha256?: string;
    remoteUrl?: string;
};
export type CheckResult = {
    key: string;
    status: "installed" | "missing" | "corrupt";
    installedPath: string;
    hasBundledZip: boolean;
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
    addListener(eventName: "ModelsHubProgress", listenerFunc: (event: ProgressEvent) => void): Promise<{
        remove: () => Promise<void>;
    }>;
}

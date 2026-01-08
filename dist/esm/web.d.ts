import type { CapacitorModelhubPluginPlugin, ModelItem, EnsurePolicy, CheckResult, EnsureResult, ProgressEvent } from "./definitions";
export declare class CapacitorModelhubPluginWeb implements CapacitorModelhubPluginPlugin {
    private listeners;
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
    ensureInstalled(_options: {
        item: ModelItem;
        policy: EnsurePolicy;
    }): Promise<EnsureResult>;
    ensureInstalledMany(_options: {
        items: ModelItem[];
        policy: EnsurePolicy;
    }): Promise<{
        results: EnsureResult[];
    }>;
    addListener(eventName: "ModelsHubProgress", listenerFunc: (event: ProgressEvent) => void): Promise<{
        remove: () => Promise<void>;
    }>;
    protected emit(ev: ProgressEvent): void;
}

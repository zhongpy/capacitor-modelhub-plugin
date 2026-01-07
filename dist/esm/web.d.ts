import { WebPlugin } from '@capacitor/core';
import type { CapacitorModelhubPluginPlugin, ModelItem, CheckResult, EnsurePolicy, EnsureResult } from './definitions';
export declare class CapacitorModelhubPluginWeb extends WebPlugin implements CapacitorModelhubPluginPlugin {
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
}

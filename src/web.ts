// src/web.ts
import type {
  CapacitorModelhubPluginPlugin,
  ModelItem,
  EnsurePolicy,
  CheckResult,
  EnsureResult,
  ProgressEvent,
} from "./definitions";

export class CapacitorModelhubPluginWeb implements CapacitorModelhubPluginPlugin {
  private listeners: Array<(e: ProgressEvent) => void> = [];

  async echo(options: { value: string }) {
    return { value: options.value };
  }

  async getRoot() {
    return { path: "/models" };
  }

  async getPath(options: { unpackTo: string }) {
    const root = (await this.getRoot()).path.replace(/\/+$/, "");
    const rel = String(options.unpackTo || "").replace(/^\/+/, "");
    return { path: `${root}/${rel}` };
  }

  async check(options: { items: ModelItem[] }): Promise<{ results: CheckResult[] }> {
    const items = Array.isArray(options.items) ? options.items : [];
    const root = (await this.getRoot()).path.replace(/\/+$/, "");

    return {
      results: items.map((it) => ({
        key: it.key,
        status: "missing",
        installedPath: `${root}/${String(it.unpackTo || "").replace(/^\/+/, "")}`,
        hasBundledZip: false,
      })),
    };
  }

  async ensureInstalled(_options: { item: ModelItem; policy: EnsurePolicy }): Promise<EnsureResult> {
    throw new Error("CapacitorModelhubPlugin is not supported on Web");
  }

  async ensureInstalledMany(_options: { items: ModelItem[]; policy: EnsurePolicy }): Promise<{ results: EnsureResult[] }> {
    throw new Error("CapacitorModelhubPlugin is not supported on Web");
  }

  async addListener(
    eventName: "ModelsHubProgress",
    listenerFunc: (event: ProgressEvent) => void
  ): Promise<{ remove: () => Promise<void> }> {
    if (eventName !== "ModelsHubProgress") return { remove: async () => void 0 };
    this.listeners.push(listenerFunc);
    return {
      remove: async () => {
        this.listeners = this.listeners.filter((fn) => fn !== listenerFunc);
      },
    };
  }

  protected emit(ev: ProgressEvent) {
    for (const fn of this.listeners) {
      try { fn(ev); } catch { /* ignore */ }
    }
  }
}

var capacitorCapacitorModelhubPlugin = (function (exports, core) {
    'use strict';

    // src/index.ts
    const CapacitorModelhubPlugin = core.registerPlugin("CapacitorModelhubPlugin", {
        web: () => Promise.resolve().then(function () { return web; }).then((m) => new m.CapacitorModelhubPluginWeb()),
    });

    class CapacitorModelhubPluginWeb {
        constructor() {
            this.listeners = [];
        }
        async echo(options) {
            return { value: options.value };
        }
        async getRoot() {
            return { path: "/models" };
        }
        async getPath(options) {
            const root = (await this.getRoot()).path.replace(/\/+$/, "");
            const rel = String(options.unpackTo || "").replace(/^\/+/, "");
            return { path: `${root}/${rel}` };
        }
        async check(options) {
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
        async ensureInstalled(_options) {
            throw new Error("CapacitorModelhubPlugin is not supported on Web");
        }
        async ensureInstalledMany(_options) {
            throw new Error("CapacitorModelhubPlugin is not supported on Web");
        }
        async addListener(eventName, listenerFunc) {
            if (eventName !== "ModelsHubProgress")
                return { remove: async () => void 0 };
            this.listeners.push(listenerFunc);
            return {
                remove: async () => {
                    this.listeners = this.listeners.filter((fn) => fn !== listenerFunc);
                },
            };
        }
        emit(ev) {
            for (const fn of this.listeners) {
                try {
                    fn(ev);
                }
                catch ( /* ignore */_a) { /* ignore */ }
            }
        }
    }

    var web = /*#__PURE__*/Object.freeze({
        __proto__: null,
        CapacitorModelhubPluginWeb: CapacitorModelhubPluginWeb
    });

    exports.CapacitorModelhubPlugin = CapacitorModelhubPlugin;

    return exports;

})({}, capacitorExports);
//# sourceMappingURL=plugin.js.map

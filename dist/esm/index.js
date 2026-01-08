// src/index.ts
import { registerPlugin } from "@capacitor/core";
export const CapacitorModelhubPlugin = registerPlugin("CapacitorModelhubPlugin", {
    web: () => import("./web").then((m) => new m.CapacitorModelhubPluginWeb()),
});
export * from "./definitions";
//# sourceMappingURL=index.js.map
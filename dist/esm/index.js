import { registerPlugin } from '@capacitor/core';
const CapacitorModelhubPlugin = registerPlugin('CapacitorModelhubPlugin', {
    web: () => import('./web').then((m) => new m.CapacitorModelhubPluginWeb()),
});
export * from './definitions';
export { CapacitorModelhubPlugin };
//# sourceMappingURL=index.js.map
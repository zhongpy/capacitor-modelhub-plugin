import { registerPlugin } from '@capacitor/core';

import type { CapacitorModelhubPluginPlugin } from './definitions';

const CapacitorModelhubPlugin = registerPlugin<CapacitorModelhubPluginPlugin>('CapacitorModelhubPlugin', {
  web: () => import('./web').then((m) => new m.CapacitorModelhubPluginWeb()),
});

export * from './definitions';
export { CapacitorModelhubPlugin };

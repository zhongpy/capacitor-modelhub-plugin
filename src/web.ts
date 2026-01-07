import { WebPlugin } from '@capacitor/core';

import type { CapacitorModelhubPluginPlugin,ModelItem,CheckResult,EnsurePolicy,EnsureResult } from './definitions';

export class CapacitorModelhubPluginWeb extends WebPlugin implements CapacitorModelhubPluginPlugin {
  async echo(options: { value: string }): Promise<{ value: string }> {
    console.log('ECHO', options);
    return options;
  }

  async getRoot(): Promise<{ path: string }>{
    const result = { path: '' };
    console.log('getRoot');
    return result;
  }
  async getPath(options: { unpackTo: string }): Promise<{ path: string }>{
    const result = { path: '' };
    console.log('getPath'+options);
    return result;
  }
  async check(options: { items: ModelItem[] }): Promise<{ results: CheckResult[] }>{
    const result = { results: [] };
    console.log('check'+options);
    return result;
  }
  async ensureInstalled(options: { item: ModelItem; policy: EnsurePolicy }): Promise<EnsureResult>{
    const result = { key: options.item.key, installedPath: '' };
    console.log('ensureInstalled');
    return result;
  }
}

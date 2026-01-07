import { WebPlugin } from '@capacitor/core';

import type { CapacitorModelhubPluginPlugin } from './definitions';

export class CapacitorModelhubPluginWeb extends WebPlugin implements CapacitorModelhubPluginPlugin {
  async echo(options: { value: string }): Promise<{ value: string }> {
    console.log('ECHO', options);
    return options;
  }
}

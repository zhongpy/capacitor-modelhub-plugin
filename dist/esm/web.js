import { WebPlugin } from '@capacitor/core';
export class CapacitorModelhubPluginWeb extends WebPlugin {
    async echo(options) {
        console.log('ECHO', options);
        return options;
    }
    async getRoot() {
        const result = { path: '' };
        console.log('getRoot');
        return result;
    }
    async getPath(options) {
        const result = { path: '' };
        console.log('getPath' + options);
        return result;
    }
    async check(options) {
        const result = { results: [] };
        console.log('check' + options);
        return result;
    }
    async ensureInstalled(options) {
        const result = { key: options.item.key, installedPath: '' };
        console.log('ensureInstalled');
        return result;
    }
}
//# sourceMappingURL=web.js.map
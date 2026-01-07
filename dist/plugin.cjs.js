'use strict';

var core = require('@capacitor/core');

const CapacitorModelhubPlugin = core.registerPlugin('CapacitorModelhubPlugin', {
    web: () => Promise.resolve().then(function () { return web; }).then((m) => new m.CapacitorModelhubPluginWeb()),
});

class CapacitorModelhubPluginWeb extends core.WebPlugin {
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
    async ensureInstalledMany(options) {
        const result = { results: [] };
        console.log('ensureInstalledMany' + options);
        return result;
    }
}

var web = /*#__PURE__*/Object.freeze({
    __proto__: null,
    CapacitorModelhubPluginWeb: CapacitorModelhubPluginWeb
});

exports.CapacitorModelhubPlugin = CapacitorModelhubPlugin;
//# sourceMappingURL=plugin.cjs.js.map

# capacitor-modelhub-plugin

CapacitorModelhubPlugin Capacitor Plugin

## Install

```bash
npm install capacitor-modelhub-plugin
npx cap sync
```

## API

<docgen-index>

* [`echo(...)`](#echo)
* [`getRoot()`](#getroot)
* [`getPath(...)`](#getpath)
* [`check(...)`](#check)
* [`ensureInstalled(...)`](#ensureinstalled)
* [`ensureInstalledMany(...)`](#ensureinstalledmany)
* [`addListener('ModelsHubProgress', ...)`](#addlistenermodelshubprogress-)
* [Type Aliases](#type-aliases)

</docgen-index>

<docgen-api>
<!--Update the source file JSDoc comments and rerun docgen to update the docs below-->

### echo(...)

```typescript
echo(options: { value: string; }) => any
```

| Param         | Type                            |
| ------------- | ------------------------------- |
| **`options`** | <code>{ value: string; }</code> |

**Returns:** <code>any</code>

--------------------


### getRoot()

```typescript
getRoot() => any
```

**Returns:** <code>any</code>

--------------------


### getPath(...)

```typescript
getPath(options: { unpackTo: string; }) => any
```

| Param         | Type                               |
| ------------- | ---------------------------------- |
| **`options`** | <code>{ unpackTo: string; }</code> |

**Returns:** <code>any</code>

--------------------


### check(...)

```typescript
check(options: { items: ModelItem[]; }) => any
```

| Param         | Type                        |
| ------------- | --------------------------- |
| **`options`** | <code>{ items: {}; }</code> |

**Returns:** <code>any</code>

--------------------


### ensureInstalled(...)

```typescript
ensureInstalled(options: { item: ModelItem; policy: EnsurePolicy; }) => any
```

| Param         | Type                                                                                                         |
| ------------- | ------------------------------------------------------------------------------------------------------------ |
| **`options`** | <code>{ item: <a href="#modelitem">ModelItem</a>; policy: <a href="#ensurepolicy">EnsurePolicy</a>; }</code> |

**Returns:** <code>any</code>

--------------------


### ensureInstalledMany(...)

```typescript
ensureInstalledMany(options: { items: ModelItem[]; policy: EnsurePolicy; }) => any
```

| Param         | Type                                                                          |
| ------------- | ----------------------------------------------------------------------------- |
| **`options`** | <code>{ items: {}; policy: <a href="#ensurepolicy">EnsurePolicy</a>; }</code> |

**Returns:** <code>any</code>

--------------------


### addListener('ModelsHubProgress', ...)

```typescript
addListener(eventName: "ModelsHubProgress", listenerFunc: (event: ProgressEvent) => void) => any
```

| Param              | Type                                                                        |
| ------------------ | --------------------------------------------------------------------------- |
| **`eventName`**    | <code>'ModelsHubProgress'</code>                                            |
| **`listenerFunc`** | <code>(event: <a href="#progressevent">ProgressEvent</a>) =&gt; void</code> |

**Returns:** <code>any</code>

--------------------


### Type Aliases


#### ModelItem

<code>{ key: string; // assets/models/&lt;key&gt;.zip unpackTo: string; // relative to models root checkFiles: string[]; // relative to unpackTo password?: string; // AES zip password sha256?: string; // NOTE: Java expects field name "sha256" remoteUrl?: string; version?: string; }</code>


#### CheckResult

<code>{ key: string; status: "installed" | "missing" | "corrupt"; installedPath: string; hasBundledZip: boolean; state?: any; }</code>


#### EnsurePolicy

<code>"bundleOnly" | "downloadOnly" | "bundleThenDownload"</code>


#### EnsureResult

<code>{ key: string; ok: boolean; installedPath: string; installedVersion?: string; code?: string; message?: string; hasBundledZip?: boolean; usedSource?: "bundle" | "download" | "none"; sha256?: string; zipSize?: number; unpackTo?: string; state?: any; }</code>


#### ProgressEvent

<code>{ key: string; phase: | "checking" | "copying" | "downloading" | "verifying" | "unpacking" | "finalizing" | "done" | "error"; downloaded?: number; total?: number; progress?: number; // 0..1 message?: string; }</code>

</docgen-api>

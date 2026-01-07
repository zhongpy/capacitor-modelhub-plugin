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

<code>{ key: string; // 必须与 assets/models/&lt;key&gt;.zip 对齐 unpackTo: string; // e.g. "stt/en_kroko" checkFiles: string[]; // e.g. ["encoder.onnx","tokens.txt"] password?: string; // AES zip password sha256?: string; // 可选：zip 的 sha256（推荐） remoteUrl?: string; // releaseLite 走下载 }</code>


#### CheckResult

<code>{ key: string; status: "installed" | "missing" | "corrupt"; installedPath: string; // &lt;modelsRoot&gt;/&lt;unpackTo&gt; hasBundledZip: boolean; // assets/models/&lt;key&gt;.zip 是否存在 }</code>


#### EnsurePolicy

<code>"bundleOnly" | "downloadOnly" | "bundleThenDownload"</code>


#### EnsureResult

<code>{ key: string; installedPath: string; }</code>


#### ProgressEvent

<code>{ key: string; phase: "checking" | "copying" | "downloading" | "verifying" | "unpacking" | "finalizing"; downloaded?: number; total?: number; progress?: number; // 0..1 message?: string; }</code>

</docgen-api>

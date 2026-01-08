package com.mycompany.capacitor.modelhub.plugin;

import com.getcapacitor.*;
import com.getcapacitor.annotation.CapacitorPlugin;

import android.content.Context;
import android.content.res.AssetManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import net.lingala.zip4j.ZipFile;

@CapacitorPlugin(name = "CapacitorModelhubPlugin")
public class CapacitorModelhubPluginPlugin extends Plugin {

    private static final String STATE_FILE_NAME = "state.json";
    private final ExecutorService executor = Executors.newCachedThreadPool();

    @PluginMethod
    public void echo(PluginCall call) {
        String value = call.getString("value");
        call.resolve(new JSObject().put("value", value));
    }

    @PluginMethod
    public void getRoot(PluginCall call) {
        File root = ensureRoot(getContext());
        call.resolve(new JSObject().put("path", root.getAbsolutePath()));
    }

    @PluginMethod
    public void getPath(PluginCall call) {
        String unpackTo = call.getString("unpackTo", "");
        File root = ensureRoot(getContext());
        File dir = new File(root, safeRel(unpackTo));
        call.resolve(new JSObject().put("path", dir.getAbsolutePath()));
    }

    @PluginMethod
    public void check(PluginCall call) {
        try {
            JSONArray items = call.getArray("items");
            if (items == null)
                items = new JSONArray();

            File root = ensureRoot(getContext());
            JSONObject stateAll = readState(root);

            JSArray results = new JSArray();
            for (int i = 0; i < items.length(); i++) {
                JSONObject it = items.getJSONObject(i);

                String key = it.optString("key", "");
                String unpackTo = it.optString("unpackTo", "");
                JSONArray checkFiles = it.optJSONArray("checkFiles");

                File installedDir = new File(root, safeRel(unpackTo));
                boolean hasBundled = assetExists(getContext(), "models/" + key + ".zip");
                Status st = checkInstalled(installedDir, checkFiles);

                JSObject r = new JSObject();
                r.put("key", key);
                r.put("installedPath", installedDir.getAbsolutePath());
                r.put("hasBundledZip", hasBundled);
                r.put("status", st.value);

                if (stateAll.has(key)) {
                    r.put("state", JSObject.fromJSONObject(stateAll.getJSONObject(key)));
                }
                results.put(r);
            }

            call.resolve(new JSObject().put("results", results));
        } catch (Exception e) {
            call.reject("check error: " + e.getMessage());
        }
    }

    @PluginMethod
    public void ensureInstalled(PluginCall call) {
        executor.execute(() -> {
            try {
                JSObject item = call.getObject("item");
                if (item == null) {
                    call.reject("item is required");
                    return;
                }
                String policy = call.getString("policy", "bundleThenDownload");

                EnsureResult r = ensureOne(new JSONObject(item.toString()), policy);
                call.resolve(r.toJs());

            } catch (Exception e) {
                // 单个 ensureInstalled：保持 reject（调用方通常希望明确失败）
                call.reject("ensureInstalled error: " + e.getMessage());
            }
        });
    }

    @PluginMethod
    public void ensureInstalledMany(PluginCall call) {
        executor.execute(() -> {
            try {
                JSONArray items = call.getArray("items");
                if (items == null)
                    items = new JSONArray();
                String policy = call.getString("policy", "bundleThenDownload");

                JSArray arr = new JSArray();
                for (int i = 0; i < items.length(); i++) {
                    JSONObject it = items.getJSONObject(i);
                    EnsureResult r;
                    try {
                        r = ensureOne(it, policy);
                    } catch (Exception oneErr) {
                        // 关键：单条失败不让整个批量 reject
                        String key = it.optString("key", "");
                        String unpackTo = it.optString("unpackTo", "");
                        File root = ensureRoot(getContext());
                        File installedDir = new File(root, safeRel(unpackTo));
                        boolean hasBundled = assetExists(getContext(), "models/" + key + ".zip");

                        r = EnsureResult.fail(
                                key,
                                installedDir.getAbsolutePath(),
                                normalizeCode(oneErr),
                                oneErr.getMessage(),
                                hasBundled,
                                unpackTo);
                    }
                    arr.put(r.toJs());
                }

                call.resolve(new JSObject().put("results", arr));
            } catch (Exception e) {
                call.reject("ensureInstalledMany error: " + e.getMessage());
            }
        });
    }

    @Override
    protected void handleOnDestroy() {
        super.handleOnDestroy();
        executor.shutdownNow();
    }

    // ===================== Core Ensure =====================

    private EnsureResult ensureOne(JSONObject item, String policy) throws Exception {
        Context ctx = getContext();
        File root = ensureRoot(ctx);

        String key = item.optString("key", "");
        String unpackTo = item.optString("unpackTo", "");
        String password = item.optString("password", "");
        String sha256 = item.optString("sha256", "");
        String remoteUrl = item.optString("remoteUrl", "");
        String version = item.optString("version", "");
        JSONArray checkFiles = item.optJSONArray("checkFiles");

        if (key.isEmpty() || unpackTo.isEmpty()) {
            throw new IllegalArgumentException("key/unpackTo is required");
        }

        File installedDir = new File(root, safeRel(unpackTo));

        emit(key, "checking", null, null, null, "checking installed");
        Status st0 = checkInstalled(installedDir, checkFiles);
        if (st0 == Status.INSTALLED) {
            // 已存在也回传 state（如果有）
            JSONObject stateAll = readState(root);
            JSONObject rec = stateAll.optJSONObject(key);

            return EnsureResult.ok(
                    key,
                    installedDir.getAbsolutePath(),
                    emptyToNull(version),
                    "installed",
                    "already installed",
                    assetExists(ctx, "models/" + key + ".zip"),
                    "none",
                    sha256,
                    0L,
                    unpackTo,
                    rec);
        }

        boolean hasBundled = assetExists(ctx, "models/" + key + ".zip");

        // ---- bundle 优先 ----
        if (!"downloadOnly".equals(policy) && hasBundled) {
            File zip = copyAssetZipToTmp(ctx, key);
            long zipSize = zip.length();

            if (!sha256.isEmpty()) {
                emit(key, "verifying", null, null, null, "sha256 verifying");
                String got = sha256File(zip);
                if (!sha256.equalsIgnoreCase(got)) {
                    // noinspection ResultOfMethodCallIgnored
                    zip.delete();
                    throw new IOException("SHA256_MISMATCH bundled expected=" + sha256 + " got=" + got);
                }
            }

            installFromZip(key, zip, installedDir, password, checkFiles);

            writeStateRecord(root, key, version, sha256, zipSize, unpackTo);
            JSONObject stateAll = readState(root);
            JSONObject rec = stateAll.optJSONObject(key);

            emit(key, "done", null, null, 1.0, "installed");
            return EnsureResult.ok(
                    key,
                    installedDir.getAbsolutePath(),
                    emptyToNull(version),
                    "installed",
                    "installed from bundle",
                    true,
                    "bundle",
                    sha256,
                    zipSize,
                    unpackTo,
                    rec);
        }

        if ("bundleOnly".equals(policy)) {
            emit(key, "error", null, null, null, "MODEL_MISSING_BUNDLED");
            throw new IOException("MODEL_MISSING_BUNDLED");
        }

        // ---- download ----
        if (remoteUrl.isEmpty()) {
            emit(key, "error", null, null, null, "MODEL_MISSING_REMOTE_URL");
            throw new IOException("MODEL_MISSING_REMOTE_URL");
        }

        File zip = downloadZipToTmp(key, remoteUrl);
        long zipSize = zip.length();

        if (!sha256.isEmpty()) {
            emit(key, "verifying", null, null, null, "sha256 verifying");
            String got = sha256File(zip);
            if (!sha256.equalsIgnoreCase(got)) {
                // noinspection ResultOfMethodCallIgnored
                zip.delete();
                throw new IOException("SHA256_MISMATCH downloaded expected=" + sha256 + " got=" + got);
            }
        }

        installFromZip(key, zip, installedDir, password, checkFiles);

        writeStateRecord(root, key, version, sha256, zipSize, unpackTo);
        JSONObject stateAll = readState(root);
        JSONObject rec = stateAll.optJSONObject(key);

        emit(key, "done", null, null, 1.0, "installed");
        return EnsureResult.ok(
                key,
                installedDir.getAbsolutePath(),
                emptyToNull(version),
                "installed",
                "installed from download",
                hasBundled,
                "download",
                sha256,
                zipSize,
                unpackTo,
                rec);
    }

    private void installFromZip(String key, File zip, File installedDir, String password, JSONArray checkFiles)
            throws Exception {
        File root = ensureRoot(getContext());
        File tmpRoot = new File(root, "_tmp");
        // noinspection ResultOfMethodCallIgnored
        tmpRoot.mkdirs();

        File unpackDir = new File(tmpRoot, "unpack_" + key);
        deleteRecursively(unpackDir);
        // noinspection ResultOfMethodCallIgnored
        unpackDir.mkdirs();

        emit(key, "unpacking", null, null, null, "unpacking zip");
        unzipAesZip(zip, unpackDir, password);

        Status st = checkInstalled(unpackDir, checkFiles);
        if (st != Status.INSTALLED) {
            throw new IOException("UNPACK_INVALID:" + st.value);
        }

        emit(key, "finalizing", null, null, null, "finalizing");
        deleteRecursively(installedDir);
        if (!unpackDir.renameTo(installedDir)) {
            copyDir(unpackDir, installedDir);
            deleteRecursively(unpackDir);
        }

        // noinspection ResultOfMethodCallIgnored
        zip.delete();
    }

    // ===================== Zip / Assets / Download =====================

    private void unzipAesZip(File zipFile, File targetDir, String password) throws Exception {
        if (password == null)
            password = "";
        ZipFile zf = new ZipFile(zipFile, password.toCharArray());
        zf.extractAll(targetDir.getAbsolutePath());
    }

    private File copyAssetZipToTmp(Context ctx, String key) throws Exception {
        emit(key, "copying", null, null, null, "copying bundled zip");
        File root = ensureRoot(ctx);
        File tmpDir = new File(root, "_tmp");
        // noinspection ResultOfMethodCallIgnored
        tmpDir.mkdirs();
        File out = new File(tmpDir, key + ".zip");

        String assetPath = "models/" + key + ".zip";
        try (InputStream in = new BufferedInputStream(ctx.getAssets().open(assetPath, AssetManager.ACCESS_STREAMING));
                OutputStream os = new BufferedOutputStream(new FileOutputStream(out))) {
            byte[] buf = new byte[1024 * 1024];
            int n;
            while ((n = in.read(buf)) >= 0)
                os.write(buf, 0, n);
            os.flush();
        }
        return out;
    }

    private File downloadZipToTmp(String key, String urlStr) throws Exception {
        File root = ensureRoot(getContext());
        File tmpDir = new File(root, "_tmp");
        // noinspection ResultOfMethodCallIgnored
        tmpDir.mkdirs();
        File out = new File(tmpDir, key + ".zip");

        HttpURLConnection conn = null;
        try (BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(out))) {
            emit(key, "downloading", 0L, 0L, 0.0, "starting download");
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(20000);
            conn.setReadTimeout(600000);
            conn.connect();
            int rc = conn.getResponseCode();
            if (rc != 200)
                throw new IOException("HTTP_" + rc);

            long total = conn.getContentLengthLong();
            long downloaded = 0;
            long lastEmit = 0;

            try (BufferedInputStream in = new BufferedInputStream(conn.getInputStream())) {
                byte[] buf = new byte[1024 * 1024];
                int n;
                while ((n = in.read(buf)) >= 0) {
                    bos.write(buf, 0, n);
                    downloaded += n;

                    long now = System.currentTimeMillis();
                    if (now - lastEmit > 250) {
                        double p = (total > 0) ? (downloaded * 1.0 / total) : 0.0;
                        emit(key, "downloading", downloaded, total, p, null);
                        lastEmit = now;
                    }
                }
            }
            bos.flush();
            emit(key, "downloading", downloaded, total, 1.0, "download complete");
            return out;
        } finally {
            if (conn != null)
                conn.disconnect();
        }
    }

    // ===================== State.json =====================

    private JSONObject readState(File root) {
        File f = new File(root, STATE_FILE_NAME);
        if (!f.exists())
            return new JSONObject();
        try (InputStream in = new FileInputStream(f)) {
            byte[] data = readAll(in);
            String s = new String(data);
            return new JSONObject(s.isEmpty() ? "{}" : s);
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    private void writeStateRecord(File root, String key, String version, String sha256, long zipSize, String unpackTo) {
        try {
            JSONObject state = readState(root);
            JSONObject rec = new JSONObject();
            rec.put("installedVersion", version == null ? "" : version);
            rec.put("sha256", sha256 == null ? "" : sha256);
            rec.put("zipSize", zipSize);
            rec.put("unpackTo", unpackTo);
            rec.put("installedAt", System.currentTimeMillis());
            state.put(key, rec);

            File f = new File(root, STATE_FILE_NAME);
            writeAtomic(f, state.toString());
        } catch (Exception ignored) {
        }
    }

    private void writeAtomic(File f, String content) throws Exception {
        File dir = f.getParentFile();
        if (dir != null && !dir.exists()) {
            // noinspection ResultOfMethodCallIgnored
            dir.mkdirs();
        }
        File tmp = new File(dir, f.getName() + ".tmp");
        try (OutputStream out = new FileOutputStream(tmp)) {
            out.write(content.getBytes());
            out.flush();
        }
        if (f.exists() && !f.delete()) {
            /* ignore */ }
        if (!tmp.renameTo(f))
            throw new IOException("rename state tmp failed");
    }

    private byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) >= 0)
            bos.write(buf, 0, n);
        return bos.toByteArray();
    }

    // ===================== Installed checking =====================

    enum Status {
        INSTALLED("installed"),
        MISSING("missing"),
        CORRUPT("corrupt");

        final String value;

        Status(String v) {
            value = v;
        }
    }

    private Status checkInstalled(File dir, JSONArray checkFiles) {
        if (dir == null || !dir.exists() || !dir.isDirectory())
            return Status.MISSING;
        if (checkFiles == null || checkFiles.length() == 0)
            return Status.INSTALLED;

        for (int i = 0; i < checkFiles.length(); i++) {
            String rel = checkFiles.optString(i, "");
            if (rel.isEmpty())
                continue;
            File f = new File(dir, rel);
            if (!f.exists())
                return Status.CORRUPT;
            if (f.isFile() && f.length() < 16)
                return Status.CORRUPT;
        }
        return Status.INSTALLED;
    }

    private boolean assetExists(Context ctx, String assetPath) {
        try (InputStream in = ctx.getAssets().open(assetPath)) {
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // ===================== sha256 =====================

    private static String sha256File(File f) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        try (InputStream in = new BufferedInputStream(new FileInputStream(f));
                DigestInputStream din = new DigestInputStream(in, md)) {
            byte[] buf = new byte[1024 * 1024];
            while (din.read(buf) >= 0) {
                /* consume */ }
        }
        byte[] d = md.digest();
        StringBuilder sb = new StringBuilder(d.length * 2);
        for (byte b : d)
            sb.append(String.format(Locale.ROOT, "%02x", b));
        return sb.toString();
    }

    // ===================== Utils =====================

    private File ensureRoot(Context ctx) {
        File root = new File(ctx.getFilesDir(), "models");
        if (!root.exists()) {
            // noinspection ResultOfMethodCallIgnored
            root.mkdirs();
        }
        return root;
    }

    private String safeRel(String rel) {
        if (rel == null)
            return "";
        rel = rel.replace("\\", "/");
        while (rel.startsWith("/"))
            rel = rel.substring(1);
        rel = rel.replace("..", "");
        return rel;
    }

    private void emit(String key, String phase, Long downloaded, Long total, Double progress, String message) {
        JSObject ev = new JSObject();
        ev.put("key", key);
        ev.put("phase", phase);
        if (downloaded != null)
            ev.put("downloaded", downloaded);
        if (total != null)
            ev.put("total", total);
        if (progress != null)
            ev.put("progress", progress);
        if (message != null)
            ev.put("message", message);
        notifyListeners("ModelsHubProgress", ev);
    }

    private void deleteRecursively(File f) {
        if (f == null || !f.exists())
            return;
        if (f.isDirectory()) {
            File[] children = f.listFiles();
            if (children != null)
                for (File c : children)
                    deleteRecursively(c);
        }
        // noinspection ResultOfMethodCallIgnored
        f.delete();
    }

    private void copyDir(File src, File dst) throws IOException {
        if (src.isDirectory()) {
            if (!dst.exists()) {
                // noinspection ResultOfMethodCallIgnored
                dst.mkdirs();
            }
            File[] children = src.listFiles();
            if (children != null) {
                for (File c : children)
                    copyDir(c, new File(dst, c.getName()));
            }
        } else {
            File parent = dst.getParentFile();
            if (parent != null && !parent.exists()) {
                // noinspection ResultOfMethodCallIgnored
                parent.mkdirs();
            }
            try (InputStream in = new FileInputStream(src);
                    OutputStream out = new FileOutputStream(dst)) {
                byte[] buf = new byte[1024 * 1024];
                int n;
                while ((n = in.read(buf)) >= 0)
                    out.write(buf, 0, n);
            }
        }
    }

    private static String emptyToNull(String s) {
        if (s == null)
            return null;
        s = s.trim();
        return s.isEmpty() ? null : s;
    }

    private static String normalizeCode(Exception e) {
        String msg = (e.getMessage() == null) ? "" : e.getMessage();
        if (msg.startsWith("HTTP_"))
            return msg; // HTTP_403 / HTTP_404 ...
        if (msg.startsWith("SHA256_MISMATCH"))
            return "SHA256_MISMATCH";
        if (msg.startsWith("UNPACK_INVALID"))
            return "UNPACK_INVALID";
        if (msg.equals("MODEL_MISSING_BUNDLED"))
            return "MODEL_MISSING_BUNDLED";
        if (msg.equals("MODEL_MISSING_REMOTE_URL"))
            return "MODEL_MISSING_REMOTE_URL";
        if (e instanceof IllegalArgumentException)
            return "BAD_ARGS";
        return "ERROR";
    }

    // ===================== DTO =====================

    static class EnsureResult {
        final String key;
        final boolean ok;
        final String installedPath;
        final String installedVersion;

        final String code;
        final String message;

        final boolean hasBundledZip;
        final String usedSource; // bundle/download/none

        final String sha256;
        final long zipSize;
        final String unpackTo;

        final JSONObject state; // state.json record

        private EnsureResult(
                String key,
                boolean ok,
                String installedPath,
                String installedVersion,
                String code,
                String message,
                boolean hasBundledZip,
                String usedSource,
                String sha256,
                long zipSize,
                String unpackTo,
                JSONObject state) {
            this.key = key;
            this.ok = ok;
            this.installedPath = installedPath;
            this.installedVersion = installedVersion;
            this.code = code;
            this.message = message;
            this.hasBundledZip = hasBundledZip;
            this.usedSource = usedSource;
            this.sha256 = sha256;
            this.zipSize = zipSize;
            this.unpackTo = unpackTo;
            this.state = state;
        }

        static EnsureResult ok(
                String key,
                String installedPath,
                String installedVersion,
                String code,
                String message,
                boolean hasBundledZip,
                String usedSource,
                String sha256,
                long zipSize,
                String unpackTo,
                JSONObject state) {
            return new EnsureResult(key, true, installedPath, installedVersion, code, message, hasBundledZip,
                    usedSource, sha256, zipSize, unpackTo, state);
        }

        static EnsureResult fail(
                String key,
                String installedPath,
                String code,
                String message,
                boolean hasBundledZip,
                String unpackTo) {
            return new EnsureResult(key, false, installedPath, null, code, message, hasBundledZip, "none", "", 0L,
                    unpackTo, null);
        }

        JSObject toJs() {
            JSObject o = new JSObject();
            o.put("key", key);
            o.put("ok", ok);
            o.put("installedPath", installedPath == null ? "" : installedPath);

            if (installedVersion != null)
                o.put("installedVersion", installedVersion);
            if (code != null)
                o.put("code", code);
            if (message != null)
                o.put("message", message);

            o.put("hasBundledZip", hasBundledZip);
            if (usedSource != null)
                o.put("usedSource", usedSource);

            if (sha256 != null && !sha256.isEmpty())
                o.put("sha256", sha256);
            o.put("zipSize", zipSize);
            if (unpackTo != null)
                o.put("unpackTo", unpackTo);

            if (state != null) {
                try {
                    o.put("state", JSObject.fromJSONObject(state));
                } catch (Exception ignored) {
                }
            }
            return o;
        }
    }
}

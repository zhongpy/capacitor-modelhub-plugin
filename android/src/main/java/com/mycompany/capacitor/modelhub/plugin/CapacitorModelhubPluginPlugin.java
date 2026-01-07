package com.mycompany.capacitor.modelhub.plugin;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import android.content.Context;
import android.content.res.AssetManager;
import com.getcapacitor.*;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.Locale;
import net.lingala.zip4j.ZipFile;

@CapacitorPlugin(name = "CapacitorModelhubPlugin")
public class CapacitorModelhubPluginPlugin extends Plugin {
    private static final String STATE_FILE_NAME = "state.json";

    private CapacitorModelhubPlugin implementation = new CapacitorModelhubPlugin();

    @PluginMethod
    public void echo(PluginCall call) {
        String value = call.getString("value");

        JSObject ret = new JSObject();
        ret.put("value", implementation.echo(value));
        call.resolve(ret);
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
            JSONObject state = readState(root);

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

                if (state.has(key))
                    r.put("state", JSObject.fromJSONObject(state.getJSONObject(key)));

                results.put(r);
            }

            JSObject ret = new JSObject();
            ret.put("results", results);
            call.resolve(ret);
        } catch (Exception e) {
            call.reject("check error: " + e.getMessage());
        }
    }

    @PluginMethod
    public void ensureInstalled(PluginCall call) {
        getBridge().executeOnThreadPool(() -> {
            try {
                JSObject item = call.getObject("item");
                if (item == null) {
                    call.reject("item is required");
                    return;
                }
                String policy = call.getString("policy", "bundleThenDownload");
                EnsureResult r = ensureOne(item.toJSONObject(), policy);
                call.resolve(r.toJs());
            } catch (Exception e) {
                call.reject("ensureInstalled error: " + e.getMessage());
            }
        });
    }

    @PluginMethod
    public void ensureInstalledMany(PluginCall call) {
        getBridge().executeOnThreadPool(() -> {
            try {
                JSONArray items = call.getArray("items");
                if (items == null)
                    items = new JSONArray();
                String policy = call.getString("policy", "bundleThenDownload");

                JSArray arr = new JSArray();
                for (int i = 0; i < items.length(); i++) {
                    JSONObject it = items.getJSONObject(i);
                    EnsureResult r = ensureOne(it, policy);
                    arr.put(r.toJs());
                }
                JSObject ret = new JSObject();
                ret.put("results", arr);
                call.resolve(ret);
            } catch (Exception e) {
                call.reject("ensureInstalledMany error: " + e.getMessage());
            }
        });
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

        if (key.isEmpty() || unpackTo.isEmpty())
            throw new IllegalArgumentException("key/unpackTo is required");

        File installedDir = new File(root, safeRel(unpackTo));

        emit(key, "checking", null, null, null, "checking installed");
        Status st0 = checkInstalled(installedDir, checkFiles);
        if (st0 == Status.INSTALLED) {
            return new EnsureResult(key, installedDir.getAbsolutePath(), version.isEmpty() ? null : version);
        }

        boolean hasBundled = assetExists(ctx, "models/" + key + ".zip");

        // 1) bundled
        if (!"downloadOnly".equals(policy) && hasBundled) {
            File zip = copyAssetZipToTmp(ctx, key);
            long zipSize = zip.length();

            if (!sha256.isEmpty()) {
                emit(key, "verifying", null, null, null, "sha256 verifying");
                String got = sha256File(zip);
                if (!sha256.equalsIgnoreCase(got)) {
                    // noinspection ResultOfMethodCallIgnored
                    zip.delete();
                    throw new IOException("SHA256 mismatch for bundled zip, expected=" + sha256 + " got=" + got);
                }
            }

            installFromZip(key, zip, installedDir, password, checkFiles);

            writeStateRecord(root, key, version, sha256, zipSize, unpackTo);

            emit(key, "done", null, null, 1.0, "installed");
            return new EnsureResult(key, installedDir.getAbsolutePath(), version.isEmpty() ? null : version);
        }

        if ("bundleOnly".equals(policy)) {
            emit(key, "error", null, null, null, "MODEL_MISSING_BUNDLED");
            throw new IOException("MODEL_MISSING_BUNDLED");
        }

        // 2) download
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
                throw new IOException("SHA256 mismatch for downloaded zip, expected=" + sha256 + " got=" + got);
            }
        }

        installFromZip(key, zip, installedDir, password, checkFiles);

        writeStateRecord(root, key, version, sha256, zipSize, unpackTo);

        emit(key, "done", null, null, 1.0, "installed");
        return new EnsureResult(key, installedDir.getAbsolutePath(), version.isEmpty() ? null : version);
    }

    private void installFromZip(String key, File zip, File installedDir, String password, JSONArray checkFiles)
            throws Exception {
        File root = ensureRoot(getContext());
        File tmpRoot = new File(root, "_tmp");
        tmpRoot.mkdirs();

        File unpackDir = new File(tmpRoot, "unpack_" + key);
        deleteRecursively(unpackDir);
        unpackDir.mkdirs();

        emit(key, "unpacking", null, null, null, "unpacking zip");
        unzipAesZip(zip, unpackDir, password);

        Status st = checkInstalled(unpackDir, checkFiles);
        if (st != Status.INSTALLED)
            throw new IOException("unpacked content invalid: " + st.value);

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
            if (conn.getResponseCode() != 200)
                throw new IOException("HTTP " + conn.getResponseCode());

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
        if (dir != null && !dir.exists())
            dir.mkdirs();
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
        INSTALLED("installed"), MISSING("missing"), CORRUPT("corrupt");

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
        if (!root.exists())
            root.mkdirs();
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
            if (!dst.exists())
                dst.mkdirs();
            File[] children = src.listFiles();
            if (children != null) {
                for (File c : children)
                    copyDir(c, new File(dst, c.getName()));
            }
        } else {
            File parent = dst.getParentFile();
            if (parent != null && !parent.exists())
                parent.mkdirs();
            try (InputStream in = new FileInputStream(src);
                    OutputStream out = new FileOutputStream(dst)) {
                byte[] buf = new byte[1024 * 1024];
                int n;
                while ((n = in.read(buf)) >= 0)
                    out.write(buf, 0, n);
            }
        }
    }

    // ===================== DTO =====================

    static class EnsureResult {
        final String key;
        final String installedPath;
        final String installedVersion;

        EnsureResult(String key, String installedPath, String installedVersion) {
            this.key = key;
            this.installedPath = installedPath;
            this.installedVersion = installedVersion;
        }

        JSObject toJs() {
            JSObject o = new JSObject();
            o.put("key", key);
            o.put("installedPath", installedPath);
            if (installedVersion != null)
                o.put("installedVersion", installedVersion);
            return o;
        }
    }
}

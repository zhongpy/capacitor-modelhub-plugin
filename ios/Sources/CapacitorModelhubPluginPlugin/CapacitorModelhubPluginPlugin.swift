import Foundation
import Capacitor
import SSZipArchive
import CommonCrypto

/**
 * Please read the Capacitor iOS Plugin Development Guide
 * here: https://capacitorjs.com/docs/plugins/ios
 */
@objc(CapacitorModelhubPluginPlugin)
public class CapacitorModelhubPluginPlugin: CAPPlugin, CAPBridgedPlugin {
    public let identifier = "CapacitorModelhubPluginPlugin"
    public let jsName = "CapacitorModelhubPlugin"
    public let pluginMethods: [CAPPluginMethod] = [
        CAPPluginMethod(name: "echo", returnType: CAPPluginReturnPromise)
    ]
    private let implementation = CapacitorModelhubPlugin()

    @objc func echo(_ call: CAPPluginCall) {
        let value = call.getString("value") ?? ""
        call.resolve([
            "value": implementation.echo(value)
        ])
    }
    @objc public func getRoot(_ call: CAPPluginCall) {
        let root = ensureRoot()
        call.resolve(["path": root.path])
    }

    @objc public func getPath(_ call: CAPPluginCall) {
        let unpackTo = call.getString("unpackTo") ?? ""
        let root = ensureRoot()
        let dir = root.appendingPathComponent(safeRel(unpackTo), isDirectory: true)
        call.resolve(["path": dir.path])
    }

    @objc public func check(_ call: CAPPluginCall) {
        let items = call.getArray("items", [Any]())
        let root = ensureRoot()

        var results: [[String: Any]] = []
        for any in items {
        guard let it = any as? [String: Any] else { continue }
        let key = it["key"] as? String ?? ""
        let unpackTo = it["unpackTo"] as? String ?? ""
        let checkFiles = it["checkFiles"] as? [String] ?? []

        let installed = root.appendingPathComponent(safeRel(unpackTo), isDirectory: true)
        let status = checkInstalled(installed, checkFiles)

        // iOS bundled zip：建议放到 main bundle（同样由构建决定是否包含）
        let hasBundled = bundleHasZip(key)

        results.append([
            "key": key,
            "installedPath": installed.path,
            "hasBundledZip": hasBundled,
            "status": status
        ])
        }
        call.resolve(["results": results])
    }

    @objc public func ensureInstalled(_ call: CAPPluginCall) {
        DispatchQueue.global(qos: .userInitiated).async {
        do {
            guard let item = call.getObject("item") else {
            call.reject("item is required"); return
            }
            let policy = call.getString("policy") ?? "bundleThenDownload"
            let key = item["key"] as? String ?? ""
            let unpackTo = item["unpackTo"] as? String ?? ""
            let password = item["password"] as? String ?? ""
            let sha256 = item["sha256"] as? String ?? ""
            let remoteUrl = item["remoteUrl"] as? String ?? ""
            let checkFiles = item["checkFiles"] as? [String] ?? []

            if key.isEmpty || unpackTo.isEmpty { call.reject("key/unpackTo is required"); return }

            let root = self.ensureRoot()
            let installedDir = root.appendingPathComponent(self.safeRel(unpackTo), isDirectory: true)

            self.emit(key, "checking", nil, nil, nil, "checking installed")
            if self.checkInstalled(installedDir, checkFiles) == "installed" {
            call.resolve(["key": key, "installedPath": installedDir.path]); return
            }

            let hasBundled = self.bundleHasZip(key)

            if policy != "downloadOnly", hasBundled {
            self.emit(key, "copying", nil, nil, nil, "copying bundled zip")
            let zipURL = try self.copyBundledZipToTmp(key)
            if !sha256.isEmpty {
                self.emit(key, "verifying", nil, nil, nil, "sha256 verifying")
                let got = try self.sha256File(zipURL)
                if got.lowercased() != sha256.lowercased() {
                try? FileManager.default.removeItem(at: zipURL)
                throw NSError(domain: "ModelsHub", code: -2, userInfo: [NSLocalizedDescriptionKey: "SHA256 mismatch (bundled)"])
                }
            }
            try self.installFromZip(key: key, zipURL: zipURL, installedDir: installedDir, password: password, checkFiles: checkFiles)
            call.resolve(["key": key, "installedPath": installedDir.path]); return
            }

            if policy == "bundleOnly" {
            call.reject("MODEL_MISSING_BUNDLED"); return
            }
            if remoteUrl.isEmpty {
            call.reject("MODEL_MISSING_REMOTE_URL"); return
            }

            let zipURL = try self.downloadZipToTmp(key: key, urlStr: remoteUrl)
            if !sha256.isEmpty {
            self.emit(key, "verifying", nil, nil, nil, "sha256 verifying")
            let got = try self.sha256File(zipURL)
            if got.lowercased() != sha256.lowercased() {
                try? FileManager.default.removeItem(at: zipURL)
                throw NSError(domain: "ModelsHub", code: -3, userInfo: [NSLocalizedDescriptionKey: "SHA256 mismatch (downloaded)"])
            }
            }
            try self.installFromZip(key: key, zipURL: zipURL, installedDir: installedDir, password: password, checkFiles: checkFiles)
            call.resolve(["key": key, "installedPath": installedDir.path])
        } catch {
            call.reject("ensureInstalled error: \(error.localizedDescription)")
        }
        }
    }

    // MARK: - Root

    private func ensureRoot() -> URL {
        let lib = FileManager.default.urls(for: .libraryDirectory, in: .userDomainMask)[0]
        let root = lib.appendingPathComponent("models", isDirectory: true)
        if !FileManager.default.fileExists(atPath: root.path) {
        try? FileManager.default.createDirectory(at: root, withIntermediateDirectories: true)
        }
        return root
    }

    private func safeRel(_ rel: String) -> String {
        var s = rel.replacingOccurrences(of: "\\", with: "/")
        while s.hasPrefix("/") { s.removeFirst() }
        s = s.replacingOccurrences(of: "..", with: "")
        return s
    }

    // MARK: - Check

    private func checkInstalled(_ dir: URL, _ checkFiles: [String]) -> String {
        var isDir: ObjCBool = false
        guard FileManager.default.fileExists(atPath: dir.path, isDirectory: &isDir), isDir.boolValue else {
        return "missing"
        }
        if checkFiles.isEmpty { return "installed" }
        for rel in checkFiles {
        let f = dir.appendingPathComponent(rel)
        if !FileManager.default.fileExists(atPath: f.path) { return "corrupt" }
        // 可选：大小阈值
        if let attr = try? FileManager.default.attributesOfItem(atPath: f.path),
            let size = attr[.size] as? NSNumber, size.intValue < 16 {
            return "corrupt"
        }
        }
        return "installed"
    }

    // MARK: - Bundled zip

    private func bundleHasZip(_ key: String) -> Bool {
        // 你最终希望 iOS 的 zip 也同样是 models/<key>.zip 放进 main bundle
        return Bundle.main.url(forResource: key, withExtension: "zip", subdirectory: "models") != nil
    }

    private func copyBundledZipToTmp(_ key: String) throws -> URL {
        guard let src = Bundle.main.url(forResource: key, withExtension: "zip", subdirectory: "models") else {
        throw NSError(domain: "ModelsHub", code: -10, userInfo: [NSLocalizedDescriptionKey: "bundled zip not found"])
        }
        let root = ensureRoot()
        let tmp = root.appendingPathComponent("_tmp", isDirectory: true)
        try? FileManager.default.createDirectory(at: tmp, withIntermediateDirectories: true)
        let dst = tmp.appendingPathComponent("\(key).zip")
        try? FileManager.default.removeItem(at: dst)
        try FileManager.default.copyItem(at: src, to: dst)
        return dst
    }

    // MARK: - Download (V1: foreground)

    private func downloadZipToTmp(key: String, urlStr: String) throws -> URL {
        let root = ensureRoot()
        let tmp = root.appendingPathComponent("_tmp", isDirectory: true)
        try? FileManager.default.createDirectory(at: tmp, withIntermediateDirectories: true)
        let dst = tmp.appendingPathComponent("\(key).zip")
        try? FileManager.default.removeItem(at: dst)

        guard let url = URL(string: urlStr) else {
        throw NSError(domain: "ModelsHub", code: -11, userInfo: [NSLocalizedDescriptionKey: "invalid url"])
        }

        self.emit(key, "downloading", 0, 0, 0, "starting download")

        // 简化：V1 用 Data(contentsOf:)（大文件建议换 URLSession + stream + progress）
        let data = try Data(contentsOf: url)
        try data.write(to: dst, options: .atomic)

        self.emit(key, "downloading", Int64(data.count), Int64(data.count), 1.0, "download complete")
        return dst
    }

    // MARK: - Install

    private func installFromZip(key: String, zipURL: URL, installedDir: URL, password: String, checkFiles: [String]) throws {
        let root = ensureRoot()
        let tmpRoot = root.appendingPathComponent("_tmp", isDirectory: true)
        try? FileManager.default.createDirectory(at: tmpRoot, withIntermediateDirectories: true)

        let unpackDir = tmpRoot.appendingPathComponent("unpack_\(key)", isDirectory: true)
        try? FileManager.default.removeItem(at: unpackDir)
        try FileManager.default.createDirectory(at: unpackDir, withIntermediateDirectories: true)

        self.emit(key, "unpacking", nil, nil, nil, "unpacking zip")

        var err: NSError?
        let ok = SSZipArchive.unzipFile(atPath: zipURL.path,
                                    toDestination: unpackDir.path,
                                    overwrite: true,
                                    password: password.isEmpty ? nil : password,
                                    error: &err,
                                    delegate: nil)

        if !ok {
        throw NSError(domain: "ModelsHub", code: -20, userInfo: [NSLocalizedDescriptionKey: err?.localizedDescription ?? "unzip failed"])
        }

        if self.checkInstalled(unpackDir, checkFiles) != "installed" {
        throw NSError(domain: "ModelsHub", code: -21, userInfo: [NSLocalizedDescriptionKey: "unpacked content invalid"])
        }

        self.emit(key, "finalizing", nil, nil, nil, "finalizing")
        try? FileManager.default.removeItem(at: installedDir)
        try FileManager.default.moveItem(at: unpackDir, to: installedDir)

        try? FileManager.default.removeItem(at: zipURL)
    }

    // MARK: - sha256

    private func sha256File(_ url: URL) throws -> String {
        let fh = try FileHandle(forReadingFrom: url)
        defer { try? fh.close() }

        var ctx = CC_SHA256_CTX()
        CC_SHA256_Init(&ctx)

        while true {
        let data = fh.readData(ofLength: 1024 * 1024)
        if data.isEmpty { break }
        data.withUnsafeBytes { ptr in
            _ = CC_SHA256_Update(&ctx, ptr.baseAddress, CC_LONG(data.count))
        }
        }

        var digest = [UInt8](repeating: 0, count: Int(CC_SHA256_DIGEST_LENGTH))
        CC_SHA256_Final(&digest, &ctx)
        return digest.map { String(format: "%02x", $0) }.joined()
    }

    // MARK: - Events

    private func emit(_ key: String, _ phase: String, _ downloaded: Int64?, _ total: Int64?, _ progress: Double?, _ message: String?) {
        var ev: [String: Any] = ["key": key, "phase": phase]
        if let d = downloaded { ev["downloaded"] = d }
        if let t = total { ev["total"] = t }
        if let p = progress { ev["progress"] = p }
        if let m = message { ev["message"] = m }
        notifyListeners("ModelsHubProgress", data: ev)
    }
}

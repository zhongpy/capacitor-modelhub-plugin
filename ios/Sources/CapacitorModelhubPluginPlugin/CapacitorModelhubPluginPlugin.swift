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
    private let stateFileName = "state.json"

  // key -> call & metadata
  private var downloadTasks: [Int: (key: String, call: CAPPluginCall?, expectedSha: String, version: String, unpackTo: String, password: String, checkFiles: [String])] = [:]

  // MARK: - APIs

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
    let state = readState(root)

    var results: [[String: Any]] = []

    for any in items {
      guard let it = any as? [String: Any] else { continue }
      let key = it["key"] as? String ?? ""
      let unpackTo = it["unpackTo"] as? String ?? ""
      let checkFiles = it["checkFiles"] as? [String] ?? []

      let installed = root.appendingPathComponent(safeRel(unpackTo), isDirectory: true)
      let status = checkInstalled(installed, checkFiles)
      let hasBundled = bundleHasZip(key)

      var r: [String: Any] = [
        "key": key,
        "installedPath": installed.path,
        "hasBundledZip": hasBundled,
        "status": status
      ]
      if let rec = state[key] { r["state"] = rec }
      results.append(r)
    }

    call.resolve(["results": results])
  }

  @objc public func ensureInstalled(_ call: CAPPluginCall) {
    DispatchQueue.global(qos: .userInitiated).async {
      do {
        guard let item = call.getObject("item") else { call.reject("item is required"); return }
        let policy = call.getString("policy") ?? "bundleThenDownload"
        let r = try self.ensureOne(item, policy)
        call.resolve(r)
      } catch {
        call.reject("ensureInstalled error: \(error.localizedDescription)")
      }
    }
  }

  @objc public func ensureInstalledMany(_ call: CAPPluginCall) {
    DispatchQueue.global(qos: .userInitiated).async {
      do {
        let items = call.getArray("items", [Any]())
        let policy = call.getString("policy") ?? "bundleThenDownload"
        var arr: [[String: Any]] = []
        for any in items {
          guard let it = any as? [String: Any] else { continue }
          let r = try self.ensureOne(it, policy)
          arr.append(r)
        }
        call.resolve(["results": arr])
      } catch {
        call.reject("ensureInstalledMany error: \(error.localizedDescription)")
      }
    }
  }

  // MARK: - Ensure Core

  private func ensureOne(_ item: [String: Any], _ policy: String) throws -> [String: Any] {
    let key = item["key"] as? String ?? ""
    let unpackTo = item["unpackTo"] as? String ?? ""
    let password = item["password"] as? String ?? ""
    let sha256 = item["sha256"] as? String ?? ""
    let remoteUrl = item["remoteUrl"] as? String ?? ""
    let version = item["version"] as? String ?? ""
    let checkFiles = item["checkFiles"] as? [String] ?? []

    if key.isEmpty || unpackTo.isEmpty { throw err("key/unpackTo is required") }

    let root = ensureRoot()
    let installedDir = root.appendingPathComponent(safeRel(unpackTo), isDirectory: true)

    emit(key, "checking", nil, nil, nil, "checking installed")
    if checkInstalled(installedDir, checkFiles) == "installed" {
      return ["key": key, "installedPath": installedDir.path, "installedVersion": version]
    }

    let hasBundled = bundleHasZip(key)

    // 1) bundled
    if policy != "downloadOnly", hasBundled {
      let zipURL = try copyBundledZipToTmp(key)
      let zipSize = (try? FileManager.default.attributesOfItem(atPath: zipURL.path)[.size] as? NSNumber)?.int64Value ?? 0

      if !sha256.isEmpty {
        emit(key, "verifying", nil, nil, nil, "sha256 verifying")
        let got = try sha256File(zipURL)
        if got.lowercased() != sha256.lowercased() {
          try? FileManager.default.removeItem(at: zipURL)
          throw err("SHA256 mismatch for bundled zip, expected=\(sha256) got=\(got)")
        }
      }

      try installFromZip(key: key, zipURL: zipURL, installedDir: installedDir, password: password, checkFiles: checkFiles)
      writeStateRecord(root: root, key: key, version: version, sha256: sha256, zipSize: zipSize, unpackTo: unpackTo)

      emit(key, "done", nil, nil, 1.0, "installed")
      return ["key": key, "installedPath": installedDir.path, "installedVersion": version]
    }

    if policy == "bundleOnly" { throw err("MODEL_MISSING_BUNDLED") }
    if remoteUrl.isEmpty { throw err("MODEL_MISSING_REMOTE_URL") }

    // 2) download: 这里改为 URLSession 流式下载 + 进度
    // 为了保持 ensureOne 同步语义：用 semaphore 等待完成
    let sem = DispatchSemaphore(value: 0)
    var downloadedZipURL: URL?
    var downloadedZipSize: Int64 = 0
    var downloadError: Error?

    emit(key, "downloading", 0, 0, 0, "starting download")
    let session = URLSession(configuration: .default, delegate: self, delegateQueue: nil)

    guard let url = URL(string: remoteUrl) else { throw err("invalid remoteUrl") }

    let task = session.downloadTask(with: url)
    downloadTasks[task.taskIdentifier] = (key: key, call: nil, expectedSha: sha256, version: version, unpackTo: unpackTo, password: password, checkFiles: checkFiles)
    task.resume()

    // 将下载结果通过临时变量返回
    // 通过通知：download delegate 完成后写入文件并校验，然后发信号（下面 delegate 里实现）
    // 这里用一个 map：taskIdentifier -> completion closures 更优；为简洁采用 NotificationCenter
    let token = NotificationCenter.default.addObserver(forName: NSNotification.Name("ModelsHubDownloadDone_\(task.taskIdentifier)"), object: nil, queue: nil) { n in
      if let u = n.userInfo?["url"] as? URL { downloadedZipURL = u }
      if let s = n.userInfo?["size"] as? Int64 { downloadedZipSize = s }
      if let e = n.userInfo?["error"] as? Error { downloadError = e }
      sem.signal()
    }

    _ = sem.wait(timeout: .distantFuture)
    NotificationCenter.default.removeObserver(token)

    if let e = downloadError { throw e }
    guard let zipURL = downloadedZipURL else { throw err("download failed") }

    // 校验 sha（如果要求）
    if !sha256.isEmpty {
      emit(key, "verifying", nil, nil, nil, "sha256 verifying")
      let got = try sha256File(zipURL)
      if got.lowercased() != sha256.lowercased() {
        try? FileManager.default.removeItem(at: zipURL)
        throw err("SHA256 mismatch for downloaded zip, expected=\(sha256) got=\(got)")
      }
    }

    try installFromZip(key: key, zipURL: zipURL, installedDir: installedDir, password: password, checkFiles: checkFiles)
    writeStateRecord(root: root, key: key, version: version, sha256: sha256, zipSize: downloadedZipSize, unpackTo: unpackTo)

    emit(key, "done", nil, nil, 1.0, "installed")
    return ["key": key, "installedPath": installedDir.path, "installedVersion": version]
  }

  // MARK: - URLSessionDownloadDelegate (progress + completion)

  public func urlSession(_ session: URLSession, downloadTask: URLSessionDownloadTask,
                         didWriteData bytesWritten: Int64, totalBytesWritten: Int64, totalBytesExpectedToWrite: Int64) {
    guard let meta = downloadTasks[downloadTask.taskIdentifier] else { return }
    let p = totalBytesExpectedToWrite > 0 ? Double(totalBytesWritten) / Double(totalBytesExpectedToWrite) : 0
    emit(meta.key, "downloading", totalBytesWritten, totalBytesExpectedToWrite, p, nil)
  }

  public func urlSession(_ session: URLSession, downloadTask: URLSessionDownloadTask,
                         didFinishDownloadingTo location: URL) {
    guard let meta = downloadTasks[downloadTask.taskIdentifier] else { return }
    let root = ensureRoot()
    let tmpDir = root.appendingPathComponent("_tmp", isDirectory: true)
    try? FileManager.default.createDirectory(at: tmpDir, withIntermediateDirectories: true)
    let dst = tmpDir.appendingPathComponent("\(meta.key).zip")

    do {
      try? FileManager.default.removeItem(at: dst)
      try FileManager.default.moveItem(at: location, to: dst)

      let zipSize = (try? FileManager.default.attributesOfItem(atPath: dst.path)[.size] as? NSNumber)?.int64Value ?? 0
      emit(meta.key, "downloading", zipSize, zipSize, 1.0, "download complete")

      NotificationCenter.default.post(
        name: NSNotification.Name("ModelsHubDownloadDone_\(downloadTask.taskIdentifier)"),
        object: nil,
        userInfo: ["url": dst, "size": zipSize]
      )
    } catch {
      NotificationCenter.default.post(
        name: NSNotification.Name("ModelsHubDownloadDone_\(downloadTask.taskIdentifier)"),
        object: nil,
        userInfo: ["error": error]
      )
    }

    downloadTasks.removeValue(forKey: downloadTask.taskIdentifier)
  }

  public func urlSession(_ session: URLSession, task: URLSessionTask, didCompleteWithError error: Error?) {
    if let error = error, downloadTasks[task.taskIdentifier] != nil {
      NotificationCenter.default.post(
        name: NSNotification.Name("ModelsHubDownloadDone_\(task.taskIdentifier)"),
        object: nil,
        userInfo: ["error": error]
      )
      downloadTasks.removeValue(forKey: task.taskIdentifier)
    }
  }

  // MARK: - Install from zip

  private func installFromZip(key: String, zipURL: URL, installedDir: URL, password: String, checkFiles: [String]) throws {
    let root = ensureRoot()
    let tmpRoot = root.appendingPathComponent("_tmp", isDirectory: true)
    try? FileManager.default.createDirectory(at: tmpRoot, withIntermediateDirectories: true)

    let unpackDir = tmpRoot.appendingPathComponent("unpack_\(key)", isDirectory: true)
    try? FileManager.default.removeItem(at: unpackDir)
    try FileManager.default.createDirectory(at: unpackDir, withIntermediateDirectories: true)

    emit(key, "unpacking", nil, nil, nil, "unpacking zip")
    var errObj: NSError?
    let ok = SSZipArchive.unzipFile(atPath: zipURL.path,
                                   toDestination: unpackDir.path,
                                   overwrite: true,
                                   password: password.isEmpty ? nil : password,
                                   error: &errObj,
                                   delegate: nil)
    if !ok {
      throw err("unzip failed: \(errObj?.localizedDescription ?? "")")
    }

    if checkInstalled(unpackDir, checkFiles) != "installed" {
      throw err("unpacked content invalid")
    }

    emit(key, "finalizing", nil, nil, nil, "finalizing")
    try? FileManager.default.removeItem(at: installedDir)
    try FileManager.default.moveItem(at: unpackDir, to: installedDir)

    try? FileManager.default.removeItem(at: zipURL)
  }

  // MARK: - Root & State

  private func ensureRoot() -> URL {
    let lib = FileManager.default.urls(for: .libraryDirectory, in: .userDomainMask)[0]
    let root = lib.appendingPathComponent("models", isDirectory: true)
    if !FileManager.default.fileExists(atPath: root.path) {
      try? FileManager.default.createDirectory(at: root, withIntermediateDirectories: true)
    }
    return root
  }

  private func stateFileURL(_ root: URL) -> URL {
    return root.appendingPathComponent(stateFileName, isDirectory: false)
  }

  private func readState(_ root: URL) -> [String: Any] {
    let f = stateFileURL(root)
    guard let data = try? Data(contentsOf: f), !data.isEmpty else { return [:] }
    return (try? JSONSerialization.jsonObject(with: data) as? [String: Any]) ?? [:]
  }

  private func writeStateRecord(root: URL, key: String, version: String, sha256: String, zipSize: Int64, unpackTo: String) {
    var state = readState(root)
    state[key] = [
      "installedVersion": version,
      "sha256": sha256,
      "zipSize": zipSize,
      "unpackTo": unpackTo,
      "installedAt": Int(Date().timeIntervalSince1970 * 1000)
    ]

    do {
      let data = try JSONSerialization.data(withJSONObject: state, options: [])
      let f = stateFileURL(root)
      let tmp = root.appendingPathComponent(stateFileName + ".tmp")
      try data.write(to: tmp, options: .atomic)
      try? FileManager.default.removeItem(at: f)
      try FileManager.default.moveItem(at: tmp, to: f)
    } catch { /* ignore */ }
  }

  // MARK: - Check installed

  private func checkInstalled(_ dir: URL, _ checkFiles: [String]) -> String {
    var isDir: ObjCBool = false
    guard FileManager.default.fileExists(atPath: dir.path, isDirectory: &isDir), isDir.boolValue else {
      return "missing"
    }
    if checkFiles.isEmpty { return "installed" }
    for rel in checkFiles {
      let f = dir.appendingPathComponent(rel)
      if !FileManager.default.fileExists(atPath: f.path) { return "corrupt" }
      if let attr = try? FileManager.default.attributesOfItem(atPath: f.path),
         let size = attr[.size] as? NSNumber, size.intValue < 16 {
        return "corrupt"
      }
    }
    return "installed"
  }

  // MARK: - Bundled zip in app bundle

  private func bundleHasZip(_ key: String) -> Bool {
    return Bundle.main.url(forResource: key, withExtension: "zip", subdirectory: "models") != nil
  }

  private func copyBundledZipToTmp(_ key: String) throws -> URL {
    guard let src = Bundle.main.url(forResource: key, withExtension: "zip", subdirectory: "models") else {
      throw err("bundled zip not found")
    }
    let root = ensureRoot()
    let tmp = root.appendingPathComponent("_tmp", isDirectory: true)
    try? FileManager.default.createDirectory(at: tmp, withIntermediateDirectories: true)
    let dst = tmp.appendingPathComponent("\(key).zip")
    try? FileManager.default.removeItem(at: dst)
    try FileManager.default.copyItem(at: src, to: dst)
    return dst
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

  // MARK: - Events & helpers

  private func emit(_ key: String, _ phase: String, _ downloaded: Int64?, _ total: Int64?, _ progress: Double?, _ message: String?) {
    var ev: [String: Any] = ["key": key, "phase": phase]
    if let d = downloaded { ev["downloaded"] = d }
    if let t = total { ev["total"] = t }
    if let p = progress { ev["progress"] = p }
    if let m = message { ev["message"] = m }
    notifyListeners("ModelsHubProgress", data: ev)
  }

  private func safeRel(_ rel: String) -> String {
    var s = rel.replacingOccurrences(of: "\\", with: "/")
    while s.hasPrefix("/") { s.removeFirst() }
    s = s.replacingOccurrences(of: "..", with: "")
    return s
  }

  private func err(_ msg: String) -> NSError {
    return NSError(domain: "ModelsHub", code: -1, userInfo: [NSLocalizedDescriptionKey: msg])
  }
}

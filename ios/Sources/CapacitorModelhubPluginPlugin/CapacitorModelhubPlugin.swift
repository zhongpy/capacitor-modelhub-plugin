import Foundation

@objc public class CapacitorModelhubPlugin: NSObject {
    @objc public func echo(_ value: String) -> String {
        print(value)
        return value
    }
}

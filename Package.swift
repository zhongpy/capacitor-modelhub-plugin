// swift-tools-version: 5.9
import PackageDescription

let package = Package(
    name: "CapacitorModelhubPlugin",
    platforms: [.iOS(.v15)],
    products: [
        .library(
            name: "CapacitorModelhubPlugin",
            targets: ["CapacitorModelhubPluginPlugin"])
    ],
    dependencies: [
        .package(url: "https://github.com/ionic-team/capacitor-swift-pm.git", from: "8.0.0")
    ],
    targets: [
        .target(
            name: "CapacitorModelhubPluginPlugin",
            dependencies: [
                .product(name: "Capacitor", package: "capacitor-swift-pm"),
                .product(name: "Cordova", package: "capacitor-swift-pm")
            ],
            path: "ios/Sources/CapacitorModelhubPluginPlugin"),
        .testTarget(
            name: "CapacitorModelhubPluginPluginTests",
            dependencies: ["CapacitorModelhubPluginPlugin"],
            path: "ios/Tests/CapacitorModelhubPluginPluginTests")
    ]
)
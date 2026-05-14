import Foundation
import CoreGraphics
import ImageIO
import UniformTypeIdentifiers

// Android adaptive-icon foreground layers are 108dp square; artwork should fit
// inside the central 66dp safe zone (~61% of the canvas) so OEM masks do not clip.
let safeZoneFraction: CGFloat = 66.0 / 108.0

// Background matches composeApp/src/androidMain/res/values/colors.xml
let backgroundColor = CGColor(
    red: 10.0 / 255.0,
    green: 13.0 / 255.0,
    blue: 26.0 / 255.0,
    alpha: 1.0,
)

struct Density {
    let name: String
    let foregroundPx: Int
    let legacyPx: Int
}

struct NotificationDensity {
    let name: String
    let sizePx: Int
}

let densities: [Density] = [
    .init(name: "mipmap-mdpi", foregroundPx: 108, legacyPx: 48),
    .init(name: "mipmap-hdpi", foregroundPx: 162, legacyPx: 72),
    .init(name: "mipmap-xhdpi", foregroundPx: 216, legacyPx: 96),
    .init(name: "mipmap-xxhdpi", foregroundPx: 324, legacyPx: 144),
    .init(name: "mipmap-xxxhdpi", foregroundPx: 432, legacyPx: 192),
]

// Status-bar notification icons are 24dp; use full-bleed artwork (no adaptive safe zone).
let notificationDensities: [NotificationDensity] = [
    .init(name: "drawable-mdpi", sizePx: 24),
    .init(name: "drawable-hdpi", sizePx: 36),
    .init(name: "drawable-xhdpi", sizePx: 48),
    .init(name: "drawable-xxhdpi", sizePx: 72),
    .init(name: "drawable-xxxhdpi", sizePx: 96),
]

func loadImage(_ path: String) -> CGImage {
    let url = URL(fileURLWithPath: path)
    guard let source = CGImageSourceCreateWithURL(url as CFURL, nil),
          let image = CGImageSourceCreateImageAtIndex(source, 0, nil) else {
        fatalError("could not load image at \(path)")
    }
    return image
}

func writePNG(_ image: CGImage, to path: String) {
    let url = URL(fileURLWithPath: path)
    let type = UTType.png.identifier as CFString
    guard let dest = CGImageDestinationCreateWithURL(url as CFURL, type, 1, nil) else {
        fatalError("could not create destination for \(path)")
    }
    CGImageDestinationAddImage(dest, image, nil)
    if !CGImageDestinationFinalize(dest) {
        fatalError("could not finalize \(path)")
    }
}

func renderForeground(source: CGImage, size: Int) -> CGImage {
    let canvas = CGFloat(size)
    let content = canvas * safeZoneFraction
    let inset = (canvas - content) / 2
    let colorSpace = CGColorSpaceCreateDeviceRGB()
    let context = CGContext(
        data: nil,
        width: size,
        height: size,
        bitsPerComponent: 8,
        bytesPerRow: 0,
        space: colorSpace,
        bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue,
    )!
    context.clear(CGRect(x: 0, y: 0, width: canvas, height: canvas))
    context.interpolationQuality = .high
    context.draw(
        source,
        in: CGRect(x: inset, y: inset, width: content, height: content),
    )
    return context.makeImage()!
}

func renderNotificationIcon(source: CGImage, size: Int) -> CGImage {
    let canvas = CGFloat(size)
    let colorSpace = CGColorSpaceCreateDeviceRGB()
    let context = CGContext(
        data: nil,
        width: size,
        height: size,
        bitsPerComponent: 8,
        bytesPerRow: 0,
        space: colorSpace,
        bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue,
    )!
    context.clear(CGRect(x: 0, y: 0, width: canvas, height: canvas))
    context.interpolationQuality = .high
    context.draw(
        source,
        in: CGRect(x: 0, y: 0, width: canvas, height: canvas),
    )
    return context.makeImage()!
}

func renderLegacyIcon(source: CGImage, size: Int) -> CGImage {
    let canvas = CGFloat(size)
    let content = canvas * safeZoneFraction
    let inset = (canvas - content) / 2
    let colorSpace = CGColorSpaceCreateDeviceRGB()
    let context = CGContext(
        data: nil,
        width: size,
        height: size,
        bitsPerComponent: 8,
        bytesPerRow: 0,
        space: colorSpace,
        bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue,
    )!
    context.setFillColor(backgroundColor)
    context.fill(CGRect(x: 0, y: 0, width: canvas, height: canvas))
    context.interpolationQuality = .high
    context.draw(
        source,
        in: CGRect(x: inset, y: inset, width: content, height: content),
    )
    return context.makeImage()!
}

let repoRoot = URL(fileURLWithPath: CommandLine.arguments[0])
    .deletingLastPathComponent()
    .deletingLastPathComponent()
let sourcePath = repoRoot
    .appendingPathComponent("branding/icon-foreground.png")
    .path
let resRoot = repoRoot
    .appendingPathComponent("composeApp/src/androidMain/res")
    .path

let source = loadImage(sourcePath)

for density in densities {
    let dir = (resRoot as NSString).appendingPathComponent(density.name)
    try FileManager.default.createDirectory(atPath: dir, withIntermediateDirectories: true)

    let foregroundPath = (dir as NSString).appendingPathComponent("ic_launcher_foreground.png")
    writePNG(renderForeground(source: source, size: density.foregroundPx), to: foregroundPath)

    let legacyPath = (dir as NSString).appendingPathComponent("ic_launcher.png")
    writePNG(renderLegacyIcon(source: source, size: density.legacyPx), to: legacyPath)

    let roundPath = (dir as NSString).appendingPathComponent("ic_launcher_round.png")
    writePNG(renderLegacyIcon(source: source, size: density.legacyPx), to: roundPath)

    print("wrote \(density.name)")
}

for density in notificationDensities {
    let dir = (resRoot as NSString).appendingPathComponent(density.name)
    try FileManager.default.createDirectory(atPath: dir, withIntermediateDirectories: true)

    let notificationPath = (dir as NSString).appendingPathComponent("ic_notification.png")
    writePNG(renderNotificationIcon(source: source, size: density.sizePx), to: notificationPath)

    print("wrote \(density.name)/ic_notification.png")
}

print("done")

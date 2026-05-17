import AppKit
import Foundation
import CoreGraphics
import ImageIO
import UniformTypeIdentifiers

// Debug icons match production (navy + bird) with a Flutter-style diagonal corner banner.
let productionBackgroundColor = CGColor(
    red: 10.0 / 255.0,
    green: 13.0 / 255.0,
    blue: 26.0 / 255.0,
    alpha: 1.0,
)
// Material debug banner red (Flutter `Banner` default).
let debugBannerFillColor = CGColor(
    red: 183.0 / 255.0,
    green: 28.0 / 255.0,
    blue: 28.0 / 255.0,
    alpha: 1.0,
)
let safeZoneFraction: CGFloat = 66.0 / 108.0

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

let notificationDensities: [NotificationDensity] = [
    .init(name: "drawable-mdpi", sizePx: 24),
    .init(name: "drawable-hdpi", sizePx: 36),
    .init(name: "drawable-xhdpi", sizePx: 48),
    .init(name: "drawable-xxhdpi", sizePx: 72),
    .init(name: "drawable-xxxhdpi", sizePx: 96),
]

let iosIconSpecs: [(filename: String, size: Int)] = [
    ("Icon-20.png", 20),
    ("Icon-20@2x.png", 40),
    ("Icon-20@3x.png", 60),
    ("Icon-20@2x-ipad.png", 40),
    ("Icon-29.png", 29),
    ("Icon-29@2x.png", 58),
    ("Icon-29@3x.png", 87),
    ("Icon-29@2x-ipad.png", 58),
    ("Icon-40.png", 40),
    ("Icon-40@2x.png", 80),
    ("Icon-40@3x.png", 120),
    ("Icon-40@2x-ipad.png", 80),
    ("Icon-60@2x.png", 120),
    ("Icon-60@3x.png", 180),
    ("Icon-76.png", 76),
    ("Icon-76@2x.png", 152),
    ("Icon-83.5@2x.png", 167),
    ("Icon-1024.png", 1024),
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

/// Matches Flutter `BannerPainter` for `BannerLocation.topStart` + LTR:
/// rect `(-offset, offset - height, 2×offset, height)` then rotate −45° at the top-left.
func drawDebugBanner(context: CGContext, canvas: CGFloat) {
    context.saveGState()
    context.translateBy(x: 0, y: canvas)
    context.scaleBy(x: 1, y: -1)

    // Same proportions as Flutter `_kHeight` / `_kOffset` (12 / 40), scaled to icon size.
    let ribbonHeight = max(3, canvas * (12.0 / 96.0))
    let offset = ribbonHeight * (40.0 / 12.0)
    let ribbon = CGRect(
        x: -offset,
        y: offset - ribbonHeight,
        width: offset * 2,
        height: ribbonHeight,
    )

    context.rotate(by: -.pi / 4)

    if canvas >= 32 {
        context.setFillColor(CGColor(red: 0, green: 0, blue: 0, alpha: 0.28))
        context.fill(ribbon.offsetBy(dx: 0.8, dy: 0.8))
    }

    context.setFillColor(debugBannerFillColor)
    context.fill(ribbon)

    if canvas >= 20 {
        let fontSize = max(4, ribbonHeight * 0.85)
        let attrs: [NSAttributedString.Key: Any] = [
            .font: NSFont.boldSystemFont(ofSize: fontSize),
            .foregroundColor: NSColor.white,
        ]
        let text = NSAttributedString(string: "DEBUG", attributes: attrs)
        let textSize = text.size()
        text.draw(
            at: CGPoint(
                x: ribbon.minX,
                y: ribbon.minY + (ribbon.height - textSize.height) / 2,
            ),
        )
    }

    context.restoreGState()
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
    context.setFillColor(productionBackgroundColor)
    context.fill(CGRect(x: 0, y: 0, width: canvas, height: canvas))
    context.interpolationQuality = .high
    context.draw(
        source,
        in: CGRect(x: inset, y: inset, width: content, height: content),
    )
    return context.makeImage()!
}

func renderDebugForeground(source: CGImage, size: Int) -> CGImage {
    let base = renderForeground(source: source, size: size)
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
    context.draw(base, in: CGRect(x: 0, y: 0, width: canvas, height: canvas))
    drawDebugBanner(context: context, canvas: canvas)
    return context.makeImage()!
}

func renderDebugLegacyIcon(source: CGImage, size: Int) -> CGImage {
    let base = renderLegacyIcon(source: source, size: size)
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
    context.draw(base, in: CGRect(x: 0, y: 0, width: canvas, height: canvas))
    drawDebugBanner(context: context, canvas: canvas)
    return context.makeImage()!
}

let repoRoot = URL(fileURLWithPath: CommandLine.arguments[0])
    .deletingLastPathComponent()
    .deletingLastPathComponent()
let sourcePath = repoRoot.appendingPathComponent("branding/icon-foreground.png").path
let source = loadImage(sourcePath)

let brandingDebugForeground = repoRoot.appendingPathComponent("branding/icon-foreground-debug.png").path
writePNG(renderDebugForeground(source: source, size: 1024), to: brandingDebugForeground)

let brandingRoundedDebug = repoRoot.appendingPathComponent("branding/icon-rounded-debug.png").path
writePNG(renderDebugLegacyIcon(source: source, size: 1024), to: brandingRoundedDebug)

let androidDebugRes = repoRoot.appendingPathComponent("composeApp/src/debug/res").path
for density in densities {
    let dir = (androidDebugRes as NSString).appendingPathComponent(density.name)
    try FileManager.default.createDirectory(atPath: dir, withIntermediateDirectories: true)
    writePNG(
        renderDebugForeground(source: source, size: density.foregroundPx),
        to: (dir as NSString).appendingPathComponent("ic_launcher_foreground.png"),
    )
    writePNG(
        renderDebugLegacyIcon(source: source, size: density.legacyPx),
        to: (dir as NSString).appendingPathComponent("ic_launcher.png"),
    )
    writePNG(
        renderDebugLegacyIcon(source: source, size: density.legacyPx),
        to: (dir as NSString).appendingPathComponent("ic_launcher_round.png"),
    )
    print("wrote debug \(density.name)")
}

for density in notificationDensities {
    let dir = (androidDebugRes as NSString).appendingPathComponent(density.name)
    try FileManager.default.createDirectory(atPath: dir, withIntermediateDirectories: true)
    writePNG(
        renderDebugLegacyIcon(source: source, size: density.sizePx),
        to: (dir as NSString).appendingPathComponent("ic_notification.png"),
    )
}

let iosDebugIconDir = repoRoot
    .appendingPathComponent("iosApp/iosApp/Assets.xcassets/AppIcon-Debug.appiconset")
    .path
try FileManager.default.createDirectory(atPath: iosDebugIconDir, withIntermediateDirectories: true)
for spec in iosIconSpecs {
    writePNG(
        renderDebugLegacyIcon(source: source, size: spec.size),
        to: (iosDebugIconDir as NSString).appendingPathComponent(spec.filename),
    )
}

let iosContents = """
{
  "images" : [
    { "filename" : "Icon-20@2x.png", "idiom" : "iphone", "scale" : "2x", "size" : "20x20" },
    { "filename" : "Icon-20@3x.png", "idiom" : "iphone", "scale" : "3x", "size" : "20x20" },
    { "filename" : "Icon-29@2x.png", "idiom" : "iphone", "scale" : "2x", "size" : "29x29" },
    { "filename" : "Icon-29@3x.png", "idiom" : "iphone", "scale" : "3x", "size" : "29x29" },
    { "filename" : "Icon-40@2x.png", "idiom" : "iphone", "scale" : "2x", "size" : "40x40" },
    { "filename" : "Icon-40@3x.png", "idiom" : "iphone", "scale" : "3x", "size" : "40x40" },
    { "filename" : "Icon-60@2x.png", "idiom" : "iphone", "scale" : "2x", "size" : "60x60" },
    { "filename" : "Icon-60@3x.png", "idiom" : "iphone", "scale" : "3x", "size" : "60x60" },
    { "filename" : "Icon-20.png", "idiom" : "ipad", "scale" : "1x", "size" : "20x20" },
    { "filename" : "Icon-20@2x-ipad.png", "idiom" : "ipad", "scale" : "2x", "size" : "20x20" },
    { "filename" : "Icon-29.png", "idiom" : "ipad", "scale" : "1x", "size" : "29x29" },
    { "filename" : "Icon-29@2x-ipad.png", "idiom" : "ipad", "scale" : "2x", "size" : "29x29" },
    { "filename" : "Icon-40.png", "idiom" : "ipad", "scale" : "1x", "size" : "40x40" },
    { "filename" : "Icon-40@2x-ipad.png", "idiom" : "ipad", "scale" : "2x", "size" : "40x40" },
    { "filename" : "Icon-76.png", "idiom" : "ipad", "scale" : "1x", "size" : "76x76" },
    { "filename" : "Icon-76@2x.png", "idiom" : "ipad", "scale" : "2x", "size" : "76x76" },
    { "filename" : "Icon-83.5@2x.png", "idiom" : "ipad", "scale" : "2x", "size" : "83.5x83.5" },
    { "filename" : "Icon-1024.png", "idiom" : "ios-marketing", "scale" : "1x", "size" : "1024x1024" }
  ],
  "info" : { "author" : "xcode", "version" : 1 }
}
"""
try iosContents.write(
    toFile: (iosDebugIconDir as NSString).appendingPathComponent("Contents.json"),
    atomically: true,
    encoding: .utf8,
)

let desktopIconsDebug = repoRoot
    .appendingPathComponent("composeApp/src/desktopMain/resources/icons-debug")
    .path
try FileManager.default.createDirectory(atPath: desktopIconsDebug, withIntermediateDirectories: true)
writePNG(
    renderDebugLegacyIcon(source: source, size: 512),
    to: (desktopIconsDebug as NSString).appendingPathComponent("icon.png"),
)

let desktopRootDebug = repoRoot.appendingPathComponent("composeApp/src/desktopMain/resources").path
writePNG(
    renderDebugLegacyIcon(source: source, size: 512),
    to: (desktopRootDebug as NSString).appendingPathComponent("icon-debug.png"),
)

print("done — debug branding and launcher assets generated")

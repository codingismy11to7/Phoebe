import Foundation
import CoreGraphics
import ImageIO
import UniformTypeIdentifiers
import AppKit

// macOS Big Sur+ icon template: 1024x1024 canvas, content fits inside 824x824 centered,
// rounded with corner radius 185 (matches the proportions Apple uses for first-party apps).
// Source: Apple's macOS app icon template (Sketch/Figma kits published with the HIG).
let canvas: CGFloat = 1024
let inset: CGFloat = 100       // (1024 - 824) / 2
let cornerRadius: CGFloat = 185

func loadImage(_ path: String) -> CGImage {
    let url = URL(fileURLWithPath: path)
    guard let source = CGImageSourceCreateWithURL(url as CFURL, nil),
          let image = CGImageSourceCreateImageAtIndex(source, 0, nil) else {
        fatalError("could not load image at \(path)")
    }
    return image
}

func renderMasked(source: CGImage, size: CGFloat) -> CGImage {
    let scaledInset = inset * (size / canvas)
    let scaledRadius = cornerRadius * (size / canvas)
    let contentRect = CGRect(
        x: scaledInset,
        y: scaledInset,
        width: size - scaledInset * 2,
        height: size - scaledInset * 2,
    )
    let colorSpace = CGColorSpaceCreateDeviceRGB()
    let context = CGContext(
        data: nil,
        width: Int(size),
        height: Int(size),
        bitsPerComponent: 8,
        bytesPerRow: 0,
        space: colorSpace,
        bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue,
    )!
    context.clear(CGRect(x: 0, y: 0, width: size, height: size))
    let path = CGPath(roundedRect: contentRect, cornerWidth: scaledRadius, cornerHeight: scaledRadius, transform: nil)
    context.addPath(path)
    context.clip()
    context.interpolationQuality = .high
    context.draw(source, in: contentRect)
    return context.makeImage()!
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

let args = CommandLine.arguments
guard args.count >= 4 else {
    FileHandle.standardError.write("usage: round_icon.swift <source> <output> <size>\n".data(using: .utf8)!)
    exit(1)
}
let source = loadImage(args[1])
let size = CGFloat(Double(args[3]) ?? 1024)
let masked = renderMasked(source: source, size: size)
writePNG(masked, to: args[2])
print("wrote \(args[2]) at \(Int(size))px")

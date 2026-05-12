import Cocoa
import CoreGraphics

let args = CommandLine.arguments
guard args.count >= 3 else {
    FileHandle.standardError.write("Usage: extract_foreground.swift <input> <output>\n".data(using: .utf8)!)
    exit(1)
}

let inputPath = args[1]
let outputPath = args[2]

let url = URL(fileURLWithPath: inputPath)
guard let source = CGImageSourceCreateWithURL(url as CFURL, nil),
      let cgImage = CGImageSourceCreateImageAtIndex(source, 0, nil) else {
    FileHandle.standardError.write("Could not load image at \(inputPath)\n".data(using: .utf8)!)
    exit(1)
}

let width = cgImage.width
let height = cgImage.height
let bytesPerPixel = 4
let bytesPerRow = width * bytesPerPixel
let bitmapInfo = CGImageAlphaInfo.premultipliedLast.rawValue | CGBitmapInfo.byteOrder32Big.rawValue
let colorSpace = CGColorSpaceCreateDeviceRGB()

guard let context = CGContext(
    data: nil,
    width: width,
    height: height,
    bitsPerComponent: 8,
    bytesPerRow: bytesPerRow,
    space: colorSpace,
    bitmapInfo: bitmapInfo
) else {
    FileHandle.standardError.write("Could not create bitmap context\n".data(using: .utf8)!)
    exit(1)
}

context.draw(cgImage, in: CGRect(x: 0, y: 0, width: width, height: height))
guard let buffer = context.data else {
    FileHandle.standardError.write("Could not get bitmap buffer\n".data(using: .utf8)!)
    exit(1)
}

let pixels = buffer.bindMemory(to: UInt8.self, capacity: width * height * 4)

// Sample background colour from the 4 corners (small 8x8 patches) and average them.
func samplePatch(originX: Int, originY: Int, sampleSize: Int) -> (r: Double, g: Double, b: Double) {
    var r: Double = 0
    var g: Double = 0
    var b: Double = 0
    var count: Double = 0
    for dy in 0..<sampleSize {
        for dx in 0..<sampleSize {
            let x = originX + dx
            let y = originY + dy
            guard x >= 0, x < width, y >= 0, y < height else { continue }
            let offset = (y * width + x) * 4
            r += Double(pixels[offset])
            g += Double(pixels[offset + 1])
            b += Double(pixels[offset + 2])
            count += 1
        }
    }
    if count == 0 { return (0, 0, 0) }
    return (r / count, g / count, b / count)
}

let s = 12
let corners = [
    samplePatch(originX: 0, originY: 0, sampleSize: s),
    samplePatch(originX: width - s, originY: 0, sampleSize: s),
    samplePatch(originX: 0, originY: height - s, sampleSize: s),
    samplePatch(originX: width - s, originY: height - s, sampleSize: s),
]
let bgR = corners.map { $0.r }.reduce(0, +) / Double(corners.count)
let bgG = corners.map { $0.g }.reduce(0, +) / Double(corners.count)
let bgB = corners.map { $0.b }.reduce(0, +) / Double(corners.count)
FileHandle.standardOutput.write("Background sample (rgb): \(Int(bgR)), \(Int(bgG)), \(Int(bgB))\n".data(using: .utf8)!)

// Convert to HSV-like luminance and distance for matting.
func dist(_ r1: Double, _ g1: Double, _ b1: Double,
          _ r2: Double, _ g2: Double, _ b2: Double) -> Double {
    let dr = r1 - r2
    let dg = g1 - g2
    let db = b1 - b2
    return sqrt(dr * dr + dg * dg + db * db)
}

// Soft alpha based on colour distance from background and luminance.
let nearThreshold: Double = 28.0    // distance below which the pixel is fully background
let farThreshold: Double = 95.0     // distance above which the pixel is fully foreground
for y in 0..<height {
    for x in 0..<width {
        let offset = (y * width + x) * 4
        let r = Double(pixels[offset])
        let g = Double(pixels[offset + 1])
        let b = Double(pixels[offset + 2])
        let d = dist(r, g, b, bgR, bgG, bgB)
        let alpha: Double
        if d <= nearThreshold {
            alpha = 0.0
        } else if d >= farThreshold {
            alpha = 1.0
        } else {
            alpha = (d - nearThreshold) / (farThreshold - nearThreshold)
        }
        // Premultiplied output.
        pixels[offset]     = UInt8(r * alpha)
        pixels[offset + 1] = UInt8(g * alpha)
        pixels[offset + 2] = UInt8(b * alpha)
        pixels[offset + 3] = UInt8(alpha * 255)
    }
}

guard let outImage = context.makeImage() else {
    FileHandle.standardError.write("Could not produce output image\n".data(using: .utf8)!)
    exit(1)
}

let outURL = URL(fileURLWithPath: outputPath)
guard let dest = CGImageDestinationCreateWithURL(outURL as CFURL, "public.png" as CFString, 1, nil) else {
    FileHandle.standardError.write("Could not create image destination\n".data(using: .utf8)!)
    exit(1)
}
CGImageDestinationAddImage(dest, outImage, nil)
if !CGImageDestinationFinalize(dest) {
    FileHandle.standardError.write("Could not write output image\n".data(using: .utf8)!)
    exit(1)
}

FileHandle.standardOutput.write("Wrote \(outputPath)\n".data(using: .utf8)!)

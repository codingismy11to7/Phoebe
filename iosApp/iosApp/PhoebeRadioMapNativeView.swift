import UIKit
import ComposeApp

#if canImport(GoogleMaps)
import GoogleMaps

final class PhoebeRadioMapNativeViewFactory: NSObject, IosRadioMapNativeViewFactory {
    func create(
        markersJson: String,
        selectedStationId: String?,
        markerTintArgb: Int32,
        useLightTheme: Bool,
        googleMapsApiKey: String,
        onMarkerSelected: @escaping (String) -> Void,
        onMarkerPlay: @escaping (String) -> Void,
        onMapZoomChanged: @escaping (KotlinDouble) -> Void,
        onMapViewportChanged: @escaping (KotlinDouble, KotlinDouble, KotlinDouble, KotlinDouble, KotlinDouble) -> Void
    ) -> UIView {
        GMSServices.provideAPIKey(googleMapsApiKey)
        return PhoebeRadioMapNativeContainerView(
            markersJson: markersJson,
            selectedStationId: selectedStationId,
            markerTintArgb: markerTintArgb,
            useLightTheme: useLightTheme,
            onMarkerSelected: onMarkerSelected,
            onMarkerPlay: onMarkerPlay,
            onMapZoomChanged: onMapZoomChanged,
            onMapViewportChanged: onMapViewportChanged
        )
    }

    func update(
        view: UIView,
        markersJson: String,
        selectedStationId: String?,
        markerTintArgb: Int32,
        useLightTheme: Bool,
        googleMapsApiKey: String
    ) {
        GMSServices.provideAPIKey(googleMapsApiKey)
        (view as? PhoebeRadioMapNativeContainerView)?.update(
            markersJson: markersJson,
            selectedStationId: selectedStationId,
            markerTintArgb: markerTintArgb,
            useLightTheme: useLightTheme
        )
    }
}

private final class PhoebeRadioMapNativeContainerView: UIView, GMSMapViewDelegate {
    private let mapView: GMSMapView
    private let onMarkerSelected: (String) -> Void
    private let onMarkerPlay: (String) -> Void
    private let onMapZoomChanged: (KotlinDouble) -> Void
    private let onMapViewportChanged: (KotlinDouble, KotlinDouble, KotlinDouble, KotlinDouble, KotlinDouble) -> Void
    private let clusterQueue = DispatchQueue(label: "com.phoebe.radioMap.cluster", qos: .userInitiated)
    private var sourceMarkers: [PhoebeRadioMapNativeMarker] = []
    private var renderedMarkers: [GMSMarker] = []
    private var renderedMarkersById: [String: GMSMarker] = [:]
    private var renderedMarkerSignaturesById: [String: PhoebeRadioMapMarkerSignature] = [:]
    private var selectedStationId: String?
    private var markerTintColor: UIColor
    private var markerIconCache: [PhoebeRadioMapIconKey: UIImage] = [:]
    private var lastMarkersJson: String?
    private var lastMarkerTintArgb: Int32?
    private var useLightTheme: Bool
    private var renderGeneration = 0

    init(
        markersJson: String,
        selectedStationId: String?,
        markerTintArgb: Int32,
        useLightTheme: Bool,
        onMarkerSelected: @escaping (String) -> Void,
        onMarkerPlay: @escaping (String) -> Void,
        onMapZoomChanged: @escaping (KotlinDouble) -> Void,
        onMapViewportChanged: @escaping (KotlinDouble, KotlinDouble, KotlinDouble, KotlinDouble, KotlinDouble) -> Void
    ) {
        self.onMarkerSelected = onMarkerSelected
        self.onMarkerPlay = onMarkerPlay
        self.onMapZoomChanged = onMapZoomChanged
        self.onMapViewportChanged = onMapViewportChanged
        self.selectedStationId = selectedStationId
        self.markerTintColor = radioMapUIColor(fromArgb: markerTintArgb)
        self.useLightTheme = useLightTheme
        let camera = GMSCameraPosition(latitude: 20.0, longitude: 0.0, zoom: 2.0)
        self.mapView = GMSMapView(frame: .zero, camera: camera)
        super.init(frame: .zero)
        mapView.delegate = self
        applyMapTheme()
        mapView.translatesAutoresizingMaskIntoConstraints = false
        addSubview(mapView)
        NSLayoutConstraint.activate([
            mapView.leadingAnchor.constraint(equalTo: leadingAnchor),
            mapView.trailingAnchor.constraint(equalTo: trailingAnchor),
            mapView.topAnchor.constraint(equalTo: topAnchor),
            mapView.bottomAnchor.constraint(equalTo: bottomAnchor)
        ])
        update(markersJson: markersJson, selectedStationId: selectedStationId, markerTintArgb: markerTintArgb, useLightTheme: useLightTheme)
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) {
        nil
    }

    func update(markersJson: String, selectedStationId: String?, markerTintArgb: Int32, useLightTheme: Bool) {
        let previousSelectedStationId = self.selectedStationId
        let selectionChanged = previousSelectedStationId != selectedStationId
        var markerStyleChanged = false
        var markerDataChanged = false
        if markerTintArgb != lastMarkerTintArgb {
            markerTintColor = radioMapUIColor(fromArgb: markerTintArgb)
            markerIconCache.removeAll(keepingCapacity: true)
            lastMarkerTintArgb = markerTintArgb
            markerStyleChanged = true
        }
        if useLightTheme != self.useLightTheme {
            self.useLightTheme = useLightTheme
            applyMapTheme()
        }
        self.selectedStationId = selectedStationId
        if markersJson != lastMarkersJson {
            let data = Data(markersJson.utf8)
            sourceMarkers = (try? JSONDecoder().decode([PhoebeRadioMapNativeMarker].self, from: data)) ?? []
            lastMarkersJson = markersJson
            markerDataChanged = true
        }
        if markerDataChanged {
            requestRenderMarkers()
        } else if markerStyleChanged {
            updateRenderedMarkerIcons()
        } else if selectionChanged {
            updateSelectedMarkerIcons(previousId: previousSelectedStationId, currentId: selectedStationId)
        }
    }

    private func applyMapTheme() {
        let background = useLightTheme ? UIColor(red: 0.95, green: 0.96, blue: 0.98, alpha: 1.0) : UIColor.black
        backgroundColor = background
        mapView.backgroundColor = background
        if #available(iOS 13.0, *) {
            overrideUserInterfaceStyle = useLightTheme ? .light : .dark
            mapView.overrideUserInterfaceStyle = useLightTheme ? .light : .dark
        }
    }

    func mapView(_ mapView: GMSMapView, idleAt position: GMSCameraPosition) {
        onMapZoomChanged(KotlinDouble(value: Double(position.zoom)))
        notifyViewportChanged(position: position)
        requestRenderMarkers()
    }

    func mapView(_ mapView: GMSMapView, didTap marker: GMSMarker) -> Bool {
        guard let item = marker.userData as? PhoebeRadioMapNativeMarker else { return false }
        if item.isClusterMarker {
            focusMap(on: item)
            return true
        }
        let previousSelectedStationId = selectedStationId
        selectedStationId = item.id
        updateSelectedMarkerIcons(previousId: previousSelectedStationId, currentId: item.id)
        onMarkerSelected(item.id)
        return true
    }

    private func requestRenderMarkers() {
        renderGeneration += 1
        let generation = renderGeneration
        let markers = sourceMarkers
        let zoom = Double(mapView.camera.zoom)
        let visibleBounds = radioMapVisibleBounds(
            for: mapView,
            paddingRatio: RadioMapRenderPaddingRatio
        )
        clusterQueue.async { [weak self] in
            let visibleMarkers = clusterMarkersForZoom(
                markers,
                zoom: zoom,
                visibleBounds: visibleBounds
            )
            DispatchQueue.main.async { [weak self] in
                guard let self, self.renderGeneration == generation else { return }
                self.applyRenderedMarkers(visibleMarkers)
            }
        }
    }

    private func applyRenderedMarkers(_ visibleMarkers: [PhoebeRadioMapNativeMarker]) {
        var visibleIds = Set<String>()
        visibleIds.reserveCapacity(visibleMarkers.count)
        visibleMarkers.forEach { item in
            visibleIds.insert(item.id)
            let signature = PhoebeRadioMapMarkerSignature(item: item)
            if let marker = renderedMarkersById[item.id] {
                marker.userData = item
                if renderedMarkerSignaturesById[item.id] != signature {
                    marker.position = CLLocationCoordinate2D(latitude: item.latitude, longitude: item.longitude)
                    marker.title = item.name
                    marker.icon = markerIcon(for: item)
                    renderedMarkerSignaturesById[item.id] = signature
                }
            } else {
                let marker = GMSMarker(position: CLLocationCoordinate2D(latitude: item.latitude, longitude: item.longitude))
                marker.title = item.name
                marker.userData = item
                marker.icon = markerIcon(for: item)
                marker.map = mapView
                renderedMarkersById[item.id] = marker
                renderedMarkerSignaturesById[item.id] = signature
            }
        }

        renderedMarkersById.keys
            .filter { !visibleIds.contains($0) }
            .forEach { itemId in
                renderedMarkersById[itemId]?.map = nil
                renderedMarkersById.removeValue(forKey: itemId)
                renderedMarkerSignaturesById.removeValue(forKey: itemId)
            }

        renderedMarkers = visibleMarkers.compactMap { renderedMarkersById[$0.id] }
    }

    private func clearRenderedMarkers() {
        renderGeneration += 1
        renderedMarkers.forEach { $0.map = nil }
        renderedMarkers.removeAll()
        renderedMarkersById.removeAll(keepingCapacity: true)
        renderedMarkerSignaturesById.removeAll(keepingCapacity: true)
    }

    deinit {
        clearRenderedMarkers()
    }

    private func updateRenderedMarkerIcons() {
        renderedMarkers.forEach { marker in
            guard let item = marker.userData as? PhoebeRadioMapNativeMarker else { return }
            marker.icon = markerIcon(for: item)
        }
    }

    private func focusMap(on item: PhoebeRadioMapNativeMarker) {
        let positions = markerPositions(for: item)
        guard positions.count > 1 else {
            mapView.animate(toLocation: CLLocationCoordinate2D(latitude: item.latitude, longitude: item.longitude))
            mapView.animate(toZoom: min(mapView.camera.zoom + 2.0, 18.0))
            return
        }
        var bounds = GMSCoordinateBounds(coordinate: positions[0], coordinate: positions[0])
        positions.dropFirst().forEach { coordinate in
            bounds = bounds.includingCoordinate(coordinate)
        }
        mapView.animate(with: GMSCameraUpdate.fit(bounds, withPadding: 48.0))
    }

    private func markerPositions(for item: PhoebeRadioMapNativeMarker) -> [CLLocationCoordinate2D] {
        if item.isClusterMarker && !item.children.isEmpty {
            return item.children.flatMap { markerPositions(for: $0) }
        }
        return [CLLocationCoordinate2D(latitude: item.latitude, longitude: item.longitude)]
    }

    private func updateSelectedMarkerIcons(previousId: String?, currentId: String?) {
        [previousId, currentId].forEach { itemId in
            guard
                let itemId,
                let marker = renderedMarkersById[itemId],
                let item = marker.userData as? PhoebeRadioMapNativeMarker
            else {
                return
            }
            marker.icon = markerIcon(for: item)
        }
    }

    private func markerIcon(for item: PhoebeRadioMapNativeMarker) -> UIImage {
        let key = PhoebeRadioMapIconKey(
            approximate: item.approximate,
            selected: item.id == selectedStationId,
            count: item.isClusterMarker ? item.countValue : nil
        )
        if let cached = markerIconCache[key] {
            return cached
        }
        let image = radioMapMarkerImage(
            tint: markerTintColor.withAlphaComponent(item.approximate ? 0.7 : 1.0),
            selected: key.selected,
            count: key.count
        )
        markerIconCache[key] = image
        return image
    }

    private func notifyViewportChanged(position: GMSCameraPosition) {
        let region = mapView.projection.visibleRegion()
        let latitudes = [
            region.nearLeft.latitude,
            region.nearRight.latitude,
            region.farLeft.latitude,
            region.farRight.latitude
        ]
        let longitudes = [
            region.nearLeft.longitude,
            region.nearRight.longitude,
            region.farLeft.longitude,
            region.farRight.longitude
        ]
        guard
            let north = latitudes.max(),
            let south = latitudes.min(),
            let east = longitudes.max(),
            let west = longitudes.min()
        else {
            return
        }
        onMapViewportChanged(
            KotlinDouble(value: north),
            KotlinDouble(value: south),
            KotlinDouble(value: east),
            KotlinDouble(value: west),
            KotlinDouble(value: Double(position.zoom))
        )
    }
}

private struct PhoebeRadioMapIconKey: Hashable {
    let approximate: Bool
    let selected: Bool
    let count: Int?
}

private struct PhoebeRadioMapMarkerSignature: Equatable {
    let latitude: Double
    let longitude: Double
    let name: String
    let approximate: Bool
    let isCluster: Bool
    let count: Int

    init(item: PhoebeRadioMapNativeMarker) {
        latitude = item.latitude
        longitude = item.longitude
        name = item.name
        approximate = item.approximate
        isCluster = item.isClusterMarker
        count = item.countValue
    }
}

private struct PhoebeRadioMapNativeMarker: Decodable, Identifiable, Equatable {
    let id: String
    let name: String
    let latitude: Double
    let longitude: Double
    let approximate: Bool
    let isCluster: Bool?
    let clusterCount: Int?
    let children: [PhoebeRadioMapNativeMarker]

    private enum CodingKeys: String, Swift.CodingKey {
        case id, name, latitude, longitude, lat, lng, approximate, isCluster, clusterCount, count, children
    }

    init(
        id: String,
        name: String,
        latitude: Double,
        longitude: Double,
        approximate: Bool,
        isCluster: Bool?,
        clusterCount: Int?,
        children: [PhoebeRadioMapNativeMarker] = []
    ) {
        self.id = id
        self.name = name
        self.latitude = latitude
        self.longitude = longitude
        self.approximate = approximate
        self.isCluster = isCluster
        self.clusterCount = clusterCount
        self.children = children
    }

    init(from decoder: Swift.Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        id = try container.decode(String.self, forKey: .id)
        name = try container.decode(String.self, forKey: .name)
        latitude = try container.decodeIfPresent(Double.self, forKey: .latitude)
            ?? container.decode(Double.self, forKey: .lat)
        longitude = try container.decodeIfPresent(Double.self, forKey: .longitude)
            ?? container.decode(Double.self, forKey: .lng)
        approximate = try container.decodeIfPresent(Bool.self, forKey: .approximate) ?? false
        isCluster = try container.decodeIfPresent(Bool.self, forKey: .isCluster)
        clusterCount = try container.decodeIfPresent(Int.self, forKey: .clusterCount)
            ?? container.decodeIfPresent(Int.self, forKey: .count)
        children = try container.decodeIfPresent([PhoebeRadioMapNativeMarker].self, forKey: .children) ?? []
    }

    var isClusterMarker: Bool { isCluster ?? false }
    var countValue: Int { clusterCount ?? 1 }
}

private struct PhoebeRadioMapClusterKey: Hashable {
    let latitude: Int
    let longitude: Int
}

private struct PhoebeRadioMapCoordinateBounds {
    let south: Double
    let north: Double
    let west: Double
    let east: Double
    let coversWorldLongitude: Bool

    func contains(latitude: Double, longitude: Double) -> Bool {
        guard latitude >= south && latitude <= north else { return false }
        guard !coversWorldLongitude else { return true }
        let normalizedLongitude = normalizeLongitude(longitude)
        if west <= east {
            return normalizedLongitude >= west && normalizedLongitude <= east
        }
        return normalizedLongitude >= west || normalizedLongitude <= east
    }
}

private func clusterMarkersForZoom(
    _ markers: [PhoebeRadioMapNativeMarker],
    zoom: Double,
    visibleBounds: PhoebeRadioMapCoordinateBounds
) -> [PhoebeRadioMapNativeMarker] {
    let leaves = flattenStationMarkers(markers)
    let threshold = radioMapClusterThresholdDegrees(zoom)
    if threshold <= 0 {
        return leaves.filter { marker in
            visibleBounds.contains(latitude: marker.latitude, longitude: marker.longitude)
        }
    }

    var groups: [PhoebeRadioMapClusterKey: [PhoebeRadioMapNativeMarker]] = [:]
    groups.reserveCapacity(leaves.count)
    leaves.forEach { station in
        groups[clusterKey(for: station, threshold: threshold), default: []].append(station)
    }

    var clusteredMarkers: [PhoebeRadioMapNativeMarker] = []
    clusteredMarkers.reserveCapacity(groups.count)
    groups.forEach { key, group in
        guard group.count > 1 else {
            if let station = group.first {
                clusteredMarkers.append(station)
            }
            return
        }
        let latitude = group.reduce(0.0) { $0 + $1.latitude } / Double(group.count)
        let longitude = group.reduce(0.0) { $0 + $1.longitude } / Double(group.count)
        clusteredMarkers.append(
            PhoebeRadioMapNativeMarker(
                id: "native_cluster_\(Int(threshold * 1_000))_\(key.latitude)_\(key.longitude)",
                name: "\(group.count) stations",
                latitude: latitude,
                longitude: longitude,
                approximate: group.contains(where: \.approximate),
                isCluster: true,
                clusterCount: group.count,
                children: group
            )
        )
    }

    return clusteredMarkers.filter { marker in
        visibleBounds.contains(latitude: marker.latitude, longitude: marker.longitude)
    }
}

private func clusterKey(
    for marker: PhoebeRadioMapNativeMarker,
    threshold: Double
) -> PhoebeRadioMapClusterKey {
    PhoebeRadioMapClusterKey(
        latitude: Int(floor((marker.latitude + 90.0) / threshold)),
        longitude: Int(floor((normalizeLongitude(marker.longitude) + 180.0) / threshold))
    )
}

private func flattenStationMarkers(_ markers: [PhoebeRadioMapNativeMarker]) -> [PhoebeRadioMapNativeMarker] {
    markers.flatMap { marker in
        if marker.isClusterMarker && !marker.children.isEmpty {
            return flattenStationMarkers(marker.children)
        }
        return [marker]
    }
}

private func radioMapMarkerImage(tint: UIColor, selected: Bool, count: Int?) -> UIImage {
    let diameter: CGFloat = count == nil ? (selected ? 10 : 8) : 22
    let scale = UIScreen.main.scale
    let size = CGSize(width: diameter, height: diameter)
    let renderer = UIGraphicsImageRenderer(size: size)
    return renderer.image { context in
        let rect = CGRect(origin: .zero, size: size).insetBy(dx: 1 / scale, dy: 1 / scale)
        tint.setFill()
        UIBezierPath(ovalIn: rect).fill()
        (selected ? UIColor.white : UIColor(red: 0.02, green: 0.04, blue: 0.09, alpha: 0.45)).setStroke()
        let stroke = UIBezierPath(ovalIn: rect)
        stroke.lineWidth = selected ? 1.5 : 1
        stroke.stroke()
        if let count {
            let text = "\(count)" as NSString
            let attributes: [NSAttributedString.Key: Any] = [
                .font: UIFont.systemFont(ofSize: 9, weight: .bold),
                .foregroundColor: UIColor(red: 0.03, green: 0.07, blue: 0.12, alpha: 1.0)
            ]
            let textSize = text.size(withAttributes: attributes)
            text.draw(
                at: CGPoint(x: (diameter - textSize.width) / 2, y: (diameter - textSize.height) / 2),
                withAttributes: attributes
            )
        }
        _ = context
    }
}

private func radioMapVisibleBounds(
    for mapView: GMSMapView,
    paddingRatio: Double
) -> PhoebeRadioMapCoordinateBounds {
    let region = mapView.projection.visibleRegion()
    let latitudes: [Double] = [
        Double(region.nearLeft.latitude),
        Double(region.nearRight.latitude),
        Double(region.farLeft.latitude),
        Double(region.farRight.latitude)
    ]
    let south: Double = latitudes.min() ?? -90.0
    let north: Double = latitudes.max() ?? 90.0
    let latitudePadding = Swift.max((north - south) * paddingRatio, 0.01)

    let centerLongitude = normalizeLongitude(Double(mapView.camera.target.longitude))
    let longitudeOffsets: [Double] = [
        Double(region.nearLeft.longitude),
        Double(region.nearRight.longitude),
        Double(region.farLeft.longitude),
        Double(region.farRight.longitude)
    ].map { longitudeOffset(from: centerLongitude, to: normalizeLongitude(Double($0))) }
    let minLongitudeOffset = longitudeOffsets.min() ?? -180.0
    let maxLongitudeOffset = longitudeOffsets.max() ?? 180.0
    let longitudePadding = Swift.max((maxLongitudeOffset - minLongitudeOffset) * paddingRatio, 0.01)
    let paddedLongitudeSpan = maxLongitudeOffset - minLongitudeOffset + (longitudePadding * 2.0)
    let coversWorldLongitude = paddedLongitudeSpan >= 360.0

    return PhoebeRadioMapCoordinateBounds(
        south: Swift.max(-90.0, south - latitudePadding),
        north: Swift.min(90.0, north + latitudePadding),
        west: normalizeLongitude(centerLongitude + minLongitudeOffset - longitudePadding),
        east: normalizeLongitude(centerLongitude + maxLongitudeOffset + longitudePadding),
        coversWorldLongitude: coversWorldLongitude
    )
}

private func longitudeOffset(from origin: Double, to longitude: Double) -> Double {
    guard origin.isFinite && longitude.isFinite else { return 0.0 }
    let offset = longitude - origin
    let remainder = (offset + 180.0).truncatingRemainder(dividingBy: 360.0)
    return remainder < 0.0 ? remainder + 180.0 : remainder - 180.0
}

private func normalizeLongitude(_ longitude: Double) -> Double {
    guard longitude.isFinite else { return 0.0 }
    let remainder = (longitude + 180.0).truncatingRemainder(dividingBy: 360.0)
    return remainder < 0.0 ? remainder + 180.0 : remainder - 180.0
}

private func radioMapUIColor(fromArgb argb: Int32) -> UIColor {
    let value = UInt32(bitPattern: argb)
    let alpha = CGFloat((value >> 24) & 0xFF) / 255.0
    let red = CGFloat((value >> 16) & 0xFF) / 255.0
    let green = CGFloat((value >> 8) & 0xFF) / 255.0
    let blue = CGFloat(value & 0xFF) / 255.0
    return UIColor(red: red, green: green, blue: blue, alpha: alpha)
}

private func radioMapClusterThresholdDegrees(_ zoom: Double) -> Double {
    if zoom >= 11.0 { return 0.0 }
    if zoom >= 9.0 { return 0.12 }
    if zoom >= 7.0 { return 0.28 }
    if zoom >= 5.5 { return 0.55 }
    if zoom >= 4.0 { return 1.1 }
    if zoom >= 3.0 { return 2.0 }
    return 3.0
}

private let RadioMapRenderPaddingRatio = 0.5

#endif

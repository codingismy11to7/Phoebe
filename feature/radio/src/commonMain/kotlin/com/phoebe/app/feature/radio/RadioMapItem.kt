package com.phoebe.app.feature.radio

import androidx.compose.runtime.Immutable
import com.phoebe.app.domain.RadioStation
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlinx.serialization.Serializable

@Immutable
@Serializable
sealed interface RadioMapItem {
    val id: String
    val latitude: Double
    val longitude: Double
    val approximate: Boolean
    val isCluster: Boolean
    val clusterCount: Int
    val name: String

    @Serializable
    data class Station(
        val station: RadioStation,
        override val latitude: Double,
        override val longitude: Double,
        override val approximate: Boolean = false,
    ) : RadioMapItem {
        override val id: String get() = station.id
        override val isCluster: Boolean get() = false
        override val clusterCount: Int get() = 1
        override val name: String get() = station.name
    }

    @Serializable
    data class Cluster(
        override val id: String,
        override val latitude: Double,
        override val longitude: Double,
        val stations: List<RadioStation>,
        override val approximate: Boolean,
    ) : RadioMapItem {
        override val isCluster: Boolean get() = true
        override val clusterCount: Int get() = stations.size
        override val name: String get() = "$clusterCount stations"
    }
}

fun clusterStations(
    stations: List<RadioStation>,
    clusterThresholdDegrees: Double = 1.5,
    expandedClusterIds: Set<String> = emptySet()
): List<RadioMapItem> {
    val geoStations = stations.filter { it.hasGeoLocation }
    if (clusterThresholdDegrees <= 0.0) {
        return geoStations
            .sortedBy { it.id }
            .mapNotNull { station ->
                val latitude = station.geoLat ?: return@mapNotNull null
                val longitude = station.geoLong ?: return@mapNotNull null
                RadioMapItem.Station(
                    station = station,
                    latitude = latitude,
                    longitude = longitude,
                    approximate = false,
                )
            }
    }

    val items = mutableListOf<RadioMapItem>()

    val preciseClusters = mutableListOf<MutableList<RadioStation>>()
    for (station in geoStations.sortedBy { it.id }) {
        val lat = station.geoLat ?: continue
        val lng = station.geoLong ?: continue
        
        val matchingCluster = preciseClusters.firstOrNull { cluster ->
            val first = cluster.first()
            val distLat = kotlin.math.abs((first.geoLat ?: 0.0) - lat)
            val distLng = kotlin.math.abs((first.geoLong ?: 0.0) - lng)
            val wrappedDistLng = if (distLng > 180.0) 360.0 - distLng else distLng
            distLat < clusterThresholdDegrees && wrappedDistLng < clusterThresholdDegrees
        }
        
        if (matchingCluster != null) {
            matchingCluster.add(station)
        } else {
            preciseClusters.add(mutableListOf(station))
        }
    }

    preciseClusters.forEach { clusterList ->
        if (clusterList.isEmpty()) return@forEach
        val clusterId = "cluster_${clusterList.first().id}"
        if (clusterList.size > 1 && !expandedClusterIds.contains(clusterId)) {
            var avgLat = 0.0
            var avgLng = 0.0
            clusterList.forEach {
                avgLat += it.geoLat ?: 0.0
                avgLng += it.geoLong ?: 0.0
            }
            avgLat /= clusterList.size
            avgLng /= clusterList.size
            
            items.add(
                RadioMapItem.Cluster(
                    id = clusterId,
                    latitude = avgLat,
                    longitude = avgLng,
                    stations = clusterList.sortedBy { it.name },
                    approximate = false
                )
            )
        } else {
            val spiderfyExpandedCluster = clusterList.shouldSpiderfyExpandedCluster()
            val centerLatitude = clusterList.mapNotNull { it.geoLat }.average()
            val centerLongitude = clusterList.mapNotNull { it.geoLong }.average()
            clusterList.forEachIndexed { index, station ->
                val location = if (spiderfyExpandedCluster) {
                    spiderfyLocation(
                        latitude = centerLatitude,
                        longitude = centerLongitude,
                        index = index,
                        count = clusterList.size,
                    )
                } else {
                    (station.geoLat ?: 0.0) to (station.geoLong ?: 0.0)
                }
                items.add(
                    RadioMapItem.Station(
                        station = station,
                        latitude = location.first,
                        longitude = location.second,
                        approximate = false
                    )
                )
            }
        }
    }

    return items
}

internal fun List<RadioMapItem>.flattenRadioMapStationMarkers(): List<RadioMapItem.Station> =
    flatMap { item ->
        when (item) {
            is RadioMapItem.Cluster -> {
                val sortedStations = item.stations.sortedBy { it.name }
                sortedStations.mapIndexed { index, station ->
                    val location = if (item.approximate) {
                        spiderfyLocation(
                            latitude = item.latitude,
                            longitude = item.longitude,
                            index = index,
                            count = sortedStations.size,
                        )
                    } else {
                        (station.geoLat ?: item.latitude) to (station.geoLong ?: item.longitude)
                    }
                    RadioMapItem.Station(
                        station = station,
                        latitude = location.first,
                        longitude = location.second,
                        approximate = item.approximate,
                    )
                }
            }
            is RadioMapItem.Station -> listOf(item)
        }
    }

fun clusterRadioMapMarkersForZoom(
    items: List<RadioMapItem>,
    zoom: Double,
): List<RadioMapItem> {
    val leaves = items.flattenRadioMapStationMarkers()
    val threshold = radioMapClusterThresholdDegrees(zoom)
    if (threshold <= 0.0) return leaves

    val groups = mutableListOf<MutableList<RadioMapItem.Station>>()
    leaves.forEach { station ->
        val group = groups.firstOrNull { candidate ->
            val first = candidate.first()
            kotlin.math.abs(first.latitude - station.latitude) < threshold &&
                radioMapLongitudeDistance(first.longitude, station.longitude) < threshold
        }
        if (group == null) {
            groups.add(mutableListOf(station))
        } else {
            group.add(station)
        }
    }

    return groups.flatMap { group ->
        if (group.size == 1) {
            group
        } else {
            val latitude = group.sumOf { it.latitude } / group.size
            val longitude = group.sumOf { it.longitude } / group.size
            listOf(
                RadioMapItem.Cluster(
                    id = "view_cluster_${compactRadioMapClusterId(group.map { it.id })}",
                    latitude = latitude,
                    longitude = longitude,
                    stations = group.map { it.station }.sortedBy { it.name },
                    approximate = group.any { it.approximate },
                ),
            )
        }
    }
}

fun expandRadioMapMarkers(
    items: List<RadioMapItem>,
    expandedClusterIds: Set<String>,
): List<RadioMapItem> =
    items.flatMap { item ->
        if (item is RadioMapItem.Cluster && item.id in expandedClusterIds) {
            listOf(item).flattenRadioMapStationMarkers()
        } else {
            listOf(item)
        }
    }

internal fun compactRadioMapClusterId(ids: List<String>): String {
    var hash = 1_125_899_906_842_597L
    ids.sorted().forEach { id ->
        id.forEach { character ->
            hash = (hash * 31) + character.code
        }
        hash = (hash * 31) + 31
    }
    return hash.toULong().toString(36)
}

internal fun radioMapLongitudeDistance(a: Double, b: Double): Double {
    val diff = kotlin.math.abs(a - b)
    return if (diff > 180.0) 360.0 - diff else diff
}

private fun List<RadioStation>.shouldSpiderfyExpandedCluster(): Boolean {
    if (size <= 1) return false
    val latitudes = mapNotNull { it.geoLat }
    val longitudes = mapNotNull { it.geoLong }
    if (latitudes.size != size || longitudes.size != size) return false
    val latitudeSpan = (latitudes.maxOrNull() ?: 0.0) - (latitudes.minOrNull() ?: 0.0)
    val longitudeSpan = radioMapLongitudeDistance(
        longitudes.maxOrNull() ?: 0.0,
        longitudes.minOrNull() ?: 0.0,
    )
    return latitudeSpan < 0.0002 && longitudeSpan < 0.0002
}

internal fun List<RadioMapItem>.findRadioMapItem(itemId: String): RadioMapItem? {
    firstOrNull { it.id == itemId }?.let { return it }
    forEach { item ->
        if (item is RadioMapItem.Cluster) {
            val station = item.stations.firstOrNull { it.id == itemId }
            if (station != null) {
                return RadioMapItem.Station(
                    station = station,
                    latitude = station.geoLat ?: item.latitude,
                    longitude = station.geoLong ?: item.longitude,
                    approximate = item.approximate,
                )
            }
        }
    }
    return null
}

internal fun RadioMapItem.Cluster.countryCodeForDrilldown(): String? =
    takeIf { approximate }
        ?.stations
        ?.mapNotNull { it.countryCode?.trim()?.uppercase()?.takeIf(String::isNotBlank) }
        ?.groupingBy { it }
        ?.eachCount()
        ?.maxByOrNull { it.value }
        ?.key

fun radioMapClusterThresholdDegrees(zoom: Double): Double =
    when {
        zoom >= 11.0 -> 0.0
        zoom >= 9.0 -> 0.12
        zoom >= 7.0 -> 0.28
        zoom >= 5.5 -> 0.55
        zoom >= 4.0 -> 1.1
        zoom >= 3.0 -> 2.0
        else -> 3.0
    }

internal fun spiderfyLocation(
    latitude: Double,
    longitude: Double,
    index: Int,
    count: Int,
): Pair<Double, Double> {
    if (count <= 1) return latitude to longitude
    val angle = (2.0 * PI * index) / count
    val ring = index / 12
    val radius = min(0.65, 0.18 + ring * 0.12)
    val latOffset = sin(angle) * radius
    val lngScale = cos(latitude * PI / 180.0).coerceAtLeast(0.25)
    val lngOffset = (cos(angle) * radius) / lngScale
    return (latitude + latOffset).coerceIn(-90.0, 90.0) to wrapLongitude(longitude + lngOffset)
}

private fun wrapLongitude(longitude: Double): Double {
    var wrapped = longitude
    while (wrapped > 180.0) wrapped -= 360.0
    while (wrapped < -180.0) wrapped += 360.0
    return wrapped
}

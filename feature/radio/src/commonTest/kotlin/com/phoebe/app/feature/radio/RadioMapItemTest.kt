package com.phoebe.app.feature.radio

import com.phoebe.app.domain.RadioStation
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RadioMapItemTest {
    @Test
    fun preciseStationUsesApiCoordinates() {
        val station = RadioStation(
            id = "kexp",
            name = "KEXP",
            streamUrl = "https://stream.example/kexp",
            countryCode = "US",
            state = "Washington",
            geoLat = 47.608,
            geoLong = -122.335,
        )

        val items = clusterStations(listOf(station))
        assertEquals(1, items.size)
        val item = items.first() as RadioMapItem.Station
        assertEquals(false, item.approximate)
        assertEquals(47.608, item.latitude)
        assertEquals(-122.335, item.longitude)
    }

    @Test
    fun stationsWithoutCoordinatesAreIgnored() {
        val station = RadioStation(
            id = "country-only",
            name = "Country Only",
            streamUrl = "https://stream.example/country",
            countryCode = "DE",
        )

        val items = clusterStations(listOf(station))
        assertEquals(emptyList(), items)
    }

    @Test
    fun closePreciseStationsBecomePreciseCluster() {
        val station1 = RadioStation(
            id = "s1",
            name = "Station 1",
            streamUrl = "https://stream.example/1",
            geoLat = 40.0,
            geoLong = -100.0,
        )
        val station2 = RadioStation(
            id = "s2",
            name = "Station 2",
            streamUrl = "https://stream.example/2",
            geoLat = 40.5,
            geoLong = -99.5,
        )

        val items = clusterStations(listOf(station1, station2))
        assertEquals(1, items.size)
        val item = items.first() as RadioMapItem.Cluster
        assertEquals(false, item.approximate)
        assertEquals(true, item.isCluster)
        assertEquals(2, item.clusterCount)
        assertEquals(40.25, item.latitude)
        assertEquals(-99.75, item.longitude)
    }

    @Test
    fun zeroThresholdReturnsRawStationMarkers() {
        val station1 = RadioStation(
            id = "s2",
            name = "Station 2",
            streamUrl = "https://stream.example/2",
            geoLat = 40.0,
            geoLong = -100.0,
        )
        val station2 = RadioStation(
            id = "s1",
            name = "Station 1",
            streamUrl = "https://stream.example/1",
            geoLat = 40.0,
            geoLong = -100.0,
        )

        val items = clusterStations(
            stations = listOf(station1, station2),
            clusterThresholdDegrees = 0.0,
        )

        assertEquals(listOf("s1", "s2"), items.map { it.id })
        assertTrue(items.all { it is RadioMapItem.Station })
    }

    @Test
    fun clusterExpansionWithExpandedIds() {
        val station1 = RadioStation(
            id = "s1",
            name = "Station 1",
            streamUrl = "https://stream.example/1",
            geoLat = 40.0,
            geoLong = -100.0,
        )
        val station2 = RadioStation(
            id = "s2",
            name = "Station 2",
            streamUrl = "https://stream.example/2",
            geoLat = 40.5,
            geoLong = -99.5,
        )

        val itemsClustered = clusterStations(listOf(station1, station2))
        assertEquals(1, itemsClustered.size)
        val cluster = itemsClustered.first() as RadioMapItem.Cluster
        val clusterId = cluster.id

        val itemsExpanded = clusterStations(
            stations = listOf(station1, station2),
            expandedClusterIds = setOf(clusterId)
        )
        assertEquals(2, itemsExpanded.size)
        assertTrue(itemsExpanded.all { it is RadioMapItem.Station })
    }

    @Test
    fun expandedExactCoordinateClusterSpreadsStationMarkers() {
        val station1 = RadioStation(
            id = "s1",
            name = "Station 1",
            streamUrl = "https://stream.example/1",
            geoLat = 40.0,
            geoLong = -100.0,
        )
        val station2 = RadioStation(
            id = "s2",
            name = "Station 2",
            streamUrl = "https://stream.example/2",
            geoLat = 40.0,
            geoLong = -100.0,
        )
        val cluster = clusterStations(listOf(station1, station2)).single() as RadioMapItem.Cluster

        val itemsExpanded = clusterStations(
            stations = listOf(station1, station2),
            expandedClusterIds = setOf(cluster.id),
        )
        val expandedStations = itemsExpanded.filterIsInstance<RadioMapItem.Station>()

        assertEquals(2, expandedStations.size)
        assertTrue(
            abs(expandedStations[0].latitude - expandedStations[1].latitude) > 0.0 ||
                radioMapLongitudeDistance(expandedStations[0].longitude, expandedStations[1].longitude) > 0.0,
        )
    }

    @Test
    fun findsStationInsideClusterForPlatformCallbacks() {
        val station1 = RadioStation(
            id = "s1",
            name = "Station 1",
            streamUrl = "https://stream.example/1",
            geoLat = 40.0,
            geoLong = -100.0,
        )
        val station2 = RadioStation(
            id = "s2",
            name = "Station 2",
            streamUrl = "https://stream.example/2",
            geoLat = 40.5,
            geoLong = -99.5,
        )

        val items = clusterStations(listOf(station1, station2))
        val resolved = items.findRadioMapItem("s2") as RadioMapItem.Station

        assertEquals(station2, resolved.station)
        assertEquals(40.5, resolved.latitude)
        assertEquals(-99.5, resolved.longitude)
    }

    @Test
    fun selectingPreciseClusterAddsItToExpandedClusterIds() {
        val station1 = RadioStation(
            id = "s1",
            name = "Station 1",
            streamUrl = "https://stream.example/1",
            geoLat = 40.0,
            geoLong = -100.0,
        )
        val station2 = RadioStation(
            id = "s2",
            name = "Station 2",
            streamUrl = "https://stream.example/2",
            geoLat = 40.5,
            geoLong = -99.5,
        )
        val cluster = clusterStations(listOf(station1, station2)).single() as RadioMapItem.Cluster

        assertEquals(setOf(cluster.id), expandedRadioMapClusterIds(cluster, emptySet()))
    }

    @Test
    fun clusterThresholdDecreasesAsMapZoomGetsCloser() {
        assertTrue(radioMapClusterThresholdDegrees(2.0) > radioMapClusterThresholdDegrees(4.5))
        assertTrue(radioMapClusterThresholdDegrees(4.5) > radioMapClusterThresholdDegrees(7.0))
        assertTrue(radioMapClusterThresholdDegrees(7.0) > radioMapClusterThresholdDegrees(11.0))
        assertEquals(0.0, radioMapClusterThresholdDegrees(11.0))
    }

    @Test
    fun closeZoomThresholdSplitsPreciseClusters() {
        val station1 = RadioStation(
            id = "s1",
            name = "Station 1",
            streamUrl = "https://stream.example/1",
            geoLat = 40.0,
            geoLong = -100.0,
        )
        val station2 = RadioStation(
            id = "s2",
            name = "Station 2",
            streamUrl = "https://stream.example/2",
            geoLat = 40.5,
            geoLong = -99.5,
        )

        val items = clusterStations(
            stations = listOf(station1, station2),
            clusterThresholdDegrees = radioMapClusterThresholdDegrees(7.0),
        )

        assertEquals(2, items.size)
        assertTrue(items.all { it is RadioMapItem.Station })
    }

    @Test
    fun viewZoomClusteringGroupsNearbyStationsAtFarZoom() {
        val station1 = RadioStation(
            id = "s1",
            name = "Station 1",
            streamUrl = "https://stream.example/1",
            geoLat = 40.0,
            geoLong = -100.0,
        )
        val station2 = RadioStation(
            id = "s2",
            name = "Station 2",
            streamUrl = "https://stream.example/2",
            geoLat = 40.2,
            geoLong = -99.8,
        )
        val source = clusterStations(
            stations = listOf(station1, station2),
            clusterThresholdDegrees = radioMapClusterThresholdDegrees(7.0),
        )

        val farZoom = clusterRadioMapMarkersForZoom(source, zoom = 2.0)
        assertEquals(1, farZoom.size)
        assertTrue(farZoom.single().isCluster)

        val closeZoom = clusterRadioMapMarkersForZoom(source, zoom = 11.0)
        assertEquals(2, closeZoom.size)
        assertTrue(closeZoom.all { it is RadioMapItem.Station })
    }

    @Test
    fun htmlGenerationUsesVisibleClusterMarkersAndZoomEvents() {
        val station1 = RadioStation(
            id = "kexp-1",
            name = "KEXP's One",
            streamUrl = "https://stream.example/kexp-1",
            homepageUrl = "https://kexp.example/",
            countryCode = "US",
            geoLat = 47.608,
            geoLong = -122.335,
        )
        val station2 = RadioStation(
            id = "kexp-2",
            name = "KEXP Two",
            streamUrl = "https://stream.example/kexp-2",
            countryCode = "US",
            geoLat = 47.61,
            geoLong = -122.337,
        )
        val items = clusterStations(listOf(station1, station2))
        val html = radioMapHtml(items, items.firstOrNull(), "test-key", "#22c55e")

        assertFalse(html.contains("id=\"status\""))
        assertFalse(html.contains("#status"))
        assertTrue(html.contains("markerTint = '#22c55e'"))
        assertTrue(html.contains("Map: GoogleMap"))
        assertTrue(html.contains("new GoogleMap(document.getElementById('map')"))
        assertFalse(html.contains("new Map(document.getElementById('map')"))
        assertTrue(html.contains("new google.maps.Marker"))
        assertTrue(html.contains("markerIcon"))
        assertTrue(html.contains("markerIconCache"))
        assertTrue(html.contains("markerIconCache.set(cacheKey, icon)"))
        assertTrue(html.contains("Math.max(28, 18 + digitCount * 7)"))
        assertTrue(html.contains("fontSize"))
        assertTrue(html.contains("@googlemaps/markerclusterer"))
        assertTrue(html.contains("\"children\": ["))
        assertTrue(html.contains("visibleMapMarkers"))
        assertFalse(html.contains("flattenStationMarkers"))
        assertTrue(html.contains("representedStationCount"))
        assertTrue(html.contains("representedMarkerCount"))
        assertTrue(html.contains("representedMarkerPositions"))
        assertTrue(html.contains(".flatMap((station) => clusterPositions(station))"))
        assertTrue(html.contains(".flatMap((child) => clusterPositions(child))"))
        assertTrue(html.contains("marker?.phoebeStation?.count"))
        assertTrue(html.contains("window.updateRadioMapSelection"))
        assertTrue(html.contains("marker.setIcon(markerIcon(station"))
        assertTrue(html.contains("currentMarkerDataSignature"))
        assertTrue(html.contains("google.maps.event.clearInstanceListeners(marker)"))
        assertTrue(html.contains("optimized: true"))
        assertFalse(html.contains("optimized: false"))
        assertTrue(html.contains("render: ({ markers, position })"))
        assertTrue(html.contains("' radio stations.'"))
        assertTrue(html.contains("zoom_changed"))
        assertTrue(html.contains("zoomChanged"))
        assertTrue(html.contains("selectItem"))
        assertTrue(html.contains("playItem"))
        assertTrue(html.contains("postMapMessage('selectItem', station.id, null, null)"))
        assertTrue(html.contains("playSelectedRadioMapStation"))
        assertTrue(html.contains("postMapMessage('playItem', station.id, null, null)"))
        assertTrue(html.contains("window.updateRadioMapSelection(station.id);"))
        assertTrue(html.contains("selectionHomepageLink"))
        assertTrue(html.contains("selectionStreamLink"))
        assertTrue(html.contains("copySelectedRadioMapStreamUrl"))
        assertTrue(html.contains("openExternalRadioMapUrl"))
        assertTrue(html.contains("overflow-wrap: anywhere"))
        assertTrue(html.contains("row.style.display = 'grid'"))
        assertTrue(html.contains("\"streamUrl\": \"https://stream.example/kexp-1\""))
        assertTrue(html.contains("\"homepageUrl\": \"https://kexp.example/\""))
        assertTrue(html.contains("currentViewportPayload"))
        assertFalse(html.contains("userInteractedWithMap"))
        assertFalse(html.contains("id=\"searchArea\""))
        assertFalse(html.contains("id=\"searchAreaSpinner\""))
        assertFalse(html.contains("id=\"searchAreaLabel\""))
        assertFalse(html.contains("Search this area"))
        assertFalse(html.contains("Searching this area"))
        assertFalse(html.contains("setRadioMapSearchLoading"))
        assertFalse(html.contains("searchCurrentRadioMapArea"))
        assertFalse(html.contains("postMapMessage('searchArea'"))
        assertFalse(html.contains("postDesktopBridge('searchArea'"))
        assertTrue(html.contains("viewportChanged"))
        assertTrue(html.contains("bounds.getNorthEast()"))
        assertTrue(html.contains("Selected "))
        assertFalse(html.contains("postMapMessage('playItem', station.id, null, null);\n                } else"))
        assertTrue(html.contains("KEXP's One"))
        assertFalse(html.contains("KEXP\\'s One"))
        assertTrue(html.contains("window.parent.postMessage"))
        assertTrue(html.contains("Showing "))
        assertFalse(html.contains("Opening "))
        assertTrue(html.contains("if (station.isCluster)"))
        assertTrue(html.contains("postMapMessage('selectItem', station.id, null, null);"))
        assertTrue(html.contains("onClusterClick: (_event, cluster, map)"))
        assertTrue(html.contains("sourceCluster.id"))
        assertTrue(html.contains("return;"))
        assertFalse(html.contains("gmp-map-3d"))
        assertFalse(html.contains("Map3DElement"))
        assertFalse(html.contains("map.range ="))
    }
}

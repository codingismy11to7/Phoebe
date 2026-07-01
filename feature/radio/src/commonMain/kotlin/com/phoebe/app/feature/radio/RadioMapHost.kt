package com.phoebe.app.feature.radio

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.phoebe.app.domain.RadioMapViewport
import com.phoebe.app.domain.RadioStation
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

@Composable
internal expect fun RadioMapHost(
    items: List<RadioMapItem>,
    selectedItem: RadioMapItem?,
    startingStationIds: Set<String>,
    markerTintColor: Color,
    googleMapsApiKey: String?,
    onItemSelected: (RadioMapItem) -> Unit,
    onItemPlay: (RadioMapItem) -> Unit,
    onMapZoomChanged: (Double) -> Unit,
    onMapViewportChanged: (RadioMapViewport) -> Unit,
    modifier: Modifier = Modifier,
    fallback: @Composable (Modifier) -> Unit,
)

internal expect fun radioMapGoogleMapsApiKey(): String?

internal expect fun radioMapUsesExternalBrowser(): Boolean

internal expect fun radioMapUsesMinimalEmbeddedChrome(): Boolean

internal expect fun radioMapHostClustersMarkers(): Boolean

internal fun Color.toRadioMapCssHex(): String {
    val r = (red * 255f).toInt().coerceIn(0, 255)
    val g = (green * 255f).toInt().coerceIn(0, 255)
    val b = (blue * 255f).toInt().coerceIn(0, 255)
    return "#${r.toHexByte()}${g.toHexByte()}${b.toHexByte()}"
}

private fun Int.toHexByte(): String {
    val value = coerceIn(0, 255)
    val hex = "0123456789abcdef"
    return "${hex[value ushr 4]}${hex[value and 0x0f]}"
}

internal fun Color.toRadioMapArgbInt(): Int {
    val a = (alpha * 255f).toInt().coerceIn(0, 255)
    val r = (red * 255f).toInt().coerceIn(0, 255)
    val g = (green * 255f).toInt().coerceIn(0, 255)
    val b = (blue * 255f).toInt().coerceIn(0, 255)
    return (a shl 24) or (r shl 16) or (g shl 8) or b
}

internal fun radioMapHtml(
    items: List<RadioMapItem>,
    selectedItem: RadioMapItem?,
    googleMapsApiKey: String,
    markerTintCssHex: String,
    desktopBridgeBaseUrl: String? = null,
    useLightTheme: Boolean = false,
    startingStationIds: Set<String> = emptySet(),
): String {
    val markerJson = items.toRadioMapMarkerJson(prefix = "", postfix = "")
    val selected = selectedItem
        ?.let { """"${it.id.escapeJs()}"""" }
        ?: "null"
    val startingIdsJson = startingStationIds.toRadioMapStartingIdsJson()
    val desktopBridge = desktopBridgeBaseUrl
        ?.let { """"${it.trimEnd('/').escapeJs()}"""" }
        ?: "null"
    val bodyClass = if (useLightTheme) "theme-light" else "theme-dark"
    val mapBackground = if (useLightTheme) "#f3f4f7" else "#080b12"
    val mapTheme = if (useLightTheme) "light" else "dark"
    return """
        <!doctype html>
        <html>
        <head>
          <meta name="viewport" content="initial-scale=1, width=device-width" />
          <style>
            html, body, #map { width: 100%; height: 100%; margin: 0; background: $mapBackground; overflow: hidden; }
            body.theme-dark {
              --phoebe-map-popup-bg: rgba(13, 18, 29, 0.96);
              --phoebe-map-popup-border: rgba(255, 255, 255, 0.10);
              --phoebe-map-popup-text: #f4f5f7;
              --phoebe-map-popup-secondary: rgba(226, 232, 240, 0.78);
              --phoebe-map-popup-muted: rgba(226, 232, 240, 0.58);
              --phoebe-map-popup-action-text: #07111e;
              --phoebe-map-popup-secondary-action: rgba(255, 255, 255, 0.08);
              --phoebe-map-popup-shadow: rgba(0, 0, 0, 0.34);
            }
            body.theme-light {
              --phoebe-map-popup-bg: rgba(255, 255, 255, 0.96);
              --phoebe-map-popup-border: rgba(24, 27, 34, 0.10);
              --phoebe-map-popup-text: #181b22;
              --phoebe-map-popup-secondary: rgba(77, 85, 99, 0.92);
              --phoebe-map-popup-muted: rgba(122, 129, 144, 0.92);
              --phoebe-map-popup-action-text: #ffffff;
              --phoebe-map-popup-secondary-action: rgba(16, 24, 32, 0.06);
              --phoebe-map-popup-shadow: rgba(16, 24, 32, 0.18);
            }
            @keyframes phoebe-radio-map-spin {
              to { transform: rotate(360deg); }
            }
            #selection {
              position: fixed;
              left: 10px;
              right: 10px;
              bottom: 10px;
              z-index: 10;
              box-sizing: border-box;
              max-width: none;
              display: none;
              gap: 8px;
              padding: 12px;
              border-radius: 8px;
              border: 1px solid var(--phoebe-map-popup-border);
              background: var(--phoebe-map-popup-bg);
              color: var(--phoebe-map-popup-text);
              font: 12px/1.35 system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
              box-shadow: 0 12px 28px var(--phoebe-map-popup-shadow);
            }
            #selectionText { min-width: 0; width: 100%; }
            #selectionName { font-size: 14px; font-weight: 800; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
            #selectionMeta { color: var(--phoebe-map-popup-secondary); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
            #selectionSub { color: var(--phoebe-map-popup-muted); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
            #selectionActions { display: flex; align-items: center; justify-content: flex-end; gap: 6px; width: 100%; }
            #selection button {
              border: 0;
              border-radius: 999px;
              padding: 7px 10px;
              color: var(--phoebe-map-popup-action-text);
              background: ${markerTintCssHex.escapeJs()};
              font: 800 12px system-ui, -apple-system, sans-serif;
              cursor: pointer;
            }
            #selection button[disabled] {
              cursor: default;
              opacity: 0.92;
            }
            #selection button.secondary {
              color: var(--phoebe-map-popup-secondary);
              background: var(--phoebe-map-popup-secondary-action);
            }
            #selectionPlaySpinner {
              display: none;
              width: 11px;
              height: 11px;
              border-radius: 50%;
              border: 2px solid rgba(7, 17, 30, 0.24);
              border-top-color: var(--phoebe-map-popup-action-text);
              animation: phoebe-radio-map-spin 0.8s linear infinite;
            }
            #selectionPlay[data-starting="true"] {
              display: inline-flex;
              align-items: center;
              gap: 6px;
            }
            #selectionPlay[data-starting="true"] #selectionPlaySpinner { display: inline-block; }
            @media (min-width: 560px) {
              #selection {
                left: 12px;
                right: auto;
                bottom: 12px;
                width: min(520px, calc(100% - 24px));
              }
            }
          </style>
          <script>
            const markers = [$markerJson];
            const selectedId = $selected;
            const initialStartingStationIds = new Set($startingIdsJson);
            const markerTint = '${markerTintCssHex.escapeJs()}';
            const mapTheme = '$mapTheme';
            const mapBackground = '$mapBackground';
            const darkMapStyles = [
              { elementType: 'geometry', stylers: [{ color: '#121722' }] },
              { elementType: 'labels.text.fill', stylers: [{ color: '#d5dae5' }] },
              { elementType: 'labels.text.stroke', stylers: [{ color: '#121722' }] },
              { featureType: 'administrative', elementType: 'geometry.stroke', stylers: [{ color: '#374151' }] },
              { featureType: 'landscape', elementType: 'geometry', stylers: [{ color: '#151b27' }] },
              { featureType: 'poi', elementType: 'geometry', stylers: [{ color: '#1d2633' }] },
              { featureType: 'poi', elementType: 'labels.text.fill', stylers: [{ color: '#9aa4b5' }] },
              { featureType: 'road', elementType: 'geometry', stylers: [{ color: '#263244' }] },
              { featureType: 'road', elementType: 'geometry.stroke', stylers: [{ color: '#111827' }] },
              { featureType: 'road', elementType: 'labels.text.fill', stylers: [{ color: '#c4cad6' }] },
              { featureType: 'transit', elementType: 'geometry', stylers: [{ color: '#202a38' }] },
              { featureType: 'water', elementType: 'geometry', stylers: [{ color: '#0b1020' }] },
              { featureType: 'water', elementType: 'labels.text.fill', stylers: [{ color: '#8b95a7' }] },
            ];
            let map = null;
            let currentMarkers = [];
            let markersById = new Map();
            let stationById = new Map();
            let clusterer = null;
            let sourceMarkers = markers;
            let sourceSelectedId = selectedId;
            let sourceStartingStationIds = initialStartingStationIds;
            let selectedStationForAction = null;
            let currentMarkerDataSignature = null;
            const markerIconCache = new Map();
            const desktopBridgeBaseUrl = $desktopBridge;
            const setStatus = () => {};
            const stationMeta = (station) => {
              const pieces = [station.country, station.language, station.codec].filter((value) => value && String(value).trim().length > 0);
              return pieces.length > 0 ? pieces.join(' · ') : (station.approximate ? 'Approximate country location' : 'Station location');
            };
            const setSelectionStarting = (starting) => {
              const playButton = document.getElementById('selectionPlay');
              const playLabel = document.getElementById('selectionPlayLabel');
              const subLabel = document.getElementById('selectionSub');
              if (playButton) {
                playButton.dataset.starting = String(starting);
                playButton.disabled = Boolean(starting);
              }
              if (playLabel) playLabel.textContent = starting ? 'Starting' : 'Play';
              if (subLabel && selectedStationForAction) {
                subLabel.textContent = starting
                  ? 'Starting stream...'
                  : (selectedStationForAction.subtitle || (selectedStationForAction.approximate ? 'Approximate location' : 'Ready to play'));
              }
            };
            window.setRadioMapStartingStationIds = (ids) => {
              sourceStartingStationIds = new Set(Array.isArray(ids) ? ids.map(String) : []);
              if (selectedStationForAction) {
                setSelectionStarting(sourceStartingStationIds.has(String(selectedStationForAction.id)));
              }
            };
            const showSelection = (station) => {
              selectedStationForAction = station;
              const selection = document.getElementById('selection');
              if (!selection) return;
              document.getElementById('selectionName').textContent = station.name || 'Radio station';
              document.getElementById('selectionMeta').textContent = stationMeta(station);
              document.getElementById('selectionSub').textContent = station.subtitle || (station.approximate ? 'Approximate location' : 'Ready to play');
              setSelectionStarting(sourceStartingStationIds.has(String(station.id)));
              selection.style.display = 'block';
            };
            window.dismissRadioMapSelection = () => {
              selectedStationForAction = null;
              const selection = document.getElementById('selection');
              if (selection) selection.style.display = 'none';
            };
            window.playSelectedRadioMapStation = () => {
              const station = selectedStationForAction;
              if (!station) return;
              window.setRadioMapStartingStationIds([station.id]);
              if (window.parent && window.parent !== window) {
                postMapMessage('playItem', station.id, null, null);
              } else {
                window.PhoebeRadioMap?.playItem?.(station.id);
              }
              postDesktopBridge('play', station.id, null, null);
              setStatus('Starting ' + (station.name || 'radio station') + '.', true);
            };
            const currentViewportPayload = () => {
              if (!map || !map.getBounds) return null;
              const bounds = map.getBounds();
              const zoom = Number(map.getZoom());
              if (!bounds || !Number.isFinite(zoom)) return null;
              const northEast = bounds.getNorthEast();
              const southWest = bounds.getSouthWest();
              return {
                north: northEast.lat(),
                south: southWest.lat(),
                east: northEast.lng(),
                west: southWest.lng(),
                zoom,
              };
            };
            const postMapMessage = (action, itemId, zoom, viewport) => {
              if (!window.parent || window.parent === window) return;
              window.parent.postMessage({
                phoebeRadioMap: true,
                action,
                itemId,
                zoom,
                viewport,
              }, '*');
            };
            const postDesktopBridge = (action, itemId, zoom, viewport) => {
              if (!desktopBridgeBaseUrl) return;
              const params = new URLSearchParams();
              if (itemId) params.set('id', itemId);
              if (Number.isFinite(Number(zoom))) params.set('zoom', String(zoom));
              if (viewport) {
                params.set('north', String(viewport.north));
                params.set('south', String(viewport.south));
                params.set('east', String(viewport.east));
                params.set('west', String(viewport.west));
              }
              fetch(desktopBridgeBaseUrl + '/' + action + '?' + params.toString()).catch(() => {});
            };
            const visibleMapMarkers = (items) => items.filter((item) =>
              Number.isFinite(Number(item.lat)) && Number.isFinite(Number(item.lng))
            );
            const representedStationCount = (items) => items.reduce((total, item) => {
              const count = Number(item.count);
              return total + (Number.isFinite(count) && count > 0 ? count : 1);
            }, 0);
            const representedMarkerCount = (markers) => markers.reduce((total, marker) => {
              const count = Number(marker?.phoebeStation?.count);
              return total + (Number.isFinite(count) && count > 0 ? count : 1);
            }, 0);
            const representedMarkerPositions = (markers) => (markers || [])
              .map((marker) => marker?.phoebeStation)
              .filter((station) => station != null)
              .flatMap((station) => clusterPositions(station));
            const clusterPositions = (station) => {
              if (station.isCluster && Array.isArray(station.children) && station.children.length > 0) {
                return station.children
                  .flatMap((child) => clusterPositions(child));
              }
              if (Number.isFinite(Number(station.lat)) && Number.isFinite(Number(station.lng))) {
                return [{ lat: Number(station.lat), lng: Number(station.lng) }];
              }
              return [];
            };
            const focusMapOnPositions = (positions) => {
              if (!map || positions.length === 0) return false;
              if (positions.length === 1) {
                map.setCenter(positions[0]);
                map.setZoom(Math.max(Number(map.getZoom()) || 2, 14));
                return true;
              }
              const bounds = new google.maps.LatLngBounds();
              positions.forEach((position) => bounds.extend(position));
              if (bounds.isEmpty()) return false;
              map.fitBounds(bounds, 48);
              const listener = map.addListener('idle', () => {
                google.maps.event.removeListener(listener);
                const zoom = Number(map.getZoom());
                if (Number.isFinite(zoom) && zoom > 18) {
                  map.setZoom(18);
                }
              });
              return true;
            };
            const focusMapOnStation = (station) => focusMapOnPositions(clusterPositions(station));
            const markerIcon = (station, selected) => {
              const count = station.isCluster ? String(station.count) : '';
              const cacheKey = [count, selected ? '1' : '0', station.approximate ? '1' : '0'].join('|');
              const cached = markerIconCache.get(cacheKey);
              if (cached) return cached;
              const digitCount = count.length;
              const size = station.isCluster ? Math.max(28, 18 + digitCount * 7) : (selected ? 18 : 14);
              const opacity = station.approximate ? 0.7 : 1;
              const stroke = selected ? '#ffffff' : '#050b18';
              const strokeWidth = selected ? 3 : 1.5;
              const radius = Math.max(2, (size - strokeWidth) / 2 - 1);
              const fontSize = digitCount >= 4 ? 10 : 11;
              const label = station.isCluster
                ? '<text x="50%" y="54%" text-anchor="middle" dominant-baseline="middle" fill="#07111e" font-size="' + fontSize + '" font-weight="800" font-family="system-ui, -apple-system, sans-serif">' + count + '</text>'
                : '';
              const svg = '<svg xmlns="http://www.w3.org/2000/svg" width="' + size + '" height="' + size + '" viewBox="0 0 ' + size + ' ' + size + '">' +
                '<circle cx="' + (size / 2) + '" cy="' + (size / 2) + '" r="' + radius + '" fill="' + markerTint + '" fill-opacity="' + opacity + '" stroke="' + stroke + '" stroke-opacity="0.75" stroke-width="' + strokeWidth + '"/>' +
                label +
                '</svg>';
              const icon = {
                url: 'data:image/svg+xml;charset=UTF-8,' + encodeURIComponent(svg),
                scaledSize: new google.maps.Size(size, size),
                anchor: new google.maps.Point(size / 2, size / 2),
              };
              markerIconCache.set(cacheKey, icon);
              return icon;
            };
            const markerDataSignature = (items) => items
              .map((item) => [item.id, item.lat, item.lng, item.count, item.approximate ? 1 : 0, item.isCluster ? 1 : 0].join('@'))
              .join('|');
            const indexStation = (station) => {
              if (!station) return;
              stationById.set(String(station.id), station);
              if (Array.isArray(station.children)) {
                station.children.forEach(indexStation);
              }
            };
            const updateMarkerIcon = (itemId) => {
              if (itemId == null) return;
              const marker = markersById.get(String(itemId));
              const station = marker?.phoebeStation;
              if (!marker || !station) return;
              marker.setIcon(markerIcon(station, String(station.id) === String(sourceSelectedId)));
            };
            window.updateRadioMapSelection = (selectedId) => {
              const previousSelectedId = sourceSelectedId == null ? null : String(sourceSelectedId);
              const nextSelectedId = selectedId == null ? null : String(selectedId);
              sourceSelectedId = nextSelectedId;
              if (previousSelectedId !== nextSelectedId) {
                updateMarkerIcon(previousSelectedId);
                updateMarkerIcon(nextSelectedId);
              }
              if (nextSelectedId == null && selectedStationForAction) {
                window.dismissRadioMapSelection();
              }
            };
            const makeMarker = (station) => {
              const selected = sourceSelectedId != null && String(station.id) === String(sourceSelectedId);
              const marker = new google.maps.Marker({
                map,
                position: { lat: station.lat, lng: station.lng },
                title: station.name,
                icon: markerIcon(station, selected),
                optimized: true,
              });
              marker.phoebeStation = station;
              const handleClick = () => {
                const targetLabel = station.isCluster ? station.count + ' stations' : station.name;
                if (station.isCluster) {
                  if (window.parent && window.parent !== window) {
                    postMapMessage('selectItem', station.id, null, null);
                  } else {
                    window.PhoebeRadioMap?.selectItem?.(station.id);
                  }
                  postDesktopBridge('select', station.id, null, null);
                  focusMapOnStation(station);
                  setStatus('Showing ' + targetLabel + '.', true);
                  return;
                }
                if (window.parent && window.parent !== window) {
                  postMapMessage('selectItem', station.id, null, null);
                } else {
                  window.PhoebeRadioMap?.selectItem?.(station.id);
                }
                postDesktopBridge('select', station.id, null, null);
                window.webkit?.messageHandlers?.phoebeRadioMap?.postMessage?.(station.id);
                window.AndroidPhoebeRadioMap?.selectStation?.(station.id);
                window.updateRadioMapSelection(station.id);
                showSelection(station);
                setStatus('Selected ' + targetLabel + '.', true);
              };
              marker.addListener('click', handleClick);
              return marker;
            };
            window.initRadioMap = async () => {
              try {
                setStatus('Loading radio stations...');
                const [{ Map: GoogleMap }, { MarkerClusterer, SuperClusterAlgorithm }] = await Promise.all([
                  google.maps.importLibrary('maps'),
                  import('https://cdn.jsdelivr.net/npm/@googlemaps/markerclusterer/+esm'),
                ]);
                map = new GoogleMap(document.getElementById('map'), {
                  center: { lat: 20, lng: 0 },
                  zoom: 2,
                  mapTypeId: 'roadmap',
                  backgroundColor: mapBackground,
                  styles: mapTheme === 'dark' ? darkMapStyles : null,
                  fullscreenControl: false,
                  mapTypeControl: false,
                  streetViewControl: false,
                });
                map.addListener('zoom_changed', () => {
                  const zoom = Number(map.getZoom());
                  if (!Number.isFinite(zoom)) return;
                  window.PhoebeRadioMap?.zoomChanged?.(zoom);
                  postDesktopBridge('zoom', null, zoom, currentViewportPayload());
                  if (window.parent && window.parent !== window) {
                    postMapMessage('zoomChanged', null, zoom, currentViewportPayload());
                  }
                });
                map.addListener('idle', () => {
                  const viewport = currentViewportPayload();
                  if (!viewport) return;
                  window.PhoebeRadioMap?.viewportChanged?.(viewport.north, viewport.south, viewport.east, viewport.west, viewport.zoom);
                  postDesktopBridge('viewport', null, viewport.zoom, viewport);
                  if (window.parent && window.parent !== window) {
                    postMapMessage('viewportChanged', null, viewport.zoom, viewport);
                  }
                });
                window.updateRadioMapMarkers = (newMarkers, selectedId) => {
                  const visibleMarkers = visibleMapMarkers(newMarkers || []);
                  const nextSignature = markerDataSignature(visibleMarkers);
                  if (nextSignature === currentMarkerDataSignature) {
                    window.updateRadioMapSelection(selectedId);
                    return;
                  }
                  sourceMarkers = newMarkers || [];
                  sourceSelectedId = selectedId == null ? null : String(selectedId);
                  currentMarkerDataSignature = nextSignature;
                  currentMarkers.forEach(marker => {
                    google.maps.event.clearInstanceListeners(marker);
                    marker.setMap(null);
                  });
                  markersById = new Map();
                  stationById = new Map();
                  visibleMarkers.forEach(indexStation);
                  currentMarkers = visibleMarkers.map(station => {
                    const marker = makeMarker(station);
                    markersById.set(String(station.id), marker);
                    return marker;
                  });
                  if (clusterer) clusterer.clearMarkers();
                  clusterer = new MarkerClusterer({
                    map,
                    markers: currentMarkers,
                    algorithm: new SuperClusterAlgorithm({ maxZoom: 11 }),
                    renderer: {
                      render: ({ markers, position }) => {
                        const count = representedMarkerCount(markers || []);
                        return new google.maps.Marker({
                          position,
                          icon: markerIcon({ isCluster: true, count, approximate: false }, false),
                          title: count + ' stations',
                          optimized: true,
                        });
                      },
                    },
                    onClusterClick: (_event, cluster, map) => {
                      const markers = cluster?.markers || [];
                      const sourceCluster = markers
                        .map(marker => marker?.phoebeStation)
                        .find(station => station && station.isCluster);
                      if (sourceCluster) {
                        if (window.parent && window.parent !== window) {
                          postMapMessage('selectItem', sourceCluster.id, null, null);
                        } else {
                          window.PhoebeRadioMap?.selectItem?.(sourceCluster.id);
                        }
                        postDesktopBridge('select', sourceCluster.id, null, null);
                      }
                      const markerPositions = representedMarkerPositions(markers);
                      if (!focusMapOnPositions(markerPositions)) {
                        map.setCenter(cluster.position);
                        map.setZoom(Math.min((map.getZoom() || 2) + 2, 18));
                      }
                    },
                  });
                  if (sourceSelectedId != null && !stationById.has(String(sourceSelectedId))) {
                    window.updateRadioMapSelection(null);
                  }
                  setStatus('Loaded ' + representedStationCount(sourceMarkers) + ' radio stations.', true);
                };
                const parentMap = window.parent && window.parent.PhoebeRadioMap;
                let markersToLoad = markers;
                let selectedIdToLoad = selectedId;
                let startingIdsToLoad = initialStartingStationIds;
                if (window.PhoebeRadioMapLatest && window.PhoebeRadioMapLatest.markers) {
                  markersToLoad = window.PhoebeRadioMapLatest.markers;
                  selectedIdToLoad = window.PhoebeRadioMapLatest.selectedId;
                  startingIdsToLoad = new Set(window.PhoebeRadioMapLatest.startingIds || []);
                }
                if (parentMap && parentMap.getLatestData) {
                  const latest = parentMap.getLatestData();
                  if (latest && latest.markers) {
                    markersToLoad = latest.markers;
                    selectedIdToLoad = latest.selectedId;
                    startingIdsToLoad = new Set(latest.startingIds || []);
                  }
                }
                window.setRadioMapStartingStationIds(Array.from(startingIdsToLoad));
                window.updateRadioMapMarkers(markersToLoad, selectedIdToLoad);
              } catch (error) {
                console.error('Phoebe radio map failed to render markers', error);
                setStatus('Could not render radio markers: ' + (error?.message || error));
              }
            };
          </script>
          <script async defer src="https://maps.googleapis.com/maps/api/js?key=${googleMapsApiKey.escapeJs()}&v=weekly&loading=async&callback=initRadioMap"></script>
        </head>
        <body class="$bodyClass">
          <div id="map"></div>
          <div id="selection" role="status">
            <div id="selectionText">
              <div id="selectionName">Radio station</div>
              <div id="selectionMeta">Station location</div>
              <div id="selectionSub">Ready to play</div>
            </div>
            <div id="selectionActions">
              <button id="selectionPlay" type="button" onclick="window.playSelectedRadioMapStation()">
                <span id="selectionPlaySpinner"></span>
                <span id="selectionPlayLabel">Play</span>
              </button>
              <button class="secondary" type="button" onclick="window.dismissRadioMapSelection()">Close</button>
            </div>
          </div>
        </body>
        </html>
    """.trimIndent()
}

internal fun Set<String>.toRadioMapStartingIdsJson(): String =
    joinToString(prefix = "[", postfix = "]", separator = ",") { id ->
        "\"${id.escapeJs()}\""
    }

internal fun List<RadioMapItem>.toRadioMapMarkerJson(
    prefix: String = "[",
    postfix: String = "]",
): String =
    joinToString(prefix = prefix, postfix = postfix, separator = ",") { item ->
        item.toRadioMapMarkerJson()
    }

private fun RadioMapItem.toRadioMapMarkerJson(): String {
    val childrenJson = if (this is RadioMapItem.Cluster) {
        stations.mapIndexed { index, station ->
            station.toRadioMapChildMarkerJson(
                parent = this,
                index = index,
                count = stations.size,
            )
        }.joinToString(prefix = "[", postfix = "]", separator = ",")
    } else {
        "[]"
    }
    return """
        {
          "id": "${id.escapeJs()}",
          "name": "${name.escapeJs()}",
          "lat": $latitude,
          "lng": $longitude,
          "approximate": $approximate,
          "isCluster": $isCluster,
          "count": $clusterCount,
          ${radioMapStationInfoJsonLine()}
          "children": $childrenJson
        }
    """.trimIndent()
}

private fun RadioMapItem.radioMapStationInfoJsonLine(): String =
    when (this) {
        is RadioMapItem.Station -> """
          "country": "${station.countryCode.orEmpty().escapeJs()}",
          "language": "${station.language.orEmpty().escapeJs()}",
          "codec": "${station.codec.orEmpty().escapeJs()}",
          "subtitle": "${station.displaySubtitle.escapeJs()}",
        """.trimIndent()
        is RadioMapItem.Cluster -> ""
    }

private fun RadioStation.toRadioMapChildMarkerJson(
    parent: RadioMapItem.Cluster,
    index: Int,
    count: Int,
): String {
    val location = if (parent.approximate) {
        radioMapSpiderfyLocation(
            latitude = parent.latitude,
            longitude = parent.longitude,
            index = index,
            count = count,
        )
    } else {
        (geoLat ?: parent.latitude) to (geoLong ?: parent.longitude)
    }
    return """
        {
          "id": "${id.escapeJs()}",
          "name": "${name.escapeJs()}",
          "lat": ${location.first},
          "lng": ${location.second},
          "approximate": ${parent.approximate},
          "isCluster": false,
          "count": 1,
          "country": "${countryCode.orEmpty().escapeJs()}",
          "language": "${language.orEmpty().escapeJs()}",
          "codec": "${codec.orEmpty().escapeJs()}",
          "subtitle": "${displaySubtitle.escapeJs()}",
          "children": []
        }
    """.trimIndent()
}

private fun radioMapSpiderfyLocation(
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
    return (latitude + latOffset).coerceIn(-90.0, 90.0) to radioMapWrapLongitude(longitude + lngOffset)
}

private fun radioMapWrapLongitude(longitude: Double): Double {
    var wrapped = longitude
    while (wrapped > 180.0) wrapped -= 360.0
    while (wrapped < -180.0) wrapped += 360.0
    return wrapped
}

internal fun String.escapeJs(): String =
    buildString(length) {
        this@escapeJs.forEach { char ->
            when (char) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(char)
            }
        }
    }

package com.phoebe.app.player

import platform.UIKit.UIView

interface IosRadioMapNativeViewFactory {
    fun create(
        markersJson: String,
        selectedStationId: String?,
        markerTintArgb: Int,
        useLightTheme: Boolean,
        googleMapsApiKey: String,
        onMarkerSelected: (String) -> Unit,
        onMarkerPlay: (String) -> Unit,
        onMapZoomChanged: (Double) -> Unit,
        onMapViewportChanged: (Double, Double, Double, Double, Double) -> Unit,
    ): UIView

    fun update(
        view: UIView,
        markersJson: String,
        selectedStationId: String?,
        markerTintArgb: Int,
        useLightTheme: Boolean,
        googleMapsApiKey: String,
    )
}

object IosRadioMapNativeBridge {
    var factory: IosRadioMapNativeViewFactory? = null
}

package com.phoebe.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect

@Composable
internal actual fun RadioMapOverlayOcclusionEffect(sheetTopPx: Float?) {
    SideEffect {
        setPhoebeRadioMapOcclusionTop(sheetTopPx?.toDouble() ?: -1.0)
    }
    DisposableEffect(Unit) {
        onDispose {
            setPhoebeRadioMapOcclusionTop(-1.0)
        }
    }
}

@OptIn(ExperimentalWasmJsInterop::class)
@JsFun(
    """
    (sheetTopPx) => {
      const bridgeHost = typeof window !== "undefined" ? window : globalThis;
      const bridge = bridgeHost.PhoebeRadioMap || {};
      bridgeHost.PhoebeRadioMap = bridge;
      globalThis.PhoebeRadioMap = bridge;
      const parsed = Number(sheetTopPx);
      bridge.occlusionTopPx = Number.isFinite(parsed) && parsed >= 0 ? parsed : null;
      const scale = window.devicePixelRatio || 1;

      const applyBounds = (iframe) => {
        const requestedLeft = Number(iframe.dataset.phoebeMapLeft || 0);
        const requestedTop = Number(iframe.dataset.phoebeMapTop || 0);
        const requestedWidth = Number(iframe.dataset.phoebeMapWidth || 0);
        const requestedHeight = Number(iframe.dataset.phoebeMapHeight || 0);
        const requestedRight = requestedLeft + requestedWidth;
        const requestedBottom = requestedTop + requestedHeight;
        const viewportRight = window.innerWidth || requestedRight;
        const viewportBottom = window.innerHeight || requestedBottom;
        const occlusionTop = bridge.occlusionTopPx == null ? null : Math.max(0, Number(bridge.occlusionTopPx) / scale);
        const clippedRight = Math.min(viewportRight, requestedRight);
        const clippedBottom = Math.min(viewportBottom, requestedBottom, occlusionTop == null ? requestedBottom : occlusionTop);
        const clippedWidth = Math.max(0, clippedRight - requestedLeft);
        const clippedHeight = Math.max(0, clippedBottom - requestedTop);
        iframe.style.left = requestedLeft + "px";
        iframe.style.top = requestedTop + "px";
        iframe.style.width = clippedWidth + "px";
        iframe.style.height = clippedHeight + "px";
        iframe.style.display = clippedWidth > 0 && clippedHeight > 0 ? "block" : "none";
        if (iframe.contentWindow) {
          iframe.contentWindow.dispatchEvent(new Event("resize"));
        }
      };

      document.querySelectorAll('iframe[id^="phoebe-radio-map-"]').forEach(applyBounds);
    }
    """,
)
private external fun setPhoebeRadioMapOcclusionTop(sheetTopPx: Double)

package com.phoebe.app

import com.phoebe.app.data.RecommendedRadioStations
import kotlin.test.Test
import kotlin.test.assertEquals

class RecommendedRadioStationsTest {
    @Test
    fun kyotoConnectionCarriesMp3CodecHintForExtensionlessLiveUrl() {
        val station = RecommendedRadioStations.first { it.id == "recommended:the-kyoto-connection" }

        assertEquals("https://server.laradio.online:59009/live", station.streamUrl)
        assertEquals("mp3", station.codec)
    }
}

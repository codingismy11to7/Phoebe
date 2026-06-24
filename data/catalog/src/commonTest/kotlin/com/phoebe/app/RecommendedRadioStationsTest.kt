package com.phoebe.app

import com.phoebe.app.data.RecommendedRadioStations
import com.phoebe.app.domain.RadioNowPlayingSourceType
import kotlin.test.Test
import kotlin.test.assertEquals

class RecommendedRadioStationsTest {
    @Test
    fun kyotoConnectionCarriesMp3CodecHintForExtensionlessLiveUrl() {
        val station = RecommendedRadioStations.first { it.id == "recommended:the-kyoto-connection" }

        assertEquals("https://server.laradio.online:59009/live", station.streamUrl)
        assertEquals("mp3", station.codec)
    }

    @Test
    fun knownRecommendedStreamsCarryNowPlayingApiHints() {
        val bbc = RecommendedRadioStations.first { it.id == "recommended:bbc-radio-6-music" }
        val kexp = RecommendedRadioStations.first { it.id == "recommended:kexp-90-3" }

        assertEquals(RadioNowPlayingSourceType.BbcRmsSegments, bbc.nowPlayingSource?.type)
        assertEquals(
            "https://rms.api.bbc.co.uk/v2/services/bbc_6music/segments/latest?experience=domestic&limit=1",
            bbc.nowPlayingSource?.url,
        )
        assertEquals("https://kexp.streamguys1.com/kexp128.mp3", kexp.streamUrl)
        assertEquals("mp3", kexp.codec)
        assertEquals(RadioNowPlayingSourceType.KexpPlays, kexp.nowPlayingSource?.type)
        assertEquals("https://api.kexp.org/v2/plays/?limit=1&ordering=-airdate", kexp.nowPlayingSource?.url)
    }
}

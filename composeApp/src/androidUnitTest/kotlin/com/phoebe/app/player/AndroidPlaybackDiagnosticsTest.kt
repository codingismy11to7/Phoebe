package com.phoebe.app.player

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AndroidPlaybackDiagnosticsTest {
    @Test
    fun media3LoadControlKeepsMainPlaybackBufferBounded() {
        val profile = PhoebeLoadControlConfig.profileFor(PlaybackEnginePath.Media3)

        assertEquals(PhoebeLoadControlConfig.MainMinBufferMs, profile.minBufferMs)
        assertEquals(PhoebeLoadControlConfig.MainMaxBufferMs, profile.maxBufferMs)
        assertEquals(PhoebeLoadControlConfig.MainTargetBufferBytes, profile.targetBufferBytes)
        assertTrue(profile.maxBufferMs <= PhoebeLoadControlConfig.MainMaxBufferMs)
        assertTrue(profile.targetBufferBytes <= PhoebeLoadControlConfig.MainTargetBufferBytes)
    }

    @Test
    fun media3LoadControlTightensBuffersOnConstrainedNetwork() {
        val wifi = PhoebeLoadControlConfig.profileFor(PlaybackEnginePath.Media3, constrainedNetwork = false)
        val cellular = PhoebeLoadControlConfig.profileFor(PlaybackEnginePath.Media3, constrainedNetwork = true)

        assertTrue(cellular.maxBufferMs < wifi.maxBufferMs)
        assertTrue(cellular.targetBufferBytes < wifi.targetBufferBytes)
        assertEquals(PhoebeLoadControlConfig.ConstrainedMainMaxBufferMs, cellular.maxBufferMs)
        assertEquals(PhoebeLoadControlConfig.ConstrainedMainTargetBufferBytes, cellular.targetBufferBytes)
    }

    @Test
    fun media3CrossfadeUsesShortLivedBufferProfile() {
        val main = PhoebeLoadControlConfig.profileFor(PlaybackEnginePath.Media3)
        val crossfade = PhoebeLoadControlConfig.profileFor(PlaybackEnginePath.Media3Crossfade)

        assertTrue(crossfade.minBufferMs < main.minBufferMs)
        assertTrue(crossfade.maxBufferMs < main.maxBufferMs)
        assertTrue(crossfade.targetBufferBytes < main.targetBufferBytes)
        assertTrue(crossfade.maxBufferMs <= 12_000)
    }

    @Test
    fun bufferDurationsRemainValidForMedia3Builder() {
        val profiles = listOf(
            PhoebeLoadControlConfig.profileFor(PlaybackEnginePath.Media3),
            PhoebeLoadControlConfig.profileFor(PlaybackEnginePath.Media3Crossfade),
        )

        profiles.forEach { profile ->
            assertTrue(profile.minBufferMs >= PhoebeLoadControlConfig.BufferForPlaybackAfterRebufferMs)
            assertTrue(profile.maxBufferMs >= profile.minBufferMs)
            assertTrue(profile.targetBufferBytes > 0)
        }
    }
}

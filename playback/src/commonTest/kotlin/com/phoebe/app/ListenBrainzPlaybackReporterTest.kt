package com.phoebe.app

import com.phoebe.app.data.ListenBrainzClient
import com.phoebe.app.data.ListenBrainzPlaybackReporter
import com.phoebe.app.domain.AppSettings
import com.phoebe.app.domain.EqualizerProfile
import com.phoebe.app.domain.ListenBrainzSettings
import com.phoebe.app.domain.PlayerState
import com.phoebe.app.domain.RepeatMode
import com.phoebe.app.domain.Track
import com.phoebe.app.player.AudioPlayer
import com.phoebe.app.platform.SecureCredentialKey
import com.phoebe.app.testing.FakeListenBrainzAccountActions
import com.phoebe.app.testing.FakeSecureCredentialStore
import com.phoebe.app.testing.testHttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.yield
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ListenBrainzPlaybackReporterTest {
    @Test
    fun submitsPermanentListensForEveryPlaybackSourceAfterAudibleThreshold() = runTest {
        val fixture = newFixture(
            settings = connectedSettings(submitNowPlaying = false),
        )
        val tracks = listOf(
            track(id = "local:track-1", title = "Local Song", streamUrl = "file:///music/local.flac"),
            track(id = "plex:46171", title = "Plex Song", streamUrl = "https://plex.example/audio.mp3"),
            track(id = "jellyfin:abc", title = "Jellyfin Song", streamUrl = "https://jellyfin.example/audio.mp3"),
            track(id = "emby:def", title = "Emby Song", streamUrl = "https://emby.example/audio.mp3"),
            track(id = "navidrome:ghi", title = "Navidrome Song", streamUrl = "https://navidrome.example/audio.mp3"),
        )

        startReporter(fixture)
        tracks.forEachIndexed { index, track ->
            fixture.audioPlayer.playState(track = track, isPlaying = true)
            runCurrent()
            fixture.clock.advance(91_000L)
            fixture.audioPlayer.playState(track = track, isPlaying = true, positionMs = 91_000L)
            runCurrent()
            fixture.requests.awaitSingleCount(index + 1)
            awaitCondition { fixture.account.listenSubmittedCount >= index + 1 }
            fixture.audioPlayer.stopState()
            runCurrent()
        }

        val singleBodies = fixture.requests.value.singleBodies()
        tracks.forEach { track ->
            assertTrue(
                singleBodies.any { body -> body.contains(""""phoebe_track_id":"${track.id}"""") },
                "Expected ListenBrainz listen body for ${track.id}",
            )
        }
        awaitCondition { fixture.account.listenSubmittedCount == tracks.size }
        assertEquals(tracks.size, fixture.account.listenSubmittedCount)
    }

    @Test
    fun seekingNearTheEndWithoutEnoughAudibleTimeDoesNotSubmitPermanentListen() = runTest {
        val fixture = newFixture(
            settings = connectedSettings(submitNowPlaying = false),
        )
        val first = track(id = "local:track-1", title = "Skipped Song")
        val second = track(id = "local:track-2", title = "Next Song")

        startReporter(fixture)
        fixture.audioPlayer.playState(track = first, queue = listOf(first, second), isPlaying = true)
        runCurrent()
        fixture.clock.advance(5_000L)
        fixture.audioPlayer.playState(
            track = first,
            queue = listOf(first, second),
            isPlaying = true,
            positionMs = 170_000L,
        )
        runCurrent()
        fixture.audioPlayer.playState(track = second, queue = listOf(first, second), currentIndex = 1, isPlaying = true)
        runCurrent()

        assertEquals(0, fixture.requests.value.singleBodies().size)
        assertEquals(0, fixture.account.listenSubmittedCount)
    }

    @Test
    fun trackChangeAfterAudibleThresholdSubmitsPreviousListenEvenWithoutPositionTick() = runTest {
        val fixture = newFixture(
            settings = connectedSettings(submitNowPlaying = false),
        )
        val first = track(id = "local:track-1", title = "First Song")
        val second = track(id = "local:track-2", title = "Second Song")

        startReporter(fixture)
        fixture.audioPlayer.playState(track = first, queue = listOf(first, second), isPlaying = true)
        runCurrent()
        fixture.clock.advance(91_000L)
        fixture.audioPlayer.playState(track = second, queue = listOf(first, second), currentIndex = 1, isPlaying = true)
        runCurrent()
        fixture.requests.awaitSingleCount(1)

        val listen = fixture.requests.value.singleBodies().single()
        assertTrue(listen.contains("First Song"))
        assertFalse(listen.contains("Second Song"))
    }

    @Test
    fun stoppingAtEndAfterAudibleThresholdSubmitsListenAndClearsPlayingNow() = runTest {
        val fixture = newFixture(
            settings = connectedSettings(submitCurrentTrackFeedback = false),
        )
        val track = track(id = "local:track-1", title = "Finished Song")

        startReporter(fixture)
        fixture.audioPlayer.playState(track = track, isPlaying = true)
        runCurrent()
        fixture.requests.awaitSize(1)
        awaitCondition { fixture.account.nowPlayingSubmittedCount == 1 }
        fixture.clock.advance(91_000L)
        fixture.audioPlayer.playState(
            track = track,
            isPlaying = false,
            positionMs = track.durationMs,
        )
        runCurrent()
        fixture.requests.awaitSize(3)

        assertEquals(1, fixture.requests.value.playingNowBodies().size, "requests=${fixture.requests.value}")
        assertEquals(1, fixture.requests.value.singleBodies().size, "requests=${fixture.requests.value}")
        assertTrue(fixture.requests.value.any { it.path.endsWith("/playing-now/delete") })
        assertEquals(1, fixture.account.listenSubmittedCount)
    }

    @Test
    fun pausedTimeDoesNotCountTowardPermanentListenThreshold() = runTest {
        val fixture = newFixture(
            settings = connectedSettings(submitNowPlaying = false),
        )
        val track = track()

        startReporter(fixture)
        fixture.audioPlayer.playState(track = track, isPlaying = true)
        runCurrent()
        fixture.clock.advance(30_000L)
        fixture.audioPlayer.playState(track = track, isPlaying = false, positionMs = 30_000L)
        runCurrent()
        fixture.clock.advance(120_000L)
        fixture.audioPlayer.playState(track = track, isPlaying = false, positionMs = 30_000L)
        runCurrent()
        assertEquals(0, fixture.requests.value.singleBodies().size)

        fixture.audioPlayer.playState(track = track, isPlaying = true, positionMs = 30_000L)
        runCurrent()
        fixture.clock.advance(59_000L)
        fixture.audioPlayer.playState(track = track, isPlaying = true, positionMs = 89_000L)
        runCurrent()
        assertEquals(0, fixture.requests.value.singleBodies().size)

        fixture.clock.advance(1_000L)
        fixture.audioPlayer.playState(track = track, isPlaying = true, positionMs = 90_000L)
        runCurrent()
        fixture.requests.awaitSingleCount(1)
    }

    @Test
    fun missingTokenDoesNotSubmitNowPlayingOrPermanentListen() = runTest {
        val fixture = newFixture(
            settings = connectedSettings(),
            token = null,
        )
        val track = track()

        startReporter(fixture)
        fixture.audioPlayer.playState(track = track, isPlaying = true)
        runCurrent()
        fixture.clock.advance(91_000L)
        fixture.audioPlayer.playState(track = track, isPlaying = true, positionMs = 91_000L)
        runCurrent()

        assertEquals(emptyList(), fixture.requests.value)
        assertEquals(0, fixture.account.nowPlayingSubmittedCount)
        assertEquals(0, fixture.account.listenSubmittedCount)
    }

    @Test
    fun failedFutureListenIsQueuedForRetry() = runTest {
        val failSingle = MutableStateFlow(true)
        val fixture = newFixture(
            settings = connectedSettings(submitNowPlaying = false),
            failSingle = failSingle,
        )

        startReporter(fixture)
        fixture.audioPlayer.playState(track = track(), isPlaying = true)
        runCurrent()
        fixture.clock.advance(91_000L)
        fixture.audioPlayer.playState(track = track(), isPlaying = true, positionMs = 91_000L)
        runCurrent()
        fixture.requests.awaitSingleCount(1)
        fixture.reporter.awaitQueuedRetryCount(1)
        assertEquals(1, fixture.account.listenFailureCount)
    }

    @Test
    fun unauthorizedListenDeletesTokenAndDisconnects() = runTest {
        val fixture = newFixture(
            settings = connectedSettings(submitNowPlaying = false),
            singleStatus = HttpStatusCode.Unauthorized,
        )

        startReporter(fixture)
        fixture.audioPlayer.playState(track = track(), isPlaying = true)
        runCurrent()
        fixture.clock.advance(91_000L)
        fixture.audioPlayer.playState(track = track(), isPlaying = true, positionMs = 91_000L)
        runCurrent()
        fixture.requests.awaitSingleCount(1)
        fixture.settings.awaitSettings { !it.listenBrainz.connected }

        assertEquals(1, fixture.account.disconnectCount)
        assertFalse(fixture.credentialStore.values.containsKey(SecureCredentialKey.ListenBrainzUserToken))
    }

    private suspend fun newFixture(
        settings: ListenBrainzSettings = connectedSettings(),
        token: String? = "token",
        failSingle: StateFlow<Boolean> = MutableStateFlow(false),
        singleStatus: HttpStatusCode = HttpStatusCode.OK,
    ): ReporterFixture {
        val clock = TestClock()
        val requests = MutableStateFlow<List<ListenBrainzRequest>>(emptyList())
        val credentialStore = FakeSecureCredentialStore()
        if (token != null) {
            credentialStore.write(SecureCredentialKey.ListenBrainzUserToken, token)
        }
        val settingsState = MutableStateFlow(AppSettings(listenBrainz = settings))
        val account = FakeListenBrainzAccountActions(
            settings = settingsState,
            credentialStore = credentialStore,
            nowMs = { clock.nowMs },
        )
        val httpClient = testHttpClient(
            MockEngine { request ->
                val body = request.bodyText()
                requests.update {
                    it + ListenBrainzRequest(
                        path = request.url.encodedPath,
                        returnMsid = request.url.parameters["return_msid"],
                        body = body,
                    )
                }
                when {
                    request.url.encodedPath.endsWith("/playing-now/delete") -> respondJson("{}")
                    body.contains("playing_now") -> respondJson("""{"recording_msid":"msid-1"}""")
                    failSingle.value -> respond("", HttpStatusCode.InternalServerError)
                    else -> respondJson("""{"status":"ok"}""", singleStatus)
                }
            },
        )
        val client = ListenBrainzClient(httpClient, baseUrl = "https://listenbrainz.example")
        val audioPlayer = FakeAudioPlayer()
        return ReporterFixture(
            reporter = ListenBrainzPlaybackReporter(
                client = client,
                credentialStore = credentialStore,
                accountRepository = account,
                audioPlayer = audioPlayer,
                appSettings = settingsState,
                nowMs = { clock.nowMs },
            ),
            audioPlayer = audioPlayer,
            requests = requests,
            settings = settingsState,
            account = account,
            credentialStore = credentialStore,
            clock = clock,
        )
    }

    private fun TestScope.startReporter(fixture: ReporterFixture) {
        fixture.reporter.start(backgroundScope)
        runCurrent()
    }

    private fun connectedSettings(
        submitNowPlaying: Boolean = true,
        submitListens: Boolean = true,
        submitCurrentTrackFeedback: Boolean = true,
    ): ListenBrainzSettings =
        ListenBrainzSettings(
            enabled = true,
            username = "ada",
            submitNowPlaying = submitNowPlaying,
            submitListens = submitListens,
            submitCurrentTrackFeedback = submitCurrentTrackFeedback,
        )

    private fun io.ktor.client.engine.mock.MockRequestHandleScope.respondJson(
        content: String,
        status: HttpStatusCode = HttpStatusCode.OK,
    ) = respond(
        content = content,
        status = status,
        headers = headersOf(HttpHeaders.ContentType, "application/json"),
    )

    private fun HttpRequestData.bodyText(): String =
        when (val content = body) {
            is OutgoingContent.ByteArrayContent -> content.bytes().decodeToString()
            is OutgoingContent.NoContent -> ""
            else -> content.toString()
        }

    private fun track(
        id: String = "local:track-1",
        title: String = "Song",
        streamUrl: String = "file:///music/song.flac",
    ): Track =
        Track(
            id = id,
            title = title,
            artist = "Artist",
            album = "Album",
            durationMs = 180_000L,
            streamUrl = streamUrl,
            downloadUrl = streamUrl,
        )

    private suspend fun StateFlow<List<ListenBrainzRequest>>.awaitSize(size: Int) {
        try {
            withTimeout(2_000L) {
                while (value.size < size) {
                    yield()
                    delay(1)
                }
            }
        } catch (error: Throwable) {
            throw AssertionError("Expected at least $size ListenBrainz requests, got ${value.size}: ${value}", error)
        }
    }

    private suspend fun StateFlow<List<ListenBrainzRequest>>.awaitSingleCount(size: Int) {
        try {
            withTimeout(2_000L) {
                while (value.singleBodies().size < size) {
                    yield()
                    delay(1)
                }
            }
        } catch (error: Throwable) {
            throw AssertionError("Expected at least $size ListenBrainz single requests, got ${value.singleBodies().size}: ${value}", error)
        }
    }

    private suspend fun StateFlow<AppSettings>.awaitSettings(predicate: (AppSettings) -> Boolean): AppSettings =
        withTimeout(2_000L) {
            while (!predicate(value)) {
                yield()
                delay(1)
            }
            value
        }

    private suspend fun awaitCondition(predicate: () -> Boolean) {
        withTimeout(2_000L) {
            while (!predicate()) {
                yield()
                delay(1)
            }
        }
    }

    private suspend fun ListenBrainzPlaybackReporter.awaitQueuedRetryCount(size: Int) {
        try {
            withTimeout(2_000L) {
                while (queuedRetryCount() != size) {
                    yield()
                    delay(1)
                }
            }
        } catch (error: Throwable) {
            throw AssertionError("Expected queued retry count $size, got ${queuedRetryCount()}", error)
        }
    }

    private fun List<ListenBrainzRequest>.playingNowBodies(): List<String> =
        filter { it.body.contains(""""listen_type":"playing_now"""") }.map { it.body }

    private fun List<ListenBrainzRequest>.singleBodies(): List<String> =
        filter { it.body.contains(""""listen_type":"single"""") }.map { it.body }

    private data class ReporterFixture(
        val reporter: ListenBrainzPlaybackReporter,
        val audioPlayer: FakeAudioPlayer,
        val requests: MutableStateFlow<List<ListenBrainzRequest>>,
        val settings: MutableStateFlow<AppSettings>,
        val account: FakeListenBrainzAccountActions,
        val credentialStore: FakeSecureCredentialStore,
        val clock: TestClock,
    )

    private class TestClock(
        var nowMs: Long = 1_700_000_000_000L,
    ) {
        fun advance(deltaMs: Long) {
            nowMs += deltaMs
        }
    }

    private data class ListenBrainzRequest(
        val path: String,
        val returnMsid: String?,
        val body: String,
    )

    private class FakeAudioPlayer : AudioPlayer {
        val mutableState = MutableStateFlow(PlayerState())
        override val state: StateFlow<PlayerState> = mutableState

        fun playState(
            track: Track,
            queue: List<Track> = listOf(track),
            currentIndex: Int = queue.indexOfFirst { it.id == track.id }.takeIf { it >= 0 } ?: 0,
            isPlaying: Boolean,
            positionMs: Long = 0L,
        ) {
            mutableState.value = PlayerState(
                queue = queue,
                currentIndex = currentIndex,
                isPlaying = isPlaying,
                isBuffering = false,
                positionMs = positionMs,
                bufferedPositionMs = positionMs,
                durationMs = track.durationMs,
            )
        }

        fun stopState() {
            mutableState.value = mutableState.value.copy(
                currentIndex = -1,
                isPlaying = false,
                isBuffering = false,
            )
        }

        override fun play(queue: List<Track>, startIndex: Int) = Unit
        override fun togglePlayPause() = Unit
        override fun clearQueue() = Unit
        override fun stopPlayback() = Unit
        override fun addToUpNext(track: Track) = Unit
        override fun appendToQueue(tracks: List<Track>) = Unit
        override fun moveUpNext(fromIndex: Int, toIndex: Int) = Unit
        override fun removeUpNext(index: Int) = Unit
        override fun next() = Unit
        override fun previous() = Unit
        override fun seekTo(positionMs: Long) = Unit
        override fun setShuffle(enabled: Boolean) = Unit
        override fun setRepeat(mode: RepeatMode) = Unit
        override fun setVolume(volume: Float) = Unit
        override fun setCrossfadeDurationMs(durationMs: Long) = Unit
        override fun setEqualizer(profile: EqualizerProfile) = Unit
        override fun setUnityOutputVolume() = Unit
        override fun updateReportedVolume(volume: Float) = Unit
    }
}

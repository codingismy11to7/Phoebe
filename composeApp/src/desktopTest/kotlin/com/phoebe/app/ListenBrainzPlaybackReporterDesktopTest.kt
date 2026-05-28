package com.phoebe.app

import app.cash.sqldelight.db.SqlDriver
import com.phoebe.app.data.AppSettingsRepository
import com.phoebe.app.data.ListenBrainzAccountRepository
import com.phoebe.app.data.ListenBrainzClient
import com.phoebe.app.data.ListenBrainzFeedbackScore
import com.phoebe.app.data.ListenBrainzFeedbackTarget
import com.phoebe.app.data.ListenBrainzPlaybackReporter
import com.phoebe.app.domain.AppSettings
import com.phoebe.app.domain.EqualizerProfile
import com.phoebe.app.domain.ListenBrainzSettings
import com.phoebe.app.domain.PlayerState
import com.phoebe.app.domain.RepeatMode
import com.phoebe.app.domain.Track
import com.phoebe.app.player.AudioPlayer
import com.phoebe.app.platform.SecureCredentialKey
import com.phoebe.app.testing.FakeSecureCredentialStore
import com.phoebe.app.testing.newInMemoryPhoebeDatabase
import com.phoebe.app.testing.testHttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ListenBrainzPlaybackReporterDesktopTest {
    private var driver: SqlDriver? = null

    @After
    fun cleanup() {
        driver?.close()
        driver = null
    }

    @Test
    fun submitsPlayingNowOnceAndPermanentListenAtThreshold() = runTest {
        val requests = MutableStateFlow<List<ListenBrainzRequest>>(emptyList())
        val audioPlayer = FakeAudioPlayer()
        var nowMs = 1_700_000_000_000L
        val (settingsRepository, reporter) = newReporterWithSettings(
            requests = requests,
            audioPlayer = audioPlayer,
            settings = ListenBrainzSettings(enabled = true, username = "ada", submitCurrentTrackFeedback = false),
            nowMs = { nowMs },
        )
        val track = track()

        reporter.start(backgroundScope)
        audioPlayer.mutableState.value = PlayerState(
            queue = listOf(track),
            currentIndex = 0,
            isPlaying = true,
            positionMs = 0L,
            durationMs = track.durationMs,
        )
        runCurrent()
        requests.awaitSize(1)

        nowMs += 91_000L
        audioPlayer.mutableState.value = audioPlayer.mutableState.value.copy(positionMs = 91_000L)
        runCurrent()
        requests.awaitSize(2)

        val nowPlaying = requests.value.first()
        val listen = requests.value.last()
        assertEquals("true", nowPlaying.returnMsid)
        assertTrue(nowPlaying.body.contains(""""listen_type":"playing_now""""))
        assertFalse(nowPlaying.body.contains("listened_at"))
        assertTrue(listen.body.contains(""""listen_type":"single""""))
        assertTrue(listen.body.contains(""""listened_at""""))
        settingsRepository.settings.awaitSettings {
            it.listenBrainz.lastNowPlayingSubmittedAtMs != null &&
                it.listenBrainz.lastListenSubmittedAtMs != null
        }
    }

    @Test
    fun pauseResumeDoesNotDuplicateListenSubmission() = runTest {
        val requests = MutableStateFlow<List<ListenBrainzRequest>>(emptyList())
        val audioPlayer = FakeAudioPlayer()
        var nowMs = 1_700_000_000_000L
        val reporter = newReporter(
            requests = requests,
            audioPlayer = audioPlayer,
            settings = ListenBrainzSettings(enabled = true, username = "ada", submitCurrentTrackFeedback = false),
            nowMs = { nowMs },
        )
        val track = track()

        reporter.start(backgroundScope)
        audioPlayer.mutableState.value = PlayerState(listOf(track), 0, isPlaying = true, positionMs = 20_000L, durationMs = track.durationMs)
        runCurrent()
        requests.awaitSize(1)
        nowMs += 30_000L
        audioPlayer.mutableState.value = audioPlayer.mutableState.value.copy(isPlaying = false, positionMs = 50_000L)
        runCurrent()
        audioPlayer.mutableState.value = audioPlayer.mutableState.value.copy(isPlaying = true, positionMs = 50_000L)
        runCurrent()
        nowMs += 61_000L
        audioPlayer.mutableState.value = audioPlayer.mutableState.value.copy(positionMs = 111_000L)
        runCurrent()
        requests.awaitSize(2)
        audioPlayer.mutableState.value = audioPlayer.mutableState.value.copy(positionMs = 120_000L)
        runCurrent()

        assertEquals(1, requests.value.count { it.body.contains(""""listen_type":"single"""") })
    }

    @Test
    fun submitsPermanentListenWhenTrackChangesAfterEnoughAudibleTimeWithoutPositionUpdate() = runTest {
        val requests = MutableStateFlow<List<ListenBrainzRequest>>(emptyList())
        val audioPlayer = FakeAudioPlayer()
        var nowMs = 1_700_000_000_000L
        val reporter = newReporter(
            requests = requests,
            audioPlayer = audioPlayer,
            settings = ListenBrainzSettings(enabled = true, username = "ada", submitNowPlaying = false),
            nowMs = { nowMs },
        )
        val firstTrack = track(id = "local:track-1", title = "First Song")
        val secondTrack = track(id = "local:track-2", title = "Second Song")

        reporter.start(backgroundScope)
        audioPlayer.mutableState.value = PlayerState(
            queue = listOf(firstTrack, secondTrack),
            currentIndex = 0,
            isPlaying = true,
            positionMs = 0L,
            durationMs = firstTrack.durationMs,
        )
        runCurrent()
        assertEquals(0, requests.value.size)

        nowMs += 91_000L
        audioPlayer.mutableState.value = PlayerState(
            queue = listOf(firstTrack, secondTrack),
            currentIndex = 1,
            isPlaying = true,
            positionMs = 0L,
            durationMs = secondTrack.durationMs,
        )
        runCurrent()
        requests.awaitSize(1)

        val listen = requests.value.single()
        assertTrue(listen.body.contains(""""listen_type":"single""""))
        assertTrue(listen.body.contains("First Song"))
        assertFalse(listen.body.contains("Second Song"))
    }

    @Test
    fun failedFutureListenIsQueuedAndRetried() = runTest {
        val requests = MutableStateFlow<List<ListenBrainzRequest>>(emptyList())
        val failSingle = MutableStateFlow(true)
        val audioPlayer = FakeAudioPlayer()
        var nowMs = 1_700_000_000_000L
        val (settingsRepository, reporter) = newReporterWithSettings(
            requests = requests,
            audioPlayer = audioPlayer,
            settings = ListenBrainzSettings(enabled = true, username = "ada", submitNowPlaying = false),
            failSingle = failSingle,
            nowMs = { nowMs },
        )

        reporter.start(backgroundScope)
        audioPlayer.mutableState.value = PlayerState(listOf(track()), 0, isPlaying = true, positionMs = 0L, durationMs = 180_000L)
        runCurrent()
        nowMs += 91_000L
        audioPlayer.mutableState.value = audioPlayer.mutableState.value.copy(positionMs = 91_000L)
        runCurrent()
        requests.awaitSize(1)

        reporter.awaitQueuedRetryCount(1)
        settingsRepository.settings.awaitSettings { it.listenBrainz.lastListenError != null }
        failSingle.value = false
        advanceTimeBy(ListenBrainzPlaybackReporter.RetryIntervalMs)
        runCurrent()

        reporter.awaitQueuedRetryCount(0)
        assertEquals(2, requests.value.size)
    }

    @Test
    fun rejectedListenUpdatesSettingsErrorInsteadOfRetryingSilently() = runTest {
        val requests = MutableStateFlow<List<ListenBrainzRequest>>(emptyList())
        val audioPlayer = FakeAudioPlayer()
        var nowMs = 1_700_000_000_000L
        val (settingsRepository, reporter) = newReporterWithSettings(
            requests = requests,
            audioPlayer = audioPlayer,
            settings = ListenBrainzSettings(enabled = true, username = "ada", submitNowPlaying = false),
            singleStatus = HttpStatusCode.BadRequest,
            singleBody = """{"error":"bad listen"}""",
            nowMs = { nowMs },
        )

        reporter.start(backgroundScope)
        audioPlayer.mutableState.value = PlayerState(listOf(track()), 0, isPlaying = true, positionMs = 0L, durationMs = 180_000L)
        runCurrent()
        nowMs += 91_000L
        audioPlayer.mutableState.value = audioPlayer.mutableState.value.copy(positionMs = 91_000L)
        runCurrent()
        requests.awaitSize(1)
        val settings = settingsRepository.settings.awaitSettings {
            it.listenBrainz.lastListenError?.contains("bad listen") == true
        }

        assertEquals(0, reporter.queuedRetryCount())
        assertTrue(settings.listenBrainz.lastError.orEmpty().contains("400"))
        assertTrue(settings.listenBrainz.lastListenSubmittedAtMs == null)
    }

    @Test
    fun feedbackShowsSubmittingStateAndClearRemovesScore() = runTest {
        val requests = MutableStateFlow<List<ListenBrainzRequest>>(emptyList())
        val audioPlayer = FakeAudioPlayer()
        val feedbackGate = CompletableDeferred<Unit>()
        val reporter = newReporter(
            requests = requests,
            audioPlayer = audioPlayer,
            feedbackGate = feedbackGate,
        )

        reporter.start(backgroundScope)
        audioPlayer.mutableState.value = PlayerState(listOf(track()), 0, isPlaying = true, positionMs = 0L, durationMs = 180_000L)
        runCurrent()
        requests.awaitSize(1)
        reporter.awaitFeedbackTarget { it.available }

        val loveJob = backgroundScope.launch {
            reporter.submitCurrentTrackFeedback(ListenBrainzFeedbackScore.Love)
        }
        reporter.awaitFeedbackTarget { it.submittingScore == ListenBrainzFeedbackScore.Love }
        feedbackGate.complete(Unit)
        loveJob.join()
        reporter.awaitFeedbackTarget {
            it.score == ListenBrainzFeedbackScore.Love && it.submittingScore == null
        }

        reporter.submitCurrentTrackFeedback(ListenBrainzFeedbackScore.Clear)
        reporter.awaitFeedbackTarget {
            it.score == null && it.submittingScore == null
        }

        val feedbackBodies = requests.value.filter { it.path.endsWith("/feedback/recording-feedback") }.map { it.body }
        assertTrue(feedbackBodies.any { it.contains(""""score":1""") })
        assertTrue(feedbackBodies.any { it.contains(""""score":0""") })
    }

    @Test
    fun loadsExistingFeedbackScoreWhenPlayingNowReturnsMsid() = runTest {
        val requests = MutableStateFlow<List<ListenBrainzRequest>>(emptyList())
        val feedbackScoresByMsid = MutableStateFlow(mapOf("msid-liked" to ListenBrainzFeedbackScore.Love.apiScore))
        val audioPlayer = FakeAudioPlayer()
        val reporter = newReporter(
            requests = requests,
            audioPlayer = audioPlayer,
            feedbackScoresByMsid = feedbackScoresByMsid,
            playingNowMsidForBody = { "msid-liked" },
        )

        reporter.start(backgroundScope)
        audioPlayer.mutableState.value = PlayerState(listOf(track()), 0, isPlaying = true, positionMs = 0L, durationMs = 180_000L)
        runCurrent()
        val target = reporter.awaitFeedbackTarget {
            it.recordingMsid == "msid-liked" &&
                it.score == ListenBrainzFeedbackScore.Love &&
                !it.loadingScore
        }

        assertTrue(target.available)
        assertTrue(requests.value.any { it.path.endsWith("/get-feedback-for-recordings") })
    }

    @Test
    fun unauthorizedSubmitDeletesCredentialAndDisconnects() = runTest {
        val requests = MutableStateFlow<List<ListenBrainzRequest>>(emptyList())
        val audioPlayer = FakeAudioPlayer()
        val credentialStore = FakeSecureCredentialStore()
        var nowMs = 1_700_000_000_000L
        credentialStore.write(SecureCredentialKey.ListenBrainzUserToken, "token")
        val (settingsRepository, reporter) = newReporterWithSettings(
            requests = requests,
            audioPlayer = audioPlayer,
            credentialStore = credentialStore,
            settings = ListenBrainzSettings(enabled = true, username = "ada", submitNowPlaying = false),
            singleStatus = HttpStatusCode.Unauthorized,
            nowMs = { nowMs },
        )

        reporter.start(backgroundScope)
        audioPlayer.mutableState.value = PlayerState(listOf(track()), 0, isPlaying = true, positionMs = 0L, durationMs = 180_000L)
        runCurrent()
        nowMs += 91_000L
        audioPlayer.mutableState.value = audioPlayer.mutableState.value.copy(positionMs = 91_000L)
        runCurrent()
        requests.awaitSize(1)
        settingsRepository.settings.awaitSettings { !it.listenBrainz.connected }

        assertFalse(settingsRepository.settings.value.listenBrainz.connected)
        assertFalse(credentialStore.values.containsKey(SecureCredentialKey.ListenBrainzUserToken))
    }

    private suspend fun newReporter(
        requests: MutableStateFlow<List<ListenBrainzRequest>>,
        audioPlayer: AudioPlayer,
        settings: ListenBrainzSettings = ListenBrainzSettings(enabled = true, username = "ada"),
        failSingle: StateFlow<Boolean> = MutableStateFlow(false),
        feedbackGate: CompletableDeferred<Unit>? = null,
        feedbackScoresByMsid: StateFlow<Map<String, Int>> = MutableStateFlow(emptyMap()),
        playingNowMsidForBody: (String) -> String = { "msid-1" },
        nowMs: () -> Long = { 1_700_000_000_000L },
    ): ListenBrainzPlaybackReporter =
        newReporterWithSettings(
            requests,
            audioPlayer,
            settings = settings,
            failSingle = failSingle,
            feedbackGate = feedbackGate,
            feedbackScoresByMsid = feedbackScoresByMsid,
            playingNowMsidForBody = playingNowMsidForBody,
            nowMs = nowMs,
        ).second

    private suspend fun newReporterWithSettings(
        requests: MutableStateFlow<List<ListenBrainzRequest>>,
        audioPlayer: AudioPlayer,
        credentialStore: FakeSecureCredentialStore? = null,
        settings: ListenBrainzSettings,
        failSingle: StateFlow<Boolean> = MutableStateFlow(false),
        singleStatus: HttpStatusCode = HttpStatusCode.OK,
        singleBody: String = """{"status":"ok"}""",
        feedbackGate: CompletableDeferred<Unit>? = null,
        feedbackScoresByMsid: StateFlow<Map<String, Int>> = MutableStateFlow(emptyMap()),
        playingNowMsidForBody: (String) -> String = { "msid-1" },
        nowMs: () -> Long = { 1_700_000_000_000L },
    ): Pair<AppSettingsRepository, ListenBrainzPlaybackReporter> {
        val resolvedCredentialStore = credentialStore ?: FakeSecureCredentialStore().also {
            it.write(SecureCredentialKey.ListenBrainzUserToken, "token")
        }
        val (db, d) = newInMemoryPhoebeDatabase()
        driver = d
        val settingsRepository = AppSettingsRepository(db)
        settingsRepository.setListenBrainzSettings(settings)
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
                    request.url.encodedPath.endsWith("/get-feedback-for-recordings") -> {
                        val feedback = feedbackScoresByMsid.value.entries.joinToString(",") { (msid, score) ->
                            """{"recording_msid":"$msid","score":$score}"""
                        }
                        respondJson("""{"count":${feedbackScoresByMsid.value.size},"feedback":[$feedback],"offset":0,"total_count":${feedbackScoresByMsid.value.size}}""")
                    }
                    request.url.encodedPath.endsWith("/feedback/recording-feedback") -> {
                        feedbackGate?.await()
                        respondJson("""{"status":"ok"}""")
                    }
                    body.contains("playing_now") -> respondJson("""{"recording_msid":"${playingNowMsidForBody(body)}"}""")
                    failSingle.value -> respond("", HttpStatusCode.InternalServerError)
                    else -> respondJson(singleBody, singleStatus)
                }
            },
        )
        val client = ListenBrainzClient(httpClient, baseUrl = "https://listenbrainz.example")
        val accountRepository = ListenBrainzAccountRepository(client, settingsRepository, resolvedCredentialStore)
        return settingsRepository to ListenBrainzPlaybackReporter(
            client = client,
            credentialStore = resolvedCredentialStore,
            accountRepository = accountRepository,
            audioPlayer = audioPlayer,
            appSettings = settingsRepository.settings,
            nowMs = nowMs,
        )
    }

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
        durationMs: Long = 180_000L,
    ): Track =
        Track(
            id = id,
            title = title,
            artist = "Artist",
            album = "Album",
            durationMs = durationMs,
            streamUrl = "file:///music/song.flac",
            downloadUrl = "file:///music/song.flac",
        )

    private suspend fun <T> StateFlow<List<T>>.awaitSize(size: Int) {
        withContext(Dispatchers.Default) {
            withTimeout(2_000L) {
                first { it.size >= size }
            }
        }
    }

    private suspend fun StateFlow<AppSettings>.awaitSettings(predicate: (AppSettings) -> Boolean): AppSettings =
        withContext(Dispatchers.Default) {
            withTimeout(2_000L) {
                first(predicate)
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

    private suspend fun ListenBrainzPlaybackReporter.awaitFeedbackTarget(
        predicate: (ListenBrainzFeedbackTarget) -> Boolean,
    ): ListenBrainzFeedbackTarget =
        withContext(Dispatchers.Default) {
            withTimeout(2_000L) {
                feedbackTarget.first(predicate)
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

        override fun play(queue: List<Track>, startIndex: Int) {
            mutableState.value = PlayerState(queue = queue, currentIndex = startIndex, isPlaying = true)
        }

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

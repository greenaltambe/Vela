@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package com.greenaltambe.vela.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.test.DesktopComposeUiTest
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import com.greenaltambe.vela.AppServices
import com.greenaltambe.vela.App
import com.greenaltambe.vela.core.http.HttpRequest
import com.greenaltambe.vela.core.http.HttpResponse
import com.greenaltambe.vela.core.http.HttpTransport
import com.greenaltambe.vela.core.network.FixedNetworkMonitor
import com.greenaltambe.vela.core.network.NetworkType
import com.greenaltambe.vela.core.settings.InMemorySettingsStore
import com.greenaltambe.vela.core.settings.LookChoice
import com.greenaltambe.vela.core.settings.Settings
import com.greenaltambe.vela.core.settings.SettingsResolver
import com.greenaltambe.vela.data.DownloadKind
import com.greenaltambe.vela.data.DownloadState
import com.greenaltambe.vela.data.JvmDatabaseDriverFactory
import com.greenaltambe.vela.data.SqlDelightWatchHistoryRepository
import com.greenaltambe.vela.data.createDatabase
import com.greenaltambe.vela.downloads.DownloadManager
import com.greenaltambe.vela.downloads.DownloadRepository
import com.greenaltambe.vela.downloads.DownloadRequest
import com.greenaltambe.vela.downloads.DownloadSink
import com.greenaltambe.vela.downloads.Downloads
import com.greenaltambe.vela.downloads.MediaMerger
import com.greenaltambe.vela.downloads.MergeJob
import com.greenaltambe.vela.downloads.OfflineCollector
import com.greenaltambe.vela.downloads.RangeFetcher
import com.greenaltambe.vela.downloads.RangeResponse
import com.greenaltambe.vela.downloads.StoredPlan
import com.greenaltambe.vela.extractor.*
import com.greenaltambe.vela.extractor.chain.DiagnosticsLog
import com.greenaltambe.vela.modules.createModules
import com.greenaltambe.vela.net.FileDownloadStorage
import com.greenaltambe.vela.player.PlaybackMetadata
import com.greenaltambe.vela.player.PlaybackPlan
import com.greenaltambe.vela.player.PlaybackSession
import com.greenaltambe.vela.player.PlaybackStats
import com.greenaltambe.vela.player.PlaybackStatus
import com.greenaltambe.vela.player.PlayerController
import com.greenaltambe.vela.player.PlayerEvent
import com.greenaltambe.vela.player.PlayerHost
import com.greenaltambe.vela.player.PlayerState
import com.greenaltambe.vela.player.StreamLeases
import com.greenaltambe.vela.ui.nav.Navigator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import java.io.File
import java.nio.file.Files
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Clock

/** Realistic, awkward data: long titles, long channel names, huge numbers, live / short / downloaded / watched states. */
object FakeData {
    private val now = Clock.System.now()
    val longTitle = "I Spent 72 Hours Building the Most Ridiculously Over-Engineered Home Server Rack in My Tiny Apartment (Full Build Guide, Costs and Mistakes)"
    val longChannel = "The Extremely Long Channel Name of a Creator Who Never Uses Short Words"

    val videos: List<VideoSummary> = listOf(
        VideoSummary("v01", longTitle, "UC1", longChannel, "https://invalid.invalid/1.jpg", 1_845, 1_234_567_890, uploadedAt = now - 3.days),
        VideoSummary("v02", "Short title", "UC2", "Mia", "https://invalid.invalid/2.jpg", 61, 12, uploadedAt = now - 2.hours),
        VideoSummary("v03", "LIVE: Space launch coverage with commentary from mission control", "UC3", "NASA", "https://invalid.invalid/3.jpg", null, 48_300, isLive = true, uploadedAt = now - 1.hours),
        VideoSummary("v04", "A 30 second trick you didn't know", "UC4", "Tips", "https://invalid.invalid/4.jpg", 29, 9_800_000, isShort = true, uploadedAt = now - 40.days),
        VideoSummary("v05", "Unbroken_word_title_without_any_spaces_at_all_that_could_overflow_a_narrow_column_easily", "UC5", "Compact", "https://invalid.invalid/5.jpg", 3_700, 999, uploadedAt = now - 400.days),
        VideoSummary("v06", "Understanding coroutines: structured concurrency from first principles", "UC6", "Kotlin Talks", "https://invalid.invalid/6.jpg", 2_460, 154_321, uploadedAt = now - 9.days),
        VideoSummary("v07", "한국어 제목도 올바르게 표시되어야 합니다 — 日本語のタイトルも", "UC7", "多言語チャンネル", "https://invalid.invalid/7.jpg", 733, 21_000, uploadedAt = now - 5.days),
        VideoSummary("v08", "Downloaded and half watched documentary", "UC8", "Docs", "https://invalid.invalid/8.jpg", 5_400, 3_200_000, uploadedAt = now - 20.days),
        VideoSummary("v09", "Ten hour ambient rain loop", "UC9", "Sleep", "https://invalid.invalid/9.jpg", 36_000, 87_000_000, uploadedAt = now - 800.days),
        VideoSummary("v10", "Review", "UC10", "Q", "https://invalid.invalid/10.jpg", 612, 1_000, uploadedAt = now - 60.days),
        VideoSummary("v11", "Another reasonably normal title for a video", "UC11", "Normal Channel", "https://invalid.invalid/11.jpg", 905, 54_000, uploadedAt = now - 12.days),
        VideoSummary("v12", "The last card in the list, with a medium length title here", "UC12", "Last One", "https://invalid.invalid/12.jpg", 1_212, 777_777, uploadedAt = now - 4.days),
    )

    val comments = listOf(
        Comment("c1", "@a-user-with-a-fairly-long-handle-name", "This is a long comment that goes on for several lines to make sure that text wrapping works properly at narrow widths and that nothing is clipped on the right-hand side of the screen at all. ".repeat(2), 12_345, "2 weeks ago", 3, isPinned = true, repliesContinuation = "r1"),
        Comment("c2", "short", "Nice.", 3, "1 day ago"),
        Comment("c3", "Reader", "Unbroken_comment_text_without_spaces_that_is_very_long_indeed_and_must_wrap_or_ellipsize_somewhere_sensible_xxxxxxxxxxxxxxxxxxxxxxxxx", 0, "3 hours ago"),
    )

    val description = "In this video we build the thing.\n0:00 Intro\n1:30 Parts list\n5:12 Assembly\n12:40 Software\n20:05 Conclusion\n\nLinks: https://example.com/a/very/long/url/that/goes/on/and/on/and/on/forever/and/ever/index.html\n" + "More description text. ".repeat(20)
}

class FakeExtractor : Extractor by StubExtractor() {
    override suspend fun trending(continuation: String?) = ExtractorResult.Success(Page(FakeData.videos, "next"))
    override suspend fun search(query: String, continuation: String?) = ExtractorResult.Success(Page(FakeData.videos.shuffled(java.util.Random(query.hashCode().toLong())), "next"))
    override suspend fun related(videoId: String) = ExtractorResult.Success(FakeData.videos.take(6))
    override suspend fun comments(videoId: String, sort: CommentSort, continuation: String?) = ExtractorResult.Success(CommentsPage(FakeData.comments, "more"))
    override suspend fun videoInfo(videoId: String) = ExtractorResult.Success(
        VideoInfo(videoId, FakeData.longTitle, FakeData.description, "UC1", FakeData.longChannel, 1_845, 1_234_567_890, 45_300_000, chapters = listOf(Chapter("Intro", 0), Chapter("Parts list", 90), Chapter("Assembly", 312), Chapter("Software", 760), Chapter("Conclusion", 1205))),
    )
    override suspend fun channel(channelId: String) = ExtractorResult.Success(ChannelPage(channelId, FakeData.longChannel, "Official channel of an extremely prolific creator. ".repeat(6), null, 12_400_000, FakeData.videos, "next"))
    override suspend fun channelVideos(channelId: String, continuation: String?) = ExtractorResult.Success(Page(FakeData.videos.take(4), null))
    override suspend fun recentUploads(channelId: String) = ExtractorResult.Success(FakeData.videos.filter { it.channelId == channelId || true }.take(8))
    override suspend fun streams(videoId: String) = ExtractorResult.Success(
        StreamSet(
            listOf(VideoStream("https://v/1080", "video/webm", "vp9", 4_000_000, 1920, 1080, 30, itag = 248), VideoStream("https://v/720", "video/webm", "vp9", 2_000_000, 1280, 720, 30, itag = 247)),
            listOf(AudioStream("https://a/1", "audio/webm", "opus", 128_000, itag = 251, isOriginalTrack = true)),
            client = "fake",
        ),
    )
}

class FakeController(var startMs: Long = 754_000, var durationMs: Long = 1_845_000, var status: PlaybackStatus = PlaybackStatus.PLAYING) : PlayerController {
    override val state = MutableStateFlow(PlayerState())
    override val events = MutableSharedFlow<PlayerEvent>(extraBufferCapacity = 4)
    override fun load(plan: PlaybackPlan, startPositionMs: Long, playWhenReady: Boolean, metadata: PlaybackMetadata) {
        state.value = PlayerState(status, if (startPositionMs > 0) startPositionMs else startMs, durationMs, 1f)
    }
    override fun play() { state.value = state.value.copy(status = PlaybackStatus.PLAYING) }
    override fun pause() { state.value = state.value.copy(status = PlaybackStatus.PAUSED) }
    override fun seekTo(positionMs: Long) { state.value = state.value.copy(positionMs = positionMs) }
    override fun setSpeed(speed: Float) { state.value = state.value.copy(speed = speed) }
    override fun stop() { state.value = PlayerState() }
    override fun release() {}
}

class FakeHost(override val session: PlaybackSession, val controller: FakeController) : PlayerHost {
    override val controllerState: StateFlow<PlayerState> get() = controller.state
    override val stats: StateFlow<PlaybackStats> = MutableStateFlow(PlaybackStats("1920x1080@30", "vp9", 4_000_000, "opus", 128_000, 40_000_000, 22_000))

    @Composable
    override fun Surface(modifier: Modifier) {
        Canvas(modifier.fillMaxSize()) { drawRect(Brush.linearGradient(listOf(Color(0xFF3A506B), Color(0xFF1C2541)), Offset.Zero, Offset(size.width, size.height))) }
    }
}

class Fixture(val services: AppServices, val navigator: Navigator, val settings: SettingsResolver, val controller: FakeController, val dir: File)

fun buildFixture(pureBlack: Boolean = false, withDownloads: Boolean = true): Fixture {
    val settings = SettingsResolver(InMemorySettingsStore())
    if (pureBlack) settings.set(Settings.look, LookChoice.PURE_BLACK)
    val database = createDatabase(JvmDatabaseDriverFactory())
    val extractor = FakeExtractor()
    val log = DiagnosticsLog()
    val modules = createModules(object : HttpTransport { override suspend fun execute(request: HttpRequest) = HttpResponse(404, "") }, database, settings)
    val controller = FakeController()
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val session = PlaybackSession(extractor, controller, settings, SqlDelightWatchHistoryRepository(database), scope, addons = listOf(modules.sponsorBlock))
    val host = FakeHost(session, controller)
    val dir = Files.createTempDirectory("vela-ui").toFile()

    // Some history, a subscription and a few playlists, so those screens have content.
    val history = SqlDelightWatchHistoryRepository(database)
    FakeData.videos.take(5).forEachIndexed { i, v -> val id = history.begin("local", v.id, v.channelId, v.title, Clock.System.now() - (i * 7).hours, 300_000L * (i + 1), (v.durationSeconds ?: 600) * 1000, 100_000) ; history.update(id, Clock.System.now() - (i * 7).hours, 300_000L * (i + 1), (v.durationSeconds ?: 600) * 1000, 100_000) }
    val subs = com.greenaltambe.vela.data.SubscriptionRepository(database)
    FakeData.videos.take(5).forEach { subs.subscribe(it.channelId, it.channelName, null, Clock.System.now()) }
    val playlists = com.greenaltambe.vela.data.PlaylistRepository(database)
    val mine = playlists.create("Weekend watching with a rather long playlist name", Clock.System.now())
    playlists.add(mine, FakeData.videos.take(6), Clock.System.now())
    playlists.add(playlists.watchLaterId(), FakeData.videos.take(3), Clock.System.now())

    var downloads: Downloads? = null
    if (withDownloads) {
        val repo = DownloadRepository(database)
        val storage = FileDownloadStorage(File(dir, "Videos"))
        fun row(v: VideoSummary, state: DownloadState, audio: Boolean = false, done: Long = 0, total: Long? = null, error: String? = null, path: String? = null): Long {
            val id = repo.insert(DownloadRequest(v, audioOnly = audio), StoredPlan(), if (audio) "Audio" else "1080p")
            repo.setProgress(id, done, total)
            if (path != null) repo.setPaths(id, null, null, path)
            repo.setState(id, state, error)
            return id
        }
        row(FakeData.videos[7], DownloadState.COMPLETED, done = 482_000_000, total = 482_000_000, path = File(dir, "a.mp4").path)
        row(FakeData.videos[4], DownloadState.PAUSED, done = 120_000_000, total = 300_000_000)
        row(FakeData.videos[5], DownloadState.FAILED, error = "The stream URL keeps being rejected (HTTP 403); giving up after 3 fresh attempts")
        row(FakeData.videos[9], DownloadState.COMPLETED, audio = true, done = 12_000_000, total = 12_000_000, path = File(dir, "b.m4a").path)
        row(FakeData.videos[8], DownloadState.QUEUED)
        val fetcher = object : RangeFetcher { override suspend fun fetch(url: String, headers: Map<String, String>, start: Long, endInclusive: Long?, sink: DownloadSink) = RangeResponse(404, 0, null) }
        val merger = object : MediaMerger { override val available = true; override val description = "fake"; override suspend fun merge(job: MergeJob) {} }
        val manager = DownloadManager(repo, StreamLeases(extractor), { storage }, fetcher, merger, OfflineCollector(extractor, modules, fetcher, { storage }, settings), settings, FixedNetworkMonitor(NetworkType.OFFLINE), scope)
        manager.start()
        downloads = Downloads(manager, repo, { storage }, merger)
    }
    val services = AppServices(extractor, settings, log, database, host, modules = modules, downloads = downloads)
    return Fixture(services, Navigator(), settings, controller, dir)
}

/** The sizes the task names (dp = px at density 1). */
data class Screen(val name: String, val width: Int, val height: Int)

val Screens = listOf(
    Screen("320x640", 320, 640), Screen("360x800", 360, 800), Screen("412x915", 412, 915),
    Screen("800x1280", 800, 1280), Screen("1280x800", 1280, 800), Screen("800x360", 800, 360),
)

/** Writes the current window as a PNG under the screens directory (only when the build set it), and returns the file. */
fun DesktopComposeUiTest.screenshot(name: String): File? {
    val dir = System.getProperty("vela.screens.dir") ?: return null
    val image = onRoot().captureToImage()
    val data = Image.makeFromBitmap(image.asSkiaBitmap()).encodeToData(EncodedImageFormat.PNG) ?: return null
    return File(dir, "$name.png").also { it.parentFile.mkdirs(); it.writeBytes(data.bytes) }
}

@Composable
fun AppUnderTest(f: Fixture) = App(f.services, f.navigator)

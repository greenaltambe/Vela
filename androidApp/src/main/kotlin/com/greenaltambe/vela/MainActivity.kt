package com.greenaltambe.vela

import android.Manifest
import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.greenaltambe.vela.ui.platform.LocalWindowControls
import com.greenaltambe.vela.ui.platform.WindowControls
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.greenaltambe.vela.player.PlaybackStatus
import com.greenaltambe.vela.player.SessionState
import com.greenaltambe.vela.player.videoHeight
import com.greenaltambe.vela.playback.AndroidPlayerHost
import com.greenaltambe.vela.playback.PlaybackRuntime
import com.greenaltambe.vela.playback.VelaPlaybackService
import com.greenaltambe.vela.ui.platform.LocalInPictureInPicture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.util.TimeZone

class MainActivity : ComponentActivity() {
    // The system document picker for backups. The launchers must exist before the activity is started, hence fields.
    private var pendingSave: kotlinx.coroutines.CompletableDeferred<android.net.Uri?>? = null
    private var pendingOpen: kotlinx.coroutines.CompletableDeferred<android.net.Uri?>? = null
    private val createDoc = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { pendingSave?.complete(it) }
    private val openDoc = registerForActivityResult(ActivityResultContracts.OpenDocument()) { pendingOpen?.complete(it) }
    private val backupFiles = object : com.greenaltambe.vela.backup.BackupFiles {
        override suspend fun save(suggestedName: String, text: String): String? {
            val d = kotlinx.coroutines.CompletableDeferred<android.net.Uri?>().also { pendingSave = it }
            createDoc.launch(suggestedName)
            val uri = d.await() ?: return null
            kotlinx.coroutines.withContext(Dispatchers.IO) { contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) } }
            return uri.lastPathSegment ?: uri.toString()
        }
        override suspend fun pick(): String? {
            val d = kotlinx.coroutines.CompletableDeferred<android.net.Uri?>().also { pendingOpen = it }
            openDoc.launch(arrayOf("application/json", "text/plain", "*/*"))
            val uri = d.await() ?: return null
            return kotlinx.coroutines.withContext(Dispatchers.IO) { contentResolver.openInputStream(uri)?.use { String(it.readBytes()) } }
        }
    }
    private val runtime by lazy { PlaybackRuntime.get(this) }
    private val host by lazy { AndroidPlayerHost(runtime, ::ensureNotificationPermission) }
    private val healthSchedule by lazy { com.greenaltambe.vela.playback.HealthWorker.schedule(applicationContext); com.greenaltambe.vela.playback.AutoDownloadWorker.schedule(applicationContext) }
    private val remoteConfigJob by lazy {
        // Fetched once per start in the background; a failure only changes the Diagnostics line and keeps the last good config.
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            runtime.remote.refresh()
            runtime.log.record(com.greenaltambe.vela.extractor.chain.TierAttempt("remote config", com.greenaltambe.vela.extractor.chain.TierId.ALT_CLIENT,
                com.greenaltambe.vela.extractor.chain.AttemptOutcome.NOTE, null, runtime.remote.status.value, kotlin.time.Duration.ZERO, kotlin.time.Clock.System.now()))
        }
    }
    private val services by lazy {
        remoteConfigJob; healthSchedule
        AppServices(
            com.greenaltambe.vela.extractor.CaptchaAwareExtractor(com.greenaltambe.vela.extractor.TimeoutExtractor(runtime.chain), runtime.captcha), runtime.settings, runtime.log, runtime.database, host,
            utcOffsetMinutes = { TimeZone.getDefault().getOffset(System.currentTimeMillis()) / 60_000 },
            modules = runtime.modules,
            downloads = runtime.downloads,
            accounts = runtime.accounts,
            login = runtime.login,
            sync = runtime.sync,
            actions = runtime.actions,
            reads = runtime.reads,
            profiles = runtime.profiles,
            audit = runtime.audit,
            health = runtime.health,
            governors = runtime.governors,
            faults = runtime.faults,
            backupFiles = backupFiles,
            liveChat = com.greenaltambe.vela.innertube.LiveChatClient(),
            captcha = runtime.captcha,
            solveCaptcha = { url -> startActivity(Intent(this, com.greenaltambe.vela.auth.CaptchaActivity::class.java).putExtra(com.greenaltambe.vela.auth.CaptchaActivity.EXTRA_URL, url)) },
            autoDownloader = runtime.autoDownloader,
            exportLog = { val out = java.io.File(getExternalFilesDir(null) ?: filesDir, "vela-log-${System.currentTimeMillis() / 1000}.txt"); out.writeText(runtime.logFile.readAll()); out.path },
            releases = listOf(com.greenaltambe.vela.health.ReleaseWatch(com.greenaltambe.vela.health.ReleaseCheck(
                "NewPipeExtractor", com.greenaltambe.vela.health.NewPipeVersion.VERSION, "TeamNewPipe/NewPipeExtractor",
                com.greenaltambe.vela.core.http.ApiClient(com.greenaltambe.vela.net.OkHttpTransport(), com.greenaltambe.vela.core.http.InMemoryApiCache()),
                com.greenaltambe.vela.data.SqlDelightSettingsStore(runtime.database)))),
            webLogin = { startActivity(android.content.Intent(this, com.greenaltambe.vela.auth.WebLoginActivity::class.java)) },
        )
    }

    /** Connecting a controller binds the playback service, which is what lets Media3 raise the foreground notification. */
    private var mediaController: ListenableFuture<MediaController>? = null
    private var askedForNotifications = false
    private var inPip by mutableStateOf(false)
    private var fullscreen by mutableStateOf(false)

    /** Immersive fullscreen with the same overlay controls: system bars hidden, the screen turned to landscape. */
    private val windowControls = object : WindowControls {
        override val isFullscreen: Boolean get() = fullscreen
        override fun toggleFullscreen() = applyFullscreen(!fullscreen)
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* playback works either way */ }

    private val pipToggle = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != ACTION_PIP_TOGGLE) return
            if (runtime.controller.state.value.status == PlaybackStatus.PLAYING) runtime.session.pause() else runtime.session.play()
        }
    }

    /**
     * If the last process died while playing (the OS or a battery manager killed background playback), say so once and offer the
     * system's battery-optimisation screen. Skipped when the app is already exempt; no permission is needed to open that screen.
     */
    private fun explainBatteryOptimisationOnce() {
        if (!runtime.previousProcessDiedWhilePlaying || runtime.batteryHintAlreadyShown) return
        val pm = getSystemService(android.os.PowerManager::class.java)
        if (pm?.isIgnoringBatteryOptimizations(packageName) == true) return
        runtime.markBatteryHintShown()
        android.app.AlertDialog.Builder(this)
            .setTitle("Playback was stopped by the system")
            .setMessage("Android ended Vela while it was playing. Letting Vela ignore battery optimisation usually keeps background playback alive. Some phones also have their own battery manager with an 'auto-start' or 'background activity' switch for apps.")
            .setPositiveButton("Open battery settings") { _, _ -> runCatching { startActivity(Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) } }
            .setNegativeButton("Not now", null).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        explainBatteryOptimisationOnce()
        ContextCompat.registerReceiver(this, pipToggle, IntentFilter(ACTION_PIP_TOGGLE), ContextCompat.RECEIVER_NOT_EXPORTED)

        // Keeps the PiP window's play/pause action in step, and (API 31+) lets the system enter PiP on its own when the user leaves.
        scope.launch {
            combine(runtime.controller.state, runtime.session.state) { player, session -> player.status to session }.collect { (status, session) ->
                setPictureInPictureParams(pipParams(status == PlaybackStatus.PLAYING, (session as? SessionState.Ready)?.plan?.videoHeight != null))
            }
        }

        setContent {
            CompositionLocalProvider(LocalInPictureInPicture provides inPip, LocalWindowControls provides windowControls) { App(services) }
        }
    }

    override fun onDestroy() {
        unregisterReceiver(pipToggle)
        scope.cancel()
        super.onDestroy()
    }

    override fun onStart() {
        super.onStart()
        val token = SessionToken(this, ComponentName(this, VelaPlaybackService::class.java))
        mediaController = MediaController.Builder(this, token).buildAsync()
    }

    override fun onStop() {
        mediaController?.let(MediaController::releaseFuture)
        mediaController = null
        super.onStop()
    }

    /** Before API 31 there is no auto-enter: leaving with a video playing enters PiP here. */
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S && videoIsPlaying()) {
            runCatching { enterPictureInPictureMode(pipParams(playing = true, hasVideo = true)) }
        }
    }

    private fun applyFullscreen(on: Boolean) {
        if (fullscreen == on) return
        fullscreen = on
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        if (on) {
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    /** Turning the phone sideways while a video plays enters fullscreen; turning it upright leaves it. */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE && videoIsPlaying()) applyFullscreen(true)
        else if (newConfig.orientation == Configuration.ORIENTATION_PORTRAIT && fullscreen) applyFullscreen(false)
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        inPip = isInPictureInPictureMode
    }

    private fun videoIsPlaying(): Boolean =
        runtime.controller.state.value.status == PlaybackStatus.PLAYING &&
            (runtime.session.state.value as? SessionState.Ready)?.plan?.videoHeight != null

    private fun pipParams(playing: Boolean, hasVideo: Boolean): PictureInPictureParams {
        val action = RemoteAction(
            Icon.createWithResource(this, if (playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play),
            if (playing) "Pause" else "Play", if (playing) "Pause" else "Play",
            PendingIntent.getBroadcast(this, 0, Intent(ACTION_PIP_TOGGLE).setPackage(packageName), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT),
        )
        return PictureInPictureParams.Builder()
            .setAspectRatio(Rational(16, 9))
            .setActions(listOf(action))
            .apply { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) setAutoEnterEnabled(hasVideo && playing) }
            .build()
    }

    /** Asked once, when the first video starts rather than at launch; denial only hides the notification. */
    private fun ensureNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || askedForNotifications) return
        askedForNotifications = true
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private companion object {
        const val ACTION_PIP_TOGGLE = "com.greenaltambe.vela.PIP_TOGGLE"
    }
}

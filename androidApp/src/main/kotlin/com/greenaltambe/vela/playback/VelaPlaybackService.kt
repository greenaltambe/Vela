package com.greenaltambe.vela.playback

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.greenaltambe.vela.MainActivity

/**
 * Keeps audio playing with the app in the background and shows the media notification / lock screen controls. The
 * player itself belongs to [PlaybackRuntime]; this service only wraps it in a [MediaSession], so destroying the
 * service never destroys the player.
 *
 * Media3 promotes the service to the foreground (type `mediaPlayback`, see the manifest) when playback starts and
 * demotes it when playback stops.
 */
class VelaPlaybackService : MediaSessionService() {
    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        session = MediaSession.Builder(this, PlaybackRuntime.get(this).player).setSessionActivity(open).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    /** Swiping the app away stops playback, like any video app; the session service pauses and stops itself. */
    override fun onTaskRemoved(rootIntent: Intent?) {
        pauseAllPlayersAndStopSelf()
    }

    override fun onDestroy() {
        session?.release() // not the player: it is shared and owned by PlaybackRuntime
        session = null
        super.onDestroy()
    }
}

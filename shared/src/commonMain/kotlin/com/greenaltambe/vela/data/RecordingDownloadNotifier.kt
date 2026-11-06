package com.greenaltambe.vela.data

import com.greenaltambe.vela.downloads.DownloadNotifier
import kotlin.time.Clock

/** Passes everything to [inner] (the system notification) and also records it in the notification feed, when [enabled]. */
class RecordingDownloadNotifier(
    private val inner: DownloadNotifier,
    private val repo: () -> NotificationRepository,
    private val enabled: () -> Boolean = { true },
    private val clock: Clock = Clock.System,
) : DownloadNotifier {
    private fun record(kind: NotificationKind, title: String, body: String) {
        if (!enabled()) return
        runCatching { repo().add(kind, "$title@${clock.now().toEpochMilliseconds()}", title, clock.now(), body = body) }
    }
    override fun completed(title: String) { inner.completed(title); record(NotificationKind.DOWNLOAD_DONE, title, "Download finished") }
    override fun failed(title: String, reason: String) { inner.failed(title, reason); record(NotificationKind.DOWNLOAD_FAILED, title, reason) }
    override fun summary(text: String) { inner.summary(text); record(NotificationKind.DOWNLOAD_DONE, text, "Automatic downloads") }
}

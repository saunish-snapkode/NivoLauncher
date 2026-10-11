package com.snapkode.nivolauncher

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Shared view of how many notifications each app currently has, keyed by [badgeKey]. */
object NotificationState {
    private val _counts = MutableStateFlow<Map<String, Int>>(emptyMap())
    val counts: StateFlow<Map<String, Int>> = _counts.asStateFlow()

    fun update(counts: Map<String, Int>) {
        _counts.value = counts
    }

    fun badgeKey(packageName: String, userHash: Int) = "$packageName#$userHash"
}

/** What is playing right now, as reported by the active media session. */
data class NowPlaying(
    val title: String, val artist: String, val playing: Boolean, val packageName: String,
    val album: String = "",
    /** The cover art, at most 512 px on a side, or null if the player gave none. */
    val art: android.graphics.Bitmap? = null,
    val durationMs: Long = 0L,
    /** The position when it was last reported, and the clock time of that report; see [MediaProgress]. */
    val positionMs: Long = 0L,
    val positionAtMs: Long = 0L,
    val speed: Float = 1f,
)

/** Where a track is now, from its last report. Pure. */
object MediaProgress {
    fun position(n: NowPlaying, nowMs: Long): Long {
        val moved = if (n.playing) ((nowMs - n.positionAtMs) * n.speed).toLong() else 0L
        val p = n.positionMs + moved
        return if (n.durationMs > 0) p.coerceIn(0L, n.durationMs) else p.coerceAtLeast(0L)
    }

    fun fraction(n: NowPlaying, nowMs: Long): Float =
        if (n.durationMs <= 0) 0f else (position(n, nowMs).toFloat() / n.durationMs).coerceIn(0f, 1f)

    /** "3:07", or "1:02:09" past an hour. */
    fun clock(ms: Long): String {
        val s = (ms / 1000).coerceAtLeast(0)
        return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s % 3600 / 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
    }
}

object NowPlayingState {
    private val _current = MutableStateFlow<NowPlaying?>(null)
    val current: StateFlow<NowPlaying?> = _current.asStateFlow()

    @Volatile var controller: android.media.session.MediaController? = null

    fun update(value: NowPlaying?) { _current.value = value }

    fun playPause() {
        val c = controller ?: return
        if (c.playbackState?.state == android.media.session.PlaybackState.STATE_PLAYING) c.transportControls.pause() else c.transportControls.play()
    }
    fun seekTo(ms: Long) { controller?.transportControls?.seekTo(ms) }
    fun next() { controller?.transportControls?.skipToNext() }
    fun previous() { controller?.transportControls?.skipToPrevious() }
}

/**
 * Counts active notifications so icons can show dots or numbers.
 * Needs the user to grant notification access once in system settings.
 */
class NivoNotificationListener : NotificationListenerService() {
    private var mediaCallbackTarget: android.media.session.MediaController? = null
    private val mediaCallback = object : android.media.session.MediaController.Callback() {
        override fun onMetadataChanged(metadata: android.media.MediaMetadata?) = publishMedia()
        override fun onPlaybackStateChanged(state: android.media.session.PlaybackState?) = publishMedia()
        override fun onSessionDestroyed() = publishMedia()
    }
    private val sessionsListener = android.media.session.MediaSessionManager.OnActiveSessionsChangedListener { watch(it) }

    override fun onListenerConnected() {
        refresh()
        runCatching {
            val manager = getSystemService(android.media.session.MediaSessionManager::class.java)
            val me = android.content.ComponentName(this, NivoNotificationListener::class.java)
            manager.addOnActiveSessionsChangedListener(sessionsListener, me)
            watch(manager.getActiveSessions(me))
        }
    }

    override fun onListenerDisconnected() {
        NotificationState.update(emptyMap())
        runCatching { getSystemService(android.media.session.MediaSessionManager::class.java).removeOnActiveSessionsChangedListener(sessionsListener) }
        mediaCallbackTarget?.unregisterCallback(mediaCallback)
        mediaCallbackTarget = null
        NowPlayingState.controller = null
        NowPlayingState.update(null)
    }

    /** Follows the most relevant session: one that is playing, else the first with a title. */
    private fun watch(controllers: List<android.media.session.MediaController>?) {
        val pick = controllers.orEmpty().firstOrNull { it.playbackState?.state == android.media.session.PlaybackState.STATE_PLAYING }
            ?: controllers.orEmpty().firstOrNull { it.metadata != null }
        if (pick?.sessionToken != mediaCallbackTarget?.sessionToken) {
            mediaCallbackTarget?.unregisterCallback(mediaCallback)
            mediaCallbackTarget = pick
            pick?.registerCallback(mediaCallback)
        }
        NowPlayingState.controller = pick
        publishMedia()
    }

    private fun publishMedia() {
        val c = mediaCallbackTarget
        val meta = c?.metadata
        val title = meta?.getString(android.media.MediaMetadata.METADATA_KEY_TITLE)
        if (c == null || title.isNullOrBlank()) { NowPlayingState.update(null); return }
        val artist = meta.getString(android.media.MediaMetadata.METADATA_KEY_ARTIST).orEmpty()
        val album = meta.getString(android.media.MediaMetadata.METADATA_KEY_ALBUM).orEmpty()
        val state = c.playbackState
        val playing = state?.state == android.media.session.PlaybackState.STATE_PLAYING
        val art = (meta.getBitmap(android.media.MediaMetadata.METADATA_KEY_ALBUM_ART) ?: meta.getBitmap(android.media.MediaMetadata.METADATA_KEY_ART))?.let(::scaled)
        NowPlayingState.update(
            NowPlaying(
                title, artist, playing, c.packageName, album, art,
                durationMs = meta.getLong(android.media.MediaMetadata.METADATA_KEY_DURATION).coerceAtLeast(0L),
                positionMs = state?.position?.coerceAtLeast(0L) ?: 0L,
                positionAtMs = state?.lastPositionUpdateTime?.let { android.os.SystemClock.elapsedRealtime().let { er -> System.currentTimeMillis() - (er - it) } } ?: System.currentTimeMillis(),
                speed = state?.playbackSpeed?.takeIf { it > 0f } ?: 1f,
            ),
        )
    }
    /** Keeps the cover small: it is shown once, on one page. */
    private fun scaled(b: android.graphics.Bitmap): android.graphics.Bitmap {
        val longest = maxOf(b.width, b.height)
        if (longest <= 512) return b
        val f = 512f / longest
        return android.graphics.Bitmap.createScaledBitmap(b, (b.width * f).toInt().coerceAtLeast(1), (b.height * f).toInt().coerceAtLeast(1), true)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) = refresh()
    override fun onNotificationRemoved(sbn: StatusBarNotification) = refresh()

    private fun refresh() {
        val counts = HashMap<String, Int>()
        val active = runCatching { activeNotifications }.getOrNull().orEmpty()
        for (sbn in active) {
            val summary = sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY != 0
            val persistent = sbn.isOngoing && !sbn.isClearable
            if (summary || persistent) continue
            val key = NotificationState.badgeKey(sbn.packageName, sbn.user.hashCode())
            counts[key] = (counts[key] ?: 0) + 1
        }
        NotificationState.update(counts)
    }
}

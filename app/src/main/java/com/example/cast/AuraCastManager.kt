package com.example.cast

import android.content.Context
import android.util.Log
import androidx.media3.cast.CastPlayer
import androidx.media3.cast.SessionAvailabilityListener
import androidx.media3.common.MediaItem as Media3Item
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.example.data.MediaItem as AuraMediaItem
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManagerListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

class AuraCastManager private constructor(
    private val context: Context,
    private val localExoPlayer: ExoPlayer
) {
    private val castContext: CastContext = CastContext.getSharedInstance(context)
    private val castPlayer: CastPlayer = CastPlayer(castContext)

    private val _isCasting = MutableStateFlow(false)
    val isCasting: StateFlow<Boolean> = _isCasting.asStateFlow()

    private val _deviceName = MutableStateFlow<String?>(null)
    val deviceName: StateFlow<String?> = _deviceName.asStateFlow()

    private val _activeCastItem = MutableStateFlow<AuraMediaItem?>(null)
    val activeCastItem: StateFlow<AuraMediaItem?> = _activeCastItem.asStateFlow()

    private var server: AuraCastServer? = null
    private val activeMediaIds = mutableSetOf<String>()

    private data class PendingCast(val resolved: ResolvedCastMedia)
    private var pending: PendingCast? = null

    init {
        castPlayer.setSessionAvailabilityListener(object : SessionAvailabilityListener {
            override fun onCastSessionAvailable() {
                Log.i(TAG, "Cast session available")
                handoffToCast()
            }

            override fun onCastSessionUnavailable() {
                Log.i(TAG, "Cast session unavailable")
                handoffToLocal()
            }
        })

        castContext.sessionManager.addSessionManagerListener(
            object : SessionManagerListener<CastSession> {
                override fun onSessionStarted(session: CastSession, sessionId: String) {
                    _isCasting.value = true
                    _deviceName.value = session.castDevice?.friendlyName ?: "Chromecast"
                    Log.i(TAG, "Cast session started on ${_deviceName.value}")
                    fulfillPending()
                }

                override fun onSessionEnded(session: CastSession, error: Int) {
                    Log.i(TAG, "Cast session ended")
                    _isCasting.value = false
                    _deviceName.value = null
                    _activeCastItem.value = null
                    pending = null
                    shutdownServer()
                }

                override fun onSessionStarting(session: CastSession) {}
                override fun onSessionStartFailed(session: CastSession, error: Int) {}
                override fun onSessionEnding(session: CastSession) {}
                override fun onSessionResuming(session: CastSession, sessionId: String) {}
                override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) {}
                override fun onSessionResumeFailed(session: CastSession, error: Int) {}
                override fun onSessionSuspended(session: CastSession, reason: Int) {}
            },
            CastSession::class.java
        )
    }

    fun requestCast(resolved: ResolvedCastMedia) {
        pending = PendingCast(resolved)
        if (_isCasting.value) {
            fulfillPending()
        }
    }

    private fun fulfillPending() {
        val p = pending ?: return
        pending = null
        castMedia(p.resolved)
    }

    private fun castMedia(resolved: ResolvedCastMedia) {
        val srv = server ?: AuraCastServer(context).also {
            it.start()
            server = it
        }

        val sanitizedId = UUID.randomUUID().toString().replace("-", "")
        srv.registerMedia(sanitizedId, resolved.source, resolved.thumbSource)
        activeMediaIds.add(sanitizedId)

        val originalMedia3Item = Media3Item.Builder()
            .setMediaMetadata(MediaMetadata.Builder().setTitle(resolved.mediaItem.title).build())
            .build()

        val castMediaItem = AuraCastMetadataSanitizer.toSanitizedCastMediaItem(
            original = originalMedia3Item,
            sanitizedId = sanitizedId,
            sessionToken = srv.sessionToken,
            localProxyBaseUrl = srv.getLocalBaseUrl(),
            durationMs = resolved.durationMs,
            isVideo = resolved.isVideo,
            mimeType = resolved.source.mimeType
        )

        _activeCastItem.value = resolved.mediaItem

        val capturePos = localExoPlayer.currentPosition
        val capturePlayWhenReady = localExoPlayer.playWhenReady
        localExoPlayer.pause()

        castPlayer.setMediaItem(castMediaItem)
        castPlayer.prepare()
        castPlayer.seekTo(capturePos)
        castPlayer.playWhenReady = capturePlayWhenReady
    }

    private fun handoffToCast() {
        val capturePos = localExoPlayer.currentPosition
        val capturePlayWhenReady = localExoPlayer.playWhenReady
        localExoPlayer.pause()

        if (castPlayer.mediaItemCount > 0) {
            castPlayer.seekTo(capturePos)
            castPlayer.playWhenReady = capturePlayWhenReady
        }
    }

    private fun handoffToLocal() {
        val remotePos = castPlayer.currentPosition
        val remoteState = castPlayer.playbackState
        val remotePlaying = castPlayer.isPlaying

        castPlayer.pause()

        // Handoff to local player within ±500ms tolerance
        val targetPos = if (remotePos > 0L) (remotePos - 500L).coerceAtLeast(0L) else 0L
        localExoPlayer.seekTo(targetPos)

        when {
            remotePlaying -> {
                localExoPlayer.prepare()
                localExoPlayer.play()
            }
            remoteState == Player.STATE_ENDED -> {
                localExoPlayer.pause()
            }
            else -> {
                localExoPlayer.pause()
            }
        }
    }

    private fun shutdownServer() {
        val srv = server ?: return
        activeMediaIds.forEach { srv.unregisterMedia(it) }
        activeMediaIds.clear()
        srv.stop()
        server = null
    }

    fun release() {
        pending = null
        shutdownServer()
        castPlayer.release()
    }

    companion object {
        private const val TAG = "AuraCastManager"
        @Volatile
        private var instance: AuraCastManager? = null

        fun getInstance(context: Context, localExoPlayer: ExoPlayer): AuraCastManager {
            return instance ?: synchronized(this) {
                instance ?: AuraCastManager(context.applicationContext, localExoPlayer).also { instance = it }
            }
        }
    }
}

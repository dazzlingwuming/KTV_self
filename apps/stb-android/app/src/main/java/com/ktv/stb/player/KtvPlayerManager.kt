package com.ktv.stb.player

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import com.ktv.stb.domain.model.PlayerSnapshot
import com.ktv.stb.queue.PlayableQueueItem
import com.ktv.stb.queue.QueueManager
import com.ktv.stb.storage.MediaFileInspector
import java.io.File

class KtvPlayerManager(
    context: Context,
    private val queueManager: QueueManager,
    private val mediaFileInspector: MediaFileInspector,
    private val playbackStateDispatcher: PlaybackStateDispatcher,
    private val onQueueStateChanged: () -> Unit,
) {
    companion object {
        private const val PROGRESS_UPDATE_INTERVAL_MS = 1000L
    }

    private val videoPlayer = ExoPlayer.Builder(context).build()
    private val vocalPlayer = ExoPlayer.Builder(context).build()
    private val accompanimentPlayer = ExoPlayer.Builder(context).build()
    private val mediaSourceFactory = ProgressiveMediaSource.Factory(DefaultDataSource.Factory(context))
    private val mainHandler = Handler(Looper.getMainLooper())

    private var currentSongId: String? = null
    private var currentTitle: String? = null
    private var currentPlayableItem: PlayableQueueItem? = null
    private var lastErrorMessage: String? = null
    private var masterVolume: Int = 100
    private var vocalVolume: Int = 100
    private var accompanimentVolume: Int = 100
    private var mixAvailable: Boolean = false
    private val progressUpdater = object : Runnable {
        override fun run() {
            dispatchState()
            if (shouldKeepProgressUpdates()) {
                mainHandler.postDelayed(this, PROGRESS_UPDATE_INTERVAL_MS)
            }
        }
    }

    @Volatile
    private var latestSnapshot = PlayerSnapshot(
        playStatus = "idle",
        currentSongId = null,
        currentTitle = null,
        currentTimeMs = 0L,
        durationMs = 0L,
        volume = 100,
        playMode = "original",
        vocalVolume = 100,
        accompanimentVolume = 100,
        mixAvailable = false,
        errorMessage = null,
    )

    init {
        videoPlayer.addListener(
            object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_ENDED) {
                        stopProgressUpdates()
                        stopAuxiliaryPlayers()
                        currentSongId?.let { queueManager.markFinished(it) }
                        onQueueStateChanged()
                        playNext()
                    } else {
                        syncProgressUpdates()
                        dispatchState()
                    }
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    if (isPlaying) {
                        syncAuxiliaryPlayState(true)
                    }
                    syncProgressUpdates()
                    dispatchState()
                }

                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    stopProgressUpdates()
                    stopAuxiliaryPlayers()
                    lastErrorMessage = error.message
                    currentSongId?.let {
                        queueManager.markSkipped(
                            songId = it,
                            skipReason = "PLAYBACK_ERROR",
                            errorMessage = error.message,
                        )
                    }
                    onQueueStateChanged()
                    dispatchState()
                    playNext()
                }
            },
        )

        val auxErrorHandler: (androidx.media3.common.PlaybackException) -> Unit = { error ->
            runOnMain {
                lastErrorMessage = error.message ?: "audio mix playback failed"
                currentSongId?.let {
                    queueManager.markSkipped(
                        songId = it,
                        skipReason = "PLAYBACK_ERROR",
                        errorMessage = lastErrorMessage,
                    )
                }
                stopInternal(resetCurrent = true)
                onQueueStateChanged()
                dispatchState()
                playNextInternal()
            }
        }

        vocalPlayer.addListener(object : Player.Listener {
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                auxErrorHandler(error)
            }
        })
        accompanimentPlayer.addListener(object : Player.Listener {
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                auxErrorHandler(error)
            }
        })
    }

    fun tryStartPlaybackFromQueue() {
        runOnMain {
            if (videoPlayer.isPlaying || currentSongId != null) return@runOnMain
            playNextInternal()
        }
    }

    fun playNext() {
        runOnMain { playNextInternal() }
    }

    fun skipToNext() {
        runOnMain {
            currentSongId?.let { queueManager.markFinished(it) }
            onQueueStateChanged()
            playNextInternal()
        }
    }

    fun handleUnavailableSong(songId: String) {
        runOnMain {
            if (currentSongId == songId) {
                stopInternal(resetCurrent = true)
                dispatchState()
                playNextInternal()
            }
        }
    }

    fun pause() {
        runOnMain {
            videoPlayer.pause()
            syncAuxiliaryPlayState(false)
            syncProgressUpdates()
            dispatchState()
        }
    }

    fun resume() {
        runOnMain {
            videoPlayer.playWhenReady = true
            syncAuxiliaryPlayState(true)
            syncProgressUpdates()
            dispatchState()
        }
    }

    fun setVolume(value: Int) {
        runOnMain {
            masterVolume = value.coerceIn(0, 100)
            applyVolumes()
            dispatchState()
        }
    }

    fun setVocalVolume(value: Int) {
        runOnMain {
            vocalVolume = value.coerceIn(0, 100)
            applyVolumes()
            dispatchState()
        }
    }

    fun setAccompanimentVolume(value: Int) {
        runOnMain {
            accompanimentVolume = value.coerceIn(0, 100)
            applyVolumes()
            dispatchState()
        }
    }

    fun setPlayMode(value: String) {
        runOnMain {
            if (!mixAvailable) {
                lastErrorMessage = "mix not ready"
                dispatchState()
                return@runOnMain
            }
            when (value.lowercase()) {
                "original" -> {
                    vocalVolume = 100
                    accompanimentVolume = 100
                }
                "accompaniment" -> {
                    vocalVolume = 0
                    accompanimentVolume = 100
                }
            }
            applyVolumes()
            dispatchState()
        }
    }

    fun onSongMixReady(songId: String) {
        runOnMain {
            if (currentSongId != songId) return@runOnMain
            val updatedItem = queueManager.findPlayableItemBySongId(songId) ?: return@runOnMain
            currentPlayableItem = updatedItem
            if (!hasUsableMix(updatedItem)) {
                dispatchState()
                return@runOnMain
            }
            attachMixSources(updatedItem, keepCurrentPosition = true)
            dispatchState()
        }
    }

    fun getSnapshot(): PlayerSnapshot = latestSnapshot

    fun getPlayer(): ExoPlayer = videoPlayer

    private fun playNextInternal() {
        val next = queueManager.findNextPlayableItem()
        if (next == null) {
            stopInternal(resetCurrent = true)
            dispatchState()
            return
        }

        queueManager.markPlaying(next.queueId)
        onQueueStateChanged()
        currentSongId = next.songId
        currentTitle = next.title
        currentPlayableItem = next
        lastErrorMessage = null

        val videoFile = File(next.filePath)
        val videoExists = videoFile.exists() && videoFile.length() > 0L
        if (!videoExists) {
            queueManager.markSkipped(next.songId, "INVALID_LOCAL_FILE", "video file missing")
            onQueueStateChanged()
            stopInternal(resetCurrent = true)
            dispatchState()
            playNextInternal()
            return
        }

        val hasMix = hasUsableMix(next)
        mixAvailable = hasMix
        if (!hasMix) {
            vocalVolume = 100
            accompanimentVolume = 100
        }

        val mediaMetadata = MediaMetadata.Builder().setTitle(next.title).build()
        val videoSource = mediaSourceFactory.createMediaSource(
            MediaItem.Builder()
                .setUri(Uri.fromFile(videoFile))
                .setMediaMetadata(mediaMetadata)
                .build(),
        )
        videoPlayer.setMediaSource(videoSource)
        videoPlayer.prepare()

        if (mixAvailable) {
            attachMixSources(next, keepCurrentPosition = false)
        } else {
            stopAuxiliaryPlayers()
        }

        applyVolumes()
        videoPlayer.playWhenReady = true
        syncAuxiliaryPlayState(true)
        syncProgressUpdates()
        dispatchState()
    }

    private fun hasUsableMix(item: PlayableQueueItem): Boolean {
        val vocalPath = item.vocalPath
        val accompanimentPath = item.accompanimentPath
        return !vocalPath.isNullOrBlank() &&
            !accompanimentPath.isNullOrBlank() &&
            File(vocalPath).exists() &&
            File(accompanimentPath).exists() &&
            File(vocalPath).length() > 0L &&
            File(accompanimentPath).length() > 0L
    }

    private fun attachMixSources(item: PlayableQueueItem, keepCurrentPosition: Boolean) {
        val vocalPath = item.vocalPath ?: return
        val accompanimentPath = item.accompanimentPath ?: return
        mixAvailable = true
        val mediaMetadata = MediaMetadata.Builder().setTitle(item.title).build()
        val vocalSource = mediaSourceFactory.createMediaSource(
            MediaItem.Builder()
                .setUri(Uri.fromFile(File(vocalPath)))
                .setMediaMetadata(mediaMetadata)
                .build(),
        )
        val accompanimentSource = mediaSourceFactory.createMediaSource(
            MediaItem.Builder()
                .setUri(Uri.fromFile(File(accompanimentPath)))
                .setMediaMetadata(mediaMetadata)
                .build(),
        )
        vocalPlayer.setMediaSource(vocalSource)
        accompanimentPlayer.setMediaSource(accompanimentSource)
        vocalPlayer.prepare()
        accompanimentPlayer.prepare()
        if (keepCurrentPosition) {
            val position = videoPlayer.currentPosition.coerceAtLeast(0L)
            vocalPlayer.seekTo(position)
            accompanimentPlayer.seekTo(position)
        }
        applyVolumes()
        syncAuxiliaryPlayState(videoPlayer.isPlaying)
    }

    private fun syncAuxiliaryPlayState(shouldPlay: Boolean) {
        if (!mixAvailable) return
        if (shouldPlay) {
            if (vocalPlayer.playbackState != Player.STATE_IDLE) {
                vocalPlayer.playWhenReady = true
            }
            if (accompanimentPlayer.playbackState != Player.STATE_IDLE) {
                accompanimentPlayer.playWhenReady = true
            }
        } else {
            vocalPlayer.pause()
            accompanimentPlayer.pause()
        }
    }

    private fun applyVolumes() {
        if (mixAvailable) {
            videoPlayer.volume = 0f
            vocalPlayer.volume = (vocalVolume.coerceIn(0, 100) / 100f) * (masterVolume.coerceIn(0, 100) / 100f)
            accompanimentPlayer.volume = (accompanimentVolume.coerceIn(0, 100) / 100f) * (masterVolume.coerceIn(0, 100) / 100f)
        } else {
            videoPlayer.volume = masterVolume.coerceIn(0, 100) / 100f
            vocalPlayer.volume = 0f
            accompanimentPlayer.volume = 0f
        }
    }

    private fun buildSnapshot(): PlayerSnapshot {
        return PlayerSnapshot(
            playStatus = resolvePlayStatus(),
            currentSongId = currentSongId,
            currentTitle = currentTitle,
            currentTimeMs = videoPlayer.currentPosition.coerceAtLeast(0L),
            durationMs = videoPlayer.duration.takeIf { it > 0 } ?: 0L,
            volume = masterVolume,
            playMode = if (mixAvailable) "mix" else "original",
            vocalVolume = vocalVolume,
            accompanimentVolume = accompanimentVolume,
            mixAvailable = mixAvailable,
            errorMessage = lastErrorMessage,
        )
    }

    private fun resolvePlayStatus(): String {
        return when {
            lastErrorMessage != null -> "error"
            videoPlayer.playbackState == Player.STATE_IDLE -> "idle"
            videoPlayer.playbackState == Player.STATE_BUFFERING -> "buffering"
            videoPlayer.playbackState == Player.STATE_ENDED -> "ended"
            videoPlayer.isPlaying -> "playing"
            videoPlayer.playbackState == Player.STATE_READY -> "paused"
            else -> "idle"
        }
    }

    private fun dispatchState() {
        latestSnapshot = buildSnapshot()
        playbackStateDispatcher.dispatch(latestSnapshot)
    }

    private fun stopInternal(resetCurrent: Boolean) {
        stopProgressUpdates()
        videoPlayer.stop()
        videoPlayer.clearMediaItems()
        stopAuxiliaryPlayers()
        if (resetCurrent) {
            currentSongId = null
            currentTitle = null
            currentPlayableItem = null
            mixAvailable = false
            lastErrorMessage = null
        }
    }

    private fun stopAuxiliaryPlayers() {
        vocalPlayer.stop()
        accompanimentPlayer.stop()
        vocalPlayer.clearMediaItems()
        accompanimentPlayer.clearMediaItems()
    }

    private fun runOnMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
        } else {
            mainHandler.post(block)
        }
    }

    private fun syncProgressUpdates() {
        stopProgressUpdates()
        if (shouldKeepProgressUpdates()) {
            mainHandler.post(progressUpdater)
        }
    }

    private fun stopProgressUpdates() {
        mainHandler.removeCallbacks(progressUpdater)
    }

    private fun shouldKeepProgressUpdates(): Boolean {
        return currentSongId != null && videoPlayer.playbackState in listOf(Player.STATE_READY, Player.STATE_BUFFERING)
    }
}

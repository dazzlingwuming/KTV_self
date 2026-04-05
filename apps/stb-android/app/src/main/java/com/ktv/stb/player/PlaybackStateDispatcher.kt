package com.ktv.stb.player

import com.ktv.stb.domain.model.PlayerSnapshot
import com.ktv.stb.server.ws.MobileWebSocketHub

class PlaybackStateDispatcher(
    private val webSocketHub: MobileWebSocketHub,
) {
    fun dispatch(snapshot: PlayerSnapshot) {
        webSocketHub.broadcastPlayerUpdated(
            mapOf(
                "play_status" to snapshot.playStatus,
                "current_song_id" to snapshot.currentSongId,
                "title" to snapshot.currentTitle,
                "current_time_ms" to snapshot.currentTimeMs,
                "duration_ms" to snapshot.durationMs,
                "volume" to snapshot.volume,
                "mode" to snapshot.playMode,
                "vocal_volume" to snapshot.vocalVolume,
                "accompaniment_volume" to snapshot.accompanimentVolume,
                "mix_available" to snapshot.mixAvailable,
                "error_message" to snapshot.errorMessage,
            ),
        )
    }
}

package com.ktv.stb.server.route

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.ktv.stb.common.result.ApiResult
import com.ktv.stb.domain.usecase.GetPlayerStatusUseCase
import com.ktv.stb.domain.usecase.PausePlayerUseCase
import com.ktv.stb.domain.usecase.PlayNextUseCase
import com.ktv.stb.domain.usecase.ResumePlayerUseCase
import com.ktv.stb.domain.usecase.SetAccompanimentVolumeUseCase
import com.ktv.stb.domain.usecase.SetVolumeUseCase
import com.ktv.stb.domain.usecase.SetVocalVolumeUseCase
import com.ktv.stb.domain.usecase.SwitchPlayModeUseCase
import fi.iki.elonen.NanoHTTPD

class PlayerRoutes(
    private val getPlayerStatusUseCase: GetPlayerStatusUseCase,
    private val playNextUseCase: PlayNextUseCase,
    private val pausePlayerUseCase: PausePlayerUseCase,
    private val resumePlayerUseCase: ResumePlayerUseCase,
    private val setVolumeUseCase: SetVolumeUseCase,
    private val setVocalVolumeUseCase: SetVocalVolumeUseCase,
    private val setAccompanimentVolumeUseCase: SetAccompanimentVolumeUseCase,
    private val switchPlayModeUseCase: SwitchPlayModeUseCase,
    private val gson: Gson,
) {
    fun next(): NanoHTTPD.Response {
        playNextUseCase.execute()
        return json(ApiResult(data = mapOf("success" to true)))
    }

    fun pause(): NanoHTTPD.Response {
        pausePlayerUseCase.execute()
        return json(ApiResult(data = mapOf("success" to true)))
    }

    fun resume(): NanoHTTPD.Response {
        resumePlayerUseCase.execute()
        return json(ApiResult(data = mapOf("success" to true)))
    }

    fun volume(session: NanoHTTPD.IHTTPSession): NanoHTTPD.Response {
        val request = gson.fromJson(parseBody(session), VolumeRequest::class.java)
        setVolumeUseCase.execute(request.value)
        return json(ApiResult(data = mapOf("success" to true, "value" to request.value)))
    }

    fun vocalVolume(session: NanoHTTPD.IHTTPSession): NanoHTTPD.Response {
        val request = gson.fromJson(parseBody(session), VolumeRequest::class.java)
        setVocalVolumeUseCase.execute(request.value)
        return json(ApiResult(data = mapOf("success" to true, "value" to request.value)))
    }

    fun accompanimentVolume(session: NanoHTTPD.IHTTPSession): NanoHTTPD.Response {
        val request = gson.fromJson(parseBody(session), VolumeRequest::class.java)
        setAccompanimentVolumeUseCase.execute(request.value)
        return json(ApiResult(data = mapOf("success" to true, "value" to request.value)))
    }

    fun mode(session: NanoHTTPD.IHTTPSession): NanoHTTPD.Response {
        val request = gson.fromJson(parseBody(session), ModeRequest::class.java)
        switchPlayModeUseCase.execute(request.mode)
        return json(ApiResult(data = mapOf("success" to true, "mode" to request.mode)))
    }

    fun status(): NanoHTTPD.Response {
        val snapshot = getPlayerStatusUseCase.execute()
        return json(
            ApiResult(
                data = PlayerStatusResponse(
                    playStatus = snapshot.playStatus,
                    currentSongId = snapshot.currentSongId,
                    title = snapshot.currentTitle,
                    currentTimeMs = snapshot.currentTimeMs,
                    durationMs = snapshot.durationMs,
                    volume = snapshot.volume,
                    mode = snapshot.playMode,
                    vocalVolume = snapshot.vocalVolume,
                    accompanimentVolume = snapshot.accompanimentVolume,
                    mixAvailable = snapshot.mixAvailable,
                    errorMessage = snapshot.errorMessage,
                ),
            ),
        )
    }

    private fun json(any: Any): NanoHTTPD.Response {
        return NanoHTTPD.newFixedLengthResponse(
            NanoHTTPD.Response.Status.OK,
            "application/json; charset=utf-8",
            gson.toJson(any),
        ).apply { addHeader("Access-Control-Allow-Origin", "*") }
    }

    private fun parseBody(session: NanoHTTPD.IHTTPSession): String {
        val files = HashMap<String, String>()
        session.parseBody(files)
        return files["postData"] ?: "{}"
    }
}

data class PlayerStatusResponse(
    @SerializedName("play_status") val playStatus: String,
    @SerializedName("current_song_id") val currentSongId: String?,
    @SerializedName("title") val title: String?,
    @SerializedName("current_time_ms") val currentTimeMs: Long,
    @SerializedName("duration_ms") val durationMs: Long,
    @SerializedName("volume") val volume: Int,
    @SerializedName("mode") val mode: String,
    @SerializedName("vocal_volume") val vocalVolume: Int,
    @SerializedName("accompaniment_volume") val accompanimentVolume: Int,
    @SerializedName("mix_available") val mixAvailable: Boolean,
    @SerializedName("error_message") val errorMessage: String?,
)

data class VolumeRequest(
    @SerializedName("value") val value: Int,
)

data class ModeRequest(
    @SerializedName("mode") val mode: String,
)

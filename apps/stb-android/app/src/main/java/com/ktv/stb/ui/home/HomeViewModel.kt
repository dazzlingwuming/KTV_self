package com.ktv.stb.ui.home

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import com.ktv.stb.app.AppContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class HomeViewModel(
    private val appContainer: AppContainer,
) : ViewModel() {
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    fun refresh() {
        val session = appContainer.sessionManager.getCurrentSession()
        val queueSnapshot = appContainer.queueManager.getQueueSnapshot()
        val playerSnapshot = appContainer.ktvPlayerManager.getSnapshot()
        _uiState.value = HomeUiState(
            currentTitle = playerSnapshot.currentTitle ?: "暂无播放",
            playStatus = playerSnapshot.playStatus,
            currentTimeMs = playerSnapshot.currentTimeMs,
            durationMs = playerSnapshot.durationMs,
            nextTitle = queueSnapshot.upcoming.firstOrNull()?.title,
            deviceName = session.deviceName,
            clientCount = session.clientCount,
            volume = playerSnapshot.volume,
            vocalVolume = playerSnapshot.vocalVolume,
            accompanimentVolume = playerSnapshot.accompanimentVolume,
            mixAvailable = playerSnapshot.mixAvailable,
        )
    }

    fun buildQrBitmap(): Bitmap {
        return appContainer.qrCodeManager.buildQrBitmap(320)
    }
}

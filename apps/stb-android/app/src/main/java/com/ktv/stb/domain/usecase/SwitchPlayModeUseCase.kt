package com.ktv.stb.domain.usecase

import com.ktv.stb.player.KtvPlayerManager

class SwitchPlayModeUseCase(
    private val playerManager: KtvPlayerManager,
) {
    fun execute(mode: String) = playerManager.setPlayMode(mode)
}

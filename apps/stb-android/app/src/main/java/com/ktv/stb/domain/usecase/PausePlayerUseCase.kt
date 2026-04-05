package com.ktv.stb.domain.usecase

import com.ktv.stb.player.KtvPlayerManager

class PausePlayerUseCase(
    private val playerManager: KtvPlayerManager,
) {
    fun execute() = playerManager.pause()
}

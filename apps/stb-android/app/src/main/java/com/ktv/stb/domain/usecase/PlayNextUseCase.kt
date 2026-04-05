package com.ktv.stb.domain.usecase

import com.ktv.stb.player.KtvPlayerManager

class PlayNextUseCase(
    private val playerManager: KtvPlayerManager,
) {
    fun execute() = playerManager.skipToNext()
}

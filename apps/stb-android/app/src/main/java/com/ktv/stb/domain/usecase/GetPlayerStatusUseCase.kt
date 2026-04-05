package com.ktv.stb.domain.usecase

import com.ktv.stb.player.KtvPlayerManager

class GetPlayerStatusUseCase(
    private val playerManager: KtvPlayerManager,
) {
    fun execute() = playerManager.getSnapshot()
}

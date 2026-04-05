package com.ktv.stb.domain.usecase

import com.ktv.stb.player.KtvPlayerManager

class SetVolumeUseCase(
    private val playerManager: KtvPlayerManager,
) {
    fun execute(value: Int) = playerManager.setVolume(value)
}

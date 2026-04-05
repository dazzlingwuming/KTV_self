package com.ktv.stb.domain.usecase

import com.ktv.stb.player.KtvPlayerManager

class SetVocalVolumeUseCase(
    private val playerManager: KtvPlayerManager,
) {
    fun execute(value: Int) = playerManager.setVocalVolume(value)
}

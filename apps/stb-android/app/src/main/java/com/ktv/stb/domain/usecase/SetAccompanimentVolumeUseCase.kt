package com.ktv.stb.domain.usecase

import com.ktv.stb.player.KtvPlayerManager

class SetAccompanimentVolumeUseCase(
    private val playerManager: KtvPlayerManager,
) {
    fun execute(value: Int) = playerManager.setAccompanimentVolume(value)
}

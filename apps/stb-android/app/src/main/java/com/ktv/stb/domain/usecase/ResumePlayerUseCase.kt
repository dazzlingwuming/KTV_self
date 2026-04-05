package com.ktv.stb.domain.usecase

import com.ktv.stb.player.KtvPlayerManager

class ResumePlayerUseCase(
    private val playerManager: KtvPlayerManager,
) {
    fun execute() = playerManager.resume()
}

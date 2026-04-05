package com.ktv.stb.domain.usecase

import com.ktv.stb.queue.QueueManager

class GetQueueUseCase(
    private val queueManager: QueueManager,
) {
    fun execute() = queueManager.getQueueSnapshot()
}

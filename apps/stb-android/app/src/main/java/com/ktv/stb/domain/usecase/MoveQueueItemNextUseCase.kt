package com.ktv.stb.domain.usecase

import com.ktv.stb.queue.QueueManager

class MoveQueueItemNextUseCase(
    private val queueManager: QueueManager,
) {
    fun execute(queueId: String) = queueManager.moveNext(queueId)
}

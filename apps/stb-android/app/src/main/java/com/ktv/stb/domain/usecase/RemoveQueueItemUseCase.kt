package com.ktv.stb.domain.usecase

import com.ktv.stb.queue.QueueManager

class RemoveQueueItemUseCase(
    private val queueManager: QueueManager,
) {
    fun execute(queueId: String) = queueManager.removeQueueItem(queueId)
}

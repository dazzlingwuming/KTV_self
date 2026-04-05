package com.ktv.stb.domain.usecase

import com.ktv.stb.queue.QueueManager
import com.ktv.stb.server.route.CreateOrderRequest

class CreateOrderUseCase(
    private val queueManager: QueueManager,
) {
    fun execute(request: CreateOrderRequest) = queueManager.createOrder(request)
}

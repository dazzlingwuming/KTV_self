package com.ktv.host.task

import java.util.concurrent.ConcurrentHashMap

class InMemorySeparateTaskRepository : SeparateTaskRepository {
    private val store = ConcurrentHashMap<String, SeparateTaskRecord>()

    override fun save(task: SeparateTaskRecord) {
        store[task.taskId] = task
    }

    override fun find(taskId: String): SeparateTaskRecord? = store[taskId]
}

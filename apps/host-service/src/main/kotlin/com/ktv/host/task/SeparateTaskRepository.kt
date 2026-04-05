package com.ktv.host.task

interface SeparateTaskRepository {
    fun save(task: SeparateTaskRecord)
    fun find(taskId: String): SeparateTaskRecord?
}

data class SeparateTaskRecord(
    val taskId: String,
    val songId: String,
    val status: String,
    val progress: Int,
    val accompanimentPath: String? = null,
    val vocalPath: String? = null,
)

package com.ktv.host.task

import com.ktv.host.storage.HostStoragePaths
import java.io.File
import java.util.UUID

class SeparateTaskManager(
    private val repository: SeparateTaskRepository,
    private val storagePaths: HostStoragePaths,
) {
    fun createPendingTask(songId: String): SeparateTaskRecord {
        val task = SeparateTaskRecord(
            taskId = UUID.randomUUID().toString(),
            songId = songId,
            status = "pending",
            progress = 0,
        )
        repository.save(task)
        return task
    }

    fun markMockCompleted(taskId: String) {
        val task = repository.find(taskId) ?: return
        val acc = storagePaths.accompanimentFile(taskId)
        val vocal = storagePaths.vocalFile(taskId)
        writePlaceholder(acc)
        writePlaceholder(vocal)
        repository.save(
            task.copy(
                status = "success",
                progress = 100,
                accompanimentPath = acc.absolutePath,
                vocalPath = vocal.absolutePath,
            ),
        )
    }

    fun find(taskId: String): SeparateTaskRecord? = repository.find(taskId)

    private fun writePlaceholder(file: File) {
        if (!file.exists()) {
            file.writeBytes(byteArrayOf())
        }
    }
}

package com.ktv.stb.domain.model

data class QueueItem(
    val queueId: String,
    val songId: String,
    val title: String,
    val sourceId: String,
    val downloadStatus: String,
    val queueStatus: String,
    val skipReason: String?,
    val errorMessage: String?,
    val playMode: String,
    val position: Int,
    val orderedByClientId: String,
    val orderedByClientName: String?,
    val createdAt: Long,
)

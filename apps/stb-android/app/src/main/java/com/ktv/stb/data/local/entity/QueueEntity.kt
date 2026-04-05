package com.ktv.stb.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "queue")
data class QueueEntity(
    @PrimaryKey val queueId: String,
    val songId: String,
    val queueStatus: String,
    val skipReason: String?,
    val errorMessage: String?,
    val playMode: String,
    val position: Int,
    val orderedByClientId: String?,
    val orderedByClientName: String?,
    val createdAt: Long,
)

package com.ktv.stb.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "separate_task")
data class SeparateTaskEntity(
    @PrimaryKey val taskId: String,
    val songId: String,
    val status: String,
    val progress: Int,
    val errorCode: String?,
    val errorMessage: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val completedAt: Long?,
)

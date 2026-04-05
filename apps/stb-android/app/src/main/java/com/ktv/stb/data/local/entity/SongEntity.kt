package com.ktv.stb.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "song")
data class SongEntity(
    @PrimaryKey val songId: String,
    val sourceType: String,
    val sourceId: String,
    val title: String,
    val artist: String?,
    val duration: Long,
    val coverUrl: String?,
    val coverLocalPath: String?,
    val videoPath: String?,
    val originalAudioPath: String?,
    val accompanimentPath: String?,
    val vocalPath: String?,
    val downloadStatus: String,
    val separateStatus: String,
    val resourceLevel: String,
    val fileSize: Long,
    val lastErrorCode: String?,
    val lastErrorMessage: String?,
    val createdAt: Long,
    val updatedAt: Long,
)

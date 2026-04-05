package com.ktv.stb.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.ktv.stb.data.local.entity.SongEntity

@Dao
interface SongDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(song: SongEntity)

    @Update
    suspend fun update(song: SongEntity)

    @Query("SELECT * FROM song WHERE songId = :songId LIMIT 1")
    suspend fun findBySongId(songId: String): SongEntity?

    @Query("SELECT * FROM song WHERE sourceId = :sourceId LIMIT 1")
    suspend fun findBySourceId(sourceId: String): SongEntity?

    @Query("SELECT * FROM song ORDER BY createdAt ASC")
    suspend fun listAll(): List<SongEntity>

    @Query("UPDATE song SET downloadStatus = :status, updatedAt = :updatedAt WHERE songId = :songId")
    suspend fun updateDownloadStatus(songId: String, status: String, updatedAt: Long)

    @Query("UPDATE song SET lastErrorCode = :errorCode, lastErrorMessage = :errorMessage, updatedAt = :updatedAt WHERE songId = :songId")
    suspend fun updateDownloadError(songId: String, errorCode: String?, errorMessage: String?, updatedAt: Long)

    @Query(
        "UPDATE song SET separateStatus = :status, accompanimentPath = :accompanimentPath, vocalPath = :vocalPath, " +
            "lastErrorCode = :errorCode, lastErrorMessage = :errorMessage, updatedAt = :updatedAt WHERE songId = :songId",
    )
    suspend fun updateSeparateState(
        songId: String,
        status: String,
        accompanimentPath: String?,
        vocalPath: String?,
        errorCode: String?,
        errorMessage: String?,
        updatedAt: Long,
    )

    @Query("DELETE FROM song WHERE songId = :songId")
    suspend fun deleteBySongId(songId: String)
}

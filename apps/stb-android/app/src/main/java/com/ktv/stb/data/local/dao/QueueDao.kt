package com.ktv.stb.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.ktv.stb.data.local.entity.QueueEntity

@Dao
interface QueueDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: QueueEntity)

    @Update
    suspend fun update(item: QueueEntity)

    @Query("SELECT * FROM queue WHERE queueStatus = 'playing' LIMIT 1")
    suspend fun findCurrentPlaying(): QueueEntity?

    @Query("SELECT * FROM queue ORDER BY position ASC")
    suspend fun listAll(): List<QueueEntity>

    @Query("SELECT COUNT(*) FROM queue q INNER JOIN song s ON q.songId = s.songId WHERE s.sourceId = :sourceId AND q.queueStatus IN ('waiting', 'playing')")
    suspend fun countBySourceId(sourceId: String): Int

    @Query("DELETE FROM queue WHERE queueId = :queueId")
    suspend fun deleteByQueueId(queueId: String)

    @Query("DELETE FROM queue WHERE songId = :songId")
    suspend fun deleteBySongId(songId: String)

    @Query("DELETE FROM queue")
    suspend fun clearAll()
}

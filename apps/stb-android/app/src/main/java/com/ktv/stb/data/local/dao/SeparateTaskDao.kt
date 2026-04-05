package com.ktv.stb.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.ktv.stb.data.local.entity.SeparateTaskEntity

@Dao
interface SeparateTaskDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(task: SeparateTaskEntity)

    @Update
    suspend fun update(task: SeparateTaskEntity)

    @Query("SELECT * FROM separate_task WHERE taskId = :taskId LIMIT 1")
    suspend fun findByTaskId(taskId: String): SeparateTaskEntity?
}

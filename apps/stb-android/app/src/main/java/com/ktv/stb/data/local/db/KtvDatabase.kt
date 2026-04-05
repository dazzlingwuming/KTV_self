package com.ktv.stb.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.ktv.stb.data.local.dao.DeviceConfigDao
import com.ktv.stb.data.local.dao.QueueDao
import com.ktv.stb.data.local.dao.SeparateTaskDao
import com.ktv.stb.data.local.dao.SongDao
import com.ktv.stb.data.local.entity.DeviceConfigEntity
import com.ktv.stb.data.local.entity.QueueEntity
import com.ktv.stb.data.local.entity.SeparateTaskEntity
import com.ktv.stb.data.local.entity.SongEntity

@Database(
    entities = [SongEntity::class, QueueEntity::class, SeparateTaskEntity::class, DeviceConfigEntity::class],
    version = 4,
    exportSchema = false,
)
abstract class KtvDatabase : RoomDatabase() {
    abstract fun songDao(): SongDao
    abstract fun queueDao(): QueueDao
    abstract fun separateTaskDao(): SeparateTaskDao
    abstract fun deviceConfigDao(): DeviceConfigDao
}

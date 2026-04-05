package com.ktv.stb.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ktv.stb.data.local.entity.DeviceConfigEntity

@Dao
interface DeviceConfigDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(config: DeviceConfigEntity)

    @Query("SELECT * FROM device_config WHERE deviceId = :deviceId LIMIT 1")
    suspend fun getByDeviceId(deviceId: String): DeviceConfigEntity?

    @Query("SELECT * FROM device_config LIMIT 1")
    suspend fun getCurrentConfig(): DeviceConfigEntity?

    @Query("SELECT * FROM device_config WHERE hostAddress != '' LIMIT 1")
    suspend fun getAnyConfiguredHost(): DeviceConfigEntity?
}

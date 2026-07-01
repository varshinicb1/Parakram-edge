package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface AuditLogDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: AuditLogEntity)

    @Query("SELECT * FROM audit_logs ORDER BY timestamp DESC")
    suspend fun getAll(): List<AuditLogEntity>

    @Query("SELECT * FROM audit_logs WHERE id = :id")
    suspend fun getById(id: String): AuditLogEntity?

    @Query("DELETE FROM audit_logs WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM audit_logs")
    suspend fun deleteAll()
}

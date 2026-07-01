package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "audit_logs")
data class AuditLogEntity(
    @PrimaryKey val id: String,
    val timestamp: Long,
    val method: String,
    val endpoint: String,
    val caller: String,
    val status: Int,
    val payload: String,
    val type: String
)

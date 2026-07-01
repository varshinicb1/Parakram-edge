package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "admin_profiles")
data class AdminProfileEntity(
    @PrimaryKey val uid: String,
    val displayName: String,
    val email: String,
    val organization: String,
    val developerRole: String,
    val apiKey: String,
    val maxDevices: Int,
    val securityLevel: String
)

package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "workflows")
data class WorkflowEntity(
    @PrimaryKey val id: String,
    val title: String,
    val description: String,
    val trigger: String,
    val action: String,
    val isActive: Boolean
)

package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface AdminProfileDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(profile: AdminProfileEntity)

    @Query("SELECT * FROM admin_profiles WHERE uid = :uid")
    suspend fun getByUid(uid: String): AdminProfileEntity?
}

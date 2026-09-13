package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface UsageSessionDao {
    @Query("SELECT * FROM usage_sessions ORDER BY unlockTime DESC")
    fun getAllSessions(): Flow<List<UsageSession>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: UsageSession): Long

    @Query("SELECT * FROM usage_sessions WHERE lockTime = 0")
    suspend fun getActiveSessions(): List<UsageSession>

    @Query(
        "UPDATE usage_sessions SET lockTime = :lockTime, durationSeconds = :durationSeconds WHERE id = :id AND lockTime = 0"
    )
    suspend fun closeSession(id: Int, lockTime: Long, durationSeconds: Long)

    @Query(
        "UPDATE usage_sessions SET warned = 1, warningCount = warningCount + 1 WHERE id = :id"
    )
    suspend fun markSessionWarned(id: Int)

    @Query("DELETE FROM usage_sessions")
    suspend fun deleteAllSessions()
}

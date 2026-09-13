package com.example.data

import kotlinx.coroutines.flow.Flow

class SessionRepository(private val dao: UsageSessionDao) {
    val allSessions: Flow<List<UsageSession>> = dao.getAllSessions()

    suspend fun insertSession(session: UsageSession): Long {
        return dao.insertSession(session)
    }

    suspend fun closeSession(id: Int, lockTime: Long, durationSeconds: Long) {
        dao.closeSession(id, lockTime, durationSeconds)
    }

    suspend fun markSessionWarned(id: Int) {
        dao.markSessionWarned(id)
    }

    suspend fun closeOrphanedSessions(endTime: Long) {
        dao.getActiveSessions().forEach { session ->
            val durationSeconds =
                ((endTime - session.unlockTime).coerceAtLeast(0L)) / 1_000L
            dao.closeSession(session.id, endTime, durationSeconds)
        }
    }

    suspend fun deleteAllSessions() {
        dao.deleteAllSessions()
    }
}

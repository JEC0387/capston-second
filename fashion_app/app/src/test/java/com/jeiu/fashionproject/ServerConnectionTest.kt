package com.jeiu.fashionproject

import com.jeiu.fashionproject.data.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ServerConnectionTest {
    private val healthy = ServerSettings.Check(true, true, "available", syncReady = true, authProtocol = 2)

    @Test fun startupConnectsAuthenticatesThenRetriesExistingQueue() = runBlocking {
        val calls = mutableListOf<String>()
        val result = ConnectionAttempt({ calls += "health"; healthy }, { calls += "auth"; true },
            { "ready" }, { true }, { calls += "retry-existing-queue" }).run()
        assertEquals(ConnectionState.READY, result)
        assertEquals(listOf("health", "auth", "retry-existing-queue"), calls)
    }

    @Test fun networkLossAndServerDatabaseFailureDoNotAuthenticateOrUpload() = runBlocking {
        for (health in listOf(ServerSettings.Check(false), ServerSettings.Check(true, false))) {
            var uploads = 0
            val result = ConnectionAttempt({ health }, { error("Must not authenticate") }, { "waiting" }, { true }, { uploads++ }).run()
            assertTrue(result in setOf(ConnectionState.OFFLINE, ConnectionState.DATABASE_UNAVAILABLE))
            assertEquals(0, uploads)
        }
    }

    @Test fun oldOrIncompleteServerSchemaCannotStartSync() = runBlocking {
        for (health in listOf(ServerSettings.Check(true, true), healthy.copy(syncReady = false))) {
            val result = ConnectionAttempt({ health }, { error("Unsafe server schema") }, { "waiting" }, { true }, { error("Must preserve queue") }).run()
            assertEquals(ConnectionState.UPDATE_REQUIRED, result)
        }
    }

    @Test fun restoredAndLegacyDataWaitForExplicitDecision() = runBlocking {
        val pending = ConnectionAttempt({ error("No network needed") }, { error("Must stay paused") },
            { "restore_pending" }, { true }, { error("Must preserve local data") }).run()
        assertEquals(ConnectionState.RESTORE_PENDING, pending)
        val legacy = ConnectionAttempt({ healthy }, { false }, { "legacy_decision_required" }, { true }, { error("Must not upload") }).run()
        assertEquals(ConnectionState.LEGACY_REQUIRED, legacy)
    }

    @Test fun revokedTokenAndDisabledBackupDoNotUploadOrRecreateAccount() = runBlocking {
        val revoked = ConnectionAttempt({ healthy }, { false }, { "reauth_required" }, { true }, { error("Must not upload") }).run()
        assertEquals(ConnectionState.AUTH_REQUIRED, revoked)
        val disabled = ConnectionAttempt({ error("Must not connect") }, { error("Must not register") }, { "waiting" }, { false }, { error("Must not upload") }).run()
        assertEquals(ConnectionState.BACKUP_DISABLED, disabled)
    }

    @Test fun retriesBackOffAndRemainBounded() {
        assertEquals(5_000L, ServerConnection.retryDelay(0))
        assertEquals(10_000L, ServerConnection.retryDelay(1))
        assertEquals(300_000L, ServerConnection.retryDelay(20))
    }
}

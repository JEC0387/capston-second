package com.jeiu.fashionproject

import android.app.Application
import android.content.Context
import com.jeiu.fashionproject.data.RestoreJournal
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class RestoreJournalTest {
    private val context get() = RuntimeEnvironment.getApplication() as Context
    @Before fun clear() {
        context.noBackupFilesDir.listFiles()?.forEach { it.delete() }
        listOf("wardrobe_store", "db_sync").forEach { context.getSharedPreferences(it, 0).edit().clear().commit() }
    }

    @Test fun failureDuringPreparationLeavesEveryLiveStoreAndQueueUntouched() {
        val wardrobe = context.getSharedPreferences("wardrobe_store", 0)
        val queue = context.getSharedPreferences("db_sync", 0)
        wardrobe.edit().putString("items", "old clothes").commit()
        queue.edit().putString("pending", "old queue").commit()
        assertTrue(runCatching { RestoreJournal.stage(context) {
            it.getSharedPreferences("wardrobe_store", 0).edit().putString("items", "new").commit()
            error("network/photo preparation failed")
        } }.isFailure)
        assertEquals("old clothes", wardrobe.getString("items", null))
        assertEquals("old queue", queue.getString("pending", null))
        assertFalse(RestoreJournal.recover(context))
    }

    @Test fun startupReplaysInterruptedInstallAndPreservesOriginalQueue() {
        val wardrobe = context.getSharedPreferences("wardrobe_store", 0)
        val queue = context.getSharedPreferences("db_sync", 0)
        wardrobe.edit().putString("items", "original-private").putLong("at", 4_000_000_000L).commit()
        queue.edit().putString("pending", "unsent-private-queue").commit()
        RestoreJournal.stage(context) { staged ->
            staged.getSharedPreferences("wardrobe_store", 0).edit().putString("items", "restored").commit()
            staged.getSharedPreferences("db_sync", 0).edit().putString("pending", "{}").commit()
        }
        // Simulate one file still at its old value after process death.
        wardrobe.edit().putString("items", "original-private").commit()
        assertTrue(RestoreJournal.recover(context))
        assertEquals("restored", wardrobe.getString("items", null))
        assertEquals(4_000_000_000L, wardrobe.getLong("at", 0))
        assertEquals("{}", queue.getString("pending", null))
        val original = context.noBackupFilesDir.listFiles()!!.single { it.name.startsWith("restore-original-") }.readText()
        assertTrue(original.contains("unsent-private-queue"))
        RestoreJournal.finish(context)
        assertFalse(RestoreJournal.recover(context))
    }
}

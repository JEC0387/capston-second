package com.jeiu.fashionproject

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import com.jeiu.fashionproject.data.ManualBodyMeasurements
import com.jeiu.fashionproject.data.ManualBodyStore
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.lang.reflect.Proxy

/** Synthetic write faults; no device files, photos, server or claims about physical disk durability. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class P34ManualBodyPersistenceTest {
    private val app get() = RuntimeEnvironment.getApplication()
    private val previous = ManualBodyMeasurements(170f, 40f, 90f, 80f, 95f, 75f, 50f, 1L)
    private val replacement = previous.copy(waistCircumferenceCm = 85f, enteredAt = 2L)
    private lateinit var faulty: P34FaultingMeasurementContext

    @Before fun seedConfirmedMeasurement() {
        assertEquals(Application::class.java, app.javaClass)
        ManualBodyStore.clear(app)
        assertTrue(ManualBodyStore.update(app, previous, true))
        faulty = P34FaultingMeasurementContext(app)
    }

    @After fun clearSyntheticPreferences() { ManualBodyStore.clear(app) }

    @Test fun failedCommitCannotPromoteUnconfirmedPreferencesOnReread() {
        faulty.failure = P34WriteFailure.MUTATE_THEN_FALSE
        assertFalse(ManualBodyStore.update(faulty, replacement, true))
        assertEquals(previous, ManualBodyStore.current.value)
        // SharedPreferences can publish its memory map before commit reports a disk failure.
        ManualBodyStore.load(faulty)
        assertEquals(previous, ManualBodyStore.current.value)
        assertTrue(ManualBodyStore.saved.value)
        assertNotNull(ManualBodyStore.issue.value)
    }

    @Test fun commitExceptionIsARecoverableSaveFailureAndRetryCanPersist() {
        faulty.failure = P34WriteFailure.THROW
        assertFalse(ManualBodyStore.update(faulty, replacement, true))
        assertEquals(previous, ManualBodyStore.current.value)
        assertNotNull(ManualBodyStore.issue.value)
        faulty.failure = P34WriteFailure.NONE
        assertTrue(ManualBodyStore.update(faulty, replacement, true))
        ManualBodyStore.load(faulty)
        assertEquals(replacement, ManualBodyStore.current.value)
        assertNull(ManualBodyStore.issue.value)
    }

    @Test fun failedRemovalKeepsLastConfirmedMeasurementUntilExplicitRetrySucceeds() {
        faulty.failure = P34WriteFailure.MUTATE_THEN_FALSE
        ManualBodyStore.clear(faulty)
        assertEquals(previous, ManualBodyStore.current.value)
        assertTrue(ManualBodyStore.saved.value)
        assertNotNull(ManualBodyStore.issue.value)
        ManualBodyStore.load(faulty)
        assertEquals(previous, ManualBodyStore.current.value)
        faulty.failure = P34WriteFailure.NONE
        ManualBodyStore.clear(faulty)
        assertNull(ManualBodyStore.current.value)
        assertFalse(ManualBodyStore.saved.value)
        assertNull(ManualBodyStore.issue.value)
        assertTrue(faulty.getSharedPreferences("manual_body_measurements", 0).all.isEmpty())
    }
}

internal enum class P34WriteFailure { NONE, MUTATE_THEN_FALSE, THROW }

/** Preserves Editor chaining, then injects the write result at its actual commit boundary. */
internal class P34FaultingMeasurementContext(base: Context) : ContextWrapper(base) {
    var failure = P34WriteFailure.NONE

    override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
        val preferences = super.getSharedPreferences(name, mode)
        if (name != "manual_body_measurements") return preferences
        return object : SharedPreferences by preferences {
            override fun edit(): SharedPreferences.Editor {
                val delegate = preferences.edit()
                return Proxy.newProxyInstance(
                    SharedPreferences.Editor::class.java.classLoader,
                    arrayOf(SharedPreferences.Editor::class.java)
                ) { proxy, method, arguments ->
                    if (method.name == "commit") {
                        when (failure) {
                            P34WriteFailure.NONE -> delegate.commit()
                            P34WriteFailure.MUTATE_THEN_FALSE -> { delegate.commit(); false }
                            P34WriteFailure.THROW -> throw IllegalStateException("synthetic persistence failure")
                        }
                    } else {
                        val result = method.invoke(delegate, *(arguments ?: emptyArray()))
                        if (result is SharedPreferences.Editor) proxy else result
                    }
                } as SharedPreferences.Editor
            }
        }
    }
}

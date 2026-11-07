package com.jeiu.fashionproject

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.jeiu.fashionproject.data.*
import org.json.JSONArray
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=Application::class)
class SizeFeedbackTest {
    private val context get()=ApplicationProvider.getApplicationContext<Application>()
    @Before @After fun clear() { context.getSharedPreferences("size_feedback",0).edit().clear().commit() }
    @Test fun explicitFeedbackStoresNoBodyPhotoUrlOrCredentialFields() {
        val entry=SizeFeedback("a".repeat(64),"TEST","M","REGULAR",SizeSatisfaction.FIT,1)
        assertTrue(SizeFeedbackStore.save(context,entry))
        assertEquals(entry,SizeFeedbackStore.load(context).single())
        val raw=context.getSharedPreferences("size_feedback",0).getString("entries",null)!!
        assertEquals(setOf("chart","brand","size","preference","rating","at"),JSONArray(raw).getJSONObject(0).keys().asSequence().toSet())
    }
    @Test fun corruptOriginalFeedbackIsNotSilentlyOverwritten() {
        val prefs=context.getSharedPreferences("size_feedback",0)
        prefs.edit().putString("entries","preserve-unreadable-original").commit()
        assertFalse(SizeFeedbackStore.save(context,SizeFeedback("a".repeat(64),"TEST","M","REGULAR",SizeSatisfaction.FIT,1)))
        assertEquals("preserve-unreadable-original",prefs.getString("entries",null))
    }
}

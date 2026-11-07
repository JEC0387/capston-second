package com.jeiu.fashionproject

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.jeiu.fashionproject.data.StylePreferenceStore
import com.jeiu.fashionproject.data.ProfileStore
import com.jeiu.fashionproject.recommend.PreferredStyles
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class P57StylePreferencePreservationTest {
    private val app get() = ApplicationProvider.getApplicationContext<Application>()
    private val prefs get() = app.getSharedPreferences("style_preference", Context.MODE_PRIVATE)
    @Before fun syntheticStore() { prefs.edit().clear().commit() }

    @Test fun legacyProjectionDoesNotRewriteOriginalOrUnknownScores() {
        prefs.edit().putInt("score_클래식", 7).putInt("score_빈티지", -2)
            .putInt("score_페미닌", 4).putInt("rated_count", 11).commit()
        val before = prefs.all.toMap()
        val read = StylePreferenceStore.readLocalScores(app)
        assertEquals(7, read["포멀"])
        assertEquals(-2, read["아메카지"])
        assertEquals(before, prefs.all)
    }

    @Test fun canonicalMarkerPreventsDoubleCountingRetainedLegacyKeys() {
        prefs.edit().putInt("score_클래식", 7).putInt("score_포멀", 9)
            .putBoolean("canonical_scores_v1", true).commit()
        assertEquals(9, StylePreferenceStore.readLocalScores(app)["포멀"])
        assertEquals(9, StylePreferenceStore.readLocalScores(app)["포멀"])
        assertEquals(7, prefs.getInt("score_클래식", 0))
    }

    @Test fun explicitRestoreRetainsSourceAliasesAndUnknownScoreFields() {
        StylePreferenceStore.writeForRestore(app, mapOf("클래식" to 4, "포멀" to 3, "페미닌" to 2), 5)
        assertEquals(7, StylePreferenceStore.readLocalScores(app)["포멀"])
        assertEquals(4, prefs.getInt("score_클래식", 0))
        assertEquals(2, prefs.getInt("score_페미닌", 0))
        assertEquals(5, prefs.getInt("rated_count", 0))
    }

    @Test fun profileReadAndRestoreKeepLegacyAndUnsupportedLabels() {
        val original = listOf("클래식", "페미닌", "댄디")
        ProfileStore.writeForRestore(app, original)
        val storage = app.getSharedPreferences("profile_store", Context.MODE_PRIVATE)
        val before = storage.all.toMap()
        ProfileStore.load(app)
        assertEquals(original, ProfileStore.preferredStyles(app))
        assertEquals(original, ProfileStore.styles.value)
        assertEquals(before, storage.all)
        assertEquals(listOf("페미닌", "댄디"), PreferredStyles.toggleSelection(original, "포멀"))
    }
}

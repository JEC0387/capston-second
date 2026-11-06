package com.jeiu.fashionproject

import android.app.Application
import android.net.Uri
import com.jeiu.fashionproject.data.Gender
import com.jeiu.fashionproject.data.WardrobeItem
import com.jeiu.fashionproject.recommend.BodyStyleSuggestions
import com.jeiu.fashionproject.ui.viewmodel.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class P19BodyStyleSuggestionsTest {
    private fun result(type: BodyType): BodyAnalysisResult {
        val widths = when (type) {
            BodyType.INVERTED_TRIANGLE -> listOf(46f, 34f, 40f)
            BodyType.TRIANGLE -> listOf(35f, 32f, 42f)
            BodyType.HOURGLASS -> listOf(40f, 28f, 40f)
            BodyType.RECTANGLE -> listOf(40f, 36f, 40f)
        }
        val (s, w, h) = widths
        val max = widths.max()
        val cm = BodyMeasurements(170f, s, w, h, 72f, 56f, .5f, 340f, 1L)
        return BodyAnalysisResult(type.label, s/max, w/max, h/max, 72f/170f,
            emptyList(), emptyList(), 90, s/h, w/h, cm)
    }
    private fun state(type: BodyType) = BodyAnalysisUiState.Success(result(type), Uri.EMPTY)
    private fun item(name: String, detail: String? = null, owned: Boolean = true, uri: String = "content://synthetic/item") =
        WardrobeItem(id = name, name = name, category = "상의", colorName = "미확인", colorValue = 0,
            imageUri = uri, detailType = detail, isOwned = owned)

    @Test fun fourValidatedTypesProduceDistinctTipsWithoutReusingHourglassCards() {
        val suggestions = BodyType.entries.map { type ->
            BodyStyleSuggestions.create(state(type), null, emptyList()).also {
                assertTrue(it.personalized)
                assertEquals(type, it.bodyType)
            }
        }
        assertEquals(4, suggestions.map { it.tips }.distinct().size)
        assertEquals("여유 있는 하의", suggestions[0].tips[1].title)
        assertEquals("상의 포인트", suggestions[1].tips[1].title)
    }

    @Test fun nonePartialAnalyzingAndErrorNeverRetainPreviousType() {
        assertTrue(BodyStyleSuggestions.create(state(BodyType.HOURGLASS), null, emptyList()).personalized)
        for (input in listOf(BodyAnalysisUiState.Idle,
            BodyAnalysisUiState.Partial("경계값 판정 보류", Uri.EMPTY, result(BodyType.HOURGLASS).measurements),
            BodyAnalysisUiState.Analyzing(2), BodyAnalysisUiState.Error("전신 미확인"))) {
            val suggestion = BodyStyleSuggestions.create(input, null, listOf(item("벨트")))
            assertFalse(suggestion.personalized)
            assertNull(suggestion.bodyType)
            assertTrue(suggestion.wardrobeReasons.isEmpty())
            assertEquals("편안한 기본 조합", suggestion.tips.first().title)
        }
        assertTrue(BodyStyleSuggestions.create(BodyAnalysisUiState.Partial("경계값 판정 보류", Uri.EMPTY),
            null, emptyList()).explanation.contains("경계값 판정 보류"))
    }

    @Test fun unsupportedOrInconsistentSuccessCannotBypassExistingQualityGuard() {
        val valid = result(BodyType.HOURGLASS)
        for (invalid in listOf(valid.copy(bodyTypeKo = "균형형"), valid.copy(measurements = null),
            valid.copy(confidence = 101), valid.copy(waistRatio = Float.NaN),
            valid.copy(bodyTypeKo = BodyType.TRIANGLE.label),
            valid.copy(measurements = valid.measurements!!.copy(cmPerPixel = .2f)))) {
            assertFalse(BodyStyleSuggestions.create(BodyAnalysisUiState.Success(invalid, Uri.EMPTY),
                null, emptyList()).personalized)
        }
    }

    @Test fun unselectedGenderRemainsNeutralAndDoesNotExcludeClothes() {
        val input = state(BodyType.HOURGLASS)
        val neutral = BodyStyleSuggestions.create(input, null, emptyList())
        assertTrue(neutral.genderNotice.contains("성별 미선택"))
        assertFalse(neutral.genderNotice.contains("남성"))
        assertFalse(neutral.genderNotice.contains("여성"))
        assertEquals(neutral.tips, BodyStyleSuggestions.create(input, Gender.MALE, emptyList()).tips)
        assertEquals(neutral.tips, BodyStyleSuggestions.create(input, Gender.FEMALE, emptyList()).tips)
    }

    @Test fun emptyOrUnknownWardrobeDoesNotInventPersonalization() {
        val input = state(BodyType.TRIANGLE)
        assertTrue(BodyStyleSuggestions.create(input, null, emptyList()).wardrobeReasons.isEmpty())
        assertTrue(BodyStyleSuggestions.create(input, null, listOf(item("이름 없는 옷"))).wardrobeReasons.isEmpty())
    }

    @Test fun wardrobeReasonsUseExplicitLabelsAndExcludeSamplesAndUnownedItems() {
        val items = listOf(item("내 셔츠"), item("임시 관심 셔츠", owned = false),
            item("샘플 셔츠", uri = "android.resource://test/1"), item("파랑 옷"))
        val suggestion = BodyStyleSuggestions.create(state(BodyType.TRIANGLE), null, items)
        assertEquals(1, suggestion.wardrobeReasons.size)
        assertTrue(suggestion.wardrobeReasons.single().contains("내 셔츠"))
        assertTrue(suggestion.wardrobeReasons.single().contains("실제 핏은 확인하지 않았어요"))
    }
}

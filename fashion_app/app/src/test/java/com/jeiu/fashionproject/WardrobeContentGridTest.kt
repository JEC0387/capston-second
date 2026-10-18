package com.jeiu.fashionproject

import android.app.Application
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.jeiu.fashionproject.ui.components.WardrobeContentGrid
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.junit.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Exercises the production scroll container with isolated fake cards, not real wardrobe storage. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class WardrobeContentGridTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var gridState: LazyListState
    private lateinit var scope: CoroutineScope
    private var bannerClicks = 0

    @Composable private fun Content(empty: Boolean = false) {
        gridState = rememberLazyListState()
        scope = rememberCoroutineScope()
        var onlyEven by rememberSaveable { mutableStateOf(false) }
        val data = if (empty) emptyList() else (0..59).filter { !onlyEven || it % 2 == 0 }
        MaterialTheme {
            Box(Modifier.size(360.dp, 620.dp)) {
                WardrobeContentGrid(
                    entries = data, key = { "card-$it" }, state = gridState, modifier = Modifier.fillMaxSize(),
                    banners = {
                        repeat(3) { index ->
                            Button(onClick = { bannerClicks++ }, modifier = Modifier.fillMaxWidth().height(92.dp).testTag("banner-$index")) { Text("배너 $index") }
                        }
                    },
                    filters = {
                        Row { FilterChip(selected = onlyEven, onClick = { onlyEven = !onlyEven }, label = { Text("필터") }, modifier = Modifier.testTag("filterToggle")) }
                        Text("항목: ${data.size}", Modifier.testTag("filterCount"))
                    },
                    empty = { Text("목록 없음", Modifier.testTag("emptyWardrobe")) }
                ) { id -> Card(Modifier.fillMaxWidth().height(160.dp).testTag("card-$id")) { Text("옷 $id") } }
            }
        }
    }
    @Test fun initialBannersAndClicksArePreserved() {
        compose.setContent { Content() }
        for (i in 0..2) compose.onNodeWithTag("banner-$i").assertIsDisplayed().performClick()
        compose.onNodeWithTag("wardrobeFilters").assertIsDisplayed()
        compose.runOnIdle { Assert.assertEquals(3, bannerClicks) }
    }
    @Test fun bannersLeaveLayoutFiltersStayAndReverseInMiddleDoesNotExpand() {
        compose.setContent { Content() }
        compose.onNodeWithTag("wardrobeContentGrid").performScrollToKey("card-30")
        compose.onNodeWithTag("banner-0").assertIsNotDisplayed()
        compose.onNodeWithTag("wardrobeFilters").assertIsDisplayed()
        compose.onNodeWithTag("card-30").assertIsDisplayed()
        compose.runOnIdle { scope.launch { gridState.scrollBy(-70f) } }
        compose.waitForIdle()
        compose.onNodeWithTag("banner-0").assertIsNotDisplayed()
        compose.onNodeWithTag("wardrobeFilters").assertIsDisplayed()
        compose.runOnIdle { scope.launch { gridState.animateScrollToItem(0) } }
        compose.waitForIdle()
        compose.onNodeWithTag("banner-0").assertIsDisplayed()
    }
    @Test fun pinnedFilterWorksAndScrollPositionSurvivesRestoration() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent { Content() }
        compose.onNodeWithTag("wardrobeContentGrid").performScrollToKey("card-30")
        compose.onNodeWithTag("filterToggle").performClick()
        compose.onNodeWithTag("filterCount").assertTextEquals("항목: 30")
        var index = 0; var offset = 0
        compose.runOnIdle { index = gridState.firstVisibleItemIndex; offset = gridState.firstVisibleItemScrollOffset }
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("filterCount").assertTextEquals("항목: 30")
        compose.runOnIdle { Assert.assertEquals(index, gridState.firstVisibleItemIndex); Assert.assertEquals(offset, gridState.firstVisibleItemScrollOffset) }
        compose.onNodeWithTag("banner-0").assertIsNotDisplayed()
    }
    @Test fun emptyListKeepsBannersAndUsableFilters() {
        compose.setContent { Content(empty = true) }
        compose.onNodeWithTag("banner-0").assertIsDisplayed()
        compose.onNodeWithTag("filterToggle").performScrollTo().performClick()
        compose.onNodeWithTag("filterCount").assertTextEquals("항목: 0")
        compose.onNodeWithTag("emptyWardrobe").performScrollTo().assertIsDisplayed()
    }
}

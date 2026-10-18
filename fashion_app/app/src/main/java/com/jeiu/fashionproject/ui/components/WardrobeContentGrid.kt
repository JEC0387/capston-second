package com.jeiu.fashionproject.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.jeiu.fashionproject.ui.theme.Cream

/** One scroll owner and two-card rows. Works with the project's existing Compose version. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun <T> WardrobeContentGrid(
    entries: List<T>,
    key: (T) -> Any,
    banners: @Composable () -> Unit,
    filters: @Composable () -> Unit,
    empty: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
    card: @Composable LazyItemScope.(T) -> Unit
) {
    LazyColumn(
        state = state,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier.testTag("wardrobeContentGrid")
    ) {
        item(key = "wardrobe:banners", contentType = "banners") {
            Column(Modifier.fillMaxWidth().testTag("wardrobeBanners")) { banners() }
        }
        stickyHeader(key = "wardrobe:filters", contentType = "filters") {
            Column(Modifier.fillMaxWidth().background(Cream).testTag("wardrobeFilters")) { filters() }
        }
        if (entries.isEmpty()) item(key = "wardrobe:empty") { empty() }
        else items(entries.chunked(2), key = { key(it.first()) }, contentType = { "garment-row" }) { row ->
            val itemScope = this
            Row(Modifier.fillMaxWidth().animateItem(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                for (entry in row) androidx.compose.runtime.key(key(entry)) {
                    Box(Modifier.weight(1f)) { itemScope.card(entry) }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

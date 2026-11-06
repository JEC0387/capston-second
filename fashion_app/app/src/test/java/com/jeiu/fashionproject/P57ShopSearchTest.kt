package com.jeiu.fashionproject

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.jeiu.fashionproject.data.FashionQuery
import com.jeiu.fashionproject.data.ShopImageResult
import com.jeiu.fashionproject.ui.viewmodel.ShopUiState
import com.jeiu.fashionproject.ui.viewmodel.ShopViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Synthetic responses only: no provider credentials or network requests. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@OptIn(ExperimentalCoroutinesApi::class)
class P57ShopSearchTest {
    private val scheduler = TestCoroutineScheduler()
    private val models = mutableListOf<ShopViewModel>()
    @Before fun setup() { Dispatchers.setMain(StandardTestDispatcher(scheduler)) }
    @After fun cleanup() {
        models.forEach { it.viewModelScope.cancel() }
        scheduler.runCurrent()
        Dispatchers.resetMain()
    }
    private fun model(search: suspend (String, Int) -> List<ShopImageResult>) =
        ShopViewModel(search).also { models += it }
    private fun result(title: String) = ShopImageResult(title, "https://synthetic.invalid/image", "https://synthetic.invalid/thumb")

    @Test fun clothingSearchPreservesUnselectedGenderAndRejectsUnrelatedQuery() {
        val plan = FashionQuery.productPlan("청바지", null)
        assertNotNull(plan.query)
        assertFalse(plan.query!!.contains("남성"))
        assertFalse(plan.query!!.contains("여성"))
        var calls = 0
        val vm = model { _, _ -> calls++; emptyList() }
        vm.searchProducts("자동차", null)
        scheduler.runCurrent()
        assertTrue(vm.productState.value is ShopUiState.NotFashion)
        assertEquals(0, calls)
    }

    @Test fun duplicateActiveSearchOnlyCallsOnce() {
        val pending = CompletableDeferred<List<ShopImageResult>>()
        var calls = 0
        val vm = model { _, _ -> calls++; pending.await() }
        vm.searchProducts("셔츠", null)
        scheduler.runCurrent()
        vm.searchProducts("셔츠", null)
        scheduler.runCurrent()
        assertEquals(1, calls)
        pending.complete(listOf(result("셔츠 상품")))
        scheduler.runCurrent()
        assertEquals("셔츠 상품", (vm.productState.value as ShopUiState.Success).results.single().title)
    }

    @Test fun supersededResponseCannotReplaceLatestProductResults() {
        val old = CompletableDeferred<List<ShopImageResult>>()
        val vm = model { query, _ ->
            if (query.contains("셔츠")) withContext(NonCancellable) { old.await() }
            else listOf(result("청바지 상품"))
        }
        vm.searchProducts("셔츠", null)
        scheduler.runCurrent()
        vm.searchProducts("청바지", null)
        scheduler.runCurrent()
        old.complete(listOf(result("셔츠 상품")))
        scheduler.runCurrent()
        assertEquals("청바지 상품", (vm.productState.value as ShopUiState.Success).results.single().title)
    }

    @Test fun blankProductQueryCancelsSearchAndKeepsCatalogOnly() {
        val pending = CompletableDeferred<List<ShopImageResult>>()
        val vm = model { _, _ -> pending.await() }
        vm.searchProducts("셔츠", null)
        scheduler.runCurrent()
        vm.searchProducts("", null)
        scheduler.runCurrent()
        assertSame(ShopUiState.Idle, vm.productState.value)
        pending.complete(listOf(result("셔츠 상품")))
        scheduler.runCurrent()
        assertSame(ShopUiState.Idle, vm.productState.value)
    }

    @Test fun failureDoesNotExposeProviderErrorAndExplicitRetryIsAllowed() {
        var calls = 0
        val vm = model { _, _ ->
            calls++
            if (calls == 1) error("synthetic private provider detail")
            listOf(result("미니멀 코디"))
        }
        vm.search("미니멀 코디", null)
        scheduler.runCurrent()
        assertFalse((vm.state.value as ShopUiState.Error).message.contains("private"))
        vm.search("미니멀 코디", null)
        scheduler.runCurrent()
        assertEquals(2, calls)
        assertTrue(vm.state.value is ShopUiState.Success)
    }
}

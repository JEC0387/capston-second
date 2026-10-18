package com.jeiu.fashionproject

import com.jeiu.fashionproject.data.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class TryOnPlanTest {
    private fun garment(category: String, id: String = category) = TryOnGarment(id, category, category, "file:///garment.jpg")
    @Test fun supportsOnlyDeclaredGarmentCategories() {
        assertEquals("tops", TryOnPlan.categoryFor("상의"))
        assertEquals("tops", TryOnPlan.categoryFor("아우터"))
        assertEquals("bottoms", TryOnPlan.categoryFor("하의"))
        assertEquals("one-pieces", TryOnPlan.categoryFor("원피스"))
        assertNull(TryOnPlan.categoryFor("신발")); assertNull(TryOnPlan.categoryFor("액세서리"))
    }
    @Test fun topThenBottomAreTwoGenerationsRegardlessOfSelectionOrder() {
        val plan = TryOnPlan.build(listOf(garment("하의"), garment("상의")))
        assertNull(plan.issue); assertEquals(2, plan.requestCount)
        assertEquals(listOf("상의", "하의"), plan.garments.map { it.category })
    }
    @Test fun unsupportedShoesAreExplicitlyExcluded() {
        val plan = TryOnPlan.build(listOf(garment("상의"), garment("신발")))
        assertEquals(1, plan.requestCount); assertEquals("신발", plan.unsupported.single().category)
    }
    @Test fun onlyUnsupportedSelectionCannotPretendToGenerate() {
        assertNotNull(TryOnPlan.build(listOf(garment("신발"))).issue)
        assertNotNull(TryOnPlan.build(emptyList()).issue)
    }
    @Test fun conflictingUpperLayersRequireUserToChoose() {
        assertNotNull(TryOnPlan.build(listOf(garment("상의"), garment("아우터"))).issue)
    }
    @Test fun dressIsSupportedAloneAndConflictingSelectionsAreRejected() {
        assertNull(TryOnPlan.build(listOf(garment("원피스"))).issue)
        assertNotNull(TryOnPlan.build(listOf(garment("원피스"), garment("하의"))).issue)
    }
    @Test fun missingGarmentImageCannotGenerate() {
        assertNotNull(TryOnPlan.build(listOf(garment("상의").copy(image = ""))).issue)
    }
    @Test fun sequentialRequestUsesFirstResultAsSecondInput() = runBlocking {
        val calls = mutableListOf<Pair<String, String>>()
        val progress = mutableListOf<Pair<Int, Int>>()
        val result = TryOnBatch.run(TryOnPlan.build(listOf(garment("하의"), garment("상의"))), "original", { model, g ->
            calls += model to g.category
            "https://example.com/${calls.size}.jpg"
        }, { index, total -> progress += index to total })
        assertEquals(listOf("original" to "상의", "https://example.com/1.jpg" to "하의"), calls)
        assertEquals(listOf(1 to 2, 2 to 2), progress)
        assertEquals("https://example.com/2.jpg", result)
    }
    @Test fun failureStopsWithoutAutomaticPaidRetry() = runBlocking {
        var calls = 0
        try {
            TryOnBatch.run(TryOnPlan.build(listOf(garment("상의"), garment("하의"))), "original", { _, _ ->
                calls++; throw TryOnFailure("failed")
            })
            fail("Failure must not become a generated result")
        } catch (_: TryOnFailure) { assertEquals(1, calls) }
    }
    @Test fun secondFailureDoesNotPublishPartialSuccess() = runBlocking {
        var calls = 0
        try {
            TryOnBatch.run(TryOnPlan.build(listOf(garment("상의"), garment("하의"))), "original", { _, _ ->
                calls++; if (calls == 2) throw TryOnFailure("second failed") else "https://example.com/first.jpg"
            }); fail("Partial success must not be returned")
        } catch (_: TryOnFailure) { assertEquals(2, calls) }
    }
    @Test fun invalidPlanDoesNotCallProvider() = runBlocking {
        var calls = 0
        try {
            TryOnBatch.run(TryOnPlan.build(listOf(garment("신발"))), "original", { _, _ -> calls++; "https://example.com/fake.jpg" })
            fail("Empty generation must not return original as success")
        } catch (_: TryOnFailure) { assertEquals(0, calls) }
    }
    @Test fun rejectsUnsafeOrEmptyResultAddresses() {
        for (url in listOf("", "http://example.com/x", "file:///x", "https://key@example.com/x", "data:image/jpeg;base64,x"))
            assertFalse(url, TryOnBatch.isResultUrl(url))
        assertTrue(TryOnBatch.isResultUrl("https://example.com/result.jpg"))
    }
    @Test fun providerEmptyResultDoesNotBecomeSuccess() = runBlocking {
        try {
            TryOnBatch.run(TryOnPlan.build(listOf(garment("상의"))), "original", { _, _ -> "" })
            fail("Empty output must fail")
        } catch (_: TryOnFailure) { }
    }
    @Test fun cancellationStopsBeforeSecondPaidRequest() = runBlocking {
        var calls = 0
        val child = launch {
            TryOnBatch.run(TryOnPlan.build(listOf(garment("상의"), garment("하의"))), "original", { _, _ ->
                calls++; currentCoroutineContext().cancel(); "https://example.com/first.jpg"
            })
        }
        child.join(); assertEquals(1, calls); assertTrue(child.isCancelled)
    }
}

package com.jeiu.fashionproject

import android.app.Application
import com.jeiu.fashionproject.data.BackupRestore
import com.jeiu.fashionproject.data.OutfitPlan
import com.jeiu.fashionproject.data.OutfitPlanStore
import com.jeiu.fashionproject.data.SavedOutfit
import com.jeiu.fashionproject.data.SavedOutfitStore
import com.jeiu.fashionproject.data.WardrobeItem
import com.jeiu.fashionproject.data.WearLog
import com.jeiu.fashionproject.recommend.CostPerWear
import com.jeiu.fashionproject.ui.screens.timesWorn
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 저장한 코디 · 코디 일정 · 착용당 비용 (서버 test_outfit_book_api.py 와 같은 규칙) */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class OutfitBookAndCostTest {

    private fun item(id: String, price: Int? = null, owned: Boolean = true) =
        WardrobeItem(id = id, name = id, category = "상의", colorName = "블랙", colorValue = 0, imageUri = "", price = price, isOwned = owned)

    // ── 저장한 코디 ──────────────────────────────

    @Test
    fun savedOutfit_idIgnoresOrderAndDuplicates() {
        assertEquals("a+b", SavedOutfit.idOf(listOf("b", "a", "b")))
    }

    @Test
    fun savedOutfit_toggleAddsToFrontAndRemovesSameCombination() {
        val first = SavedOutfit("a+b", listOf("a", "b"), "미니멀", null, "", 1)
        val second = SavedOutfit("c", listOf("c"), "캐주얼", null, "", 2)
        val list = SavedOutfitStore.toggled(SavedOutfitStore.toggled(emptyList(), first), second)
        assertEquals(listOf("c", "a+b"), list.map { it.id })
        assertEquals(listOf("c"), SavedOutfitStore.toggled(list, first.copy(itemIds = listOf("b", "a"))).map { it.id })
    }

    @Test
    fun savedOutfit_encodeDecode() {
        val list = listOf(SavedOutfit("a+b", listOf("a", "b"), "미니멀", "톤온톤", "회식", 5),
            SavedOutfit("c", listOf("c"), "캐주얼", null, "", 6))
        assertEquals(list, SavedOutfitStore.decode(SavedOutfitStore.encode(list)))
    }

    @Test
    fun timesWorn_countsDaysWithAllItems() {
        val logs = listOf(WearLog("2026-10-01", listOf("a", "b", "c")), WearLog("2026-10-05", listOf("a")),
            WearLog("2026-10-09", listOf("b", "a")))
        assertEquals(2 to "2026-10-09", timesWorn(listOf("a", "b"), logs))
        assertEquals(0 to null, timesWorn(listOf("z"), logs))
    }

    // ── 코디 일정 ────────────────────────────────

    @Test
    fun plans_upsertReplacesSameDayAndSorts() {
        var plans = OutfitPlanStore.upserted(emptyList(), OutfitPlan("2026-10-20", listOf("a"), "", 1))
        plans = OutfitPlanStore.upserted(plans, OutfitPlan("2026-10-19", listOf("b"), "", 2))
        plans = OutfitPlanStore.upserted(plans, OutfitPlan("2026-10-20", listOf("c"), "", 3))
        assertEquals(listOf("2026-10-19" to listOf("b"), "2026-10-20" to listOf("c")), plans.map { it.date to it.itemIds })
    }

    @Test
    fun plans_pruneOldAndFollowed() {
        val plans = listOf(OutfitPlan("2026-08-01", listOf("a"), "", 0), OutfitPlan("2026-10-18", listOf("a", "b"), "", 0))
        assertEquals(listOf("2026-10-18"), OutfitPlanStore.pruned(plans, "2026-10-18").map { it.date })
        val plan = plans[1]
        assertEquals(true, OutfitPlanStore.followed(plan, WearLog("2026-10-18", listOf("b", "a", "c"))))
        assertEquals(false, OutfitPlanStore.followed(plan, WearLog("2026-10-18", listOf("a"))))
        assertNull(OutfitPlanStore.followed(plan, null))
        assertEquals(plans, OutfitPlanStore.decode(OutfitPlanStore.encode(plans)))
    }

    // ── 착용당 비용 ──────────────────────────────

    @Test
    fun costPerWear_report() {
        val items = listOf(item("cheap-often", 20000), item("pricey-never", 200000), item("mid-once", 60000),
            item("no-price"), item("interest", 99000, owned = false))
        val logs = (1..10).map { WearLog("2026-10-%02d".format(it), listOf("cheap-often")) } +
            WearLog("2026-10-11", listOf("mid-once"))
        val r = CostPerWear.report(items, logs)
        assertEquals(3, r.pricedCount)
        assertEquals(1, r.unpricedCount)                       // 관심 옷은 세지 않음
        assertEquals(280000L, r.totalValue)
        assertEquals((20000 + 60000) / 11, r.averagePerWear)
        assertEquals(listOf("cheap-often", "mid-once"), r.bestValue.map { it.item.id })
        assertEquals(2000, r.bestValue.first().perWear)
        assertEquals(listOf("pricey-never", "mid-once"), r.leastWorn.map { it.item.id })
        assertFalse(r.isEmpty)
        assertTrue(CostPerWear.report(listOf(item("x")), emptyList()).isEmpty)
    }

    @Test
    fun costPerWear_parsePriceAndFormat() {
        assertEquals(39000, CostPerWear.parsePrice("39,000원"))
        assertNull(CostPerWear.parsePrice(""))
        assertNull(CostPerWear.parsePrice("0"))
        assertNull(CostPerWear.parsePrice("1234567890"))
        assertEquals("39,000원", CostPerWear.won(39000))
    }

    // ── 복원 응답 읽기 ────────────────────────────

    @Test
    fun restore_parsesOutfitBookAndPrice() {
        val saved = BackupRestore.parseSavedOutfits("""{"totalCount":2,"outfits":[
            {"id":"a+b","itemIds":["a","b"],"style":"미니멀","pattern":null,"note":"","savedAt":1},
            {"id":"c","itemIds":["c"],"style":null,"pattern":"톤온톤","note":"메모","savedAt":5}]}""")
        assertEquals(listOf("c", "a+b"), saved.map { it.id })          // 최근 저장 순
        assertEquals("", saved[0].style)
        val plans = BackupRestore.parsePlans("""{"totalCount":1,"plans":[{"date":"2026-10-20","itemIds":["a"],"note":"","createdAt":null}]}""")
        assertEquals(OutfitPlan("2026-10-20", listOf("a"), "", 0), plans.single())
        val clothes = BackupRestore.parseWardrobe("""{"totalCount":1,"categories":[],"items":[
            {"id":1,"clientId":"u1","name":"셔츠","category":"상의","color":"#000000","imageUrl":"","price":39000,"brand":"브랜드"}]}""")
        assertEquals(39000, clothes.single().item.price)
        assertEquals("브랜드", clothes.single().item.brand)
    }
}

package com.jeiu.fashionproject

import android.app.Application
import com.jeiu.fashionproject.data.BackupRestore
import com.jeiu.fashionproject.data.RecommendationEntry
import com.jeiu.fashionproject.data.RecommendationLog
import com.jeiu.fashionproject.data.TasteEvent
import com.jeiu.fashionproject.data.WardrobeItem
import com.jeiu.fashionproject.data.WearDates
import com.jeiu.fashionproject.data.WearLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 추천 기록 · 추천 성과 계산, 백업 코드 복원용 응답 읽기 (서버 테스트 test_backup_api.py 와 같은 규칙) */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class BackupAndRecommendationLogTest {

    private fun rec(date: String, context: String, vararg ids: String) =
        RecommendationEntry(date, context, "OWNED", 0, ids.sorted(), 1L)

    private fun outfitRating(at: Long, liked: Boolean, vararg ids: String) =
        TasteEvent(at, TasteEvent.SOURCE_OUTFIT, liked, emptyList(), emptyList(), itemIds = ids.toList())

    @Test
    fun summarize_matchesServerRules() {
        val entries = listOf(
            rec("2026-10-17", "home", "a", "b", "c"),                 // 👍 + 다음 날 그대로 입음
            rec("2026-10-17", "today", "d", "e"),                     // 👎, 입은 건 기간 밖
            rec("2026-10-17", "situation:INTERVIEW", "a", "x", "y"),  // 1/3 → 부분 아님
            rec("2026-10-17", "situation:DATE", "a", "b", "z"),       // 2/3 → 부분
        )
        val events = listOf(
            outfitRating(1, false, "c", "b", "a"),
            outfitRating(2, true, "a", "b", "c"),     // 마지막 평가가 👍
            outfitRating(3, false, "d", "e"),
            TasteEvent(4, TasteEvent.SOURCE_WORN, true, emptyList(), emptyList(), itemIds = listOf("a", "b", "c"), date = "2026-10-18"),
        )
        val logs = listOf(WearLog("2026-10-18", listOf("a", "b", "c")), WearLog("2026-10-25", listOf("d", "e")))
        val s = RecommendationLog.summarize(entries, events, logs)
        assertEquals(4, s.shown)
        assertEquals(1, s.liked)
        assertEquals(1, s.disliked)
        assertEquals(1, s.worn)
        assertEquals(2, s.wornPartial)
        assertEquals(25, s.percent(s.liked))
        assertEquals(50, s.percent(s.wornPartial))
    }

    @Test
    fun summarize_emptyIsZero() {
        val s = RecommendationLog.summarize(emptyList(), emptyList(), emptyList())
        assertEquals(0, s.shown)
        assertEquals(0, s.percent(s.liked))
    }

    @Test
    fun entries_encodeDecodeRoundTrip_andKeyIgnoresOrder() {
        val list = listOf(rec("2026-10-18", "forecast", "b", "a"), rec("2026-10-19", "situation:TRAVEL", "c"))
        assertEquals(list, RecommendationLog.decode(RecommendationLog.encode(list)))
        assertEquals(rec("2026-10-18", "home", "a", "b").key, rec("2026-10-18", "home", "b", "a").key)
    }

    @Test
    fun addDays_crossesMonth() {
        assertEquals("2026-11-01", WearDates.addDays("2026-10-31", 1))
        assertEquals("2026-10-30", WearDates.addDays("2026-11-01", -2))
    }

    // ── 백업 코드 ─────────────────────────────

    @Test
    fun normalize_code() {
        assertEquals("3f2a9c1e-1111-2222-3333-444455556666",
            BackupRestore.normalize(" 3F2A9C1E-1111-2222-3333-444455556666 \n"))
        assertNull(BackupRestore.normalize("short"))
        assertNull(BackupRestore.normalize("bad/code/with/slash"))
    }

    @Test
    fun colorValue_fromHex() {
        assertEquals(0xFF1F2A44L, BackupRestore.colorValue("#1F2A44"))
        assertEquals(0xFFEDE3D5L, BackupRestore.colorValue("nope"))
        assertEquals(0xFFEDE3D5L, BackupRestore.colorValue(null))
    }

    @Test
    fun parseWardrobe_usesSamplePhotos_skipsServerOnlyItems_sortsByAddedAt() {
        val body = """
            {"totalCount":3,"categories":[],"items":[
              {"id":1,"clientId":"user-1","name":"셔츠","category":"상의","color":"#1F2A44","imageUrl":"file:///old/phone/a.png",
               "colorName":"네이비","isCutout":true,"addedAt":200,"favoritedAt":300,"hasPhoto":true,"cutoutUri":"file:///old/phone/a.png"},
              {"id":2,"clientId":"sample_01","name":"샘플","category":"상의","color":"#FFFFFF","imageUrl":"android.resource://x/old",
               "addedAt":100,"hasPhoto":true},
              {"id":3,"clientId":"server-abc","name":"Swagger 옷","category":"상의","color":"#000000","imageUrl":"","hasPhoto":false}
            ]}
        """.trimIndent()
        val list = BackupRestore.parseWardrobe(body)
        assertEquals(listOf("sample_01", "user-1"), list.map { it.item.id })
        val sample = list[0]
        assertFalse(sample.hasPhoto)                                       // 샘플은 사진을 받지 않음
        assertTrue(sample.item.imageUri.startsWith("android.resource://com.jeiu.fashionproject/"))
        val user = list[1]
        assertTrue(user.hasPhoto)
        assertEquals(0xFF1F2A44L, user.item.colorValue)
        assertEquals(300L, user.item.favoritedAt)
    }

    @Test
    fun withPhoto_replacesOldPhoneFiles() {
        val item = WardrobeItem(id = "u", name = "셔츠", category = "상의", colorName = "네이비", colorValue = 0, isCutout = true,
            imageUri = "file:///old/a.png", originalUri = "file:///old/o.jpg", cutoutUri = "file:///old/a.png",
            garmentUri = "file:///old/g.png")
        val restored = BackupRestore.withPhoto(item, "file:///new/restored_u.webp")
        assertEquals("file:///new/restored_u.webp", restored.imageUri)
        assertEquals("file:///new/restored_u.webp", restored.cutoutUri)
        assertNull(restored.originalUri)
        assertNull(restored.garmentUri)
        val missing = BackupRestore.withPhoto(item, null)
        assertEquals("", missing.imageUri)                                 // 예전 폰 파일 주소는 이 폰에 없음
        assertNull(missing.originalUri)
        val web = BackupRestore.withPhoto(item.copy(imageUri = "https://image.example/a.jpg"), null)
        assertEquals("https://image.example/a.jpg", web.imageUri)
    }

    @Test
    fun parseOtherResponses() {
        val logs = BackupRestore.parseWearLogs("""{"totalCount":2,"logs":[
            {"date":"2026-10-18","itemIds":["a"],"memo":"m"},{"date":"2026-10-01","itemIds":[],"memo":""}]}""")
        assertEquals(listOf(WearLog("2026-10-18", listOf("a"), "m")), logs)
        assertEquals(listOf("p2", "p1"), BackupRestore.parseInterests("""{"totalCount":2,"items":[
            {"productKey":"p1","position":1},{"productKey":"p2","position":0}]}"""))
        val events = BackupRestore.parseTasteEvents("""{"totalCount":1,"events":[
            {"at":5,"source":"worn","liked":true,"colors":["베이지"],"kinds":["니트"],"pattern":null,"style":null,
             "itemIds":["a"],"date":"2026-10-18"}]}""")
        assertEquals(TasteEvent(5, "worn", true, listOf("베이지"), listOf("니트"), null, null, listOf("a"), "2026-10-18"), events.single())
        assertEquals(listOf("미니멀", "캐주얼") to "FEMALE",
            BackupRestore.parseProfile("""{"preferredStyles":["미니멀","캐주얼"],"gender":"FEMALE"}"""))
        assertEquals(listOf<String>() to null, BackupRestore.parseProfile("""{"preferredStyles":[],"gender":null}"""))
        val (scores, rated) = BackupRestore.parseStylePreference(
            """{"scores":[{"style":"미니멀","score":4},{"style":"캐주얼","score":-1}],"ratedCount":3,"topStyle":"미니멀"}""")
        assertEquals(mapOf("미니멀" to 4, "캐주얼" to -1), scores)
        assertEquals(3, rated)
    }
}

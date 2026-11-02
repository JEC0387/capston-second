package com.jeiu.fashionproject

import com.jeiu.fashionproject.data.*
import com.jeiu.fashionproject.recommend.*
import com.jeiu.fashionproject.ui.viewmodel.ClosetPhotoRules
import org.junit.Assert.*
import org.junit.Test

/** 쇼핑 검색 제한 · 성별 필터 · 스타일 취향 학습 반영 · 옷장 사진 통일 · 가상피팅 사진 다듬기 */
class ShopFittingTasteTest {
    private val day = "2026-10-15"
    private fun item(
        id: String, category: String, sub: String, detail: String, color: String = "블랙", style: String? = "캐주얼"
    ) = WardrobeItem(
        id = id, name = id, category = category, subCategory = sub, detailType = detail,
        colorName = color, colorValue = 0xFF222222, imageUri = "", season = "사계절",
        style = style, addedAt = WearDates.toCalendar(day).timeInMillis
    )

    // ── 쇼핑 검색 ────────────────────────────────────────────────────────

    @Test fun productSearchAcceptsOnlyClothing() {
        assertEquals("남성 셔츠", FashionQuery.productPlan("셔츠", Gender.MALE).query)
        assertEquals("청바지", FashionQuery.productPlan(" 청바지 ", null).query)
        val laptop = FashionQuery.productPlan("노트북", Gender.MALE)
        assertNull(laptop.query)
        assertEquals(FashionQuery.PRODUCT_HINT, laptop.message)
        assertNull(FashionQuery.productPlan("", null).query)
        assertNull(FashionQuery.productPlan("", null).message)
    }

    @Test fun styleSearchIsAlwaysAboutOutfits() {
        assertEquals("여성 가을 코디", FashionQuery.stylePlan("가을", Gender.FEMALE).query)
        assertEquals("미니멀룩", FashionQuery.stylePlan("미니멀룩", null).query)
        assertEquals("남성 데일리룩 코디", FashionQuery.stylePlan("", Gender.MALE).query)
        // 옷과 관련 없는 검색어는 검색하지 않는다
        assertNull(FashionQuery.stylePlan("자동차", null).query)
        assertNull(FashionQuery.stylePlan("맛집", Gender.MALE).query)
        // 모르는 단어(사람 이름 등)는 "코디"를 붙이고 결과는 패션 제목만
        val celeb = FashionQuery.stylePlan("아이유", null)
        assertEquals("아이유 코디", celeb.query)
        assertTrue(celeb.strict)
        assertTrue(FashionQuery.keep("아이유 공항 패션", celeb, null))
        assertFalse(FashionQuery.keep("아이유 콘서트 후기", celeb, null))
    }

    @Test fun resultsDropOtherFieldsAndWomenOnlyForMen() {
        val plan = FashionQuery.stylePlan("가을 코디", Gender.MALE)
        assertTrue(FashionQuery.keep("가을 남자 코디 니트", plan, Gender.MALE))
        assertFalse(FashionQuery.keep("가을 네일 디자인", plan, Gender.MALE))
        assertFalse(FashionQuery.keep("가을 원피스 코디", plan, Gender.MALE))
        assertTrue(FashionQuery.keep("가을 원피스 코디", plan, Gender.FEMALE))
        // 남성이 직접 원피스를 검색하면 그대로
        val dress = FashionQuery.stylePlan("원피스", Gender.MALE)
        assertTrue(FashionQuery.keep("원피스 코디", dress, Gender.MALE))
    }

    @Test fun catalogMatchesSynonyms() {
        val pants = ShopCatalog.products.first { it.name.contains("오피서 팬츠") }
        assertTrue(FashionQuery.matchesProduct(pants, "바지"))
        assertTrue(FashionQuery.matchesProduct(pants, "남성 팬츠"))
        assertFalse(FashionQuery.matchesProduct(pants, "셔츠"))
    }

    // ── 성별 ────────────────────────────────────────────────────────────

    @Test fun menAreNeverRecommendedDressesOrSkirts() {
        val tee = item("tee", "상의", "반팔 상의", "반팔티")
        val blouse = item("blouse", "상의", "반팔 상의", "블라우스")
        val skirt = item("skirt", "하의", "치마", "미니스커트")
        val shorts = item("shorts", "하의", "반바지", "반바지")
        val dress = item("dress", "원피스", "반팔 원피스", "원피스")
        val shoes = item("shoes", "신발", "운동화", "운동화")
        val all = listOf(tee, blouse, skirt, shorts, dress, shoes)
        val weather = WeatherInfo("맑음", 26, 50, 800)
        fun ids(g: Gender?) = (OutfitEngine.recommend(all, weather, emptyList(), emptyMap(), today = "2026-07-01", count = 10, gender = g)
            as OutfitEngine.Result.Ok).outfits.flatMap { o -> o.items.map { it.id } }.toSet()
        val men = ids(Gender.MALE)
        assertTrue(men.none { it in setOf("blouse", "skirt", "dress") })
        assertTrue("tee" in men && "shorts" in men)
        assertTrue(ids(Gender.FEMALE).any { it in setOf("skirt", "dress") })
        assertTrue(ids(null).any { it in setOf("skirt", "dress") })
        assertFalse(GenderFit.allowsTitle("러블린 셔링 블라우스", Gender.MALE))
        assertTrue(GenderFit.allowsTitle("러블린 셔링 블라우스", Gender.FEMALE))
    }

    @Test fun womenGetWomenFitAndNoMenOnlyItems() {
        val tee = item("tee", "상의", "반팔 상의", "반팔티")
        val menShirt = item("남성 셔츠", "상의", "반팔 상의", "셔츠")
        val skirt = item("skirt", "하의", "치마", "미니스커트")
        val shorts = item("shorts", "하의", "반바지", "반바지")
        val shoes = item("shoes", "신발", "운동화", "운동화")
        val all = listOf(tee, menShirt, skirt, shorts, shoes)
        val weather = WeatherInfo("맑음", 26, 50, 800)
        val women = (OutfitEngine.recommend(all, weather, emptyList(), emptyMap(), today = "2026-07-01", count = 10,
            gender = Gender.FEMALE) as OutfitEngine.Result.Ok).outfits
        assertTrue(women.none { o -> o.items.any { it.id == "남성 셔츠" } })
        // 같은 조건이면 스커트 코디가 위로
        assertTrue(women.first().items.any { it.id == "skirt" })
        assertEquals(GenderFit.FEMALE_BONUS, GenderFit.fitBonus(listOf(tee, skirt, shoes), Gender.FEMALE), 1e-9)
        assertEquals(0.0, GenderFit.fitBonus(listOf(tee, skirt, shoes), Gender.MALE), 0.0)
        assertTrue(GenderFit.isMenOnly("Men's Oxford Shirt"))
        assertFalse(GenderFit.isMenOnly("Women's Oxford Shirt"))
        assertFalse(GenderFit.allowsTitle("남자 데일리룩 코디", Gender.FEMALE))
        assertTrue(GenderFit.allowsTitle("여자 데일리룩 코디", Gender.FEMALE))
    }

    @Test fun loginResponseIsReadSafely() {
        val ok = AuthStore.parseLogin("""{"accountKey":"3f1c2a9e-0000-4000-8000-123456789abc","email":"a@example.com",
            "name":"계정A","isNewAccount":false,"switched":true}""")!!
        assertTrue(ok.switched); assertFalse(ok.isNewAccount); assertEquals("계정A", ok.name)
        assertNull(AuthStore.parseLogin("""{"accountKey":"../../etc","email":""}"""))
        assertNull(AuthStore.parseLogin("not json"))
        assertTrue(AuthStore.messageFor(-1, null).contains("서버"))
        assertEquals("서버에 GOOGLE_CLIENT_ID 가 없어요", AuthStore.messageFor(503, """{"detail":"서버에 GOOGLE_CLIENT_ID 가 없어요"}"""))
    }

    @Test fun buyThisNeverSuggestsWomenOnlyItemsToMen() {
        val report = MissingItemFinder.analyze(emptyList(), day, gender = Gender.MALE)
        assertTrue(report.suggestions.none { GenderFit.isWomenOnly(it.item) })
        report.situationNeeds.flatMap { it.items }.forEach { assertFalse(GenderFit.isWomenOnly(it)) }
    }

    // ── 스타일 취향 학습 → 추천 ─────────────────────────────────────────

    private val shirt = item("shirt", "상의", "긴팔 상의", "셔츠")
    private val hoodie = item("hoodie", "상의", "긴팔 상의", "후드티")
    private val slacks = item("slacks", "하의", "긴바지", "슬랙스")
    private val training = item("training", "하의", "긴바지", "트레이닝팬츠")
    private val loafer = item("loafer", "신발", "구두", "로퍼")
    private val sneakers = item("sneakers", "신발", "운동화", "운동화")
    private val six = listOf(shirt, hoodie, slacks, training, loafer, sneakers)

    private fun top(scores: Map<String, Int>) =
        (OutfitEngine.recommend(six, null, emptyList(), scores, today = day) as OutfitEngine.Result.Ok).outfits.first()

    @Test fun learnedStylesChangeTheTopOutfit() {
        assertEquals(setOf("shirt", "slacks", "loafer"), top(mapOf("포멀" to 6, "스포티" to -2)).items.map { it.id }.toSet())
        assertEquals(setOf("hoodie", "training", "sneakers"), top(mapOf("스포티" to 6, "포멀" to -2)).items.map { it.id }.toSet())
        assertTrue(top(mapOf("포멀" to 6)).reasons.contains("취향 학습에서 좋아한 포멀 스타일이에요"))
    }

    @Test fun legacyLearnedNamesAreMerged() {
        assertEquals(5, PreferredStyles.canonicalScores(mapOf("클래식" to 3, "포멀" to 2, "페미닌" to 9))["포멀"])
        assertEquals(8, PreferredStyles.canonicalScores(emptyMap()).size)
        assertEquals(0.0, PreferredStyles.learnedAffinity(listOf(shirt), emptyMap()), 0.0)
        assertEquals(1.0, PreferredStyles.learnedAffinity(listOf(shirt, slacks, loafer), mapOf("클래식" to 4)), 1e-9)
        assertTrue(PreferredStyles.learnedAffinity(listOf(hoodie, training, sneakers), mapOf("스포티" to -2, "포멀" to 4)) < 0)
        assertEquals("스포티", PreferredStyles.dominant(listOf(hoodie, training, sneakers)))
        assertEquals("포멀", PreferredStyles.fromTag("클래식"))
        assertNull(PreferredStyles.fromTag("페미닌"))
    }

    // ── 옷장 사진 통일 ───────────────────────────────────────────────────

    @Test fun plainBackgroundIsRemovedButGarmentInsideKept() {
        val w = 40; val h = 40
        val bg = 0xFFEFEFEF.toInt(); val cloth = 0xFF203060.toInt()
        val px = IntArray(w * h) { bg }
        for (y in 10 until 30) for (x in 12 until 28) px[y * w + x] = cloth
        // 옷 안쪽에 배경과 같은 색 단추 — 테두리와 이어지지 않아 지우지 않는다
        px[20 * w + 20] = bg
        val mask = ClosetPhotoRules.plainBackgroundMask(px, w, h)!!
        assertTrue(mask[0]); assertTrue(mask[5 * w + 5])
        assertFalse(mask[15 * w + 15]); assertFalse(mask[20 * w + 20])
    }

    @Test fun busyBackgroundIsLeftAlone() {
        val w = 40; val h = 40
        val px = IntArray(w * h) { i -> if ((i / 3) % 2 == 0) 0xFF000000.toInt() else 0xFFFFFFFF.toInt() }
        assertNull(ClosetPhotoRules.plainBackgroundMask(px, w, h))
    }

    @Test fun everyGarmentGetsTheSameFrame() {
        val tall = ClosetPhotoRules.layout(300, 900)
        val wide = ClosetPhotoRules.layout(800, 400)
        val c = ClosetPhotoRules.CANVAS * ClosetPhotoRules.FILL
        assertEquals(c, tall.height, 0.01f); assertEquals(c, wide.width, 0.01f)
        assertEquals(ClosetPhotoRules.CANVAS / 2f, tall.left + tall.width / 2, 0.01f)
        assertEquals(ClosetPhotoRules.CANVAS / 2f, wide.left + wide.width / 2, 0.01f)
    }

    // ── 가상피팅 사진 다듬기 ─────────────────────────────────────────────

    @Test fun smallPersonIsFramedTwoByThree() {
        val body = TryOnFraming.Box(800f, 300f, 1100f, 1500f)
        val crop = TryOnFraming.crop(2400, 1800, body)!!
        assertEquals(1.5f, crop.height / crop.width, 0.01f)
        assertTrue(crop.left <= body.left && crop.right >= body.right && crop.top <= body.top && crop.bottom >= body.bottom)
        assertTrue(crop.left >= 0f && crop.top >= 0f && crop.right <= 2400f && crop.bottom <= 1800f)
        // 이미 사람이 사진을 꽉 채우면 자르지 않는다
        assertNull(TryOnFraming.crop(1000, 1500, TryOnFraming.Box(50f, 20f, 950f, 1480f)))
    }

    @Test fun faceBlendKeepsOriginalFaceAndFadesOut() {
        val o = TryOnFraming.faceOval(TryOnFraming.P(500f, 200f), TryOnFraming.P(440f, 200f), TryOnFraming.P(560f, 200f),
            null, null, null)!!
        assertEquals(1f, TryOnFraming.faceWeight(o.cx, o.cy, o), 0f)
        assertEquals(0f, TryOnFraming.faceWeight(o.cx + o.rx * 1.2f, o.cy, o), 0f)
        val mid = TryOnFraming.faceWeight(o.cx + o.rx * 0.85f, o.cy, o)
        assertTrue(mid > 0f && mid < 1f)
        assertNull(TryOnFraming.faceOval(null, null, null, null, null, 100f))
    }
}

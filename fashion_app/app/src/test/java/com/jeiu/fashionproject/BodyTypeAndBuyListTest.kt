package com.jeiu.fashionproject

import com.jeiu.fashionproject.data.*
import com.jeiu.fashionproject.recommend.*
import org.junit.Assert.*
import org.junit.Test

/** 직접 입력 치수 + 사진 체형 합치기, 체형 · 선호 스타일 추천 반영, "이것만 사면" 다양화 · 상황별 계획 */
class BodyTypeAndBuyListTest {
    private val day = "2026-11-15"
    private fun manual(bust: Float?, waist: Float?, hip: Float?) =
        ManualBodyMeasurements(170f, null, bust, waist, hip, null, null, 1L)
    private fun item(id: String, category: String, sub: String, kind: String, colorName: String = "블랙",
                     color: Long = 0xFF222222, name: String = kind) = WardrobeItem(
        id = id, name = name, category = category, colorName = colorName, colorValue = color,
        imageUri = "", subCategory = sub, detailType = kind, season = "사계절"
    )

    @Test fun manualCircumferencesClassifyAndOverridePhoto() {
        assertEquals("역삼각형", BodyShapeFusion.fromManual(manual(100f, 80f, 90f)))
        assertEquals("삼각형", BodyShapeFusion.fromManual(manual(85f, 70f, 100f)))
        assertEquals("모래시계형", BodyShapeFusion.fromManual(manual(92f, 66f, 92f)))
        assertEquals("직사각형", BodyShapeFusion.fromManual(manual(92f, 80f, 92f)))
        assertNull(BodyShapeFusion.fromManual(manual(92f, null, 92f)))

        val agree = BodyShapeFusion.fuse("역삼각형", manual(100f, 80f, 90f))!!
        assertEquals(BodyShapeFusion.Source.BOTH, agree.source); assertEquals(true, agree.agreed)
        val override = BodyShapeFusion.fuse("직사각형", manual(100f, 80f, 90f))!!
        assertEquals("역삼각형", override.type); assertEquals(false, override.agreed)
        assertEquals("삼각형", BodyShapeFusion.fuse("삼각형", null)!!.type)
        assertNull(BodyShapeFusion.fuse(null, null))
    }

    @Test fun bodyTypeAloneStillReachesOutfitScoring() {
        val profile = BodyProfileRepository.withBodyType(null, null, "역삼각형", Gender.MALE)!!
        assertFalse(profile.hasLengths)
        val wide = item("w", "하의", "긴바지", "슬랙스", name = "와이드 슬랙스")
        val skinny = item("s", "하의", "긴바지", "청바지", name = "스키니 진")
        val top = item("t", "상의", "긴팔 상의", "니트", "그레이", 0xFF999999)
        assertEquals(BodyAwareOutfitScorer.SHAPE_BONUS, BodyAwareOutfitScorer.evaluate(profile, listOf(top, wide)).score, 1e-9)
        assertEquals(BodyAwareOutfitScorer.SHAPE_PENALTY, BodyAwareOutfitScorer.evaluate(profile, listOf(top, skinny)).score, 1e-9)
        assertTrue(BodyAwareOutfitScorer.evaluate(profile, listOf(top, wide)).reason!!.startsWith("체형 분석 반영"))
        // 길이 정보만 있는 기존 프로필은 그대로 (체형 가산 없음)
        assertEquals(0.0, BodyAwareOutfitScorer.evaluate(BodyProfile(.45, .65), listOf(top, wide)).score, 1e-9)
    }

    @Test fun calibrationUsesMedianEnteredToPhotoRatio() {
        fun c(d: BodyDimension, e: Float, p: Float) = DimensionComparison(d, e, p)
        assertEquals(1.04f, BodyDimensions.calibrationFactor(listOf(
            c(BodyDimension.SHOULDER_WIDTH, 40f, 38f), c(BodyDimension.LEG_LENGTH, 88f, 85f),
            c(BodyDimension.TORSO_LENGTH, 52f, 50f)))!!, .001f)
        assertNull(BodyDimensions.calibrationFactor(listOf(c(BodyDimension.HEIGHT, 170f, 170f))))
        assertEquals(1.15f, BodyDimensions.calibrationFactor(listOf(c(BodyDimension.SHOULDER_WIDTH, 60f, 30f)))!!, .001f)
    }

    @Test fun productsForPreferredStyleFollowGenderAndBody() {
        val street = ProductRecommender.forStyle(ShopCatalog.products, "스트릿", Gender.MALE)
        assertTrue(street.isNotEmpty() && street.size <= 6)
        assertTrue(street.none { it.name.startsWith("여성") || "우먼" in it.name })
        val guide = BodyStyleGuide.guide("역삼각형", Gender.MALE)!!
        ProductRecommender.forStyle(ShopCatalog.products, "캐주얼", Gender.MALE, "역삼각형").forEach { p ->
            assertTrue(p.name, guide.avoidWords.none { it in p.name })
        }
    }

    @Test fun topSuggestionsAreDiverseAndLabelledPerItem() {
        val owned = listOf(item("top", "상의", "반팔 상의", "반팔티", "화이트", 0xFFF2F2F2), item("shoes", "신발", "운동화", "스니커즈"))
        val report = MissingItemFinder.analyze(owned, "2026-07-01", Gender.MALE)
        assertTrue(report.suggestions.isNotEmpty())
        val categories = report.suggestions.map { it.item.category }
        // 대분류가 여러 개 있을 수 있으면 TOP 3가 한 대분류로 몰리지 않는다
        if (report.suggestions.size >= 2) assertTrue(categories.toSet().size >= 2 ||
            report.suggestions.map { SituationRules.kindOf(it.item) }.toSet().size == report.suggestions.size)
        assertTrue(report.suggestions.zipWithNext().all { (a, b) -> a.gain >= b.gain || a.item.category != b.item.category })
        report.suggestions.forEach { assertNotEquals("기본템", MissingItemFinder.labelOf(it.item)) }
        assertEquals("겨울 패딩", MissingItemFinder.labelOf(BasicItems.basics.first { it.detailType == "패딩" }))
    }

    @Test fun everySituationGetsAPlanWithAllowedProducts() {
        val report = MissingItemFinder.analyze(emptyList(), day, Gender.FEMALE)
        assertEquals(Situation.entries.size, report.situationPlans.size)
        assertTrue(Situation.entries.size >= 8)
        report.situationPlans.forEach { plan ->
            plan.products.forEach { p ->
                val c = OutfitSources.candidate(p, 1L)!!
                assertTrue("${plan.situation} ${p.name}", SituationRules.allows(c, plan.situation))
                assertFalse(p.name, p.name.startsWith("남성"))
            }
            plan.outfit?.let { o -> assertTrue(o.items.all { SituationRules.allows(it, plan.situation) }) }
        }
        assertTrue(report.situationPlans.any { it.products.isNotEmpty() })
    }

    @Test fun basicItemsGetExamplePhotosFromRealProducts() {
        val padding = BasicItems.basics.first { it.detailType == "패딩" }
        val example = BasicItems.exampleOf(padding, Gender.MALE)
        assertNotNull(example); assertNotNull(example!!.productId)
        assertTrue(BasicItems.catalogItems.isNotEmpty())
        assertTrue(BasicItems.catalogItems.all { it.productId != null && it.item.colorName.isNotBlank() })
    }
}

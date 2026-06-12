package com.jeiu.fashionproject.data

import androidx.compose.ui.graphics.Color
import com.jeiu.fashionproject.R

data class OutfitCardData(
    val title: String,
    val desc: String,
    val bgColor: Color,
    val imageSource: Any,   // R.drawable.xxx (Int) 또는 URL String
    val purchaseUrl: String = ""
)
data class OutfitItemData(val label: String, val color: Color, val imageRes: Int = 0, val purchaseUrl: String = "")
data class OutfitSetData(val title: String, val tag: String, val top: OutfitItemData, val bottom: OutfitItemData, val shoes: OutfitItemData? = null, val imageSource: Any = 0)

object WeatherOutfits {

    fun cardsFor(info: WeatherInfo?): List<OutfitCardData> = when {
        info == null          -> coolCards()
        info.isRainy()        -> rainyCards()
        info.isCloudy()       -> cloudyCards()
        info.temp < 10        -> coldCards()
        info.temp < 20        -> coolCards()
        info.temp < 28        -> warmCards()
        else                  -> hotCards()
    }

    fun setsFor(info: WeatherInfo?): List<OutfitSetData> = when {
        info == null          -> coolSets()
        info.isRainy()        -> rainySets()
        info.isCloudy()       -> cloudySets()
        info.temp < 10        -> coldSets()
        info.temp < 20        -> coolSets()
        info.temp < 28        -> warmSets()
        else                  -> hotSets()
    }

    // ── 비 (conditionId 200~599) ────────────────────────────────────────────
    private fun rainyCards() = listOf(
        OutfitCardData("레인 트렌치룩",   "트렌치코트 + 슬랙스",    Color(0xFFD8D0C8), R.drawable.casual_daily_look),
        OutfitCardData("캐주얼 레이어드", "후드집업 + 카고팬츠",    Color(0xFFCFC8C0), R.drawable.casual_daily_look),
        OutfitCardData("미니멀 레인룩",   "오버핏 셔츠 + 슬랙스",  Color(0xFFD5CECC), R.drawable.casual_daily_look),
    )
    private fun rainySets() = listOf(
        OutfitSetData("레인 트렌치룩",   "비 최적",
            OutfitItemData("트렌치코트", Color(0xFFD8D0C8)),
            OutfitItemData("슬랙스",    Color(0xFFCDC5BC)),
            OutfitItemData("레인부츠",  Color(0xFF8A8078)),
            R.drawable.casual_daily_look),
        OutfitSetData("캐주얼 레이어드", "방수 추천",
            OutfitItemData("후드집업",  Color(0xFFCFC8C0)),
            OutfitItemData("카고팬츠",  Color(0xFFBBB5AC)),
            OutfitItemData("워커",      Color(0xFF9A9288)),
            R.drawable.casual_daily_look),
        OutfitSetData("미니멀 레인룩",   "내 스타일",
            OutfitItemData("오버핏 셔츠", Color(0xFFD5CECC)),
            OutfitItemData("슬랙스",    Color(0xFFC5C0BC)),
            OutfitItemData("단화",      Color(0xFFB0A8A0)),
            R.drawable.casual_daily_look),
    )

    // ── 흐림 (conditionId 801~899) ──────────────────────────────────────────
    private fun cloudyCards() = listOf(
        OutfitCardData("레이어드 캐주얼", "가디건 + 슬랙스",        Color(0xFFE0D8CC), R.drawable.casual_daily_look),
        OutfitCardData("모던 오피스룩",   "블라우스 + 와이드팬츠",  Color(0xFFE8E0D8), R.drawable.casual_daily_look),
        OutfitCardData("스트릿 무드",    "후드티 + 데님팬츠",       Color(0xFFDDD5C8), R.drawable.casual_daily_look),
    )
    private fun cloudySets() = listOf(
        OutfitSetData("레이어드 캐주얼", "흐림 최적",
            OutfitItemData("가디건",     Color(0xFFE0D8CC)),
            OutfitItemData("슬랙스",     Color(0xFFCDC5BC)),
            OutfitItemData("스니커즈",   Color(0xFFF0EBE3)),
            R.drawable.casual_daily_look),
        OutfitSetData("모던 오피스룩",   "내 스타일",
            OutfitItemData("블라우스",   Color(0xFFE8E0D8)),
            OutfitItemData("와이드팬츠", Color(0xFFD5CABD)),
            OutfitItemData("로퍼",       Color(0xFFB8A898)),
            R.drawable.summer_blouse),
        OutfitSetData("스트릿 무드",    "트렌디",
            OutfitItemData("후드티",     Color(0xFFDDD5C8)),
            OutfitItemData("데님팬츠",   Color(0xFFA8B8C8)),
            OutfitItemData("운동화",     Color(0xFFD0CCC5)),
            R.drawable.street_cool),
    )

    // ── 추움 (< 10°C) ───────────────────────────────────────────────────────
    private fun coldCards() = listOf(
        OutfitCardData("코트 코디",       "울코트 + 터틀넥 + 슬랙스", Color(0xFFD0C8C0), R.drawable.casual_daily_look),
        OutfitCardData("패딩 캐주얼",     "패딩 + 후드티 + 조거팬츠", Color(0xFFC8C0B8), R.drawable.casual_daily_look),
        OutfitCardData("히트텍 레이어드", "니트 + 청바지",             Color(0xFFD5CEC8), R.drawable.casual_daily_look),
    )
    private fun coldSets() = listOf(
        OutfitSetData("코트 코디",       "추위 최적",
            OutfitItemData("울코트",          Color(0xFFD0C8C0)),
            OutfitItemData("터틀넥 + 슬랙스", Color(0xFFC0B8B0)),
            OutfitItemData("부츠",            Color(0xFF908880)),
            R.drawable.casual_daily_look),
        OutfitSetData("패딩 캐주얼",     "보온 추천",
            OutfitItemData("패딩",            Color(0xFFC8C0B8)),
            OutfitItemData("후드티 + 조거팬츠", Color(0xFFB8B0A8)),
            OutfitItemData("스니커즈",        Color(0xFFD8D0C8)),
            R.drawable.casual_daily_look),
        OutfitSetData("히트텍 레이어드", "내 스타일",
            OutfitItemData("니트",            Color(0xFFD5CEC8)),
            OutfitItemData("청바지",           Color(0xFFA0AEC0)),
            OutfitItemData("앵클부츠",         Color(0xFF988880)),
            R.drawable.summer_knit),
    )

    // ── 쌀쌀 (10~19°C) ──────────────────────────────────────────────────────
    private fun coolCards() = listOf(
        OutfitCardData("캐주얼 데일리룩", "베이지 니트 + 와이드팬츠", Color(0xFFEDE3D5), R.drawable.casual_daily_look),
        OutfitCardData("미니멀 오피스룩", "화이트 블라우스 + 슬랙스", Color(0xFFE8E0D8), R.drawable.casual_daily_look),
        OutfitCardData("스트릿 캐주얼",  "후드집업 + 카고팬츠",       Color(0xFFDDD5C8), R.drawable.casual_daily_look),
    )
    private fun coolSets() = listOf(
        OutfitSetData("캐주얼 데일리룩", "날씨 최적",
            OutfitItemData("베이지 니트",  Color(0xFFEDE3D5)),
            OutfitItemData("와이드팬츠",   Color(0xFFD5CABD)),
            OutfitItemData("스니커즈",     Color(0xFFF0EBE3)),
            R.drawable.casual_daily_look),
        OutfitSetData("미니멀 오피스룩", "내 스타일",
            OutfitItemData("화이트 블라우스", Color(0xFFF5F2EE)),
            OutfitItemData("슬랙스",       Color(0xFFCDC5BC)),
            OutfitItemData("로퍼",         Color(0xFFB8A898)),
            R.drawable.summer_blouse),
        OutfitSetData("스트릿 캐주얼",  "트렌디",
            OutfitItemData("후드집업",     Color(0xFFE8DDD0)),
            OutfitItemData("카고팬츠",     Color(0xFFCFCAC0)),
            OutfitItemData("청키 스니커즈", Color(0xFFDDD8D0)),
            R.drawable.street_cool),
    )

    // ── 따뜻 (20~27°C) ──────────────────────────────────────────────────────
    private fun warmCards() = listOf(
        OutfitCardData("청순 블라우스 코디", "일체형 블라우스+린넨 팬츠",  Color(0xFFEDE3D5), R.drawable.summer_blouse, "https://zigzag.kr/catalog/products/163193045"),
        OutfitCardData("썸머 라인 셔츠코디", "린넨 셔츠 + 와이드팬츠",     Color(0xFFE8E8E0), R.drawable.half_sleeve_shirt, "https://zigzag.kr/catalog/products/145426176"),
        OutfitCardData("여름 데일리 셔츠코디",  "썸머 긴팔셔츠+ 썸머 팬츠",           Color(0xFFDDD5C8), R.drawable.street_cool, "https://zigzag.kr/catalog/products/169754357"),
    )
    private fun warmSets() = listOf(
        OutfitSetData("청순 블라우스 코디", "날씨 최적",
            OutfitItemData("일체형 블라우스", Color(0xFFEDE3D5), R.drawable.summer_blouse),
            OutfitItemData("린넨 팬츠",      Color(0xFFD5CABD), R.drawable.outfit_linen_pants, "https://zigzag.kr/catalog/products/162619955"),
            imageSource = R.drawable.summer_blouse),
        OutfitSetData("썸머 라인 셔츠코디", "내 스타일",
            OutfitItemData("라인 셔츠",       Color(0xFFE8E8E0), R.drawable.half_sleeve_shirt),
            OutfitItemData("포스트 와이드 팬츠", Color(0xFFD0CEC8), R.drawable.post_wide_pants),
            imageSource = R.drawable.half_sleeve_shirt),
        OutfitSetData("여름 데일리 셔츠코디",  "트렌디",
            OutfitItemData("그래픽 티",      Color(0xFFDDD5C8)),
            OutfitItemData("쇼츠",           Color(0xFFCFC8C0)),
            OutfitItemData("청키 스니커즈",   Color(0xFFDDD8D0)),
            R.drawable.street_cool),
    )

    // ── 더움 (≥ 28°C) ───────────────────────────────────────────────────────
    private fun hotCards() = listOf(
        OutfitCardData("쿨 썸머룩",   "민소매 + 린넨 쇼츠",              Color(0xFFF0E8DC), R.drawable.cool_summer,  "https://zigzag.kr/catalog/products/157599443"),
        OutfitCardData("여름니트",    "린넨 브이넥 니트 + 썸머 밴딩 팬츠", Color(0xFFE8E4DC), R.drawable.summer_knit, "https://zigzag.kr/catalog/products/169823158"),
        OutfitCardData("루즈핏 반팔", "루즈핏 티 + 린넨 팬츠",         Color(0xFFE0DCD4), R.drawable.loose_tshirt, "https://zigzag.kr/catalog/products/163392618"),
    )
    private fun hotSets() = listOf(
        OutfitSetData("쿨 썸머룩",   "더위 최적",
            OutfitItemData("민소매",       Color(0xFFF0E8DC)),
            OutfitItemData("린넨 쇼츠",    Color(0xFFE0D8CC)),
            OutfitItemData("샌들",         Color(0xFFD0C8BC)),
            R.drawable.cool_summer),
        OutfitSetData("여름니트",    "내 스타일",
            OutfitItemData("린넨 브이넥 니트", Color(0xFFE8E4DC)),
            OutfitItemData("썸머 밴딩 팬츠",  Color(0xFFD8D4CC)),
            OutfitItemData("슬리퍼",          Color(0xFFC8C4BC)),
            R.drawable.summer_knit),
        OutfitSetData("루즈핏 반팔", "트렌디",
            OutfitItemData("루즈핏 티",    Color(0xFFE0DCD4)),
            OutfitItemData("린넨 팬츠",    Color(0xFFD0CCC4)),
            OutfitItemData("스니커즈",     Color(0xFFDDD8D0)),
            R.drawable.loose_tshirt),
    )
}

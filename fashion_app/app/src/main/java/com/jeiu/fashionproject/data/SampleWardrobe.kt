package com.jeiu.fashionproject.data

/**
 * 첫 실행 때 옷장에 넣어 두는 샘플 옷 12벌.
 *
 * 예전에는 WardrobeScreen 코드에 고정되어 있어 착용 기록·즐겨찾기 저장이 불가능했다.
 * 이제 저장소에 들어가므로 다른 옷과 똑같이 다룰 수 있다.
 * id를 고정해 두어 여러 번 넣더라도 같은 옷으로 인식된다.
 *
 * 사진은 앱 안(res/drawable/sample_XX)에 들어 있어 인터넷 없이도 보인다.
 * 쇼핑몰 착용 사진을 DeepFashion2 윤곽으로 옷만 오려낸 PNG(isCutout)와, 오려내기가 어색해 원래 사진을 쓴 JPG가 섞여 있다.
 * 색은 사진의 옷 부분에서 계산한 값이다.
 * 옷 정보 수정에서 사진 종류를 바꿀 수 있도록 원본·배경 제거·옷만 버전 주소도 함께 둔다.
 */
object SampleWardrobe {
    private const val RES = "android.resource://com.jeiu.fashionproject/drawable/"

    fun items(): List<WardrobeItem> = listOf(
        // 옷만 오려낸 사진 — 원본은 오려내기 전 쇼핑몰 사진
        sample("sample_01", "베이지 니트", "상의", "긴팔 상의", "니트", "사계절", "베이지", 0xFFB5A38F, Kind.Garment("casual_daily_look")),
        sample("sample_02", "스트라이프 블라우스", "상의", "반팔 상의", "블라우스", "여름", "그레이", 0xFFA6A7A9, Kind.Garment("summer_blouse")),
        sample("sample_03", "화이트 반팔티", "상의", "반팔 상의", "반팔티", "여름", "화이트", 0xFFCFC8C3, Kind.Garment("popular_crop_tshirt")),
        sample("sample_04", "체크 셔츠", "상의", "긴팔 상의", "셔츠", "사계절", "그레이", 0xFF838892, Kind.Photo),
        sample("sample_05", "와이드 청바지", "하의", "긴바지", "청바지", "사계절", "블루", 0xFF67777C, Kind.Photo),
        sample("sample_06", "블랙 슬랙스", "하의", "긴바지", "슬랙스", "사계절", "블랙", 0xFF181C1E, Kind.Garment("shop_officer_pants")),
        sample("sample_07", "카고 팬츠", "하의", "긴바지", "면바지", "사계절", "블랙", 0xFF313233, Kind.Garment("popular_cargo_pants")),
        sample("sample_08", "블랙 미니 스커트", "하의", "치마", "미니스커트", "사계절", "블랙", 0xFF282521, Kind.Photo),
        sample("sample_09", "베이지 블레이저", "아우터", "긴팔 아우터", "자켓", "봄·가을", "베이지", 0xFFCDB6A1, Kind.Photo),
        sample("sample_10", "데님 반팔 셔츠", "상의", "반팔 상의", "셔츠", "여름", "블루", 0xFF515A69, Kind.Garment("shop_denim_shirt")),
        sample("sample_11", "스니커즈", "신발", "운동화", "스니커즈", "사계절", "화이트", 0xFFDAD4D5, Kind.Photo),
        sample("sample_12", "로퍼", "신발", "구두", "로퍼", "사계절", "블랙", 0xFF242728, Kind.BackgroundRemoved),
    )

    /** 샘플 사진이 어떤 종류인지 — 옷 정보 수정에서 고를 수 있는 사진 버전이 달라진다. */
    private sealed class Kind {
        data class Garment(val originalDrawable: String) : Kind()   // 옷만 오려냄 (원본 쇼핑몰 사진 있음)
        object Photo : Kind()                                     // 원래 사진을 잘라 씀
        object BackgroundRemoved : Kind()                         // 밝은 배경만 지움 (원본 없음)
    }

    /** 옛날 버전의 샘플 사진 주소(외부 사이트) — 이 주소를 가진 샘플은 새 정보로 바꾼다. */
    fun isOutdated(item: WardrobeItem): Boolean = item.imageUri.startsWith("https://loremflickr.com")

    private fun sample(
        id: String,
        name: String,
        category: String,
        subCategory: String?,
        detailType: String?,
        season: String,
        colorName: String,
        colorValue: Long,
        kind: Kind
    ): WardrobeItem {
        val image = RES + id
        return WardrobeItem(
            id = id,
            name = name,
            category = category,
            colorName = colorName,
            colorValue = colorValue,
            imageUri = image,
            subCategory = subCategory,
            detailType = detailType,
            season = season,
            isCutout = kind !is Kind.Photo,
            originalUri = when (kind) {
                is Kind.Garment -> RES + kind.originalDrawable
                Kind.Photo -> image
                Kind.BackgroundRemoved -> null
            },
            cutoutUri = if (kind is Kind.BackgroundRemoved) image else null,
            garmentUri = if (kind is Kind.Garment) image else null
        )
    }
}

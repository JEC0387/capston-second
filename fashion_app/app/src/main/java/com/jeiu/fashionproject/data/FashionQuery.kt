package com.jeiu.fashionproject.data

import com.jeiu.fashionproject.recommend.GenderFit

/**
 * 쇼핑 탭 검색어 · 검색 결과를 옷과 관련된 것만 남기는 규칙.
 *
 *  - 상품 검색: 옷 · 신발 · 패션 잡화 단어(셔츠, 청바지, 운동화 …)가 들어간 검색어만 받는다.
 *  - 스타일 검색: 옷 단어나 스타일 단어(코디, 미니멀룩, 가을, 데이트 …)가 있으면 그대로,
 *    없으면(예: 연예인 이름) "코디"를 붙여 찾고 제목에 패션 단어가 있는 결과만 남긴다.
 *    옷과 무관한 검색어(자동차, 맛집 …)는 검색하지 않고 안내한다.
 *  - 결과 제목에 헤어 · 네일 · 인테리어 같은 다른 분야 단어가 있으면 뺀다.
 *  - 성별에 맞지 않는 결과는 뺀다 — 남성: 원피스 · 치마 등, 여성: 남성 전용 (검색어에 직접 쓴 경우는 제외).
 */
object FashionQuery {

    /** 옷 · 신발 · 패션 잡화 (상품 검색에 필요한 단어) */
    private val CLOTHING = listOf(
        "상의", "하의", "아우터", "신발", "의류", "옷",
        "셔츠", "남방", "블라우스", "티셔츠", "반팔티", "긴팔티", "카라티", "폴로", "반팔", "긴팔", "민소매", "나시",
        "맨투맨", "스웻", "후드", "후디", "니트", "스웨터", "가디건", "조끼", "베스트",
        "자켓", "재킷", "점퍼", "블루종", "코트", "트렌치", "패딩", "다운자켓", "플리스", "바람막이", "아노락", "야상", "블레이저",
        "수트", "정장", "셋업", "바지", "팬츠", "슬랙스", "청바지", "데님", "반바지", "쇼츠", "조거", "트레이닝", "츄리닝",
        "레깅스", "카고", "치노", "면바지", "스커트", "치마", "원피스", "드레스", "점프수트", "크롭탑", "나시탑",
        "운동화", "스니커즈", "구두", "로퍼", "부츠", "샌들", "슬리퍼", "슬립온", "뮬", "힐",
        "모자", "볼캡", "비니", "버킷햇", "양말", "벨트", "머플러", "목도리", "장갑", "가방", "백팩", "토트백", "크로스백", "숄더백",
        "shirt", "tee", "hoodie", "sweat", "knit", "cardigan", "jacket", "coat", "padding", "pants", "jeans",
        "denim", "slacks", "skirt", "dress", "sneakers", "shoes", "boots", "loafer"
    )

    /** 코디 · 스타일 단어 (스타일 검색에서 옷과 관련된 검색어로 보는 단어) */
    private val STYLE = listOf(
        "코디", "룩", "패션", "스타일", "착장", "옷차림", "데일리", "ootd", "outfit", "fashion", "look",
        "캐주얼", "스트릿", "미니멀", "댄디", "아메카지", "유니크", "스포티", "포멀", "오피스", "하객", "출근", "데이트",
        "여행", "면접", "캠퍼스", "대학생", "직장인", "페미닌", "빈티지", "클래식", "레이어드", "꾸안꾸", "시티보이",
        "고프코어", "놈코어", "y2k", "프레피", "워크웨어", "애슬레저", "골프웨어", "등산복", "오버핏", "와이드",
        "봄", "여름", "가을", "겨울", "간절기", "장마", "비오는날", "추운날", "더운날", "연말",
        "블랙", "화이트", "그레이", "베이지", "네이비", "브라운", "카키", "아이보리", "레드", "블루", "그린", "핑크",
        "퍼플", "옐로우", "오렌지", "톤온톤", "키작남", "키큰", "체형"
    )

    /** 다른 분야 단어 — 검색어만 이것이면 검색하지 않고, 결과 제목에 있으면 뺀다 */
    private val BLOCKED = listOf(
        "헤어", "머리스타일", "펌", "염색", "네일", "메이크업", "화장품", "립스틱", "향수", "인테리어", "가구", "소파",
        "침대", "요리", "레시피", "맛집", "음식", "자동차", "게임", "강아지", "고양이", "반려", "부동산", "아파트",
        "주식", "노트북", "휴대폰", "폰케이스", "아이폰", "갤럭시", "일러스트", "도안", "애니", "만화", "웹툰", "문신",
        "타투", "꽃다발", "케이크", "장난감", "레고", "피규어", "컬러링", "색칠", "운세", "풍경", "펜션"
    )

    /** "코디"를 붙이지 않아도 되는 단어 */
    private val LOOK_WORDS = listOf("코디", "룩", "패션", "착장", "옷차림", "ootd", "스타일링")

    private fun String.has(words: List<String>) = lowercase().let { low -> words.any { it in low } }

    fun isClothing(text: String): Boolean = text.has(CLOTHING)
    fun isFashion(text: String): Boolean = text.has(CLOTHING) || text.has(STYLE)
    fun isBlocked(text: String): Boolean = text.has(BLOCKED)

    /**
     * 검색 계획.
     * @param query 네이버에 보낼 검색어 (null이면 검색하지 않음)
     * @param strict true면 결과 제목에 패션 단어가 있는 것만 남긴다
     * @param message 검색하지 않을 때 보여줄 안내
     */
    data class Plan(val query: String?, val strict: Boolean = false, val message: String? = null, val raw: String = "")

    const val STYLE_HINT = "옷 스타일과 관련된 검색어로 찾아 주세요.\n예: 가을 코디 · 미니멀룩 · 셔츠 코디 · 데이트룩"
    const val PRODUCT_HINT = "옷 상품만 검색할 수 있어요.\n예: 셔츠 · 청바지 · 가디건 · 운동화"

    private fun clean(raw: String) = raw.trim().replace(Regex("\\s+"), " ")

    private fun withGender(q: String, gender: Gender?): String =
        if (gender == null || listOf("남성", "여성", "남자", "여자").any { it in q }) q else "${gender.label} $q"

    /** 스타일 검색 */
    fun stylePlan(raw: String, gender: Gender?): Plan {
        val q = clean(raw)
        if (q.isEmpty()) return Plan(withGender("데일리룩 코디", gender))
        val fashion = isFashion(q)
        if (!fashion && isBlocked(q)) return Plan(null, message = STYLE_HINT, raw = q)
        val base = if (q.has(LOOK_WORDS)) q else "$q 코디"
        return Plan(withGender(base, gender), strict = !fashion, raw = q)
    }

    /** 상품 검색 (온라인 결과) — 옷 단어가 없으면 검색하지 않는다. 빈 검색어면 null (앱 추천 상품만 보여줌) */
    fun productPlan(raw: String, gender: Gender?): Plan {
        val q = clean(raw)
        if (q.isEmpty()) return Plan(null, raw = q)
        if (!isClothing(q)) return Plan(null, message = PRODUCT_HINT, raw = q)
        return Plan(withGender(q, gender), strict = true, raw = q)
    }

    /** 결과 하나를 남길지 */
    fun keep(title: String, plan: Plan, gender: Gender?): Boolean {
        if (isBlocked(title) && !isBlocked(plan.raw)) return false
        if (plan.strict && !isFashion(title)) return false
        // 성별에 맞지 않는 옷이 제목에 있으면 뺀다 — 검색어에 직접 그 옷을 쓴 경우는 그대로
        if (!GenderFit.allowsTitle(plan.raw, gender)) return true
        return GenderFit.allowsTitle(title, gender)
    }

    private val SYNONYMS = mapOf(
        "바지" to listOf("팬츠", "슬랙스", "데님", "하의"),
        "팬츠" to listOf("바지", "슬랙스"),
        "청바지" to listOf("데님"),
        "데님" to listOf("청바지"),
        "남방" to listOf("셔츠"),
        "셔츠" to listOf("남방"),
        "신발" to listOf("샌들", "운동화", "스니커즈", "구두", "로퍼", "부츠"),
        "상의" to listOf("셔츠", "블라우스", "티셔츠", "니트"),
        "하의" to listOf("팬츠", "바지", "슬랙스", "스커트"),
        "아우터" to listOf("가디건", "자켓", "코트", "점퍼"),
        "재킷" to listOf("자켓"),
        "자켓" to listOf("재킷")
    )

    /** 앱 추천 상품(ShopCatalog)이 검색어에 맞는지 — 이름 · 브랜드 · 분류와 비슷한 말까지 본다 */
    fun matchesProduct(p: ShopProduct, raw: String): Boolean {
        val q = clean(raw)
        if (q.isEmpty()) return true
        val text = listOfNotNull(p.name, p.brand, p.category, p.subCategory, p.detailType).joinToString(" ").lowercase()
        return q.lowercase().split(" ").filter { it !in setOf("남성", "여성", "남자", "여자") }.all { token ->
            token in text || SYNONYMS[token].orEmpty().any { it.lowercase() in text }
        }
    }
}

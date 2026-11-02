package com.jeiu.fashionproject.recommend

import com.jeiu.fashionproject.data.Gender
import com.jeiu.fashionproject.data.WardrobeItem

/**
 * 체형별 스타일 제안 (마이페이지 > 체형 맞춤 스타일 제안).
 *
 * 체형 분석의 4가지 분류(역삼각형 · 삼각형 · 모래시계형 · 직사각형)와 성별로 제안을 고르고,
 * 내 옷장 옷 중 제안에 맞는 옷 / 피하면 좋은 옷을 찾아 준다.
 * 일반적인 코디 원칙(어깨 · 힙 비율 균형, 허리선)이며, 실제 핏을 측정한 결과가 아니다.
 */
object BodyStyleGuide {

    /** @param query 쇼핑 · 사진 검색어 (성별은 화면에서 붙인다) */
    data class Tip(val title: String, val desc: String, val query: String)

    /**
     * @param goodWords 옷 이름 · 종류에 이 단어가 있으면 잘 맞는 옷
     * @param avoidWords 옷 이름 · 종류에 이 단어가 있으면 피하면 좋은 옷
     * @param darkBottom true 면 어두운 하의가 잘 맞고 밝은 하의는 피한다 (하체 볼륨이 큰 체형)
     * @param brightTop true 면 밝은 상의가 잘 맞는다 (시선을 위로)
     */
    data class Guide(
        val bodyType: String,
        val summary: String,
        val tips: List<Tip>,
        val avoid: List<String>,
        val goodWords: List<String>,
        val avoidWords: List<String>,
        val darkBottom: Boolean = false,
        val brightTop: Boolean = false
    )

    val BODY_TYPES = listOf("역삼각형", "삼각형", "모래시계형", "직사각형")

    fun guide(bodyType: String?, gender: Gender?): Guide? = when (bodyType) {
        "역삼각형" -> if (gender == Gender.FEMALE) Guide(
            "역삼각형", "어깨가 힙보다 넓어요. 하체에 볼륨을 더하고 어깨선은 분산하면 균형이 맞아요.",
            listOf(
                Tip("와이드 · 플레어 하의", "하체에 볼륨을 더해 어깨와 균형을 맞춰요", "와이드팬츠 코디"),
                Tip("V넥 상의", "세로선으로 어깨 너비를 분산해요", "브이넥 니트 코디"),
                Tip("A라인 스커트", "허리 아래로 퍼지는 라인이 하체를 채워 줘요", "A라인 스커트 코디")
            ),
            listOf("퍼프 소매 · 어깨 패드 상의", "보트넥처럼 어깨가 넓어 보이는 넥라인", "스키니 팬츠"),
            goodWords = listOf("와이드", "플레어", "A라인", "브이넥", "V넥", "롱스커트", "가디건"),
            avoidWords = listOf("퍼프", "패드", "보트넥", "오프숄더", "스키니")
        ) else Guide(
            "역삼각형", "어깨가 힙보다 넓어요. 하체에 볼륨을 더하고 상의는 단정하게 하면 균형이 맞아요.",
            listOf(
                Tip("와이드 · 스트레이트 팬츠", "하체에 볼륨을 더해 넓은 어깨와 균형을 맞춰요", "와이드팬츠 코디"),
                Tip("V넥 · 라운드 니트", "어깨선을 강조하지 않는 부드러운 상의", "브이넥 니트 코디"),
                Tip("어두운 상의 + 밝은 하의", "시선을 아래로 내려 상하체 비율을 맞춰요", "어두운 상의 밝은 바지 코디")
            ),
            listOf("어깨 패드 · 견장 있는 자켓", "가로 스트라이프 상의", "스키니 팬츠"),
            goodWords = listOf("와이드", "스트레이트", "카고", "브이넥", "V넥", "니트", "면바지"),
            avoidWords = listOf("패드", "견장", "스트라이프", "스키니", "머슬")
        )
        "삼각형" -> if (gender == Gender.FEMALE) Guide(
            "삼각형", "힙이 어깨보다 넓어요. 상체에 시선을 모으고 하체는 깔끔하게 정리하면 좋아요.",
            listOf(
                Tip("어깨 볼륨 상의", "퍼프 소매 · 보트넥으로 어깨를 넓혀 균형을 맞춰요", "퍼프 블라우스 코디"),
                Tip("A라인 스커트", "엉덩이에서 자연스럽게 떨어져 하체를 커버해요", "A라인 스커트 코디"),
                Tip("밝은 상의 + 어두운 하의", "시선을 위로 올려 줘요", "밝은 상의 블랙 팬츠 코디")
            ),
            listOf("스키니 · 레깅스", "엉덩이에 딱 붙는 미니스커트", "밝은 색 하의"),
            goodWords = listOf("퍼프", "보트넥", "A라인", "블라우스", "자켓", "와이드"),
            avoidWords = listOf("스키니", "레깅스", "미니스커트"),
            darkBottom = true, brightTop = true
        ) else Guide(
            "삼각형", "힙이 어깨보다 넓어요. 구조감 있는 상의로 어깨를 보강하고 하의는 어둡게 정리하면 좋아요.",
            listOf(
                Tip("구조감 있는 자켓 · 셔츠", "어깨선이 잡힌 아우터로 상체를 넓혀요", "남자 자켓 셔츠 코디"),
                Tip("스트레이트 · 세미와이드 팬츠", "엉덩이에서 일자로 떨어지는 바지", "세미와이드 슬랙스 코디"),
                Tip("밝은 상의 + 어두운 하의", "시선을 위로 올려 줘요", "밝은 상의 검정 바지 코디")
            ),
            listOf("스키니 팬츠", "밝은 색 하의", "엉덩이 바로 위에서 끊기는 짧은 상의"),
            goodWords = listOf("자켓", "셔츠", "블레이저", "스트레이트", "세미와이드", "슬랙스", "코트"),
            avoidWords = listOf("스키니", "크롭"),
            darkBottom = true, brightTop = true
        )
        "모래시계형" -> if (gender == Gender.MALE) Guide(
            "모래시계형", "어깨와 힙이 비슷하고 허리가 들어가요. 몸선을 살리는 핏이 잘 어울려요.",
            listOf(
                Tip("레귤러 · 슬림핏 셔츠", "허리선이 자연스럽게 드러나는 핏", "슬림핏 셔츠 코디"),
                Tip("테이퍼드 슬랙스", "허리는 맞고 발목으로 좁아지는 바지", "테이퍼드 슬랙스 코디"),
                Tip("몸에 맞는 니트", "균형 잡힌 실루엣을 그대로 보여 줘요", "남자 니트 코디")
            ),
            listOf("허리선이 사라지는 지나친 오버핏", "위아래 모두 박시한 조합"),
            goodWords = listOf("셔츠", "슬랙스", "니트", "폴로", "테이퍼드", "슬림"),
            avoidWords = listOf("오버핏", "박시")
        ) else Guide(
            "모래시계형", "어깨와 힙이 비슷하고 허리가 들어가요. 허리선을 살리면 가장 잘 어울려요.",
            listOf(
                Tip("허리 강조 원피스", "허리 라인을 드러내는 랩 · 벨트 원피스", "랩원피스 코디"),
                Tip("하이웨이스트 하의", "허리선을 높여 비율을 살려요", "하이웨이스트 팬츠 코디"),
                Tip("벨트 포인트", "박시한 옷도 벨트로 허리를 잡아 줘요", "벨트 코디")
            ),
            listOf("허리 라인이 없는 박시한 원피스", "위아래 모두 오버사이즈인 조합"),
            goodWords = listOf("원피스", "하이웨이스트", "벨트", "랩", "니트", "슬랙스"),
            avoidWords = listOf("오버핏", "박시")
        )
        "직사각형" -> if (gender == Gender.FEMALE) Guide(
            "직사각형", "어깨 · 허리 · 힙 너비가 비슷해요. 허리선을 만들고 레이어드로 입체감을 주면 좋아요.",
            listOf(
                Tip("벨트로 허리선 만들기", "일자 실루엣에 곡선을 더해요", "벨트 원피스 코디"),
                Tip("페플럼 · 랩 블라우스", "허리 아래가 퍼지며 굴곡을 만들어요", "랩 블라우스 코디"),
                Tip("레이어드", "셔츠 + 니트 · 가디건으로 입체감", "레이어드 코디")
            ),
            listOf("위아래 같은 일자 핏 단색", "허리선 없는 박시 원피스"),
            goodWords = listOf("벨트", "랩", "페플럼", "가디건", "자켓", "니트", "셔츠"),
            avoidWords = listOf("박시")
        ) else Guide(
            "직사각형", "어깨 · 허리 · 힙 너비가 비슷해요. 레이어드와 실루엣 있는 하의로 입체감을 주면 좋아요.",
            listOf(
                Tip("레이어드", "셔츠 위 니트 · 자켓으로 상체에 입체감", "남자 레이어드 코디"),
                Tip("카고 · 와이드 팬츠", "하체 실루엣으로 밋밋함을 덜어요", "카고팬츠 코디"),
                Tip("포인트 컬러 · 패턴", "단조로운 실루엣에 시선을 줄 포인트", "포인트 컬러 코디")
            ),
            listOf("위아래 같은 일자 핏 단색 조합", "몸에 딱 붙는 스키니 셋업"),
            goodWords = listOf("자켓", "가디건", "니트", "셔츠", "카고", "와이드", "체크", "스트라이프"),
            avoidWords = listOf("스키니")
        )
        else -> null
    }

    /** 옷 하나가 제안에 맞는지: 1 = 잘 맞음, -1 = 피하면 좋음, 0 = 보통 */
    fun fit(item: WardrobeItem, g: Guide): Int {
        val text = listOfNotNull(item.name, item.detailType, item.subCategory).joinToString(" ")
        val bright = WeatherRules.isBright(item)
        if (g.avoidWords.any { it in text }) return -1
        if (g.darkBottom && item.category == "하의" && bright) return -1
        if (g.goodWords.any { it in text }) return 1
        if (g.darkBottom && item.category == "하의" && !bright) return 1
        if (g.brightTop && item.category == "상의" && bright) return 1
        return 0
    }

    /** 내 옷장에서 잘 맞는 옷 · 피하면 좋은 옷 (성별에 맞지 않는 옷은 뺌) */
    fun sort(items: List<WardrobeItem>, g: Guide, gender: Gender?): Pair<List<WardrobeItem>, List<WardrobeItem>> {
        val pool = GenderFit.filter(items.filter { it.isOwned }, gender)
        return pool.filter { fit(it, g) > 0 } to pool.filter { fit(it, g) < 0 }
    }
}

package com.jeiu.fashionproject.recommend

import com.jeiu.fashionproject.data.BodyAnalysisPersistence
import com.jeiu.fashionproject.data.Gender
import com.jeiu.fashionproject.data.WardrobeItem
import com.jeiu.fashionproject.ui.viewmodel.BodyAnalysisUiState
import com.jeiu.fashionproject.ui.viewmodel.BodyType

data class BodyStyleTip(val title: String, val description: String)

data class BodyStyleSuggestion(
    val bodyType: BodyType?,
    val explanation: String,
    val genderNotice: String,
    val tips: List<BodyStyleTip>,
    val considerations: List<String>,
    val wardrobeReasons: List<String>
) {
    val personalized: Boolean get() = bodyType != null
}

/** Read-only silhouette styling, not sizing or fit validation. No saved-profile/network fallback. */
object BodyStyleSuggestions {
    fun create(
        state: BodyAnalysisUiState,
        gender: Gender?,
        wardrobe: List<WardrobeItem>
    ): BodyStyleSuggestion {
        val result = (state as? BodyAnalysisUiState.Success)?.result
        val supportedType = result?.let { value -> BodyType.entries.find { it.label == value.bodyTypeKo } }
        val type = supportedType?.takeIf { result != null && BodyAnalysisPersistence.mayStore(result) }
        val explanation = when {
            type != null -> "이번 사진의 ${type.label} 추정 분류를 바탕으로 한 규칙 기반 스타일 제안이에요."
            state is BodyAnalysisUiState.Partial -> "부분 분석에서는 체형별 제안을 보류해요. ${state.message}"
            state is BodyAnalysisUiState.Analyzing -> "분석 중이에요. 유효한 결과가 나오기 전에는 일반 예시만 표시해요."
            state is BodyAnalysisUiState.Error -> "분석이 완료되지 않아 개인화하지 않았어요. ${state.message}"
            result != null && supportedType == null -> "지원하지 않는 체형 분류여서 개인화하지 않았어요."
            result != null -> "분석값의 품질·일관성을 확인할 수 없어 체형별 제안을 보류해요."
            else -> "유효한 현재 분석 결과가 없어 일반 스타일 예시를 표시해요."
        }
        val tips = when (type) {
            BodyType.INVERTED_TRIANGLE -> listOf(
                BodyStyleTip("V넥 또는 열린 셔츠", "상체의 세로선을 표현하고 싶다면 열린 목선의 상의를 시도해보세요."),
                BodyStyleTip("여유 있는 하의", "상하체 실루엣의 균형을 원한다면 와이드 팬츠처럼 밑단에 여유가 있는 하의를 비교해보세요."),
                BodyStyleTip("간결한 어깨선", "어깨 장식이 적은 상의와 하의의 볼륨을 조합해보세요.")
            )
            BodyType.TRIANGLE -> listOf(
                BodyStyleTip("셔츠·재킷의 어깨선", "상체에 시선을 더하고 싶다면 형태가 잡힌 셔츠나 재킷을 비교해보세요."),
                BodyStyleTip("상의 포인트", "밝은 색이나 무늬를 상의에 배치해 시선을 조절해보세요."),
                BodyStyleTip("스트레이트 하의", "아래로 자연스럽게 떨어지는 팬츠의 실루엣을 비교해보세요.")
            )
            BodyType.HOURGLASS -> listOf(
                BodyStyleTip("허리선이 보이는 조합", "허리선을 드러내고 싶다면 상의를 넣어 입거나 길이를 조절해보세요."),
                BodyStyleTip("벨트로 포인트", "편안함을 해치지 않는 선에서 벨트로 허리 위치를 표현해보세요."),
                BodyStyleTip("상하체 볼륨 균형", "한쪽에만 큰 볼륨을 더하기보다 상의와 하의의 여유를 함께 비교해보세요.")
            )
            BodyType.RECTANGLE -> listOf(
                BodyStyleTip("가벼운 레이어드", "셔츠와 가디건처럼 겹치는 길이를 달리해 입체감을 표현해보세요."),
                BodyStyleTip("허리 위치 표현", "상의 길이나 벨트 위치를 바꿔 원하는 실루엣을 찾아보세요."),
                BodyStyleTip("소재 대비", "상의와 하의의 소재·질감을 달리해 시선을 나누어보세요.")
            )
            null -> listOf(
                BodyStyleTip("편안한 기본 조합", "자주 입는 상의와 하의를 먼저 조합하고 움직임이 편한지 확인하세요."),
                BodyStyleTip("색상·소재 비교", "선호하는 색과 소재를 기준으로 여러 조합을 비교해보세요."),
                BodyStyleTip("상품 실측 확인", "사이즈는 브랜드별 실측표와 직접 입력한 신체 치수를 별도로 비교하세요.")
            )
        }
        val keywords = when (type) {
            BodyType.INVERTED_TRIANGLE -> listOf("V넥", "브이넥", "와이드")
            BodyType.TRIANGLE -> listOf("셔츠", "재킷", "자켓", "스트레이트")
            BodyType.HOURGLASS -> listOf("벨트", "하이웨이스트")
            BodyType.RECTANGLE -> listOf("가디건", "셔츠", "베스트")
            null -> emptyList()
        }
        val wardrobeReasons = wardrobe.asSequence()
            .filter { it.isOwned && !it.imageUri.startsWith("android.resource://") }
            .mapNotNull { item ->
                val labels = listOfNotNull(item.name, item.subCategory, item.detailType).joinToString(" ")
                keywords.firstOrNull { labels.contains(it, ignoreCase = true) }?.let { keyword ->
                    "등록된 ‘${item.name}’의 이름·분류에 ‘$keyword’가 있어 위 스타일을 비교할 후보예요. 실제 핏은 확인하지 않았어요."
                }
            }.take(3).toList()
        return BodyStyleSuggestion(
            type, explanation,
            if (gender == null) "성별 미선택 · 성별을 추정하지 않는 중립적인 스타일 안내예요."
            else "선택한 성별은 ${gender.label}이에요. 의류 선택은 성별로 제한하지 않고 취향을 존중해요.",
            tips,
            listOf("사진의 정면 너비 추정은 실제 신체둘레가 아니에요.",
                "스타일 규칙은 선택을 돕는 예시이며 실측 핏 보장이나 AI 착장 생성 결과가 아니에요."),
            wardrobeReasons
        )
    }
}

package com.jeiu.fashionproject.data

/**
 * 사용자가 마이페이지에서 선택하는 성별. 상품/코디 추천 필터링 기준으로 쓰인다.
 * null로 선택 안 한 상태를 표현할 수 있게, 뷰모델 쪽은 Gender? 로 다룬다.
 */
enum class Gender(val label: String) {
    FEMALE("여성"),
    MALE("남성")
}

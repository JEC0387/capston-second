> 2026-10-18: 이 병합본을 기준으로 체형분석 안정화 및 실제 Android build/test/lint를 수행했다. [BODY_ANALYSIS_FIX_REPORT.md](BODY_ANALYSIS_FIX_REPORT.md)의 최신 결과를 따른다. 실기기·외부 API 실행·실제 사람 정확도는 미검증이다. 아래 내용은 원래 병합 시점의 메모다.

# 손유리 작업 병합 메모 (son-yuri/merge)

`feature/body-analysis-camera`(10/13 통합본) 위에 `son-yuri/frontend`(손유리 10/06·10/13 작업)를 합친 브랜치입니다.
**아직 Android Studio 빌드·실행 확인 전**이라 main에 합치기 전에 꼭 빌드해 주세요.

## 새로 들어온 것
- 네이버 API HUB 연동: 이미지 검색(`NaverImageSearch*`), 쇼핑인사이트(`ShoppingInsight*`), `NaverApiHubClient`
- 성별 설정: `Gender`, `UserPreferencesViewModel`, 마이페이지 "성별" 선택 → 홈 · 쇼핑 · 날씨 코디 추천에 반영
- 홈: 날씨가 20°C 미만이면 "요즘 날씨 인기 스타일"을 네이버 이미지 검색으로 표시, 전체보기 → 쇼핑 탭 검색으로 이어짐
- 쇼핑 탭: **추천 상품**(기존 ShopCatalog · 관심 하트 → 옷장 '관심 옷') / **스타일 검색**(네이버 이미지) 두 보기로 나눔
- **무신사 랭킹 옷장**(`MusinsaClosetScreen`, 새 화면): 10/13 무신사 랭킹 상품으로 만든 성별 맞춤 데모 옷장 + 날씨 코디 + FASHN 가상 피팅. 옷장 탭 상단 카드에서 들어감
- 날씨: 한 번 받은 날씨는 다시 조회하지 않음(`fetchWeather(force = true)`로 강제 새로고침)

## 팀 버전을 그대로 둔 것
- 옷장 탭(`WardrobeScreen`): 황상윤 버전(WardrobeStore · 착용 기록 · 관심 옷) 유지. 무신사 옷장은 별도 화면으로 분리
- 카메라(`CameraPreviewScreen`): 이지민 버전(촬영 가이드 · 자동 촬영) 유지. 손유리의 **전면/후면 카메라 전환 버튼은 빠짐** → 필요하면 이지민 코드에 다시 추가
- 체형 분석(`BodyAnalysisViewModel`): 팀 버전 유지 (손유리의 옛 판정 기준 수정은 팀 버전에 이미 반영된 내용)
- CameraX 버전 1.3.4 유지 (손유리 쪽 1.4.2로 올리지 않음)

## local.properties에 추가할 키 (Git에 올리지 말 것)
```
NAVER_API_KEY_ID=...
NAVER_API_KEY=...
FASHN_API_KEY=...   # 없으면 '입어보기' 버튼이 숨겨짐
```

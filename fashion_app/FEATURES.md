# 추가된 기능 (보고서 기능 구현)

5·6주차 보고서에서 정의한 핵심 기능 중 미구현이던 3가지를 프론트엔드에 추가하고,
FastAPI 백엔드(YOLOv8) 연동을 붙였습니다.

## 1. AI 옷 등록 — 원터치 가상 옷장 구축
- 진입: 옷장 탭 하단 **[AI 등록]** 버튼
- 사진 선택 → 백엔드 YOLOv8 객체 탐지 + 중앙 영역 대표 색상 추출 → 이름/카테고리/색상 자동 태깅
- 서버 미연결 시 색상 기반 로컬 태깅으로 폴백 (오프라인에서도 동작)
- 등록한 옷은 SharedPreferences에 영속화되어 앱 재시작 후에도 옷장에 유지
- 관련 파일: `data/BackendApi.kt`, `data/WardrobeStore.kt`,
  `ui/viewmodel/AddClothingViewModel.kt`, `ui/screens/AddClothingScreen.kt`

## 2. 스타일 취향 학습 — Style Preference Learning
- 진입: 마이페이지 → **스타일 취향 학습**
- 코디 카드를 좋아요(+2)/싫어요(−1)로 평가 → 5개 스타일(미니멀·캐주얼·클래식·스트릿·빈티지) 점수 실시간 갱신
- 평가 누적 횟수와 선호도 순위 막대가 함께 표시되어 "평가할수록 정확해지는" 구조를 시연 가능
- 관련 파일: `data/StylePreferenceStore.kt`, `ui/screens/StylePreferenceScreen.kt`

## 3. 가상 피팅 — 착용 이미지 미리보기 (1차 프로토타입)
- 진입: 옷장 탭 **[가상 피팅]** 또는 마이페이지 → 가상 피팅
- 전신 사진 위에 옷 이미지를 오버레이하고 드래그(이동)·핀치(크기)·투명도 슬라이더로 착용감 미리보기
- 내 옷장에 등록한 옷 + 샘플 의류 중에서 선택 가능
- 관련 파일: `ui/screens/VirtualFittingScreen.kt`

## 백엔드 연동 방법
1. `fashion_backend_with_detection`의 backend 폴더에서 서버 실행:
   ```
   python -m uvicorn main:app --host 0.0.0.0 --port 8000
   ```
2. 에뮬레이터는 기본값(`http://10.0.2.2:8000`) 그대로 동작
3. 실기기 테스트 시 `data/BackendApi.kt`의 `BASE_URL`을 PC 내부 IP로 변경
   (Manifest에 `usesCleartextTraffic="true"` 적용됨 — 로컬 HTTP 테스트용)

## 수정된 기존 파일
- `navigation/Screen.kt`, `navigation/AppNavigation.kt` — 라우트 3개 추가
- `ui/screens/WardrobeScreen.kt` — AI 등록/가상 피팅 버튼, 등록 의류 병합 표시
- `ui/screens/MyPageScreen.kt` — 취향 학습/가상 피팅 메뉴 추가
- `AndroidManifest.xml` — usesCleartextTraffic 추가

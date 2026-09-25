# 체형 분석 정확도 개선 (MediaPipe 세그멘테이션)

## 무엇이 바뀌었나
기존: ML Kit 포즈 관절점만으로 어깨·힙을 재고 허리는 추정 → 모래시계형/직사각형 구분이 부정확
개선: **ML Kit 포즈(부위 높이) + MediaPipe 세그멘테이션(실제 가로폭)** 결합
- 포즈가 어깨/허리/힙의 '높이 위치'를 잡음
- 세그멘테이션 마스크에서 그 높이의 **실제 몸 폭(픽셀)**을 측정 → 허리까지 실측
- 결과적으로 체형 4종 분류 정확도 향상, 신뢰도 표시에 가산점

## ⚠️ 모델 파일 직접 추가 필요 (1개)
빌드 전에 아래 모델을 다운로드해 assets 폴더에 넣어주세요.

1) 다운로드:
   https://storage.googleapis.com/mediapipe-models/image_segmenter/selfie_segmenter/float16/latest/selfie_segmenter.tflite

2) 배치 위치 (파일명 그대로):
   app/src/main/assets/selfie_segmenter.tflite

3) 같은 폴더의 PUT_MODEL_HERE.txt 는 넣은 뒤 삭제해도 됩니다.

## 모델이 없으면?
앱은 크래시 없이 자동으로 기존 '포즈 기반 추정'으로 동작합니다.
(세그멘테이션 초기화 실패 시 isAvailable=false → 폴백)

## 동작 흐름
사진 → 포즈 검증(전신/정면) → 비트맵 로딩 → 세그멘테이션으로 어깨·허리·힙 실측
→ 어깨:힙 비율 + 허리 잘록도로 체형 분류 → 신뢰도와 함께 표시

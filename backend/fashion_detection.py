"""
옷 종류 분류 모듈 (계획 3 — 옷장, 3단계).

두 모델을 함께 실행하고 결과를 합친다.
  - DeepFashion2 YOLOv8-seg : 옷 13종류 + 옷 윤곽(마스크)
      short_sleeved_shirt, long_sleeved_shirt, short_sleeved_outwear, long_sleeved_outwear,
      vest, sling, shorts, trousers, skirt,
      short_sleeved_dress, long_sleeved_dress, vest_dress, sling_dress
  - COCO YOLOv8 (기존 detection.py) : DeepFashion2에 없는 가방류만 골라 사용

후보는 크게 잡힌 순서로 정렬해 반환한다. (앱은 첫 번째 후보를 기본 선택)
마스크는 앱이 "옷만 오려내기"에 쓰도록 흑백 PNG(base64)로 보낸다.

모델 파일: models/deepfashion2_yolov8s-seg.pt  (없으면 처음 쓸 때 허깅페이스에서 자동 다운로드, README.md 참고)
"""
import base64
from functools import lru_cache
from pathlib import Path
from typing import Any, Dict, List, Optional

import cv2
import numpy as np
from PIL import Image

import detection

DF2_MODEL_PATH = Path(__file__).parent / "models" / "deepfashion2_yolov8s-seg.pt"
# 공개 가중치 (Bingsu/adetailer, 저장소 라이선스 Apache-2.0 / 학습 데이터 DeepFashion2는 비상업 연구용)
DF2_MODEL_URL = "https://huggingface.co/Bingsu/adetailer/resolve/main/deepfashion2_yolov8s-seg.pt"
BAG_LABELS = {"handbag", "backpack", "suitcase"}


def _download_df2_model() -> None:
    """모델 파일이 없으면 허깅페이스에서 내려받는다 (약 24MB, 처음 한 번만).

    임시 파일로 받은 뒤 이름을 바꿔, 받다가 끊겨도 깨진 파일이 남지 않게 한다.
    """
    import urllib.request

    DF2_MODEL_PATH.parent.mkdir(parents=True, exist_ok=True)
    tmp = DF2_MODEL_PATH.with_suffix(".part")
    print(f"[fashion_detection] DeepFashion2 모델 내려받는 중… ({DF2_MODEL_URL})")
    try:
        urllib.request.urlretrieve(DF2_MODEL_URL, tmp)
        tmp.replace(DF2_MODEL_PATH)
    finally:
        tmp.unlink(missing_ok=True)
    print(f"[fashion_detection] 모델 저장 완료: {DF2_MODEL_PATH}")


@lru_cache(maxsize=1)
def load_df2_model():
    from ultralytics import YOLO  # 무거운 import는 최초 호출 시점으로 지연

    if not DF2_MODEL_PATH.exists():
        try:
            _download_df2_model()
        except Exception as e:
            raise FileNotFoundError(
                f"DeepFashion2 모델 파일이 없고 자동 다운로드에도 실패했습니다: {e} (backend/README.md 참고)"
            ) from e
    return YOLO(str(DF2_MODEL_PATH))


def _mask_to_png_base64(mask: np.ndarray) -> str:
    """0~1 마스크 → 흑백 PNG → base64 문자열."""
    img = (mask > 0.5).astype(np.uint8) * 255
    ok, buf = cv2.imencode(".png", img)
    if not ok:
        raise RuntimeError("마스크 인코딩 실패")
    return base64.b64encode(buf.tobytes()).decode("ascii")


def _run_deepfashion2(image_np: np.ndarray, conf: float) -> List[Dict[str, Any]]:
    model = load_df2_model()
    # retina_masks=True → 마스크를 입력 이미지와 같은 크기로 받는다
    result = model.predict(
        source=image_np, conf=conf, device="cpu", retina_masks=True, verbose=False
    )[0]
    h, w = image_np.shape[:2]
    candidates = []
    if result.boxes is None:
        return candidates

    masks = result.masks.data.cpu().numpy() if result.masks is not None else None
    for i, box in enumerate(result.boxes):
        x1, y1, x2, y2 = [int(v) for v in box.xyxy[0].tolist()]
        mask_b64: Optional[str] = None
        if masks is not None and i < len(masks):
            mask_b64 = _mask_to_png_base64(masks[i])
        candidates.append(
            {
                "label": model.names[int(box.cls[0].item())],
                "source": "deepfashion2",
                "confidence": round(float(box.conf[0].item()), 4),
                "bbox": [x1, y1, x2, y2],
                "area": round((x2 - x1) * (y2 - y1) / (w * h), 4),
                "mask": mask_b64,
            }
        )
    return candidates


def _run_coco_bags(image_np: np.ndarray, conf: float) -> List[Dict[str, Any]]:
    h, w = image_np.shape[:2]
    bags = []
    for det in detection.run_detection(image_np, conf_threshold=conf):
        if det["label"] not in BAG_LABELS:
            continue
        x1, y1, x2, y2 = det["bbox"]
        bags.append(
            {
                "label": det["label"],
                "source": "coco",
                "confidence": det["confidence"],
                "bbox": det["bbox"],
                "area": round((x2 - x1) * (y2 - y1) / (w * h), 4),
                "mask": None,
            }
        )
    return bags


def _iou(a: List[int], b: List[int]) -> float:
    ix = max(0, min(a[2], b[2]) - max(a[0], b[0]))
    iy = max(0, min(a[3], b[3]) - max(a[1], b[1]))
    inter = ix * iy
    union = (a[2] - a[0]) * (a[3] - a[1]) + (b[2] - b[0]) * (b[3] - b[1]) - inter
    return inter / union if union > 0 else 0.0


def _drop_duplicates(candidates: List[Dict[str, Any]], iou_limit: float = 0.7) -> List[Dict[str, Any]]:
    """같은 옷에 다른 이름이 겹쳐 잡힌 경우(예: 반팔 상의 / 반팔 아우터) 확신도 높은 쪽만 남긴다."""
    kept: List[Dict[str, Any]] = []
    for c in sorted(candidates, key=lambda c: c["confidence"], reverse=True):
        if all(_iou(c["bbox"], k["bbox"]) < iou_limit for k in kept):
            kept.append(c)
    return kept


def classify_clothing(image: Image.Image, conf_threshold: float = 0.3) -> Dict[str, Any]:
    """엔드포인트에서 호출하는 통합 함수."""
    image_np = detection.preprocess_image(image)
    candidates = _drop_duplicates(_run_deepfashion2(image_np, conf_threshold)) + _run_coco_bags(
        image_np, conf_threshold
    )
    candidates.sort(key=lambda c: c["area"], reverse=True)
    h, w = image_np.shape[:2]
    return {"imageWidth": w, "imageHeight": h, "candidates": candidates}

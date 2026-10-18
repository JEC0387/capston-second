"""
YOLOv8 객체 탐지 모듈.

object_detect 프로젝트(Streamlit 앱)의 탐지 로직을 백엔드용으로 이전한 것.
Streamlit 의존성을 제거하고, FastAPI 엔드포인트에서 호출할 수 있도록
순수 함수 형태로 구성했다.

흐름: 이미지 입력 → preprocess_image → run_detection → draw_boxes
"""
from collections import Counter
from functools import lru_cache
from typing import Any, Dict, List, Tuple

import cv2
import numpy as np
from PIL import Image

MODEL_PATH = "yolov8n.pt"  # 사전학습 YOLOv8 nano (최초 실행 시 자동 다운로드)


@lru_cache(maxsize=1)
def load_model(model_path: str = MODEL_PATH):
    """YOLOv8 모델을 한 번만 로드하고 캐싱한다.

    (기존 Streamlit 버전의 @st.cache_resource 역할을 lru_cache로 대체)
    """
    from ultralytics import YOLO  # 무거운 import는 최초 호출 시점으로 지연

    return YOLO(model_path)


def preprocess_image(image: Image.Image, max_size: int = 960) -> np.ndarray:
    """PIL 이미지를 RGB NumPy 배열로 변환하고, CPU 추론 속도를 위해 리사이즈."""
    if image.mode != "RGB":
        image = image.convert("RGB")

    img_np = np.array(image)
    height, width = img_np.shape[:2]
    scale = min(max_size / max(height, width), 1.0)

    if scale < 1.0:
        new_w = int(width * scale)
        new_h = int(height * scale)
        img_np = cv2.resize(img_np, (new_w, new_h), interpolation=cv2.INTER_AREA)

    return img_np


def run_detection(
    image_np: np.ndarray, conf_threshold: float = 0.25
) -> List[Dict[str, Any]]:
    """YOLOv8 추론(CPU)을 실행하고 탐지 결과 리스트를 반환."""
    model = load_model()
    results = model.predict(
        source=image_np,
        conf=conf_threshold,
        device="cpu",
        verbose=False,
    )

    result = results[0]
    detections: List[Dict[str, Any]] = []

    if result.boxes is not None:
        for box in result.boxes:
            cls_id = int(box.cls[0].item())
            conf = float(box.conf[0].item())
            x1, y1, x2, y2 = box.xyxy[0].tolist()
            label = model.names.get(cls_id, str(cls_id))

            detections.append(
                {
                    "label": label,
                    "confidence": round(conf, 4),
                    "bbox": [int(x1), int(y1), int(x2), int(y2)],
                }
            )

    return detections


def draw_boxes(image_np: np.ndarray, detections: List[Dict[str, Any]]) -> np.ndarray:
    """탐지 결과 바운딩 박스와 라벨을 이미지에 그린다 (RGB 기준)."""
    vis_img = image_np.copy()

    for det in detections:
        x1, y1, x2, y2 = det["bbox"]
        caption = f"{det['label']} {det['confidence']:.2f}"

        cv2.rectangle(vis_img, (x1, y1), (x2, y2), (0, 255, 0), 2)
        cv2.rectangle(vis_img, (x1, max(y1 - 24, 0)), (x2, y1), (0, 255, 0), -1)
        cv2.putText(
            vis_img,
            caption,
            (x1 + 4, max(y1 - 8, 12)),
            cv2.FONT_HERSHEY_SIMPLEX,
            0.5,
            (0, 0, 0),
            1,
            cv2.LINE_AA,
        )

    return vis_img


def detect_objects(
    image: Image.Image, conf_threshold: float = 0.25
) -> Tuple[List[Dict[str, Any]], Dict[str, int], np.ndarray]:
    """엔드포인트에서 호출하는 통합 함수.

    Returns:
        detections: 탐지 객체 리스트 (label / confidence / bbox)
        counts:     라벨별 개수 통계
        result_img: 바운딩 박스가 그려진 RGB 이미지 (NumPy)
    """
    image_np = preprocess_image(image)
    detections = run_detection(image_np, conf_threshold=conf_threshold)
    counts = dict(Counter(det["label"] for det in detections))
    result_img = draw_boxes(image_np, detections)
    return detections, counts, result_img

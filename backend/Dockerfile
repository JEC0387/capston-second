# Railway 배포용 서버 상자 설명서
# 내 PC 에서 시험:  docker build -t fashion-backend .
#                  docker run --rm -p 8000:8000 --env-file .env fashion-backend
FROM python:3.10-slim

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PIP_NO_CACHE_DIR=1

WORKDIR /app

# OpenCV 가 쓰는 리눅스 라이브러리 (ultralytics 가 일반 opencv 를 설치하는 경우 대비)
RUN apt-get update \
    && apt-get install -y --no-install-recommends libgl1 libglib2.0-0 \
    && rm -rf /var/lib/apt/lists/*

# PyTorch 는 CPU 전용으로 먼저 설치 (그래픽카드용은 몇 GB 라 상자가 너무 커짐)
# 기본 pip 가 오래돼 PyTorch 저장소 파일 이름을 못 읽으므로 먼저 새 버전으로 바꾼다
RUN pip install --upgrade pip \
    && pip install torch torchvision --index-url https://download.pytorch.org/whl/cpu

COPY requirements.txt .
RUN pip install -r requirements.txt

COPY . .

# AI 모델 2개를 상자에 미리 넣어 둔다 (서버를 다시 켤 때마다 내려받지 않도록)
RUN python -c "from ultralytics import YOLO; YOLO('yolov8n.pt')" \
    && python -c "import fashion_detection; fashion_detection._download_df2_model()"

# Railway 가 정해 주는 PORT 번호로 켠다 (없으면 8000)
CMD ["sh", "-c", "uvicorn main:app --host 0.0.0.0 --port ${PORT:-8000}"]

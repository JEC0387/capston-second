"""
네이버 이미지 검색 (NAVER API HUB · NCP) 대리 호출 모듈.

키를 앱(APK) 안에 넣지 않으려고, 앱 대신 서버가 키를 들고 네이버에 물어본다.
    앱 → 우리 서버(/api/naver/image-search) → 네이버
키는 환경변수 NAVER_API_KEY_ID · NAVER_API_KEY (Railway 는 Variables, 내 PC 는 backend/.env).
응답은 앱 NaverImageSearchRepository 의 ShopImageResult 와 같은 모양(title · imageUrl · thumbnailUrl).
"""
import json
import os
import re
import time
from datetime import date, timedelta
import urllib.error
import urllib.parse
import urllib.request

# 붙여 넣을 때 딸려 온 앞뒤 공백 · 줄바꿈은 지운다
NAVER_API_KEY_ID = os.environ.get("NAVER_API_KEY_ID", "").strip()
NAVER_API_KEY = os.environ.get("NAVER_API_KEY", "").strip()
IMAGE_SEARCH_URL = "https://naverapihub.apigw.ntruss.com/search/v1/image"

# 홈 화면은 코디 카드마다 같은 검색어로 사진을 찾으므로, 같은 검색어는 1시간 동안 다시 묻지 않는다 (호출 한도 절약)
CACHE_SECONDS = 3600
_cache: dict = {}
_insight_cache: tuple[float, dict] | None = None

_TAG = re.compile(r"<[^>]*>")


class NaverNotConfigured(Exception):
    """서버에 네이버 키가 없음"""


class NaverUnavailable(Exception):
    """네이버가 응답하지 않거나 오류를 돌려줌"""


def is_configured() -> bool:
    return bool(NAVER_API_KEY_ID and NAVER_API_KEY)


def search_images(query: str, display: int = 30) -> list[dict]:
    """이미지 검색 → [{title, imageUrl, thumbnailUrl}]. 검색어 강조용 <b> 태그는 지운다."""
    if not is_configured():
        raise NaverNotConfigured()
    key = (query, display)
    now = time.monotonic()
    hit = _cache.get(key)
    if hit and now - hit[0] < CACHE_SECONDS:
        return hit[1]

    params = urllib.parse.urlencode({
        "query": query, "display": display, "start": 1, "sort": "sim", "filter": "all",
    })
    req = urllib.request.Request(f"{IMAGE_SEARCH_URL}?{params}", headers={
        "X-NCP-APIGW-API-KEY-ID": NAVER_API_KEY_ID,
        "X-NCP-APIGW-API-KEY": NAVER_API_KEY,
    })
    try:
        with urllib.request.urlopen(req, timeout=8) as resp:
            body = json.loads(resp.read().decode("utf-8"))
    except (urllib.error.URLError, TimeoutError, ValueError) as e:
        # 키 · 응답 내용은 남기지 않고 오류 종류만 알린다
        raise NaverUnavailable(type(e).__name__) from None

    items = [
        {
            "title": _TAG.sub("", it.get("title", "")),
            "imageUrl": it.get("link", ""),
            "thumbnailUrl": it.get("thumbnail", ""),
        }
        for it in body.get("items", [])
    ]
    if len(_cache) > 500:
        _cache.clear()
    _cache[key] = (now, items)
    return items


def shopping_insight() -> dict:
    """Fixed clothing category trend query; credentials never leave this server."""
    global _insight_cache
    if not is_configured():
        raise NaverNotConfigured()
    now = time.monotonic()
    if _insight_cache and now - _insight_cache[0] < CACHE_SECONDS:
        return _insight_cache[1]
    end = date.today()
    request_body = {
        "startDate": (end - timedelta(days=365)).isoformat(),
        "endDate": end.isoformat(),
        "timeUnit": "month",
        "category": [{"name": "패션의류", "param": ["50000000"]}],
    }
    req = urllib.request.Request(
        "https://naverapihub.apigw.ntruss.com/shopping/v1/categories",
        data=json.dumps(request_body).encode("utf-8"),
        headers={
            "X-NCP-APIGW-API-KEY-ID": NAVER_API_KEY_ID,
            "X-NCP-APIGW-API-KEY": NAVER_API_KEY,
            "Content-Type": "application/json",
        },
    )
    try:
        with urllib.request.urlopen(req, timeout=8) as resp:
            raw = resp.read(512 * 1024 + 1)
        if len(raw) > 512 * 1024:
            raise ValueError("response_size")
        body = json.loads(raw)
        if not isinstance(body.get("results"), list):
            raise ValueError("response_format")
    except (urllib.error.URLError, TimeoutError, ValueError, TypeError) as e:
        raise NaverUnavailable(type(e).__name__) from None
    _insight_cache = (now, body)
    return body

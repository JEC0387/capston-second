"""
실시간 날씨 조회 모듈.

OpenWeatherMap API로 현재 날씨(온도·습도·상태)를 받아오고,
앱의 WeatherRepository.advice() 와 동일한 규칙으로 옷차림 조언을 만든다.
API 키가 없거나 호출이 실패하면 합리적인 기본값(맑음 22도)으로 폴백한다.
"""
import os
import urllib.request
import urllib.parse
import json

# 환경변수 OWM_API_KEY 로 키를 주입 (없으면 폴백 동작)
OWM_API_KEY = os.environ.get("OWM_API_KEY", "")
OWM_URL = "https://api.openweathermap.org/data/2.5/weather"

DEFAULT_LAT = 37.5665   # 서울시청
DEFAULT_LON = 126.9780


def fetch_weather(lat: float = DEFAULT_LAT, lon: float = DEFAULT_LON) -> dict:
    """현재 날씨를 조회해 {condition, temp, humidity, conditionId} 반환."""
    if OWM_API_KEY:
        try:
            params = urllib.parse.urlencode({
                "lat": lat, "lon": lon,
                "appid": OWM_API_KEY,
                "units": "metric", "lang": "kr",
            })
            with urllib.request.urlopen(f"{OWM_URL}?{params}", timeout=5) as resp:
                d = json.loads(resp.read().decode("utf-8"))
            return {
                "condition": d["weather"][0]["description"],
                "temp": round(d["main"]["temp"]),
                "humidity": d["main"]["humidity"],
                "conditionId": d["weather"][0]["id"],
            }
        except Exception:
            pass
    # 폴백: 온화한 기본 날씨
    return {"condition": "맑음", "temp": 22, "humidity": 50, "conditionId": 800}


def make_advice(temp: int, humidity: int, condition_id: int) -> str:
    """온도·습도·날씨 상태에 맞춘 옷차림 한 줄 조언 (앱 advice 로직과 동일)."""
    # 특수 기상 우선
    if 200 <= condition_id <= 299:
        return "천둥번개가 쳐요. 외출은 잠시 미루는 게 좋아요"
    if 600 <= condition_id <= 699:
        return ("눈이 와요. 두꺼운 패딩과 방한용품을 꼭 챙기세요" if temp <= 0
                else "눈이 내려요. 미끄럼 방지 신발과 따뜻한 코트를 추천해요")
    if 500 <= condition_id <= 599:
        return ("비가 와요. 우산과 통풍 잘되는 가벼운 옷을 챙기세요" if temp >= 23
                else "비가 와요. 우산과 방수 아우터를 챙기는 게 좋아요")
    if 300 <= condition_id <= 399:
        return "이슬비가 내려요. 가벼운 우산이나 후드를 챙겨보세요"
    if 700 <= condition_id <= 799:
        return "안개가 꼈어요. 밝은 색 옷으로 가시성을 높여보세요"

    # 온도 구간별 기본 코디
    if temp >= 30:
        base = "한여름 더위예요. 민소매나 린넨 소재로 시원하게 입어보세요"
    elif temp >= 27:
        base = "더운 날씨예요. 반소매 티셔츠와 반바지가 잘 어울려요"
    elif temp >= 23:
        base = "따뜻해요. 가벼운 반소매나 얇은 셔츠가 좋아요"
    elif temp >= 20:
        base = "선선해요. 얇은 가디건이나 가벼운 아우터를 걸치기 좋아요"
    elif temp >= 17:
        base = "활동하기 좋은 날씨예요. 긴소매 셔츠나 얇은 니트를 추천해요"
    elif temp >= 12:
        base = "쌀쌀해요. 자켓이나 트렌치코트가 필요한 날씨예요"
    elif temp >= 8:
        base = "추워요. 따뜻한 니트와 코트를 챙기세요"
    elif temp >= 2:
        base = "많이 추워요. 두꺼운 코트와 목도리를 추천해요"
    else:
        base = "한파예요. 패딩과 방한용품으로 단단히 챙겨 입으세요"

    # 습도 보정
    if temp >= 25 and humidity >= 70:
        note = " 습도가 높아 끈적해요. 통풍 잘되는 소재가 좋아요"
    elif temp >= 25 and humidity <= 30:
        note = " 건조하고 더우니 수분을 자주 보충하세요"
    elif temp <= 10 and humidity <= 30:
        note = " 공기가 건조하니 보습에 신경 쓰세요"
    elif humidity >= 80:
        note = " 습도가 높으니 가벼운 소재를 추천해요"
    else:
        note = ""

    return base + note

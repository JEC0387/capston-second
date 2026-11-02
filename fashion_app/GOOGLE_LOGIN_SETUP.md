# 구글 로그인 설정 (처음 한 번)

앱의 "Google 계정으로 계속하기" 버튼이 동작하려면 Google Cloud 콘솔에서 OAuth 클라이언트를 만들고,
그 ID를 앱과 서버에 넣어야 한다. 비밀번호 · 클라이언트 보안 비밀(secret)은 쓰지 않는다.

## 1. Google Cloud 콘솔 (https://console.cloud.google.com)

1. 프로젝트 만들기 (예: `fashion-ai`)
2. **API 및 서비스 → OAuth 동의 화면**: 앱 이름 `STYLE AI`, 사용자 유형 "외부", 테스트 사용자에 팀원 구글 계정 추가
3. **사용자 인증 정보 → 사용자 인증 정보 만들기 → OAuth 클라이언트 ID**를 두 종류 만든다
   - **웹 애플리케이션** 1개 → 만들어진 `xxxx.apps.googleusercontent.com` 이 **웹 클라이언트 ID**
   - **Android** (APK에 서명한 키마다 1개)
     - 패키지 이름: `com.jeiu.fashionproject`
     - SHA-1: 아래 명령으로 확인
       ```
       cd fashion_app
       ./gradlew signingReport        # Variant: debug 의 SHA1 값
       ```
     - GitHub Actions 가 만든 APK 를 설치해서 쓰면, 그 APK 의 서명 키(캐시된 debug.keystore) SHA-1 도 등록해야 한다.
       팀원마다 자기 PC 에서 빌드하면 팀원 PC 의 SHA-1 도 각각 등록한다.

## 2. 앱 — `fashion_app/local.properties` (Git 에 올리지 않음)

```
GOOGLE_WEB_CLIENT_ID=웹클라이언트ID.apps.googleusercontent.com
```

Android 클라이언트 ID 가 아니라 **웹** 클라이언트 ID 를 넣는다 (구글이 이 ID 앞으로 로그인 토큰을 만들어 준다).

## 3. 서버 — Railway Variables (내 PC 는 `backend/.env`)

```
GOOGLE_CLIENT_ID=웹클라이언트ID.apps.googleusercontent.com
```

그리고 DB 에 로그인 표를 만든다 (한 번만):

```
cd backend
python apply_migrations.py        # 005_user_logins.sql 적용
```

## 동작 방식

- 처음 로그인하는 구글 계정: 지금 폰에 있던 옷장 · 착용 기록 · 취향을 그 계정으로 이어 받는다.
- 다른 폰에서 같은 계정으로 로그인: 서버에 저장된 그 계정의 기록을 받아 오고 앱을 다시 시작한다.
- 로그아웃: 보내지 못한 기록을 서버로 보낸 뒤 이 폰의 기록을 지우고 로그인 화면으로 돌아간다.
- "로그인 없이 둘러보기"는 예전처럼 이 폰(기기 번호)에만 저장한다. 나중에 마이페이지에서 로그인하면 계정으로 옮겨진다.

## 안 될 때

| 화면에 나오는 말 | 원인 |
|---|---|
| 구글 로그인 설정이 아직 없어요 | 앱 `local.properties` 에 `GOOGLE_WEB_CLIENT_ID` 없음 → 넣고 다시 빌드 |
| 구글 로그인 창을 열지 못했어요 (…) | Android 클라이언트의 패키지 이름 · SHA-1 이 APK 서명과 다름, 또는 동의 화면 테스트 사용자에 없음 |
| 서버에 GOOGLE_CLIENT_ID 가 없어요 | Railway Variables 에 `GOOGLE_CLIENT_ID` 추가 후 재배포 |
| DB 에 로그인 표가 없어요 | `python apply_migrations.py` 실행 |
| 구글 로그인 정보를 확인하지 못했어요 | 앱과 서버의 웹 클라이언트 ID 가 서로 다름 |

# 로그인 설정 (처음 한 번) — Google · 카카오

Facebook · Apple 로그인은 넣지 않는다 (Apple 은 개발자 등록비 연 $99).
Google · 카카오는 무료다. 설정을 넣은 버튼만 켜지고, 설정이 없으면 버튼이 흐리게 보인다.

> 비밀 값(API 키 · 클라이언트 secret · DB 주소)은 GitHub · 카톡 단체방에 올리지 않는다.
> 앱 값은 `fashion_app/local.properties`, 서버 값은 Railway Variables(내 PC 는 `backend/.env`)에만 넣는다.

## 0. 서버 공통 — Railway Variables

```
SOCIAL_LOGIN_ENABLED=true
PUBLIC_BASE_URL=https://fashion-backend-production-aba0.up.railway.app
AUTH_REGISTRATION_ENABLED=true        # 앱이 Bearer 계정을 만들 수 있어야 로그인도 된다
```

`SOCIAL_LOGIN_ENABLED` 가 없거나 `false` 면 로그인 API 전부가 503 으로 꺼진다 (기존 동작).
DB 표(`user_logins`)는 `005_user_logins.sql` 로 이미 있으므로 새 마이그레이션은 없다.

# A. Google

## 1. Google Cloud 콘솔 (https://console.cloud.google.com)

1. 프로젝트 만들기 (예: `fashion-ai`)
2. **API 및 서비스 → OAuth 동의 화면**: 앱 이름 `STYLE AI`, 사용자 유형 "외부", 테스트 사용자에 팀원 구글 계정 추가
3. **사용자 인증 정보 → OAuth 클라이언트 ID** 를 두 종류 만든다
   - **웹 애플리케이션** 1개 → `xxxx.apps.googleusercontent.com` 이 **웹 클라이언트 ID**
   - **Android** (APK 에 서명한 키마다 1개) — 패키지 이름 `com.jeiu.fashionproject`, SHA-1 은
     `cd fashion_app && ./gradlew signingReport` 의 debug SHA1.
     GitHub Actions 가 만든 APK 를 쓰면 그 APK 서명 키의 SHA-1 도 등록한다.

## 2. 앱

웹 클라이언트 ID 는 `app/build.gradle.kts` 에 기본값으로 들어가 있다 (비밀 값 아님). 따로 넣을 것 없음.
다른 ID 로 시험할 때만 `fashion_app/local.properties` 에 `GOOGLE_WEB_CLIENT_ID=...` 를 넣어 덮어쓴다.

## 3. 서버 — Railway Variables

```
GOOGLE_CLIENT_ID=웹클라이언트ID.apps.googleusercontent.com
```

앱과 서버에 **같은 웹 클라이언트 ID** 를 넣는다. secret 은 쓰지 않는다.

# B. 카카오

## 1. Kakao Developers (https://developers.kakao.com)

1. 내 애플리케이션 → 애플리케이션 추가 (앱 이름 `STYLE AI`)
2. **[앱] > [플랫폼 키] > [REST API 키]**
   - REST API 키 값 복사
   - 리다이렉트 URI 등록: `https://fashion-backend-production-aba0.up.railway.app/api/auth/kakao/callback`
   - 클라이언트 시크릿: 기본으로 켜져 있다 → 값 복사 (끄지 않는다)
3. **[카카오 로그인]** 사용 설정 ON
4. **[카카오 로그인] > [동의항목]**: 닉네임(필수). 이메일은 비즈 앱 전환이 필요하므로 없어도 된다.

## 2. 서버 — Railway Variables

```
KAKAO_REST_API_KEY=REST API 키
KAKAO_CLIENT_SECRET=클라이언트 시크릿
```

앱에는 카카오 값을 넣지 않는다.

# 동작 방식

- 앱은 처음 켤 때 서버에서 Bearer 계정(토큰)을 받는다. 로그인은 그 계정에 구글 · 카카오 계정을 **연결**하는 것이다.
  기기 번호(device id)는 로그인에 쓰지 않는다.
- **처음 로그인**: 지금 폰의 옷장 · 기록이 그대로 그 구글/카카오 계정에 연결된다. 옮기거나 지우는 것은 없다.
- **다른 폰에서 같은 계정으로 로그인**: 서버에 있던 그 계정의 기록을 받아 오고 앱을 다시 연다.
  이때 그 계정의 토큰이 새로 바뀌므로 **예전 폰은 로그아웃**된다 (한 계정은 폰 한 대 — 복구 코드와 같은 규칙).
  새 폰에서 쓰던 기록은 서버의 원래 자리에 그대로 남는다.
- 이미 구글 A 로 로그인한 폰에서 구글 B 로 로그인하면 거절한다 (기록이 섞이지 않게).
- 예전 방식(기기 번호)으로 연결됐던 계정은 운영자 확인이 필요하다고 안내한다.
- 카카오: 브라우저에서 로그인 → `fashionai://login?code=...` 로 앱에 돌아온다. 이 코드는 로그인을 시작한 계정에서만,
  한 번만, 5분 안에만 쓸 수 있다.
- 로그아웃은 로그인 화면으로 돌아갈 뿐 기록을 지우지 않는다.

# 안 될 때

| 화면에 나오는 말 | 원인 |
|---|---|
| 버튼이 흐림 · "아직 설정되지 않았어요" | 서버 `SOCIAL_LOGIN_ENABLED` / `GOOGLE_CLIENT_ID` / `KAKAO_REST_API_KEY` 없음, 또는 앱 `GOOGLE_WEB_CLIENT_ID` 없음 |
| 서버에 연결하지 못했어요 | 서버 꺼짐, 또는 `AUTH_REGISTRATION_ENABLED` 가 꺼져 있어 앱 계정이 아직 없음 |
| 구글 로그인 창을 열지 못했어요 | Android 클라이언트의 패키지 이름 · SHA-1 이 APK 와 다름, 또는 테스트 사용자에 없음 |
| 로그인 정보를 확인하지 못했어요 | 앱과 서버의 웹 클라이언트 ID 가 다름 |
| 카카오 화면에서 "Redirect URI mismatch" | Kakao Developers 의 Redirect URI 와 `PUBLIC_BASE_URL` 이 다름 |
| 운영자 확인이 필요해요 | 예전 기기 번호 방식으로 연결된 계정 → `AUTH_MIGRATION.md` 의 운영자 승인 절차 |

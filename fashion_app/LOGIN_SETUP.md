# 로그인 설정 (처음 한 번) — Google · 카카오 · Facebook · Apple

각 로그인 버튼은 설정을 넣은 것만 켜진다. 설정이 없는 버튼은 흐리게 보이고 "아직 설정되지 않았어요"라고 안내한다.

# A. Google

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

# 공통 — 서버 주소 (카카오 · Facebook · Apple)

세 곳 모두 "로그인 뒤 돌아올 주소(Redirect URI)"를 등록해야 한다. Railway Variables 에 서버 주소를 넣는다.

```
PUBLIC_BASE_URL=https://fashion-backend-production-aba0.up.railway.app
```

등록할 Redirect URI (서버 주소 + 아래 경로)

| 로그인 | Redirect URI |
|---|---|
| 카카오 | `{PUBLIC_BASE_URL}/api/auth/kakao/callback` |
| Facebook | `{PUBLIC_BASE_URL}/api/auth/facebook/callback` |
| Apple | `{PUBLIC_BASE_URL}/api/auth/apple/callback` |

앱에는 아무것도 넣지 않는다. 비밀 값은 모두 서버(Railway Variables)에만 둔다.
로그인은 브라우저에서 하고, 끝나면 `fashionai://login` 주소로 앱이 다시 열린다.

# B. 카카오 (무료)

1. https://developers.kakao.com → 내 애플리케이션 → 애플리케이션 추가
2. **앱 키 → REST API 키** 복사
3. **카카오 로그인 → 활성화 ON**, Redirect URI 에 위 주소 등록
4. **카카오 로그인 → 동의항목**: 닉네임(필수), 카카오계정(이메일)(선택 — 비즈 앱 전환 전에는 선택 동의만 가능)
5. **플랫폼 → Web** 사이트 도메인에 `PUBLIC_BASE_URL` 등록
6. Railway Variables
   ```
   KAKAO_REST_API_KEY=REST API 키
   KAKAO_CLIENT_SECRET=  (카카오 로그인 > 보안 > Client Secret 을 '사용함'으로 했을 때만)
   ```

# C. Facebook (무료)

1. https://developers.facebook.com → 내 앱 → 앱 만들기 → "Facebook 로그인으로 사용자 인증" 사용 사례
2. **Facebook 로그인 → 설정 → 유효한 OAuth 리디렉션 URI** 에 위 주소 등록
3. **앱 설정 → 기본 설정**의 앱 ID · 앱 시크릿 코드
4. 개발 모드에서는 **앱 역할 → 역할**에 추가한 팀원 계정만 로그인된다 (모든 사람이 쓰려면 앱 검수 · 게시)
5. Railway Variables
   ```
   FACEBOOK_APP_ID=앱 ID
   FACEBOOK_APP_SECRET=앱 시크릿 코드
   ```

# D. Apple (유료 — Apple Developer Program 연 $99 가입 필요)

1. https://developer.apple.com/account → Certificates, Identifiers & Profiles
2. **Identifiers → App IDs** 하나 만들고 "Sign In with Apple" 체크
3. **Identifiers → Services IDs** 만들기 (예: `com.jeiu.fashionproject.signin`) → Sign In with Apple 설정에서
   Domains 에 서버 도메인(https 없이), Return URLs 에 위 Apple 주소 등록
4. **Keys** → 새 키, "Sign In with Apple" 체크 → `.p8` 파일 내려받기 (한 번만 받을 수 있음), Key ID 확인
5. 오른쪽 위 계정 이름 아래 **Team ID** 확인
6. Railway Variables
   ```
   APPLE_SERVICE_ID=com.jeiu.fashionproject.signin
   APPLE_TEAM_ID=팀 ID
   APPLE_KEY_ID=키 ID
   APPLE_PRIVATE_KEY=.p8 파일 내용 (줄바꿈은 \n 으로 이어서 한 줄)
   ```
   `.p8` 파일과 키 내용은 절대 Git 에 올리지 않는다.

# 확인

서버 주소 + `/api/auth/providers` 를 브라우저로 열면 켜진 로그인 방법이 보인다.
```
{"google": true, "kakao": true, "facebook": false, "apple": false}
```

| 화면에 나오는 말 | 원인 |
|---|---|
| 서버에 카카오(페이스북 · Apple) 로그인 설정이 없어요 | Railway Variables 에 해당 값 없음 → 넣고 재배포 |
| 로그인하지 못했어요 (브라우저 화면) | Redirect URI 가 각 회사에 등록한 값과 다름, 또는 앱 시크릿 · 키 값이 틀림 |
| 로그인 시간이 지났어요 | 브라우저에서 앱으로 돌아오기까지 5분이 넘음 → 다시 로그인 |
| Facebook 에서 "앱을 사용할 수 없음" | 개발 모드인데 그 계정이 앱 역할에 없음 |

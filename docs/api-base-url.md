# API 주소와 빌드 타입

앱이 우리 서버로 보내는 API 요청의 도착지는 `core/network` 의 `BuildConfig.BASE_URL` 하나로 정해진다. `NetworkModule` 이 만드는 Retrofit 다섯 개(일반·재발급·업로드·구조 감지·지원서 스트림)가 모두 이 값을 `baseUrl` 로 받는다. 앱이 보내는 HTTP 요청 전부는 아니다. 소셜 로그인 SDK(카카오·구글)와 Firebase(Crashlytics·FCM)는 각자 자기 서버로 가고 이 값과 무관하다.

## 어느 빌드가 어느 주소를 쓰나

| 빌드 타입 | 읽는 키 | 키가 없을 때 | 이 값으로 나가는 산출물 |
| --- | --- | --- | --- |
| `debug` | `BASE_URL_DEV` | 자리표시자 `https://api.careercompass.invalid/api/v1/` | 로컬 `assembleDebug`, 계측 테스트, 에뮬레이터·실기기 QA 빌드 |
| `release` | `BASE_URL_PROD` | 같은 자리표시자 | Firebase App Distribution APK, Play 내부 테스트 AAB |

값은 저장소에 두지 않는다. 루트 `local.properties`(gitignore)를 먼저 보고, 없으면 같은 이름의 환경변수를 본다. 사람마다 다른 개발 서버를 쓸 수 있고, 운영 주소가 커밋에 남지 않게 하려는 경로 제한이다. 소셜 로그인 키와 같은 규칙이고, 읽는 코드도 `build-logic` 의 `externalBuildValue` 하나를 함께 쓴다. release 서명 키는 `local.properties` 만 보므로 환경변수 폴백이 없다.

자리표시자의 `.invalid` 는 예약 TLD 라 어떤 DNS 로도 해석되지 않는다. 주소를 아직 넣지 않은 빌드는 남의 서버로 요청을 보내는 대신 연결 자체에 실패한다.

## 가드

빌드 설정이 두 가지를 빌드 시점에 끊는다. 배선은 서명 키 가드와 같다(`build-logic` 의 `registerVariantBuildGuard`). 서명 쪽이 `preReleaseBuild` 에 거는 자리에, 주소 쪽은 `preDebugBuild` 에 건다.

1. **debug 빌드가 운영 호스트를 가리키면 실패한다.** 운영 호스트는 그 빌드에 주입된 `BASE_URL_PROD` 의 호스트다. 경로가 달라도(`/api/v1/` 과 `/api/v2/`) 호스트가 같으면 같은 서버로 본다. 저장소는 운영 주소를 갖고 있지 않으므로, `BASE_URL_PROD` 를 어디에도 두지 않은 머신에서 `BASE_URL_DEV` 에만 운영 주소를 적으면 이 가드는 그것을 알 수 없다. 그 경우까지 막으려면 운영 키를 함께 두어야 한다.
2. **주입한 주소의 형식이 어긋나면 설정 단계에서 실패한다.** http 나 https 스킴과 호스트를 갖추고 `/` 로 끝나야 한다. Retrofit 이 `/` 로 끝나지 않는 `baseUrl` 을 런타임에 거부하기 때문에, 오타를 앱 첫 요청까지 끌고 가지 않는다.

가드가 실제로 막는지는 `BaseUrlGuardTest`(build-logic, Gradle TestKit)가 고정한다. 두 키에서 빌드 타입별 주소가 갈리는 것도 거기서 본다. 실제로 구워진 값이 Retrofit 이 받는 형태인지는 `BaseUrlBuildConfigTest`(core:network)가 확인한다. 이 저장소는 단위 테스트를 debug 변형에만 만들므로, 그 테스트가 보는 값은 `BASE_URL_DEV` 쪽이다.

## 실주소가 도착하면

백엔드 주소는 [CareerCompass-BE #7](https://github.com/Team-CareerCompass/CareerCompass-BE/issues/7) 이 준다. 도착하면 고칠 코드는 없고 값만 넣는다.

```properties
# 루트 local.properties
BASE_URL_DEV=https://<개발 서버 호스트>/api/v1/
BASE_URL_PROD=https://<운영 서버 호스트>/api/v1/
```

CI 는 같은 이름의 환경변수로 받는다. 배포 산출물을 만드는 워크플로에 `BASE_URL_PROD` 를 넣지 않으면 release 빌드는 자리표시자로 나가고, 그 APK 는 어떤 요청도 성공하지 못한다.

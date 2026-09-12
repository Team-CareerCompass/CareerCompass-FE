# Compose Preview Screenshot Testing (docker baseline)

`Compose Preview Screenshot Testing`의 anti-aliasing·font hinting·scale 등 host 환경 의존 렌더링 차이로 CI rendered PNG를 baseline으로 교체하는 ping-pong이 발생해 왔다. 본 저장소는 `Dockerfile.screenshot`을 공통 환경으로 사용하고 역할을 세 workflow로 나눈다.

- [Compose Preview Screenshot Test](../../.github/workflows/screenshot.yml): PR과 `develop` 합성 결과의 기존 baseline 검증
- [Generate Screenshot Baselines](../../.github/workflows/screenshot-baseline-generate.yml): 라벨이 붙은 PR의 정확한 head에서 baseline 생성·재검증 후 artifact 발행
- [Apply Screenshot Baselines](../../.github/workflows/screenshot-baseline-apply.yml): artifact와 head를 다시 검증한 뒤 허용된 PNG만 PR 브랜치에 적용

## 로컬 fallback 사전 준비

- Docker 호환 runtime 설치 (macOS 의 Colima/Docker Desktop 또는 Linux Docker)

## Actions 에서 baseline 갱신 (기본 경로)

1. 갱신할 PR에 `screenshot-baseline` 라벨을 붙인다.
2. 읽기 전용 [Generate Screenshot Baselines](../../.github/workflows/screenshot-baseline-generate.yml)가 PR의 정확한 head SHA를 CI 표준 Docker 이미지에서 렌더하고 재검증한다.
3. 생성 workflow는 라벨을 붙인 `pull_request` 권한 경계에서 실행되므로 PR 코드가 default branch cache를 오염시키지 않는다.
4. 별도 [Apply Screenshot Baselines](../../.github/workflows/screenshot-baseline-apply.yml)가 artifact에 PNG baseline 이외 변경이 없는지, 생성 대상과 현재 PR head가 같은지 checkout 없이 재검증한다.
5. 검증된 PNG만 PR 브랜치에 커밋하고 필수 검사를 다시 요청한다. 성공하면 라벨도 제거된다.

무엇을 캡처할지는 Action 이 화면을 탐색해서 추측하지 않는다. 각 모듈의
`src/screenshotTest/kotlin/**/*ScreenshotTest.kt` 가 Preview 함수, 상태와 device spec 을 선언하며,
Action 은 그 테스트 전체를 실행한다. 새 화면·새 상태를 추가하려면 먼저 screenshot test 를 추가한다.
생성된 이미지는 PR 의 PNG diff 에서 눈으로 최종 확인한다.

## 큰 글꼴(fontScale) 골든

라이트·다크만 덮으면 «시스템 글꼴을 키운 사용자» 는 아무도 검증하지 않는다. 한국어 UI 는 영어보다
줄바꿈 여지가 적고 preview 가 `heightDp` 를 고정해 두어, 잘림은 조용히 일어난다 — 실제로 온보딩
Step 4 의 「건너뛰기」는 **기본 배율에서도** 「건너뛰」로 잘려 있었고 아무 검사도 그것을 잡지 못했다.

### 배율은 2.0 하나

`@Preview(fontScale = 2.0f)` 를 쓴다. Android 14(API 34)가 글꼴 크기 설정의 상한을 200% 로 올렸고
(그 전 AOSP `config_fontScaleValues` 의 상한은 1.30) 그 위는 없다. 상한 한 자리를 지키면 1.15·1.30
같은 중간 단계는 같은 레이아웃의 덜 심한 경우라 골든을 따로 두지 않는다. 단말의 실제 배율 곡선은
비선형이라 큰 글자를 2.0 보다 덜 키우는데 preview 의 `fontScale` 은 선형으로 곱한다 — 이 골든이
단말보다 엄격하다.

각 모듈 `screenshotTest` 소스셋의 `LARGE_FONT_SCALE` 이 그 값이다. screenshotTest 소스셋은 모듈 밖으로
나가지 않아 모듈마다 같은 상수를 둔다.

### 무엇을 넣고 무엇을 뺐나

넣은 것 — 공유 부품 한 벌과, 부품 조합이 서로 다른 핵심 화면.

| 골든 | 넣은 이유 |
| --- | --- |
| `core:ui` Button / Text field / Badge·Tag·Score 매트릭스 | 여기서 깨지는 것은 모든 화면에서 깨진다. 화면 골든보다 먼저·싸게 잡힌다 |
| `core:ui` Icon set | 아이콘이 글꼴 배율을 **안** 따라간다는 근거. 32dp 글리프와 2배로 커진 라벨이 한 장에 있다 |
| `core:ui` Bottom bar · Top app bar(부제) | 글꼴을 따라 커지면 안 되는 자리(탭 글리프)와, 고정 높이가 가장 좁은 자리 |
| 로그인 · 온보딩 Step 1 | 앱의 첫 두 화면. 여기서 막히면 뒤가 없다. 폼은 라벨·입력·도움말이 겹겹이 쌓인다 |
| 피드 홈 · 공고 상세 · 게시판 목록 | 가장 오래 머무는 화면과, 칩·점수·긴 한국어 제목이 한 화면에 몰린 최악의 경우 |

뺀 것 — baseline 이 늘면 CI 시간이 늘어난다. 아래는 새 실패 유형을 만들지 않는다.

- **큰 글꼴 × 다크** — 배율은 배치를, 테마는 색을 바꾼다. 배치는 라이트 큰 글꼴 골든이 이미 못 박는다.
- **온보딩 Step 2~4·완료·모든 bottom sheet, editor·profile·foryou·notification 모듈** — 위 갤러리가
  2.0 에서 못 박은 core:ui 부품을 조합한다. 부품이 깨지면 core:ui 에서 먼저 걸린다.
- **고른 화면의 loading·empty·error 상태** — 글자 수가 loaded 보다 적다. loaded 가 최악의 경우다.

### 캔버스 높이

화면 골든은 단말 높이를 **그대로 둔다** — 큰 글꼴에서 화면 밖으로 밀리는 것 자체가 관측 대상이다.
갤러리 매트릭스만 큰 글꼴 변형의 `heightDp` 를 늘린다. 갤러리 캔버스는 단말이 아니라 «컴포넌트를
전부 담는 판» 이라, 높이를 그대로 두면 아래쪽 컴포넌트가 캔버스 밖으로 밀려 정작 봐야 할 것이
골든에 안 남는다.

## 로컬 baseline 갱신 (Actions 장애 시 fallback)

```bash
docker build --platform linux/amd64 -t careercompass-screenshot:latest -f Dockerfile.screenshot .
docker run --rm --platform linux/amd64 -v "$PWD":/workspace -w /workspace careercompass-screenshot:latest \
  ./gradlew :core:ui:updateScreenshotTest \
          :feature:onboarding:presentation:updateScreenshotTest \
          :feature:feed:presentation:updateScreenshotTest \
          :feature:editor:presentation:updateScreenshotTest \
          :feature:foryou:presentation:updateScreenshotTest \
          :feature:notification:presentation:updateScreenshotTest \
            --rerun
```

→ 변경된 PNG 가 각 모듈 `src/screenshotTestDebug/reference/...` 에 갱신. `git add` 후 commit.

> 실패한 모듈만 갱신하려면 그 모듈 태스크만 지정한다 — 예: `./gradlew :feature:feed:presentation:updateScreenshotTest`
>
> 대상 모듈 목록은 [검증](../../.github/workflows/screenshot.yml)·[생성](../../.github/workflows/screenshot-baseline-generate.yml) workflow와 [적용 workflow의 허용 경로](../../.github/workflows/screenshot-baseline-apply.yml)에 함께 들어 있다. 모듈을 추가·이전했다면 세 workflow와 `Dockerfile.screenshot`, 이 문서를 함께 갱신한다. 다섯 자리가 어긋나면 `.github/scripts/remote-dev-workflows-policy.test.mjs` 의 `screenshotModules` 대조가 실패한다.

## 로컬 baseline 검증 (CI 실패 재현)

```bash
docker run --rm --platform linux/amd64 -v "$PWD":/workspace -w /workspace careercompass-screenshot:latest \
  ./gradlew :core:ui:validateScreenshotTest \
          :feature:onboarding:presentation:validateScreenshotTest \
          :feature:feed:presentation:validateScreenshotTest \
          :feature:editor:presentation:validateScreenshotTest \
          :feature:foryou:presentation:validateScreenshotTest \
          :feature:notification:presentation:validateScreenshotTest
```

→ baseline 과 docker 환경에서 새로 그린 PNG 비교. 실패 시 `build/outputs/screenshotTest-results/preview/debug/diffs/` 에서 diff PNG 확인.

## 호스트 직접 실행은 사용하지 않음

`./gradlew :<module>:updateScreenshotTest` 를 host 에서 직접 실행하면 macOS / Linux / JDK 마이너 버전 / 폰트 캐시 차이로 CI 와 baseline 이 어긋난다. docker 환경 통일이 root fix.

# 비개발자 APK 배포 (Firebase App Distribution)

디자이너·PM·QA·외부 베타테스터에게 release APK 를 자동 배포하는 흐름. Firebase 프로젝트 `careercompass-fe` 의 테스터 그룹 `careercompass`(표시 이름 CareerCompass QA)로 나간다. 그룹 별칭은 `app/build.gradle.kts` 의 `firebaseAppDistribution { groups = ... }` 와 같은 값이어야 한다.

테스터를 넣으려면 `firebase appdistribution:testers:add <이메일> --project careercompass-fe` 로 프로젝트에 넣고 콘솔에서 그룹에 넣는다. 그룹에 아무도 없으면 업로드는 성공해도 아무에게도 도착하지 않는다.

자격이 지금 어디까지 채워져 있는지는 [`credentials.md`](credentials.md) 가 표로 관리한다.

## 운영 자격 인계 (로컬 fallback 담당자만)

일반 배포는 GitHub Actions가 담당하므로 모든 개발자가 이 설정을 가질 필요는 없다. CI 장애 때 로컬 fallback을 수행할 **배포 권한 보유자**만 다음을 준비한다.

1. **release keystore 인계.** 2026-09-06 에 이 프로젝트의 서명 키를 만들었다. 파일과 비밀번호는 1Password 개인 볼트에 있다(문서 `CareerCompass release keystore (jks)`, 항목 `CareerCompass release signing`). 인계받아 로컬에 두고 쓴다.

    **새로 만들지 않는다.** 다른 키로 서명한 앱은 기존 설치본을 업데이트할 수 없다. 자격을 찾지 못하면 배포를 멈추고 인계부터 받는다. (이 문서의 예전 판은 「기존 keystore 를 인계받으라」고만 적었는데, 그때 이 프로젝트에는 keystore 가 없었다. 다른 저장소에서 이식되며 따라온 문장이었다.)

    | 항목 | 값 |
    |---|---|
    | alias | `careercompass-release` |
    | 알고리즘 | RSA 4096, SHA384withRSA |
    | 유효기간 | 10,000일 (2026-09-06 발급) |
    | SHA-1 | `5A:DE:33:50:B2:F9:01:BA:68:9E:33:31:FB:66:64:1C:D8:9F:BE:01` |
    | SHA-256 | `51:08:56:1C:29:46:2D:91:A3:2F:23:9E:B4:19:BA:8F:2D:64:6B:9C:9A:7E:6A:36:BE:7A:BB:33:F1:36:13:01` |
    | 카카오 키 해시 | `Wt4zULL5AbponjMx+2ZkHNifvgE=` |

    지문은 비밀이 아니다. 카카오 개발자 콘솔의 키 해시와 Google OAuth 클라이언트에 이 값을 등록해야 release 빌드에서 소셜 로그인이 된다.

2. **`local.properties` 끝에 4개 키 추가** (signing config 가 읽음)

    ```properties
    RELEASE_STORE_FILE=/Users/<you>/careercompass-release.jks
    RELEASE_STORE_PASSWORD=<keystore 비밀번호>
    RELEASE_KEY_ALIAS=careercompass-release
    RELEASE_KEY_PASSWORD=<key 비밀번호>
    ```

3. **`google-services.json` 배치** — Firebase Console → 프로젝트 설정 → 일반 → Android 앱 `com.cambridge.careercompass_fe` 카드에서 다운로드 → `app/google-services.json`

4. **Firebase CLI 설치 + 인증** (로컬 fallback 업로드용)

    ```bash
    npm install -g firebase-tools
    firebase login
    ```

5. **기존 서명 ID 확인** — `./gradlew :app:signingReport`의 release SHA-1과 `keytool`로 구한 카카오 키 해시가 각 콘솔에 이미 등록된 값과 같은지 확인한다. 다른 값이면 새로 등록해 우회하지 말고 인계받은 keystore가 맞는지부터 확인한다.

## 배포 (매 회)

모든 배포의 릴리스 노트에는 `포함 이슈`와 `QA 포인트`가 필요하다. 둘 중 하나라도 비어 있거나 포함 이슈에 `#123` 형식의 번호가 없으면 Firebase 업로드 전에 실패한다.

### 배포 판단 기준

배포 시점은 일 단위 주기가 아니라 `develop`에 머지된 변경 묶음의 크기와 위험도로 정한다.

- 인증·온보딩·데이터 손실·API 계약·빌드/서명처럼 영향이 큰 변경은 다른 변경을 기다리지 않고 단독 배포한다.
- 작은 변경은 하나의 QA 세션에서 회귀 원인을 구분할 수 있는 범위까지만 묶는다. 서로 다른 사용자 흐름을 한꺼번에 확인해야 하거나 함께 롤백하기 어려워지는 시점이 배포 경계다.
- 수정 결과를 테스터가 확인해야 하는 결함이 머지되면, 묶음 크기와 관계없이 확인 가능한 빌드를 배포한다.
- 현재 묶음의 모든 QA 포인트가 통과한 뒤 `develop`을 `main`으로 승격한다.

### 릴리스 PR 범위 자동 산출

배포 시점은 위 기준에 따라 사람이 정한다 — `develop` → `main` 릴리스 PR을 여는 것이 곧 배포 결정이다.

릴리스 PR이 열리거나 head가 갱신되면 [`release-scope.yml`](../../.github/workflows/release-scope.yml)이 마지막 성공 배포 이후 `develop`에 머지된 PR과 `Closes`·`Fixes`·`Resolves`로 완료 처리할 이슈를 모아 PR 본문의 `## 포함 이슈`를 채운다. head가 움직일 때마다 다시 채우므로 머지 직전에 목록을 손으로 대조할 필요가 없다.

`## QA 포인트`는 비어 있을 때만 구성 PR 본문에서 모은 초안으로 채우고, 사람이 쓴 문장이 있으면 건드리지 않는다. 두 섹션은 main push 시 그대로 릴리스 노트가 되므로 배포 전에 테스터가 실행할 문장으로 다듬는다.

별도 API나 유료 AI를 호출하지 않으며 기존 GitHub Actions 실행량만 사용한다. Actions의 **Collect Release Scope**에서 릴리스 PR 번호를 입력해 다시 산출할 수도 있다.

### PR별 QA 원천과 게이트

구성 PR이 테스트 범위를 본문으로 선언하는 게이트는 `CI Test Plan` 하나다. [PR 템플릿](../../.github/pull_request_template.md)의 JSON 블록에 Android 계측 테스트를 `none`·`selected`·`full` 중 무엇으로 돌릴지 적으면, Repository Quality workflow의 `Validate CI Test Plan` 스텝이 [`validate-pr-ci-test-plan.mjs`](../../.github/scripts/validate-pr-ci-test-plan.mjs)로 변경 경계와 대조한다. `selected`는 그 revision에 실제로 존재하는 `FQCN#method`와 실행 lane이어야 한다.

QA 문구 자체는 구성 PR에서 검증하지 않는다. 구성 PR 본문에 `## QA 포인트` 헤딩을 두면 [`render-release-scope.mjs`](../../.github/scripts/render-release-scope.mjs)가 그 목록을 릴리스 PR 초안으로 모으고, 없으면 릴리스 PR에서 사람이 직접 쓴다. 검사 지점은 배포 직전 릴리스 노트 렌더 단계 하나이며, 그 판정 기준은 아래 「일반 배포」에 있다.

### 일반 배포 — `main` → Firebase App Distribution (자동)

여기서 배포는 검증할 `main` 빌드를 Firebase 테스터에게 전달하는 단계이며, Play Store 프로덕션 릴리스를 뜻하지 않는다. 일반 배포의 자동 진입점은 `main` push 하나다.

`release-distribution.yml`에 `develop` 수동 배포(`workflow_dispatch`) 경로는 두지 않는다. 도착지와 산출물 버전이 main 경로와 같아 실익이 PR 생성 한 단계뿐이었던 반면, release keystore와 service account를 임의 ref에 노출하는 표면이었다.

예외적으로 [`firebase-wif-canary.yml`](../../.github/workflows/firebase-wif-canary.yml)은 `develop` 또는 `main`에서 보호 Environment 승인과 명시적 확인을 받은 뒤 실제 APK를 올리는 수동 인증 호환성 검증 경로다. 일반 릴리스나 임의 브랜치 배포 수단으로 사용하지 않는다. 두 경로가 같은 WIF 설정을 쓰므로, 인증 쪽을 건드릴 때 무엇을 확인하는지는 [WIF canary runbook](firebase-wif-canary.md)에 있다.

`develop` → `main` 릴리스 PR 본문에 다음 섹션을 채운다.

```markdown
## 포함 이슈
- #41
- #42

## QA 포인트
- 오프라인에서 오류 안내와 재시도 수단이 표시되는지 확인
- 주차를 변경한 뒤 최신 리포트가 표시되는지 확인
```

PR이 `main`에 머지되면 워크플로가 두 섹션을 릴리스 노트로 사용한다. 연결된 PR이나 필수 섹션을 찾지 못하면 배포하지 않는다. `## QA 포인트`에 사전조건·행동·기대 결과가 없는 generic fallback 문구가 있으면 릴리스 노트 렌더 단계에서 실패한다.

CI가 사용하는 GitHub Secrets (Settings → Secrets and variables → Actions):

- `KAKAO_NATIVE_APP_KEY`·`GOOGLE_WEB_CLIENT_ID`·`GOOGLE_SERVICES_JSON_B64`는 Environment를 사용하지 않는 dependency audit에서도 필요하므로 repository-level secret으로 둔다.
- release keystore와 Firebase 업로드용 WIF 설정은 보호된 `release-distribution` Environment의 secret으로 둔다.

| 키 | 용도 |
|---|---|
| `RELEASE_STORE_FILE_B64` | release keystore 파일 (`~/careercompass-release.jks`) 의 base64 인코딩 |
| `RELEASE_STORE_PASSWORD` | keystore 비밀번호 |
| `RELEASE_KEY_ALIAS` | key alias (`careercompass-release`) |
| `RELEASE_KEY_PASSWORD` | key 비밀번호 |
| `KAKAO_NATIVE_APP_KEY` · `GOOGLE_WEB_CLIENT_ID` · `GOOGLE_SERVICES_JSON_B64` | release distribution·WIF canary·dependency audit가 쓰는 실서비스 앱 설정 |
| `GCP_WORKLOAD_IDENTITY_PROVIDER` · `GCP_FIREBASE_SERVICE_ACCOUNT` | Firebase 업로드가 단기 자격을 받는 OIDC 설정. release distribution과 WIF canary가 같은 값을 쓴다 ([runbook](firebase-wif-canary.md)) |

> base64 인코딩: `base64 -i ~/careercompass-release.jks | pbcopy` (macOS)

PR 검증용 lint·unit-test·screenshot은 repository secret 대신
`.github/actions/setup-ci-config`가 만드는 결정적 CI 전용 placeholder를 사용한다. 이 fixture는
배포에 사용할 수 없다. production `release-distribution.yml`은 승인된 Environment 뒤에서 Workload Identity로 인증하고, 업로드 직전에 발급받은 단기 자격만 쓴다. 장기 service account 키는 이 파이프라인 어디에도 두지 않으며, [`firebase-wif-canary-policy.test.mjs`](../../.github/scripts/firebase-wif-canary-policy.test.mjs)가 두 워크플로와 release config action을 읽어 그 부재를 단언한다.

### 배포 provenance — 이 APK 가 어느 commit·run 에서 나왔는지

배포 워크플로는 signing 이 끝난 그 APK 하나를 subject 로 [GitHub artifact attestation](https://docs.github.com/en/actions/how-tos/secure-your-work/use-artifact-attestations/use-artifact-attestations)을 발급하고, 업로드 전에 스스로 검증한다. 서명·저장소·signer workflow·source commit·GitHub-hosted 러너 중 하나라도 어긋나거나 attestation subject digest 가 빌드 직후 digest 와 다르면 Firebase 업로드까지 가지 않는다.

성공한 run 의 summary 에 남는 값은 넷이다 — source commit SHA, `sha256:` artifact digest, attestation URL, run URL. APK·AAB 와 R8 mapping 자체는 여기서도 public Actions artifact 로 게시하지 않는다.

받은 APK 가 정말 그 배포 경로에서 나왔는지는 손에 든 파일로 직접 확인할 수 있다.

```bash
gh attestation verify ~/Downloads/careercompass-release.apk --repo Team-CareerCompass/CareerCompass-FE --signer-workflow Team-CareerCompass/CareerCompass-FE/.github/workflows/release-distribution.yml --source-ref refs/heads/main --deny-self-hosted-runners
```

특정 릴리스 commit 으로 좁히려면 `--source-digest <commit SHA>` 를 더한다. 파일이 1비트라도 다르면 digest 가 달라져 검증이 실패한다.

> 아래 로컬 fallback 으로 올린 빌드에는 attestation 이 없다. 그 경로로 배포한 APK 는 위 명령이 실패하는 게 정상이며, 그래서 CI 장애 때만 쓴다.

### 로컬 — 배포 자격 보유자 (fallback / 긴급 시)

먼저 원격을 갱신하고 배포할 commit이 정확히 `origin/main`이며 작업 트리가 깨끗한지 확인한다. 다른 브랜치나 임의 SHA에서는 이 경로를 실행하지 않는다.

```bash
git fetch origin main
git switch --detach origin/main
test "$(git rev-parse HEAD)" = "$(git rev-parse origin/main)"
test -z "$(git status --porcelain)"
```

위 운영 자격을 준비한 같은 checkout에서 릴리스 노트를 만들고 업로드한다.

```bash
EVENT_NAME=workflow_dispatch \
ISSUE_NUMBERS="#41, #42" \
QA_POINTS="오프라인 오류 안내 확인;주차 변경 후 재시도 확인" \
SOURCE_REF=main \
SOURCE_SHA="$(git rev-parse origin/main)" \
bash .github/scripts/render-distribution-release-notes.sh /tmp/careercompass-release-notes.txt

./gradlew assembleRelease appDistributionUploadRelease \
  --releaseNotesFile=/tmp/careercompass-release-notes.txt \
  --no-daemon
```

→ 동일하게 APK 빌드 + Firebase 업로드. CI 장애 시에만 사용한다.

> 일반 로컬·Firebase 빌드는 기본 `versionCode` 1을 유지한다. 이 채널은 Firebase App Distribution이 versionCode로 빌드를 구분하지 않으므로 값을 올릴 이유가 없다. `app/build.gradle.kts`를 직접 수정하지 말고, Play용 값이 필요하면 `CAREERCOMPASS_VERSION_CODE` 환경변수로 주입한다. Play 내부 테스트 트랙 배포는 [`release-play-internal.yml`](../../.github/workflows/release-play-internal.yml)이 run마다 단조 증가하는 값을 산출해 빌드 전에 Play의 현재 최댓값과 대조한다 — 정책과 사전 준비는 [Play release runbook](../play-release.md)에 있다.

### Play 내부 테스트 트랙과의 경계

두 채널은 목적도 산출물도 자격도 다르다. 한쪽 절차를 다른 쪽에 적용하지 않는다.

| | Firebase App Distribution | Play 내부 테스트 트랙 |
|---|---|---|
| 목적 | 개발·기획·디자인·QA 내부 확인 | 출시 전 Play 설치 경로 검증 |
| 산출물 | release APK | release AAB |
| 트리거 | `main` push 자동 | `main`에서 수동 실행 + environment 승인 |
| workflow | [`release-distribution.yml`](../../.github/workflows/release-distribution.yml) | [`release-play-internal.yml`](../../.github/workflows/release-play-internal.yml) |
| versionCode | `1` 고정 | run마다 단조 증가 |
| 자격 | `release-distribution` environment | `play-internal` environment (별도 service account) |
| 롤백 | 이전 빌드를 다시 배포 | 릴리스 중단 후 더 큰 versionCode로 재배포 |

Play 출시 이후에도 Firebase는 기존 QA 채널로 유지한다. 설치 인증서가 다르면 두 채널의 앱은 서로 위에 업데이트되지 않으므로, 테스터가 채널을 옮길 때는 삭제 후 재설치가 필요하다.

## 테스터 관리

- 추가/제거: Firebase Console → App Distribution → 테스터 및 그룹 → `careercompass` 그룹 편집
- 신규 테스터는 첫 초대 이메일에서 **App Tester** 앱 설치 안내를 받음 → 이후 빌드는 자동 알림

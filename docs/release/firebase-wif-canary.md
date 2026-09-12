# Firebase App Distribution WIF canary

이 문서는 릴리스 경로와 분리된 수동 canary에서 GitHub OIDC와 Google Cloud Workload Identity
Federation 호환성을 확인하는 절차다. 저장소의 Firebase 업로드는 릴리스·canary 모두 이미 WIF로
인증하므로, canary는 전환 전 리허설이 아니라 인증 설정을 건드릴 때 릴리스 경로 밖에서 먼저
실측하는 수단이다. Google Cloud·GitHub Settings 변경은 여기서 하지 않는다.

## 현재 안전 경계

- production `release-distribution.yml`과 canary는 같은 WIF secret(`GCP_WORKLOAD_IDENTITY_PROVIDER`·
  `GCP_FIREBASE_SERVICE_ACCOUNT`)으로 인증한다. 장기 service account 키는 어느 경로에도 없다.
- canary는 `develop` 또는 `main`의 수동 실행과 `release-distribution` Environment 승인을 모두
  요구한다.
- signed APK는 인증 전에 빌드하고 같은 runner에서 바로 업로드한다. public Actions artifact로
  게시하지 않는다.
- WIF 인증은 업로드 직전에만 수행한다. provider·service account 값, token, 생성된 ADC 상세는
  workflow summary나 별도 artifact에 기록하지 않는다.
- canary 실패는 canary에서 끝난다. production workflow나 Environment secret을 canary 결과로
  변경·삭제하지 않는다.

## 저장소 밖 설정

다음은 저장소 파일이 아니라 Google Cloud와 GitHub Settings 쪽 설정이라 이 문서가 바꾸지 않는다.
아래는 그 설정이 만족해야 하는 모양이고, 지금 어디까지 채워져 있는지는
[`credentials.md`](credentials.md)가 표로 관리한다.

1. GitHub OIDC용 workload identity pool/provider와 Firebase App Distribution 전용 최소 권한
   service account를 준비한다.
2. provider attribute mapping에 `repository`, `repository_owner`, `ref`, `workflow_ref`,
   `environment` claim을 포함한다.
3. attribute condition은 최소한 다음을 동시에 제한한다.
   - repository: `Team-CareerCompass/CareerCompass-FE`
   - owner: `Team-CareerCompass`
   - ref: `refs/heads/develop` 또는 `refs/heads/main`
   - workflow: `.github/workflows/firebase-wif-canary.yml`
   - environment: `release-distribution`
4. 보호된 Environment에 `GCP_WORKLOAD_IDENTITY_PROVIDER`와
   `GCP_FIREBASE_SERVICE_ACCOUNT`를 masked secret으로 등록한다.
5. 허용 branch canary 성공뿐 아니라 다른 branch·fork·workflow의 credential 발급 거부도
   Google Cloud audit evidence로 확인한다.

공식 근거:

- [Google Cloud deployment pipeline WIF](https://docs.cloud.google.com/iam/docs/workload-identity-federation-with-deployment-pipelines)
- [GitHub Actions OIDC for Google Cloud](https://docs.github.com/en/actions/how-tos/secure-your-work/security-harden-deployments/oidc-in-google-cloud-platform)
- [Firebase App Distribution CI/CD와 WIF credential configuration](https://firebase.google.com/docs/app-distribution/best-practices-distributing-android-apps-to-qa-testers-with-ci-cd)
- [google-github-actions/auth](https://github.com/google-github-actions/auth)

## canary 판정

인증 설정을 바꾼 뒤 릴리스 경로를 믿으려면 아래가 모두 PASS여야 한다.

- 승인된 `develop` 또는 `main` canary APK가 WIF ADC로 1회 업로드된다.
- Firebase release의 source SHA와 실행 URL이 해당 canary run과 일치한다.
- 허용하지 않은 branch·fork·workflow의 credential 발급이 거부된다.
- 대상 service account 권한이 Firebase App Distribution 업로드 최소 범위로 검토된다.

하나라도 확인되지 않으면 상태는 `UNVERIFIED`다. 되돌아갈 JSON 경로가 없으므로, 그 상태에서는
Firebase 배포가 필요하면 [`distribution.md`](distribution.md)의 로컬 fallback으로 올리고 원인을
먼저 잡는다. 이 저장소에서 Actions의 WIF 경로를 실제로 실행한 이력은 [`credentials.md`](credentials.md)가
관리한다.

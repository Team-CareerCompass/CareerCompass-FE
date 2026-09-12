# 개인정보처리방침 게시처와 절차

[`privacy-policy.md`](privacy-policy.md) 를 어디에 어떻게 올릴지 미리 정해 둔 문서다. 방침 본문의 빈 곳 둘(문의처, LLM 전송 범위)은 [#274](https://github.com/Team-CareerCompass/CareerCompass-FE/issues/274) 가 채운다. Play Console 의 앱 콘텐츠 선언이 공개 URL 을 요구하므로([`../play-release.md`](../play-release.md)), 게시처를 정해 두지 않으면 빈칸이 채워진 날 선언 화면 앞에서 되돌리기 어려운 선택을 급하게 하게 된다.

지금은 켜지 않는다. Pages 는 켜는 순간이 게시라, 빈칸이 남은 초안이 그대로 공개된다.

## 게시처

**FE 저장소의 GitHub Pages 를 쓴다.**

| 후보 | 판정 | 근거 |
|---|---|---|
| FE 저장소 GitHub Pages | 쓴다 | 저장소가 public 이라 조직의 free 플랜에서 켤 수 있다. 방침 본문과 게시가 한 저장소에 있어 개정 이력이 git 하나로 남는다 |
| 조직 페이지 | 안 쓴다 | `Team-CareerCompass.github.io` 저장소가 없다. 조직에는 FE·BE·AI·Design 넷뿐이라 저장소를 새로 만들어야 하고, 본문은 FE 에 있으니 두 저장소를 계속 맞춰 가야 한다 |
| 서버 도메인 | 안 쓴다 | 도메인이 없다. `core:network` 의 `BASE_URL` 은 아직 `https://api.careercompass.invalid/api/v1/` 이고 서버는 서지 않았다([`../plan/progress-report.md`](../plan/progress-report.md)). 방침 주소는 서버가 죽어 있어도 열려야 하는 주소다 |

주소는 `https://team-careercompass.github.io/CareerCompass-FE/privacy/` 가 된다. Play 선언에는 켠 뒤 Pages 설정 화면이 보여 주는 주소를 그대로 옮긴다.

## 소스

| 축 | 값 | 왜 |
|---|---|---|
| 파일 | `docs/store/privacy-policy.md` 한 장 | 사본을 만들지 않는다. 공개된 문장과 저장소의 문장이 갈라지면 어느 쪽이 방침인지 다툴 여지가 생긴다 |
| 브랜치 | `main` | 배포 산출물이 나오는 브랜치다. `develop` 을 소스로 두면 아직 리뷰 중인 문구가 머지되는 순간 공개된다. 승격 기준은 [`../release/distribution.md`](../release/distribution.md) 의 것을 그대로 쓴다 |
| Pages 소스 | GitHub Actions | 브랜치 소스는 저장소 루트나 `/docs` 폴더 통째로만 고를 수 있다. `/docs` 를 고르면 QA 기록·보안 점검·계획 문서까지 사이트가 된다. 게다가 앞머리(front matter)가 없는 `.md` 는 Jekyll 이 변환하지 않고 정적 파일로 내보내, 브라우저가 페이지 대신 파일을 받는다 |

## 게시 절차

빈칸이 채워진 날 이 순서대로 한다.

1. 방침 본문의 문의처와 LLM 전송 범위를 채우고, 머리의 시행일에 게시 예정일을 적는다. 같은 PR 에서 [`listing.md`](listing.md) 의 데이터 보안 표를 같은 값으로 맞춘다.
2. `develop` 에 머지하고, 배포 묶음과 함께 `main` 으로 승격한다.
3. Settings → Pages → Build and deployment 의 Source 를 GitHub Actions 로 바꾼다.
4. 워크플로 `.github/workflows/publish-privacy-policy.yml` 을 더한다. 내용은 아래.
5. 워크플로를 한 번 수동 실행하고 주소를 연다. 표와 링크가 렌더됐는지, 초안 표시가 남아 있지 않은지 확인한다.
6. 주소를 세 곳에 적는다. Play Console 의 앱 콘텐츠 선언, [`listing.md`](listing.md) 의 선언 표, 그리고 앱(아래 「앱 안의 자리」).

### 워크플로가 할 일

- 트리거는 둘이다. `main` push 중 `docs/store/privacy-policy.md` 가 바뀐 경우, 그리고 `workflow_dispatch`.
- 권한은 `contents: read`, `pages: write`, `id-token: write`. 환경은 `github-pages`.
- 쓰는 액션은 `actions/checkout`, `actions/upload-pages-artifact`, `actions/deploy-pages` 다. 셋 다 `actions` 소유라 허용 목록(`.github/scripts/supply-chain-policy.test.mjs`)은 고치지 않아도 되지만, 저장소의 `sha_pinning_required` 가 켜져 있어 40자 SHA 로 고정해야 한다([`../security/actions-supply-chain.md`](../security/actions-supply-chain.md)). 고정하지 않으면 job 이 만들어지기 전에 죽고 로그에 사유가 남지 않는다.
- 마크다운을 페이지로 바꾸는 단계는 렌더 API 한 번으로 끝난다. 새 의존성을 받지 않는다.

```bash
gh api --method POST /markdown -f mode=gfm \
  -f "text=$(cat docs/store/privacy-policy.md)" > body.html
```

돌아오는 것은 `<h1>` 부터 시작하는 조각이다. 제목과 `charset`·`viewport`·본문 폭만 넣은 최소 템플릿에 끼워 `_site/privacy/index.html` 로 두고 그 폴더를 업로드한다. 표는 `<markdown-accessiblity-table>` 로 감싸여 나오는데 브라우저가 모르는 요소라 표시에는 영향이 없다(2026-09-06 실측).

### 어긋나기 쉬운 곳

| 상황 | 무엇이 보이는가 |
|---|---|
| Pages 소스가 브랜치로 남아 있다 | `docs/` 전체가 사이트가 되고, 방침은 페이지가 아니라 내려받는 파일이 된다 |
| `main` 승격 전에 켰다 | 옛 본문이 공개된다. 빈칸이 남은 초안일 수 있다 |
| 액션을 SHA 로 고정하지 않았다 | run 이 `startup_failure` 로 죽고 로그에 사유가 없다 |

## 시행일과 개정 이력

- 시행일은 방침 본문 머리의 줄이다. 게시 워크플로가 성공한 날짜를 적는다.
- 개정 이력은 방침 문서 끝에 표로 남긴다. 판, 시행일, 바뀐 것, PR 번호 네 칸이고 첫 줄이 초판이다.
- 이력을 페이지 안에 두는 이유는 읽는 사람이 git 로그를 볼 수 없기 때문이다. Play 심사도 이 페이지만 본다.
- 수집 항목이나 제3자 제공이 느는 개정은 앱 고지가 7일 먼저다(방침 9항). 고칠 문구는 브랜치에 두고 `main` 승격 시점을 시행일에 맞춘다. 페이지가 앱보다 먼저 바뀌면 고지 기간이 사라진다.

## 앱 안의 자리

| 자리 | 무엇을 | 모듈 | 언제 |
|---|---|---|---|
| 로그인 화면의 고지 문구 | `onboarding_login_terms_notice` 안의 「개인정보 처리방침」에 링크를 건다 | `feature:onboarding` | 게시 뒤 |
| 마이 홈의 계정 절 | 방침으로 가는 줄 하나 | `feature:profile` | 마이 홈은 [#175](https://github.com/Team-CareerCompass/CareerCompass-FE/issues/175) 로 이미 서 있다. 게시 뒤 그 화면에 줄을 더한다 |

로그인 화면을 첫 자리로 잡은 이유는 그 문구가 이미 동의를 간주하고 있기 때문이다. 지금은 링크가 없는 `Text` 라 읽을 길이 없다. 다만 그 문구는 문서 둘을 부르는데 저장소에 있는 것은 방침 하나뿐이다. 이용약관을 둘지는 이 문서가 정하지 않는다. 링크는 방침 쪽에만 건다.

마이 탭은 더 이상 셸의 자리표시자가 아니다. #175 가 `MyTabPlaceholderScreen` 을 지우고 profile 모듈의 마이 홈으로 바꿨다. 그래서 방침 줄은 `app` 이 아니라 `feature:profile` 의 마이 홈에, 지문 로그인·화면 테마·로그아웃이 있는 「계정」 절에 넣는다.

여는 방법은 공고 원문 보기(`PostingRawScreen`)와 같다. `Intent.ACTION_VIEW` 로 외부 브라우저에 넘기고 앱 안에 WebView 를 두지 않는다. 주소는 서버가 준 값이 아니라 앱이 박아 두는 상수라 `ExternalUrl.openableOrNull` 을 거칠 필요는 없다. 상수는 `core:common` 에 한 번만 두고 두 화면이 함께 읽는다. 화면마다 따로 적으면 개정 때 한쪽이 남는다.

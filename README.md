# CareerCompass-FE

건국대학교 졸업 프로젝트 **CareerCompass** 의 Android 클라이언트.

개인 맞춤 장학금·공모전·채용공고를 모아 적합도를 분석하고, 경험 카드를 바탕으로 자기소개서 초안을 만들어 주는 앱이다.

기획·디자인 산출물(Figma 13페이지, 기능 스펙, 유스케이스, 발표자료)은 코드 저장소에 두지 않는다. 별도 문서 폴더에서 관리한다. **어느 문서를 믿을 것인가와 문서끼리 어긋난 곳의 판정은 [`docs/spec/canon.md`](docs/spec/canon.md) 에 있다** — 이 프로젝트는 주제가 한 번 갈아엎어져 날짜만으로는 정본을 고를 수 없다.

학기 일정과 목표 진척도는 [`docs/plan/schedule.md`](docs/plan/schedule.md) 에 있다. 2026-04-01 에 교수님께 제출한 표와 매월 실제 진척을 나란히 둔다.

## 현재 상태

저장소 운영 하네스, GitHub Actions 파이프라인과 **Android 클라이언트의 28개 Gradle 모듈 골격** 위에 공통 UI·`core` 데이터 계층·온보딩과 피드의 화면·ViewModel·내비게이션이 구현돼 있다. 앱 셸(`AppNavigation`)은 세션 여부와 온보딩 완료 여부로 시작 목적지를 정해 온보딩 그래프 또는 피드 그래프로 들어가고, 하단 탭 4개 중 「마이」는 profile 모듈의 마이 홈이고, 나머지 두 탭과 알림은 다른 담당 모듈이 진입점을 제공할 때까지 자리표시자다. 백엔드는 아직 자리표시자 주소(`core/network` 의 `BASE_URL`)라 실제 서버 없이 도는 흐름은 계측 테스트의 fake 주입으로 확인한다.

빠른 로컬 검증은 다음과 같이 실행한다.

```bash
./gradlew assembleDebug          # debug APK 빌드
./gradlew test testDebugUnitTest # 단위 테스트 + Konsist 아키텍처 테스트
./gradlew ktlintCheck lint       # 정적 검사
node --test .github/scripts/*.test.mjs   # 저장소 정책 테스트
```

영향 범위별 실제 머지 게이트와 태스크 선택의 단일 정본은 [`pr-validation.yml`](.github/workflows/pr-validation.yml), [`unit-test.yml`](.github/workflows/unit-test.yml), [`lint.yml`](.github/workflows/lint.yml), [`screenshot.yml`](.github/workflows/screenshot.yml), [`resolve-pr-impact.mjs`](.github/scripts/resolve-pr-impact.mjs) 다.

Android 명령 전에는 `local.properties` 의 `sdk.dir` 로 SDK 위치를 제공한다. `app/google-services.json` 은 커밋하지 않는다 — CI 는 `.github/actions/setup-ci-config` 가 secret 없이 스텁을 만든다. 로컬에서는 실제 설정 파일이 없는지 먼저 확인한 뒤 `node .github/scripts/create-ci-config.mjs --workspace . --github-env /dev/null` 로 debug 검증용 스텁만 만들 수 있다. 이 명령은 기존 파일을 덮어쓰며 `/dev/null` 사용은 CI 의 환경 변수 전달을 재현하지 않는다.

### 구현 범위

| 범위 | 현재 구현 | 아직 구현하지 않은 경계 |
| --- | --- | --- |
| [`:core:ui`](core/ui) | 색상(라이트·다크 테마, 시스템 설정 연동)·타이포그래피·간격·모양 테마와 버튼, 텍스트 필드, 배지, 태그, 적합도 칩, 하단 탭, 앱 바, 카드, 학교·졸업 예정 선택 시트, 경험 카드 편집 시트, 지원서 항목 분류 시트(온보딩과 마이 탭이 함께 쓴다 — 검증·날짜 정밀도 전이 포함), 엣지 상태 화면 6종(연결 실패·분석 중·빈 결과·권한 거부·서버 점검·사유 없는 실패). 단위 테스트(팔레트 대비 검사 포함)와 Compose Preview screenshot baseline(라이트·다크) 포함. 화면 테마는 시스템 설정을 따르거나 밝게·어둡게로 직접 고를 수 있고(마이 탭), 고른 값은 기기 수명으로 남는다 | — |
| `core` 데이터 계층 ([`common`](core/common) · [`datastore`](core/datastore) · [`model`](core/model) · [`domain`](core/domain) · [`network`](core/network) · [`data`](core/data)) | API_SPEC v0.1 §1~§5(인증·프로필·경험 카드·과거 지원서·게시판·공고)의 모델, 리포지토리 계약과 fake(`src/testFixtures`), Retrofit 서비스·DTO·토큰 재발급(single-flight)·401 재시도·`ApiException` 변환, DataStore 세션·기기 저장소, 리포지토리 구현과 Hilt 바인딩. §6(지원서 작성)·§7(For You·로드맵·Export)의 Retrofit 서비스·DTO 와 SSE 스트림 전용 클라이언트(`LongRunningOperation.ApplicationStream`)·`asServerSentEvents` 리더도 여기 있다. §9 에러 코드를 도메인 사유로 옮기는 번역(`ApiFailureMapper`)은 `core:data` 밖의 data 모듈도 부르도록 `core:network` 에 있다. `ApiWireContractSmokeTest` 가 전 엔드포인트의 route·body·응답 스키마를 검증 | 알림(§8) 계약, 실제 서버 주소 |
| [`:feature:onboarding`](feature/onboarding) | 소셜 로그인(카카오·Google)·지문 로그인(계정 귀속·성공 뒤 세션 검증)·Step 1~4·완료 화면, 그래프 스코프 ViewModel(검증·저장·업로드·재개), 진행 상태 DataStore, 학교/졸업 피커·경험 빠른 추가·직접 입력 시트, 내비게이션 그래프. 단위/Compose 테스트와 screenshot baseline 포함 | 직무·학교 목록의 서버 연동(현재 로컬 상수), 경험 카드 편집(profile 모듈) |
| [`:feature:feed`](feature/feed) | 메인 피드(검색·카테고리·필터 시트·정렬·커서 페이징·북마크), 공고 상세(적합도 분석·키워드·자격/우대·지원서 항목·유사 공고)·원문, 게시판 등록(구조 감지·미리보기)·목록·수정 시트(이름·유형·수집 주기 부분 수정), 오프라인 스냅샷(마지막 기본 조회 첫 페이지를 세션 저장소에 남겨 네트워크 단절 시 「오프라인 모드로 보기」로 제공), domain use case(마감·검색 클라이언트 규칙), 내비게이션 그래프 | 지원서 초안 작성 진입(editor 모듈), 알림 화면(notification 모듈), 상세 화면 분석 축 문구의 서버 연동 |
| [`:feature:profile`](feature/profile) | 마이 홈(프로필 요약·완성도 게이지와 빈 칸 안내·메뉴 4종·계정 설정), 프로필 편집(이름·학교·학과·학점·졸업 연도와 희망 직무 3개·관심 태그 5개 — 검증·목록·피커를 온보딩과 한 벌로 공유), 경험 카드 목록(유형 필터·커서 페이징·30장 상한 표시)과 등록·수정·삭제(온보딩과 같은 시트·같은 전이), 과거 지원서 목록(분류 수동 조정·10개 상한·삭제), domain use case. 하단 탭 「마이」가 이 화면이다 | 지원서 등록(업로드·직접 작성 — 텍스트 엔드포인트가 계약에 없다) |
| [`:app`](app) · [`:baselineprofile`](baselineprofile) | 시작 목적지 ViewModel(로컬 세션·캐시 프로필로 먼저 확정하고 프로필은 백그라운드로 갱신), 온보딩·피드 그래프와 하단 탭을 잇는 앱 셸, 공고 상세 딥링크(`careercompass://postings/{id}`, 인증 뒤 적용), 마이 탭에 profile 모듈의 마이 홈을 띄우고 그 메뉴 4종을 루트 자리표시자로 잇는 배선, Kakao SDK 초기화, Crashlytics `ErrorReporter`, 계측 테스트의 Hilt fake 주입(`AppNavigationAndroidTest`·API 경계·접근성 smoke), Baseline/Startup Profile, 콜드 스타트 실측([`docs/qa/cold-start-2026-09-03.md`](docs/qa/cold-start-2026-09-03.md)) | 다른 담당 모듈 탭·알림의 실제 진입점, 마이 홈 메뉴가 가리키는 나머지 화면(지원서 등록 — profile, 알림 설정 — notification) |
| [`:feature:editor`](feature/editor) | API_SPEC v0.1 §6 의 도메인·데이터 계층 — 지원서/항목 상태 모델(`generating`·`ready`·`partial_failed`·`saved`, 항목은 `loading`·`done`·실패), 스트림 사건을 초안에 접어 넣는 순수 전이(`applying`·`settled`), 리포지토리 계약과 fake(`src/testFixtures`), use case 8종, SSE 스트림 구독과 그 실패 구분(연결 실패 / 받은 뒤 끊김) | `presentation` 전체(에디터·진행 화면·재생성·저장·이력) |
| [`:feature:foryou`](feature/foryou) | API_SPEC v0.1 §7 의 도메인·데이터 계층 — For You 추천(톱 픽·강점 기반·취약점 보완, 배열/문자열로 갈린 `reason` 을 한 벌 목록으로 모은다), 커리어 로드맵 비교(`peer`·`senior`·`me_only`, 표본 크기 포함), 강점 Export(형식 4종·묶음 5종, 빈 선택을 요청 전에 막고 문서 순서로 정렬), 리포지토리 계약과 fake(`src/testFixtures`), use case 3종 | `presentation` 전체(For You 탭·로드맵·Export 시트) |
| `notification` | 멀티모듈 build/패키지 골격 | `data`·`domain`·`presentation` 전체 구현 |

### 모듈

28개. `:app` · `:baselineprofile` · `:konsist`, `core` 7개(`common` `data` `datastore` `domain` `model` `network` `ui`), 그리고 feature 6개가 각각 `data`·`domain`·`presentation` 로 나뉜다.

레이어 의존 방향은 `:konsist` 의 아키텍처 테스트가 강제한다. `build-logic` 의 `careercompass.*` 규약 플러그인이 모듈 공통 설정을 소유하므로, 모듈 build 파일에는 그 모듈만의 것을 적는다.

### 검증 기준선

예전에 빨갛다고 적혀 있던 두 정책은 필요한 산출물과 smoke가 추가돼 현재 소스 기준으로 해소됐다. 전체 정책 테스트 수는 계속 변하므로 README에 통과 개수를 고정하지 않고, 빠른 로컬 명령과 위 workflow의 CI 결과로 확인한다.

| 검증 | 현재 기준 |
| --- | --- |
| Baseline Profile | [`baseline-prof.txt`](app/src/main/generated/baselineProfiles/baseline-prof.txt) 와 [`startup-prof.txt`](app/src/main/generated/baselineProfiles/startup-prof.txt) 를 커밋하고, generator·주간 workflow·release AAB 패키징 계약을 정책 테스트로 검증한다 |
| API 경계 smoke | [`ApiBoundarySmokeAndroidTest`](app/src/androidTest/java/com/careercompass/careercompass_fe/ApiBoundarySmokeAndroidTest.kt) 가 API 26·36 managed-device lane에서 앱 시작 시맨틱과 지원 범위를 확인하고, [`AppNavigationAndroidTest`](app/src/androidTest/java/com/careercompass/careercompass_fe/AppNavigationAndroidTest.kt) 가 fake 세션으로 로그인·온보딩·피드 시작 분기를 확인한다 |
| 접근성 smoke | [`AccessibilitySmokeAndroidTest`](app/src/androidTest/java/com/careercompass/careercompass_fe/AccessibilitySmokeAndroidTest.kt) 가 API 34에서 온보딩 Step 1~4·피드·마이 홈 화면에 Android Accessibility Test Framework 검사를 수행한다 |
| UI 회귀 | `core:ui`, 온보딩(로그인·지문·Step 1~4·완료·시트), 피드(홈·상세·원문·필터·게시판)의 단위/Compose 테스트와 커밋된 screenshot baseline을 검증한다. 마이 홈은 Compose(Robolectric) 테스트로 덮고 골든은 두지 않는다([근거](docs/testing/screenshot.md)) |

## 분담

FE 2인. 경계는 **모듈 소유권**이고, 단일 정본은 `.github/scripts/reconcile-issue-metadata.mjs` 의 `ASSIGNEE_BY_MODULE` 이다. 이슈를 등록하면 `Issue Metadata Guard` 가 `area:*` 라벨과 담당자를 자동으로 맞춘다.

| 모듈 | 범위 | 담당 |
| --- | --- | --- |
| `core` | 디자인 시스템·네트워크·인증 | 정일혁 (`@1hyok`) |
| `careercompass` | 앱 셸·내비게이션 | 정일혁 (`@1hyok`) |
| `onboarding` | 스플래시·로그인·지문 로그인·온보딩 4단계 | 정일혁 (`@1hyok`) |
| `feed` | 공고 피드·필터·공고 상세(채용/공모전)·원문 | 정일혁 (`@1hyok`) |
| `platform` | CI·빌드·릴리스·저장소 운영 | 정일혁 (`@1hyok`) |
| `editor` | AI 자소서 초안 생성·에디터·재생성·저장/이력 | 이준혁 (`@Sadturtleman`) |
| `profile` | 마이·프로필 편집·경험 카드 5종·과거 자소서 | 이준혁 (`@Sadturtleman`) |
| `foryou` | For You·커리어 로드맵·강점 Export | 이준혁 (`@Sadturtleman`) |
| `notification` | 알림·알림 설정 | 이준혁 (`@Sadturtleman`) |

`core` 는 두 사람의 공통 선행 의존이다. 여기에 손대는 이슈는 `blocked_by` 로 순서를 걸어 `merge-order-guard` 가 머지 순서를 강제하게 한다.

## 머지 정책

**리뷰 승인 없이 작성자가 직접 머지한다.** 승인자를 기다리지 않는다.

머지의 게이트는 두 개뿐이다.

1. CI 필수 검사 통과
2. `merge-order-guard` — 이슈 `blocked_by` 의존성

코멘트와 `REQUEST_CHANGES` 는 누구나 남길 수 있지만 머지를 막지 않는다. 답하지 않은 변경요청이 있으면 `conflict-label` 이 `awaiting-author` 라벨만 붙인다.

리뷰가 게이트가 아니라는 것이 검증을 건너뛴다는 뜻은 아니다. 리뷰어가 잡아 주던 몫을 작성자가 진다 — PR 전에 변경 diff 와 영향 경계를 대조해 테스트 누락을 스스로 점검한다.

## 문서

에이전트 하네스(`AGENTS.md`·`CLAUDE.md`·`.claude/`·`.codex/`)는 각자 쓰는 것이 달라 저장소에 올리지 않는다 — `.gitignore` 가 막는다. 저장소가 공유하는 규약은 아래 문서와 `.github/` 의 정책 스크립트다.

- [`docs/manual/`](docs/manual) — 최종 사용자 매뉴얼. 1부 가입과 온보딩 · 2부 게시판 · 3부 피드와 적합도까지 썼다(#289). 지원서·마이 탭·알림은 그 화면이 나온 뒤에 쓴다(#268)
- [`docs/convention/`](docs/convention) — presentation 패키지 구조, Composable 콜백 기본값, 리소스 네이밍, [색 대비(WCAG AA)](docs/convention/color-contrast.md), [MVI](docs/convention/mvi.md), [Navigation 3](docs/convention/navigation3.md)
- [`docs/spec/edge-states.md`](docs/spec/edge-states.md) — 엣지 상태 화면별 확정표(오프라인·로딩·빈 결과·권한·점검·세션 만료). **새 화면을 만들기 전에 채울 칸이 마지막 절에 있다**
- [`docs/spec/error-copy.md`](docs/spec/error-copy.md) — 서버 에러 코드 14종의 사용자 문구·행동·재시도 가능 여부
- [`docs/spec/notification-screens.md`](docs/spec/notification-screens.md) — 알림 목록·설정 화면의 글로 된 시안(§8·F2-4). Figma 에 없는 두 화면을 #195·#196 이 이것으로 만든다
- [`docs/testing/screenshot.md`](docs/testing/screenshot.md) — Compose Preview 스크린샷 테스트
- [`docs/api-base-url.md`](docs/api-base-url.md) — 빌드 타입별 API 주소(`BASE_URL_DEV`·`BASE_URL_PROD`)와 debug 가 운영을 가리키지 못하게 막는 가드
- [`docs/release/`](docs/release) — 배포·Firebase WIF
- [`docs/qa/`](docs/qa) — QA 기기 기준, 증적 스키마, [실서버 라운드 대본](docs/qa/server-round-script.md)(로그인부터 원문 보기까지의 순서·판정과 계약 드리프트 기록 양식), [베타 운영 규칙과 관찰형 태스크 대본](docs/qa/beta-plan.md)

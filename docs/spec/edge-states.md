# 엣지 상태 — 화면별 확정표

Figma 09 Edge Cases 는 상태 화면을 **다섯 장**만 그려 두었다 — 연결 실패 · 분석 중 · 검색 결과 없음 · 알림 권한 꺼짐 · 서버 점검. 어느 화면에서 어떤 문구로 쓰는지는 시안에 없고, 그래서 FE 가 화면마다 문구를 스스로 지었다. 이 문서는 **지어낸 규칙이 아니라 이미 코드에 흩어져 있는 판정을 한자리에 모은 것**이다.

- 기준 커밋: `develop` `4c4b472`(원문 보기·게시판 등록의 서버 점검 칸은 #212 가 채웠다). 표의 문구는 전부 실제 리소스에서 그대로 옮겼다 — 지어낸 문장은 하나도 없다.
- 판정 이력: [#127](https://github.com/Team-CareerCompass/CareerCompass-FE/issues/127)(빈 피드 사유 5종과 우선순위) · [#144](https://github.com/Team-CareerCompass/CareerCompass-FE/issues/144)(조건 때문에 실패했을 때 조건을 되돌릴 길) · [#101](https://github.com/Team-CareerCompass/CareerCompass-FE/issues/101)(서버 점검을 별도 사유로).
- **진행 중인 이슈가 바꿀 칸은 표에 그렇게 적어 두었다** — #204(에러 코드 14종 → 문구 매핑) · #206(빈 피드에 「사라진 게시판」 사유 추가) · #197(알림 권한 동의 흐름) · #200(적합도 경계) · #205(색 대비). 그 칸의 지금 값은 곧 바뀐다.
- 이 표를 채우다 드러난 결함은 [#211](https://github.com/Team-CareerCompass/CareerCompass-FE/issues/211)(온보딩 세션 만료의 막다른 길) · [#212](https://github.com/Team-CareerCompass/CareerCompass-FE/issues/212)(원문 보기·게시판 등록이 503 을 안 가른다)로 나갔다. **이 문서는 고치지 않고 기록만 한다.**

---

## 0. 부품 — `core:ui` 의 상태 화면 5종 + 실패 1종

전부 [`core/ui/src/main/kotlin/com/careercompass/core/ui/component/CareerCompassStateView.kt`](../../core/ui/src/main/kotlin/com/careercompass/core/ui/component/CareerCompassStateView.kt) 한 파일에 있고, 공통 뼈대(`CareerCompassStateLayout`)가 `fillMaxSize` 로 **화면 한 장을 통째로 차지한다**. 카드 안이나 목록 끝에 끼워 넣는 용도가 아니다. **예외는 「분석 중」 하나다** — `presentation = Inline` 을 받아 카드 안에 들어간다(#221). 기다리는 상태만 화면의 나머지가 그대로 쓸모 있어서다(실패·빈 결과·권한·점검은 화면이 더 보여 줄 것이 없다).

| 컴포넌트 | Figma 09 | 문구를 누가 정하나 | 행동 슬롯 | 삽화 |
|---|---|---|---|---|
| `CareerCompassNetworkErrorState` | 연결 실패 | **부품이 고정** — 호출자는 문구를 못 바꾼다 | 「다시 시도」 고정 + 「오프라인 모드로 보기」(`onOfflineClick != null` 일 때만) | 📡 |
| `CareerCompassAnalyzingState` | 분석 중 | 호출자(`title`·`description`·`progressLabel`) + `presentation`(FullScreen / **Inline**, #221) | **없음** — 행동 슬롯이 빈 `{}` 로 닫혀 있다 | 진행 인디케이터 |
| `CareerCompassEmptyState` | 검색 결과 없음 | 호출자(`title`·`description`·`actionText`) | 행동 0개 또는 1개(`actionText`+`onActionClick` 은 둘 다 있거나 둘 다 없다) | 🔍 |
| `CareerCompassFailureState` | **시안 없음** — #222 에서 더했다 | 호출자(`title`·`description`·`actionText`) **또는 실패 표의 행**(`FailureDisplay`, #204) | 행동 0개 또는 1개 — 표 판을 쓰면 **표의 행동이 있고 호출자가 콜백을 넘길 때만** 붙는다 | ⚠️ |
| `CareerCompassPermissionDeniedState` | 알림 권한 꺼짐 | 호출자(`title`·`description`·`benefits`) | 「설정에서 권한 켜기」 + 「나중에」 고정 | 🔔 |
| `CareerCompassMaintenanceState` | 서버 점검 | 호출자(`title`·`description`·`statusLabel`·`contactLabel`) | 「새로고침」 고정 + 「오프라인 모드로 보기」(선택) | 🛠 |

부품이 고정한 문구([`core_ui_state_strings.xml`](../../core/ui/src/main/res/values/core_ui_state_strings.xml)):

| 리소스 | 값 |
|---|---|
| `core_ui_state_network_title` | 연결할 수 없어요 |
| `core_ui_state_network_description` | 인터넷 연결을 확인하고 다시 시도해 주세요 |
| `core_ui_state_retry` | 다시 시도 |
| `core_ui_state_offline` | 오프라인 모드로 보기 |
| `core_ui_state_refresh` | 새로고침 |
| `core_ui_state_open_settings` | 설정에서 권한 켜기 |
| `core_ui_state_later` | 나중에 |

### 지금 아무 데서도 안 쓰이는 부품

- ~~**`CareerCompassAnalyzingState`** — 운영 코드 호출처가 0곳~~ — **#221 부터 공고 상세의 적합도 카드가 `Inline` 으로 쓴다**(§2.2). 앱의 「분석 중」은 화면 한 장을 덮을 자리가 없어 부품 쪽에 카드 안 표현을 열었다. 다른 진행 표시(온보딩 문서 분류·게시판 감지)에는 쓰지 않는다 — 문서마다 한 줄이라 부품을 문서 수만큼 반복하면 카드가 화면을 넘기고, 감지는 URL 입력칸이 살아 있어야 해 이미 힌트와 타임아웃 상자로 끝나는 길이 있다.
- **`CareerCompassPermissionDeniedState`** — 호출처 0곳. 알림 권한 상태 자체가 미구현이다(§2.4).

---

## 1. 화면 × 상태 매트릭스

칸의 값은 **그 상태에서 실제로 그려지는 것**이다. 상세(문구·리소스·버튼 동작)는 §2 의 같은 이름 행에 있다.

| 화면 | 오프라인·네트워크 실패 | 로딩·분석 중 | 빈 결과 | 권한 거부 | 서버 점검(503) | 세션 만료(401) |
|---|---|---|---|---|---|---|
| 앱 시작(셸) | 없음 — 캐시로 판정 | 시스템 스플래시 | — | 없음 | 없음 — 캐시로 판정 | 로그인 화면 + 만료 배너 |
| 로그인 | 인라인 오류 카드 | 인라인 진행 줄 | — | 없음 | 인라인 오류 카드(일반 문구) | 만료 배너(셸이 켠다) |
| 지문 로그인 | 인라인 오류 카드 | 버튼 안 진행 표시 | — | 없음 | 인라인 오류 카드(일반 문구) | 로그인 화면으로 이동 |
| 온보딩 Step 1~4 | 하단 실패 배너 | 버튼 잠금(진행 표시 없음) | — | 없음 | 하단 실패 배너 | 셸에 알림 → 로그인(#211) |
| 온보딩 학교 선택 시트 | — (로컬 목록) | — | 맨 `Text` + 직접 입력 버튼 | — | — | — |
| 온보딩 완료 | 없음 | 없음 | — | 없음 | 없음 | 없음 |
| 피드 홈 | `CareerCompassNetworkErrorState` | `FeedLoading` | `CareerCompassEmptyState` × 6사유 | 없음 | `FeedMaintenanceState` | 셸에 알림 → 로그인 |
| 공고 상세 | `CareerCompassNetworkErrorState`(#222) | `FeedLoadingContent` · 적합도 카드는 `CareerCompassAnalyzingState(Inline)` → 소진 뒤 「다시 확인」(#221) | — | 없음 | `FeedMaintenanceState` | 셸에 알림 → 로그인 |
| 원문 보기 | `CareerCompassNetworkErrorState` | `FeedLoadingContent` | 본문 자리 대체 문구 | 없음 | `FeedMaintenanceState` | 셸에 알림 → 로그인 |
| 내 게시판(목록) | `CareerCompassNetworkErrorState` | `FeedLoadingContent` | `BoardListEmpty`(손으로 그림) | 없음 | `FeedMaintenanceState` | 셸에 알림 → 로그인 |
| 게시판 등록 | 스낵바 / 타임아웃 상자 | 인라인 진행 줄 × 2 | 감지 실패 상자 4종 | 없음 | 감지 = `FeedMaintenanceNotice` 상자 · 제출 = 점검 스낵바 | 셸에 알림 → 로그인 |
| 게시판 수정 시트 | 스낵바 | 저장 중 표시 | — | 없음 | 스낵바 일반 문구 | 셸에 알림 → 로그인 |
| 마이 홈(마이 탭) | 캐시 있으면 스낵바 `profile_home_refresh_failed` · 없으면 `CareerCompassFailureState`(실패 표 #204) | 첫 조회만 화면 가운데 진행 표시 | 없음 — 프로필은 언제나 하나, 개수 0 은 배지를 뗀다 | 없음 | 오프라인과 같은 길(실패 표가 점검 문구를 준다) | 로그아웃(`LoggedOut`) · 401(`Expired`) 둘 다 셸에 알린다 |
| 프로필 편집 | 프리필할 값이 없을 때만 `CareerCompassFailureState`(실패 표 #204) · 기본 정보 저장 실패는 스낵바 `profile_edit_save_failed` · **전체 교체(직무·태그) 실패는 서버 값으로 되돌리고** `profile_edit_interests_reverted` | 첫 조회만 화면 가운데 진행 표시 | 없음 | 없음 | 오프라인과 같은 길 | 셸에 알림 → 로그인 |
| 경험 카드 편집 시트 | 저장 실패는 시트를 열어 둔 채 스낵바 `profile_experience_save_failed` (상한 거부는 `profile_experience_limit_reached`) | 저장 중 시트 잠금(닫기 무시) | — | 없음 | 오프라인과 같은 길 | 셸에 알림 → 로그인 |
| 경험 카드 목록 | 읽은 카드가 없을 때만 `CareerCompassFailureState`(실패 표 #204 · `FailureSurface.ExperienceCard`) · 이어 읽기 실패는 스낵바 `profile_experience_load_more_failed` | 첫 조회만 화면 가운데 진행 표시 | `CareerCompassEmptyState` × 2사유(미등록 → 「첫 카드 만들기」 · 필터 → 「전체 보기」) | 없음 | 오프라인과 같은 길 | 셸에 알림 → 로그인 |
| 과거 지원서 목록 | 읽은 지원서가 없을 때만 `CareerCompassFailureState`(실패 표 #204 · `FailureSurface.Application`) · 분류 변경·삭제 실패는 스낵바 | 첫 조회만 화면 가운데 진행 표시 | `CareerCompassEmptyState` — 사유 하나(아직 안 올렸다) → 「첫 지원서 올리기」 | 없음 | 오프라인과 같은 길 | 셸에 알림 → 로그인 |
| 지원서 작성(문항 확인) | 공고를 못 읽으면 `CareerCompassFailureState`(실패 표 #204 · `FailureSurface.Posting`) · 생성 실패는 문항을 그대로 두고 스낵바 `editor_setup_create_failed` | 첫 조회만 화면 가운데 진행 표시 · 생성 중에는 버튼 잠금 + 「초안을 준비하고 있어요…」 | **빈 결과가 실패가 아니다** — 인식된 문항이 0개면 「공고에서 항목을 찾지 못했어요」와 함께 직접 쓰는 자리를 연다(F4-1) | 없음 | 오프라인과 같은 길 · 생성 중 503(LLM 장애)은 스낵바 | 셸에 알림 → 로그인 |
| 지원서 문항 편집 시트 | 없음 — 서버를 부르지 않는다 | 없음 | — | 없음 | 없음 | 없음 |
| 분석·지원서·알림 탭 | 미구현 | 미구현 | `CareerCompassEmptyState`(자리표시자) | 미구현 | 미구현 | 미구현 |

`feature/editor` · `feature/foryou` · `feature/notification` 은 **Kotlin 파일이 하나도 없다** — 빌드 스크립트만 있는 자리표시자다. 앱 셸이 `PlaceholderTabScreen` 으로 대신 그린다.
`feature/profile` 은 마이 홈(#175) · 프로필 편집(#176 · #177) · 경험 카드 목록과 편집 시트(#178 · #179) · 과거 지원서 목록(#180)이 서 있고, 지원서 등록(#181)과 알림 설정(#196)만 셸의 `PlaceholderScreen` 이다.

경험 카드 편집은 **저장 실패에 시트를 닫지 않는다.** 친 값을 버리면 다시 쳐야 하고, 전체 교체가 아니라 다시 누르면 되는 요청이다. 시트는 저장 중에만 잠긴다 — 숨긴 뒤 닫기를 무시하면 빈 창만 남는다.

경험 카드 목록의 **빈 결과는 사유가 둘이다.** 필터 때문에 비었으면 되돌릴 조작(「전체 보기」)이 그 화면에 있고, 정말 없으면 만들러 가는 길을 연다 — 같은 문장을 쓰면 필터를 걸어 둔 사용자는 자기 카드가 사라진 줄 안다. 상한(30장)은 서버가 422 를 줄 때까지 기다리지 않고 목록 머리에 `현재 / 상한` 으로 늘 보인다.

프로필 편집은 **저장 실패의 처분이 API 모양에 따라 갈린다.** 기본 정보(`PATCH`)는 부분 수정이라 입력을 그대로 두고 다시 누르게 하고, 희망 직무·관심 태그(`PUT` 전체 교체)는 부분 성공이 없으므로 서버 값을 다시 읽어 화면을 되돌린다 — 화면만 새 값으로 남으면 사용자는 저장된 줄 안다(#177).

프로필 편집의 **저장 실패는 화면을 덮지 않는다.** 사용자가 방금 친 값이 칸에 남아 있으므로 실패 화면으로 덮으면 다시 쳐야 한다. 서버가 어느 칸을 거부했는지 말해 주면(400 `INVALID_INPUT` 의 `error.field`) 그 칸에 문구를 붙이고, 필드를 모르면 칸을 짚지 않고 스낵바로만 알린다 — 엉뚱한 칸에 빨간 줄을 그으면 멀쩡한 값을 고치게 된다.

지원서 작성 첫 화면의 **빈 결과는 실패가 아니다.** 공고에서 문항을 하나도 못 찾았거나 파싱이 아직 안 끝난 경우인데, 둘 다 「항목을 못 찾았습니다」로 끝내면 그 공고는 영영 초안을 못 만든다. 명세가 정한 처리대로 직접 쓰는 자리를 연다(F4-1). 그래서 이 화면에서는 빈 결과 칸이 **막다른 길이 아니라 입력 자리**다.

생성 실패도 화면을 덮지 않는다. 사용자가 방금 고친 문항이 목록에 남아 있으므로 실패 화면으로 덮으면 처음부터 다시 쓴다 — 프로필 편집의 저장 실패와 같은 방향이다.

마이 홈의 실패 처분은 **캐시가 있느냐**로 갈린다. 캐시가 있으면 이름·소속·완성도가 여전히 참이므로 화면을 덮지 않고 스낵바 한 줄로만 알리고, 캐시가 없을 때만 실패 화면이 화면 한 장을 차지한다 — 앱 시작이 캐시로 목적지를 정하는 규칙과 같은 방향이다(§5 「오프라인 시작」). 문구는 새로 짓지 않고 실패 표(#204)의 행을 그대로 그린다.

표에 열이 없는 상태가 하나 더 있다 — **사유를 특정하지 못한 실패**다. 위 다섯 사유 중 어느 것도 아닌 실패가 전부 여기로 접히므로 실제로는 가장 자주 뜨는 실패 화면이다. §2.7 에 따로 적었다.

---

## 2. 상태별 상세

### 2.1 오프라인 · 네트워크 실패

판정 근거는 `CoreDataFailure.NetworkUnavailable` 하나다 — data 계층이 `IOException` 을 **전부** 이 값으로 접는다([`ApiFailureMapper.kt`](../../core/network/src/main/kotlin/com/careercompass/core/network/failure/ApiFailureMapper.kt)). 사용자에게는 cleartext 차단도 TLS 회귀도 「네트워크 오류」라는 판정이다. 단 하나 더 갈라 보는 것이 **타임아웃**(`NetworkUnavailable.isTimeout`)이고, 그 구분을 쓰는 화면은 게시판 등록뿐이다.

| 화면 | 컴포넌트 | 문구(리소스 = 값) | 버튼 → 동작 |
|---|---|---|---|
| 피드 홈 | `CareerCompassNetworkErrorState` | `core_ui_state_network_title` = 연결할 수 없어요 / `core_ui_state_network_description` = 인터넷 연결을 확인하고 다시 시도해 주세요 | 「다시 시도」 → `FeedViewModel.retry()`(**지금 조건 그대로** 재조회) · 「오프라인 모드로 보기」 → `showOfflineSnapshot()` (**스냅샷이 있을 때만 그린다**) · 조건이 걸려 있으면 하단에 「조건 지우고 다시 보기」가 따로 붙는다(§4) |
| 공고 상세 | `CareerCompassNetworkErrorState`(#222 — 전에는 손으로 그린 `Column` 에 한 줄 문장) | 부품 고정 = 연결할 수 없어요 / 인터넷 연결을 확인하고 다시 시도해 주세요 | 「다시 시도」 → `PostingDetailEvent.RetryClicked` · 오프라인 모드 **없음**(스냅샷을 저장하지 않는다) |
| 원문 보기 | `CareerCompassNetworkErrorState` | 부품 고정 문구 | 「다시 시도」 → `PostingRawViewModel.retry()` · 오프라인 모드 **없음**(원문은 스냅샷을 저장하지 않는다) |
| 내 게시판 | `CareerCompassNetworkErrorState` | 부품 고정 문구 | 「다시 시도」 → `BoardListViewModel.retryLoad()` · 오프라인 모드 **없음**(목록은 스냅샷을 저장하지 않는다) · 상단 바의 뒤로가기는 남는다 |
| 게시판 등록 — 감지 중 끊김 | 스낵바 | 실패 표의 `NoConnection` 행(#204) = 연결할 수 없어요. 인터넷 연결을 확인하고 다시 시도해 주세요 | 없음 — 감지 상태는 `Idle` 로 되돌아가고 「구조 분석하기」가 다시 눌린다 |
| 게시판 등록 — 감지 타임아웃 | `BoardDetectionTimedOutBox`(경고 톤 상자) | `feed_board_detect_timeout_title` = 분석이 오래 걸려 멈췄어요 / `feed_board_detect_timeout_description` = 사이트 응답이 늦어 기다리기를 그만뒀어요. 지원되지 않는 게시판이라는 뜻은 아니니 잠시 후 다시 시도해 주세요 | 「다시 시도」(`feed_board_register_retry`) → `DetectClicked` |
| 로그인 | `OnboardingErrorCard`(하단 인라인 배너) | `onboarding_login_failure_network` = 네트워크 연결을 확인한 뒤 다시 시도해 주세요 | 「닫기」만 — 재시도는 소셜 로그인 버튼이 그대로 살아 있어 그것이 대신한다 |
| 지문 로그인 | `OnboardingErrorCard` | 지문 실패 사유별(`onboarding_biometric_failure_*`) | 「닫기」 · 「다른 방법으로 로그인」은 상시 |
| 온보딩 Step 1~4 | `OnboardingFlowFailureHost` 의 하단 배너 | 실패 표의 `NoConnection` 행(#204, #236 에서 옮김) = 연결할 수 없어요. 인터넷 연결을 확인하고 다시 시도해 주세요 · 타임아웃은 `Timeout` 행 | 「닫기」만 — 재시도는 단계 하단의 「다음」이 대신한다 |
| 온보딩 문서 업로드 카드 | 카드 상태 줄 | `onboarding_upload_failed_network` = 연결 실패 (`onboarding_step4_document_failed_retry` = `%1$s · 재시도` 틀에 끼워진다) | 「재시도」 → 그 문서만 다시 올린다 |

**같은 사실을 네 가지로 말하고 있었다.** 「연결할 수 없어요 / 인터넷 연결을 확인하고 다시 시도해 주세요」(부품) · 「공고를 불러오지 못했어요. 네트워크 연결을 확인해 주세요」(상세) · 「네트워크 연결을 확인한 뒤 다시 시도해 주세요」(온보딩) · 「네트워크에 연결할 수 없어요. 연결을 확인해 주세요」(게시판 등록). #204 가 [실패 표](error-copy.md)로 통일했다 — 부품·상세·게시판 등록이 모두 `NoConnection` 행 하나를 읽고, 온보딩도 #236 에서 옮겼다(화면 고유 사유인 파일 형식·크기만 온보딩 문자열로 남는다).

### 2.2 로딩 · 분석 중

Figma 09 의 「분석 중」은 화면 한 장인데, **앱에는 화면을 통째로 덮을 자리가 없다** — 덮으면 사용자가 방금 누른 버튼과 입력이 사라지기 때문이다. 그래서 부품에 카드 안 표현(`CareerCompassStatePresentation.Inline`)을 열어 공고 상세의 적합도 카드에 넣었고(#221), 나머지 진행 표시는 손으로 그린 인라인이다.

| 화면 · 자리 | 컴포넌트 | 문구(리소스 = 값) | 행동 |
|---|---|---|---|
| 앱 시작 | 시스템 스플래시(`installSplashScreen`) | 없음 | 없음 — 시작 목적지가 확정될 때까지 유지한다. 네트워크 왕복은 이 경로에 두지 않는다 |
| 피드 홈 — 첫 조회 | `FeedLoading`(`FeedContent.kt`, 손으로 그림) | `feed_loading` = 공고를 불러오는 중이에요 | 없음(기다리는 상태) |
| 피드 홈 — 이어 읽기 | `FeedLoadingMoreRow` | `feed_loading_more` = 공고를 더 불러오는 중이에요 | 없음 |
| 피드 홈 — 이어 읽기 멈춤 | `FeedLoadMoreActionRow` | `feed_load_more_paused` = 여기까지 찾았어요 | 「더 찾아보기」(`feed_load_more_action`) → `LoadMoreSelected` |
| 피드 홈 — 이어 읽기 실패 | `FeedLoadMoreActionRow` | `feed_load_more_failed` = 공고를 더 불러오지 못했어요 | 「다시 시도」(`feed_error_retry`) → `LoadMoreSelected` |
| 피드 카드 — 적합도 | `FeedSuitabilityChip` | `feed_suitability_analyzing` = 분석 중 | 없음 |
| 공고 상세 — 화면 | `FeedLoadingContent` | `feed_posting_detail_loading` = 공고를 불러오는 중이에요 | 없음 |
| 공고 상세 — 적합도 카드 | `CareerCompassAnalyzingState(presentation = Inline)` — 카드 제목이 `liveRegion = Polite` + `stateDescription`(상태 요약)을 들어 스스로 바뀔 때 스크린 리더가 알린다(#239) | `feed_posting_detail_analyzing` = AI가 분석 중이에요 / `feed_posting_detail_analyzing_description` = 적합도가 나오면 이 자리에 바로 보여 드릴게요 | 없음 — 화면이 스스로 5초 × 4회 조용히 다시 읽는다(`PostingDetailViewModel`, #221). 「분석 중」과 「프로필 미입력」은 판정(`judgeSuitability`)이 가르고, 재조회도 「분석 중」일 때만 돈다 |
| 공고 상세 — 적합도 카드 · 자동 재조회 소진 | 카드 안 문구 + 버튼(`PostingSuitabilityPending`) — 진행 표시를 거둔다 | `feed_posting_detail_analysis_pending_title` = 아직 적합도가 나오지 않았어요 / `feed_posting_detail_analysis_pending_description` = 분석이 오래 걸리는 공고도 있고, 끝내 분석되지 않는 공고도 있어요.(줄바꿈)「원문 보기」로 내용을 먼저 확인할 수 있어요 | 「다시 확인」(`feed_posting_detail_analysis_recheck`) → `SuitabilityRecheckClicked` — 한 번만 더 묻고, 여전히 없으면 다시 이 상태 |
| 원문 보기 | `FeedLoadingContent` | `feed_posting_detail_loading` = 공고를 불러오는 중이에요 (**상세 문구를 그대로 재사용한다**) | 없음 |
| 내 게시판 | `FeedLoadingContent` | `feed_board_list_loading` = 게시판을 불러오는 중이에요 | 없음 |
| 게시판 등록 — 구조 감지 | `BoardDetectingRow` | `feed_board_register_detecting` = 게시글 구조를 분석하고 있어요 / `feed_board_register_detecting_hint` = 사이트 응답 속도에 따라 1분 넘게 걸릴 수 있어요 | 없음 — 「구조 분석하기」가 잠긴다 |
| 게시판 등록 — 제출 | `BoardRegisterSubmittingRow`(하단, 스크롤 밖) | `feed_board_register_submitting` = 게시판을 등록하고 있어요 / `feed_board_register_submitting_hint` = 끝나면 게시판 목록으로 돌아가요 | 없음 — 이탈도 막힌다(뒤로가기 → `feed_board_register_submit_in_progress` 스낵바) |
| 게시판 수정 시트 | 저장 중 표시 | `feed_board_edit_saving` = 변경 내용을 저장하고 있어요 | 없음 — 시트가 닫히지 않는다 |
| 로그인 | `LoginProgress` | `onboarding_login_loading` = 로그인하는 중이에요 | 없음 — 소셜 버튼이 잠긴다 |
| 지문 로그인 | 버튼 안 진행 표시 | `onboarding_biometric_authenticating_state` = 지문 확인 중 (`stateDescription`) | 없음 |
| 온보딩 Step 1~4 | **진행 표시 없음** | 없음 | 없음 — `isSubmitting` 으로 「다음」만 잠근다 |
| 온보딩 문서 분류 | 카드 상태 줄 | `onboarding_step4_document_processing` = 분류 중 | 없음 |

「분석 중」이 진행률(`progress`·`progressLabel`)을 말할 수 있는 자리는 아직 없다 — 서버가 진행률을 주지 않는다. 그래서 모든 진행 표시가 **무한 인디케이터**다.

**「분석 중」이 끝나는 길**은 공고 상세에만 있다(#221) — 5초 × 4회 자동 재조회, 그 뒤 「다시 확인」. 파싱이 영구 실패한 공고는 계약에 그 상태를 나타낼 자리가 없어(#200) 영원히 「분석 중」이다. 그래서 다 쓴 뒤의 문구는 「실패했어요」도 「곧 됩니다」도 말하지 않고, 하단 바에 늘 있는 「원문 보기」를 대안으로 가리킨다.

### 2.3 빈 결과 (사유별)

빈 결과의 사유를 가르는 판정은 피드 홈에만 있다([`FeedViewState.toEmptyReason`](../../feature/feed/presentation/src/main/kotlin/com/careercompass/feature/feed/presentation/feed/FeedStateMapping.kt), 사유 정의는 [`FeedEmptyReason`](../../feature/feed/presentation/src/main/kotlin/com/careercompass/feature/feed/presentation/FeedContract.kt)). 나머지 화면은 빈 결과가 한 가지 뜻뿐이라 사유를 가를 것이 없다.

#### 피드 홈 — 우선순위와 사유별 문구

겹칠 때는 **하나만** 고른다. 순서는 `OfflineSnapshot` > `NoBoards` > `MoreAvailable` > `Search` > `Filter` > `NotCollected` 이고, 기준은 「그 조건을 되돌리면 결과가 달라지는가」다(#127).

| 사유 | 판정 | 문구(리소스 = 값) | 버튼 → 동작 |
|---|---|---|---|
| `OfflineSnapshot` | `isOffline` | `feed_empty_offline_title` = 저장해 둔 목록에는 공고가 없어요 / `feed_empty_offline_description` = 연결되면 최신 공고를 다시 불러와요 | **없음(기다리는 상태)** — 되돌릴 조건이 없고, 조건을 되돌리는 행동은 곧 재조회라 오프라인에서 권하면 실패 화면으로 튄다 |
| `NoBoards` | `boardsLoaded && boards.isEmpty()` | `feed_empty_no_boards_title` = 아직 등록한 게시판이 없어요 / `feed_empty_no_boards_description` = 학교 공지·채용 사이트를 등록하면 공고를 모아 여기에 보여 드려요 | 「게시판 등록하기」(`feed_empty_no_boards_action`) → `BoardRegisterSelected` → 게시판 등록 화면 |
| `MoreAvailable` | `hasNext`(커서가 남았다) | `feed_empty_more_available_title` = 여기까지는 찾지 못했어요 / `feed_empty_more_available_description` = 최근 공고부터 차례로 훑어봤어요. 더 찾아보면 나올 수 있어요 | 「더 찾아보기」(`feed_load_more_action`) → `LoadMoreSelected` |
| `Search` | `query.hasSearchQuery` | `feed_empty_search_title` = ‘%1$s’ 검색 결과가 없어요(조회에 실린 검색어) / `feed_empty_search_description` = 다른 낱말로 찾거나 검색어를 지워 보세요 | 「검색어 지우기」(`feed_empty_search_action`) → `SearchQueryChanged("")` |
| `Filter` | `hasActiveFilter`(시트 조건 + 카테고리 칩) | `feed_empty_filter_title` = 필터에 맞는 공고가 없어요 / `feed_empty_filter_description` = 걸어 둔 조건을 풀면 더 많은 공고를 볼 수 있어요 | 「필터 초기화」(`feed_empty_filter_action`) → `FilterResetSelected` |
| `NotCollected` | 나머지 | `feed_empty_not_collected_title` = 아직 모인 공고가 없어요 / `feed_empty_not_collected_description` = 등록한 게시판에 새 글이 올라오면 여기에 쌓여요. 수집이 도는 게시판이 있으면 `feed_empty_not_collected_description_with_notice` 로 한 줄을 덧붙인다 — `feed_empty_collect_notice` = 등록한 게시판을 %1$s 확인하고 있어요(주기가 갈리면 `feed_empty_collect_notice_mixed` = 가장 자주 보는 게시판을 %1$s 확인하고 있어요) | **없음(기다리는 상태)** — 눌러도 목록이 달라지지 않는 버튼은 기다리라는 안내와 모순된다 |

> **#206 에서 정리 중** — 「선택한 게시판이 이미 삭제됐다」가 새 사유로 들어오고 `Filter` 보다 앞에 놓인다. 그때 이 표에 행 하나와 우선순위 한 자리가 바뀐다.

#### 그 밖의 화면

| 화면 | 컴포넌트 | 문구(리소스 = 값) | 버튼 → 동작 |
|---|---|---|---|
| 내 게시판 | `BoardListEmpty` — **부품이 아니라 `BoardListContent.kt` 가 손으로 그린 `Column`**(삽화가 🗂 `feed_icon_board_empty` 라 🔍 고정인 부품을 못 썼다) | `feed_board_list_empty_title` = 등록된 게시판이 없어요 / `feed_board_list_empty_description` = 학교 공지·채용 사이트 URL 을 등록하면 자동으로 공고를 모아요 | 「게시판 등록하기」(`feed_board_list_empty_action`) → `AddBoardClicked` |
| 학교 선택 시트 | 맨 `Text` | `onboarding_school_picker_empty` = 검색 결과가 없어요 | 「목록에 없어요. 직접 입력할게요」(`onboarding_school_picker_direct_action`) → 직접 입력 모드. **검색어를 친 뒤에만 뜬다**(`isDirectInputOffered`) |
| 원문 보기 — 본문이 빈 경우 | 본문 자리를 문구로 대체 | `feed_posting_raw_empty_content` = 본문을 가져오지 못했어요 | 없음 — 상단의 「원본 링크 열기」가 대신한다 |
| 공고 상세 — 축별 점수가 없음 | 카드 안 캡션 | `feed_posting_detail_breakdown_unavailable` = 축별 세부 점수는 아직 없어요 | 없음 — 「모름」이지 「미충족」이 아니라 0점 축을 그리지 않는다 |
| 공고 상세 — 지원서 항목 없음 | 섹션 안 문구 | `feed_posting_detail_form_questions_empty` = 자동 인식된 항목이 없어요 | 없음 |
| 자리표시자 탭(분석·지원서·알림·마이) | `CareerCompassEmptyState` | `placeholder_analysis_title` = 분석 탭을 준비하고 있어요 등 / `placeholder_description` = 곧 이용할 수 있어요 | 없음 |

### 2.4 권한 거부

**앱 전체에 구현이 없다.**

- `POST_NOTIFICATIONS` 는 [`app/src/main/AndroidManifest.xml`](../../app/src/main/AndroidManifest.xml) 에 선언돼 있지만 **런타임 권한을 요청하는 코드가 어디에도 없다**(`rememberLauncherForActivityResult(RequestPermission)` · `checkSelfPermission` 호출 0건).
- 문구는 이미 있는데 아무도 안 읽는다 — `notification_permission_denied_message` = 알림이 꺼져 있어 새로운 소식을 받을 수 없어요 / `notification_permission_denied_action` = 설정. 참조하는 코드가 0곳이다.
- 부품 `CareerCompassPermissionDeniedState` 도 호출처가 0곳이다.

곧 붙을 자리는 `feature/notification` 모듈인데 그 모듈에는 Kotlin 파일이 아직 없다. 런타임 요청 자체는 앱 셸 몫이다.

> **#197 에서 정리 중** — FCM 수신과 권한 동의 흐름을 붙이는 이슈가 이 부품의 자리를 명시하고 있다(「앱을 켜자마자 묻지 않는다 — 알림이 왜 필요한지 아는 자리에서 묻는다」·거부 기록은 기기당이 아니라 **계정별**). 그때 이 절이 표로 채워진다.

### 2.5 서버 점검 (503 `LLM_UNAVAILABLE`)

판정은 `CoreDataFailure.ServiceUnavailable` → `FeedFailureReason.Maintenance`([`FeedFailureReason.kt`](../../feature/feed/presentation/src/main/kotlin/com/careercompass/feature/feed/presentation/shared/model/FeedFailureReason.kt)). 세 화면이 [`FeedMaintenanceState`](../../feature/feed/presentation/src/main/kotlin/com/careercompass/feature/feed/presentation/shared/component/FeedMaintenanceState.kt) 하나를 공유해 **같은 사실을 같은 문구로** 말한다.

| 화면 | 컴포넌트 | 문구(리소스 = 값) | 버튼 → 동작 |
|---|---|---|---|
| 피드 홈 | `FeedMaintenanceState` | 실패 표의 `ServiceUnavailable` 행(#204) — `core_ui_failure_service_unavailable_title` = 서비스가 잠시 점검 중이에요 / `core_ui_failure_service_unavailable_description` = AI 분석 서버를 손보고 있어요.(줄바꿈)조금 뒤에 다시 열어 주세요 / 배지는 피드 몫으로 남는다(`feed_maintenance_status` = 점검 진행 중) | 「새로고침」 → `retry()` · 「오프라인 모드로 보기」 → `showOfflineSnapshot()`(**스냅샷이 있을 때만**) · 조건이 걸려 있으면 「조건 지우고 다시 보기」(§4) |
| 공고 상세 | `FeedMaintenanceState` | 같음 | 「새로고침」 → `RetryClicked` · 오프라인 모드 **없음** |
| 내 게시판 | `FeedMaintenanceState` | 같음 | 「새로고침」 → `retryLoad()` · 오프라인 모드 **없음** |
| 원문 보기 | `FeedMaintenanceState` (#212 에서 붙임 — 실패 상태가 `FeedFailureReason` 을 든다) | 같음 | 「새로고침」 → `retry()` · 오프라인 모드 **없음**(원문은 스냅샷을 저장하지 않는다) |
| 게시판 등록 — 감지 | `FeedMaintenanceNotice` — 화면에 남는 인라인 상자(`BoardDetectionState.Maintenance`). 폼이 살아 있어야 해 화면 한 장을 쓸 수 없다 | 실패 표의 `ServiceUnavailable` 행(#204) — `FeedMaintenanceState` 와 같은 제목·본문 | **버튼 없음** — 서버가 돌아와야 답이 달라진다(§3). 위의 「구조 분석하기」는 그대로 눌린다 |
| 게시판 등록 — 제출 | 점검 스낵바(`BoardRegisterMessage.Maintenance`) | 실패 표의 `ServiceUnavailable` 행 제목(#204) — `core_ui_failure_service_unavailable_title` = 서비스가 잠시 점검 중이에요(본문은 줄바꿈을 품어 스낵바에는 제목만) | 스낵바라 버튼 없음 — 감지 결과와 폼은 그대로 남는다 |
| 온보딩 Step 1~4 | 하단 배너 — `OnboardingFailureReason.Maintenance` 로 갈라 실패 표의 `ServiceUnavailable` 행을 읽는다(#236, 전에는 일반 서버 오류로 접혔다) | `core_ui_failure_service_unavailable_title` = 서비스가 잠시 점검 중이에요 / 본문은 표와 같다 | 「닫기」 |

문의처(`contactLabel`)는 **넘기지 않는다** — 아직 공개된 창구가 없고, 없는 주소를 적으면 사용자를 막다른 길로 보낸다. 점검은 리포팅에서도 「보고할 결함」이 아니다(네트워크 단절과 같은 취급, #101).

### 2.6 세션 만료 (401)

**실패 화면을 그리지 않는다.** 401 은 화면이 아니라 이동으로 답한다 — 네트워크 계층이 세션 정리를 이미 끝냈고, 화면이 할 수 있는 일은 로그인으로 보내는 것뿐이기 때문이다. 그래서 `FeedFailureReason` 에 401 이 없다.

정확히는 401 도 `toFeedFailureReason()` 의 `else` 로 떨어져 `Generic` 실패 상태가 **함께** 실린다. 다만 같은 자리에서 `sessionEnded` 가 켜지고(`FeedViewModel.recordFailure`) Screen 의 `LaunchedEffect` 가 곧바로 화면을 걷어내므로 그 실패 화면은 사용자에게 남지 않는다. **이동 배선이 곧 답이고, 사유 분기는 답이 아니다** — 온보딩이 사유를 `SessionExpired` 로 정확히 좁혀 놓고도 이동이 없어 막다른 길이던 것을 #211 이 같은 배선으로 고쳤다. 고치면서 `OnboardingFailureReason` 에서 `SessionExpired` 를 아예 뺐다 — 열거형에 만료가 없으니 온보딩 화면에는 만료를 그리는 자리도 없다.

| 화면 | 무엇이 일어나나 | 문구(리소스 = 값) |
|---|---|---|
| 피드 홈 · 공고 상세 · 원문 보기 · 내 게시판 · 게시판 등록 | `sessionEnded = true` → Screen 이 `onSessionEnded` 호출 → 앱 셸(`MainViewModel.onSessionEnded(Expired)`) → NavHost 를 새로 만들고 로그인 화면으로 | 이동 자체에는 문구가 없다 |
| 앱 셸 자신(콜드 스타트 세션 판정 · 메인 뒤 백그라운드 확인) | `SessionEndCause.Expired` 를 실어 로그인으로 | — |
| 지문 로그인 | 세션 검증이 만료를 알리면 그래프가 지문 화면을 걷어내고 로그인으로(`navigateToLoginAfterSessionExpiry`) | — |
| 로그인 화면 | 도착했을 때 **만료로 온 경우에만** 배너가 켜져 있다(`AppShellLaunch.sessionExpiryNotice`). 「닫기」를 누르거나 다시 로그인을 시도하면 꺼진다 | `onboarding_failure_session_expired` = 로그인이 만료됐어요. 다시 로그인해 주세요 |
| 온보딩 Step 1~4 | 사용자가 시킨 저장·업로드·삭제가 401 을 물면 `sessionEnded = true` → Step Screen 이 `OnboardingNavActions.onSessionEnded` → 앱 셸(`Expired`) — 피드와 같은 길(#211). **화면 진입만으로 도는 자동 조회의 401 은 기록만 남긴다** — 세션 정리가 실패해 토큰이 남은 기기에서 「만료 → 재계산 → 다시 온보딩 → 같은 조회」가 손 없이 도는 고리를 막는다. 입력 초안은 NavHost 와 함께 버린다(다음 로그인이 같은 계정이라는 보장이 없다) | 이동 자체에는 문구가 없다 — 배너를 그리지 않는다(로그인 화면이 켠다) |
| 온보딩 문서 업로드 카드 | 카드를 실패로 칠하지 않는다 — 화면을 떠나므로 읽힐 자리가 없고, 「재시도」는 같은 401 을 다시 무는 막다른 행동이다(#211) | — (`onboarding_upload_failed_session_expired` 는 지웠다) |

로그아웃(`SessionEndCause.LoggedOut`)은 만료와 갈라 둔다 — 사용자가 방금 누른 결과라 로그인 화면이 덧붙일 말이 없다. 한 번의 재계산에 둘이 겹치면 로그아웃이 이긴다.

### 2.7 사유를 특정하지 못한 실패 (Generic)

`FeedFailureReason.Generic` — 네트워크 단절도 점검도 아닌 나머지 전부다. 400·403·404·409·422·429·500 이 여기로 접히고, 401 도 상태로는 여기 실리지만 §2.6 의 이동이 먼저 화면을 걷어낸다. **화면 한 장을 쓰는 자리는 `CareerCompassFailureState` 로 그린다**(#222) — 전에는 「검색 결과 없음」 부품을 돌려 써 삽화가 🔍 였고, 사용자는 서버가 500 을 준 것을 자기 조건이 잘못된 것으로 읽었다. 이제 삽화(⚠️)·문구·행동이 빈 결과와 갈리고, 버튼은 표의 행동이 있을 때만 붙는다.

| 화면 | 컴포넌트 | 문구(리소스 = 값) | 버튼 → 동작 |
|---|---|---|---|
| 피드 홈 | `CareerCompassFailureState` | 실패 표의 `Unexpected`×`Posting` 행(#204) = 공고를 불러오지 못했어요 / 잠시 후 다시 시도해 주세요 | 「다시 시도」(`feed_error_retry`) → `retry()` · 조건이 걸려 있으면 「조건 지우고 다시 보기」도 붙는다(§4 — Generic 도 조건 탓일 여지가 있다) |
| 내 게시판 | `CareerCompassFailureState` | 실패 표의 `Unexpected`×`Board` 행(#204) = 게시판을 불러오지 못했어요 / 잠시 후 다시 시도해 주세요 | 「다시 시도」(`core_ui_state_retry`) → `retryLoad()` |
| 공고 상세 | `CareerCompassFailureState`(#222 — 전에는 손으로 그린 `Column` 에 한 줄 문장) | 실패 표의 `Unexpected`×`Posting` 행(#204) = 공고를 불러오지 못했어요 / 잠시 후 다시 시도해 주세요 | 「다시 시도」(`feed_posting_detail_retry`) — 표의 `isRetryable` 이 거짓이면 버튼 없음 |
| 원문 보기 | `CareerCompassFailureState` | `feed_posting_raw_error_title` = 원문을 불러오지 못했어요 / `feed_posting_raw_error_description` = 잠시 후 다시 시도해 주세요 | 「다시 시도」(`feed_posting_raw_error_retry`) |
| 온보딩 Step 1~4 | 하단 배너 | 실패 표의 `Unexpected` 행(#204·#236) = 문제가 생겼어요. 잠시 후 다시 시도해 주세요 — 사유가 좁혀지면 그 행(입력값·상한은 단계가 아는 문맥으로) | 「닫기」 |
| 게시판 등록 | 스낵바 | `feed_board_register_detect_failed` / `feed_board_register_failed` (503 은 #212 에서 빠져나갔다) | 없음 |

> **#204 에서 정리됐다** — API_SPEC §9 의 에러 코드 14종이 [`docs/spec/error-copy.md`](error-copy.md) 의 표로 모였다. 코드마다 제목·본문과 「사용자가 할 수 있는 일」이 정해져 있고, **재시도해도 답이 갈리지 않는 실패에는 재시도가 붙지 않는다** — 상한 초과는 「정리하러 가기」로, 프로필 미완성은 「프로필 입력하기」로 갈린다. 화면이 `FailureSurface` 를 넘기면 표가 명사까지 채운다(공고 / 게시판). 여기 남은 Generic 행은 **표의 `Unexpected` 행**이다 — `INTERNAL_ERROR` 와 사유를 확인하지 못한 실패만 들어온다.

---

## 3. 「할 수 있는 일이 있는 상태」와 「기다리는 수밖에 없는 상태」

**규칙**: 사용자가 되돌릴 수 있는 조건이 원인이면 그 조건을 되돌리는 버튼이 반드시 있다. 원인이 서버·네트워크·시간이면 버튼을 만들지 않는다 — 눌러도 아무 일 없는 버튼은 「내가 뭘 잘못했나」를 되짚게 만든다.

| 상태 | 어느 쪽인가 | 행동이 있어야 하는 이유 / 없어야 하는 이유 | 현재 |
|---|---|---|---|
| 빈 피드 · 게시판 0개 | **할 수 있다** | 게시판을 등록해야 공고가 모인다 | 「게시판 등록하기」 ✅ |
| 빈 피드 · 검색어 | **할 수 있다** | 검색어를 지우면 결과가 달라진다 | 「검색어 지우기」 ✅ |
| 빈 피드 · 필터 | **할 수 있다** | 조건을 풀면 결과가 달라진다 | 「필터 초기화」 ✅ |
| 빈 피드 · 더 남음 | **할 수 있다** | 아직 안 읽은 페이지가 있다 | 「더 찾아보기」 ✅ |
| 빈 피드 · 수집 전 | **기다린다** | 첫 수집을 기다리는 중 — 누를 것이 없다. 대신 언제쯤인지 한 줄로 말한다 | 버튼 없음 ✅ |
| 빈 피드 · 오프라인 스냅샷 | **기다린다** | 되돌릴 조건이 없고, 재조회는 실패 화면으로 튄다 | 버튼 없음 ✅ |
| 네트워크 실패 | **할 수 있다**(연결 확인 후 재시도 / 스냅샷 보기) | 재시도는 사용자가 연결을 살린 뒤 눌러야 뜻이 있다 | 「다시 시도」 + 스냅샷 있으면 「오프라인 모드로 보기」 ✅ |
| 서버 점검 | **기다린다** — 단, **조건 탓일 수 있으면 할 수 있다** | 503 `LLM_UNAVAILABLE` 은 「그 조건은 지금 못 한다」는 답이라 적합도 정렬·최소 점수를 풀면 갈린다 | 「새로고침」 + 조건이 있으면 「조건 지우고 다시 보기」 ✅ · 게시판 등록의 감지에는 조건이 없어 **버튼 없이 알리기만 한다**(#212) ✅ |
| 세션 만료 | **할 수 있다**(다시 로그인) | 로그인 화면에 닿아야 할 수 있는 일이 된다 | 피드·상세·게시판 ✅ · 온보딩 ✅(#211) |
| 알림 권한 꺼짐 | **할 수 있다**(설정에서 켜기) | 권한은 사용자만 켤 수 있다 | **상태 자체가 없음 ❌ #197** |
| 감지 타임아웃 | **할 수 있다** | 사이트가 느렸을 뿐이라 다시 시도하면 된다 | 「다시 시도」 ✅ |
| 감지 실패(로그인 필요·SPA·차단) | **기다릴 수도 없다** — 되돌릴 길이 사용자 손에 없다 | 재시도해도 같은 답이 온다 | 재시도 버튼 없음 ✅ (#204 에서 뗐다. URL 입력란과 「구조 분석하기」가 위에 남아 주소를 고칠 길은 열려 있다) |
| 로딩 · 분석 중 | **기다린다** | 진행 중인 요청을 사용자가 앞당길 수 없다 | 버튼 없음 ✅ — 공고 상세의 자동 재조회가 **멈춘 뒤에는** 「다시 확인」(#221). 누르는 것이 실제로 상태를 바꾸므로(누르지 않으면 아무 일도 안 일어난다) 이 규칙과 충돌하지 않는다 |

### 빈 칸으로 드러난 결함

1. ~~**온보딩에서 세션이 만료되면 나갈 길이 없다**~~ — **#211 에서 고침.** 배너가 「다시 로그인해 주세요」라고 말하는데 로그인으로 가는 길이 화면에 없었다(#144 와 같은 유형). 이제 Step 1~4 도 피드와 같은 `sessionEnded` 배선으로 앱 셸에 올리고, 배너는 그리지 않는다.
2. ~~**원문 보기와 게시판 등록이 서버 점검을 가르지 않는다**~~ — **#212 에서 고침.** 원문 보기는 실패를 `FeedFailureReason` 으로 들어 `FeedMaintenanceState` 를 쓰고, 게시판 등록은 서버가 알린 감지 결과(`detect_status`)와 요청 자체의 실패를 갈라 503 을 `BoardDetectionState.Maintenance` 로 남긴다.
3. **알림 권한 꺼짐 상태가 통째로 없다** — Figma 09 의 다섯 장 중 한 장이 앱에 존재하지 않고, 문구와 부품만 미리 만들어 놓고 방치돼 있다. → **#197 에서 정리 중**(그 이슈의 완료 조건에 `CareerCompassPermissionDeniedState` 를 쓰는 자리가 명시돼 있다). 새 이슈를 내지 않는다.
4. ~~**재시도해도 소용없는 실패에 재시도 버튼이 붙어 있다**(게시판 감지의 로그인 필요·SPA·차단)~~ — **#204 에서 고쳤다.** 「목록 페이지 주소인지 확인해 주세요」(`Failed`)만 재시도를 남긴다 — 거기서는 주소를 고치면 답이 실제로 갈린다.

---

## 4. 실패 화면에서 원인이 된 조건을 되돌릴 길

#144 가 피드에서 고친 결함이다: 정렬을 「적합도순」으로 바꾸거나 최소 적합도를 걸어 503 을 맞으면 화면 **전체**가 점검 안내로 바뀌어 검색칸·필터·정렬·칩이 함께 사라졌고, 「새로고침」은 같은 조건을 그대로 다시 보내 같은 실패를 되풀이했다.

**지금의 규칙** — 실패 화면 아래에 「조건 지우고 다시 보기」를 붙인다. 근거 **둘이 함께** 서야 연다(`FeedViewState.canResetFailedQuery`).

1. 되돌릴 조건이 실제로 걸려 있다(`!query.isDefault` — 검색어·필터·정렬·카테고리를 모두 센다).
2. 그 실패가 조건 탓일 여지가 있다(`FeedFailureReason.isQueryAttributable` — `Maintenance`·`Generic` 은 참, `NetworkUnavailable` 은 거짓).

문구는 `feed_failure_query_reset_notice` = 걸어 둔 검색어·필터·정렬 때문일 수 있어요 / `feed_failure_query_reset_action` = 조건 지우고 다시 보기 이고, 동작은 `resetQueryAndRetry()` — 조건을 기본값으로 되돌리고 **그 자리에서 다시 읽는다**. 사유 화면의 행동(새로고침·오프라인 모드)과 한 줄에 두지 않고 아래에 따로 세운다 — 저 둘은 「지금 조건 그대로」 하는 일이고 이것만 조건을 바꾸는 일이다.

### 다른 화면에 같은 함정이 있는가

| 화면 | 실패가 화면을 통째로 대체하나 | 조건을 걸 수 있나 | 판정 |
|---|---|---|---|
| 피드 홈 | 그렇다 | 검색어·필터·정렬·카테고리 | #144 에서 고침 ✅ |
| 내 게시판 | 아니다 — `BoardListErrorChrome` 가 **상단 바를 남긴다**(뒤로가기가 산다) | 조건 없음 | 함정 없음 ✅ |
| 공고 상세 | 아니다 — `FeedTopBar` 가 항상 그려진다 | 조건 없음(공고 id 하나) | 함정 없음 ✅ |
| 원문 보기 | 아니다 — `PostingRawChrome` 가 상단 바를 남긴다 | 조건 없음 | 함정 없음 ✅ |
| 게시판 등록 | 아니다 — 실패는 인라인 상자·스낵바이고 URL 입력칸이 그대로 산다 | URL(사용자가 고칠 수 있다) | 함정 없음 ✅ |
| 온보딩 Step 1~4 | 아니다 — 배너가 화면 위에 얹힌다 | 입력값(사용자가 고칠 수 있다) | 입력 조건은 살아 있다 ✅ / 세션 만료는 로그인으로 보낸다 ✅(#211) |
| 학교 선택 시트 | 아니다 | 검색어(입력칸이 그대로 산다) | 함정 없음 ✅ |

**결론** — 조건 때문에 실패할 수 있으면서 실패가 화면을 통째로 대체하는 자리는 피드 홈뿐이고 이미 고쳐져 있다. 다른 화면은 실패해도 원인이 된 입력칸·상단 바가 살아 있다. 남은 막다른 길은 조건이 아니라 **세션**이다(§3 결함 1).

---

## 5. 오프라인 규칙

### 무엇을 저장하나

[`FeedSnapshot`](../../feature/feed/domain/src/main/kotlin/com/careercompass/feature/feed/domain/model/FeedSnapshot.kt) — **마지막으로 성공한 기본 조건 첫 페이지의 사본 하나**뿐이다.

- **기본 조건일 때만 저장한다**(`FeedQuery.isDefault`). 조건이 걸린 결과를 저장하면 오프라인에서 「전체」로 보이는 목록이 사실은 부분집합이 되어, 마감 임박 공고가 빠진 줄도 모른 채 읽게 된다.
- **첫 페이지만**. 상한은 `FeedSnapshot.MAX_POSTINGS`(= `PostingQuery.DEFAULT_LIMIT`)이고 data 계층이 잘라 저장한다.
- **빈 목록은 저장하지 않는다** — 「스냅샷 없음」과 구분할 이유가 없다.
- 저장 자리는 DataStore 의 JSON 문자열 키 하나. 해석 실패(형식 변경·모르는 열거값·불변식 위반)와 읽기 `IOException` 은 모두 「스냅샷 없음」으로 읽는다.
- 저장소는 **SESSION 스코프**(`StoreScope.SESSION`)라 **세션 경계마다 `LocalStoreRegistry.clearScope` 가 스냅샷도 함께 비운다** — 로그아웃·세션 정리뿐 아니라 새 세션을 저장할 때도 비운다. 로그아웃을 거치지 않고 다음 계정이 들어오는 길(지문 화면의 「다른 방법으로 로그인」)이 있어서, 저장 시점에 비우지 않으면 그 계정이 앞 계정의 공고 목록을 오프라인으로 본다(#335).
- 비운 뒤에 도착한 저장은 세션 세대 가드가 버린다 — 조회가 진행 중일 때 세션이 끝나면 그 응답은 다음 계정의 저장소에 들어가지 않는다(#348).
- 저장 실패는 기록만 남기고 사용자에게 알리지 않는다 — 이번 조회는 이미 성공했고 사용자가 할 일이 없다.
- 피드 밖에는 스냅샷이 **없다**. 게시판 목록·공고 상세·원문은 저장하지 않는다.

### 언제 보여 주나

- 조회가 실패하고 사유가 `NetworkUnavailable` 또는 `Maintenance` 일 때만 스냅샷을 읽어 온다(`Generic` 은 읽지 않는다). 있으면 실패 화면에 「오프라인 모드로 보기」가 열린다.
- **스냅샷이 없으면 그 버튼을 아예 그리지 않는다** — 눌러도 보여 줄 것이 없는 버튼을 만들지 않는다.
- 점검 중에도 같은 길을 연다 — 서버가 살아나기를 기다리는 동안 마지막 목록은 여전히 유효하다.
- 오프라인 모드로 들어가면 목록 위에 배너가 뜬다: `feed_offline_notice` = 오프라인 · %1$s 기준 목록 (`M월 d일 HH:mm`, 주입된 시계의 지역 시간대).

### 오프라인에서 무엇이 되고 무엇이 막히나

| 조작 | 오프라인에서 | 근거 |
|---|---|---|
| 목록 읽기 | **된다** — 스냅샷 목록 | 저장해 둔 사본이 있다 |
| 지금 걸린 조건 적용 | **된다** — 스냅샷에도 같은 클라이언트 필터(`FeedQuery.filterClientSide`)를 한 번 더 건다 | 마감일 범위를 걸어 둔 채 넘어온 사람이 범위 밖 공고를 보지 않게 |
| 이어 읽기 | **막힌다** — `onLoadMore` 가 즉시 반환한다 | 스냅샷에는 다음 커서가 없다 |
| 북마크 | **막힌다** — 스낵바 `feed_offline_read_only` = 오프라인에서는 북마크를 바꿀 수 없어요 | 스냅샷은 읽기 전용이다 |
| 검색·필터·정렬 바꾸기 | **막지 않는다** — 그대로 재조회를 부른다 | 성공하면 온라인 목록으로 돌아온다(`online()` 이 오프라인 표시와 근거를 모두 버린다). 실패하면 다시 실패 화면 |
| 당겨서 새로고침 | **막지 않는다** | 실패하면 목록은 그대로 두고 스낵바 `feed_refresh_failed` = 공고를 새로고침하지 못했어요 |
| 공고 상세 열기 | **막지 않지만 실패한다** | 상세는 스냅샷이 없어 네트워크 실패 화면이 뜬다 |

### 오프라인 시작(콜드 스타트)

앱 시작은 네트워크를 기다리지 않는다. 로컬 세션과 마지막으로 알려진 온보딩 완료 여부로 시작 목적지를 먼저 정하고, 서버 확인은 메인에 들어간 뒤 백그라운드로 한 번 돌린다. 그 확인이 **네트워크 때문에** 실패하면 기록만 남기고 화면을 흔들지 않는다 — **오프라인 시작에 만료 안내를 띄우지 않는다.**

---

## 6. 새 화면을 더할 때 이 표에 무엇을 채워야 하는가

새 화면을 만들면서 엣지 상태 문구를 스스로 짓지 않으려면, 코드를 쓰기 전에 아래 여섯 칸을 채운다. 채울 수 없는 칸이 있으면 그건 「모른다」가 아니라 **판정이 아직 안 됐다**는 뜻이다.

1. **오프라인·네트워크 실패** — 화면 한 장을 쓸 자리인가(→ `CareerCompassNetworkErrorState`, 문구는 부품이 고정한다), 인라인 한 줄인가(→ 문구를 새로 짓기 전에 §2.1 의 기존 네 문구 중 쓸 것이 있는지 본다). **스냅샷이 있는가**를 먼저 답한다 — 없으면 `onOfflineClick = null` 이다.
2. **로딩·분석 중** — 화면 전체를 덮어도 되는가(첫 조회면 그렇다), 사용자가 방금 누른 버튼 곁이어야 하는가(제출·이어 읽기면 그렇다). 오래 걸리는 요청이면 **얼마나 걸리는지 한 줄**을 붙인다(게시판 감지의 `_hint` 가 선례다). 진행률을 서버가 주지 않으면 무한 인디케이터다.
3. **빈 결과** — 사유가 하나뿐인가, 여럿인가. 여럿이면 **우선순위와 그 근거**를 KDoc 에 남긴다(기준은 「그 조건을 되돌리면 결과가 달라지는가」). 사유마다 ① 문구 ② 되돌릴 조건이 있으면 그것을 되돌리는 버튼 ③ 없으면 버튼 없이 「언제쯤 달라지는가」를 적는다.
4. **권한 거부** — 이 화면이 권한을 필요로 하는가. 그렇다면 `CareerCompassPermissionDeniedState` 에 넘길 `benefits`(권한을 켜면 무엇이 좋아지는지, 최소 1개)를 정한다.
5. **서버 점검(503)** — `FeedFailureReason.Maintenance` 를 갈라 받는가. 갈라 받는다면 화면 한 장이면 `FeedMaintenanceState`(문구 공유), 인라인이면 §2.5 의 문구를 그대로 쓴다. **점검에 새 문구를 짓지 않는다** — 같은 사실을 화면마다 다르게 말하게 된다.
6. **세션 만료(401)** — 화면에 그리지 않는다. `sessionEnded` 를 올려 Screen 이 `onSessionEnded` 를 부르고 앱 셸이 로그인으로 보낸다. **이 배선을 빼먹으면 막다른 길이 된다**(§3 결함 1이 그 예다).

그리고 마지막으로 하나 더:

7. **되돌릴 길** — 실패 화면이 화면을 **통째로** 대체하는가. 그렇다면 원인이 된 조건을 되돌릴 조작이 그 화면에 남아 있는지 확인한다. 남지 않는다면 §4 의 규칙대로 실패 화면 안에 되돌릴 행동을 넣거나, 실패 화면이 목록 영역만 대체하고 헤더를 남기게 한다.

### 새 문구를 만들기 전에

- **이미 있는 문구를 먼저 찾는다.** 네트워크 실패·점검·재시도는 이미 정본이 있다.
- 리소스 이름은 모듈 프리픽스 규칙을 따른다([`docs/convention/resource-naming.md`](../convention/resource-naming.md)).
- 문구를 새로 지었으면 **이 문서의 표에 행을 더한다.** 표에 없는 문구는 다음 사람이 또 새로 짓는다.

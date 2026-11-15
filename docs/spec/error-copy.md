# 실패 문구 표 (API_SPEC v0.1 §9)

서버가 코드를 주면 앱은 **문구를 여기서 찾는다.** 화면이 스스로 짓지 않는다.

- 화면별로 **어떤 상태를 그리는가**는 [`edge-states.md`](edge-states.md) 다. 이 문서는 그 상태에 **무슨 문장을 넣는가**를 정한다.
- 계약: `core/ui/src/main/kotlin/com/careercompass/core/ui/failure/FailureDisplay.kt`
- 문자열: `core/ui/src/main/res/values/core_ui_failure_strings.xml`
- 회귀 가드: `core/ui/src/test/kotlin/com/careercompass/core/ui/failure/FailureDisplayTest.kt`

## 왜 표인가

§9 의 코드에는 개발자용 설명만 붙어 있다. 그것을 그대로 띄우면 사용자는 무슨 말인지 모르고, 화면마다 문구를 지으면 같은 사실을 서로 다르게 말하게 된다. 「게시판을 못 불러왔다」를 어떤 화면은 「불러오지 못했어요」로, 어떤 화면은 「오류가 발생했습니다」로 말하는 상태가 그것이다.

표는 코드마다 세 가지를 정한다.

1. **무슨 일이 일어났는가** — 제목과 본문(`@StringRes`)
2. **사용자가 할 수 있는 일이 있는가** — 있으면 버튼이 붙고, 없으면 안 붙는다
3. **있으면 무엇인가** — 재시도 · 프로필 채우기 · 정리하기 · 다시 로그인

## 표

`행동` 열의 「없음」은 **버튼을 그리지 않는다**는 뜻이다. 재시도 가능 여부는 `행동 == 재시도` 와 같다(`FailureDisplay.isRetryable`).

화면 한 장을 쓰는 자리는 `CareerCompassFailureState(display, onActionClick)` 가 행을 **그대로** 그린다(#222) — 버튼은 행동이 있고 화면이 콜백을 넘길 때만 붙는다. 「검색 결과 없음」 부품을 실패에 돌려 쓰지 않는다.

| 코드 | HTTP | 갈래(`FailureKind`) | 제목 | 본문 | 행동 | 재시도 |
|---|---|---|---|---|---|---|
| — (전송 실패) | — | `NoConnection` | 연결할 수 없어요 | 인터넷 연결을 확인하고 다시 시도해 주세요 | 재시도 | ✅ |
| — (전송 실패·타임아웃) | — | `Timeout` | 응답이 너무 늦어요 | 기다리는 동안 답이 오지 않았어요. 잠시 후 다시 시도해 주세요 | 재시도 | ✅ |
| `INVALID_INPUT` | 400 | `InvalidInput` | 입력한 내용을 확인해 주세요 | 형식에 맞지 않는 값이 있어요. 고쳐서 다시 보내 주세요 | 없음 | ❌ |
| `AUTH_REQUIRED` | 401 | `AuthExpired` | 다시 로그인해 주세요 | 로그인 정보가 만료됐어요. 다시 로그인하면 이어서 할 수 있어요 | 다시 로그인 | ❌ |
| `AUTH_INVALID` | 401 | `AuthExpired` | (위와 같음) | (위와 같음) | 다시 로그인 | ❌ |
| `PERMISSION_DENIED` | 403 | `PermissionDenied` | 접근할 수 없어요 | 이 내용을 볼 권한이 없어요 | 없음 | ❌ |
| `RESOURCE_NOT_FOUND` | 404 | `NotFound` | 찾을 수 없어요 | 이미 지워졌거나 주소가 바뀐 것 같아요 | 없음 | ❌ |
| `POSTING_NOT_FOUND` | 404 | `NotFound` | (문맥이 명사를 채운다 — 아래 참조) | | 없음 | ❌ |
| `DUPLICATE_BOARD` | 409 | `DuplicateBoard` | 이미 등록된 게시판이에요 | 내 게시판 목록에서 확인해 주세요 | 없음 | ❌ |
| `LIMIT_EXCEEDED` | 422 | `LimitExceeded` | 더 담을 수 없어요 | 쓰지 않는 항목을 지우고 다시 시도해 주세요 | 정리하러 가기 | ❌ |
| `PROFILE_INCOMPLETE` | 422 | `ProfileIncomplete` | 프로필이 아직 비어 있어요 | 기본 정보를 채우면 적합도를 계산할 수 있어요 | 프로필 입력하기 | ❌ |
| `PARSING_FAILED` | 422 | `ParsingFailed` | 공고를 읽지 못했어요 | 원문 형식을 알아보지 못했어요. 원문 보기로 확인해 주세요 | 없음 | ❌ |
| `BOARD_BLOCKED` | 422 | `BoardBlocked` | 자동 수집이 허용되지 않는 사이트예요 | 사이트가 수집을 막아 두었어요. 다른 게시판 주소로 등록해 주세요 | 없음 | ❌ |
| `RATE_LIMITED` | 429 | `RateLimited` | 요청이 너무 많아요 | 잠시 후 다시 시도해 주세요 | 재시도 | ✅ |
| `LLM_UNAVAILABLE` | 503 | `ServiceUnavailable` | 서비스가 잠시 점검 중이에요 | AI 분석 서버를 손보고 있어요. 조금 뒤에 다시 열어 주세요 | 재시도 | ✅ |
| `INTERNAL_ERROR` | 500·5xx | `Unexpected` | 문제가 생겼어요 | 잠시 후 다시 시도해 주세요 | 재시도 | ✅ |
| (사유 미확인) | — | `Unexpected` | (위와 같음) | (위와 같음) | 재시도 | ✅ |

코드 14종 · 갈래 14개다. **코드와 갈래는 1:1 이 아니다.**

- `AUTH_REQUIRED` · `AUTH_INVALID` → `AuthExpired` 하나. 사용자가 할 일이 「다시 로그인」으로 같다.
- `RESOURCE_NOT_FOUND` · `POSTING_NOT_FOUND` → `NotFound` 하나. 「없어졌다」는 같은 사실이고, 없어진 것이 무엇인지는 화면 문맥이 말한다.
- `INTERNAL_ERROR` 와 정체를 모르는 실패 → `Unexpected` 하나. 사용자에게는 같은 말이다. 관측용 분류는 이 접힘과 무관하게 원본을 다시 읽는다(`core:common` 의 `StagedFailureReporting`, #117).
- 코드 없는 전송 실패는 반대로 **둘로 갈린다** — 연결이 안 된 것(`NoConnection`)과 우리가 기다리다 먼저 끊은 것(`Timeout`). `CoreDataFailure.NetworkUnavailable.isTimeout` 의 판정을 그대로 쓴다(#134).

## 재시도를 주지 않는 실패

같은 요청을 그대로 다시 보내면 **같은 답이 온다** — 여기에 재시도 버튼을 주면 사용자는 버튼이 있으니 누르고 같은 실패를 다시 만난다.

`PROFILE_INCOMPLETE` · `PARSING_FAILED` · `LIMIT_EXCEEDED` · `DUPLICATE_BOARD` · `BOARD_BLOCKED` · `PERMISSION_DENIED` · `INVALID_INPUT` · 404.

이 중 셋에는 대신 **상황을 실제로 바꾸는 길**이 붙는다: 프로필 채우기 · 정리하기 · 다시 로그인.

반대로 시간이 지나면 답이 갈리는 실패에는 재시도가 붙는다 — `RATE_LIMITED` · `LLM_UNAVAILABLE` · `INTERNAL_ERROR` · 전송 실패 둘.

## 화면 문맥(`FailureSurface`)

같은 코드라도 무엇을 하다 만났느냐에 따라 읽어야 할 문장이 다르다. 화면이 문맥을 넘기면 표가 명사를 채우고, **넘기지 않으면 어느 화면에 붙어도 어긋나지 않는 기본 문구가 나간다** — 틀린 명사를 말하느니 명사를 말하지 않는다.

| 갈래 | `Unspecified` | `Posting` | `Board` | `ExperienceCard` | `Application` |
|---|---|---|---|---|---|
| `NotFound` | 찾을 수 없어요 | 공고를 찾을 수 없어요 | 게시판을 찾을 수 없어요 | (기본) | (기본) |
| `LimitExceeded` | 더 담을 수 없어요 | (기본) | 게시판을 더 등록할 수 없어요 / 최대 **20**개 | 경험 카드를 더 만들 수 없어요 / 최대 **30**개 | 지원서를 더 저장할 수 없어요 / 최대 **10**개 |
| `Unexpected` | 문제가 생겼어요 | 공고를 불러오지 못했어요 | 게시판을 불러오지 못했어요 | 경험 카드를 불러오지 못했어요 | 지원서를 불러오지 못했어요 |

개수의 정본은 `core:model` 의 `MAX_BOARDS`(20) · `MAX_EXPERIENCE_CARDS`(30) · `MAX_PAST_APPLICATIONS`(10) 이다. 도메인이 자기 상한을 들고 오는 자리(`FeedFailure.BoardLimitReached.limit`)는 `display(surface, itemLimit = …)` 로 그 값을 넘긴다 — 서버가 말한 쪽이 사용자에게는 참이다.

## 쓰는 법

```kotlin
// 문맥을 아는 화면
val display = throwable.toFailureDisplay(FailureSurface.Posting)

val actionText = display.actionLabel()        // 행동이 없으면 null → 버튼을 그리지 않는다

CareerCompassEmptyState(
    title = display.title(),
    description = display.description(),
    actionText = actionText,
    onActionClick = onRetryClick.takeIf { actionText != null },
)

// 한 줄만 허용되는 자리(스낵바)
snackbarHostState.showSnackbar(display.sentence(resources))
```

행동을 **어디로** 보낼지는 화면이 채운다. 목적지(프로필 화면 · 게시판 목록 · 로그인)는 모듈마다 다르고 core 가 알 수 있는 것이 아니다.

## 표가 대체하지 않는 것

표는 **문구의 정본**이지 판정의 정본이 아니다. 이미 사유별로 갈라 둔 판정은 그대로 남는다.

- `FeedFailureReason` 셋(#144) — 조건을 지우고 다시 볼 수 있는 실패인지를 가른다. `failureKind` 로 표에 이어 붙일 뿐, 사유는 지우지 않았다.
- 게시판 구조 감지의 타임아웃 화면(#134) — `CoreDataFailure.NetworkUnavailable.isTimeout` 을 직접 보고 전용 상태를 그린다.
- 게시판 등록의 화면 고유 안내 — 「구조를 분석하지 못했어요」 · 「등록을 끝내는 중이에요」는 §9 의 어느 코드도 아니라 표에 넣지 않았다. 표에 넣으면 다른 기능이 쓸 수 없는 행이 하나 늘 뿐이다.
- 사유를 확인하지 못한 실패의 스낵바(#360). 내 게시판의 수집 토글 · 재시도 · 삭제 · 수정과 게시판 등록 제출은 `FailureKind.Unexpected` 일 때만 화면 고유 문구를 쓴다. 표의 그 행은 게시판 문맥에서 「게시판을 불러오지 못했어요」라 조회 실패를 가리키는 문장이고, 사용자가 방금 시킨 삭제 · 수정에 그대로 붙이면 무엇이 실패했는지 잘못 읽는다. 사유가 좁혀진 실패는 표의 제 행이 이긴다.

다만 **재시도 규칙은 표 밖에서도 같다.** 게시판 구조 감지의 실패 상자(로그인 필요 · SPA · 차단)에 붙어 있던 재시도를 뗐다 — 몇 번을 다시 보내도 같은 답이 온다. 「목록 페이지 주소인지 확인해 주세요」만 남긴다(`BoardDetectionFailure.isRetryable`).

## 표에 행을 더할 때

1. `FailureKind` 에 갈래를 더한다 — `display` 의 `when` 이 exhaustive 라 **컴파일이 먼저 막는다.**
2. `core_ui_failure_strings.xml` 에 제목·본문을 더한다.
3. `FailureDisplayTest.specRows` 에 그 갈래에 닿는 서버 코드를 적는다. 적지 않으면 「아무도 닿지 않는 행」으로 테스트가 실패한다.
4. 이 문서의 표를 고친다.

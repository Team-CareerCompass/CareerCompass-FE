# MVI 규칙

**화면 ViewModel 은 `MviViewModel` 을 상속해 진입점 하나(`onIntent`)와 순수 전이 하나(`reduce`)만 갖는다.**

`feature/*/presentation` 과 `app` 의 ViewModel 에 적용하고, `konsist` 의 `MviContractKonsistTest` 가 강제한다 (#244 · #252).
베이스와 마커는 `core/ui` 의 `com.careercompass.core.ui.mvi` 에 있다. 애프터노트가 추진 중인 같은 전환(Afternote/Afternote-FE #1811)의 계약을 그대로 들여왔다.

## 왜인가

단일 `UiState` 노출과 stateless 화면은 이미 정착해 있다. ViewModel 11개가 전부 `StateFlow<XxxViewState>` 하나를 내고, 화면은 `XxxScreen(state, onEvent)` 로 그린다. 그런데 그 아래가 갈려 있다.

- 전이가 코루틴 안에 흩어져 있다. `_uiState.update { it.copy(isLoading = true, failure = null) }` 같은 갱신이 `onSocialLoginRequested`·`startServerLogin`·`handleTokenFailure` 마다 따로 있다. 어느 입력이 어느 상태를 만드는지 한 곳에서 읽을 수 없다.
- 소비 함수가 화면마다 public fun 이다. `onNavigationConsumed()`·`onFailureConsumed()` 를 부르지 않아도 컴파일은 통과한다. 신호가 남은 채 다음 실패를 덮는다.
- 진입점이 ViewModel 마다 여럿이다. `onSocialLoginRequested`·`onLoginHostDetached`·`onXxxConsumed` 처럼 화면이 부를 수 있는 함수가 ViewModel 마다 3개에서 20개까지 있다.

## 3타입 — Intent · UiState · ReducerEvent

```kotlin
sealed interface LoginIntent : MviIntent {
    data class RequestSocialLogin(val provider: SocialProvider, val requestToken: suspend () -> Result<String>) : LoginIntent
    data object DetachLoginHost : LoginIntent
    data object ConsumeNavigation : LoginIntent
    data object ConsumeFailure : LoginIntent
}

data class LoginUiState(
    val isLoading: Boolean = false,
    val failure: LoginFailureReason? = null,             // 일회성 신호도 상태다
    val pendingNavigation: LoginDestination? = null,
) : UiState

sealed interface LoginReducerEvent : ReducerEvent {
    data object LoginStarted : LoginReducerEvent
    data class LoggedIn(val destination: LoginDestination) : LoginReducerEvent
    data class LoginFailed(val reason: LoginFailureReason) : LoginReducerEvent
    data object HostDetached : LoginReducerEvent
    data object NavigationConsumed : LoginReducerEvent
    data object FailureConsumed : LoginReducerEvent
}
```

### Intent 와 ReducerEvent 를 가르는 기준

| | Intent | ReducerEvent |
| --- | --- | --- |
| 무엇인가 | 사용자가 **하려는 것** | 상태가 **겪은 것** |
| 누가 만드는가 | 화면 | ViewModel 만 |
| 예 | `RequestSocialLogin` · `SelectFilter` | `LoginStarted` · `Loaded` · `LoginFailed` |

Intent 하나가 ReducerEvent 를 0개에서 N개까지 낳는다. 네비게이션만 하는 Intent 는 0개, 로드 Intent 는 `Loading` → `Loaded` 로 2개다. 이 분리가 없으면 비동기 중간 상태를 표현할 곳이 없어 다시 `_uiState.update` 로 돌아간다.

### 부수효과는 `onIntent` 에, 전이는 `reduce` 에

```kotlin
override fun onIntent(intent: LoginIntent) {
    when (intent) {
        is LoginIntent.RequestSocialLogin -> requestSocialLogin(intent.provider, intent.requestToken)
        LoginIntent.DetachLoginHost -> detachLoginHost()
        LoginIntent.ConsumeNavigation -> dispatch(LoginReducerEvent.NavigationConsumed)
        LoginIntent.ConsumeFailure -> dispatch(LoginReducerEvent.FailureConsumed)
    }
}

private fun requestSocialLogin(provider: SocialProvider, requestToken: suspend () -> Result<String>) {
    if (currentState.isLoading) return                        // 가드는 currentState 를 읽는다
    tokenRequest = viewModelScope.launch {
        dispatch(LoginReducerEvent.LoginStarted)               // 중간 상태도 event 다
        ...
    }
}
```

`reduce` 는 저장소 호출·로깅·계측을 하지 않는다. `MutableStateFlow.update` 는 경합하면 람다를 다시 부르므로, 부수효과를 리듀서에 두면 그 부수효과가 두 번 일어난다.

`when` 에 `else` 를 두지 않는다. 갈래가 늘면 컴파일이 빠진 분기를 알려야 한다.

## 화면은 `Screen` / `Content` 2단이다

```kotlin
@Composable
fun LoginScreen(
    onNavigate: (LoginDestination) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LoginViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ObserveSignal(signal = state.pendingNavigation, consumed = LoginIntent.ConsumeNavigation, onIntent = viewModel::onIntent, onSignal = onNavigate)
    LoginContent(state = state, onIntent = viewModel::onIntent, modifier = modifier)
}

@Composable
internal fun LoginContent(state: LoginUiState, onIntent: (LoginIntent) -> Unit, modifier: Modifier = Modifier)
```

- `Screen` 은 stateful 이고 ViewModel 을 주입받는다.
- `Content` 는 stateless 이고, 프리뷰 · screenshotTest · Robolectric 의 진입점이다. 화면을 그리려고 ViewModel 을 조립하지 않는다. 기본은 `internal` 이고, 다른 모듈의 androidTest 가 합성하는 것만 public 이다. 지금 그 자격이 있는 것은 app 의 접근성 스모크가 그리는 `FeedContent` · `OnboardingStep1~4Content` · `LoginContent` · `ProfileHomeContent` · `ProfileEditContent` 다. `ExperienceListContent` · `PastApplicationListContent` · `ApplicationSetupContent` 도 public 이지만 자기 모듈 밖 소비처가 없다. 규약대로면 `internal` 이고, 좁히는 것은 profile · editor 담당 몫이다.

### 옛 이름과의 대응

이 저장소는 stateful 층을 `XxxEntry`, stateless 층을 `XxxScreen` 이라 불러 왔다. #255 에서 전부 옮겼고 옛 이름은 이 표로만 남는다. 로그인·생체인증은 #249 에서 처음부터 새 이름으로 썼다.

| 옛 이름 | 지금 | 비고 |
| --- | --- | --- |
| `XxxEntry` (stateful) | `XxxScreen` | ViewModel 주입, 신호 소비, 네비게이션 콜백 |
| `XxxScreen(state, onEvent)` (stateless) | `XxxContent(state, onEvent)` | 프리뷰 · screenshotTest · Robolectric 의 진입점. 골든 스크린샷 경로는 프리뷰 함수와 screenshotTest 파일 이름으로 정해지므로 둘은 옛 이름을 유지한다(`FeedScreenScreenshotTest`) |
| `XxxViewState` → `XxxUiState` 변환 | `Screen` 이 그대로 한다 | ViewModel 은 리소스를 모르므로 라벨 · D-day · 상대 시각 같은 표시 문구는 `Screen` 이 리소스로 만든다. 리소스가 필요 없는 파생 판정(`canResetFailedQuery` 같은 것)은 ViewModel 이 내는 `UiState` 의 계산 프로퍼티다 |
| `XxxEvent` (`Content` → `Screen`) | 그대로 두고 `Screen` 이 `XxxIntent.Screen(event)` 로 감싼다 | `Content` 의 계약은 화면 하나에 닫혀 있고 ViewModel 을 모른다. 네비게이션 갈래는 Intent 가 아니라 `Screen` 의 콜백으로 남는다 |

- ViewModel 을 화면이 만들지 않는 경우가 있다. 온보딩은 그래프 스코프로 여러 화면이 `OnboardingViewModel` 하나를 공유하므로, `Screen` 이 `hiltViewModel()` 을 부르지 않고 VM 을 파라미터로 받는다. 공유가 없는 화면은 `viewModel: XxxViewModel = hiltViewModel()` 로 둔다.
- 한 파일에 둘 다 둔다. 커지면 가른다. `Screen` + `Content` 를 한 파일에 두는 것이 기본이고, stateful 층이 커지면 파일을 가른다.
- 플랫폼에 매인 콜백은 `Content` 의 파라미터로 남는다. 소셜 SDK 토큰 요청은 Activity 의존이라 stateful 층이 람다를 만들어 Intent 에 실어 보낸다. ViewModel 은 플랫폼 독립을 유지한다.
- 파생값을 화면에 넘기지 않는다. `isActionEnabled` 같은 값은 `UiState` 의 계산 프로퍼티로 두고 `Content` 가 `state` 에서 읽는다.

## 일회성 신호 — `UiState` 흡수 + `Intent.ConsumeXxx`

`Channel`·`MutableSharedFlow` 를 쓰지 않는다. producer(ViewModel)가 consumer(UI)보다 오래 사는 순간 `Channel` 은 전달을 보장하지 못한다. 구성 변경·프로세스 사망·분할 화면이다([공식 가이드](https://developer.android.com/topic/architecture/ui-layer/events#handle-viewmodel-events)). 이 저장소의 ViewModel 은 처음부터 신호를 `UiState` 의 nullable 필드에 담아 왔고, 바뀌는 것은 소비 경로뿐이다.

```kotlin
ObserveSignal(
    signal = state.failure,
    consumed = LoginIntent.ConsumeFailure,
    onIntent = onIntent,
) { reason -> showSnackbar(reason) }
```

신호가 값 없이 「올라갔다/내려갔다」 로만 표현되면 `ObserveFlag` 를 쓴다. `isLoggedIn` · `shouldNavigateToXxx` 처럼 나를 값이 없는 신호다. 안에서 `ObserveSignal` 로 접히므로 소비 규약은 하나다.

- `onXxxConsumed()` 를 public fun 으로 노출하지 않는다. 진입점이 화면 수만큼 늘고, 배선을 빠뜨려도 컴파일이 통과해 신호가 남은 채 다음 실패를 덮는다. `Intent.ConsumeXxx` 로 접으면 소비도 `onIntent` 라는 같은 문을 지난다.
- 소비가 신호를 null 로 되돌리므로 같은 값이 연속으로 와도 두 번 소비된다 (`A → null → A`). reset 없이 같은 값을 다시 쓰면 두 번째는 조용히 묻힌다.
- `onSignal` 안에서 suspend 를 직접 기다리지 않는다. 소비 직후의 상태 변화가 `LaunchedEffect` 를 재시작시켜 이전 코루틴을 취소한다. 스낵바처럼 시간이 걸리는 표출은 `rememberCoroutineScope()` 에 launch 한다.

`Effect` 타입 파라미터는 베이스에 없다. MVI 가 요구하는 것은 「일회성 효과를 상태 전이에서 분리한다」 까지고, 전달 수단은 아키텍처 계약 밖이다. 참조 구현(RuleUp)의 `Channel` 은 그쪽의 선택이지 계약이 아니다.

## `composable-callback-defaults.md` 와의 관계

두 규칙은 충돌하지 않는다. MVI 화면은 콜백 파라미터 자체가 사라져 no-op 디폴트를 둘 자리가 없어진다. `Content` 가 받는 것은 `state` 와 `onIntent` 둘뿐이다.

- 전환한 화면에서 상호작용을 늘릴 때 콜백 파라미터를 되살리지 않는다. `Intent` 갈래를 하나 더한다. 갈래를 빠뜨리면 `when` 이 컴파일 에러를 낸다.
- 네비게이션 콜백은 전환 범위 밖이다. `Screen` 이 받는 네비게이션 콜백은 기존 방식·기존 개수를 유지하고, `= {}` 디폴트를 두지 않는 규칙을 그대로 따른다. 목적지 결정은 `AppNavigation` 이 갖는다.
- `core:ui` 리프 컴포넌트는 MVI 대상이 아니다. 거기서는 nullable 핸들러·오버로드로 선택성을 모델링하는 기존 처분 기준이 그대로다.

## 테스트는 두 층으로 가른다

- 리듀서 테스트는 코루틴 하네스 없이 순수 함수로 돈다. `reduce(state, event)` 의 입력과 출력만 단언한다. 전환 전 ViewModel 테스트 대부분이 `runTest` 위에서 상태를 관찰하던 비용이 여기서 빠진다.
- ViewModel 테스트는 비동기·부수효과만 남긴다. UseCase 호출, 취소, 계측 기록.

## 강제

`MviContractKonsistTest` 가 셋을 본다.

| 규칙 | 내용 |
| --- | --- |
| A | `MviViewModel` 상속체는 `MutableStateFlow`·`MutableSharedFlow`·`Channel` 을 직접 선언하지 않는다 |
| B | `feature/*/presentation` 과 `app` 의 ViewModel 은 `MviViewModel` 을 상속한다 |
| C | `MviIntent`·`ReducerEvent` 를 직접 구현하는 타입은 `sealed interface` 다 |

규칙 B 는 도입 시점(#244)에 전환 전 ViewModel 9개를 `PENDING_MVI_MIGRATION` 예외로 뒀고, onboarding(#249·#245)·feed(#246) 전환이 끝나면서 예외를 지웠다. `app` 의 ViewModel 2개(`MainViewModel`·`MyTabPlaceholderViewModel`)도 #252 에서 옮기고 규칙 B 의 범위에 넣었다. 이제 `feature/*/presentation` 과 `app` 의 새 ViewModel 은 처음부터 `MviViewModel` 을 상속해야 한다.

세 규칙은 프로덕션 소스만 본다. 테스트 더블이 `MviViewModel` 을 상속하며 보조 상태 홀더를 드는 것은 규칙 A 의 대상이 아니다.

규칙 A·B 의 상속 판정은 중간 추상 베이스를 낀 사슬까지 따라간다. Konsist 0.17.3 의 `parents(indirectParents = true)` 는 이 스코프 구성으로 직계와 같은 목록을 돌려주므로, 스캔한 파일에서 이름 색인을 만들어 직접 걷는다.

### 가드가 「안」 보는 것

- `onIntent` 밖의 public 진입점. 상속체가 `fun refreshOnReturn()` 같은 public 함수를 노출해도 세 규칙 어디도 막지 않는다. 화면이 ViewModel 을 직접 부르는 통로가 `onIntent` 하나여야 한다는 것은 규약이지 CI 계약이 아니다. 리뷰에서 본다.
- `reduce` 밖의 전이. 규칙 A 는 「상태 홀더 선언」 을 막을 뿐, `dispatch` 를 거치지 않는 다른 경로를 전수로 잡지는 못한다.

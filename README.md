# LaunchingView Android

앱 시작 시점에 [LaunchingService](https://github.com/swift-man/LaunchingService-Android)가 판정한 실행 정책에 따라 본 화면, 런치 화면, 업데이트 안내, 공지, 차단 화면을 보여 주는 Jetpack Compose 라이브러리입니다.

이 저장소는 [LaunchingView for Apple platforms](https://github.com/swift-man/LaunchingView)의 Android 구현입니다. Kotlin Multiplatform 을 사용하지 않으며 Android 소비자는 Maven AAR 만 받습니다. 상태 전이는 iOS 의 TCA reducer 를 순수 Kotlin 상태 머신으로 옮긴 것이고, Compose 는 그 상태를 그리기만 합니다.

## Requirements

- Android API 23+
- JDK 17
- Jetpack Compose (Material 3)
- Firebase 가 구성된 Android 애플리케이션 (`LaunchingService` 를 쓸 때)

## API Documentation

`main` 브랜치에 병합되어 배포된 API 문서는 [docs.gorani.me/LaunchingView-Android](https://docs.gorani.me/LaunchingView-Android/)에서 확인할 수 있습니다.

## Installation

```kotlin
dependencies {
  implementation("me.gorani:launching-view:0.1.0")
}
```

`me.gorani:launching-service` 는 `api` 의존성으로 함께 내려옵니다. Firebase 의존성은 Firebase Android BoM 으로 정렬하는 것을 권장합니다.

```kotlin
dependencies {
  implementation(platform("com.google.firebase:firebase-bom:34.16.0"))
  implementation("com.google.firebase:firebase-config")
}
```

## Usage

### LaunchingView

런치 화면을 보여 주다가 실행 정책 확인이 끝나면 본 화면으로 전환합니다. `service` 에는 Activity 수명 동안 유지되는 인스턴스를 넘기세요.

```kotlin
class MainActivity : ComponentActivity() {
  private val launchingService by lazy { LaunchingService(applicationContext) }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContent {
      MaterialTheme {
        LaunchingView(
          content = { ContentScreen() },
          launchScreen = { LaunchScreen() },
          service = launchingService,
        )
      }
    }
  }
}
```

### 앱 초기화 작업과 함께 사용

로그인 복원 같은 앱 자체 초기화가 끝날 때까지 본 화면 표시를 미루려면 `isFinished` 를 넘깁니다. 정책이 `Valid` 여도 `isFinished` 가 `false` 인 동안은 런치 화면이 유지됩니다.

```kotlin
var isFinished by rememberSaveable { mutableStateOf(false) }

LaunchingView(
  content = { ContentScreen() },
  launchScreen = {
    LaunchScreen(onReady = { isFinished = true })
  },
  isFinished = isFinished,
  service = launchingService,
)
```

### AsyncLaunchingView

별도의 런치 화면 없이 본문을 바로 보여 주고, 정책이 차단일 때만 차단 화면으로 바꿉니다.

```kotlin
AsyncLaunchingView(
  content = { ContentScreen() },
  service = launchingService,
)
```

### 기본 문구 교체

Remote Config 의 제목·메시지가 비어 있을 때 쓸 문구와 버튼 이름을 바꿀 수 있습니다. 런타임에 바꿔도 이미 조회한 정책은 유지되고 다음 조회부터 새 문구가 쓰입니다. 제목을 비워 두면 앱 이름(`applicationInfo.loadLabel`)이 쓰입니다.

```kotlin
LaunchingView(
  content = { ContentScreen() },
  launchScreen = { LaunchScreen() },
  service = launchingService,
  defaultText = LaunchingAlertDefaultText(
    forceUpdate = LaunchingAlertDefaultText.ForceUpdate(done = "업데이트"),
    optionalUpdate = LaunchingAlertDefaultText.OptionalUpdate(cancel = "나중에", done = "업데이트"),
    notice = LaunchingAlertDefaultText.Notice(done = "확인"),
    fetchError = LaunchingAlertDefaultText.FetchError(done = "다시 시도"),
  ),
)
```

### 링크 열기 교체

업데이트·공지 링크는 기본적으로 `ACTION_VIEW` 인텐트로 열립니다. 인앱 브라우저 등으로 바꾸려면 `onOpenUri` 를 넘깁니다.

```kotlin
LaunchingView(
  content = { ContentScreen() },
  launchScreen = { LaunchScreen() },
  service = launchingService,
  onOpenUri = { uri -> customTabsLauncher.launch(uri) },
)
```

## 동작 방식

1. `LaunchingView` 또는 `AsyncLaunchingView` 가 화면에 붙어 `ON_RESUME` 을 받으면 `LaunchingServiceClient.fetchAppUpdateStatus()` 를 호출합니다. 조회 중에 다시 요청되면 한 번만 더 조회합니다.
2. `Valid` 이면 본 화면을 보여 줍니다. `isFinished` 가 `false` 이면 런치 화면을 유지합니다.
3. `OptionalUpdateRequired` 이면 본 화면 위에 "나중에 / 업데이트" 다이얼로그를 띄웁니다. 업데이트를 누르면 `doneUri` 를 엽니다.
4. `ForcedUpdateRequired` 이면 본 화면 대신 차단 화면을 보여 줍니다. 버튼 하나가 `doneUri` 를 열고, 뒤로가기로는 닫히지 않습니다.
5. `Notice` 이면 `terminateOnDismiss` 가 `true` 일 때 차단 화면을, 아니면 본 화면 위에 공지 다이얼로그를 띄웁니다. 공지 다이얼로그를 닫을 때 `doneUri` 가 있으면 엽니다.
6. 조회가 예외로 끝나면 에러 다이얼로그를 띄우고, 닫으면 다시 조회합니다.
7. 앱이 백그라운드에서 돌아와 다시 `ON_RESUME` 을 받으면 최신 정책을 다시 확인합니다. 회전처럼 같은 프로세스에서 Activity 만 재생성된 경우에는 마지막 정책을 복원하고 다시 조회하지 않습니다.

## iOS 와의 대응

| iOS (`Launching.swift`) | Android | 비고 |
| --- | --- | --- |
| `launchScreen.onAppear` → `fetchAppUpdateStatus` | `Lifecycle.Event.ON_RESUME` → `FetchRequested` | 옵저버 등록 시점에 이미 RESUMED 이면 즉시 한 번 전달되므로 첫 표시 조회를 겸함 |
| `scenePhase == .active` 재조회 | 이후의 `ON_RESUME` | 같은 프로세스에서 Activity 가 재생성된 직후(회전·언어·다크 모드)의 합성 `ON_RESUME` 은 건너뜀. iOS 는 뷰를 재생성하지 않으므로 이때 재조회하지 않는 것과 같음 |
| `isFetching` 중 재요청 → `hasPendingFetch` | 동일 | 현재 조회가 끝난 뒤 한 번 더 조회 |
| effect 취소 → `fetchAppUpdateStatusCancelled` | 코루틴 취소 → `FetchCancelled` | 알림 없이 정리. 서비스가 던진 `CancellationException` 은 코루틴이 살아 있으면 조회 실패로 처리 |
| `.valid` | `Valid` | 본문 표시, 모든 알림 제거 |
| `.forcedUpdateRequired` | `ForcedUpdateRequired` | `BlockingLaunchView`, 버튼 문구는 `forceUpdate.done` |
| `.optionalUpdateRequired` | `OptionalUpdateRequired` | 본문 + 취소/업데이트 `AlertDialog`, 업데이트는 상태가 아직 optional 일 때만 링크 열기 |
| `.notice(isAppTerminated: true)` | `Notice(terminateOnDismiss = true)` | `BlockingLaunchView`, 버튼 문구는 `notice.done` |
| `.notice(isAppTerminated: false)` | `Notice(terminateOnDismiss = false)` | 본문 + 공지 다이얼로그, 닫을 때 `doneUri` 열기 |
| `showFetchErrorAlert` | `FetchFailed` | 대기 중 재요청이 있으면 알림 없이 재조회, 없으면 다른 다이얼로그를 지우고 에러 다이얼로그 |
| `appUpdateFetchErrorAlert(.dismiss)` → 재조회 | `FetchErrorDismissed` → `FetchRequested` | 동일 |
| `blockingAlertButtonTapped` | `BlockingButtonTapped` | 링크가 있을 때만 열기 |
| `Bundle.main.displayName` 기본 제목 | `applicationInfo.loadLabel` | `LaunchingAlertDefaultText` 의 제목을 비워 두면 적용 |
| 에러 alert 의 시스템 "OK" 버튼 | `LaunchingAlertDefaultText.FetchError.done` | Android 에는 시스템 기본 버튼이 없어 문구를 노출 |
| `@Dependency(\.openURL)` | `onOpenUri: (Uri) -> Unit` | 기본값은 `ACTION_VIEW` 인텐트 |
| `isFinished: Binding<Bool>` | `isFinished: Boolean` | Compose 상태를 그대로 넘김 |
| SwiftUI `@State` (구성 변경 없음) | `rememberSaveable` 로 마지막 `AppUpdateStatus` 복원 | 차단 화면과 본문 표시만 복원하고, 사용자가 닫았을 수 있는 선택 업데이트·공지 다이얼로그는 다시 띄우지 않음. 프로세스 종료 후 복원(저장된 프로세스 토큰이 다름)은 콜드 런치처럼 재조회하며, 저장은 `onSaveInstanceState` 시점이므로 그 뒤에 도착한 정책은 그때 교정됨 |
| `LaunchingAlertDefaultText.Notice.cancel` | 없음 | 공지 다이얼로그와 차단 화면 모두 버튼이 하나뿐이라 노출하지 않음 |

Remote Config 키와 판정 규칙은 [LaunchingService Android README](https://github.com/swift-man/LaunchingService-Android#remote-config) 를 참고하세요.

## Architecture

```text
LaunchingServiceClient.fetchAppUpdateStatus()
        |
        v
LaunchingStore  --(LaunchingAction)-->  LaunchingReducer  --(LaunchingState, effects)-->  LaunchingStore
        |                                                                                   |
        v                                                                                   v
collectAsStateWithLifecycle()                                                      Fetch / OpenUri 실행
        |
        v
LaunchingView / AsyncLaunchingView
  |- BlockingLaunchView
  |- OptionalUpdateDialog / NoticeDialog / FetchErrorDialog
  `- content / launchScreen
```

`LaunchingReducer` 는 Android 프레임워크에 의존하지 않는 순수 함수이며 `launching-view/src/test` 의 JVM 단위 테스트가 iOS reducer 의 각 분기를 고정합니다.

## Sample

`sample` 모듈은 Firebase 없이 빌드되며, 상단 메뉴에서 정상·선택 업데이트·강제 업데이트·공지·종료형 공지·조회 실패 시나리오와 `isFinished` 를 바꿔 가며 화면을 확인할 수 있습니다.

```bash
./gradlew :sample:installDebug
```

## Release

GitHub Releases trigger Maven Central publication. The repository requires these secrets:

- `MAVEN_CENTRAL_USERNAME`
- `MAVEN_CENTRAL_PASSWORD`
- `MAVEN_SIGNING_KEY`
- `MAVEN_SIGNING_PASSWORD`

The release tag must equal `v$(cat VERSION.txt)`. The workflow checks that all four secrets are present before checking out or building anything, so a missing secret fails at the first step with a clear error instead of after an upload.

### Manual Re-run

The workflow also accepts `workflow_dispatch`, so a failed publication can be retried without creating a new release. A manual run publishes only when the resolved tag equals `v$(cat VERSION.txt)`; the tag comes from the optional `tag` input, or, when that is empty, from the ref the run was started on. Starting the run on a branch therefore fails the version check and publishes nothing. After registering the secrets, republish a tag from `main` with:

```bash
gh workflow run release.yml -f tag=v0.1.0
```

## License

MIT License. See [LICENSE](LICENSE).

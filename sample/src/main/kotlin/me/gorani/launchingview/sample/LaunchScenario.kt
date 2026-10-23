package me.gorani.launchingview.sample

import java.net.URI
import me.gorani.launchingservice.AppUpdateStatus
import me.gorani.launchingservice.NoticeAlert
import me.gorani.launchingservice.UpdateAlert

/** Launch policies the sample can switch between without Firebase. */
enum class LaunchScenario(val label: String) {
  VALID("정상"),
  OPTIONAL_UPDATE("선택 업데이트"),
  FORCED_UPDATE("강제 업데이트"),
  NOTICE("공지"),
  TERMINATING_NOTICE("종료형 공지"),
  FETCH_ERROR("조회 실패"),
  ;

  /** The status a real `LaunchingService` would return for this scenario. */
  fun toStatus(): AppUpdateStatus = when (this) {
    VALID -> AppUpdateStatus.Valid
    OPTIONAL_UPDATE -> AppUpdateStatus.OptionalUpdateRequired(
      UpdateAlert("업데이트 안내", "새 버전을 사용할 수 있습니다.", STORE_URI),
    )
    FORCED_UPDATE -> AppUpdateStatus.ForcedUpdateRequired(
      UpdateAlert("업데이트가 필요합니다", "계속 사용하려면 최신 버전으로 업데이트해 주세요.", STORE_URI),
    )
    NOTICE -> AppUpdateStatus.Notice(
      NoticeAlert("공지", "오늘 밤 점검이 예정되어 있습니다.", terminateOnDismiss = false, doneUri = NOTICE_URI),
    )
    TERMINATING_NOTICE -> AppUpdateStatus.Notice(
      NoticeAlert("서비스 점검 중", "점검이 끝난 뒤 다시 이용해 주세요.", terminateOnDismiss = true, doneUri = NOTICE_URI),
    )
    FETCH_ERROR -> throw IllegalStateException("Remote Config 를 불러오지 못했습니다.")
  }

  private companion object {
    val STORE_URI: URI = URI("https://play.google.com/store/apps/details?id=me.gorani.launchingview.sample")
    val NOTICE_URI: URI = URI("https://github.com/swift-man/LaunchingView-Android")
  }
}

package com.bisue.tthelper.core

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent

/**
 * 무루트 제스처/터치 주입 및 외부 앱 이탈 감지 서비스
 */
class TtAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.i(TAG, "TtAccessibilityService 연결 완료")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        // 포그라운드 활성 창 변경 이벤트 감지
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val pkg = event.packageName?.toString() ?: return

            // 1. TT2 게임이면 즉시 게임 포그라운드로 인정
            if (pkg == HelperConfig.TT2_PACKAGE_NAME) {
                if (!isGameInForeground) {
                    isGameInForeground = true
                    Log.i(TAG, "게임 포그라운드 진입 확인: $pkg")
                }
                return
            }

            // 2. 시스템 UI, 삼성 게임 부스터/도구, 키보드 등 보조/일시적 창은 무시 (이전 상태 유지)
            if (isTransientOrSystemPackage(pkg)) {
                return
            }

            // 3. 사용자가 완전히 다른 일반 앱(홈런처, 유튜브, 웹브라우저 등)으로 전환한 경우
            if (isGameInForeground) {
                isGameInForeground = false
                Log.d(TAG, "게임 포그라운드 이탈 감지 (현재 앱: $pkg)")
            }
        }
    }

    private fun isTransientOrSystemPackage(pkg: String): Boolean {
        return pkg == packageName ||
                pkg == "android" ||
                pkg == "com.android.systemui" ||
                pkg.startsWith("com.samsung.android.game") || // Game Booster, Game Tools, GOS
                pkg.startsWith("com.samsung.android.honeyboard") || // 삼성 키보드
                pkg.contains("inputmethod") ||
                pkg.contains("ime")
    }

    override fun onInterrupt() {
        Log.w(TAG, "TtAccessibilityService 인터럽트 발생")
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instance == this) {
            instance = null
        }
        Log.i(TAG, "TtAccessibilityService 종료")
    }

    /**
     * 돌발 팝업이나 광고 닫기를 위한 안드로이드 전역 뒤로가기 액션 주입
     */
    fun pressBackButton(): Boolean {
        return performGlobalAction(GLOBAL_ACTION_BACK)
    }

    companion object {
        private const val TAG = "TtAccessibilityService"

        @Volatile
        var instance: TtAccessibilityService? = null
            private set

        @Volatile
        var isGameInForeground: Boolean = true
            internal set
    }
}

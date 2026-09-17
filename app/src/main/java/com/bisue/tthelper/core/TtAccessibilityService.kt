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
            
            // 시스템 UI나 헬퍼 오버레이 자체는 무시
            if (pkg == packageName || pkg == "com.android.systemui") {
                return
            }

            val wasInGame = isGameInForeground
            isGameInForeground = (pkg == HelperConfig.TT2_PACKAGE_NAME)

            if (wasInGame != isGameInForeground) {
                Log.d(TAG, "게임 포그라운드 상태 변경: $isGameInForeground (현재 패키지: $pkg)")
            }
        }
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
            private set
    }
}

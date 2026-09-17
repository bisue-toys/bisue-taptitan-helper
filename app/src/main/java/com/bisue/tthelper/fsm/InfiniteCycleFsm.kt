package com.bisue.tthelper.fsm

import android.util.Log
import com.bisue.tthelper.core.HelperConfig
import com.bisue.tthelper.core.TtAccessibilityService
import com.bisue.tthelper.touch.CoordinateProfile
import com.bisue.tthelper.touch.HumanTouchEngine
import com.bisue.tthelper.vision.ImagePreprocessor
import com.bisue.tthelper.vision.OcrEngine
import com.bisue.tthelper.vision.ScreenCapturer
import com.bisue.tthelper.vision.StageParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Tap Titans 2 완전 자율 무한 루프 상태 머신 (FSM)
 * 환생 ➔ 탭 레벨업 ➔ 6대 스킬 1레벨 해금 ➔ 전체 스킬 활성화 ➔ 등반/감시 ➔ 환생 무한 반복
 */
class InfiniteCycleFsm(
    private val config: HelperConfig,
    private val touchEngine: HumanTouchEngine,
    private val screenCapturer: ScreenCapturer,
    private val ocrEngine: OcrEngine,
    private val onStateChanged: (FsmState) -> Unit,
    private val onStageDetected: (Int) -> Unit
) {
    private val scope = CoroutineScope(Dispatchers.Default)
    private var loopJob: Job? = null
    private val stageParser = StageParser()

    @Volatile
    var currentState: FsmState = FsmState.IDLE
        private set(value) {
            field = value
            onStateChanged(value)
        }

    private var lastRecastTimestamp: Long = 0L

    fun start() {
        if (loopJob?.isActive == true) return
        config.isAutomationRunning = true
        currentState = FsmState.MONITORING_STAGE
        stageParser.resetHysteresis()

        loopJob = scope.launch {
            Log.i(TAG, "무한 자동화 FSM 루프 시작 (목표 층수: ${config.targetStage})")
            while (isActive && config.isAutomationRunning) {
                try {
                    step()
                } catch (e: CancellationException) {
                    break
                } catch (e: Exception) {
                    Log.e(TAG, "FSM 실행 중 예외 발생: ${e.message}", e)
                    handleErrorAndRecover()
                }
            }
            currentState = FsmState.IDLE
            Log.i(TAG, "무한 자동화 FSM 루프 종료")
        }
    }

    fun stop() {
        config.isAutomationRunning = false
        loopJob?.cancel()
        loopJob = null
        currentState = FsmState.IDLE
    }

    /**
     * 사용자가 플로팅 창에서 '즉시 환생 & 세팅 루프'를 수동 테스트할 수 있는 기능
     */
    fun triggerManualLoop() {
        scope.launch {
            Log.i(TAG, "수동 환생 & 스킬 세팅 루프 즉시 테스트 트리거")
            executeFullPrestigeAndSetupCycle()
        }
    }

    private suspend fun step() {
        // 1. 보안 가드: 게임이 화면에 떠 있지 않으면 일시정지하고 대기
        if (!TtAccessibilityService.isGameInForeground) {
            delay(1500)
            return
        }

        when (currentState) {
            FsmState.IDLE -> {
                delay(1000)
            }

            FsmState.MONITORING_STAGE -> {
                // 상단 층수 캡처 및 OCR 판별
                checkStageAndEvaluate()

                // 등반 중 스킬 주기적 재시전 (옵션 활성화 시)
                checkAutoRecastSkills()

                // 2.5초 간격으로 모니터링
                delay(2500)
            }

            else -> {
                // 환생 또는 스킬 세팅 진행 중일 때
                delay(500)
            }
        }
    }

    /**
     * 층수 캡처 및 목표 도달 검사
     */
    private suspend fun checkStageAndEvaluate() {
        val roiBitmap = screenCapturer.captureStageRoi() ?: return
        val enhancedBitmap = ImagePreprocessor.enhanceForOcr(roiBitmap)
        val text = ocrEngine.recognizeText(enhancedBitmap)
        enhancedBitmap.recycle()

        val result = stageParser.evaluate(text, config.targetStage, requiredConsecutiveHits = 2)
        result.parsedStage?.let { stage ->
            config.currentDetectedStage = stage
            onStageDetected(stage)
        }

        // 목표 층수 2회 연속 도달 시 환생 사이클 돌입
        if (result.isTargetReached) {
            Log.i(TAG, "목표 층수(${config.targetStage}) 달성 확인! 환생 시퀀스 개시")
            executeFullPrestigeAndSetupCycle()
        }
    }

    /**
     * 환생 -> 탭 레벨업 -> 스킬 해금(1렙) -> 스킬 활성화 전체 사이클 수행
     */
    private suspend fun executeFullPrestigeAndSetupCycle() {
        // ==========================================
        // 1단계: 환생 (Prestige)
        // ==========================================
        currentState = FsmState.PRESTIGE_OPEN_TAB
        touchEngine.tapRelative(CoordinateProfile.TAB_SWORDMASTER, postDelayMs = 600)

        currentState = FsmState.PRESTIGE_SCROLL_DOWN
        // 최하단 환생 버튼 위치로 2회 시원하게 스크롤
        touchEngine.swipeRelative(CoordinateProfile.SCROLL_PRESTIGE_START, CoordinateProfile.SCROLL_PRESTIGE_END, postDelayMs = 500)
        touchEngine.swipeRelative(CoordinateProfile.SCROLL_PRESTIGE_START, CoordinateProfile.SCROLL_PRESTIGE_END, postDelayMs = 600)

        currentState = FsmState.PRESTIGE_CLICK_BUTTON
        touchEngine.tapRelative(CoordinateProfile.BTN_PRESTIGE, postDelayMs = 800)

        currentState = FsmState.PRESTIGE_CONFIRM_MODAL
        touchEngine.tapRelative(CoordinateProfile.BTN_CONFIRM_PRESTIGE, postDelayMs = 1000)

        currentState = FsmState.PRESTIGE_WAIT_RESET
        Log.i(TAG, "환생 리셋 대기 시작 (12초)")
        delay(12000) // 게임 화이트아웃 / 리셋 애니메이션 대기
        stageParser.resetHysteresis()

        // ==========================================
        // 2단계: 탭(소드마스터) 레벨업
        // ==========================================
        currentState = FsmState.SWORDMASTER_PREPARE_MAX
        touchEngine.tapRelative(CoordinateProfile.TAB_SWORDMASTER, postDelayMs = 600)
        // 레벨업 단위를 MAX 모드로 세팅 (필요 시 토글)
        touchEngine.tapRelative(CoordinateProfile.BTN_LEVELUP_MULTIPLIER, postDelayMs = 400)

        currentState = FsmState.SWORDMASTER_UPGRADE_TAP
        // 소드마스터 업그레이드 연타 (Lv 600 이상 확보)
        touchEngine.multiTapRelative(CoordinateProfile.BTN_SWORDMASTER_UPGRADE, times = 5, intervalMs = 150)
        delay(400)

        // ==========================================
        // 3단계: 스킬 1레벨씩만 해금 (마나 절약)
        // ==========================================
        currentState = FsmState.SKILLS_SWITCH_X1
        // 레벨업 단위를 'x1'로 복구 (연속 클릭하여 x1 맞춤)
        touchEngine.tapRelative(CoordinateProfile.BTN_LEVELUP_MULTIPLIER, postDelayMs = 350)
        touchEngine.tapRelative(CoordinateProfile.BTN_LEVELUP_MULTIPLIER, postDelayMs = 350)
        touchEngine.tapRelative(CoordinateProfile.BTN_LEVELUP_MULTIPLIER, postDelayMs = 400)

        currentState = FsmState.SKILLS_UNLOCK_TOP_4
        touchEngine.tapRelative(CoordinateProfile.BTN_SKILL_1_HEAVENLY, postDelayMs = 300)
        touchEngine.tapRelative(CoordinateProfile.BTN_SKILL_2_DEADLY, postDelayMs = 300)
        touchEngine.tapRelative(CoordinateProfile.BTN_SKILL_3_MIDAS, postDelayMs = 300)
        touchEngine.tapRelative(CoordinateProfile.BTN_SKILL_4_FIRESWORD, postDelayMs = 400)

        currentState = FsmState.SKILLS_SCROLL_DOWN
        touchEngine.swipeRelative(CoordinateProfile.SCROLL_SKILL_START, CoordinateProfile.SCROLL_SKILL_END, postDelayMs = 500)

        currentState = FsmState.SKILLS_UNLOCK_BOTTOM_2
        touchEngine.tapRelative(CoordinateProfile.BTN_SKILL_5_WARCRY, postDelayMs = 300)
        touchEngine.tapRelative(CoordinateProfile.BTN_SKILL_6_CLONE, postDelayMs = 500)

        // ==========================================
        // 4단계: 6대 액티브 스킬 전체 활성화
        // ==========================================
        currentState = FsmState.ACTIVATE_CLOSE_TAB
        touchEngine.tapRelative(CoordinateProfile.CLOSE_TAB_TAP, postDelayMs = 500)

        currentState = FsmState.ACTIVATE_ALL_SKILLS
        activateAllActiveSkills()

        lastRecastTimestamp = System.currentTimeMillis()
        Log.i(TAG, "환생 ➔ 탭 레벨업 ➔ 스킬 1렙 해금 ➔ 스킬 활성화 사이클 1회 완주!")

        // 5단계: 다시 층수 감시 모드로 복귀하여 무한 반복
        currentState = FsmState.MONITORING_STAGE
    }

    /**
     * 전투 화면 하단 6개 스킬 슬롯 순차 터치
     */
    private suspend fun activateAllActiveSkills() {
        for (slot in CoordinateProfile.ACTIVE_SKILL_SLOTS) {
            touchEngine.tapRelative(slot, jitterPx = 10f, postDelayMs = 250)
        }
    }

    /**
     * 등반 중 스킬 쿨타임 주기마다 자동 재사용
     */
    private suspend fun checkAutoRecastSkills() {
        if (!config.autoRecastSkills) return

        val now = System.currentTimeMillis()
        val intervalMs = config.recastIntervalSeconds * 1000L
        if (now - lastRecastTimestamp >= intervalMs) {
            Log.d(TAG, "등반 중 액티브 스킬 주기적 재활성화 트리거")
            activateAllActiveSkills()
            lastRecastTimestamp = now
        }
    }

    /**
     * 이상 상태 감지 시 안전 복구 워치독
     */
    private suspend fun handleErrorAndRecover() {
        currentState = FsmState.RECOVERY
        Log.w(TAG, "워치독: 예외 발생으로 인한 안전 복구 절차 수행 (뒤로가기 주입)")
        TtAccessibilityService.instance?.pressBackButton()
        delay(1000)
        currentState = FsmState.MONITORING_STAGE
    }

    companion object {
        private const val TAG = "InfiniteCycleFsm"
    }
}

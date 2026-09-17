package com.bisue.tthelper.core

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.bisue.tthelper.MainActivity
import com.bisue.tthelper.R
import com.bisue.tthelper.fsm.FsmState
import com.bisue.tthelper.fsm.InfiniteCycleFsm
import com.bisue.tthelper.touch.HumanTouchEngine
import com.bisue.tthelper.ui.CalibrationOverlayView
import com.bisue.tthelper.ui.FloatingBubbleView
import com.bisue.tthelper.ui.FloatingPanelLayout
import com.bisue.tthelper.vision.OcrEngine
import com.bisue.tthelper.vision.ScreenCapturer

/**
 * Android 14+ MediaProjection 화면 캡처 세션, 플로팅 뷰 및 FSM 라이프사이클 총괄 포그라운드 서비스
 */
class TtForegroundService : Service() {

    private lateinit var config: HelperConfig
    private var mediaProjection: MediaProjection? = null
    private var screenCapturer: ScreenCapturer? = null
    private var ocrEngine: OcrEngine? = null
    private var touchEngine: HumanTouchEngine? = null
    private var fsm: InfiniteCycleFsm? = null

    private var floatingBubble: FloatingBubbleView? = null
    private var floatingPanel: FloatingPanelLayout? = null
    private var calibrationOverlay: CalibrationOverlayView? = null

    override fun onCreate() {
        super.onCreate()
        config = HelperConfig.getInstance(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, 0) ?: 0
        val resultData = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent?.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent?.getParcelableExtra(EXTRA_RESULT_DATA)
        }
        val autoStart = intent?.getBooleanExtra(EXTRA_AUTO_START, false) ?: false

        if (resultCode != 0 && resultData != null && mediaProjection == null) {
            // Android 14+ 포그라운드 서비스 알림 활성화
            startForegroundServiceWithNotification()

            val mpManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            mediaProjection = mpManager.getMediaProjection(resultCode, resultData)

            initAutomationComponents(autoStart)
        }

        return START_STICKY
    }

    private fun startForegroundServiceWithNotification() {
        val notification = createNotification("TT2 헬퍼 대기 중")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun initAutomationComponents(autoStart: Boolean) {
        val mp = mediaProjection ?: return

        // 화면 해상도 및 DPI 획득
        val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getRealMetrics(metrics)

        val screenWidth = metrics.widthPixels
        val screenHeight = metrics.heightPixels
        val densityDpi = metrics.densityDpi

        Log.i(TAG, "S24 Ultra 화면 규격 감지: ${screenWidth}x${screenHeight}, dpi=$densityDpi")

        // 1. UI 위젯을 최우선적으로 초기화하여 화면에 즉시 노출
        floatingBubble = FloatingBubbleView(this) {
            floatingPanel?.show()
        }
        floatingBubble?.show()
        Log.i(TAG, "FloatingBubble 화면 노출 완료")

        touchEngine = HumanTouchEngine(screenWidth, screenHeight)

        // 2. 비전 엔진 초기화
        try {
            screenCapturer = ScreenCapturer(this, mp, screenWidth, screenHeight, densityDpi)
            ocrEngine = OcrEngine()
        } catch (e: Throwable) {
            Log.e(TAG, "비전 엔진 초기화 실패: ${e.message}", e)
        }

        floatingPanel = FloatingPanelLayout(
            context = this,
            config = config,
            onToggleAutomation = { start ->
                if (start) {
                    fsm?.start(config.runSkillSetupOnStart)
                } else {
                    fsm?.stop()
                }
                floatingBubble?.updateStatusBadge(start)
            },
            onSkillSetupClicked = {
                fsm?.triggerSkillSetupNow()
            },
            onManualTestClicked = {
                fsm?.triggerManualLoop()
            },
            onCalibrationClicked = {
                floatingPanel?.hide()
                val sc = screenCapturer
                val ocr = ocrEngine
                val touch = touchEngine
                if (sc != null && ocr != null && touch != null) {
                    if (calibrationOverlay == null) {
                        calibrationOverlay = CalibrationOverlayView(
                            serviceContext = this,
                            config = config,
                            screenCapturer = sc,
                            ocrEngine = ocr,
                            touchEngine = touch,
                            onSaved = {
                                floatingPanel?.show()
                            },
                            onClosed = {
                                floatingPanel?.show()
                            }
                        )
                    }
                    calibrationOverlay?.show()
                } else {
                    Toast.makeText(this, "비전 또는 터치 엔진이 준비되지 않았습니다.", Toast.LENGTH_SHORT).show()
                    floatingPanel?.show()
                }
            },
            onCloseClicked = {
                floatingPanel?.hide()
            }
        )

        // 3. FSM 안전 초기화
        try {
            if (screenCapturer != null && ocrEngine != null && touchEngine != null) {
                fsm = InfiniteCycleFsm(
                    config = config,
                    touchEngine = touchEngine!!,
                    screenCapturer = screenCapturer!!,
                    ocrEngine = ocrEngine!!,
                    onStateChanged = { state ->
                        floatingPanel?.updateState(state)
                        val badgeText = when (state) {
                            FsmState.IDLE -> "OFF"
                            FsmState.MONITORING_STAGE -> "RUN"
                            FsmState.RECOVERY -> "ERR"
                            else -> "ACT"
                        }
                        floatingBubble?.updateStatusBadge(state != FsmState.IDLE, badgeText)
                        updateNotification("상태: ${state.description}")
                    },
                    onStageDetected = { stage ->
                        floatingPanel?.updateDetectedStage(stage)
                        updateNotification("층수: ${String.format("%,d", stage)} / 목표: ${String.format("%,d", config.targetStage)}")
                    }
                )

                if (autoStart) {
                    Log.i(TAG, "자동 시작 플래그 확인 -> FSM 즉시 구동 (스킬 세팅: ${config.runSkillSetupOnStart})")
                    fsm?.start(config.runSkillSetupOnStart)
                    floatingBubble?.updateStatusBadge(true, "RUN")
                }
            }
        } catch (e: Throwable) {
            Log.e(TAG, "비전 캡처/FSM 초기화 실패: ${e.message}", e)
            floatingBubble?.updateStatusBadge(false, "ERR")
            updateNotification("초기화 오류: ${e.message}")
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_desc)
            }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    private fun createNotification(statusText: String): Notification {
        val stopIntent = Intent(this, TtForegroundService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val appIntent = Intent(this, MainActivity::class.java)
        val appPendingIntent = PendingIntent.getActivity(
            this, 0, appIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(statusText)
            .setSmallIcon(R.drawable.ic_helper)
            .setContentIntent(appPendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "헬퍼 종료", stopPendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(statusText: String) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, createNotification(statusText))
    }

    override fun onDestroy() {
        super.onDestroy()
        fsm?.stop()
        calibrationOverlay?.hide()
        calibrationOverlay = null
        floatingPanel?.hide()
        floatingBubble?.hide()
        screenCapturer?.release()
        ocrEngine?.close()
        mediaProjection?.stop()
        mediaProjection = null
        Log.i(TAG, "TtForegroundService 정상 종료")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "TtForegroundService"
        const val CHANNEL_ID = "tt_helper_fg_channel"
        const val NOTIFICATION_ID = 2024
        const val ACTION_STOP = "com.bisue.tthelper.action.STOP"
        const val EXTRA_RESULT_CODE = "extra_result_code"
        const val EXTRA_RESULT_DATA = "extra_result_data"
        const val EXTRA_AUTO_START = "extra_auto_start"
    }
}

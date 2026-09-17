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
import androidx.core.app.NotificationCompat
import com.bisue.tthelper.MainActivity
import com.bisue.tthelper.R
import com.bisue.tthelper.fsm.InfiniteCycleFsm
import com.bisue.tthelper.touch.HumanTouchEngine
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

        if (resultCode != 0 && resultData != null && mediaProjection == null) {
            // Android 14+ 포그라운드 서비스 알림 활성화
            startForegroundServiceWithNotification()

            val mpManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            mediaProjection = mpManager.getMediaProjection(resultCode, resultData)

            initAutomationComponents()
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

    private fun initAutomationComponents() {
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

        // 컴포넌트 초기화
        screenCapturer = ScreenCapturer(this, mp, screenWidth, screenHeight, densityDpi)
        ocrEngine = OcrEngine()
        touchEngine = HumanTouchEngine(screenWidth, screenHeight)

        // UI 위젯 초기화
        floatingBubble = FloatingBubbleView(this) {
            floatingPanel?.show()
        }

        floatingPanel = FloatingPanelLayout(
            context = this,
            config = config,
            onToggleAutomation = { start ->
                if (start) {
                    fsm?.start()
                } else {
                    fsm?.stop()
                }
                floatingBubble?.updateStatusBadge(start)
            },
            onManualTestClicked = {
                fsm?.triggerManualLoop()
            },
            onCloseClicked = {
                floatingPanel?.hide()
            }
        )

        // FSM 생성 및 UI 연동
        fsm = InfiniteCycleFsm(
            config = config,
            touchEngine = touchEngine!!,
            screenCapturer = screenCapturer!!,
            ocrEngine = ocrEngine!!,
            onStateChanged = { state ->
                floatingPanel?.updateState(state)
                updateNotification("상태: ${state.description}")
            },
            onStageDetected = { stage ->
                floatingPanel?.updateDetectedStage(stage)
            }
        )

        floatingBubble?.show()
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
    }
}

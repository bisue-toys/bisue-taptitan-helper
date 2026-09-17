package com.bisue.tthelper

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.bisue.tthelper.core.TtAccessibilityService
import com.bisue.tthelper.core.TtForegroundService
import com.bisue.tthelper.databinding.ActivityMainBinding

/**
 * 3대 핵심 권한(오버레이, 접근성 서비스, 배터리 최적화) 설정 및 화면 캡처 세션 시작 액티비티
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    // Android 14+ MediaProjection 화면 캡처 권한 요청 런처
    private val screenCaptureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            startHelperService(result.resultCode, result.data!!)
        } else {
            Toast.makeText(this, "화면 캡처 권한이 거부되어 헬퍼를 시작할 수 없습니다.", Toast.LENGTH_SHORT).show()
        }
    }

    // 알림 권한 런처 (Android 13+)
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        checkAndRefreshPermissionStatus()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupListeners()
        loadConfigValues()
        requestNotificationPermissionIfNeeded()
    }

    private fun loadConfigValues() {
        val config = HelperConfig.getInstance(this)
        binding.etMainTargetStage.setText(config.targetStage.toString())
        binding.cbMainRunSkillSetupOnStart.isChecked = config.runSkillSetupOnStart
        binding.cbMainAutoRecastSkills.isChecked = config.autoRecastSkills
    }

    override fun onResume() {
        super.onResume()
        checkAndRefreshPermissionStatus()
    }

    private fun setupListeners() {
        // 1. 오버레이 권한
        binding.btnReqOverlay.setOnClickListener {
            if (!Settings.canDrawOverlays(this)) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                startActivity(intent)
            }
        }

        // 2. 접근성 서비스 권한
        binding.btnReqAccessibility.setOnClickListener {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
            Toast.makeText(this, "'TT2 헬퍼 자동 터치' 서비스를 켜주세요.", Toast.LENGTH_LONG).show()
        }

        // 3. 배터리 최적화 해제
        binding.btnReqBattery.setOnClickListener {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
            }
        }

        // 4. 헬퍼 시작
        binding.btnStartHelper.setOnClickListener {
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "먼저 '다른 앱 위에 표시' 권한을 허용해 주세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (TtAccessibilityService.instance == null) {
                Toast.makeText(this, "먼저 '접근성 서비스'를 켜주세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // MediaProjection 화면 캡처 권한 다이얼로그 요청
            val mpManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            screenCaptureLauncher.launch(mpManager.createScreenCaptureIntent())
        }
    }

    private fun checkAndRefreshPermissionStatus() {
        // 1. 오버레이 권한 확인
        val hasOverlay = Settings.canDrawOverlays(this)
        binding.tvOverlayStatus.text = if (hasOverlay) "상태: 허용됨 (정상)" else "상태: 미허용 (필수)"
        binding.tvOverlayStatus.setTextColor(if (hasOverlay) 0xFF4CAF50.toInt() else 0xFFF44336.toInt())
        binding.btnReqOverlay.isEnabled = !hasOverlay

        // 2. 접근성 서비스 상태 확인
        val isAccConnected = (TtAccessibilityService.instance != null)
        binding.tvAccessibilityStatus.text = if (isAccConnected) "상태: 서비스 켜짐 (정상)" else "상태: 서비스 꺼짐 (필수)"
        binding.tvAccessibilityStatus.setTextColor(if (isAccConnected) 0xFF4CAF50.toInt() else 0xFFF44336.toInt())

        // 3. 배터리 최적화 확인
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        val isBatteryIgnored = powerManager.isIgnoringBatteryOptimizations(packageName)
        binding.tvBatteryStatus.text = if (isBatteryIgnored) "상태: 제한 없음 (안전)" else "상태: 최적화 켜짐 (중단 위험)"
        binding.tvBatteryStatus.setTextColor(if (isBatteryIgnored) 0xFF4CAF50.toInt() else 0xFFFF9800.toInt())
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun startHelperService(resultCode: Int, resultData: Intent) {
        val config = HelperConfig.getInstance(this)
        val stageInput = binding.etMainTargetStage.text.toString().trim().toIntOrNull()
        if (stageInput != null && stageInput > 0) {
            config.targetStage = stageInput
        }
        config.runSkillSetupOnStart = binding.cbMainRunSkillSetupOnStart.isChecked
        config.autoRecastSkills = binding.cbMainAutoRecastSkills.isChecked

        val serviceIntent = Intent(this, TtForegroundService::class.java).apply {
            putExtra(TtForegroundService.EXTRA_RESULT_CODE, resultCode)
            putExtra(TtForegroundService.EXTRA_RESULT_DATA, resultData)
            putExtra(TtForegroundService.EXTRA_AUTO_START, true)
        }
        ContextCompat.startForegroundService(this, serviceIntent)
        Toast.makeText(this, "TT2 헬퍼가 자동 시작되었습니다. 게임을 켜주세요!", Toast.LENGTH_SHORT).show()
        
        // 홈 화면 또는 게임으로 바로 전환할 수 있도록 액티비티 최소화
        moveTaskToBack(true)
    }
}

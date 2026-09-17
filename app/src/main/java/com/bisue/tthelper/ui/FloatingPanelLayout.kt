package com.bisue.tthelper.ui

import android.content.Context
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import com.bisue.tthelper.R
import com.bisue.tthelper.core.HelperConfig
import com.bisue.tthelper.fsm.FsmState

/**
 * 플로팅 버블 클릭 시 열리는 상세 제어 및 실시간 상태 모니터링 패널
 */
class FloatingPanelLayout(
    private val context: Context,
    private val config: HelperConfig,
    private val onToggleAutomation: (Boolean) -> Unit,
    private val onSkillSetupClicked: () -> Unit,
    private val onManualTestClicked: () -> Unit,
    private val onCloseClicked: () -> Unit
) {
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val themedContext = android.view.ContextThemeWrapper(context, R.style.Theme_TapTitanHelper)
    val view: View = LayoutInflater.from(themedContext).inflate(R.layout.view_floating_panel, null)

    private val tvStatus: TextView = view.findViewById(R.id.tvCurrentStatus)
    private val tvDetectedStage: TextView = view.findViewById(R.id.tvDetectedStage)
    private val etTargetStage: EditText = view.findViewById(R.id.etTargetStage)
    private val cbAutoRecast: CheckBox = view.findViewById(R.id.cbAutoRecastSkills)
    private val btnToggle: Button = view.findViewById(R.id.btnToggleAutomation)
    private val btnSkillSetupNow: Button = view.findViewById(R.id.btnSkillSetupNow)
    private val btnManualTest: Button = view.findViewById(R.id.btnManualTestLoop)
    private val btnClose: View = view.findViewById(R.id.btnPanelClose)

    private val params = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        // EditText 입력을 받을 수 있도록 FLAG_NOT_TOUCH_MODAL 설정
        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
        PixelFormat.TRANSLUCENT
    ).apply {
        gravity = Gravity.CENTER
    }

    init {
        setupViews()
    }

    private fun setupViews() {
        etTargetStage.setText(config.targetStage.toString())
        cbAutoRecast.isChecked = config.autoRecastSkills

        cbAutoRecast.setOnCheckedChangeListener { _, isChecked ->
            config.autoRecastSkills = isChecked
        }

        btnToggle.setOnClickListener {
            saveTargetStage()
            val willRun = !config.isAutomationRunning
            updateRunButtonState(willRun)
            android.widget.Toast.makeText(context, if (willRun) "자동화 루프를 시작합니다." else "자동화를 일시 정지했습니다.", android.widget.Toast.LENGTH_SHORT).show()
            onToggleAutomation(willRun)
        }

        btnSkillSetupNow.setOnClickListener {
            saveTargetStage()
            android.widget.Toast.makeText(context, "스킬 1렙 해금 및 활성화를 시작합니다.", android.widget.Toast.LENGTH_SHORT).show()
            onSkillSetupClicked()
        }

        btnManualTest.setOnClickListener {
            saveTargetStage()
            android.widget.Toast.makeText(context, "환생 및 전체 사이클 테스트를 시작합니다.", android.widget.Toast.LENGTH_SHORT).show()
            onManualTestClicked()
        }

        btnClose.setOnClickListener {
            saveTargetStage()
            onCloseClicked()
        }
    }

    private fun saveTargetStage() {
        val input = etTargetStage.text.toString().trim()
        val stage = input.toIntOrNull()
        if (stage != null && stage > 0) {
            config.targetStage = stage
        }
    }

    fun updateState(state: FsmState) {
        view.post {
            tvStatus.text = state.description
            when (state) {
                FsmState.IDLE -> tvStatus.setTextColor(0xFF03DAC5.toInt())
                FsmState.MONITORING_STAGE -> tvStatus.setTextColor(0xFF4CAF50.toInt())
                FsmState.RECOVERY -> tvStatus.setTextColor(0xFFF44336.toInt())
                else -> tvStatus.setTextColor(0xFFFF9800.toInt())
            }
        }
    }

    fun updateDetectedStage(stage: Int) {
        view.post {
            tvDetectedStage.text = String.format("Stage %,d", stage)
        }
    }

    fun updateRunButtonState(isRunning: Boolean) {
        view.post {
            if (isRunning) {
                btnToggle.text = "자동화 일시 정지"
                btnToggle.setBackgroundColor(0xFFF44336.toInt())
            } else {
                btnToggle.text = "자동화 루프 시작"
                btnToggle.setBackgroundColor(0xFF4CAF50.toInt())
            }
        }
    }

    fun show() {
        if (view.parent == null) {
            etTargetStage.setText(config.targetStage.toString())
            updateRunButtonState(config.isAutomationRunning)
            windowManager.addView(view, params)
        }
    }

    fun hide() {
        if (view.parent != null) {
            saveTargetStage()
            windowManager.removeView(view)
        }
    }
}

package com.bisue.tthelper.touch

import android.graphics.PointF
import android.graphics.RectF

/**
 * 갤럭시 S24 Ultra (19.5:9 화면 비율) 최적화 상대 좌표 프로파일
 * 해상도(QHD+ 3120x1440, FHD+ 2340x1080) 설정에 무관하게 작동하도록
 * 0.0 ~ 1.0 비율 기반 상대 좌표로 환산합니다.
 */
object CoordinateProfile {

    // 1. 화면 상단 층수(Stage) 표시 영역 (ROI: Region of Interest)
    // 상단 펀치홀 카메라 아래, 스테이지 텍스트가 표시되는 영역
    val STAGE_ROI_RATIO = RectF(0.20f, 0.035f, 0.80f, 0.085f)

    // 2. 하단 네비게이션 탭 바 (총 6개 탭 중 1번 소드마스터 탭)
    val TAB_SWORDMASTER = PointF(0.08f, 0.94f)

    // 3. 소드마스터 탭 내부 컨트롤
    // 레벨업 단위 토글 버튼 (x1 -> x10 -> x100 -> MAX)
    val BTN_LEVELUP_MULTIPLIER = PointF(0.86f, 0.475f)

    // 소드마스터 탭 레벨업 버튼 (소드마스터 초상화 우측)
    val BTN_SWORDMASTER_UPGRADE = PointF(0.82f, 0.54f)

    // 4. 스킬 해금(1레벨) 버튼 목록 (소드마스터 탭 내부)
    // 상단 4개 스킬 (스크롤 전)
    val BTN_SKILL_1_HEAVENLY = PointF(0.82f, 0.635f)
    val BTN_SKILL_2_DEADLY = PointF(0.82f, 0.715f)
    val BTN_SKILL_3_MIDAS = PointF(0.82f, 0.795f)
    val BTN_SKILL_4_FIRESWORD = PointF(0.82f, 0.875f)

    // 하단 2개 스킬 (살짝 스크롤 후 노출)
    val BTN_SKILL_5_WARCRY = PointF(0.82f, 0.70f)
    val BTN_SKILL_6_CLONE = PointF(0.82f, 0.78f)

    // 스킬 목록 아래로 살짝 스크롤하기 위한 제스처 좌표
    val SCROLL_SKILL_START = PointF(0.50f, 0.85f)
    val SCROLL_SKILL_END = PointF(0.50f, 0.60f)

    // 5. 환생(Prestige) 관련 제스처 및 버튼
    // 소드마스터 탭 최하단으로 크게 스크롤
    val SCROLL_PRESTIGE_START = PointF(0.50f, 0.85f)
    val SCROLL_PRESTIGE_END = PointF(0.50f, 0.35f)

    // 환생(Prestige) 진입 버튼 (최하단 스크롤 후)
    val BTN_PRESTIGE = PointF(0.50f, 0.86f)

    // 환생 확인 팝업 승인 버튼
    val BTN_CONFIRM_PRESTIGE = PointF(0.50f, 0.68f)

    // 6. 전투 화면 하단 6개 액티브 스킬 슬롯 (1번 ~ 6번)
    val ACTIVE_SKILL_SLOTS = listOf(
        PointF(0.12f, 0.825f), // 1. 천상의 일격
        PointF(0.27f, 0.825f), // 2. 치명적인 일격
        PointF(0.42f, 0.825f), // 3. 마이더스의 손
        PointF(0.58f, 0.825f), // 4. 불타는 검
        PointF(0.73f, 0.825f), // 5. 함성
        PointF(0.88f, 0.825f)  // 6. 그림자 분신
    )

    // 7. 하단 메뉴 닫기 / 전투 화면 포커스 (화면 중앙 상단 터치)
    val CLOSE_TAB_TAP = PointF(0.50f, 0.25f)

    /**
     * 상대 비율 좌표를 현재 기기의 실제 픽셀 좌표로 변환
     */
    fun toPixel(point: PointF, screenWidth: Int, screenHeight: Int): Pair<Float, Float> {
        return Pair(point.x * screenWidth, point.y * screenHeight)
    }

    fun toPixelRect(rect: RectF, screenWidth: Int, screenHeight: Int): android.graphics.Rect {
        return android.graphics.Rect(
            (rect.left * screenWidth).toInt(),
            (rect.top * screenHeight).toInt(),
            (rect.right * screenWidth).toInt(),
            (rect.bottom * screenHeight).toInt()
        )
    }
}

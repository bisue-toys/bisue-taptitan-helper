package com.bisue.tthelper.fsm

/**
 * 5단계 무한 자동화 루프를 위한 FSM 세부 상태 정의
 */
enum class FsmState(val description: String) {
    IDLE("대기 중 (IDLE)"),

    // 5. 등반 모니터링
    MONITORING_STAGE("층수 감시 및 등반 중"),

    // 1. 환생(Prestige) 시퀀스
    PRESTIGE_OPEN_TAB("소드마스터 탭 열기"),
    PRESTIGE_SCROLL_DOWN("환생 버튼 위치로 스크롤"),
    PRESTIGE_CLICK_BUTTON("환생 버튼 클릭"),
    PRESTIGE_CONFIRM_MODAL("환생 확인 팝업 승인"),
    PRESTIGE_WAIT_RESET("환생 리셋 및 로딩 대기"),

    // 2. 소드마스터 레벨업 시퀀스
    SWORDMASTER_PREPARE_MAX("레벨업 단위 MAX 확인"),
    SWORDMASTER_UPGRADE_TAP("소드마스터 레벨업 (스킬 해금용)"),

    // 3. 스킬 1레벨 해금 시퀀스
    SKILLS_SWITCH_X1("레벨업 단위를 x1로 전환 (1렙만 찍기)"),
    SKILLS_UNLOCK_TOP_4("상위 4대 스킬 해금 (천상/치명/마이더스/불꽃)"),
    SKILLS_SCROLL_DOWN("스킬 목록 아래로 스크롤"),
    SKILLS_UNLOCK_BOTTOM_2("하위 2대 스킬 해금 (함성/분신)"),

    // 4. 스킬 전체 활성화 시퀀스
    ACTIVATE_CLOSE_TAB("하단 메뉴 닫기 (전투 화면 복귀)"),
    ACTIVATE_ALL_SKILLS("6대 액티브 스킬 전체 순차 발동"),

    // 예외 복구
    RECOVERY("돌발 팝업 닫기 및 안전 복구 중")
}

# Tap Titans 2 Auto-Prestige Helper (Galaxy S24 Ultra)

갤럭시 S24 Ultra (SM-S928N) 환경에 최적화된 **Tap Titans 2 완전 자율 무한 루프 자동화 모바일 헬퍼** 프로젝트입니다.

---

## 🚀 주요 기능

1. **5단계 완전 자율 무한 사이클**:
   - `환생 (Prestige)` ➔ `소드마스터 레벨업 (Lv 600+)` ➔ `6대 액티브 스킬 1레벨씩만 해금 (마나 보존)` ➔ `6대 스킬 전체 동시 활성화` ➔ `목표 층수 도달 감시` ➔ `무한 반복`
2. **100% 모바일 단독 실행**:
   - PC 연결이나 Wi-Fi/무선 ADB 없이 스마트폰 하나로 이동 중(지하철, 버스 등)에도 완벽 동작.
3. **무루트(No-Root) 안전 터치 엔진**:
   - Android `AccessibilityService`를 활용하여 루팅 없이 구동.
   - 밴(Ban) 방지를 위한 **가우시안 랜덤 좌표 오차($\pm 5\sim 15\text{px}$)** 및 **터치 딜레이 난수화** 적용.
4. **Google ML Kit 온디바이스 OCR**:
   - 데이터 통신 0MB, NPU/CPU 기반 20ms 내 초고속 층수 인식.
   - 다중 프레임 검증(Hysteresis): 2회 연속 목표치 확인 시에만 환생 개시.
5. **강인한 자가 복구 (Self-Healing Watchdog)**:
   - 외부 요정/광고 팝업 시 `뒤로가기(Back)` 자동 닫기.
   - 타 앱(카카오톡 등) 전환 시 터치 주입 자동 일시정지 (포그라운드 패키지 가드).
6. **플로팅 오버레이 대시보드**:
   - 게임 화면 위에 상시 떠 있는 드래그 가능한 미니 버블 & 제어 패널.

---

## 📁 프로젝트 구조

```
bisue-taptitan-helper/
├── app/
│   ├── src/main/
│   │   ├── AndroidManifest.xml
│   │   ├── java/com/bisue/tthelper/
│   │   │   ├── MainActivity.kt                // 권한 가이드 및 화면 캡처 세션 시작
│   │   │   ├── core/
│   │   │   │   ├── HelperConfig.kt           // 목표 층수 및 설정 저장소
│   │   │   │   ├── TtAccessibilityService.kt // 무루트 터치 주입 및 앱 이탈 가드
│   │   │   │   └── TtForegroundService.kt    // MediaProjection 및 FSM 라이프사이클
│   │   │   ├── fsm/
│   │   │   │   ├── FsmState.kt               // 5단계 루프 세부 상태 정의
│   │   │   │   └── InfiniteCycleFsm.kt       // 환생-탭업-스킬해금-활성화 FSM
│   │   │   ├── touch/
│   │   │   │   ├── CoordinateProfile.kt      // S24 Ultra 상대 좌표 프로파일
│   │   │   │   └── HumanTouchEngine.kt       // 가우시안 지터 및 제스처 디스패처
│   │   │   ├── ui/
│   │   │   │   ├── FloatingBubbleView.kt     // 플로팅 버블 위젯
│   │   │   │   └── FloatingPanelLayout.kt    // 상세 설정 및 실시간 모니터링 패널
│   │   │   └── vision/
│   │   │       ├── ImagePreprocessor.kt      // OCR 대비 향상 및 전처리
│   │   │       ├── OcrEngine.kt              // Google ML Kit 텍스트 인식기
│   │   │       ├── ScreenCapturer.kt         // 상단 층수 영역 ImageReader 고속 크롭
│   │   │       └── StageParser.kt            // 층수 정규식 파싱 & 다중 프레임 검증
│   │   └── res/                              // 레이아웃, 벡터 아이콘, 테마 정의
│   └── src/test/java/com/bisue/tthelper/
│       ├── CoordinateProfileTest.kt          // S24 Ultra 좌표 변환 유닛테스트
│       └── StageParserTest.kt                // 층수 OCR 파싱 & 노이즈 필터 유닛테스트
└── doc/
    ├── feasibility_study.md                  // 모바일 단독 구동 기술 검토 보고서
    └── user_guide.md                         // 갤럭시 S24 Ultra 설치 및 사용 가이드
```

---

## 📖 문서 바로가기

- [기술 검토 보고서 (feasibility_study.md)](doc/feasibility_study.md)
- [설치 및 상세 사용 설명서 (user_guide.md)](doc/user_guide.md)

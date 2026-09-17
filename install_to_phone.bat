@echo off
chcp 65001 >nul
echo ========================================================
echo   Tap Titans 2 Helper - Galaxy S24 Ultra 원클릭 설치기
echo ========================================================
echo.
echo 연결된 기기 확인 중...
"%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe" devices
echo.
echo TapTitanHelper.apk 설치를 진행합니다...
"%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe" install -r -d "%~dp0TapTitanHelper.apk"
if %ERRORLEVEL% equ 0 (
    echo.
    echo [성공] 갤럭시 S24 Ultra에 TT2 헬퍼가 성공적으로 설치되었습니다!
) else (
    echo.
    echo [설치 실패] 스마트폰이 USB로 연결되어 있고, 개발자 옵션의 'USB 디버깅'이 켜져 있는지 확인해 주세요.
)
echo.
pause

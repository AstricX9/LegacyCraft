@echo off
setlocal EnableDelayedExpansion
rem LegacyCraft build console: builds ready-to-copy packages for each OS (see "packages" in build.gradle).
rem   build.bat                 the menu
rem   build.bat linuxX64 [ver]  one package straight away (windows, windowsX64, windowsX86, windowsArm64,
rem                             linuxX64, linuxArm64, macosX64, macosArm64 or all), optionally setting the version
cd /d "%~dp0"
title LegacyCraft build console

if not "%~1"=="" (
    set "TARGET=%~1"
    set "VERSION_ARG="
    if not "%~2"=="" set "VERSION_ARG=-PlcVersion=%~2"
    if /i "!TARGET!"=="all" (set "TASK=packageAll") else (set "TASK=package!TARGET!")
    call gradlew.bat !TASK! !VERSION_ARG!
    exit /b !errorlevel!
)

:menu
set "LC=?"
for /f "usebackq tokens=1,* delims=:" %%a in ("version.txt") do if /i "%%a"=="lc" set "LC=%%b"
set "LC=!LC: =!"
cls
echo.
echo   LegacyCraft build console                       version !LC!
echo   ---------------------------------------------------------------
echo.
echo   Packages (game + launcher, ready to copy to another PC)
echo     1  Windows, 64- and 32-bit together (Windows 7 or later)
echo     2  Windows 64-bit
echo     3  Windows 32-bit
echo     4  Windows ARM64
echo     5  Linux x64
echo     6  Linux ARM64
echo     7  macOS Intel
echo     8  macOS Apple silicon
echo     9  All of the above
echo.
echo     V  Change the version (now !LC!)
echo     O  Open the folder with the packages
echo     R  Run the game from source
echo     T  Run the tests
echo     Q  Quit
echo.
choice /c 123456789VORTQ /n /m "  Pick one: "
set "PICK=%errorlevel%"
if "%PICK%"=="1" call :package Windows "Windows (64- and 32-bit)"
if "%PICK%"=="2" call :package WindowsX64 "Windows 64-bit"
if "%PICK%"=="3" call :package WindowsX86 "Windows 32-bit"
if "%PICK%"=="4" call :package WindowsArm64 "Windows ARM64"
if "%PICK%"=="5" call :package LinuxX64 "Linux x64"
if "%PICK%"=="6" call :package LinuxArm64 "Linux ARM64"
if "%PICK%"=="7" call :package MacosX64 "macOS Intel"
if "%PICK%"=="8" call :package MacosArm64 "macOS Apple silicon"
if "%PICK%"=="9" call :package All "every OS"
if "%PICK%"=="10" call :version
if "%PICK%"=="11" call :open
if "%PICK%"=="12" call :gradle run
if "%PICK%"=="13" call :gradle test
if "%PICK%"=="14" exit /b 0
goto menu

:package
echo.
echo   Building LegacyCraft !LC! for %~2...
echo.
call gradlew.bat package%~1
if errorlevel 1 (
    echo.
    echo   The build failed; the reason is above.
) else (
    echo.
    echo   Done. The zip is in %CD%\build\distributions
)
echo.
pause
exit /b 0

:version
echo.
set "NEW="
set /p "NEW=  New version (Enter keeps !LC!): "
if "!NEW!"=="" exit /b 0
call gradlew.bat -q setLcVersion "-PlcVersion=!NEW!"
pause
exit /b 0

:open
if not exist "build\distributions" mkdir "build\distributions"
start "" explorer "%CD%\build\distributions"
exit /b 0

:gradle
call gradlew.bat %*
echo.
pause
exit /b 0

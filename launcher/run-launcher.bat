@echo off
setlocal
set "LAUNCHER_ROOT=%~dp0"
set "LAUNCHER_CLASSES=%LAUNCHER_ROOT%build\classes"

if not exist "%LAUNCHER_CLASSES%" mkdir "%LAUNCHER_CLASSES%"
dir /s /b "%LAUNCHER_ROOT%src\main\java\*.java" > "%LAUNCHER_ROOT%build\sources.txt"
javac --release 17 -encoding UTF-8 -d "%LAUNCHER_CLASSES%" @"%LAUNCHER_ROOT%build\sources.txt"
if errorlevel 1 (
    pause
    exit /b %errorlevel%
)
xcopy /e /y /q "%LAUNCHER_ROOT%src\main\resources\*" "%LAUNCHER_CLASSES%\" > nul
rem The Additions list is the game's; a copy goes with the launcher for when it can't find the LegacyCraft folder.
copy /y "%LAUNCHER_ROOT%..\src\main\resources\assets\legacycraft\additions.json" "%LAUNCHER_CLASSES%\net\legacycraft\launcher\additions.json" > nul

start "" javaw -cp "%LAUNCHER_CLASSES%" net.legacycraft.launcher.LauncherApp

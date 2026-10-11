@echo off
setlocal
rem Starts the LegacyCraft launcher. Uses the Java in runtime\ when there is one, else the one on PATH.
set "ROOT=%~dp0"
call "%ROOT%lib\find-java.bat" || exit /b 1
start "" "%JAVA_DIR%javaw.exe" -jar "%ROOT%launcher\LegacyCraftLauncher.jar"

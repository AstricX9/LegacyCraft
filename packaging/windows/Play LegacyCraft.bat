@echo off
setlocal
rem Plays LegacyCraft straight away, without the launcher. Saves and options go in the game folder next to this file.
set "ROOT=%~dp0"
call "%ROOT%lib\find-java.bat" || exit /b 1
set "MEMORY=-Xmx2g"
rem A 32-bit Java can't reserve 2 GB.
"%JAVA_DIR%java.exe" -XshowSettings:properties -version 2>&1 | findstr /c:"sun.arch.data.model = 32" >nul && set "MEMORY=-Xmx1g"
set "PLAYER=%~1"
if "%PLAYER%"=="" set "PLAYER=Player"
"%JAVA_DIR%java.exe" -Xms512m %MEMORY% -cp "%ROOT%lib\*" net.minecraft.client.main.Main "--gamedir=%ROOT%game" "--username=%PLAYER%"
if errorlevel 1 pause

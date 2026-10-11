@echo off
rem Sets JAVA_DIR to the bin folder (with a trailing backslash) of a Java 17 or newer:
rem runtime\ next to LegacyCraft first, then JAVA_HOME, then whatever is on PATH.
set "JAVA_DIR="
if exist "%~dp0..\runtime\bin\java.exe" set "JAVA_DIR=%~dp0..\runtime\bin\"
if not defined JAVA_DIR if defined JAVA_HOME if exist "%JAVA_HOME%\bin\java.exe" set "JAVA_DIR=%JAVA_HOME%\bin\"
if not defined JAVA_DIR for %%J in (java.exe) do if not "%%~$PATH:J"=="" set "JAVA_DIR=%%~dp$PATH:J"
if not defined JAVA_DIR goto missing
"%JAVA_DIR%java.exe" -version 2>&1 | findstr /r /c:"version \"1[7-9]" /c:"version \"[2-9][0-9]" >nul || goto old
exit /b 0

:missing
echo LegacyCraft needs Java 17, and none was found.
goto help
:old
echo LegacyCraft needs Java 17 or newer, but the Java found is older:
"%JAVA_DIR%java.exe" -version
goto help
:help
echo.
echo Download the Java 17 JRE as a .zip (Windows x64, or x86 for 32-bit Windows),
echo unzip it, and rename its folder to "runtime" inside the LegacyCraft folder.
echo See README.txt for the details.
echo.
pause
exit /b 1

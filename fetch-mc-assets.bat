@echo off
setlocal EnableExtensions

cd /d "%~dp0"
set "LEGACYCRAFT_ROOT=%CD%"
set "ASSET_URL=https://github.com/InventivetalentDev/minecraft-assets/archive/refs/heads/1.8.9.zip"

echo.
echo LegacyCraft Minecraft 1.8.9 asset setup
echo ----------------------------------------
echo.
echo This downloads the vanilla 1.8.9 assets from the source linked by
echo https://mcasset.cloud/1.8.9/ and copies only assets\minecraft into
echo src\main\resources\assets\minecraft.
echo.
echo The downloaded files are not part of LegacyCraft and are not committed.
echo Make sure you are entitled to use the assets before continuing.
echo.

if not exist "%LEGACYCRAFT_ROOT%\src\main\resources\assets" (
    echo ERROR: This does not look like the LegacyCraft repository.
    echo Run this file from the repository checkout.
    exit /b 1
)

if exist "%LEGACYCRAFT_ROOT%\src\main\resources\assets\minecraft" (
    echo ERROR: src\main\resources\assets\minecraft already exists.
    echo Remove or rename it manually if you want to fetch a fresh copy.
    exit /b 1
)

where powershell.exe >nul 2>nul
if errorlevel 1 (
    echo ERROR: Windows PowerShell was not found.
    echo This helper is intended for Windows 10 or newer.
    exit /b 1
)

powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -Command ^
  "$ErrorActionPreference = 'Stop'; ^
   $ProgressPreference = 'SilentlyContinue'; ^
   [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12; ^
   $work = Join-Path $env:TEMP ('LegacyCraft-mc-assets-' + [guid]::NewGuid().ToString('N')); ^
   $archive = Join-Path $work 'minecraft-assets.zip'; ^
   try { ^
     New-Item -ItemType Directory -Path $work | Out-Null; ^
     Write-Host 'Downloading the 1.8.9 asset archive...'; ^
     Invoke-WebRequest -UseBasicParsing -Uri '%ASSET_URL%' -OutFile $archive; ^
     Write-Host 'Extracting the asset archive...'; ^
     Expand-Archive -LiteralPath $archive -DestinationPath $work; ^
     $checkout = Get-ChildItem -LiteralPath $work -Directory | Where-Object { $_.Name -like 'minecraft-assets-*' } | Select-Object -First 1; ^
     if ($null -eq $checkout) { throw 'The downloaded archive has an unexpected layout.' }; ^
     $source = Join-Path $checkout.FullName 'assets\minecraft'; ^
     if (!(Test-Path -LiteralPath (Join-Path $source 'blockstates'))) { throw 'The archive does not contain assets\minecraft\blockstates.' }; ^
     $destination = Join-Path $env:LEGACYCRAFT_ROOT 'src\main\resources\assets\minecraft'; ^
     if (Test-Path -LiteralPath $destination) { throw 'The destination already exists.' }; ^
     Copy-Item -LiteralPath $source -Destination $destination -Recurse; ^
     Write-Host 'Minecraft 1.8.9 assets installed successfully.'; ^
     Write-Host ('Installed at: ' + $destination); ^
   } finally { ^
     if (Test-Path -LiteralPath $work) { Remove-Item -LiteralPath $work -Recurse -Force -ErrorAction SilentlyContinue } ^
   }"

if errorlevel 1 (
    echo.
    echo Asset setup failed. No existing asset folder was overwritten.
    exit /b 1
)

echo.
echo You can now run: gradlew.bat run
exit /b 0

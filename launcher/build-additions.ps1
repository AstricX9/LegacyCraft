param([Parameter(Mandatory = $true)][string]$LauncherClasses)
$ErrorActionPreference = 'Stop'
$catalog = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../additions'))
foreach ($manifest in (Get-ChildItem -LiteralPath $catalog -File -Filter '*.json')) {
    if ($manifest.Name.StartsWith('_')) { continue }
    $definition = Get-Content -LiteralPath $manifest.FullName -Raw | ConvertFrom-Json
    if ($definition.scope -ne 'launcher' -or !$definition.entrypoint -or !$definition.code) { continue }
    if ($definition.id -notmatch '^[A-Za-z0-9_-]+$') { throw 'Invalid addition ID' }
    $module = Join-Path $catalog $definition.id
    $sources = @(Get-ChildItem -LiteralPath (Join-Path $module 'src/main/java') -Recurse -File -Filter '*.java' -ErrorAction SilentlyContinue)
    if ($sources.Count -eq 0) { continue }
    $jarPath = [System.IO.Path]::GetFullPath((Join-Path $catalog $definition.code))
    if (!$jarPath.StartsWith($catalog + [System.IO.Path]::DirectorySeparatorChar)) { throw 'Module code must stay in additions/' }
    $classes = Join-Path $module 'build/launcher-classes'
    New-Item -ItemType Directory -Force -Path $classes | Out-Null
    & javac --release 17 -encoding UTF-8 -cp $LauncherClasses -d $classes $sources.FullName
    if ($LASTEXITCODE -ne 0) { throw "Could not compile $($definition.id)" }
    & jar --create --file $jarPath -C $classes .
    if ($LASTEXITCODE -ne 0) { throw "Could not package $($definition.id)" }
}

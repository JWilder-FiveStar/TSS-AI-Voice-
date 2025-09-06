param(
    [switch]$Clean,
    [switch]$SkipBuild,
    [switch]$VerboseLog
)

$ErrorActionPreference = 'Stop'

function Write-Info($msg) { Write-Host "[INFO ] $msg" -ForegroundColor Cyan }
function Write-Warn($msg) { Write-Host "[WARN ] $msg" -ForegroundColor Yellow }
function Write-Err($msg)  { Write-Host "[ERROR] $msg" -ForegroundColor Red }

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $scriptDir

$gradlew = Join-Path $scriptDir 'gradlew.bat'
if (-not (Test-Path $gradlew)) {
    Write-Err "gradlew.bat not found in $scriptDir"
    exit 1
}

if ($Clean) {
    Write-Info "Cleaning project..."
    & $gradlew clean | Out-Null
}

if (-not $SkipBuild) {
    Write-Info "Building fat jar (:osrs-tts-runelite-plugin:fatJar)..."
    & $gradlew ":osrs-tts-runelite-plugin:fatJar" | ForEach-Object { if ($VerboseLog) { $_ } }  
    if ($LASTEXITCODE -ne 0) { Write-Err "Gradle build failed (exit $LASTEXITCODE)"; exit $LASTEXITCODE }
} else {
    Write-Warn "Skipping build (-SkipBuild specified)"
}

$jarDir = Join-Path $scriptDir 'osrs-tts-runelite-plugin\build\libs'
if (-not (Test-Path $jarDir)) { Write-Err "Jar output dir not found: $jarDir"; exit 1 }

$jar = Get-ChildItem $jarDir -File -Filter 'osrs-tts-runelite-plugin-*-all.jar' | Sort-Object LastWriteTime -Descending | Select-Object -First 1
if (-not $jar) { Write-Err "Fat jar not found in $jarDir (expected *-all.jar)."; exit 1 }

$rlHome = Join-Path $env:USERPROFILE '.runelite'
$pluginsDir = Join-Path $rlHome 'plugins'
if (-not (Test-Path $pluginsDir)) { Write-Info "Creating plugins directory: $pluginsDir"; New-Item -ItemType Directory -Path $pluginsDir | Out-Null }

$dest = Join-Path $pluginsDir 'osrs-tts-runelite-plugin.jar'
Copy-Item $jar.FullName $dest -Force

Write-Host "--------------------------------------------------" -ForegroundColor DarkGray
Write-Info "Deployed plugin -> $dest"
Write-Host "Source jar: $($jar.FullName)" -ForegroundColor DarkGray
Write-Host "Timestamp : $($jar.LastWriteTime)" -ForegroundColor DarkGray
Write-Host "--------------------------------------------------" -ForegroundColor DarkGray
Write-Host "Next steps:" -ForegroundColor Green
Write-Host " 1. Start (or restart) your normal RuneLite client." -ForegroundColor Green
Write-Host " 2. RuneLite auto-loads external plugins from ~/.runelite/plugins." -ForegroundColor Green
Write-Host " 3. Open the sidebar and configure 'Old School RuneScape TTS'." -ForegroundColor Green
Write-Host "" 
Write-Host "Flags: -Clean -SkipBuild -VerboseLog" -ForegroundColor DarkGray

exit 0

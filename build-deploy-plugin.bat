@echo off
setlocal ENABLEDELAYEDEXPANSION

REM Build the fat (all) jar for the TTS plugin
call gradlew :osrs-tts-runelite-plugin:fatJar || goto :error

set PLUGIN_NAME=osrs-tts-runelite-plugin
set VERSION=1.0-SNAPSHOT
set BUILT_JAR=osrs-tts-runelite-plugin\build\libs\%PLUGIN_NAME%-%VERSION%-all.jar

if not exist "%BUILT_JAR%" (
  echo Built jar not found: %BUILT_JAR%
  goto :error
)

REM RuneLite external plugins directory (created if missing)
set RL_HOME=%USERPROFILE%\.runelite
set PLUGINS_DIR=%RL_HOME%\plugins
if not exist "%PLUGINS_DIR%" mkdir "%PLUGINS_DIR%"

REM Deploy (overwrite old copy with simpler name for convenience)
set DEPLOY_JAR=%PLUGINS_DIR%\%PLUGIN_NAME%.jar
copy /Y "%BUILT_JAR%" "%DEPLOY_JAR%" >nul || goto :error

echo --------------------------------------------------
echo Deployed plugin jar to:
echo   %DEPLOY_JAR%
echo --------------------------------------------------
echo Next steps:
echo   1. Launch your normal RuneLite client (exe or launcher)
echo   2. Ensure External Plugins are enabled (RuneLite auto-loads jars in plugins directory)
echo   3. Open the sidebar and configure "Old School RuneScape TTS"
echo.
echo (If RuneLite was already open, restart it to pick up the new jar.)
echo Done.
exit /b 0

:error
echo Build or deployment failed with error level %ERRORLEVEL%.
exit /b 1

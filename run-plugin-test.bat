@echo off
setlocal
cd /d "%~dp0"
call gradlew.bat :osrs-tts-runelite-plugin:runPlugin --no-daemon
endlocal

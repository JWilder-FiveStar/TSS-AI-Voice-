@echo off
setlocal
cd /d "%~dp0"
call gradlew.bat :dev-run:run --no-daemon
endlocal

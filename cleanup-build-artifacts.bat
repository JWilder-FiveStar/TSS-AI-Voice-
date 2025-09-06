@echo off
REM Quick cleanup of generated build artifacts to reduce visible file count in IDE
REM Keeps only the built jars; removes intermediate class/resource expansion and tmp dirs.

setlocal

REM Run Gradle clean first (removes build/ entirely)
call gradlew clean >nul 2>&1

REM Optionally prune any stray extraction folders if they exist
for %%D in (jarExtract fat-work) do (
  if exist "osrs-tts-runelite-plugin\build\%%D" (
    echo Removing leftover %%D folder...
    rmdir /s /q "osrs-tts-runelite-plugin\build\%%D"
  )
)

echo Cleanup complete. Rebuild with: gradlew :osrs-tts-runelite-plugin:fatJar
exit /b 0

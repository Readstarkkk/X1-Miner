@echo off
setlocal
set "VERSION=8.13"
set "BASE=%USERPROFILE%\.gradle\x1-bootstrap"
set "GH=%BASE%\gradle-%VERSION%"
if not exist "%GH%\bin\gradle.bat" (
  if not exist "%BASE%" mkdir "%BASE%"
  powershell -NoProfile -ExecutionPolicy Bypass -Command "$ErrorActionPreference='Stop'; $ProgressPreference='SilentlyContinue'; Invoke-WebRequest -Uri 'https://services.gradle.org/distributions/gradle-%VERSION%-bin.zip' -OutFile '%BASE%\gradle-%VERSION%-bin.zip'; $actual=(Get-FileHash '%BASE%\gradle-%VERSION%-bin.zip' -Algorithm SHA256).Hash.ToLower(); if ($actual -ne '20f1b1176237254a6fc204d8434196fa11a4cfb387567519c61556e8710aed78') { Remove-Item '%BASE%\gradle-%VERSION%-bin.zip' -Force; throw 'Gradle SHA-256 verification failed' }; Expand-Archive -Force '%BASE%\gradle-%VERSION%-bin.zip' '%BASE%\extract'; if (Test-Path '%GH%') { Remove-Item -Recurse -Force '%GH%' }; Move-Item '%BASE%\extract\gradle-%VERSION%' '%GH%'; Remove-Item -Recurse -Force '%BASE%\extract'"
  if errorlevel 1 exit /b 1
)
call "%GH%\bin\gradle.bat" -p "%~dp0" %*

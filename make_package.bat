@echo off
setlocal enabledelayedexpansion

:: Read version from manifest file
for /f "tokens=2 delims==" %%v in ('findstr /b /i "version=" properties\manifest') do (
    set VERSION=%%v
)

:: Remove previous build folder
rmdir /S /Q package

:: Create dirs
md package
md package\bin
md package\lib
md package\properties
md package\frontend
md package\backend

:: Copy application files to package
xcopy /Q /E /Y bin package\bin
xcopy /Q /E /Y lib package\lib
xcopy /Q /E /Y properties package\properties
xcopy /Q /E /Y frontend package\frontend
xcopy /Q /E /Y backend package\backend
copy settings.json package\properties\settings.json

:: Build and sign package
..\ar8x_samples\tools\ar_signtool_cli\bin\ar_signtool_cli.exe package ar8x_hidreader-v%VERSION%-signed.zip

pause
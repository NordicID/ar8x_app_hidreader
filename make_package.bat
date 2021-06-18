rmdir /S /Q package

md package
md package\bin
md package\lib
md package\properties
md package\frontend
md package\backend

xcopy /Q /E /Y bin package\bin
xcopy /Q /E /Y lib package\lib
xcopy /Q /E /Y properties package\properties
xcopy /Q /E /Y frontend package\frontend
xcopy /Q /E /Y backend package\backend
copy settings.json package\properties\settings.json

..\ar8x_samples\tools\ar_signtool_cli\bin\ar_signtool_cli.exe package ar8x_hidreader-signed.zip

pause

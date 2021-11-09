del /f /q bin\hidreader\*
"%JAVA_HOME%\bin\javac" -target 1.6 -source 1.6 -cp ".;lib/*" -d "bin" src/hidreader/*.java
pause

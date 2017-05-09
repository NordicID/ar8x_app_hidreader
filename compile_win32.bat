del /f /q bin\hidreader\*
"%JAVA_HOME%\bin\javac" -cp ".;lib/*" -d "bin" src/hidreader/*.java
pause

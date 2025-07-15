@echo off
cd /d "%~dp0src"
echo [COMPILO SERVER...]
javac -cp "../lib/javafx-sdk-24.0.1/lib/*" *.java
echo [AVVIO SERVER]
java -cp ".;../lib/javafx-sdk-24.0.1/lib/*" ServerMain
pause

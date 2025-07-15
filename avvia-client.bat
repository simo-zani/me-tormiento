@echo off
cd /d "%~dp0src"

echo [COMPILAZIONE CLIENT TUTTI I FILE]
javac -cp "../lib/javafx-sdk-24.0.1/lib/*" *.java
IF ERRORLEVEL 1 (
    echo Errore in compilazione.
    pause
    exit /b 1
)

echo [AVVIO CLIENT]
java --module-path ../lib/javafx-sdk-24.0.1/lib --add-modules javafx.controls,javafx.graphics,javafx.fxml -cp . GameClient

pause
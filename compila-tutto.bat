@echo off
echo [COMPILAZIONE TUTTI I FILE JAVA IN SRC]
cd /d "%~dp0src"
javac -cp "../lib/javafx-sdk-24.0.1/lib/*" *.java
IF %ERRORLEVEL% EQU 0 (
    echo Compilazione completata con successo.
) ELSE (
    echo Errore nella compilazione!
)
pause

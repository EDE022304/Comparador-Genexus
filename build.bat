@echo off
if not exist bin mkdir bin
"C:\Program Files\AdoptOpenJDK\jdk-11.0.5.10-hotspot\bin\javac.exe" -encoding UTF-8 -d bin src\com\genexus\xpzdiff\*.java
if %ERRORLEVEL% EQU 0 (
    echo Compilacion Exitosa.
) else (
    echo Error de compilacion.
)

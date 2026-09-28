@echo off
if not exist bin\com\genexus\xpzdiff\Main.class (
    call build.bat
)
"C:\Program Files\AdoptOpenJDK\jdk-11.0.5.10-hotspot\bin\java.exe" -cp bin com.genexus.xpzdiff.Main %*

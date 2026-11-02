@echo off
rem Requiere JDK 11+ (javac y jar en el PATH)
if exist build rmdir /s /q build
mkdir build
javac --release 11 -encoding UTF-8 -d build src\modex\*.java || exit /b 1
echo Main-Class: modex.Main> build\manifest.txt
jar cfm ModEx.jar build\manifest.txt -C build modex
echo Listo: ModEx.jar

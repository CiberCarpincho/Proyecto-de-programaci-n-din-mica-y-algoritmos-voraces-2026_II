#!/bin/sh
# Requiere JDK 11+ (javac y jar en el PATH)
set -e
rm -rf build && mkdir build
javac --release 11 -encoding UTF-8 -d build src/modex/*.java
printf 'Main-Class: modex.Main\n' > build/manifest.txt
jar cfm ModEx.jar build/manifest.txt -C build modex
echo "Listo: ModEx.jar"

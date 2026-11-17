#!/usr/bin/env sh
set -eu
cd "$(dirname "$0")"
mkdir -p out
javac -encoding UTF-8 -d out src/main/java/com/pixelveil/*.java
exec java -cp out com.pixelveil.SteganographyApp

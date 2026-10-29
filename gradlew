#!/usr/bin/env sh
# Lightweight Gradle bootstrap for environments where the Gradle wrapper JAR is unavailable.
# The build itself runs with the official Gradle distribution from services.gradle.org.
set -eu
ROOT=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
VERSION="8.13"
GRADLE_HOME_BASE="${GRADLE_USER_HOME:-$HOME/.gradle}/x1-bootstrap"
GRADLE_HOME="$GRADLE_HOME_BASE/gradle-$VERSION"
GRADLE_BIN="$GRADLE_HOME/bin/gradle"
if [ ! -x "$GRADLE_BIN" ]; then
  mkdir -p "$GRADLE_HOME_BASE"
  ZIP="$GRADLE_HOME_BASE/gradle-$VERSION-bin.zip"
  if [ ! -f "$ZIP" ]; then
    if command -v curl >/dev/null 2>&1; then
      curl -fL --retry 3 --retry-delay 2 "https://services.gradle.org/distributions/gradle-$VERSION-bin.zip" -o "$ZIP"
    elif command -v wget >/dev/null 2>&1; then
      wget -O "$ZIP" "https://services.gradle.org/distributions/gradle-$VERSION-bin.zip"
    else
      echo 'Erro: instale curl ou wget para baixar o Gradle.' >&2
      exit 2
    fi
  fi
  command -v sha256sum >/dev/null 2>&1 || { echo 'Erro: sha256sum não encontrado; instale coreutils para validar o Gradle.' >&2; exit 2; }
  EXPECTED='20f1b1176237254a6fc204d8434196fa11a4cfb387567519c61556e8710aed78'
  ACTUAL=$(sha256sum "$ZIP" | awk '{print $1}')
  if [ "$ACTUAL" != "$EXPECTED" ]; then
    rm -f "$ZIP"
    echo 'Erro: checksum do Gradle inválido; download descartado.' >&2
    exit 2
  fi
  command -v unzip >/dev/null 2>&1 || { echo 'Erro: instale unzip para extrair o Gradle.' >&2; exit 2; }
  TMP="$GRADLE_HOME_BASE/extract"
  rm -rf "$TMP"
  mkdir -p "$TMP"
  unzip -q "$ZIP" -d "$TMP"
  rm -rf "$GRADLE_HOME"
  mv "$TMP/gradle-$VERSION" "$GRADLE_HOME"
  rm -rf "$TMP"
fi
exec "$GRADLE_BIN" -p "$ROOT" "$@"

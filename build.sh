#!/usr/bin/env bash
# Build AetherMind.jar and its Java dependencies with Maven.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")" && pwd)"
cd "$ROOT"

if [[ -z "${JAVA_HOME:-}" ]]; then
  for candidate in "$HOME"/.vscode/extensions/redhat.java-*/jre/* \
                  "$HOME"/.vscode-server/extensions/redhat.java-*/jre/*; do
    if [[ -x "$candidate/bin/javac" ]]; then
      JAVA_HOME="$candidate"
      break
    fi
  done
fi

if [[ -n "${JAVA_HOME:-}" ]]; then
  if [[ ! -x "$JAVA_HOME/bin/javac" ]]; then
    echo "AetherMind: JAVA_HOME must point to a JDK with javac."
    exit 1
  fi
  export PATH="$JAVA_HOME/bin:$PATH"
elif command -v javac >/dev/null 2>&1; then
  JAVAC_PATH="$(command -v javac)"
  JAVA_HOME="$(cd "$(dirname "$JAVAC_PATH")/.." && pwd)"
  export JAVA_HOME
  export PATH="$JAVA_HOME/bin:$PATH"
else
  echo "AetherMind: a JDK with javac is required to build."
  exit 1
fi

if ! command -v mvn >/dev/null 2>&1; then
  echo "AetherMind: Maven is required to build the Java Playwright dependency."
  echo "Install Maven and a JDK 11 or newer, then re-run ./build.sh."
  exit 1
fi

cp -f shared/knowledge.json web/knowledge.json

echo "[1/2] Building the desktop application and dependencies..."
mvn -q -DskipTests package

echo "[2/2] Copying the runnable JAR..."
cp target/AetherMind.jar AetherMind.jar

echo "Done: $ROOT/AetherMind.jar"
echo "Run with:  java -jar AetherMind.jar"
echo "       or:  ./run-desktop.sh"

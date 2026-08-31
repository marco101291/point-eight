#!/usr/bin/env bash
# Corre los tests de java-system dentro de un contenedor Gradle.
#
# No hace falta JDK ni Gradle local (DEC-002). Se pasa --user para que los artefactos
# de build queden con el dueño del host y no como root, y se cachea GRADLE_USER_HOME
# en un volumen nombrado para que la segunda corrida no vuelva a bajar dependencias.
set -euo pipefail

cd "$(dirname "$0")/.."

docker run --rm \
  --user "$(id -u):$(id -g)" \
  -e GRADLE_USER_HOME=/home/gradle/.gradle \
  -v pointeight-gradle-cache:/home/gradle/.gradle \
  -v "$PWD/java-system":/app \
  -w /app \
  gradle:8.10-jdk21 \
  gradle "${@:-test}" --no-daemon --console=plain

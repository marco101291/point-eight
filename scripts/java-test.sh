#!/usr/bin/env bash
# Runs java-system's tests inside a Gradle container.
#
# No local JDK or Gradle needed (DEC-002). --user keeps build artifacts owned by the host user
# instead of root; GRADLE_USER_HOME is cached in a named volume so a second run doesn't
# re-download dependencies.
#
# The Docker socket is mounted so Testcontainers-backed tests (see com.pointeight.support.
# PostgresIntegrationTest) can start a real Postgres as a sibling container on the host's own
# daemon — this is the standard "Docker-outside-of-Docker" pattern, not Docker-in-Docker; no
# nested daemon involved. --group-add grants the non-root --user supplementary access to the
# socket's actual group on this host, since the plain gradle image has no matching group by name.
set -euo pipefail

cd "$(dirname "$0")/.."

docker run --rm \
  --user "$(id -u):$(id -g)" \
  --group-add "$(stat -c '%g' /var/run/docker.sock)" \
  -v /var/run/docker.sock:/var/run/docker.sock \
  -e GRADLE_USER_HOME=/home/gradle/.gradle \
  -v pointeight-gradle-cache:/home/gradle/.gradle \
  -v "$PWD/java-system":/app \
  -w /app \
  gradle:8.10-jdk21 \
  gradle "${@:-test}" --no-daemon --console=plain

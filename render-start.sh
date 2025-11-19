#!/usr/bin/env bash
set -euo pipefail
export JAVA_HOME="/opt/render/project/.jdk"
export PATH="$JAVA_HOME/bin:$PATH"
exec java -Dquarkus.http.port

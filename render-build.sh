#!/usr/bin/env bash
set -e

JDK_DIR=/opt/render/project/.jdk
mkdir -p "$JDK_DIR"
curl -fsSL https://api.adoptium.net/v3/binary/latest/17/ga/linux/x64/jdk/hotspot/normal/eclipse \
  | tar -xz --strip-components=1 -C "$JDK_DIR"

export JAVA_HOME="$JDK_DIR"
export PATH="$JAVA_HOME/bin:$PATH"

CACHE=/opt/render/project/.cache
MAVEN_DIR="$CACHE/apache-maven-3.9.8"
if [ ! -x "$MAVEN_DIR/bin/mvn" ]; then
  mkdir -p "$CACHE"
  curl -fsSL https://archive.apache.org/dist/maven/maven-3/3.9.8/binaries/apache-maven-3.9.8-bin.tar.gz \
    | tar -xz -C "$CACHE"
fi

"$MAVEN_DIR/bin/mvn" -DskipTests -Dquarkus.package.type=uber-jar package

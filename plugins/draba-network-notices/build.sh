#!/usr/bin/env bash
set -Eeuo pipefail

project_root="$(cd "$(dirname "$0")" && pwd)"
velocity_jar="${VELOCITY_JAR:-}"
if [[ -z "$velocity_jar" || ! -f "$velocity_jar" ]]; then
  printf '%s\n' 'Set VELOCITY_JAR to an official Velocity proxy jar.' >&2
  exit 2
fi
classes="$project_root/build/classes"
test_classes="$project_root/build/test-classes"
output="$project_root/build/libs/draba-network-notices-1.3.0.jar"

rm -rf "$project_root/build"
mkdir -p "$classes" "$test_classes" "$(dirname "$output")"
javac --release 21 -cp "$velocity_jar" -d "$classes" \
  "$project_root/src/main/java/xyz/draba/network/DrabaNetworkNotices.java"
javac --release 21 -cp "$velocity_jar:$classes" -d "$test_classes" \
  "$project_root/src/test/java/xyz/draba/network/DrabaNetworkNoticesTest.java"
java -ea -cp "$velocity_jar:$classes:$test_classes" xyz.draba.network.DrabaNetworkNoticesTest
jar --create --file "$output" -C "$classes" .
jar --update --file "$output" -C "$project_root/src/main/resources" velocity-plugin.json
printf '%s\n' "$output"

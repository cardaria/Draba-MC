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
output="$project_root/build/libs/RelayLine-1.4.2-draba.1.jar"

rm -rf "$project_root/build"
mkdir -p "$classes" "$test_classes" "$(dirname "$output")"
javac --release 21 -cp "$velocity_jar" -d "$classes" \
  "$project_root/src/main/java/net/groundplayz/relayline/RelayLinePlugin.java" \
  "$project_root/src/main/java/net/groundplayz/relayline/RelayLineMessagePolicy.java"
javac --release 21 -cp "$classes" -d "$test_classes" \
  "$project_root/src/test/java/net/groundplayz/relayline/RelayLineMessagePolicyTest.java"
java -ea -cp "$classes:$test_classes" net.groundplayz.relayline.RelayLineMessagePolicyTest
jar --create --file "$output" -C "$classes" .
jar --update --file "$output" -C "$project_root/src/main/resources" velocity-plugin.json
printf '%s\n' "$output"

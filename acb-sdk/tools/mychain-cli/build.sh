#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
: "${MYCHAIN_PLUGIN_JAR:?Set MYCHAIN_PLUGIN_JAR to a reviewed Mychain020 plugin with callContract(Identity,...)}"
java_bin="${JAVA_HOME:+${JAVA_HOME}/bin/}java"
javac_bin="${JAVA_HOME:+${JAVA_HOME}/bin/}javac"
mkdir -p build
"$javac_bin" -proc:none -source 8 -target 8 -cp "$MYCHAIN_PLUGIN_JAR" -d build ./*.java
"$java_bin" -cp "build:$MYCHAIN_PLUGIN_JAR" ReceiptStatusTest

#!/usr/bin/env bash
# ZIP_BUG Zippy Cut — plan-builder verification harness (NOT app code).
#
# 1) Compiles the pure plan code (ZippyCutPlan + SubtitleExporter) with
#    kotlinc and runs the JUnit suite (ZippyCutPlanTest).
# 2) Compiles and runs RenderSmokeMain, which executes the plan builder's
#    real ffmpeg argument lists against synthetic media with the system
#    ffmpeg and verifies outputs with ffprobe (dimensions, audio stream,
#    duration) — the executable proof for the portrait-canvas and
#    no-audio-clip fixes.
#
# Prerequisites: JDK, kotlinc, ffmpeg/ffprobe, curl (to fetch the org.json
# jar once). JUnit jars are located via $JUNIT_JAR/$HAMCREST_JAR or the
# Muse sandbox defaults below.
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
WORK="${RENDER_SMOKE_WORK:-/tmp/zipbug-render-smoke}"
mkdir -p "$WORK"
KOTLINC="${KOTLINC:-kotlinc}"
SCRATCH="$HOME/workspace/.scratch"
JUNIT_JAR="${JUNIT_JAR:-$SCRATCH/junit-4.13.2.jar}"
HAMCREST_JAR="${HAMCREST_JAR:-$SCRATCH/hamcrest-core-1.3.jar}"
JSON_JAR="$WORK/org-json.jar"
FONT="${RENDER_FONT:-/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf}"

[ -f "$JSON_JAR" ] || curl -sL -o "$JSON_JAR" \
  https://repo1.maven.org/maven2/org/json/json/20240303/json-20240303.jar

MAIN_SRC="$REPO_ROOT/app/src/main/java/com/zipbug/base/zippy/ZippyCutPlan.kt"
EXPORTER_SRC="$REPO_ROOT/app/src/main/java/com/zipbug/base/recap/SubtitleExporter.kt"
STUB_SRC="$REPO_ROOT/scripts/render-smoke/stub/RecapScriptEntity.kt"
TEST_SRC="$REPO_ROOT/app/src/test/java/com/zipbug/base/zippy/ZippyCutPlanTest.kt"
SMOKE_SRC="$REPO_ROOT/scripts/render-smoke/RenderSmokeMain.kt"

echo "== compile main =="
"$KOTLINC" "$MAIN_SRC" "$EXPORTER_SRC" "$STUB_SRC" -cp "$JSON_JAR" -d "$WORK/main"
echo "== compile tests =="
"$KOTLINC" "$TEST_SRC" "$SMOKE_SRC" \
  -cp "$JSON_JAR:$JUNIT_JAR:$HAMCREST_JAR:$WORK/main" -d "$WORK/test"

STDLIB="$(dirname "$(readlink -f "$(command -v "$KOTLINC")")")/../lib/kotlin-stdlib.jar"

echo "== JUnit =="
java -cp "$WORK/main:$WORK/test:$JSON_JAR:$JUNIT_JAR:$HAMCREST_JAR:$STDLIB" \
  org.junit.runner.JUnitCore com.zipbug.base.zippy.ZippyCutPlanTest

echo "== ffmpeg render smoke =="
java -cp "$WORK/main:$WORK/test:$JSON_JAR:$STDLIB" \
  com.zipbug.base.zippy.RenderSmokeMain "$WORK/media" "$FONT"

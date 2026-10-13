#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")"

for command_name in java javac ant xvfb-run timeout unzip jar sha256sum; do
    command -v "${command_name}" >/dev/null || {
        printf 'missing command: %s\n' "${command_name}" >&2
        exit 2
    }
done

# the CSV contract is shared with AutoFillSuite: the same samples and the same
# document must sit in both repositories, byte for byte
(
    cd test/fixtures
    sha256sum --check --strict --quiet CHECKSUMS
) || {
    printf 'contract files changed: test/fixtures/CHECKSUMS does not match\n' >&2
    exit 1
}

rm -rf out build dist
mkdir -p out

# warnings are errors; the excluded categories are toolchain noise, not code
# (see .github/workflows/lint.yml)
javac --release 8 -Xlint:all,-options,-serial,-this-escape -Werror \
    -d out $(find src test -name '*.java')
(
    cd src
    find . -name '*.md' -exec cp --parents {} ../out/ ';'
)

xvfb-run -a bash -eu -o pipefail <<'TESTS'
java -cp out app.core.ScanGuardTest
java -cp out app.core.JournalTest
java -cp out app.core.CrashTest
java -cp out app.ui.GlyphSafetyTest
java -cp out app.ui.IconRenderTest
java -cp out app.ui.SpinnerCommitTest
java -cp out app.ui.ScanTabSupportTest
java -cp out app.ui.TextFitTest
java -cp out app.ui.StartupSmokeTest
java -cp out app.ui.StartupSmokeTest --saved
TESTS

ant -q clean jar

test -s dist/ScanLedger.jar
unzip -p dist/ScanLedger.jar META-INF/MANIFEST.MF \
    | tr -d '\r' \
    | grep -Fx 'Main-Class: app.Main'
jar tf dist/ScanLedger.jar | grep -Fx 'app/Main.class'

# the packaged app starts and stays up, in a throwaway home
xvfb-run -a bash -eu -c '
home=$(mktemp -d)
java -Duser.home="$home" -jar dist/ScanLedger.jar >"$home/package-startup.log" 2>&1 &
pid=$!
sleep 5
kill -0 "$pid"
kill -TERM "$pid"
for attempt in 1 2 3 4 5; do
    kill -0 "$pid" 2>/dev/null || exit 0
    sleep 1
done
kill -KILL "$pid"
wait "$pid" || true
'

printf 'ScanLedger verification: PASS\n'
sha256sum dist/ScanLedger.jar

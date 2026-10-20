#!/usr/bin/env bash
# Decompile a Java/J2ME JAR into readable (approximate) Java sources.
set -euo pipefail

if (( $# != 2 )); then
  echo "Usage: bash scripts/decompile-jar.sh input/game.jar decompiled/game" >&2
  exit 64
fi

input="$1"
output="$2"

if [[ ! -f "$input" ]]; then
  echo "ERROR: JAR file not found: $input" >&2
  exit 1
fi
if ! unzip -tqq "$input"; then
  echo "ERROR: Invalid or corrupted JAR/ZIP: $input" >&2
  exit 1
fi

class_count="$(unzip -Z1 "$input" | awk '/\.class$/ { n++ } END { print n+0 }')"
if (( class_count == 0 )); then
  echo "ERROR: No .class bytecode in $input; assets alone cannot be decompiled." >&2
  exit 2
fi

mkdir -p "$output/src" "$output/metadata"
scratch="$(mktemp -d)"
trap 'rm -rf "$scratch"' EXIT

cfr="$scratch/cfr-0.152.jar"
curl --fail --location --silent --show-error --retry 3 \
  "https://repo.maven.apache.org/maven2/org/benf/cfr/0.152/cfr-0.152.jar" \
  --output "$cfr"

# Check against the checksum published on the official CFR download page.
printf '%s  %s\n' "8a85ada8cec494121246805a5562b82b" "$cfr" | md5sum --check --status

echo "Decompiling $class_count classes from: $input"
java -jar "$cfr" "$input" --outputdir "$output/src" --silent true

java_count="$(find "$output/src" -type f -name '*.java' | wc -l | tr -d ' ')"
if (( java_count == 0 )); then
  echo "ERROR: CFR did not emit any .java files." >&2
  exit 3
fi

unzip -p "$input" META-INF/MANIFEST.MF > "$output/metadata/MANIFEST.MF" 2>/dev/null || true
unzip -Z1 "$input" > "$output/metadata/jar-entries.txt"

cat > "$output/metadata/REPORT.txt" <<EOF
Input JAR: $(basename "$input")
Class bytecode entries: $class_count
Recovered Java files: $java_count
Decompiler: CFR 0.152
Note: Decompilation approximates original source. Comments, local names and
some Java ME constructs may not be recovered. Building/running the client
requires Java ME (MIDP/CLDC) libraries and may require manual repairs.
EOF

if unzip -Z1 "$input" | grep -qx 'main/GameMidlet.class'; then
  echo "Found main/GameMidlet.class (expected NRO-Offline entry point)." | tee -a "$output/metadata/REPORT.txt"
else
  echo "main/GameMidlet.class not found; inspect the manifest for the entry point." | tee -a "$output/metadata/REPORT.txt"
fi

echo "SUCCESS: recovered $java_count .java files under $output/src"

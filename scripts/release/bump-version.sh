#!/usr/bin/env bash
# Bump the project version across the main reactor and every example.
#
# Hybrid strategy:
#   - main reactor : `versions:set`
#       (root + searchable-bom + 6 submodules; parent/child <version>
#        elements stay in sync; comments and SBOM samples are skipped
#        because Maven understands POM structure)
#   - examples     : literal sed on two well-known elements per file
#       (<version>OLD</version> for the example artifact itself, and
#        <searchable.version>OLD</searchable.version> for the BOM
#        coordinate). Maven cannot be used here because each example is
#        an independent reactor that imports `searchable-bom:OLD` — if
#        that BOM is not yet in the local repo at the time `versions:set`
#        reads the POM, resolution fails and aborts the bump halfway.
#        Each example pom has exactly these two literal version sites,
#        so direct text replacement is safe and idempotent.
#
# Out of scope (intentional):
#   - docs/devel/work/poc/**/pom.xml: POC sandboxes, not released
#   - examples/ai-ollama, examples/search-ui: no pom.xml
#   - git commit / git tag: owned by the human, see
#     docs/devel/operation/release.md
#
# Usage:
#   scripts/release/bump-version.sh <new-version>
#
# Examples:
#   scripts/release/bump-version.sh 1.0.1            # patch release
#   scripts/release/bump-version.sh 1.1.0-SNAPSHOT   # next dev cycle
set -euo pipefail

if [ $# -ne 1 ]; then
  cat >&2 <<'EOF'
Usage: scripts/release/bump-version.sh <new-version>

Examples:
  scripts/release/bump-version.sh 1.0.1            # patch release
  scripts/release/bump-version.sh 1.1.0-SNAPSHOT   # next dev cycle
EOF
  exit 1
fi

NEW="$1"
# Strict semver with optional -SNAPSHOT or other qualifier.
if ! [[ "$NEW" =~ ^[0-9]+\.[0-9]+\.[0-9]+(-[A-Za-z0-9.-]+)?$ ]]; then
  echo "Error: '$NEW' is not a valid semver-style version" >&2
  exit 1
fi

REPO_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$REPO_ROOT"

ROOT_POM="$REPO_ROOT/pom.xml"
if [ ! -f "$ROOT_POM" ]; then
  echo "Error: root pom.xml not found at $ROOT_POM" >&2
  exit 1
fi

MVNW="$REPO_ROOT/mvnw"
if [ ! -x "$MVNW" ]; then
  echo "Error: ./mvnw not executable at $MVNW" >&2
  exit 1
fi

# Current root version, for the dry-run banner.
CURRENT=$(grep -m1 -oE '<version>[^<]+</version>' "$ROOT_POM" \
  | sed -E 's|</?version>||g')

echo "Current root version : $CURRENT"
echo "Target version       : $NEW"
echo ""

if [ "$CURRENT" = "$NEW" ]; then
  echo "Already at $NEW -- nothing to do."
  exit 0
fi

# GNU sed accepts `-i`; BSD/macOS sed requires `-i ''` (empty backup ext).
SED_INPLACE=("-i")
if ! sed --version >/dev/null 2>&1; then
  SED_INPLACE=("-i" "")
fi

MVN_OPTS=(-B -q -DgenerateBackupPoms=false)

echo "[1/2] Updating main reactor (searchable-parent + BOM + 6 submodules)"
"$MVNW" "${MVN_OPTS[@]}" versions:set -DnewVersion="$NEW"

EXAMPLE_POMS=(
  examples/api/pom.xml
  examples/mcp/pom.xml
  examples/webapp/pom.xml
  examples/plugin-datasource-s3/pom.xml
)

echo ""
echo "[2/2] Updating example reactors (own <version> + <searchable.version>)"
for pom in "${EXAMPLE_POMS[@]}"; do
  if [ ! -f "$REPO_ROOT/$pom" ]; then
    echo "  skip (missing): $pom"
    continue
  fi
  # Each example has exactly:
  #   <version>OLD</version>           ← the example artifact itself
  #   <searchable.version>OLD</searchable.version>  ← the BOM coordinate
  # Both are flat one-line elements, so literal replacement is safe.
  if grep -qE "<(version|searchable\.version)>${CURRENT}</(version|searchable\.version)>" "$pom"; then
    sed "${SED_INPLACE[@]}" \
      -e "s|<version>${CURRENT}</version>|<version>${NEW}</version>|g" \
      -e "s|<searchable.version>${CURRENT}</searchable.version>|<searchable.version>${NEW}</searchable.version>|g" \
      "$pom"
    echo "  - $pom"
  else
    echo "  skip (no $CURRENT found): $pom"
  fi
done

echo ""
echo "Done. Review with: git diff -- '*pom.xml'"
echo "Next:  ./mvnw -B clean install -DskipTests   # verify reactor"

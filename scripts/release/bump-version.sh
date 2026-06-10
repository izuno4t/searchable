#!/usr/bin/env bash
# Bump the project version across pom.xml files and docs that quote it.
#
# Steps:
#   [1/3] main reactor : `versions:set` walks searchable-parent +
#         searchable-bom + 6 submodules, keeping parent/child <version>
#         in sync.
#   [2/3] examples     : each example is an independent reactor that
#         imports searchable-bom:OLD — `versions:set` aborts there
#         because OLD is not yet in the local repo. Each example pom
#         has exactly <version>OLD</version> and
#         <searchable.version>OLD</searchable.version>, so literal sed
#         is safe and idempotent.
#   [3/3] docs         : sed each file in DOC_FILES below that contains
#         OLD. Add a file to the list when a new doc quotes the
#         version; remove from POM/example coverage if a section moves.
#
# Out of scope:
#   - docs/devel/work/**, .github/workflows/release.yml: past-tense or
#     example references, must not shift on bump
#   - git commit / git tag: see docs/devel/operation/release.md
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

# `-DprocessAllModules=true` is required for searchable-bom to be
# updated. Without it, versions:set silently skips the BOM because it
# has no <parent> ref linking back to searchable-parent (intentional,
# to avoid the parent → import-BOM → parent cycle), and the default
# traversal only walks parent-linked sub-modules.
echo "[1/2] Updating main reactor (searchable-parent + BOM + 6 submodules)"
"$MVNW" "${MVN_OPTS[@]}" versions:set \
  -DnewVersion="$NEW" \
  -DprocessAllModules=true

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
echo "[3/3] Updating dev-version-tracking refs in *.md (JAR filename + POM <version>)"
# Only the two patterns that legitimately track the working dev version:
#   - `-CURRENT.jar` : output of `mvn package` against the current pom
#   - `<version>CURRENT</version>` : Maven coordinate snippets in docs
# Badges (`Version-X-...`), status text (`**X (stable)**`), and similar
# prose live in a curated checklist in docs/devel/operation/release.md §4
# because they need contextual judgement (badge points at last released,
# status wording differs by stage). Files under docs/devel/work/ are
# history-or-future docs and are never auto-bumped.
EXCLUDE_REGEX='^(\./)?(docs/devel/work/|.*/archive/)'
PATTERNS=(
  "<version>${CURRENT}</version>:<version>${NEW}</version>"
  "-${CURRENT}.jar:-${NEW}.jar"
)
declare -A SEEN
for spec in "${PATTERNS[@]}"; do
  old="${spec%%:*}"
  new="${spec#*:}"
  while IFS= read -r doc; do
    [[ "$doc" =~ $EXCLUDE_REGEX ]] && continue
    sed "${SED_INPLACE[@]}" "s|${old}|${new}|g" "$doc"
    if [ -z "${SEEN[$doc]+x}" ]; then
      echo "  - ${doc#./}"
      SEEN[$doc]=1
    fi
  done < <(grep -rlF "$old" . \
    --include='*.md' \
    --exclude-dir=.git \
    --exclude-dir=target \
    --exclude-dir=node_modules)
done

echo ""
echo "=== Checking that no auto-bump pattern leaks for $CURRENT ==="
for pat in "<version>${CURRENT}</version>" "-${CURRENT}.jar"; do
  LEAKS=$(grep -rlF "$pat" "$REPO_ROOT" \
    --include='*.xml' --include='*.md' \
    --exclude-dir=.git \
    --exclude-dir=target \
    --exclude-dir=node_modules \
    | sed "s|^$REPO_ROOT/||" \
    | { grep -vE "$EXCLUDE_REGEX" || true; }) || true
  if [ -n "$LEAKS" ]; then
    echo "ERROR: $pat still in:" >&2
    printf '  %s\n' $LEAKS >&2
    exit 1
  fi
done
echo "OK: no $CURRENT auto-bump pattern remains outside history paths."

echo ""
echo "=== Verifying reactor integrity (./mvnw -B -q clean install -DskipTests) ==="
"$MVNW" -B -q clean install -DskipTests

echo ""
echo "Done. Version is now $NEW; no $CURRENT leftovers; reactor builds clean."

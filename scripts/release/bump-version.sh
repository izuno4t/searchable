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

# ANSI colour helpers (disabled if stdout is not a TTY). Defined early
# so the pre-check below can use them.
if [ -t 1 ]; then
  R=$'\033[1;31m'  # bold red
  Y=$'\033[1;33m'  # bold yellow
  G=$'\033[1;32m'  # bold green
  N=$'\033[0m'     # reset
else
  R= Y= G= N=
fi
banner() {
  local colour="$1" emoji="$2" title="$3"
  local bar='━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━'
  printf '\n%s%s%s\n' "$colour" "$bar" "$N"
  printf '%s %s  %s%s\n' "$colour" "$emoji" "$title" "$N"
  printf '%s%s%s\n\n' "$colour" "$bar" "$N"
}

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

# Pre-check: any version-shaped literal in the excluded paths means
# someone accidentally hard-coded a project version into a doc that the
# bump script intentionally never touches. Fail BEFORE any pom or doc
# is modified, so the working tree stays clean and the human can fix
# the offending file before retrying.
echo ""
echo "Pre-check: forbidding auto-bump patterns in excluded paths"
GUARD_PATTERN='(<version>|-)[0-9]+\.[0-9]+\.[0-9]+([-.A-Za-z0-9]*)?(</version>|\.jar)'
GUARD_HITS=$(grep -rnE \
  --include='*.md' \
  --exclude-dir=.git \
  --exclude-dir=target \
  --exclude-dir=node_modules \
  --exclude-dir=archive \
  -e "$GUARD_PATTERN" \
  CLAUDE.md docs/devel/ 2>/dev/null || true)
if [ -n "$GUARD_HITS" ]; then
  banner "$R" "🚨" "FORBIDDEN: auto-bump pattern in dev-only doc"
  printf '%s    %s%s\n' "$R" "$GUARD_HITS" "$N"
  printf '\n%sCLAUDE.md と docs/devel/ は bump-version.sh の対象外です。%s\n' "$R" "$N"
  printf '%sproject-version literal を削除するか、user-facing な場所に移してください。%s\n' "$R" "$N"
  banner "$R" "❌" "BUMP ABORTED (no files modified)"
  exit 1
fi
printf '%s ✅  Pre-check OK%s — no project-version literals in excluded paths.\n' "$G" "$N"

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
# Scope: user-facing docs only (README.md, docs/public/**, examples/**,
# searchable-cli/README.md). Anything under docs/devel/ is internal
# developer documentation that never tracks the working dev version.
#
# Auto-bump targets only two unambiguous patterns:
#   - `-CURRENT.jar` : output of `mvn package` against the current pom
#   - `<version>CURRENT</version>` : Maven coordinate snippets in docs
# Badges, status text, OpenAPI/JSON version fields, and similar prose
# need contextual judgement and are NOT auto-bumped. They're listed in
# the Advisory section below for manual review.
EXCLUDE_REGEX='^(\./)?(docs/devel/|CLAUDE\.md(:|$)|.*/archive/)'

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
  done < <(grep -rlF \
    --include='*.md' \
    --exclude-dir=.git \
    --exclude-dir=target \
    --exclude-dir=node_modules \
    -e "$old" .)
done

echo ""
echo "=== Checking that no auto-bump pattern leaks for $CURRENT ==="
LEAK_FOUND=0
for pat in "<version>${CURRENT}</version>" "-${CURRENT}.jar"; do
  LEAKS=$(grep -rlF \
    --include='*.xml' --include='*.md' \
    --exclude-dir=.git \
    --exclude-dir=target \
    --exclude-dir=node_modules \
    -e "$pat" "$REPO_ROOT" \
    | sed "s|^$REPO_ROOT/||" \
    | { grep -vE "$EXCLUDE_REGEX" || true; }) || true
  if [ -n "$LEAKS" ]; then
    banner "$R" "🚨" "LEAK: pattern '$pat' still present after bump"
    printf '%s    %s%s\n' "$R" "$LEAKS" "$N"
    LEAK_FOUND=1
  fi
done
if [ "$LEAK_FOUND" = "1" ]; then
  banner "$R" "❌" "BUMP ABORTED — investigate the leaked references above"
  exit 1
fi
printf '%s ✅  OK%s — no %s auto-bump pattern remains outside history paths.\n' "$G" "$N" "$CURRENT"

# Non-fatal advisory: other plain-literal CURRENT references that are
# NOT part of the auto-bump pattern set (badges, OpenAPI version,
# server info, etc.). They might or might not need a manual update.
echo ""
echo "=== Advisory: other $CURRENT literals (NOT auto-bumped, manual review) ==="
OTHER=$(grep -rnF \
  --include='*.md' --include='*.xml' --include='*.json' \
  --include='*.yaml' --include='*.yml' --include='*.properties' \
  --exclude-dir=.git \
  --exclude-dir=target \
  --exclude-dir=node_modules \
  -e "$CURRENT" "$REPO_ROOT" \
  | grep -vE "<version>${CURRENT}</version>|-${CURRENT}\\.jar" \
  | sed "s|^$REPO_ROOT/||" \
  | { grep -vE "$EXCLUDE_REGEX" || true; } || true)
if [ -n "$OTHER" ]; then
  banner "$Y" "⚠️" "MANUAL REVIEW: $CURRENT literals NOT covered by auto-bump"
  printf '%s%s%s\n' "$Y" "$OTHER" "$N"
  printf '\n%s💡 Check each against the release.md §4 checklist and decide whether to bump.%s\n' "$Y" "$N"
else
  printf '%s ✅  OK%s — no other %s literals to review.\n' "$G" "$N" "$CURRENT"
fi

echo ""
echo "=== Verifying reactor integrity (./mvnw -B -q clean install -DskipTests) ==="
"$MVNW" -B -q clean install -DskipTests

echo ""
echo "Done. Version is now $NEW; no $CURRENT leftovers; reactor builds clean."

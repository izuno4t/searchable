#!/usr/bin/env bash
# Bump the project version across every pom.xml in the repository.
#
# Reads the current version from the root pom and replaces every literal
# <version>${CURRENT}</version> occurrence with the requested target.
# Project version, <parent> back-references, and the BOM self-reference
# all match because they share the same literal version string. Inter-
# module dependencies use ${project.version} and are auto-tracked.
#
# After running, review with `git diff` and commit yourself.
#
# Usage:
#   scripts/bump-version.sh <new-version>
#
# Examples:
#   scripts/bump-version.sh 1.0.0            # SNAPSHOT → release
#   scripts/bump-version.sh 1.0.1-SNAPSHOT   # release  → next snapshot
set -euo pipefail

if [ $# -ne 1 ]; then
  cat >&2 <<'EOF'
Usage: scripts/bump-version.sh <new-version>

Examples:
  scripts/bump-version.sh 1.0.0            # SNAPSHOT → release
  scripts/bump-version.sh 1.0.1-SNAPSHOT   # release  → next snapshot
EOF
  exit 1
fi

NEW="$1"
# Strict semver with optional -SNAPSHOT or other qualifier.
if ! [[ "$NEW" =~ ^[0-9]+\.[0-9]+\.[0-9]+(-[A-Za-z0-9.-]+)?$ ]]; then
  echo "Error: '$NEW' is not a valid semver-style version" >&2
  exit 1
fi

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
ROOT_POM="$REPO_ROOT/pom.xml"
if [ ! -f "$ROOT_POM" ]; then
  echo "Error: root pom.xml not found at $ROOT_POM" >&2
  exit 1
fi

# Root pom's first <version> element is the project version.
CURRENT=$(grep -m1 -oE '<version>[^<]+</version>' "$ROOT_POM" \
  | sed -E 's|</?version>||g')

echo "Current version : $CURRENT"
echo "Target version  : $NEW"

if [ "$CURRENT" = "$NEW" ]; then
  echo "Already at $NEW -- nothing to do."
  exit 0
fi

# GNU sed accepts `-i`; BSD/macOS sed requires `-i ''` (empty backup ext).
SED_INPLACE=("-i")
if ! sed --version >/dev/null 2>&1; then
  SED_INPLACE=("-i" "")
fi

CHANGED=0
while IFS= read -r pom; do
  if grep -q "<version>${CURRENT}</version>" "$pom"; then
    sed "${SED_INPLACE[@]}" \
      "s|<version>${CURRENT}</version>|<version>${NEW}</version>|g" "$pom"
    echo "  updated ${pom#"$REPO_ROOT/"}"
    CHANGED=$((CHANGED + 1))
  fi
done < <(find "$REPO_ROOT" -name "pom.xml" \
  -not -path "*/target/*" -not -path "*/node_modules/*")

echo ""
echo "Done. $CHANGED file(s) updated."
echo "Review with: git diff"

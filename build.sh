#!/usr/bin/env bash
# Build the multi-module library reactor first (installed into the local
# Maven repository so examples can resolve `searchable-core` etc.), then
# build each project under examples/ in turn.
#
# Usage:
#   ./build.sh                 # full build with tests
#   ./build.sh --skip-tests    # skip tests
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT_DIR"

SKIP_TESTS=false
for arg in "$@"; do
    case "$arg" in
        --skip-tests) SKIP_TESTS=true ;;
        -h|--help)
            sed -n '2,8p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'
            exit 0
            ;;
        *)
            echo "Unknown argument: $arg" >&2
            echo "Run with --help for usage." >&2
            exit 2
            ;;
    esac
done

MVN_ARGS=(-B)
if $SKIP_TESTS; then
    MVN_ARGS+=(-DskipTests)
fi

# Use ANSI bold/green only when stdout is a TTY.
if [[ -t 1 ]]; then
    bold=$'\033[1m'; green=$'\033[32m'; reset=$'\033[0m'
else
    bold=""; green=""; reset=""
fi

banner() {
    printf '\n%s==> %s%s\n' "${green}${bold}" "$1" "${reset}"
}

banner "Libraries (root reactor): clean install"
./mvnw "${MVN_ARGS[@]}" clean install

for pom in examples/*/pom.xml; do
    module="$(dirname "$pom")"
    banner "Example ${module}: clean package"
    ./mvnw -f "$pom" "${MVN_ARGS[@]}" clean package
done

banner "All builds succeeded."

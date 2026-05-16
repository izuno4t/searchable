#!/usr/bin/env bash
# Verification source: bundled demo data.
#
# Stages the files under docker/demo-data/ into ${VERIFY_STAGING_DIR}
# and exports the queries that step 5 (full-text) and step 6 (vector)
# should use against the resulting index.
#
# This script is intended to be `source`d by scripts/verify/run.sh
# (which sets VERIFY_STAGING_DIR before sourcing).

set -euo pipefail

# Allow standalone execution: positional arg overrides VERIFY_STAGING_DIR.
if [[ $# -ge 1 && "${BASH_SOURCE[0]}" == "${0}" ]]; then
    VERIFY_STAGING_DIR="$1"
fi
: "${VERIFY_STAGING_DIR:?VERIFY_STAGING_DIR must be set (or pass a target dir as arg 1)}"

_src_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)/docker/demo-data"

if [[ ! -d "${_src_dir}" ]]; then
    echo "bundled source: missing ${_src_dir}" >&2
    exit 1
fi

mkdir -p "${VERIFY_STAGING_DIR}"

# Wipe any prior staging files (keep the directory itself).
find "${VERIFY_STAGING_DIR}" -maxdepth 1 -type f \
    \( -name '*.md' -o -name '*.txt' \) -delete

# Copy the demo files verbatim. demo-data/ ships .md only at present.
shopt -s nullglob
for f in "${_src_dir}"/*.md "${_src_dir}"/*.txt; do
    cp "$f" "${VERIFY_STAGING_DIR}/"
done
shopt -u nullglob

# Queries paired to known content of docker/demo-data/:
# - 02-vector-search.md contains literal phrases like "ベクトル検索".
# - The same file describes semantic / similarity-based retrieval,
#   so "意味的な類似性" should resolve via the embedding even if the
#   exact phrase does not appear.
export VERIFY_QUERY_FULLTEXT="ベクトル検索"
export VERIFY_QUERY_VECTOR="意味的な類似性"

# When run standalone, the env-var exports vanish with the process.
# Emit the file list and queries so a caller can pick them up.
if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then
    echo "staged to: ${VERIFY_STAGING_DIR}"
    find "${VERIFY_STAGING_DIR}" -maxdepth 1 -type f \
        \( -name '*.md' -o -name '*.txt' \) -print
    echo "VERIFY_QUERY_FULLTEXT=${VERIFY_QUERY_FULLTEXT}"
    echo "VERIFY_QUERY_VECTOR=${VERIFY_QUERY_VECTOR}"
fi

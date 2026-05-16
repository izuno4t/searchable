#!/usr/bin/env bash
# Verification source: Japanese Wikipedia (MediaWiki API).
#
# Downloads a small fixed set of Japanese articles as plain text via
# the public MediaWiki extracts API and stages them under
# ${VERIFY_STAGING_DIR}. Exports the queries that step 5 (full-text)
# and step 6 (vector) should use against the resulting index.
#
# This script is intended to be `source`d by scripts/verify/run.sh
# (which sets VERIFY_STAGING_DIR before sourcing).
#
# Network access is required. The MediaWiki API is rate-limited and
# can refuse without a descriptive User-Agent; we send one.

set -euo pipefail

# Allow standalone execution: positional arg overrides VERIFY_STAGING_DIR.
if [[ $# -ge 1 && "${BASH_SOURCE[0]}" == "${0}" ]]; then
    VERIFY_STAGING_DIR="$1"
fi
: "${VERIFY_STAGING_DIR:?VERIFY_STAGING_DIR must be set (or pass a target dir as arg 1)}"

command -v jq >/dev/null 2>&1 || { echo "internet source: jq required" >&2; exit 1; }
command -v curl >/dev/null 2>&1 || { echo "internet source: curl required" >&2; exit 1; }

mkdir -p "${VERIFY_STAGING_DIR}"

# Wipe any prior staging files (keep the directory itself).
find "${VERIFY_STAGING_DIR}" -maxdepth 1 -type f \
    \( -name '*.md' -o -name '*.txt' \) -delete

_ua="searchable-verify/1.0 (+https://github.com/izuno4t/searchable)"
_api="https://ja.wikipedia.org/w/api.php"
# Cap each article so a single doc cannot blow up the request. The
# leading paragraphs are sufficient to assert lexical and semantic
# matches.
_max_chars=8000

fetch_article() {
    local title="$1"
    local out="${VERIFY_STAGING_DIR}/${title}.txt"
    local resp
    resp=$(curl -fsS \
        --get \
        --data-urlencode "action=query" \
        --data-urlencode "format=json" \
        --data-urlencode "prop=extracts" \
        --data-urlencode "explaintext=1" \
        --data-urlencode "redirects=1" \
        --data-urlencode "titles=${title}" \
        -A "${_ua}" \
        "${_api}")
    local extract
    extract=$(printf '%s' "${resp}" \
        | jq -r '.query.pages | to_entries[0].value.extract // ""')
    if [[ -z "${extract}" || "${extract}" == "null" ]]; then
        echo "internet source: empty extract for '${title}'" >&2
        return 1
    fi
    printf '# %s\n\n%s\n' "${title}" "${extract}" \
        | awk -v max="${_max_chars}" 'BEGIN{n=0} { if (n < max) { print; n += length($0)+1 } }' \
        >"${out}"
    echo "  fetched ${title} ($(wc -c <"${out}" | tr -d ' ') bytes)"
}

echo "internet source: downloading Japanese Wikipedia articles"
fetch_article "形態素解析"
fetch_article "全文検索"
fetch_article "ベクトル空間モデル"

# Queries paired to the fetched articles:
# - "形態素解析" appears verbatim in the article of the same name.
# - "自然言語処理" is semantically adjacent to all three articles
#   but the surface form is not guaranteed to be on every page; the
#   embedding (multilingual-e5) is expected to bridge that.
export VERIFY_QUERY_FULLTEXT="形態素解析"
export VERIFY_QUERY_VECTOR="自然言語処理"

# When run standalone, the env-var exports vanish with the process.
# Emit the file list and queries so a caller can pick them up.
if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then
    echo "staged to: ${VERIFY_STAGING_DIR}"
    find "${VERIFY_STAGING_DIR}" -maxdepth 1 -type f \
        \( -name '*.md' -o -name '*.txt' \) -print
    echo "VERIFY_QUERY_FULLTEXT=${VERIFY_QUERY_FULLTEXT}"
    echo "VERIFY_QUERY_VECTOR=${VERIFY_QUERY_VECTOR}"
fi

#!/usr/bin/env bash
# Verification source: Japanese Wikipedia (MediaWiki APIs).
#
# Downloads each of a small fixed set of Japanese articles in three
# different parser-relevant formats so that PlainTextParser,
# MarkdownParser (via the wikitext source), and HtmlParser are all
# exercised when staged content is ingested.
#
#   - <title>.txt   : extracts API (explaintext=1)              → PlainText
#   - <title>.html  : action=parse&prop=text                    → HTML
#   - <title>.md    : raw wikitext, lightly prefixed as Markdown → Markdown
#
# Exports the queries that step 5 (full-text) and step 6 (vector)
# should use against the resulting index.
#
# This script is intended to be `source`d by scripts/verify/run.sh
# (which sets VERIFY_STAGING_DIR before sourcing) but it also runs
# standalone if invoked with a target directory as the first argument.
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
    \( -name '*.md' -o -name '*.markdown' \
    -o -name '*.txt' -o -name '*.text' -o -name '*.log' \
    -o -name '*.adoc' -o -name '*.asciidoc' \
    -o -name '*.html' -o -name '*.htm' -o -name '*.xhtml' \) -delete

_ua="searchable-verify/1.0 (+https://github.com/izuno4t/searchable)"
_api="https://ja.wikipedia.org/w/api.php"
# Cap each article so a single doc cannot blow up the request. The
# leading paragraphs are sufficient to assert lexical and semantic
# matches.
_max_chars=8000

_truncate() {
    awk -v max="$1" 'BEGIN{n=0} { if (n < max) { print; n += length($0)+1 } }'
}

# Fetch the plain-text extract of an article via the MediaWiki
# extracts API. Saved as <title>.txt.
fetch_txt() {
    local title="$1"
    local out="${VERIFY_STAGING_DIR}/${title}.txt"
    local resp extract
    resp=$(curl -fsS --get \
        --data-urlencode "action=query" \
        --data-urlencode "format=json" \
        --data-urlencode "prop=extracts" \
        --data-urlencode "explaintext=1" \
        --data-urlencode "redirects=1" \
        --data-urlencode "titles=${title}" \
        -A "${_ua}" "${_api}")
    extract=$(printf '%s' "${resp}" \
        | jq -r '.query.pages | to_entries[0].value.extract // ""')
    if [[ -z "${extract}" || "${extract}" == "null" ]]; then
        echo "internet source: empty extract for '${title}'" >&2
        return 1
    fi
    printf '# %s\n\n%s\n' "${title}" "${extract}" | _truncate "${_max_chars}" >"${out}"
    echo "  fetched ${title}.txt ($(wc -c <"${out}" | tr -d ' ') bytes)"
}

# Fetch the rendered HTML body of an article via action=parse. Saved
# as <title>.html, wrapped in a minimal HTML5 skeleton so HtmlParser
# sees a well-formed document.
fetch_html() {
    local title="$1"
    local out="${VERIFY_STAGING_DIR}/${title}.html"
    local resp body
    resp=$(curl -fsS --get \
        --data-urlencode "action=parse" \
        --data-urlencode "format=json" \
        --data-urlencode "prop=text" \
        --data-urlencode "redirects=1" \
        --data-urlencode "page=${title}" \
        -A "${_ua}" "${_api}")
    body=$(printf '%s' "${resp}" | jq -r '.parse.text["*"] // ""')
    if [[ -z "${body}" || "${body}" == "null" ]]; then
        echo "internet source: empty html for '${title}'" >&2
        return 1
    fi
    {
        printf '<!doctype html>\n<html lang="ja">\n<head><meta charset="utf-8"><title>%s</title></head>\n<body>\n' \
            "${title}"
        printf '%s\n' "${body}" | _truncate "${_max_chars}"
        printf '</body></html>\n'
    } >"${out}"
    echo "  fetched ${title}.html ($(wc -c <"${out}" | tr -d ' ') bytes)"
}

# Fetch the wikitext source via the parse API and treat it as
# Markdown (the syntaxes overlap enough for the assertion: lists,
# headings, links, plain prose all parse). Saved as <title>.md.
fetch_md() {
    local title="$1"
    local out="${VERIFY_STAGING_DIR}/${title}.md"
    local resp body
    resp=$(curl -fsS --get \
        --data-urlencode "action=parse" \
        --data-urlencode "format=json" \
        --data-urlencode "prop=wikitext" \
        --data-urlencode "redirects=1" \
        --data-urlencode "page=${title}" \
        -A "${_ua}" "${_api}")
    body=$(printf '%s' "${resp}" | jq -r '.parse.wikitext["*"] // ""')
    if [[ -z "${body}" || "${body}" == "null" ]]; then
        echo "internet source: empty wikitext for '${title}'" >&2
        return 1
    fi
    {
        printf '# %s\n\n' "${title}"
        printf '%s\n' "${body}" | _truncate "${_max_chars}"
    } >"${out}"
    echo "  fetched ${title}.md ($(wc -c <"${out}" | tr -d ' ') bytes)"
}

echo "internet source: downloading Japanese Wikipedia articles in 3 formats"
fetch_txt  "形態素解析"
fetch_html "全文検索"
fetch_md   "ベクトル空間モデル"

# Queries paired to the fetched articles:
# - "形態素解析" is the title of the .txt article so PlainTextParser +
#   full-text search must find it.
# - "自然言語処理" is semantically adjacent to all three articles but
#   does not appear as a verbatim heading; HYBRID search bridges via
#   the multilingual-e5 embedding.
export VERIFY_QUERY_FULLTEXT="形態素解析"
export VERIFY_QUERY_VECTOR="自然言語処理"

# NOTE: PDF is intentionally not fetched here because:
#   - stable small Japanese PDFs are not trivially available on a
#     well-known URL,
#   - the current run.sh ingests via the JSON API which requires
#     pre-extracted text and therefore cannot exercise PdfParser
#     anyway.
# Use the CLI-based runner (separate work item) to verify PDFs.

# When run standalone, the env-var exports vanish with the process.
# Emit the file list and queries so a caller can pick them up.
if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then
    echo "staged to: ${VERIFY_STAGING_DIR}"
    find "${VERIFY_STAGING_DIR}" -maxdepth 1 -type f \
        \( -name '*.md' -o -name '*.txt' -o -name '*.html' \) -print | sort
    echo "VERIFY_QUERY_FULLTEXT=${VERIFY_QUERY_FULLTEXT}"
    echo "VERIFY_QUERY_VECTOR=${VERIFY_QUERY_VECTOR}"
fi

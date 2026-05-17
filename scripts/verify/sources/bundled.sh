#!/usr/bin/env bash
# Verification source: bundled demo data.
#
# Stages the existing docker/demo-data/ Markdown files into
# ${VERIFY_STAGING_DIR} and additionally synthesizes small samples in
# the other text-based formats Searchable supports (PlainText,
# AsciiDoc, HTML) so that multiple parsers / extensions are covered.
# PDF is intentionally omitted here — see notes at the bottom.
#
# Exports the queries that step 5 (full-text) and step 6 (vector)
# should use against the resulting index.
#
# This script is intended to be `source`d by scripts/verify/run.sh
# (which sets VERIFY_STAGING_DIR before sourcing) but it also runs
# standalone if invoked with a target directory as the first argument.

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
    \( -name '*.md' -o -name '*.markdown' \
    -o -name '*.txt' -o -name '*.text' -o -name '*.log' \
    -o -name '*.adoc' -o -name '*.asciidoc' \
    -o -name '*.html' -o -name '*.htm' -o -name '*.xhtml' \) -delete

# (1) Markdown: copy whatever ships under docker/demo-data/ verbatim.
shopt -s nullglob
for f in "${_src_dir}"/*.md "${_src_dir}"/*.markdown; do
    cp "$f" "${VERIFY_STAGING_DIR}/"
done
shopt -u nullglob

# (2) Plain text: synthesized so PlainTextParser has something to chew.
cat >"${VERIFY_STAGING_DIR}/plaintext-sample.txt" <<'EOF'
ベクトル検索の概要

ベクトル検索は、文書とクエリを高次元のベクトル空間に埋め込み、
コサイン類似度などの距離尺度で関連性の高い文書を返す手法である。
全文検索のような語彙的一致ではなく、意味的な類似性に基づいて
ランキングを行える点が特徴である。
EOF

# (3) AsciiDoc: synthesized so AsciiDocParser has something to chew.
cat >"${VERIFY_STAGING_DIR}/asciidoc-sample.adoc" <<'EOF'
= ハイブリッド検索の設計
:lang: ja

== 概要

ハイブリッド検索は、語彙的一致を扱う全文検索エンジンと、
意味的な類似性を扱うベクトル検索エンジンを組み合わせた仕組みである。

== スコア融合

二つのエンジンが返したランキングは、Reciprocal Rank Fusion
などのスコア融合アルゴリズムで一つの結果リストに統合される。
EOF

# (4) HTML: synthesized so HtmlParser has something to chew.
cat >"${VERIFY_STAGING_DIR}/html-sample.html" <<'EOF'
<!doctype html>
<html lang="ja">
  <head>
    <meta charset="utf-8">
    <title>日本語形態素解析</title>
  </head>
  <body>
    <h1>日本語形態素解析と全文検索</h1>
    <p>
      Kuromoji や Sudachi といった形態素解析器は、日本語の文を
      語に切り分け、活用形を基本形に戻すことで全文検索エンジンが
      適切にトークンを扱えるようにする。
    </p>
    <p>
      これにより「形態素解析しています」のような活用形を含む文に
      対しても、基本形「形態素解析」のクエリでヒットできる。
    </p>
  </body>
</html>
EOF

# Queries paired to the staged content:
# - "ベクトル検索" appears literally in docker/demo-data/02-vector-search.md
#   and the generated plaintext-sample.txt.
# - "意味的な類似性" appears literally in the synthesized samples but is
#   also the semantic theme of 02-vector-search.md and html-sample.html,
#   so HYBRID search must work for the assertion to hold across docs.
export VERIFY_QUERY_FULLTEXT="ベクトル検索"
export VERIFY_QUERY_VECTOR="意味的な類似性"

# NOTE: PDF samples are not generated here because producing a real
# PDF requires a toolchain (e.g. wkhtmltopdf, ghostscript) outside the
# Searchable repo. To verify PdfParser end-to-end, either drop a .pdf
# under docker/demo-data/ before running this script, or switch to the
# `internet` source / a CLI-based runner once available.

# When run standalone, the env-var exports vanish with the process.
# Emit the file list and queries so a caller can pick them up.
if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then
    echo "staged to: ${VERIFY_STAGING_DIR}"
    find "${VERIFY_STAGING_DIR}" -maxdepth 1 -type f \
        \( -name '*.md' -o -name '*.txt' -o -name '*.adoc' -o -name '*.html' \) \
        -print | sort
    echo "VERIFY_QUERY_FULLTEXT=${VERIFY_QUERY_FULLTEXT}"
    echo "VERIFY_QUERY_VECTOR=${VERIFY_QUERY_VECTOR}"
fi

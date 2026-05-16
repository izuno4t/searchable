#!/usr/bin/env bash
# Run the Searchable end-to-end verification procedure documented in
# docs/verify.ja.md. Performs steps 0-7 against the examples/api runner.
#
# Document source is pluggable: the file under scripts/verify/sources/
# matching --source NAME is sourced to stage input files and provide
# the queries used by step 5 (full-text) and step 6 (vector).
#
# Usage:
#   scripts/verify/run.sh [--source bundled|internet] [options]
#
# Options:
#   --source NAME       Document source (default: bundled)
#   --skip-build        Reuse existing examples/api JAR
#   --keep-running      Do not stop the server after verification
#   --base-url URL      Override base URL (default: http://localhost:8080)
#   -h, --help          Show this help
#
# Environment:
#   JAVA                java binary to use (default: java)
#   VERIFY_LOG          Server log file (default: .verify/server.log)

set -euo pipefail

# ---------- paths ----------
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
WORK_DIR="${PROJECT_ROOT}/.verify"
STAGING_DIR="${WORK_DIR}/staging"
DATA_DIR="${WORK_DIR}/data"
SERVER_LOG="${VERIFY_LOG:-${WORK_DIR}/server.log}"

# ---------- defaults ----------
SOURCE="bundled"
SKIP_BUILD=false
KEEP_RUNNING=false
BASE_URL="${BASE_URL:-http://localhost:8080}"
NAMESPACE_ID="verify"
SERVER_READY_TIMEOUT=180

# ---------- arg parsing ----------
usage() { sed -n '2,/^set -euo/p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//;/^set /d'; }

while [[ $# -gt 0 ]]; do
    case "$1" in
        --source) SOURCE="$2"; shift 2 ;;
        --source=*) SOURCE="${1#*=}"; shift ;;
        --skip-build) SKIP_BUILD=true; shift ;;
        --keep-running) KEEP_RUNNING=true; shift ;;
        --base-url) BASE_URL="$2"; shift 2 ;;
        --base-url=*) BASE_URL="${1#*=}"; shift ;;
        -h|--help) usage; exit 0 ;;
        *) echo "unknown arg: $1" >&2; usage >&2; exit 2 ;;
    esac
done

# ---------- helpers ----------
SERVER_PID=""

log()    { printf '%s\n' "$*"; }
header() { printf '\n=== %s ===\n' "$*"; }
pass()   { printf '  ✅ PASS: %s\n' "$*"; }
fail()   { printf '  ❌ FAIL: %s\n' "$*" >&2; exit 1; }

require_cmd() {
    local c
    for c in "$@"; do
        command -v "$c" >/dev/null 2>&1 || fail "command not found: $c"
    done
}

curl_json() {
    # curl_json METHOD URL [JSON_BODY]
    local method="$1" url="$2" body="${3:-}"
    if [[ -n "$body" ]]; then
        curl -fsS -X "$method" "$url" \
            -H 'Content-Type: application/json' \
            -d "$body"
    else
        curl -fsS -X "$method" "$url"
    fi
}

http_code() {
    # http_code METHOD URL [JSON_BODY] -> stdout: status code
    local method="$1" url="$2" body="${3:-}"
    if [[ -n "$body" ]]; then
        curl -s -o /dev/null -w '%{http_code}' -X "$method" "$url" \
            -H 'Content-Type: application/json' \
            -d "$body"
    else
        curl -s -o /dev/null -w '%{http_code}' -X "$method" "$url"
    fi
}

cleanup() {
    local status=$?
    if [[ -n "${SERVER_PID}" ]] && kill -0 "${SERVER_PID}" 2>/dev/null; then
        if [[ "${KEEP_RUNNING}" == "true" && ${status} -eq 0 ]]; then
            log "server kept running (PID ${SERVER_PID})"
        else
            log "stopping server (PID ${SERVER_PID})"
            kill "${SERVER_PID}" 2>/dev/null || true
            wait "${SERVER_PID}" 2>/dev/null || true
        fi
    fi
    return $status
}
trap cleanup EXIT

# ---------- step 0 ----------
step_0_prereq() {
    header "Step 0: Prerequisites"
    require_cmd curl jq java
    local v
    v=$("${JAVA:-java}" -version 2>&1 | head -1 | awk -F'"' '{print $2}')
    local major
    major=$(printf '%s' "$v" | awk -F. '{ if ($1==1) print $2; else print $1 }')
    [[ "${major}" -ge 21 ]] || fail "Java 21+ required (found ${v})"
    pass "java ${v}"

    if (echo >/dev/tcp/127.0.0.1/8080) 2>/dev/null; then
        fail "port 8080 already in use"
    fi
    pass "port 8080 free"

    mkdir -p "${WORK_DIR}" "${STAGING_DIR}" "${DATA_DIR}"
    pass "work dir: ${WORK_DIR}"
}

# ---------- step 1 ----------
step_1_build() {
    header "Step 1: Build"
    local jar="${PROJECT_ROOT}/examples/api/target/api-example-1.0.0-SNAPSHOT.jar"
    if [[ "${SKIP_BUILD}" == "true" ]]; then
        [[ -f "${jar}" ]] || fail "JAR not found at ${jar} (drop --skip-build to build)"
        pass "reusing existing JAR"
        return
    fi
    (cd "${PROJECT_ROOT}" && ./mvnw -B -pl examples/api -am package -DskipTests >"${WORK_DIR}/build.log" 2>&1) \
        || fail "build failed (see ${WORK_DIR}/build.log)"
    [[ -f "${jar}" ]] || fail "JAR not produced"
    pass "JAR built: ${jar}"
}

# ---------- step 2 ----------
step_2_start() {
    header "Step 2: Start"
    rm -rf "${DATA_DIR}"
    mkdir -p "${DATA_DIR}"
    : >"${SERVER_LOG}"
    SEARCHABLE_DATA_DIRECTORY="${DATA_DIR}" \
        nohup "${JAVA:-java}" -jar \
        "${PROJECT_ROOT}/examples/api/target/api-example-1.0.0-SNAPSHOT.jar" \
        >"${SERVER_LOG}" 2>&1 &
    SERVER_PID=$!
    log "  server PID: ${SERVER_PID} (log: ${SERVER_LOG})"

    local i
    for ((i = 0; i < SERVER_READY_TIMEOUT; i++)); do
        if ! kill -0 "${SERVER_PID}" 2>/dev/null; then
            fail "server process exited prematurely (check ${SERVER_LOG})"
        fi
        if curl -fsS "${BASE_URL}/api/v1/namespaces" >/dev/null 2>&1; then
            pass "server ready in ${i}s"
            return
        fi
        sleep 1
    done
    fail "server did not become ready within ${SERVER_READY_TIMEOUT}s"
}

# ---------- step 3 ----------
step_3_namespace() {
    header "Step 3: Create namespace"
    # remove any leftover from a prior run
    http_code DELETE "${BASE_URL}/api/v1/namespaces/${NAMESPACE_ID}" >/dev/null

    local payload
    payload=$(jq -nc --arg id "${NAMESPACE_ID}" \
        '{id:$id, name:$id, config:{architecture:"HYBRID", searchStrategy:"PARALLEL"}}')
    local code
    code=$(http_code POST "${BASE_URL}/api/v1/namespaces" "${payload}")
    [[ "${code}" =~ ^20[01]$ ]] || fail "create namespace returned ${code}"

    local got
    got=$(curl_json GET "${BASE_URL}/api/v1/namespaces/${NAMESPACE_ID}")
    local arch
    arch=$(printf '%s' "${got}" | jq -r '.config.architecture')
    [[ "${arch}" == "HYBRID" ]] || fail "expected HYBRID, got ${arch}"
    pass "namespace '${NAMESPACE_ID}' created (architecture=HYBRID)"

    local recreate
    recreate=$(http_code POST "${BASE_URL}/api/v1/namespaces" "${payload}")
    [[ "${recreate}" == "409" ]] || fail "duplicate create returned ${recreate}, expected 409"
    pass "duplicate create rejected with 409"
}

# ---------- step 4 ----------
step_4_index() {
    header "Step 4: Index documents"
    local count=0 docs_json
    docs_json=$(mktemp)
    printf '[' >"${docs_json}"
    local first=true
    local f
    for f in "${STAGING_DIR}"/*.md "${STAGING_DIR}"/*.txt; do
        [[ -f "$f" ]] || continue
        local id title content
        id=$(basename "$f")
        title=$(basename "$f" | sed 's/\.[^.]*$//')
        content=$(jq -Rs . <"$f")
        if [[ "${first}" == "true" ]]; then first=false; else printf ',' >>"${docs_json}"; fi
        printf '{"id":%s,"title":%s,"content":%s}' \
            "$(jq -Rn --arg v "$id" '$v')" \
            "$(jq -Rn --arg v "$title" '$v')" \
            "${content}" >>"${docs_json}"
        count=$((count + 1))
    done
    printf ']' >>"${docs_json}"

    [[ "${count}" -gt 0 ]] || fail "no documents staged in ${STAGING_DIR}"

    local req
    req=$(jq -n --arg ns "${NAMESPACE_ID}" --slurpfile docs "${docs_json}" \
        '{namespaceId:$ns, documents:$docs[0]}')
    local res
    res=$(curl_json POST "${BASE_URL}/api/v1/index/batch" "${req}")
    rm -f "${docs_json}"

    local succeeded failed
    succeeded=$(printf '%s' "${res}" | jq -r '.succeeded')
    failed=$(printf '%s' "${res}" | jq -r '.failed')
    [[ "${succeeded}" -eq "${count}" && "${failed}" -eq 0 ]] \
        || fail "batch result: succeeded=${succeeded} failed=${failed} (expected ${count}/0)"
    pass "indexed ${succeeded} document(s)"

    local meta total
    meta=$(curl_json GET "${BASE_URL}/api/v1/index/${NAMESPACE_ID}/metadata")
    total=$(printf '%s' "${meta}" \
        | jq -r '.documentCount // .totalDocuments // .total // empty')
    if [[ -n "${total}" ]]; then
        [[ "${total}" -ge 1 ]] || fail "metadata reports zero documents"
        pass "metadata documentCount=${total}"
    else
        log "  (metadata schema differs; skipping count assertion)"
    fi
}

# ---------- step 5 ----------
step_5_fulltext() {
    header "Step 5: Full-text search (Japanese)"
    [[ -n "${VERIFY_QUERY_FULLTEXT:-}" ]] || fail "source script did not set VERIFY_QUERY_FULLTEXT"
    log "  query: ${VERIFY_QUERY_FULLTEXT}"
    local req res hits
    req=$(jq -nc --arg q "${VERIFY_QUERY_FULLTEXT}" --arg ns "${NAMESPACE_ID}" \
        '{query:$q, namespaceIds:[$ns], searchType:"FULL_TEXT"}')
    res=$(curl_json POST "${BASE_URL}/api/v1/search" "${req}")
    hits=$(printf '%s' "${res}" | jq -r '.hits | length')
    [[ "${hits}" -ge 1 ]] || {
        printf '%s\n' "${res}" | jq . >&2
        fail "no hits for full-text query"
    }
    pass "full-text returned ${hits} hit(s)"
}

# ---------- step 6 ----------
step_6_vector() {
    header "Step 6: Vector / hybrid search"
    [[ -n "${VERIFY_QUERY_VECTOR:-}" ]] || fail "source script did not set VERIFY_QUERY_VECTOR"
    log "  query: ${VERIFY_QUERY_VECTOR}"
    local req res hits max_score
    req=$(jq -nc --arg q "${VERIFY_QUERY_VECTOR}" --arg ns "${NAMESPACE_ID}" \
        '{query:$q, namespaceIds:[$ns], searchType:"HYBRID"}')
    res=$(curl_json POST "${BASE_URL}/api/v1/search" "${req}")
    hits=$(printf '%s' "${res}" | jq -r '.hits | length')
    max_score=$(printf '%s' "${res}" | jq -r '.maxScore // 0')
    [[ "${hits}" -ge 1 ]] || {
        printf '%s\n' "${res}" | jq . >&2
        fail "no hits for hybrid query"
    }
    awk -v s="${max_score}" 'BEGIN{ exit (s+0 > 0) ? 0 : 1 }' \
        || fail "hybrid hit has zero/missing score (${max_score})"
    pass "hybrid returned ${hits} hit(s), maxScore=${max_score}"
}

# ---------- step 7 ----------
step_7_cleanup() {
    header "Step 7: Cleanup"
    local code
    code=$(http_code DELETE "${BASE_URL}/api/v1/namespaces/${NAMESPACE_ID}")
    [[ "${code}" =~ ^20[04]$ ]] || fail "namespace delete returned ${code}"
    pass "namespace deleted"

    local missing
    missing=$(http_code GET "${BASE_URL}/api/v1/namespaces/${NAMESPACE_ID}")
    [[ "${missing}" == "404" ]] || fail "namespace still resolvable after delete (HTTP ${missing})"
    pass "namespace returns 404 after delete"
}

# ---------- entry ----------
main() {
    log "Searchable verification (source=${SOURCE})"

    local source_script="${SCRIPT_DIR}/sources/${SOURCE}.sh"
    [[ -f "${source_script}" ]] || fail "no source script: ${source_script}"

    step_0_prereq

    log ""
    log "--- Staging documents via source '${SOURCE}' ---"
    # shellcheck source=/dev/null
    VERIFY_STAGING_DIR="${STAGING_DIR}" source "${source_script}"
    [[ -n "${VERIFY_QUERY_FULLTEXT:-}" && -n "${VERIFY_QUERY_VECTOR:-}" ]] \
        || fail "source script must export VERIFY_QUERY_FULLTEXT and VERIFY_QUERY_VECTOR"
    log "  staged $(find "${STAGING_DIR}" -maxdepth 1 -type f \( -name '*.md' -o -name '*.txt' \) | wc -l | tr -d ' ') file(s)"
    log "  full-text query : ${VERIFY_QUERY_FULLTEXT}"
    log "  vector query    : ${VERIFY_QUERY_VECTOR}"

    step_1_build
    step_2_start
    step_3_namespace
    step_4_index
    step_5_fulltext
    step_6_vector
    step_7_cleanup

    header "All steps PASSED"
}

main "$@"

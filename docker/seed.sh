#!/usr/bin/env bash
# Seed a demo namespace and upload sample documents to a running Searchable server.
#
# Usage: ./docker/seed.sh [base-url]
#        BASE_URL defaults to http://localhost:8080

set -euo pipefail

BASE_URL="${1:-http://localhost:8080}"
DEMO_NS="demo"
DEMO_DIR="$(cd "$(dirname "$0")" && pwd)/demo-data"

if ! command -v curl >/dev/null 2>&1; then
    echo "curl is required" >&2
    exit 1
fi

echo "[seed] base url: ${BASE_URL}"
echo "[seed] demo data dir: ${DEMO_DIR}"

# Wait until the server is reachable.
for i in {1..30}; do
    if curl -fsS "${BASE_URL}/api/v1/namespaces" >/dev/null 2>&1; then
        break
    fi
    echo "[seed] waiting for server... (${i}/30)"
    sleep 2
done

# Create the demo namespace (idempotent: ignore 409 conflicts).
echo "[seed] creating namespace '${DEMO_NS}'"
HTTP_CODE=$(curl -s -o /tmp/seed-create.json -w "%{http_code}" \
    -X POST "${BASE_URL}/api/v1/namespaces" \
    -H "Content-Type: application/json" \
    -d "{\"id\":\"${DEMO_NS}\",\"name\":\"Demo\",\"config\":{\"architecture\":\"HYBRID\",\"searchStrategy\":\"PARALLEL\"}}")
if [[ "${HTTP_CODE}" == "201" ]]; then
    echo "[seed] namespace created"
elif [[ "${HTTP_CODE}" == "409" ]]; then
    echo "[seed] namespace already exists"
else
    echo "[seed] unexpected response (${HTTP_CODE}):" >&2
    cat /tmp/seed-create.json >&2 || true
    exit 1
fi

# Upload sample documents.
shopt -s nullglob
for f in "${DEMO_DIR}"/*; do
    case "${f}" in
        *.md|*.markdown|*.txt|*.adoc|*.html|*.pdf)
            echo "[seed] uploading $(basename "${f}")"
            curl -fsS -X POST "${BASE_URL}/documents/upload" \
                -F "namespaceId=${DEMO_NS}" \
                -F "file=@${f}" \
                -o /dev/null || echo "[seed] upload failed for $(basename "${f}")"
            ;;
    esac
done

echo "[seed] done. open ${BASE_URL}/indexes/${DEMO_NS} to inspect."

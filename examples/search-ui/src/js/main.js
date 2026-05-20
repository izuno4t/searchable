/* Wiring for the vanilla search UI:
 * - Debounced live search (300 ms)
 * - AbortController to cancel in-flight requests on every new keystroke
 * - Facet sidebar
 * - Highlight-safe rendering
 * - Page navigation
 */
(() => {
    const PAGE_SIZE = 10;
    const DEBOUNCE_MS = 300;
    const FACET_FIELDS = ["category", "lang", "tags"];

    const form = document.getElementById("search-form");
    const input = document.getElementById("search-input");
    const hits = document.getElementById("hits");
    const status = document.getElementById("status");
    const facetSidebar = document.getElementById("facets");
    const facetList = document.getElementById("facet-list");
    const facetClear = document.getElementById("facet-clear");
    const pagination = document.getElementById("pagination");
    const endpointLabel = document.getElementById("api-endpoint");
    const configBtn = document.getElementById("config-btn");

    let debounceHandle = null;
    let activeController = null;
    let currentPage = 0;
    let currentFilters = {};

    endpointLabel.textContent = SearchableApi.endpoint();

    configBtn.addEventListener("click", () => {
        const value = window.prompt(
            "API エンドポイント URL を入力してください",
            SearchableApi.endpoint()
        );
        if (value !== null) {
            SearchableApi.setEndpoint(value.trim());
            endpointLabel.textContent = SearchableApi.endpoint();
        }
    });

    form.addEventListener("submit", (e) => {
        e.preventDefault();
        currentPage = 0;
        runSearch();
    });

    input.addEventListener("input", () => {
        window.clearTimeout(debounceHandle);
        debounceHandle = window.setTimeout(() => {
            currentPage = 0;
            runSearch();
        }, DEBOUNCE_MS);
    });

    facetClear.addEventListener("click", () => {
        currentFilters = {};
        currentPage = 0;
        runSearch();
    });

    async function runSearch() {
        const query = input.value.trim();
        if (!query) {
            hits.innerHTML = "";
            status.textContent = "";
            pagination.hidden = true;
            facetSidebar.hidden = true;
            return;
        }
        if (activeController) {
            activeController.abort();
        }
        activeController = new AbortController();
        status.textContent = "検索中...";

        try {
            const result = await SearchableApi.search({
                query,
                offset: currentPage * PAGE_SIZE,
                limit: PAGE_SIZE,
                filters: currentFilters,
                signal: activeController.signal,
            });
            render(result);
        } catch (err) {
            if (err.name === "AbortError") {
                return;
            }
            status.textContent = err.message;
        }
    }

    function render(result) {
        const total = result.totalHits ?? 0;
        const took = result.tookMs ?? 0;
        status.textContent = `${total} 件ヒット (${took} ms)`;

        hits.innerHTML = "";
        for (const hit of result.hits ?? []) {
            hits.appendChild(renderHit(hit));
        }

        renderFacets(result.hits ?? []);
        renderPagination(total);
    }

    function renderHit(hit) {
        const li = document.createElement("li");

        const title = document.createElement("h3");
        const link = document.createElement("a");
        // metadata.url is the reserved key for the document origin
        // (see docs/architecture.md §5.7). Falls back to a no-op anchor
        // when the indexer did not populate it.
        const originUrl = hit.metadata?.url;
        link.href = originUrl ?? "#";
        if (originUrl) {
            link.target = "_blank";
            link.rel = "noopener";
        }
        link.textContent = hit.title ?? hit.documentId;
        title.appendChild(link);

        const meta = document.createElement("div");
        meta.className = "meta";
        meta.textContent = `${hit.namespaceId} / ${hit.documentId}  ・  score ${(hit.score ?? 0).toFixed(4)}`;

        const snippet = document.createElement("p");
        snippet.className = "snippet";
        const fragments = hit.highlights?.content ?? [];
        if (fragments.length > 0) {
            // The server returns trusted HTML with only <mark> wrappers; setting
            // innerHTML is intentional here. If the caller turns off `escapeMarkup`
            // they accept the risk.
            snippet.innerHTML = fragments[0];
        } else if (hit.content) {
            snippet.textContent = hit.content.slice(0, 200);
        }

        li.appendChild(title);
        li.appendChild(meta);
        li.appendChild(snippet);
        return li;
    }

    function renderFacets(rows) {
        facetList.innerHTML = "";
        let anyFacets = false;
        for (const field of FACET_FIELDS) {
            const counts = aggregate(rows, field);
            if (counts.size === 0) continue;
            anyFacets = true;
            const section = document.createElement("div");
            section.className = "facet-section";

            const heading = document.createElement("h3");
            heading.textContent = field;
            section.appendChild(heading);

            for (const [value, count] of counts) {
                const label = document.createElement("label");

                const input = document.createElement("input");
                input.type = "checkbox";
                input.checked = isFilterActive(field, value);
                input.addEventListener("change", () => {
                    toggleFilter(field, value, input.checked);
                });

                const text = document.createElement("span");
                text.textContent = value;
                const cnt = document.createElement("span");
                cnt.className = "count";
                cnt.textContent = count;

                label.appendChild(input);
                label.appendChild(text);
                label.appendChild(cnt);
                section.appendChild(label);
            }
            facetList.appendChild(section);
        }
        facetSidebar.hidden = !anyFacets;
    }

    function aggregate(rows, field) {
        const counts = new Map();
        for (const row of rows) {
            const value = row.metadata?.[field];
            if (value == null) continue;
            const values = Array.isArray(value) ? value : [value];
            for (const v of values) {
                counts.set(v, (counts.get(v) || 0) + 1);
            }
        }
        return new Map([...counts].sort((a, b) => b[1] - a[1]));
    }

    function isFilterActive(field, value) {
        const v = currentFilters[field];
        if (Array.isArray(v)) return v.includes(value);
        return v === value;
    }

    function toggleFilter(field, value, on) {
        const existing = currentFilters[field];
        const list = Array.isArray(existing) ? new Set(existing)
            : existing != null ? new Set([existing]) : new Set();
        if (on) list.add(value); else list.delete(value);
        if (list.size === 0) {
            delete currentFilters[field];
        } else if (list.size === 1) {
            currentFilters[field] = [...list][0];
        } else {
            currentFilters[field] = [...list];
        }
        currentPage = 0;
        runSearch();
    }

    function renderPagination(total) {
        pagination.innerHTML = "";
        const pages = Math.ceil(total / PAGE_SIZE);
        if (pages <= 1) {
            pagination.hidden = true;
            return;
        }
        pagination.hidden = false;
        for (let i = 0; i < pages; i++) {
            const btn = document.createElement("button");
            btn.type = "button";
            btn.textContent = String(i + 1);
            if (i === currentPage) btn.classList.add("current");
            btn.addEventListener("click", () => {
                currentPage = i;
                runSearch();
            });
            pagination.appendChild(btn);
        }
    }
})();

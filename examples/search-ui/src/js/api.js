/**
 * Thin wrapper around the searchable REST API used by the demo UI.
 *
 * Defaults to a same-origin call (`/api/v1/search`); the UI lets users
 * point to a remote API by setting `localStorage.searchableEndpoint`.
 */
const SearchableApi = (() => {
    const STORAGE_KEY = "searchableEndpoint";

    function endpoint() {
        return window.localStorage.getItem(STORAGE_KEY) || "/api/v1/search";
    }

    function setEndpoint(url) {
        if (!url) {
            window.localStorage.removeItem(STORAGE_KEY);
        } else {
            window.localStorage.setItem(STORAGE_KEY, url);
        }
    }

    /**
     * Execute a search. Returns a fetch Promise resolving to the parsed JSON.
     *
     * @param {Object} options
     * @param {string} options.query - search query text
     * @param {string[]} [options.namespaceIds] - optional namespace filter
     * @param {number} [options.offset] - pagination offset (default 0)
     * @param {number} [options.limit] - page size (default 10)
     * @param {Object} [options.filters] - facet filters (key -> string|string[])
     * @param {AbortSignal} [options.signal] - lets callers cancel via AbortController
     */
    async function search({ query, namespaceIds = [], offset = 0, limit = 10,
                            filters = {}, signal } = {}) {
        const body = {
            query,
            namespaceIds,
            pagination: { offset, limit },
            filters,
        };
        const response = await fetch(endpoint(), {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify(body),
            signal,
        });
        if (!response.ok) {
            const text = await response.text();
            throw new Error(`API error ${response.status}: ${text}`);
        }
        return response.json();
    }

    return { search, endpoint, setEndpoint };
})();

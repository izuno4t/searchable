# Searchable UI (Example)

Vanilla HTML + JS + CSS client for the [`examples/api`](../api) REST
server. The UI is intentionally framework-free so it can be served from
any static file host or copied into another web project.

## Layout

```
examples/search-ui/
├── index.html                ← entry point (search box + result list)
├── src/
│   ├── css/styles.css        ← layout + facet sidebar styles
│   └── js/
│       ├── api.js            ← fetch wrapper for /api/v1/search
│       └── main.js           ← UI behaviors (debounce, facets, pagination)
└── README.md
```

## Run

The simplest way is to point any local web server at this directory:

```bash
cd examples/search-ui
python3 -m http.server 8081
# or: npx serve .
```

Then open <http://localhost:8081/>.

By default the UI calls the same-origin `/api/v1/search` endpoint. Click
"変更" in the footer to point at a remote API (e.g.
`http://localhost:8080/api/v1/search`); the value is persisted in
`localStorage` so subsequent reloads keep it.

## Features (TASK-131 .. TASK-136)

| Feature | Source |
| --- | --- |
| Search box + form submission | `index.html`, `main.js` |
| Live search with 300 ms debounce + AbortController | `main.js` |
| Facet sidebar (category / lang / tags) | `main.js#renderFacets` |
| Highlight rendering with `<mark>` tags | `main.js#renderHit` |
| Pagination | `main.js#renderPagination` |
| Styling | `src/css/styles.css` |

## CORS

When hitting a different origin make sure the REST server allows it.
For `examples/api` set `searchable.cors.allowed-origins=...` in
`application.properties` (or via env vars).

## API key

If the API server is behind `X-API-Key`, extend `api.js` so the
`fetch()` call includes the header (the demo deliberately omits this
to keep the snippet copy-pastable into other static demos).

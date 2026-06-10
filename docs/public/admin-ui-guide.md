# Searchable Admin UI guide

Features and operating procedures for the Searchable Admin UI
(searchable-ui).

## 1. Startup

```bash
mvn -B clean package
java -jar searchable-ui/target/searchable-ui-1.0.1-SNAPSHOT.jar
```

By default, both the UI and the REST API are served at
`http://localhost:8080`.

To override the configuration file:

```bash
java -jar searchable-ui-1.0.1-SNAPSHOT.jar \
  --spring.config.location=/path/to/application.properties
```

## 2. Screen layout

| Nav | Path | Contents |
| --- | --- | --- |
| Dashboard | `/` | Statistics summary + search latency graph |
| Namespaces | `/namespaces` | List, create, edit, and delete Namespaces |
| Indexes | `/indexes` | Index status and document management |
| Upload | `/documents/upload` | File upload (automatic parser selection) |
| Settings | `/settings` | Global settings (defaults for new Namespaces) |

## 3. Dashboard

Shown at `GET /`.

- **Namespaces**: number of registered Namespaces
- **Documents**: total document count across all Namespaces
- **Index Size**: total size across all Namespaces (MB)
- **Search p95**: 95th percentile of recent search latency
- **Search Latency graph**: a Chart.js time-series line chart
  (up to 1,024 samples)

Latency is recorded every time the search API
(`/api/v1/search`) is called and reflected on the dashboard.

## 4. Namespace management

### List (`/namespaces`)

- Clicking an ID navigates to the edit screen
- Each row has Edit / Delete buttons
- Delete prompts a browser confirmation dialog

### Create (`/namespaces/new`)

| Field | Required | Constraints |
| --- | --- | --- |
| ID | Yes | `[a-z0-9][a-z0-9_-]{0,63}` |
| Name | Yes | 256 characters or fewer |
| Architecture | No | FULL_TEXT / VECTOR / HYBRID |
| Search Strategy | No | SEQUENTIAL / PARALLEL |
| Search Order | No | FULL_TEXT_FIRST / VECTOR_FIRST |

Leaving a selection empty ("(global default)") falls back to the
global configuration value.

### Edit (`/namespaces/{id}/edit`)

- The ID cannot be changed
- Name and config can be updated together
- Deletion is also available from the same screen

## 5. Index management

### List (`/indexes`)

Each Namespace shows the following:

- Documents (count)
- Size (bytes)
- Status pill (READY/INDEXING/EMPTY/ERROR, color-coded)
- Last Updated timestamp
- View / Rebuild buttons

### Details (`/indexes/{namespaceId}`)

- Metrics cards (Documents/Size/Status/Last Updated)
- Document list (up to 20 per page) — fetched via
  `DocumentMetadataRepository`, so chunk splitting does not produce
  duplicate rows
  - ID, title, indexed_at (body snippets are hidden under the new schema)
  - Delete button (with confirmation dialog)
- Pagination
- Rebuild button

> **Rebuild behavior**: the switch happens without stopping search. A new
> empty index directory is prepared, and the directory name is atomically
> renamed on write completion to switch over. The old directory is
> deleted after a 30-second grace period. The search API keeps returning
> results from the old version while the rebuild is running.

### Document upload (`/documents/upload`)

Supported formats:

- Text family: `.txt`, `.text`, `.log`
- Markdown: `.md`, `.markdown`
- AsciiDoc: `.adoc`, `.asciidoc`
- HTML: `.html`, `.htm`, `.xhtml`
- PDF: `.pdf`
- Word: `.docx`, `.doc`
- Excel: `.xlsx`, `.xls`
- PowerPoint: `.pptx`, `.ppt`

Maximum file size: 64 MB (configurable in `application.properties`).

After a successful upload, the page redirects to the target Namespace's
details screen and shows the indexing result as a flash message.

## 6. Global settings (`/settings`)

A screen that sets the default values for newly created Namespaces.

- Default Architecture
- Default Search Strategy
- Default Search Order

> **Note**: existing Namespace settings are not changed. Per-Namespace
> configuration changes are made individually at
> `/namespaces/{id}/edit`.

## 7. Error pages

Errors such as missing resources are rendered by
`templates/error.html`.

| Situation | HTTP | Display |
| --- | --- | --- |
| Namespace not found | 404 | Not Found |
| Validation failure (ID, etc.) | 400 | Bad Request |
| Duplicate ID | 409 | Conflict |
| Other | 500 | Internal Server Error |

## 8. Keyboard interaction

- `Tab` to move between form elements
- Forms are standard HTML5; no special interactions

## 9. Troubleshooting

### The graph is not drawn

- Check the browser console for Chart.js loading errors
- Verify connectivity to the CDN (jsdelivr.net)

### Bootstrap layout is broken

- Verify that the browser is a Bootstrap 5-supported version
  (latest two versions of Chrome/Firefox/Edge, Safari 13.1+)

### Size error during upload

- `searchable-ui` `application.properties`
- Increase `spring.servlet.multipart.max-file-size` and `max-request-size`

### Can the REST API also be used

- `/api/v1/*` is available on the same port (8080)
- See `examples/api/openapi.yaml` for the OpenAPI specification

## 10. Demo environment

A Docker Compose-based demo environment is provided under `docker/`.
See `docs/public/demo-setup.md` for details.

---

**Document Version**: 1.0
**Last Updated**: 2026-05-15
**Status**: Phase 3

# Filesystem Data Source Plugin (Example)

Example `DataSourcePlugin` for Searchable. Scans a directory tree and
yields each readable file as a `PluginDocument`.

## Build

```bash
mvn -q -B package
```

## Deploy

Drop the produced JAR into Searchable's `plugins/` directory and configure
the plugin via the namespace settings:

```yaml
plugins:
  filesystem:
    directory: /path/to/docs
    extensions: ['.md', '.txt', '.adoc']
```

## Source layout

- `FilesystemDataSourcePlugin.java`: implementation
- `META-INF/services/com.searchable.plugin.DataSourcePlugin`: service registration

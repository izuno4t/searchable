package com.searchable.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;

/**
 * Maps {@code searchable.*} properties from {@code application.properties}.
 */
@ConfigurationProperties(prefix = "searchable")
public class SearchableProperties {

    private Path dataDirectory = Path.of("./data");
    private Persistence persistence = new Persistence();
    private Index index = new Index();
    private Plugins plugins = new Plugins();
    private Global global = new Global();
    private Embedding embedding = new Embedding();

    public Path getDataDirectory() { return dataDirectory; }
    public void setDataDirectory(final Path v) { this.dataDirectory = v; }
    public Persistence getPersistence() { return persistence; }
    public void setPersistence(final Persistence v) { this.persistence = v; }
    public Index getIndex() { return index; }
    public void setIndex(final Index v) { this.index = v; }
    public Plugins getPlugins() { return plugins; }
    public void setPlugins(final Plugins v) { this.plugins = v; }
    public Global getGlobal() { return global; }
    public void setGlobal(final Global v) { this.global = v; }
    public Embedding getEmbedding() { return embedding; }
    public void setEmbedding(final Embedding v) { this.embedding = v; }

    /** Persistence DB connection settings. */
    public static class Persistence {
        private String type = "H2";
        private String url = "jdbc:h2:./data/metadata;MODE=PostgreSQL";
        private String username = "sa";
        private String password = "";

        public String getType() { return type; }
        public void setType(final String v) { this.type = v; }
        public String getUrl() { return url; }
        public void setUrl(final String v) { this.url = v; }
        public String getUsername() { return username; }
        public void setUsername(final String v) { this.username = v; }
        public String getPassword() { return password; }
        public void setPassword(final String v) { this.password = v; }
    }

    /** Lucene index settings. */
    public static class Index {
        private Path directory = Path.of("./data/indexes");

        public Path getDirectory() { return directory; }
        public void setDirectory(final Path v) { this.directory = v; }
    }

    /** Plugin loader settings. */
    public static class Plugins {
        private Path directory;

        public Path getDirectory() { return directory; }
        public void setDirectory(final Path v) { this.directory = v; }
    }

    /** Vector embedding settings. */
    public static class Embedding {
        /** Embedding provider: {@code hash} (default, deterministic) or {@code onnx}. */
        private String provider = "hash";
        /** Output vector dimension. Must be a multiple of 8 for the hash provider. */
        private int dimension = 384;
        /** Path to an ONNX model file (required when {@code provider=onnx}). */
        private Path modelPath;
        /** Identifier for diagnostics (e.g. {@code multilingual-e5-small}). */
        private String modelId = "hash:384";
        /** Maximum sequence length for the ONNX tokenizer. */
        private int maxSequenceLength = 512;

        public String getProvider() { return provider; }
        public void setProvider(final String v) { this.provider = v; }
        public int getDimension() { return dimension; }
        public void setDimension(final int v) { this.dimension = v; }
        public Path getModelPath() { return modelPath; }
        public void setModelPath(final Path v) { this.modelPath = v; }
        public String getModelId() { return modelId; }
        public void setModelId(final String v) { this.modelId = v; }
        public int getMaxSequenceLength() { return maxSequenceLength; }
        public void setMaxSequenceLength(final int v) { this.maxSequenceLength = v; }
    }

    /** Default search behavior for namespaces. */
    public static class Global {
        private String defaultArchitecture = "FULL_TEXT";
        private String defaultSearchStrategy = "SEQUENTIAL";
        private String defaultSearchOrder = "FULL_TEXT_FIRST";

        public String getDefaultArchitecture() { return defaultArchitecture; }
        public void setDefaultArchitecture(final String v) { this.defaultArchitecture = v; }
        public String getDefaultSearchStrategy() { return defaultSearchStrategy; }
        public void setDefaultSearchStrategy(final String v) { this.defaultSearchStrategy = v; }
        public String getDefaultSearchOrder() { return defaultSearchOrder; }
        public void setDefaultSearchOrder(final String v) { this.defaultSearchOrder = v; }
    }
}

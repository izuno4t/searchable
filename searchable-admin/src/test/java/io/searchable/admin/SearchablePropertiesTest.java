package io.searchable.admin;

import io.searchable.admin.config.SearchableProperties;
import io.searchable.core.infrastructure.lucene.StorageBackend;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/** Direct getter/setter coverage for {@link SearchableProperties} and nested types. */
class SearchablePropertiesTest {

    @Test
    void topLevelGettersAndSettersAreReachable() {
        final SearchableProperties p = new SearchableProperties();
        assertThat(p.getDataDirectory()).isEqualTo(Path.of("./data"));
        p.setDataDirectory(Path.of("/tmp/x"));
        assertThat(p.getDataDirectory()).isEqualTo(Path.of("/tmp/x"));

        final var persistence = new SearchableProperties.Persistence();
        p.setPersistence(persistence);
        assertThat(p.getPersistence()).isSameAs(persistence);

        final var index = new SearchableProperties.Index();
        p.setIndex(index);
        assertThat(p.getIndex()).isSameAs(index);

        final var plugins = new SearchableProperties.Plugins();
        p.setPlugins(plugins);
        assertThat(p.getPlugins()).isSameAs(plugins);

        final var global = new SearchableProperties.Global();
        p.setGlobal(global);
        assertThat(p.getGlobal()).isSameAs(global);

        final var embedding = new SearchableProperties.Embedding();
        p.setEmbedding(embedding);
        assertThat(p.getEmbedding()).isSameAs(embedding);

        final var dictionary = new SearchableProperties.Dictionary();
        p.setDictionary(dictionary);
        assertThat(p.getDictionary()).isSameAs(dictionary);

        final var chunking = new SearchableProperties.Chunking();
        p.setChunking(chunking);
        assertThat(p.getChunking()).isSameAs(chunking);
    }

    @Test
    void persistenceSettersAndGetters() {
        final var pe = new SearchableProperties.Persistence();
        pe.setType("POSTGRESQL");
        pe.setUrl("jdbc:postgresql://x");
        pe.setUsername("u");
        pe.setPassword("p");
        assertThat(pe.getType()).isEqualTo("POSTGRESQL");
        assertThat(pe.getUrl()).isEqualTo("jdbc:postgresql://x");
        assertThat(pe.getUsername()).isEqualTo("u");
        assertThat(pe.getPassword()).isEqualTo("p");
    }

    @Test
    void indexSettersAndGetters() {
        final var i = new SearchableProperties.Index();
        i.setDirectory(Path.of("/idx"));
        i.setBackend(StorageBackend.MEMORY);
        assertThat(i.getDirectory()).isEqualTo(Path.of("/idx"));
        assertThat(i.getBackend()).isEqualTo(StorageBackend.MEMORY);
    }

    @Test
    void pluginsSettersAndGetters() {
        final var pl = new SearchableProperties.Plugins();
        assertThat(pl.getDirectory()).isNull();
        pl.setDirectory(Path.of("/plugins"));
        assertThat(pl.getDirectory()).isEqualTo(Path.of("/plugins"));
    }

    @Test
    void embeddingSettersAndGetters() {
        final var e = new SearchableProperties.Embedding();
        e.setProvider("onnx");
        e.setDimension(384);
        e.setModelPath(Path.of("/models/x.onnx"));
        e.setModelId("multilingual-e5-small");
        e.setMaxSequenceLength(256);

        assertThat(e.getProvider()).isEqualTo("onnx");
        assertThat(e.getDimension()).isEqualTo(384);
        assertThat(e.getModelPath()).isEqualTo(Path.of("/models/x.onnx"));
        assertThat(e.getModelId()).isEqualTo("multilingual-e5-small");
        assertThat(e.getMaxSequenceLength()).isEqualTo(256);
    }

    @Test
    void chunkingSettersAndGetters() {
        final var c = new SearchableProperties.Chunking();
        c.setStrategy("section");
        c.setChunkSize(1024);
        c.setOverlap(128);
        c.setSentenceTargetSize(500);

        assertThat(c.getStrategy()).isEqualTo("section");
        assertThat(c.getChunkSize()).isEqualTo(1024);
        assertThat(c.getOverlap()).isEqualTo(128);
        assertThat(c.getSentenceTargetSize()).isEqualTo(500);
    }

    @Test
    void dictionarySettersAndGetters() {
        final var d = new SearchableProperties.Dictionary();
        d.setStorage("db");
        d.setDirectory(Path.of("/dicts"));
        assertThat(d.getStorage()).isEqualTo("db");
        assertThat(d.getDirectory()).isEqualTo(Path.of("/dicts"));
    }

    @Test
    void globalSettersAndGetters() {
        final var g = new SearchableProperties.Global();
        g.setDefaultArchitecture("HYBRID");
        g.setDefaultSearchStrategy("PARALLEL");
        g.setDefaultSearchOrder("VECTOR_FIRST");

        assertThat(g.getDefaultArchitecture()).isEqualTo("HYBRID");
        assertThat(g.getDefaultSearchStrategy()).isEqualTo("PARALLEL");
        assertThat(g.getDefaultSearchOrder()).isEqualTo("VECTOR_FIRST");
    }
}

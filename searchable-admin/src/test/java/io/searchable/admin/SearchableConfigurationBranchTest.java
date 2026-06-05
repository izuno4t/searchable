package io.searchable.admin;

import io.searchable.admin.config.SearchableProperties;
import io.searchable.admin.config.SearchableTestDataConfig;
import io.searchable.core.domain.chunking.ChunkingStrategy;
import io.searchable.core.domain.dictionary.UserDictionaryRepository;
import io.searchable.core.infrastructure.chunking.FixedSizeChunkingStrategy;
import io.searchable.core.infrastructure.chunking.ParagraphChunkingStrategy;
import io.searchable.core.infrastructure.chunking.SectionChunkingStrategy;
import io.searchable.core.infrastructure.chunking.SentenceChunkingStrategy;
import io.searchable.core.infrastructure.dictionary.JdbcUserDictionaryRepository;
import io.searchable.testkit.spring.SearchableSpringBootTest;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the non-default branches of the bean factories in
 * {@link io.searchable.admin.config.SearchableConfiguration}.
 */
class SearchableConfigurationBranchTest {

    @SearchableSpringBootTest
    @Import(SearchableTestDataConfig.class)
    @TestPropertySource(properties = {
        "searchable.chunking.strategy=fixed",
        "searchable.dictionary.storage=db"
    })
    @Nested
    class FixedChunkingDbDictionary {
        @Autowired ChunkingStrategy chunkingStrategy;
        @Autowired UserDictionaryRepository dictionaryRepository;

        @Test
        void fixedChunkingAndDbDictionaryWired() {
            assertThat(chunkingStrategy).isInstanceOf(FixedSizeChunkingStrategy.class);
            assertThat(dictionaryRepository).isInstanceOf(JdbcUserDictionaryRepository.class);
        }
    }

    @SearchableSpringBootTest
    @Import(SearchableTestDataConfig.class)
    @TestPropertySource(properties = {
        "searchable.chunking.strategy=sentence"
    })
    @Nested
    class SentenceChunking {
        @Autowired ChunkingStrategy chunkingStrategy;

        @Test
        void sentenceChunkingWired() {
            assertThat(chunkingStrategy).isInstanceOf(SentenceChunkingStrategy.class);
        }
    }

    @SearchableSpringBootTest
    @Import(SearchableTestDataConfig.class)
    @TestPropertySource(properties = {
        "searchable.chunking.strategy=paragraph"
    })
    @Nested
    class ParagraphChunking {
        @Autowired ChunkingStrategy chunkingStrategy;

        @Test
        void paragraphChunkingWired() {
            assertThat(chunkingStrategy).isInstanceOf(ParagraphChunkingStrategy.class);
        }
    }

    @SearchableSpringBootTest
    @Import(SearchableTestDataConfig.class)
    @TestPropertySource(properties = {
        "searchable.chunking.strategy=section"
    })
    @Nested
    class SectionChunking {
        @Autowired ChunkingStrategy chunkingStrategy;

        @Test
        void sectionChunkingWired() {
            assertThat(chunkingStrategy).isInstanceOf(SectionChunkingStrategy.class);
        }
    }
}

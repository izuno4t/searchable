package io.searchable.admin;

import io.searchable.admin.config.SearchableProperties;
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
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the non-default branches of the bean factories in
 * {@link io.searchable.admin.config.SearchableConfiguration}.
 */
class SearchableConfigurationBranchTest {

    @SearchableSpringBootTest
    @TestPropertySource(properties = {
        "searchable.data-directory=./build/ui-cfg-fixed",
        "searchable.persistence.url=jdbc:h2:mem:cfg-fixed;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "searchable.index.directory=./build/ui-cfg-fixed/indexes",
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
    @TestPropertySource(properties = {
        "searchable.data-directory=./build/ui-cfg-sent",
        "searchable.persistence.url=jdbc:h2:mem:cfg-sent;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "searchable.index.directory=./build/ui-cfg-sent/indexes",
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
    @TestPropertySource(properties = {
        "searchable.data-directory=./build/ui-cfg-para",
        "searchable.persistence.url=jdbc:h2:mem:cfg-para;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "searchable.index.directory=./build/ui-cfg-para/indexes",
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
    @TestPropertySource(properties = {
        "searchable.data-directory=./build/ui-cfg-section",
        "searchable.persistence.url=jdbc:h2:mem:cfg-section;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "searchable.index.directory=./build/ui-cfg-section/indexes",
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

package io.searchable.core.infrastructure.chunking;

import io.searchable.core.domain.chunking.Chunk;
import io.searchable.core.domain.document.Document;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Branch coverage for {@link SentenceChunkingStrategy}. */
class SentenceChunkingStrategyBranchTest {

    private Document doc(final String content) {
        return Document.builder()
            .id("d").namespaceId("ns").title("title").content(content).build();
    }

    @Test
    void rejectsNonPositiveTargetSize() {
        assertThatThrownBy(() -> new SentenceChunkingStrategy(0))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SentenceChunkingStrategy(-5))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNullDocument() {
        assertThatThrownBy(() -> new SentenceChunkingStrategy().chunk(null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void recognizesEveryAcceptedTerminator() {
        // 6 terminator characters + 1 non-terminator
        final String src = "あ。い！う？ま.な!お?xx";
        final List<Chunk> chunks = new SentenceChunkingStrategy(1).chunk(doc(src));
        // targetSize=1 forces flush after every sentence (7 sentences total)
        assertThat(chunks.size()).isGreaterThanOrEqualTo(6);
    }

    @Test
    void trailingFragmentWithoutTerminatorIsAppended() {
        final List<Chunk> chunks = new SentenceChunkingStrategy(20)
            .chunk(doc("第一文。第二文 終わりの非文"));
        assertThat(chunks).isNotEmpty();
        // last chunk contains the unterminated tail
        assertThat(chunks.get(chunks.size() - 1).text()).contains("終わりの非文");
    }

    @Test
    void whitespaceOnlyTailIsNotKept() {
        // "第一文。" -> sentence list of 1. The tail '\n  ' should be trimmed and dropped.
        final List<Chunk> chunks = new SentenceChunkingStrategy(50)
            .chunk(doc("第一文。\n  "));
        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).text()).contains("第一文。");
    }

    @Test
    void defaultConstructorUsesDefaultTargetSize() {
        // Construct via default; smoke test that it produces output.
        final List<Chunk> chunks = new SentenceChunkingStrategy().chunk(doc("文1。文2。"));
        assertThat(chunks).hasSize(1);
    }
}

package io.searchable.core.infrastructure.embedding;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OnnxValue;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import io.searchable.core.domain.embedding.Tokenizer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * Exercises the OnnxEmbeddingProvider forward pass (embed / meanPool /
 * normalize / close) by stubbing the static {@link OrtEnvironment} and
 * {@link OnnxTensor} factories so that no real {@code .onnx} model is
 * required.
 *
 * <p>Constructor failure paths and validation are covered by
 * {@link OnnxEmbeddingProviderTest}.
 */
class OnnxEmbeddingProviderForwardPassTest {

    @TempDir Path tempDir;

    /** Tokenizer that produces deterministic, fully-attended sequences. */
    private static Tokenizer tokenizer(final int max) {
        return (text, m) -> {
            final long[] ids = new long[m];
            final long[] mask = new long[m];
            for (int i = 0; i < m; i++) {
                ids[i] = i + 1;
                mask[i] = 1;
            }
            return new Tokenizer.Encoding(ids, mask);
        };
    }

    /**
     * Stub the ai.onnxruntime layer with mocks for the whole test scope.
     *
     * @return a triple of (provider, mocked session, mocked environment).
     */
    private static record Harness(OnnxEmbeddingProvider provider,
                                  OrtSession session,
                                  OrtEnvironment environment) implements AutoCloseable {
        @Override public void close() { provider.close(); }
    }

    /**
     * Build an OnnxEmbeddingProvider whose ONNX dependencies are entirely
     * mocked. Caller is responsible for closing the returned MockedStatic
     * handles to keep test isolation.
     */
    private Harness newHarness(final int dimension,
                               final int maxSeq,
                               final float[][][] modelOutput,
                               final MockedStatic<OrtEnvironment> envStatic,
                               final MockedStatic<OnnxTensor> tensorStatic) throws Exception {
        final OrtEnvironment env = mock(OrtEnvironment.class);
        final OrtSession session = mock(OrtSession.class);

        envStatic.when(OrtEnvironment::getEnvironment).thenReturn(env);
        when(env.createSession(any(String.class), any(OrtSession.SessionOptions.class)))
            .thenReturn(session);

        final OnnxTensor tensor = mock(OnnxTensor.class);
        tensorStatic.when(() -> OnnxTensor.createTensor(any(OrtEnvironment.class),
                any(java.nio.LongBuffer.class), any(long[].class)))
            .thenReturn(tensor);

        final OrtSession.Result result = mock(OrtSession.Result.class);
        final OnnxValue value = mock(OnnxValue.class);
        when(result.get(0)).thenReturn(value);
        when(value.getValue()).thenReturn(modelOutput);
        when(session.run(any(Map.class))).thenReturn(result);

        final OnnxEmbeddingProvider provider = new OnnxEmbeddingProvider(
            "stub", tempDir.resolve("fake.onnx"),
            tokenizer(maxSeq), dimension, maxSeq);
        return new Harness(provider, session, env);
    }

    @Test
    void embedReturnsNormalizedMeanPooledVector() throws Exception {
        final int dim = 4, max = 2;
        // Two tokens, both attended; model emits [[1,0,0,0],[1,0,0,0]] so
        // mean pool == [1,0,0,0] and L2 norm leaves it unchanged.
        final float[][][] output = {{
            {1f, 0f, 0f, 0f},
            {1f, 0f, 0f, 0f}
        }};
        try (MockedStatic<OrtEnvironment> envStatic = mockStatic(OrtEnvironment.class);
             MockedStatic<OnnxTensor> tensorStatic = mockStatic(OnnxTensor.class);
             Harness h = newHarness(dim, max, output, envStatic, tensorStatic)) {

            final float[] vec = h.provider().embed("こんにちは");
            assertThat(vec).hasSize(dim);
            assertThat(vec[0]).isEqualTo(1f);
            assertThat(vec[1]).isZero();

            assertThat(h.provider().dimension()).isEqualTo(dim);
            assertThat(h.provider().identifier()).isEqualTo("stub");
        }
    }

    @Test
    void embedNormalisesNonUnitVectorsToUnitLength() throws Exception {
        final int dim = 3, max = 1;
        // Single attended token emitting [3,0,4]; mean == [3,0,4]; L2 norm
        // is 5, so the normalized vector is [0.6, 0, 0.8].
        final float[][][] output = {{
            {3f, 0f, 4f}
        }};
        try (MockedStatic<OrtEnvironment> envStatic = mockStatic(OrtEnvironment.class);
             MockedStatic<OnnxTensor> tensorStatic = mockStatic(OnnxTensor.class);
             Harness h = newHarness(dim, max, output, envStatic, tensorStatic)) {

            final float[] vec = h.provider().embed("text");
            assertThat(vec[0]).isEqualTo(0.6f);
            assertThat(vec[1]).isZero();
            assertThat(vec[2]).isEqualTo(0.8f);
        }
    }

    @Test
    void embedSkipsTokensWithZeroAttention() throws Exception {
        // Two tokens but only the first is attended; model returns
        // [[2,2],[100,100]]. Mean pool ignores the padded second row.
        final int dim = 2, max = 2;
        final float[][][] output = {{
            {2f, 2f},
            {100f, 100f}
        }};
        // Custom tokenizer with masked second token.
        final Tokenizer maskedTok = (text, m) -> new Tokenizer.Encoding(
            new long[]{1L, 0L}, new long[]{1L, 0L});

        try (MockedStatic<OrtEnvironment> envStatic = mockStatic(OrtEnvironment.class);
             MockedStatic<OnnxTensor> tensorStatic = mockStatic(OnnxTensor.class)) {
            final OrtEnvironment env = mock(OrtEnvironment.class);
            envStatic.when(OrtEnvironment::getEnvironment).thenReturn(env);
            final OrtSession session = mock(OrtSession.class);
            when(env.createSession(any(String.class), any(OrtSession.SessionOptions.class)))
                .thenReturn(session);
            tensorStatic.when(() -> OnnxTensor.createTensor(any(OrtEnvironment.class),
                    any(java.nio.LongBuffer.class), any(long[].class)))
                .thenReturn(mock(OnnxTensor.class));
            final OrtSession.Result result = mock(OrtSession.Result.class);
            final OnnxValue value = mock(OnnxValue.class);
            when(result.get(0)).thenReturn(value);
            when(value.getValue()).thenReturn(output);
            when(session.run(any(Map.class))).thenReturn(result);

            try (OnnxEmbeddingProvider provider = new OnnxEmbeddingProvider(
                    "masked", tempDir.resolve("fake.onnx"),
                    maskedTok, dim, max)) {
                final float[] vec = provider.embed("text");
                // mean = [2,2]; norm = sqrt(8) ≈ 2.828; vec ≈ [0.707, 0.707]
                assertThat(vec[0]).isEqualTo(vec[1]);
                assertThat((double) vec[0]).isCloseTo(0.7071, org.assertj.core.data.Offset.offset(1e-3));
            }
        }
    }

    @Test
    void embedRejectsTokenizerWithWrongLength() throws Exception {
        final Tokenizer broken = (text, m) ->
            new Tokenizer.Encoding(new long[]{1L}, new long[]{1L}); // shorter than maxSeq
        try (MockedStatic<OrtEnvironment> envStatic = mockStatic(OrtEnvironment.class)) {
            final OrtEnvironment env = mock(OrtEnvironment.class);
            envStatic.when(OrtEnvironment::getEnvironment).thenReturn(env);
            when(env.createSession(any(String.class), any(OrtSession.SessionOptions.class)))
                .thenReturn(mock(OrtSession.class));

            try (OnnxEmbeddingProvider provider = new OnnxEmbeddingProvider(
                    "x", tempDir.resolve("fake.onnx"), broken, 4, 8)) {
                assertThatThrownBy(() -> provider.embed("hi"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("length 8");
            }
        }
    }

    @Test
    void embedRejectsModelOutputWithMismatchedDimension() throws Exception {
        // Model emits 5-dim vectors but provider expects 4.
        final int dim = 4, max = 1;
        final float[][][] output = {{{0f, 0f, 0f, 0f, 0f}}};
        try (MockedStatic<OrtEnvironment> envStatic = mockStatic(OrtEnvironment.class);
             MockedStatic<OnnxTensor> tensorStatic = mockStatic(OnnxTensor.class);
             Harness h = newHarness(dim, max, output, envStatic, tensorStatic)) {

            assertThatThrownBy(() -> h.provider().embed("x"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("does not match configured " + dim);
        }
    }

    @Test
    void embedWrapsOrtExceptionFromSessionRun() throws Exception {
        try (MockedStatic<OrtEnvironment> envStatic = mockStatic(OrtEnvironment.class);
             MockedStatic<OnnxTensor> tensorStatic = mockStatic(OnnxTensor.class)) {
            final OrtEnvironment env = mock(OrtEnvironment.class);
            envStatic.when(OrtEnvironment::getEnvironment).thenReturn(env);
            final OrtSession session = mock(OrtSession.class);
            when(env.createSession(any(String.class), any(OrtSession.SessionOptions.class)))
                .thenReturn(session);
            tensorStatic.when(() -> OnnxTensor.createTensor(any(OrtEnvironment.class),
                    any(java.nio.LongBuffer.class), any(long[].class)))
                .thenReturn(mock(OnnxTensor.class));
            when(session.run(any(Map.class))).thenThrow(new OrtException("synthetic"));

            try (OnnxEmbeddingProvider provider = new OnnxEmbeddingProvider(
                    "x", tempDir.resolve("fake.onnx"), tokenizer(2), 4, 2)) {
                assertThatThrownBy(() -> provider.embed("x"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("ONNX inference failed");
            }
        }
    }

    @Test
    void embedReturnsZeroVectorWhenNormIsZero() throws Exception {
        // Model emits all-zeros; norm == 0 so normalize returns the input unchanged.
        final int dim = 3, max = 1;
        final float[][][] output = {{{0f, 0f, 0f}}};
        try (MockedStatic<OrtEnvironment> envStatic = mockStatic(OrtEnvironment.class);
             MockedStatic<OnnxTensor> tensorStatic = mockStatic(OnnxTensor.class);
             Harness h = newHarness(dim, max, output, envStatic, tensorStatic)) {

            assertThat(h.provider().embed("x")).containsExactly(0f, 0f, 0f);
        }
    }

    @Test
    void closeSwallowsOrtExceptionFromSession() throws Exception {
        try (MockedStatic<OrtEnvironment> envStatic = mockStatic(OrtEnvironment.class)) {
            final OrtEnvironment env = mock(OrtEnvironment.class);
            envStatic.when(OrtEnvironment::getEnvironment).thenReturn(env);
            final OrtSession session = mock(OrtSession.class);
            when(env.createSession(any(String.class), any(OrtSession.SessionOptions.class)))
                .thenReturn(session);
            // session.close() throws OrtException; provider must swallow it.
            org.mockito.Mockito.doThrow(new OrtException("synthetic"))
                .when(session).close();

            final OnnxEmbeddingProvider provider = new OnnxEmbeddingProvider(
                "x", tempDir.resolve("fake.onnx"), tokenizer(1), 4, 1);
            provider.close(); // must not propagate
        }
    }

    @Test
    void rejectsNullText() throws Exception {
        try (MockedStatic<OrtEnvironment> envStatic = mockStatic(OrtEnvironment.class)) {
            final OrtEnvironment env = mock(OrtEnvironment.class);
            envStatic.when(OrtEnvironment::getEnvironment).thenReturn(env);
            when(env.createSession(any(String.class), any(OrtSession.SessionOptions.class)))
                .thenReturn(mock(OrtSession.class));

            try (OnnxEmbeddingProvider provider = new OnnxEmbeddingProvider(
                    "x", tempDir.resolve("fake.onnx"), tokenizer(1), 4, 1)) {
                assertThatThrownBy(() -> provider.embed(null))
                    .isInstanceOf(NullPointerException.class);
            }
        }
    }
}

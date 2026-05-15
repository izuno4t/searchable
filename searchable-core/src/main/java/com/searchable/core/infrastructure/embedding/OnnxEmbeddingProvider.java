package com.searchable.core.infrastructure.embedding;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import com.searchable.core.domain.embedding.EmbeddingProvider;
import com.searchable.core.domain.embedding.Tokenizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.LongBuffer;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * ONNX Runtime backed embedding provider.
 *
 * <p>The provider does not bundle a tokenizer. Callers supply a
 * {@link Tokenizer} that matches the model's vocabulary (e.g. SentencePiece
 * for multilingual-e5). Inference does mean pooling over the last hidden
 * state masked by the attention mask, then L2-normalizes the result.
 */
public final class OnnxEmbeddingProvider implements EmbeddingProvider {

    private static final Logger log = LoggerFactory.getLogger(OnnxEmbeddingProvider.class);

    private final String identifier;
    private final int dimension;
    private final int maxSequenceLength;
    private final Tokenizer tokenizer;
    private final OrtEnvironment environment;
    private final OrtSession session;

    public OnnxEmbeddingProvider(final String identifier,
                                 final Path modelPath,
                                 final Tokenizer tokenizer,
                                 final int dimension,
                                 final int maxSequenceLength) {
        this.identifier = Objects.requireNonNull(identifier, "identifier must not be null");
        Objects.requireNonNull(modelPath, "modelPath must not be null");
        this.tokenizer = Objects.requireNonNull(tokenizer, "tokenizer must not be null");
        if (dimension <= 0) {
            throw new IllegalArgumentException("dimension must be positive");
        }
        if (maxSequenceLength <= 0) {
            throw new IllegalArgumentException("maxSequenceLength must be positive");
        }
        this.dimension = dimension;
        this.maxSequenceLength = maxSequenceLength;

        try {
            this.environment = OrtEnvironment.getEnvironment();
            this.session = environment.createSession(modelPath.toString(),
                new OrtSession.SessionOptions());
            log.info("loaded ONNX model {} from {}", identifier, modelPath);
        } catch (OrtException e) {
            throw new IllegalStateException("Failed to load ONNX model " + modelPath, e);
        }
    }

    @Override
    public float[] embed(final String text) {
        Objects.requireNonNull(text, "text must not be null");
        final Tokenizer.Encoding encoding = tokenizer.encode(text, maxSequenceLength);
        if (encoding.inputIds().length != maxSequenceLength) {
            throw new IllegalStateException(
                "tokenizer must return arrays of length " + maxSequenceLength);
        }

        try {
            final long[][] inputIds = new long[][]{encoding.inputIds()};
            final long[][] attentionMask = new long[][]{encoding.attentionMask()};

            try (OnnxTensor idsTensor = OnnxTensor.createTensor(environment,
                    LongBuffer.wrap(inputIds[0]), new long[]{1, maxSequenceLength});
                 OnnxTensor maskTensor = OnnxTensor.createTensor(environment,
                    LongBuffer.wrap(attentionMask[0]), new long[]{1, maxSequenceLength})) {

                final Map<String, OnnxTensor> inputs = new HashMap<>();
                inputs.put("input_ids", idsTensor);
                inputs.put("attention_mask", maskTensor);

                try (OrtSession.Result result = session.run(inputs)) {
                    final float[][][] hidden = (float[][][]) result.get(0).getValue();
                    return normalize(meanPool(hidden[0], encoding.attentionMask()));
                }
            }
        } catch (OrtException e) {
            throw new IllegalStateException("ONNX inference failed", e);
        }
    }

    @Override
    public int dimension() {
        return dimension;
    }

    @Override
    public String identifier() {
        return identifier;
    }

    @Override
    public void close() {
        try {
            session.close();
        } catch (OrtException e) {
            log.warn("Failed to close ONNX session", e);
        }
    }

    private float[] meanPool(final float[][] hidden, final long[] attentionMask) {
        if (hidden[0].length != dimension) {
            throw new IllegalStateException("Model output dimension "
                + hidden[0].length + " does not match configured " + dimension);
        }
        final double[] sum = new double[dimension];
        long count = 0;
        for (int t = 0; t < hidden.length; t++) {
            if (attentionMask[t] == 0L) {
                continue;
            }
            count++;
            for (int d = 0; d < dimension; d++) {
                sum[d] += hidden[t][d];
            }
        }
        final float[] result = new float[dimension];
        final double divisor = Math.max(count, 1);
        for (int d = 0; d < dimension; d++) {
            result[d] = (float) (sum[d] / divisor);
        }
        return result;
    }

    private static float[] normalize(final float[] vector) {
        double sum = 0.0;
        for (final float v : vector) {
            sum += (double) v * v;
        }
        final double norm = Math.sqrt(sum);
        if (norm == 0.0) {
            return vector;
        }
        for (int i = 0; i < vector.length; i++) {
            vector[i] = (float) (vector[i] / norm);
        }
        return vector;
    }
}

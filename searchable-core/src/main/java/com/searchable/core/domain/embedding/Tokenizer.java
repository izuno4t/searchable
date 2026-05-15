package com.searchable.core.domain.embedding;

/**
 * Converts text into token IDs that an embedding model can consume.
 *
 * <p>Implementations supplied by users of {@code OnnxEmbeddingProvider}
 * encapsulate the model-specific tokenization (e.g. SentencePiece for
 * multilingual-e5).
 */
public interface Tokenizer {

    /**
     * Tokenize the input text.
     *
     * @param text      raw input text
     * @param maxLength maximum sequence length (truncate longer inputs)
     * @return token IDs padded with {@code 0} up to {@code maxLength}
     */
    Encoding encode(String text, int maxLength);

    /**
     * Encoding output containing token IDs and attention mask.
     *
     * @param inputIds      token IDs, length == maxLength
     * @param attentionMask 1 for real tokens, 0 for padding
     */
    record Encoding(long[] inputIds, long[] attentionMask) {

        public Encoding {
            if (inputIds.length != attentionMask.length) {
                throw new IllegalArgumentException(
                    "inputIds and attentionMask must have the same length");
            }
        }
    }
}

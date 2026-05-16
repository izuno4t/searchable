package io.searchable.ai;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Result of an {@link AiProvider#summarize(AiRequest)} call.
 *
 * @param text        generated text (summary / synthesized answer)
 * @param model       identifier of the model that produced the response
 *                    (may differ from the requested one when the provider
 *                    falls back)
 * @param citations   identifiers of the {@link AiContextItem context items}
 *                    referenced in the response, in order of first use
 * @param usage       token / character usage reported by the provider
 *                    (empty when the provider does not expose usage)
 */
public record AiResponse(
    String text,
    String model,
    List<String> citations,
    Map<String, Object> usage
) {

    public AiResponse {
        Objects.requireNonNull(text, "text must not be null");
        Objects.requireNonNull(model, "model must not be null");
        citations = citations == null ? List.of() : List.copyOf(citations);
        usage = usage == null
            ? Map.of()
            : Collections.unmodifiableMap(new LinkedHashMap<>(usage));
    }
}

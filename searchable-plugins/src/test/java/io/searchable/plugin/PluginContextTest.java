package io.searchable.plugin;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PluginContextTest {

    @Test
    void rejectsNullNamespaceId() {
        assertThatThrownBy(() -> new PluginContext(null, Map.of()))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("namespaceId");
    }

    @Test
    void nullConfigBecomesEmptyMap() {
        final PluginContext ctx = new PluginContext("ns-1", null);
        assertThat(ctx.namespaceId()).isEqualTo("ns-1");
        assertThat(ctx.config()).isEmpty();
    }

    @Test
    void configIsDefensivelyCopied() {
        final Map<String, Object> mutable = new HashMap<>();
        mutable.put("token", "abc");
        final PluginContext ctx = new PluginContext("ns-2", mutable);

        mutable.put("token", "xyz");
        mutable.put("other", "value");

        assertThat(ctx.config()).containsExactlyEntriesOf(Map.of("token", "abc"));
    }
}

package io.searchable.core.domain.dictionary;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DictionaryScopeTest {

    @Test
    void globalAppliesToEveryNamespace() {
        assertThat(DictionaryScope.GLOBAL.appliesTo("a")).isTrue();
        assertThat(DictionaryScope.GLOBAL.appliesTo("any")).isTrue();
        assertThat(DictionaryScope.GLOBAL.key()).isEqualTo("GLOBAL");
    }

    @Test
    void namespaceAppliesOnlyToOwnId() {
        final DictionaryScope scope = DictionaryScope.namespace("ns-a");
        assertThat(scope.appliesTo("ns-a")).isTrue();
        assertThat(scope.appliesTo("ns-b")).isFalse();
        assertThat(scope.key()).isEqualTo("NAMESPACE:ns-a");
    }

    @Test
    void namespaceRejectsBlankId() {
        assertThatThrownBy(() -> DictionaryScope.namespace(" "))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void namespaceRejectsNullId() {
        assertThatThrownBy(() -> DictionaryScope.namespace(null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void fromKeyRoundTripsGlobal() {
        assertThat(DictionaryScope.fromKey("GLOBAL")).isEqualTo(DictionaryScope.GLOBAL);
    }

    @Test
    void fromKeyRoundTripsNamespace() {
        final DictionaryScope scope = DictionaryScope.fromKey("NAMESPACE:project-a");
        assertThat(scope.key()).isEqualTo("NAMESPACE:project-a");
        assertThat(scope.appliesTo("project-a")).isTrue();
    }

    @Test
    void fromKeyRejectsUnknownPrefix() {
        assertThatThrownBy(() -> DictionaryScope.fromKey("UNKNOWN:foo"))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> DictionaryScope.fromKey(""))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void fromKeyRejectsNull() {
        assertThatThrownBy(() -> DictionaryScope.fromKey(null))
            .isInstanceOf(NullPointerException.class);
    }
}

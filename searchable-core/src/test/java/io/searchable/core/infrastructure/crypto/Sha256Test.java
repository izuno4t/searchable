package io.searchable.core.infrastructure.crypto;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mockStatic;

class Sha256Test {

    @Test
    void newDigestReturnsSha256() {
        assertThat(Sha256.newDigest().getAlgorithm()).isEqualTo("SHA-256");
    }

    @Test
    void digestProducesExpected32Bytes() {
        assertThat(Sha256.digest(new byte[]{1, 2, 3})).hasSize(32);
    }

    @Test
    void newDigestWrapsNoSuchAlgorithmException() {
        // Force MessageDigest.getInstance to throw so we cover the defensive
        // catch arm — the only way to hit it on a normal JRE.
        try (MockedStatic<MessageDigest> mock = mockStatic(MessageDigest.class)) {
            mock.when(() -> MessageDigest.getInstance("SHA-256"))
                .thenThrow(new NoSuchAlgorithmException("synthetic"));
            assertThatThrownBy(Sha256::newDigest)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SHA-256 unavailable");
        }
    }
}

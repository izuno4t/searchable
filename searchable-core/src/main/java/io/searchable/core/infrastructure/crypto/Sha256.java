package io.searchable.core.infrastructure.crypto;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Single entry point for SHA-256 digesting.
 *
 * <p>Centralizing the {@link MessageDigest} call lets callers stay free of
 * the checked {@link NoSuchAlgorithmException} that the JCA API exposes
 * but that cannot occur in practice — SHA-256 is one of the algorithms
 * every JRE is required to ship.
 */
public final class Sha256 {

    private Sha256() { }

    /** Return a fresh SHA-256 {@link MessageDigest}. */
    public static MessageDigest newDigest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is mandated by Java's standard cryptographic
            // services; reaching here means the JRE itself is broken.
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    /** Compute the SHA-256 digest of the supplied bytes. */
    public static byte[] digest(final byte[] data) {
        return newDigest().digest(data);
    }
}

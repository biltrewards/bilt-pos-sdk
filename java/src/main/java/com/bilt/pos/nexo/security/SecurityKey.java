/*
 *    ____  _ _ _
 *   | __ )(_) | |_
 *   |  _ \| | | __|
 *   | |_) | | | |_
 *   |____/|_|_|\__|
 *
 *   Bilt POS SDK
 */
package com.bilt.pos.nexo.security;

/**
 * Holds the shared secret and metadata required for Nexo message encryption.
 *
 * <p>The terminal and the Sale System must be provisioned with the same
 * passphrase. The passphrase is used to derive AES and HMAC keys via PBKDF2.</p>
 *
 * <p>Create instances via the {@link Builder}:</p>
 * <pre>{@code
 * SecurityKey key = SecurityKey.builder()
 *     .passphrase("mySharedSecret")
 *     .keyIdentifier("myTerminal")
 *     .keyVersion(0)
 *     .cryptoVersion(1)
 *     .build();
 * }</pre>
 */
public final class SecurityKey {

    private final String passphrase;
    private final String keyIdentifier;
    private final int keyVersion;
    private final int cryptoVersion;

    private SecurityKey(Builder builder) {
        this.passphrase = builder.passphrase;
        this.keyIdentifier = builder.keyIdentifier;
        this.keyVersion = builder.keyVersion;
        this.cryptoVersion = builder.cryptoVersion;
    }

    public String getPassphrase() { return passphrase; }
    public String getKeyIdentifier() { return keyIdentifier; }
    public int getKeyVersion() { return keyVersion; }
    public int getCryptoVersion() { return cryptoVersion; }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private String passphrase;
        private String keyIdentifier;
        private int keyVersion;
        private int cryptoVersion = 1;

        private Builder() {}

        public Builder passphrase(String passphrase) {
            this.passphrase = passphrase;
            return this;
        }

        public Builder keyIdentifier(String keyIdentifier) {
            this.keyIdentifier = keyIdentifier;
            return this;
        }

        public Builder keyVersion(int keyVersion) {
            this.keyVersion = keyVersion;
            return this;
        }

        public Builder cryptoVersion(int cryptoVersion) {
            this.cryptoVersion = cryptoVersion;
            return this;
        }

        public SecurityKey build() {
            if (passphrase == null || passphrase.isEmpty()) {
                throw new IllegalStateException("passphrase is required");
            }
            if (keyIdentifier == null || keyIdentifier.isEmpty()) {
                throw new IllegalStateException("keyIdentifier is required");
            }
            return new SecurityKey(this);
        }
    }
}

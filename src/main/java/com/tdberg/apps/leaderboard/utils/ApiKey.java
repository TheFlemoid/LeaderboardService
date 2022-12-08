package com.tdberg.apps.leaderboard.utils;

import java.util.UUID;

public class ApiKey {
    /**
     * Creates a new private key and returns it as a String.
     * NOTE: We do not currently check this against the database listings for uniqueness before returning it, 
     *       and we probably should.
     *
     * @return String a new private key
     */
    public static String createPrivateKey() {
        UUID uuid = UUID.randomUUID();
        String privKey = uuid.toString().replace("-", "");
        privKey  = privKey.substring(0, Math.min(privKey.length(), 31));
        return privKey;
    }

    /**
     * Creates a new public key and returns it as a String.
     * NOTE: We do not currently check this against the database listings for uniqueness before returning it, 
     *       and we probably should.
     *
     * @return String a new public key
     */
    public static String createPublicKey() {
        UUID uuid = UUID.randomUUID();
        String pubKey = uuid.toString().replace("-", "");
        pubKey  = pubKey.substring(0, Math.min(pubKey.length(), 20));
        return pubKey;
    }
}

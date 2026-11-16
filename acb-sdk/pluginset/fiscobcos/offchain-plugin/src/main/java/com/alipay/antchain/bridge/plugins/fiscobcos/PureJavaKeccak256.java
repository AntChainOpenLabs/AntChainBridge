package com.alipay.antchain.bridge.plugins.fiscobcos;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

import org.bouncycastle.crypto.digests.KeccakDigest;
import org.fisco.bcos.sdk.v3.crypto.hash.Hash;
import org.fisco.bcos.sdk.v3.utils.Hex;

/**
 * Keccak-256 for receipt-proof verification without loading the WeDPR JNI library.
 * Each invocation owns its digest; instances can be shared between verifier threads.
 */
final class PureJavaKeccak256 implements Hash {
    @Override
    public String hash(String input) {
        return hashBytes(input.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public String hashBytes(byte[] input) {
        return Hex.toHexString(hash(input));
    }

    @Override
    public byte[] hash(byte[] input) {
        Objects.requireNonNull(input, "input");
        KeccakDigest digest = new KeccakDigest(256);
        digest.update(input, 0, input.length);
        byte[] output = new byte[32];
        digest.doFinal(output, 0);
        return output;
    }
}

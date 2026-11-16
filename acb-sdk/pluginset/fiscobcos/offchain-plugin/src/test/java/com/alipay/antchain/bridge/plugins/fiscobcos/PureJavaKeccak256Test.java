package com.alipay.antchain.bridge.plugins.fiscobcos;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.fisco.bcos.sdk.v3.utils.Hex;
import org.fisco.bcos.sdk.v3.utils.MerkleProofUtility;
import org.junit.Test;

import static org.junit.Assert.*;

public class PureJavaKeccak256Test {
    private final PureJavaKeccak256 hash = new PureJavaKeccak256();

    @Test
    public void knownVectorsAndAllHashEntryPointsAgree() {
        check(new byte[0], "c5d2460186f7233c927e7db2dcc703c0e500b653ca82273b7bfad8045d85a470");
        check("abc".getBytes(StandardCharsets.UTF_8), "4e03657aea45a94fc7d47ba826c8d667c0d1e6e33a64a036ec44f58fa12d6c45");
        check("跨链消息".getBytes(StandardCharsets.UTF_8), "b8bb7a6d21e2f06a0b953af9157ad34eff5ae98d44217e49b4a150ed8683cbae");
        assertEquals(hash.hashBytes("跨链消息".getBytes(StandardCharsets.UTF_8)), hash.hash("跨链消息"));
        assertEquals(hash.hashBytes(new byte[0]), hash.hash(""));
        assertEquals(hash.hashBytes("abc".getBytes(StandardCharsets.UTF_8)), hash.hash("abc"));
        byte[] binary = new byte[256];
        for (int i = 0; i < binary.length; i++) binary[i] = (byte) i;
        check(binary, "dc924469b334aed2a19fac7252e9961aea41f8d91996366029dbe0884229bf36");
    }

    private void check(byte[] input, String expected) {
        assertArrayEquals(Hex.decode(expected), hash.hash(input));
        assertEquals(expected, hash.hashBytes(input));
        assertTrue(hash.hashBytes(input).matches("[0-9a-f]{64}"));
    }

    @Test
    public void usesKeccakRatherThanSha3() throws Exception {
        assertFalse(Arrays.equals(MessageDigest.getInstance("SHA3-256").digest(new byte[0]), hash.hash(new byte[0])));
    }

    @Test
    public void rejectsNullLikeSdkInterface() {
        assertThrows(NullPointerException.class, () -> hash.hash((String) null));
        assertThrows(NullPointerException.class, () -> hash.hash((byte[]) null));
        assertThrows(NullPointerException.class, () -> hash.hashBytes(null));
    }

    @Test
    public void multiLeafProofAndTamperingAreChecked() {
        String first = hash.hash("receipt-a");
        String second = hash.hash("receipt-b");
        String root = hash.hashBytes(Hex.decode(first + second));
        List<String> proof = List.of("00000002", first, second);
        assertTrue(MerkleProofUtility.verifyMerkle(root, proof, first, hash));
        assertTrue(MerkleProofUtility.verifyMerkle(root, proof, second, hash));
        assertFalse(MerkleProofUtility.verifyMerkle("00".repeat(32), proof, first, hash));
        assertFalse(MerkleProofUtility.verifyMerkle(root, proof, "00".repeat(32), hash));
        assertFalse(MerkleProofUtility.verifyMerkle(root, List.of("00000002", first, "00".repeat(32)), first, hash));
    }

    @Test
    public void oneAdapterCanBeUsedByConcurrentProofVerifications() throws Exception {
        String first = hash.hash("receipt-a");
        String second = hash.hash("receipt-b");
        String root = hash.hashBytes(Hex.decode(first + second));
        List<String> proof = List.of("00000002", first, second);
        var workers = Executors.newFixedThreadPool(16);
        try {
            List<Future<Boolean>> results = new ArrayList<>();
            for (int i = 0; i < 150; i++) results.add(workers.submit(
                    () -> MerkleProofUtility.verifyMerkle(root, proof, first, hash)));
            for (var result : results) assertTrue(result.get(10, TimeUnit.SECONDS));
        } finally {
            workers.shutdownNow();
            assertTrue(workers.awaitTermination(10, TimeUnit.SECONDS));
        }
    }
}

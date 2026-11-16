package com.alipay.antchain.bridge.plugins.lib.transactions;

import org.junit.*;
import java.sql.*;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.Assert.*;

public class JdbcTransactionCoordinatorTest {
    private static final byte[] PAYLOAD = new byte[]{1, 2, 3};
    private String network;
    private JdbcTransactionCoordinator coordinator;
    private JdbcTransactionCoordinator.Connections connections;

    @Test public void computesDioxideHashFromSignedBytes() {
        byte[] signed = hex("6300A3C188B3A0010300000077000B000C80640000000000000000000109000140420F0000000000"
                + "03E2BC6A2FE6104B24B41B4B6864F1B9C2DC533C12DEBE0316B0D6EBA471EF9911A3B7A2BA0134AB5F"
                + "C1119EA07447830B5DF3D031005FCB8AA0A8FCE23E20DCDECDCC9EA09AF368542F10E998F6EEF869AC4"
                + "11ABC678513FE83ED05B1BDC0E4088C0E00007C2D000036340000");
        assertEquals("9apsx7wjpxbvg0b99tjpjgfacet3zhx2f4nsbcs1fwcghdexgz00",
                JdbcTransactionCoordinator.dioxideTransactionHash(signed));
    }

    private static byte[] hex(String input) {
        byte[] output = new byte[input.length() / 2];
        for (int i = 0; i < output.length; i++) {
            output[i] = (byte) Integer.parseInt(input.substring(i * 2, i * 2 + 2), 16);
        }
        return output;
    }

    @Before public void setup() throws Exception {
        String url = System.getProperty("isn.test.jdbc");
        Assume.assumeTrue("Use a disposable MySQL database with the coordinator schema installed", url != null);
        Class.forName("com.mysql.cj.jdbc.Driver");
        connections = () -> DriverManager.getConnection(url, "root", "");
        network = "test-" + UUID.randomUUID();
        coordinator = testCoordinator();
    }

    private JdbcTransactionCoordinator testCoordinator() {
        return new JdbcTransactionCoordinator(connections, network, "checkpoint", 0, 1);
    }

    private static class Node implements JdbcTransactionCoordinator.Transport {
        final Set<Long> submitted = ConcurrentHashMap.newKeySet();
        final AtomicInteger composeCalls = new AtomicInteger();
        volatile boolean failSign;
        volatile boolean loseResponse;
        volatile boolean reject;
        volatile boolean acceptOnBroadcast = true;
        volatile boolean unavailable;
        volatile boolean unavailableAfterBroadcast;
        volatile String checkpoint = "checkpoint";
        final AtomicLong nodeIsn = new AtomicLong(181);
        public String checkpoint() { return checkpoint; }
        public long currentIsn(String account) throws Exception {
            if (unavailable) { throw new java.net.ConnectException("node unavailable"); }
            return nodeIsn.get();
        }
        public byte[] composeAndSign(long isn) throws Exception {
            composeCalls.incrementAndGet();
            if (failSign) { throw new Exception("signing unavailable"); }
            return ByteBuffer.allocate(8).putLong(isn).array();
        }
        public String transactionHash(byte[] bytes) {
            return "tx-" + ByteBuffer.wrap(bytes).getLong();
        }
        public String broadcast(byte[] bytes) throws Exception {
            long isn = ByteBuffer.wrap(bytes).getLong();
            if (reject) { throw new JdbcTransactionCoordinator.NodeRejectedException(10001, "rejected"); }
            submitted.add(isn); // the node deduplicates identical signed bytes
            if (acceptOnBroadcast) { nodeIsn.accumulateAndGet(isn + 1, Math::max); }
            if (unavailableAfterBroadcast) { unavailable = true; }
            if (loseResponse) { loseResponse = false; throw new java.net.SocketTimeoutException(); }
            return "tx-" + isn;
        }
    }

    @Test public void concurrentClientsShareOneAccountWithoutLosingConcurrencyAcrossAccounts() throws Exception {
        Node node = new Node();
        ExecutorService pool = Executors.newFixedThreadPool(16);
        List<Future<String>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < 64; i++) {
                final String id = "op-" + i;
                futures.add(pool.submit(() -> testCoordinator()
                        .submit(id, "account", PAYLOAD, node)));
            }
            Set<String> hashes = new HashSet<>();
            for (Future<String> f : futures) { hashes.add(f.get(30, TimeUnit.SECONDS)); }
            assertEquals(64, hashes.size());
            assertEquals(64, node.submitted.size());
            assertTrue(hashes.contains("tx-181")); assertTrue(hashes.contains("tx-244"));
            assertEquals("tx-181", coordinator.submit("different-account", "other", PAYLOAD, new Node()));
        } finally { pool.shutdownNow(); }
    }

    @Test public void responseLossAndRestartReuseSignedBytes() throws Exception {
        Node node = new Node(); node.loseResponse = true;
        try { coordinator.submit("stable", "account", PAYLOAD, node); fail(); }
        catch (java.net.SocketTimeoutException expected) { }
        JdbcTransactionCoordinator restarted = testCoordinator();
        assertEquals("tx-181", restarted.submit("stable", "account", PAYLOAD, node));
        assertEquals("tx-181", restarted.submit("stable", "account", PAYLOAD, node));
        assertEquals(1, node.composeCalls.get()); assertEquals(1, node.submitted.size());
        assertEquals("tx-182", restarted.submit("new-intent-same-payload", "account", PAYLOAD, node));
    }

    @Test public void signingFailureRollsBackAndIdentityConflictsFailClosed() throws Exception {
        Node node = new Node(); node.failSign = true;
        try { coordinator.submit("stable", "account", PAYLOAD, node); fail(); } catch (Exception expected) { }
        node.failSign = false;
        assertEquals("tx-181", coordinator.submit("stable", "account", PAYLOAD, node));
        try { coordinator.submit("stable", "account", new byte[]{9}, node); fail(); }
        catch (IllegalStateException expected) { assertTrue(expected.getMessage().contains("identity")); }
        node.checkpoint = "another-chain";
        try { coordinator.submit("new", "account", PAYLOAD, node); fail(); }
        catch (IllegalStateException expected) { assertTrue(expected.getMessage().contains("checkpoint")); }
    }

    @Test public void explicitNodeRejectionDoesNotAdvanceAndRetriesSameIsn() throws Exception {
        Node node = new Node();
        node.reject = true;
        try { coordinator.submit("stable", "account", PAYLOAD, node); fail(); }
        catch (JdbcTransactionCoordinator.NodeRejectedException expected) { assertEquals(10001, expected.getErrorCode()); }
        assertEquals(181, node.nodeIsn.get());
        node.reject = false;
        assertEquals("tx-181", coordinator.submit("stable", "account", PAYLOAD, node));
        assertEquals(2, node.composeCalls.get());
        assertEquals("tx-182", coordinator.submit("next", "account", PAYLOAD, node));
    }

    @Test public void returnedHashDoesNotReleaseNextIsnUntilNodeAdvances() throws Exception {
        Node node = new Node();
        node.acceptOnBroadcast = false;
        assertEquals("tx-181", coordinator.submit("first", "account", PAYLOAD, node));
        try { coordinator.submit("second", "account", PAYLOAD, node); fail(); }
        catch (JdbcTransactionCoordinator.AccountBlockedException expected) {
            assertTrue(expected.getMessage().contains("181"));
        }
        assertEquals(1, node.composeCalls.get());
        node.nodeIsn.set(182);
        node.acceptOnBroadcast = true;
        assertEquals("tx-182", coordinator.submit("second", "account", PAYLOAD, node));
    }

    @Test public void unavailableNodeDoesNotComposeOrReserveAnIsn() throws Exception {
        Node node = new Node(); node.unavailable = true;
        try { coordinator.submit("first", "account", PAYLOAD, node); fail(); }
        catch (java.net.ConnectException expected) { }
        assertEquals(0, node.composeCalls.get());
        node.unavailable = false;
        assertEquals("tx-181", coordinator.submit("first", "account", PAYLOAD, node));
    }

    @Test public void acceptanceCheckFailureReturnsHashButKeepsNextOperationBlocked() throws Exception {
        Node node = new Node(); node.unavailableAfterBroadcast = true;
        assertEquals("tx-181", coordinator.submit("first", "account", PAYLOAD, node));
        node.unavailableAfterBroadcast = false;
        try { coordinator.submit("second", "account", PAYLOAD, node); fail(); }
        catch (java.net.ConnectException expected) { }
        node.unavailable = false;
        assertEquals("tx-182", coordinator.submit("second", "account", PAYLOAD, node));
    }

    @Test public void queryMailboxLockCoversReadAfterWrite() throws Exception {
        AtomicInteger mailbox = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(8);
        List<Future<Integer>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < 16; i++) {
                final int own = i;
                futures.add(pool.submit(() -> coordinator.withQueryLock("sdp|account", () -> {
                    mailbox.set(own); Thread.sleep(5); return mailbox.get();
                })));
            }
            for (int i = 0; i < futures.size(); i++) { assertEquals(i, (int) futures.get(i).get()); }
        } finally { pool.shutdownNow(); }
    }

    @Test public void databaseFailureDoesNotComposeOrBroadcast() throws Exception {
        Node node = new Node();
        JdbcTransactionCoordinator unavailable = new JdbcTransactionCoordinator(
                () -> { throw new SQLException("unavailable"); }, network, "checkpoint");
        try { unavailable.submit("op", "account", PAYLOAD, node); fail(); } catch (SQLException expected) { }
        assertEquals(0, node.composeCalls.get()); assertTrue(node.submitted.isEmpty());
    }

    @Test public void unsigned32BoundaryDoesNotWrap() throws Exception {
        Node node = new Node(); node.nodeIsn.set(0xffff_ffffL);
        assertEquals("tx-4294967295", coordinator.submit("last", "account", PAYLOAD, node));
        try { coordinator.submit("overflow", "account", PAYLOAD, node); fail(); }
        catch (IllegalStateException expected) { assertTrue(expected.getMessage().contains("ISN")); }
    }

    @Test public void regressedNodeCounterRequiresReconciliation() throws Exception {
        Node node = new Node();
        coordinator.submit("first", "account", PAYLOAD, node);
        node.nodeIsn.set(180);
        try { coordinator.submit("second", "account", PAYLOAD, node); fail(); }
        catch (IllegalStateException expected) { assertTrue(expected.getMessage().contains("regressed")); }
        assertEquals(1, node.composeCalls.get());
        try (Connection c = connections.open(); PreparedStatement s = c.prepareStatement(
                "SELECT allocation_state FROM bridge_tx_account WHERE network_id=? AND account='account'")) {
            s.setString(1, network);
            try (ResultSet r = s.executeQuery()) { assertTrue(r.next()); assertEquals("RECONCILE_REQUIRED", r.getString(1)); }
        }
    }

    @Test public void jdbcDriverIsLoadedFromThePluginClassLoader() throws Exception {
        java.nio.file.Path password = java.nio.file.Files.createTempFile("isn-test-", ".password");
        try {
            Properties config = new Properties();
            config.setProperty("jdbcUrl", System.getProperty("isn.test.jdbc"));
            config.setProperty("user", "root"); config.setProperty("passwordFile", password.toString());
            config.setProperty("networkId", network); config.setProperty("checkpointHash", "checkpoint");
            java.util.concurrent.atomic.AtomicBoolean consulted = new java.util.concurrent.atomic.AtomicBoolean();
            ClassLoader loader = new ClassLoader(getClass().getClassLoader()) {
                @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                    if (name.equals("com.mysql.cj.jdbc.Driver")) { consulted.set(true); }
                    return super.loadClass(name, resolve);
                }
            };
            assertEquals("tx-181", JdbcTransactionCoordinator.fromProperties(config, loader)
                    .submit("op", "account", PAYLOAD, new Node()));
            assertTrue(consulted.get());
        } finally { java.nio.file.Files.deleteIfExists(password); }
    }
}

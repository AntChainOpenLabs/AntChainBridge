package com.alipay.antchain.bridge.plugins.lib.transactions;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.io.InputStream;
import java.security.MessageDigest;
import java.sql.*;
import java.util.Properties;
import java.util.concurrent.Callable;

/**
 * Durable account-scoped allocation, shared across plugin class loaders and hosts.
 * Schema is installed explicitly, never by a transaction-serving process.
 * Signed bytes are committed BEFORE broadcast; uncertain submissions never allocate again.
 */
public final class JdbcTransactionCoordinator {
    public static final long MAX_ISN = 0xffff_ffffL;

    public interface Transport {
        String checkpoint() throws Exception;
        long currentIsn(String account) throws Exception;
        byte[] composeAndSign(long isn) throws Exception;
        String broadcast(byte[] signed) throws Exception;
    }

    public interface Connections { Connection open() throws SQLException; }

    private final Connections connections;
    private final String network;
    private final String checkpoint;

    public JdbcTransactionCoordinator(Connections connections, String network, String checkpoint) {
        require(network, 96, "network");
        require(checkpoint, 128, "checkpoint");
        this.connections = connections;
        this.network = network;
        this.checkpoint = checkpoint;
    }

    public static Properties readConfig(String filename) throws Exception {
        if (filename == null || filename.trim().isEmpty()) {
            throw new IllegalStateException("DIOXIDE_TX_COORDINATOR_CONFIG must be configured; unsafe allocation is disabled");
        }
        Properties p = new Properties();
        try (InputStream in = Files.newInputStream(Paths.get(filename))) { p.load(in); }
        return p;
    }

    public static JdbcTransactionCoordinator fromProperties(Properties p) throws Exception {
        return fromProperties(p, Thread.currentThread().getContextClassLoader());
    }

    public static JdbcTransactionCoordinator fromProperties(Properties p, ClassLoader pluginLoader) throws Exception {
        final String url = required(p, "jdbcUrl");
        final Properties credentials = new Properties();
        credentials.setProperty("user", required(p, "user"));
        credentials.setProperty("password", new String(Files.readAllBytes(Paths.get(
                required(p, "passwordFile"))), StandardCharsets.UTF_8).trim());
        // DriverManager's caller class-loader filtering is unsuitable for PF4J plugin drivers.
        final Driver driver = (Driver) Class.forName("com.mysql.cj.jdbc.Driver", true, pluginLoader).newInstance();
        return new JdbcTransactionCoordinator(() -> {
            Connection c = driver.connect(url, credentials);
            if (c == null) { throw new SQLException("unsupported transaction coordinator JDBC URL"); }
            return c;
        }, required(p, "networkId"), required(p, "checkpointHash"));
    }

    public static String required(Properties p, String name) {
        String value = p.getProperty(name);
        if (value == null || value.trim().isEmpty()) { throw new IllegalArgumentException("missing coordinator setting: " + name); }
        return value.trim();
    }

    public static String sha256(byte[] input) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(input);
            StringBuilder out = new StringBuilder(64);
            for (byte b : bytes) { out.append(String.format("%02x", b & 255)); }
            return out.toString();
        } catch (Exception e) { throw new IllegalStateException(e); }
    }

    public static long nextIsn(long node, long persisted) {
        if (node < 0 || node > MAX_ISN || persisted < 0 || persisted > MAX_ISN) {
            throw new IllegalStateException("ISN exhausted or invalid; refusing wraparound");
        }
        return Math.max(node, persisted);
    }

    private static void require(String value, int limit, String field) {
        if (value == null || value.isEmpty() || value.length() > limit || !value.matches("[\\x21-\\x7e]+")) {
            throw new IllegalArgumentException("invalid coordinator " + field);
        }
    }

    private String lockName(String kind, String account) {
        return sha256((kind + "|" + network + "|" + account).getBytes(StandardCharsets.UTF_8));
    }

    public static String normalizeAccount(String account) {
        if (account != null && account.toLowerCase(java.util.Locale.ROOT).endsWith(":ed25519")) {
            return account.substring(0, account.length() - 8).toLowerCase(java.util.Locale.ROOT);
        }
        return account;
    }

    private static void acquire(Connection c, String name) throws SQLException {
        try (PreparedStatement s = c.prepareStatement("SELECT GET_LOCK(?, 20)")) {
            s.setString(1, name);
            try (ResultSet r = s.executeQuery()) {
                if (!r.next() || r.getInt(1) != 1 || r.wasNull()) { throw new SQLException("coordinator lock timeout"); }
            }
        }
    }

    private static void release(Connection c, String name) {
        try (PreparedStatement s = c.prepareStatement("SELECT RELEASE_LOCK(?)")) {
            s.setString(1, name); s.executeQuery().close();
        } catch (SQLException ignored) { /* Closing the connection also releases its named locks. */ }
    }

    /** Used only around the legacy shared SDP query mailbox, never normal confirmation polling. */
    public <T> T withQueryLock(String contractAndAccount, Callable<T> query) throws Exception {
        String name = lockName("query", contractAndAccount);
        try (Connection c = connections.open()) {
            acquire(c, name);
            try { return query.call(); } finally { release(c, name); }
        }
    }

    public String submit(String operationId, String account, byte[] payload, Transport transport) throws Exception {
        account = normalizeAccount(account);
        require(operationId, 191, "operationId");
        require(account, 160, "account");
        String name = lockName("submission", account);
        String fingerprint = sha256(payload);
        try (Connection c = connections.open()) {
            acquire(c, name);
            try {
                if (!checkpoint.equals(transport.checkpoint())) {
                    throw new IllegalStateException("Dioxide network checkpoint changed; submission disabled");
                }
                c.setAutoCommit(false);
                byte[] signed;
                try {
                    try (PreparedStatement s = c.prepareStatement(
                            "INSERT INTO bridge_tx_account(network_id,account,checkpoint_hash,next_isn) VALUES(?,?,?,0) " +
                            "ON DUPLICATE KEY UPDATE account=VALUES(account)")) {
                        s.setString(1, network); s.setString(2, account); s.setString(3, checkpoint); s.executeUpdate();
                    }
                    long persisted;
                    long observed;
                    try (PreparedStatement s = c.prepareStatement(
                            "SELECT checkpoint_hash,next_isn,observed_isn FROM bridge_tx_account WHERE network_id=? AND account=? FOR UPDATE")) {
                        s.setString(1, network); s.setString(2, account);
                        try (ResultSet r = s.executeQuery()) {
                            if (!r.next() || !checkpoint.equals(r.getString(1))) { throw new IllegalStateException("coordinator network mismatch"); }
                            persisted = r.getLong(2);
                            observed = r.getLong(3);
                        }
                    }
                    try (PreparedStatement s = c.prepareStatement(
                            "SELECT account,payload_hash,signed_tx,tx_hash FROM bridge_tx_submission WHERE network_id=? AND operation_id=? FOR UPDATE")) {
                        s.setString(1, network); s.setString(2, operationId);
                        try (ResultSet r = s.executeQuery()) {
                            if (r.next()) {
                                if (!account.equals(r.getString(1)) || !fingerprint.equals(r.getString(2))) {
                                    throw new IllegalStateException("submission identity reused with different account or payload");
                                }
                                signed = r.getBytes(3);
                                String hash = r.getString(4);
                                c.commit(); c.setAutoCommit(true);
                                if (hash != null && !hash.isEmpty()) { return hash; }
                                return broadcast(c, operationId, signed, transport);
                            }
                        }
                    }
                    long nodeIsn = transport.currentIsn(account);
                    if (nodeIsn < observed) {
                        throw new IllegalStateException("node ISN regressed; reconcile network state before new submission");
                    }
                    long isn = nextIsn(nodeIsn, persisted);
                    signed = transport.composeAndSign(isn);
                    if (signed == null || signed.length == 0) { throw new IllegalStateException("empty signed transaction"); }
                    try (PreparedStatement s = c.prepareStatement(
                            "INSERT INTO bridge_tx_submission(network_id,operation_id,account,isn,payload_hash,signed_tx,state) VALUES(?,?,?,?,?,?,'SIGNED')")) {
                        s.setString(1, network); s.setString(2, operationId); s.setString(3, account);
                        s.setLong(4, isn); s.setString(5, fingerprint); s.setBytes(6, signed); s.executeUpdate();
                    }
                    try (PreparedStatement s = c.prepareStatement(
                            "UPDATE bridge_tx_account SET next_isn=?,observed_isn=? WHERE network_id=? AND account=?")) {
                        s.setLong(1, isn + 1); s.setLong(2, nodeIsn); s.setString(3, network); s.setString(4, account); s.executeUpdate();
                    }
                    c.commit(); // Never move this below broadcast.
                } catch (Exception e) {
                    try { if (!c.getAutoCommit()) { c.rollback(); } } catch (SQLException rollback) { e.addSuppressed(rollback); }
                    throw e;
                } finally { c.setAutoCommit(true); }
                return broadcast(c, operationId, signed, transport);
            } finally { release(c, name); }
        }
    }

    private String broadcast(Connection c, String operationId, byte[] signed, Transport transport) throws Exception {
        try {
            String hash = transport.broadcast(signed);
            if (hash == null || hash.trim().isEmpty()) { throw new IllegalStateException("broadcast returned no hash"); }
            try (PreparedStatement s = c.prepareStatement(
                    "UPDATE bridge_tx_submission SET tx_hash=?,state='BROADCAST',last_error=NULL WHERE network_id=? AND operation_id=?")) {
                s.setString(1, hash); s.setString(2, network); s.setString(3, operationId); s.executeUpdate();
            }
            return hash;
        } catch (Exception e) {
            try (PreparedStatement s = c.prepareStatement(
                    "UPDATE bridge_tx_submission SET state='UNKNOWN',last_error=? WHERE network_id=? AND operation_id=? AND tx_hash IS NULL")) {
                s.setString(1, e.getClass().getSimpleName()); s.setString(2, network); s.setString(3, operationId); s.executeUpdate();
            } catch (SQLException ignored) { /* SIGNED is also recoverable using the exact stored bytes. */ }
            throw e;
        }
    }

    public void recordOutcome(String hash, boolean success) throws SQLException {
        try (Connection c = connections.open(); PreparedStatement s = c.prepareStatement(
                "UPDATE bridge_tx_submission SET state=? WHERE network_id=? AND tx_hash=?")) {
            s.setString(1, success ? "FINALIZED" : "FAILED"); s.setString(2, network); s.setString(3, hash); s.executeUpdate();
        }
    }
}

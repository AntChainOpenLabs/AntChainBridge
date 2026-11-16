package com.alipay.antchain.bridge.plugins.lib.transactions;

import java.io.InputStream;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.sql.*;
import java.util.Locale;
import java.util.Properties;
import java.util.concurrent.Callable;

/** Durable account-scoped Dioxide ISN coordination shared by all writers. */
public final class JdbcTransactionCoordinator {
    public static final int SCHEMA_VERSION = 2;
    public static final long MAX_ISN = 0xffff_ffffL;
    private static final String COMPONENT = "dioxide-coordinator";

    public interface Transport {
        String checkpoint() throws Exception;
        long currentIsn(String account) throws Exception;
        byte[] composeAndSign(long isn) throws Exception;
        default String transactionHash(byte[] signed) { return dioxideTransactionHash(signed); }
        String broadcast(byte[] signed) throws Exception;
    }

    /** A valid Dioxide RPC response that explicitly rejected tx.send. */
    public static final class NodeRejectedException extends Exception {
        private final int errorCode;

        public NodeRejectedException(int errorCode, String message) {
            super(message);
            this.errorCode = errorCode;
        }

        public int getErrorCode() { return errorCode; }
    }

    /** Another operation owns the account's current node ISN. */
    public static final class AccountBlockedException extends Exception {
        public AccountBlockedException(String message) { super(message); }
    }

    public static final class ReconciliationRequiredException extends IllegalStateException {
        public ReconciliationRequiredException(String message) { super(message); }
    }

    public interface Connections { Connection open() throws SQLException; }

    private final Connections connections;
    private final String network;
    private final String checkpoint;
    private final long acceptanceWaitMillis;
    private final long acceptancePollMillis;

    public JdbcTransactionCoordinator(Connections connections, String network, String checkpoint) {
        this(connections, network, checkpoint, 20_000L, 500L);
    }

    public JdbcTransactionCoordinator(Connections connections, String network, String checkpoint,
                                      long acceptanceWaitMillis, long acceptancePollMillis) {
        require(network, 96, "network");
        require(checkpoint, 128, "checkpoint");
        if (acceptanceWaitMillis < 0 || acceptancePollMillis <= 0) {
            throw new IllegalArgumentException("invalid coordinator acceptance polling settings");
        }
        this.connections = connections;
        this.network = network;
        this.checkpoint = checkpoint;
        this.acceptanceWaitMillis = acceptanceWaitMillis;
        this.acceptancePollMillis = acceptancePollMillis;
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
        final Driver driver = (Driver) Class.forName("com.mysql.cj.jdbc.Driver", true, pluginLoader).newInstance();
        Connections connections = () -> {
            Connection c = driver.connect(url, credentials);
            if (c == null) { throw new SQLException("unsupported transaction coordinator JDBC URL"); }
            return c;
        };
        return new JdbcTransactionCoordinator(
                connections,
                required(p, "networkId"),
                required(p, "checkpointHash"),
                optionalLong(p, "acceptanceWaitMillis", 20_000L),
                optionalLong(p, "acceptancePollMillis", 500L)
        );
    }

    private static long optionalLong(Properties p, String name, long defaultValue) {
        String value = p.getProperty(name);
        return value == null || value.trim().isEmpty() ? defaultValue : Long.parseLong(value.trim());
    }

    public static String required(Properties p, String name) {
        String value = p.getProperty(name);
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("missing coordinator setting: " + name);
        }
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

    /** Dioxide renders the SHA-256 transaction digest with its Crockford base32 alphabet. */
    public static String dioxideTransactionHash(byte[] signedTransaction) {
        if (signedTransaction == null || signedTransaction.length == 0) {
            throw new IllegalArgumentException("empty signed transaction");
        }
        final byte[] digest;
        try {
            digest = MessageDigest.getInstance("SHA-256").digest(signedTransaction);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        final char[] alphabet = "0123456789abcdefghjkmnpqrstvwxyz".toCharArray();
        StringBuilder out = new StringBuilder((digest.length * 8 + 4) / 5);
        for (int group = 0; group * 5 < digest.length * 8; group++) {
            int value = 0;
            for (int offset = 0; offset < 5; offset++) {
                int bit = group * 5 + offset;
                value <<= 1;
                if (bit < digest.length * 8) {
                    value |= (digest[bit / 8] >>> (7 - bit % 8)) & 1;
                }
            }
            out.append(alphabet[value]);
        }
        return out.toString();
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
        if (account != null && account.toLowerCase(Locale.ROOT).endsWith(":ed25519")) {
            return account.substring(0, account.length() - 8).toLowerCase(Locale.ROOT);
        }
        return account;
    }

    private static void acquire(Connection c, String name) throws SQLException {
        try (PreparedStatement s = c.prepareStatement("SELECT GET_LOCK(?, 20)")) {
            s.setString(1, name);
            try (ResultSet r = s.executeQuery()) {
                if (!r.next() || r.getInt(1) != 1 || r.wasNull()) {
                    throw new SQLException("coordinator lock timeout");
                }
            }
        }
    }

    private static void release(Connection c, String name) {
        try (PreparedStatement s = c.prepareStatement("SELECT RELEASE_LOCK(?)")) {
            s.setString(1, name);
            s.executeQuery().close();
        } catch (SQLException ignored) {
            // Closing the connection also releases its named locks.
        }
    }

    private static void verifySchema(Connection c) throws SQLException {
        try (PreparedStatement s = c.prepareStatement(
                "SELECT schema_version FROM bridge_tx_schema_version WHERE component=?")) {
            s.setString(1, COMPONENT);
            try (ResultSet r = s.executeQuery()) {
                if (!r.next() || r.getInt(1) != SCHEMA_VERSION) {
                    throw new SQLException("Dioxide coordinator schema v" + SCHEMA_VERSION + " is required");
                }
            }
        } catch (SQLSyntaxErrorException e) {
            throw new SQLException("Dioxide coordinator schema v" + SCHEMA_VERSION + " is not installed", e);
        }
    }

    /** Used only around the legacy shared SDP query mailbox. */
    public <T> T withQueryLock(String contractAndAccount, Callable<T> query) throws Exception {
        String name = lockName("query", contractAndAccount);
        try (Connection c = connections.open()) {
            verifySchema(c);
            acquire(c, name);
            try { return query.call(); } finally { release(c, name); }
        }
    }

    private static final class AccountRow {
        String checkpoint;
        long observed;
        String state;
        String activeOperation;
        Integer activeAttempt;
        Long activeIsn;
    }

    private static final class SubmissionRow {
        String account;
        String payloadHash;
        int attemptNo;
    }

    private static final class AttemptRow {
        int attemptNo;
        long isn;
        byte[] signed;
        String hash;
        String state;
    }

    public String submit(String operationId, String account, byte[] payload, Transport transport) throws Exception {
        account = normalizeAccount(account);
        require(operationId, 191, "operationId");
        require(account, 160, "account");
        if (payload == null) { throw new IllegalArgumentException("null coordinator payload"); }
        final String normalizedAccount = account;
        final String name = lockName("submission", account);
        final String fingerprint = sha256(payload);

        try (Connection c = connections.open()) {
            verifySchema(c);
            acquire(c, name);
            try {
                if (!checkpoint.equals(transport.checkpoint())) {
                    throw new IllegalStateException("Dioxide network checkpoint changed; submission disabled");
                }

                c.setAutoCommit(false);
                AttemptRow attemptToBroadcast;
                try {
                    insertAccount(c, normalizedAccount);
                    AccountRow accountRow = loadAccount(c, normalizedAccount);
                    if (!checkpoint.equals(accountRow.checkpoint)) {
                        throw new IllegalStateException("coordinator network mismatch");
                    }
                    if ("RECONCILE_REQUIRED".equals(accountRow.state)) {
                        throw new IllegalStateException("Dioxide account requires reconciliation before submission");
                    }

                    SubmissionRow submission = loadSubmission(c, operationId);
                    validateIdentity(submission, normalizedAccount, fingerprint);

                    long nodeIsn = checkedIsn(transport.currentIsn(normalizedAccount));
                    reconcileActive(c, normalizedAccount, accountRow, nodeIsn);
                    accountRow = loadAccount(c, normalizedAccount);
                    submission = loadSubmission(c, operationId);
                    validateIdentity(submission, normalizedAccount, fingerprint);

                    if (accountRow.activeOperation != null
                            && !operationId.equals(accountRow.activeOperation)) {
                        throw new AccountBlockedException("Dioxide account ISN " + accountRow.activeIsn
                                + " is owned by operation " + accountRow.activeOperation);
                    }

                    if (submission != null) {
                        AttemptRow latest = loadAttempt(c, operationId, submission.attemptNo);
                        if (latest == null) {
                            throw new IllegalStateException("coordinator submission has no attempt journal");
                        }
                        if (accountRow.activeOperation != null) {
                            if (isUncertain(latest.state)) {
                                c.commit();
                                c.setAutoCommit(true);
                                return broadcast(c, normalizedAccount, operationId, latest, transport);
                            }
                            if (latest.hash != null && !isRetryableTerminal(latest.state)) {
                                c.commit();
                                return latest.hash;
                            }
                        } else if (!isRetryableTerminal(latest.state)) {
                            c.commit();
                            if (latest.hash == null) {
                                throw new IllegalStateException("accepted Dioxide submission has no transaction hash");
                            }
                            return latest.hash;
                        }
                    }

                    int attemptNo = submission == null ? 1 : submission.attemptNo + 1;
                    if (nodeIsn > MAX_ISN) {
                        throw new IllegalStateException("ISN exhausted or invalid; refusing wraparound");
                    }
                    byte[] signed = transport.composeAndSign(nodeIsn);
                    if (signed == null || signed.length == 0) {
                        throw new IllegalStateException("empty signed transaction");
                    }
                    String expectedHash = transport.transactionHash(signed);
                    if (expectedHash == null || expectedHash.trim().isEmpty()) {
                        throw new IllegalStateException("signed transaction has no deterministic hash");
                    }
                    insertAttempt(c, operationId, attemptNo, normalizedAccount, nodeIsn, fingerprint, signed, expectedHash);
                    upsertSubmission(c, operationId, normalizedAccount, nodeIsn, fingerprint, signed, expectedHash, attemptNo);
                    reserveAccount(c, normalizedAccount, operationId, attemptNo, nodeIsn);
                    c.commit(); // Signed bytes must be durable before broadcast.

                    attemptToBroadcast = new AttemptRow();
                    attemptToBroadcast.attemptNo = attemptNo;
                    attemptToBroadcast.isn = nodeIsn;
                    attemptToBroadcast.signed = signed;
                    attemptToBroadcast.hash = expectedHash;
                    attemptToBroadcast.state = "SIGNED";
                } catch (ReconciliationRequiredException e) {
                    try { if (!c.getAutoCommit()) { c.commit(); } } catch (SQLException commit) { e.addSuppressed(commit); }
                    throw e;
                } catch (Exception e) {
                    try { if (!c.getAutoCommit()) { c.rollback(); } } catch (SQLException rollback) { e.addSuppressed(rollback); }
                    throw e;
                } finally {
                    c.setAutoCommit(true);
                }
                return broadcast(c, normalizedAccount, operationId, attemptToBroadcast, transport);
            } finally {
                release(c, name);
            }
        }
    }

    private static void validateIdentity(SubmissionRow row, String account, String fingerprint) {
        if (row != null && (!account.equals(row.account) || !fingerprint.equals(row.payloadHash))) {
            throw new IllegalStateException("submission identity reused with different account or payload");
        }
    }

    private void insertAccount(Connection c, String account) throws SQLException {
        try (PreparedStatement s = c.prepareStatement(
                "INSERT INTO bridge_tx_account(network_id,account,checkpoint_hash,next_isn,observed_isn) "
                        + "VALUES(?,?,?,0,0) ON DUPLICATE KEY UPDATE account=VALUES(account)")) {
            s.setString(1, network);
            s.setString(2, account);
            s.setString(3, checkpoint);
            s.executeUpdate();
        }
    }

    private AccountRow loadAccount(Connection c, String account) throws SQLException {
        try (PreparedStatement s = c.prepareStatement(
                "SELECT checkpoint_hash,observed_isn,allocation_state,active_operation_id,active_attempt_no,active_isn "
                        + "FROM bridge_tx_account WHERE network_id=? AND account=? FOR UPDATE")) {
            s.setString(1, network);
            s.setString(2, account);
            try (ResultSet r = s.executeQuery()) {
                if (!r.next()) { throw new SQLException("coordinator account row missing"); }
                AccountRow row = new AccountRow();
                row.checkpoint = r.getString(1);
                row.observed = r.getLong(2);
                row.state = r.getString(3);
                row.activeOperation = r.getString(4);
                int attempt = r.getInt(5);
                row.activeAttempt = r.wasNull() ? null : attempt;
                long isn = r.getLong(6);
                row.activeIsn = r.wasNull() ? null : isn;
                return row;
            }
        }
    }

    private SubmissionRow loadSubmission(Connection c, String operationId) throws SQLException {
        try (PreparedStatement s = c.prepareStatement(
                "SELECT account,payload_hash,active_attempt_no FROM bridge_tx_submission "
                        + "WHERE network_id=? AND operation_id=? FOR UPDATE")) {
            s.setString(1, network);
            s.setString(2, operationId);
            try (ResultSet r = s.executeQuery()) {
                if (!r.next()) { return null; }
                SubmissionRow row = new SubmissionRow();
                row.account = r.getString(1);
                row.payloadHash = r.getString(2);
                row.attemptNo = r.getInt(3);
                return row;
            }
        }
    }

    private AttemptRow loadAttempt(Connection c, String operationId, Integer attemptNo) throws SQLException {
        if (attemptNo == null) { return null; }
        try (PreparedStatement s = c.prepareStatement(
                "SELECT attempt_no,isn,signed_tx,tx_hash,state FROM bridge_tx_attempt "
                        + "WHERE network_id=? AND operation_id=? AND attempt_no=? FOR UPDATE")) {
            s.setString(1, network);
            s.setString(2, operationId);
            s.setInt(3, attemptNo);
            try (ResultSet r = s.executeQuery()) {
                if (!r.next()) { return null; }
                AttemptRow row = new AttemptRow();
                row.attemptNo = r.getInt(1);
                row.isn = r.getLong(2);
                row.signed = r.getBytes(3);
                row.hash = r.getString(4);
                row.state = r.getString(5);
                return row;
            }
        }
    }

    private void reconcileActive(Connection c, String account, AccountRow row, long nodeIsn) throws SQLException {
        if (row.activeOperation == null) {
            if (nodeIsn < row.observed) {
                markReconcileRequired(c, account, "node ISN regressed from " + row.observed + " to " + nodeIsn);
                throw new ReconciliationRequiredException("node ISN regressed; reconcile network state before new submission");
            }
            updateObserved(c, account, nodeIsn);
            return;
        }
        if (row.activeIsn == null || row.activeAttempt == null) {
            markReconcileRequired(c, account, "incomplete active reservation");
            throw new ReconciliationRequiredException("incomplete Dioxide account reservation");
        }
        if (nodeIsn < row.activeIsn) {
            markReconcileRequired(c, account, "node ISN regressed below active reservation");
            throw new ReconciliationRequiredException("node ISN regressed below active Dioxide reservation");
        }
        if (nodeIsn == row.activeIsn) {
            updateObserved(c, account, nodeIsn);
            return;
        }
        try (PreparedStatement s = c.prepareStatement(
                "UPDATE bridge_tx_attempt SET state=CASE WHEN state IN ('SIGNED','BROADCAST','UNKNOWN') "
                        + "THEN 'ACCEPTED' ELSE state END WHERE network_id=? AND operation_id=? AND attempt_no=?")) {
            s.setString(1, network);
            s.setString(2, row.activeOperation);
            s.setInt(3, row.activeAttempt);
            s.executeUpdate();
        }
        try (PreparedStatement s = c.prepareStatement(
                "UPDATE bridge_tx_submission SET state=CASE WHEN state IN ('SIGNED','BROADCAST','UNKNOWN') "
                        + "THEN 'ACCEPTED' ELSE state END WHERE network_id=? AND operation_id=?")) {
            s.setString(1, network);
            s.setString(2, row.activeOperation);
            s.executeUpdate();
        }
        try (PreparedStatement s = c.prepareStatement(
                "UPDATE bridge_tx_account SET next_isn=?,observed_isn=?,allocation_state='READY',"
                        + "active_operation_id=NULL,active_attempt_no=NULL,active_isn=NULL,last_error=NULL "
                        + "WHERE network_id=? AND account=?")) {
            s.setLong(1, nodeIsn);
            s.setLong(2, nodeIsn);
            s.setString(3, network);
            s.setString(4, account);
            s.executeUpdate();
        }
    }

    private void updateObserved(Connection c, String account, long nodeIsn) throws SQLException {
        try (PreparedStatement s = c.prepareStatement(
                "UPDATE bridge_tx_account SET next_isn=?,observed_isn=? WHERE network_id=? AND account=?")) {
            s.setLong(1, nodeIsn);
            s.setLong(2, nodeIsn);
            s.setString(3, network);
            s.setString(4, account);
            s.executeUpdate();
        }
    }

    private void markReconcileRequired(Connection c, String account, String error) throws SQLException {
        try (PreparedStatement s = c.prepareStatement(
                "UPDATE bridge_tx_account SET allocation_state='RECONCILE_REQUIRED',last_error=? "
                        + "WHERE network_id=? AND account=?")) {
            s.setString(1, truncate(error, 512));
            s.setString(2, network);
            s.setString(3, account);
            s.executeUpdate();
        }
    }

    private void insertAttempt(Connection c, String operationId, int attemptNo, String account,
                               long isn, String payloadHash, byte[] signed, String transactionHash) throws SQLException {
        try (PreparedStatement s = c.prepareStatement(
                "INSERT INTO bridge_tx_attempt(network_id,operation_id,attempt_no,account,isn,payload_hash,signed_tx,tx_hash,state) "
                        + "VALUES(?,?,?,?,?,?,?,?,'SIGNED')")) {
            s.setString(1, network);
            s.setString(2, operationId);
            s.setInt(3, attemptNo);
            s.setString(4, account);
            s.setLong(5, isn);
            s.setString(6, payloadHash);
            s.setBytes(7, signed);
            s.setString(8, transactionHash);
            s.executeUpdate();
        }
    }

    private void upsertSubmission(Connection c, String operationId, String account, long isn,
                                  String payloadHash, byte[] signed, String transactionHash, int attemptNo) throws SQLException {
        try (PreparedStatement s = c.prepareStatement(
                "INSERT INTO bridge_tx_submission(network_id,operation_id,account,isn,payload_hash,signed_tx,tx_hash,state,active_attempt_no) "
                        + "VALUES(?,?,?,?,?,?,?,'SIGNED',?) ON DUPLICATE KEY UPDATE isn=VALUES(isn),"
                        + "signed_tx=VALUES(signed_tx),tx_hash=VALUES(tx_hash),state='SIGNED',active_attempt_no=VALUES(active_attempt_no),last_error=NULL")) {
            s.setString(1, network);
            s.setString(2, operationId);
            s.setString(3, account);
            s.setLong(4, isn);
            s.setString(5, payloadHash);
            s.setBytes(6, signed);
            s.setString(7, transactionHash);
            s.setInt(8, attemptNo);
            s.executeUpdate();
        }
    }

    private void reserveAccount(Connection c, String account, String operationId, int attemptNo, long isn)
            throws SQLException {
        try (PreparedStatement s = c.prepareStatement(
                "UPDATE bridge_tx_account SET next_isn=?,observed_isn=?,allocation_state='BLOCKED',"
                        + "active_operation_id=?,active_attempt_no=?,active_isn=?,last_error=NULL "
                        + "WHERE network_id=? AND account=?")) {
            s.setLong(1, isn);
            s.setLong(2, isn);
            s.setString(3, operationId);
            s.setInt(4, attemptNo);
            s.setLong(5, isn);
            s.setString(6, network);
            s.setString(7, account);
            s.executeUpdate();
        }
    }

    private String broadcast(Connection c, String account, String operationId,
                             AttemptRow attempt, Transport transport) throws Exception {
        final String hash;
        try {
            hash = transport.broadcast(attempt.signed);
            if (hash == null || hash.trim().isEmpty()) {
                throw new IllegalStateException("broadcast returned no hash");
            }
            if (!hash.equals(attempt.hash)) {
                throw new IllegalStateException("node returned a transaction hash different from the signed bytes");
            }
            updateAttemptAfterBroadcast(c, operationId, attempt.attemptNo, hash);
        } catch (NodeRejectedException e) {
            try {
                long nodeIsn = checkedIsn(transport.currentIsn(account));
                if (nodeIsn > attempt.isn) {
                    reconcileAfterObservedAdvance(c, account, nodeIsn);
                    return attempt.hash;
                }
            } catch (ReconciliationRequiredException reconciliation) {
                e.addSuppressed(reconciliation);
                throw reconciliation;
            } catch (Exception checkFailure) {
                e.addSuppressed(checkFailure);
            }
            updateAttemptFailure(c, operationId, attempt.attemptNo, "REJECTED", e.getErrorCode(), e.getMessage());
            throw e;
        } catch (Exception e) {
            updateAttemptFailure(c, operationId, attempt.attemptNo, "UNKNOWN", null, errorName(e));
            throw e;
        }
        try {
            waitForAcceptance(c, account, attempt, transport);
        } catch (ReconciliationRequiredException e) {
            throw e;
        } catch (Exception e) {
            recordAcceptanceCheckError(c, account, e);
        }
        return hash;
    }

    private void reconcileAfterObservedAdvance(Connection c, String account, long nodeIsn) throws Exception {
        c.setAutoCommit(false);
        try {
            AccountRow accountRow = loadAccount(c, account);
            reconcileActive(c, account, accountRow, nodeIsn);
            c.commit();
        } catch (ReconciliationRequiredException e) {
            c.commit();
            throw e;
        } catch (Exception e) {
            c.rollback();
            throw e;
        } finally {
            c.setAutoCommit(true);
        }
    }

    private void recordAcceptanceCheckError(Connection c, String account, Exception error) {
        try (PreparedStatement s = c.prepareStatement(
                "UPDATE bridge_tx_account SET last_error=? WHERE network_id=? AND account=?")) {
            s.setString(1, truncate("acceptance check: " + errorName(error), 512));
            s.setString(2, network);
            s.setString(3, account);
            s.executeUpdate();
        } catch (SQLException ignored) {
            // The active reservation already keeps the account safe.
        }
    }

    private void updateAttemptAfterBroadcast(Connection c, String operationId, int attemptNo, String hash)
            throws SQLException {
        try (PreparedStatement s = c.prepareStatement(
                "UPDATE bridge_tx_attempt SET tx_hash=?,state='BROADCAST',node_error_code=NULL,last_error=NULL "
                        + "WHERE network_id=? AND operation_id=? AND attempt_no=?")) {
            s.setString(1, hash);
            s.setString(2, network);
            s.setString(3, operationId);
            s.setInt(4, attemptNo);
            s.executeUpdate();
        }
        try (PreparedStatement s = c.prepareStatement(
                "UPDATE bridge_tx_submission SET tx_hash=?,state='BROADCAST',last_error=NULL "
                        + "WHERE network_id=? AND operation_id=?")) {
            s.setString(1, hash);
            s.setString(2, network);
            s.setString(3, operationId);
            s.executeUpdate();
        }
    }

    private void updateAttemptFailure(Connection c, String operationId, int attemptNo, String state,
                                      Integer errorCode, String error) {
        try {
            try (PreparedStatement s = c.prepareStatement(
                    "UPDATE bridge_tx_attempt SET state=?,node_error_code=?,last_error=? "
                            + "WHERE network_id=? AND operation_id=? AND attempt_no=?")) {
                s.setString(1, state);
                if (errorCode == null) { s.setNull(2, Types.INTEGER); } else { s.setInt(2, errorCode); }
                s.setString(3, truncate(error, 512));
                s.setString(4, network);
                s.setString(5, operationId);
                s.setInt(6, attemptNo);
                s.executeUpdate();
            }
            try (PreparedStatement s = c.prepareStatement(
                    "UPDATE bridge_tx_submission SET state=?,last_error=? WHERE network_id=? AND operation_id=?")) {
                s.setString(1, state);
                s.setString(2, truncate(error, 512));
                s.setString(3, network);
                s.setString(4, operationId);
                s.executeUpdate();
            }
        } catch (SQLException ignored) {
            // The durable SIGNED row still makes the attempt recoverable.
        }
    }

    private void waitForAcceptance(Connection c, String account, AttemptRow attempt, Transport transport)
            throws Exception {
        long deadline = System.nanoTime() + acceptanceWaitMillis * 1_000_000L;
        do {
            long nodeIsn = checkedIsn(transport.currentIsn(account));
            c.setAutoCommit(false);
            try {
                AccountRow accountRow = loadAccount(c, account);
                reconcileActive(c, account, accountRow, nodeIsn);
                c.commit();
            } catch (ReconciliationRequiredException e) {
                c.commit();
                throw e;
            } catch (Exception e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(true);
            }
            if (nodeIsn > attempt.isn) { return; }
            if (System.nanoTime() >= deadline || acceptanceWaitMillis == 0) { return; }
            try {
                Thread.sleep(Math.min(acceptancePollMillis,
                        Math.max(1L, (deadline - System.nanoTime()) / 1_000_000L)));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new SocketTimeoutException("interrupted while waiting for Dioxide ISN acceptance");
            }
        } while (true);
    }

    public void recordOutcome(String hash, boolean success) throws SQLException {
        String state = success ? "FINALIZED" : "FAILED";
        try (Connection c = connections.open()) {
            verifySchema(c);
            try (PreparedStatement s = c.prepareStatement(
                    "UPDATE bridge_tx_attempt SET state=? WHERE network_id=? AND tx_hash=?")) {
                s.setString(1, state);
                s.setString(2, network);
                s.setString(3, hash);
                s.executeUpdate();
            }
            try (PreparedStatement s = c.prepareStatement(
                    "UPDATE bridge_tx_submission SET state=? WHERE network_id=? AND tx_hash=?")) {
                s.setString(1, state);
                s.setString(2, network);
                s.setString(3, hash);
                s.executeUpdate();
            }
        }
    }

    private static long checkedIsn(long isn) {
        if (isn < 0 || isn > MAX_ISN + 1) {
            throw new IllegalStateException("ISN exhausted or invalid; refusing wraparound");
        }
        return isn;
    }

    private static boolean isUncertain(String state) {
        return "SIGNED".equals(state) || "UNKNOWN".equals(state);
    }

    private static boolean isRetryableTerminal(String state) {
        return "REJECTED".equals(state) || "FAILED".equals(state) || "EXPIRED".equals(state)
                || "FORKED".equals(state) || "ABANDONED".equals(state);
    }

    private static String errorName(Exception e) {
        Throwable cause = e;
        while (cause.getCause() != null && cause.getCause() != cause) { cause = cause.getCause(); }
        String message = cause.getMessage();
        return cause.getClass().getSimpleName() + (message == null ? "" : ": " + message);
    }

    private static String truncate(String value, int limit) {
        if (value == null || value.length() <= limit) { return value; }
        return value.substring(0, limit);
    }
}

package com.alipay.antchain.bridge.plugins.lib.transactions;

import java.nio.ByteBuffer;
import java.sql.DriverManager;

/** Subprocess fixture only: no real node calls or credentials. */
public class CoordinatorProcessProbe {
    public static void main(String[] args) throws Exception {
        Class.forName("com.mysql.cj.jdbc.Driver");
        JdbcTransactionCoordinator coordinator = new JdbcTransactionCoordinator(
                () -> DriverManager.getConnection(args[0], "root", ""), args[1], "fixture");
        for (int i = 0; i < Integer.parseInt(args[3]); i++) {
            if (args.length > 4 && "query".equals(args[4])) {
                final String value = args[2] + "-" + i;
                System.out.println(coordinator.withQueryLock("sdp|account", () -> {
                    try (java.sql.Connection c = DriverManager.getConnection(args[0], "root", "");
                         java.sql.PreparedStatement write = c.prepareStatement("UPDATE coordinator_test_mailbox SET value=? WHERE network_id=?");
                         java.sql.PreparedStatement read = c.prepareStatement("SELECT value FROM coordinator_test_mailbox WHERE network_id=?")) {
                        write.setString(1, value); write.setString(2, args[1]); write.executeUpdate();
                        Thread.sleep(10);
                        read.setString(1, args[1]);
                        try (java.sql.ResultSet r = read.executeQuery()) {
                            if (!r.next() || !value.equals(r.getString(1))) { throw new IllegalStateException("mailbox overwritten"); }
                        }
                    }
                    return value;
                }));
                continue;
            }
            String hash = coordinator.submit(args[2] + "-" + i, "account",
                    new byte[]{1, 2, 3}, new JdbcTransactionCoordinator.Transport() {
                        public String checkpoint() { return "fixture"; }
                        public long currentIsn(String account) throws Exception {
                            try (java.sql.Connection c = DriverManager.getConnection(args[0], "root", "");
                                 java.sql.PreparedStatement s = c.prepareStatement(
                                         "SELECT isn FROM coordinator_test_node WHERE network_id=?")) {
                                s.setString(1, args[1]);
                                try (java.sql.ResultSet r = s.executeQuery()) {
                                    if (!r.next()) { throw new IllegalStateException("test node missing"); }
                                    return r.getLong(1);
                                }
                            }
                        }
                        public byte[] composeAndSign(long isn) {
                            if (args.length > 4 && "crash-before-sign".equals(args[4])) { Runtime.getRuntime().halt(18); }
                            return ByteBuffer.allocate(8).putLong(isn).array();
                        }
                        public String transactionHash(byte[] signed) {
                            return "tx-" + ByteBuffer.wrap(signed).getLong();
                        }
                        public String broadcast(byte[] signed) throws Exception {
                            if (args.length > 4 && "crash".equals(args[4])) { Runtime.getRuntime().halt(17); }
                            long isn = ByteBuffer.wrap(signed).getLong();
                            try (java.sql.Connection c = DriverManager.getConnection(args[0], "root", "")) {
                                c.setAutoCommit(false);
                                try (java.sql.PreparedStatement read = c.prepareStatement(
                                             "SELECT isn FROM coordinator_test_node WHERE network_id=? FOR UPDATE");
                                     java.sql.PreparedStatement update = c.prepareStatement(
                                             "UPDATE coordinator_test_node SET isn=isn+1 WHERE network_id=?")) {
                                    read.setString(1, args[1]);
                                    long current;
                                    try (java.sql.ResultSet r = read.executeQuery()) {
                                        if (!r.next()) { throw new IllegalStateException("test node missing"); }
                                        current = r.getLong(1);
                                    }
                                    if (isn > current) { throw new IllegalStateException("future test ISN"); }
                                    if (isn == current) {
                                        update.setString(1, args[1]);
                                        update.executeUpdate();
                                    }
                                    c.commit();
                                } catch (Exception e) {
                                    c.rollback();
                                    throw e;
                                }
                            }
                            return transactionHash(signed);
                        }
                    });
            System.out.println(hash);
        }
    }
}

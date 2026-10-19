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
                        public long currentIsn(String account) { return 181; }
                        public byte[] composeAndSign(long isn) {
                            if (args.length > 4 && "crash-before-sign".equals(args[4])) { Runtime.getRuntime().halt(18); }
                            return ByteBuffer.allocate(8).putLong(isn).array();
                        }
                        public String broadcast(byte[] signed) {
                            if (args.length > 4 && "crash".equals(args[4])) { Runtime.getRuntime().halt(17); }
                            return "tx-" + ByteBuffer.wrap(signed).getLong();
                        }
                    });
            System.out.println(hash);
        }
    }
}

package com.alipay.antchain.bridge.plugins.lib.transactions;

import org.junit.Assume;
import org.junit.Test;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.fail;

/** Opt-in probe executed while the local Dioxide process is deliberately paused. */
public class JdbcTransactionCoordinatorUnavailableLiveTest {
    @Test public void unavailableNodeDoesNotReachSigningOrBroadcast() throws Exception {
        Assume.assumeTrue(Boolean.getBoolean("dioxide.live.unavailable"));
        String filename = System.getProperty("dioxide.live.coordinator");
        Assume.assumeTrue(filename != null && !filename.isEmpty());
        Properties properties = JdbcTransactionCoordinator.readConfig(filename);
        JdbcTransactionCoordinator coordinator = JdbcTransactionCoordinator.fromProperties(properties);
        AtomicBoolean composed = new AtomicBoolean();
        AtomicBoolean broadcast = new AtomicBoolean();

        try {
            coordinator.submit("live-down-" + UUID.randomUUID(), "local-unavailable-account", new byte[]{1},
                    new JdbcTransactionCoordinator.Transport() {
                        public String checkpoint() { return properties.getProperty("checkpointHash"); }
                        public long currentIsn(String account) throws Exception {
                            HttpURLConnection connection = (HttpURLConnection) new URL(
                                    "http://127.0.0.1:62222/api?req=dx.isn").openConnection();
                            connection.setConnectTimeout(1000);
                            connection.setReadTimeout(1000);
                            connection.setRequestMethod("POST");
                            connection.setRequestProperty("Content-Type", "application/json");
                            connection.setDoOutput(true);
                            try (OutputStream out = connection.getOutputStream()) {
                                out.write(("{\"address\":\"" + account + "\"}").getBytes("UTF-8"));
                            }
                            connection.getInputStream().close();
                            return 0;
                        }
                        public byte[] composeAndSign(long isn) { composed.set(true); return new byte[]{1}; }
                        public String broadcast(byte[] signed) { broadcast.set(true); return "unexpected"; }
                    });
            fail("submission unexpectedly succeeded while the local node was paused");
        } catch (java.io.IOException expected) {
            // Connection timeout/refusal is the expected failure mode.
        }
        assertFalse(composed.get());
        assertFalse(broadcast.get());
    }
}

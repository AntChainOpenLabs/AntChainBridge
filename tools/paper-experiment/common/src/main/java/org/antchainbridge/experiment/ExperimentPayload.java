package org.antchainbridge.experiment;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public final class ExperimentPayload {

    public static final int PAYLOAD_SIZE = 512;

    private ExperimentPayload() {
    }

    public static byte[] create(
            String experimentId,
            String scheme,
            String route,
            int round,
            int sampleIndex
    ) {
        String sampleId = experimentId + "-" + scheme + "-" + route + "-r" + round + "-s" + sampleIndex;
        byte[] prefix = ("ACBEXP1|" + sampleId + "|").getBytes(StandardCharsets.UTF_8);
        if (prefix.length > PAYLOAD_SIZE) {
            throw new IllegalArgumentException("experiment metadata exceeds 512-byte payload");
        }
        byte[] payload = new byte[PAYLOAD_SIZE];
        Arrays.fill(payload, (byte) '0');
        System.arraycopy(prefix, 0, payload, 0, prefix.length);
        return payload;
    }

    public static String sampleId(
            String experimentId,
            String scheme,
            String route,
            int round,
            int sampleIndex
    ) {
        return experimentId + "-" + scheme + "-" + route + "-r" + round + "-s" + sampleIndex;
    }
}

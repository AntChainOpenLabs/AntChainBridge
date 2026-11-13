package com.alipay.antchain.bridge.plugins.ethereum3.conf;

import java.util.Locale;
import java.util.Optional;

import com.alibaba.fastjson.annotation.JSONCreator;
import com.alibaba.fastjson.annotation.JSONField;

public enum Eth2NetworkEnum {

    // Live networks
    MAINNET("mainnet"),
    SEPOLIA("sepolia"),
    LUKSO("lukso"),
    GNOSIS("gnosis"),
    CHIADO("chiado"),
    HOLESKY("holesky"),
    EPHEMERY("ephemery"),
    // Test networks
    MINIMAL("minimal"),
    SWIFT("swift"),
    LESS_SWIFT("less-swift"),

    PRIVATE_NET("private-net");

    private final String configName;

    Eth2NetworkEnum(final String configName) {
        this.configName = configName;
    }

    @JSONField
    public String getConfigName() {
        return configName;
    }

    @JSONCreator
    public static Eth2NetworkEnum fromString(final String networkName) {
        return fromStringLenient(networkName)
                .orElseThrow(() -> new IllegalArgumentException("Unknown network: " + networkName));
    }

    public static Optional<Eth2NetworkEnum> fromStringLenient(final String networkName) {
        final String normalizedNetworkName =
                networkName.strip().toUpperCase(Locale.US).replace("-", "_");
        for (Eth2NetworkEnum value : values()) {
            if (value.name().equals(normalizedNetworkName)) {
                return Optional.of(value);
            }
        }
        return Optional.empty();
    }
}

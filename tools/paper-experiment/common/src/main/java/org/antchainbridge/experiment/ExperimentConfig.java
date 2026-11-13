package org.antchainbridge.experiment;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public final class ExperimentConfig {

    private final Properties properties;

    private ExperimentConfig(Properties properties) {
        this.properties = properties;
    }

    public static ExperimentConfig load(Path path) throws IOException {
        Properties properties = new Properties();
        try (java.io.Reader input = Files.newBufferedReader(path, java.nio.charset.StandardCharsets.UTF_8)) {
            properties.load(input);
        }
        return new ExperimentConfig(properties);
    }

    public String required(String key) {
        String value = properties.getProperty(key);
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("missing required property: " + key);
        }
        return value.trim();
    }

    public String optional(String key, String defaultValue) {
        String value = properties.getProperty(key);
        return value == null ? defaultValue : value.trim();
    }

    public int integer(String key, int defaultValue) {
        return Integer.parseInt(optional(key, Integer.toString(defaultValue)));
    }

    public long longValue(String key, long defaultValue) {
        return Long.parseLong(optional(key, Long.toString(defaultValue)));
    }

    public boolean bool(String key, boolean defaultValue) {
        return Boolean.parseBoolean(optional(key, Boolean.toString(defaultValue)));
    }

    /** A target connection reuses only SDK fields, not source workload metadata. */
    public ExperimentConfig targetConnection() {
        Properties copy = new Properties(); copy.putAll(properties);
        copy.setProperty("chain.config", required("target.chain.config"));
        copy.setProperty("source.app", required("target.app"));
        copy.setProperty("source.product", required("target.product"));
        copy.setProperty("source.blockchain.id", required("target.blockchain.id"));
        copy.setProperty("source.app.mode", optional("target.app.mode", ""));
        return new ExperimentConfig(copy);
    }
}

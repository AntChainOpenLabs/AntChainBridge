package com.alipay.antchain.bridge.relayer.core.service.metrics;

import java.lang.management.ManagementFactory;
import java.time.Clock;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson.JSONObject;
import com.alipay.antchain.bridge.relayer.commons.model.AuthMsgWrapper;
import com.alipay.antchain.bridge.relayer.commons.model.SDPMsgWrapper;
import com.alipay.antchain.bridge.relayer.commons.model.UniformCrosschainPacketContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Records experiment timestamps without changing the Relayer's persistence or delivery semantics.
 * The feature is disabled by default and deliberately has no network or database dependency.
 */
@Component
public class ExperimentMetricsRecorder {

    public static final String SOURCE_ACCEPTED = "SOURCE_ACCEPTED";
    public static final String TARGET_SUBMITTING = "TARGET_SUBMITTING";
    public static final String TARGET_CONFIRMED = "TARGET_CONFIRMED";
    public static final String TARGET_FAILED = "TARGET_FAILED";

    private static final Logger METRIC_LOGGER = LoggerFactory.getLogger("EXPERIMENT_METRIC");

    private final Clock clock;
    private final Consumer<String> eventSink;
    private final String processInstanceId;
    private final ConcurrentMap<String, AtomicInteger> attempts = new ConcurrentHashMap<>();
    private final Set<String> confirmedUcpIds = ConcurrentHashMap.newKeySet();

    @Value("${relayer.experiment_metrics.enabled:false}")
    private boolean enabled;

    public ExperimentMetricsRecorder() {
        this(
                Clock.systemUTC(),
                line -> METRIC_LOGGER.info("EXPERIMENT_METRIC {}", line)
        );
    }

    ExperimentMetricsRecorder(Clock clock, Consumer<String> eventSink) {
        this.clock = clock;
        this.eventSink = eventSink;
        this.processInstanceId = ManagementFactory.getRuntimeMXBean().getName() + "-" + UUID.randomUUID();
    }

    public void recordSourceAccepted(UniformCrosschainPacketContext context) {
        if (!enabled || context == null || context.isFromNetwork()) {
            return;
        }
        JSONObject event = baseEvent(SOURCE_ACCEPTED, clock.millis(), context.getUcpId());
        event.put("sourceProduct", context.getProduct());
        event.put("sourceBlockchainId", context.getBlockchainId());
        event.put("sourceDomain", context.getSrcDomain());
        event.put("sourceTxHashKey", normaliseHashKey(context.getTxHash()));
        event.put("success", true);
        emit(event);
    }

    /** Capture t2 before entering the target-chain plugin. */
    public SubmissionAttempt beginTargetSubmission(String ucpId, SDPMsgWrapper message) {
        if (!enabled) {
            return SubmissionAttempt.disabled();
        }
        int attempt = attempts.computeIfAbsent(safe(ucpId), ignored -> new AtomicInteger()).incrementAndGet();
        return new SubmissionAttempt(clock.millis(), attempt, ucpId, message, true);
    }

    /** Write the already captured t2 after the plugin call so logging is not included in target-chain time. */
    public void recordTargetSubmitting(SubmissionAttempt submission, String targetTxHash, boolean accepted) {
        if (!enabled || submission == null || !submission.enabled) {
            return;
        }
        JSONObject event = eventForMessage(
                TARGET_SUBMITTING,
                submission.eventTimeEpochMs,
                submission.ucpId,
                submission.message
        );
        event.put("targetTxHash", safe(targetTxHash));
        event.put("attempt", submission.attempt);
        event.put("success", accepted);
        emit(event);
    }

    public void recordTargetConfirmed(String ucpId, SDPMsgWrapper message, String targetTxHash) {
        if (!enabled || StrUtil.isEmpty(ucpId) || !confirmedUcpIds.add(ucpId)) {
            return;
        }
        JSONObject event = eventForMessage(TARGET_CONFIRMED, clock.millis(), ucpId, message);
        event.put("targetTxHash", safe(targetTxHash));
        event.put("success", true);
        emit(event);
    }

    public void recordTargetFailed(
            String ucpId,
            SDPMsgWrapper message,
            String targetTxHash,
            boolean timeout,
            String reason
    ) {
        if (!enabled) {
            return;
        }
        JSONObject event = eventForMessage(TARGET_FAILED, clock.millis(), ucpId, message);
        event.put("targetTxHash", safe(targetTxHash));
        event.put("success", false);
        event.put("timeout", timeout);
        event.put("reason", safe(reason));
        emit(event);
    }

    private JSONObject eventForMessage(String name, long timestamp, String ucpId, SDPMsgWrapper message) {
        JSONObject event = baseEvent(name, timestamp, ucpId);
        if (message == null) {
            return event;
        }
        event.put("sourceProduct", message.getSenderBlockchainProduct());
        event.put("sourceBlockchainId", message.getSenderBlockchainId());
        event.put("sourceDomain", message.getSenderBlockchainDomain());
        AuthMsgWrapper auth = message.getAuthMsgWrapper();
        event.put(
                "sourceTxHashKey",
                auth == null ? "" : normaliseHashKey(auth.getLedgerInfo().get(AuthMsgWrapper.AM_TX_ID))
        );
        event.put("targetProduct", message.getReceiverBlockchainProduct());
        event.put("targetBlockchainId", message.getReceiverBlockchainId());
        return event;
    }

    private JSONObject baseEvent(String name, long timestamp, String ucpId) {
        JSONObject event = new JSONObject(true);
        event.put("schemaVersion", 1);
        event.put("event", name);
        event.put("eventTimeEpochMs", timestamp);
        event.put("ucpId", safe(ucpId));
        event.put("processInstanceId", processInstanceId);
        return event;
    }

    private void emit(JSONObject event) {
        try {
            eventSink.accept(event.toJSONString());
        } catch (Throwable ignored) {
            // Experiment telemetry must never alter cross-chain processing.
        }
    }

    static String normaliseHashKey(String value) {
        if (value == null) {
            return "";
        }
        String result = value.trim();
        if (result.startsWith("0x") || result.startsWith("0X")) {
            result = result.substring(2);
        }
        return result.toLowerCase(Locale.ROOT);
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    void setEnabledForTest(boolean enabled) {
        this.enabled = enabled;
    }

    public static final class SubmissionAttempt {
        private final long eventTimeEpochMs;
        private final int attempt;
        private final String ucpId;
        private final SDPMsgWrapper message;
        private final boolean enabled;

        private SubmissionAttempt(
                long eventTimeEpochMs,
                int attempt,
                String ucpId,
                SDPMsgWrapper message,
                boolean enabled
        ) {
            this.eventTimeEpochMs = eventTimeEpochMs;
            this.attempt = attempt;
            this.ucpId = ucpId;
            this.message = message;
            this.enabled = enabled;
        }

        private static SubmissionAttempt disabled() {
            return new SubmissionAttempt(0L, 0, "", null, false);
        }
    }
}

package com.alipay.antchain.bridge.relayer.core.service.metrics;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.alipay.antchain.bridge.relayer.commons.model.AuthMsgWrapper;
import com.alipay.antchain.bridge.relayer.commons.model.SDPMsgWrapper;
import org.junit.Assert;
import org.junit.Test;

public class ExperimentMetricsRecorderTest {

    @Test
    public void disabledRecorderProducesNoEvents() {
        List<String> lines = new ArrayList<>();
        ExperimentMetricsRecorder recorder = recorder(lines, false);

        recorder.recordTargetConfirmed("ucp-1", message(), "0xtarget");

        Assert.assertTrue(lines.isEmpty());
    }

    @Test
    public void capturesSubmissionTimeAndDeduplicatesConfirmation() {
        List<String> lines = new ArrayList<>();
        ExperimentMetricsRecorder recorder = recorder(lines, true);
        SDPMsgWrapper message = message();

        ExperimentMetricsRecorder.SubmissionAttempt first =
                recorder.beginTargetSubmission("ucp-1", message);
        recorder.recordTargetSubmitting(first, "0xABCD", true);
        recorder.recordTargetConfirmed("ucp-1", message, "0xABCD");
        recorder.recordTargetConfirmed("ucp-1", message, "0xABCD");

        Assert.assertEquals(2, lines.size());
        JSONObject submitting = JSON.parseObject(lines.get(0));
        Assert.assertEquals(ExperimentMetricsRecorder.TARGET_SUBMITTING, submitting.getString("event"));
        Assert.assertEquals(1700000000123L, submitting.getLongValue("eventTimeEpochMs"));
        Assert.assertEquals(1, submitting.getIntValue("attempt"));
        Assert.assertEquals("abcd", submitting.getString("targetTxHash").substring(2).toLowerCase());
        Assert.assertEquals("aabb", submitting.getString("sourceTxHashKey"));

        JSONObject confirmed = JSON.parseObject(lines.get(1));
        Assert.assertEquals(ExperimentMetricsRecorder.TARGET_CONFIRMED, confirmed.getString("event"));
        Assert.assertTrue(confirmed.getBooleanValue("success"));
    }

    @Test
    public void normalisesHashKeys() {
        Assert.assertEquals("aabb", ExperimentMetricsRecorder.normaliseHashKey("0XAaBb"));
        Assert.assertEquals("native-hash", ExperimentMetricsRecorder.normaliseHashKey(" Native-Hash "));
        Assert.assertEquals("", ExperimentMetricsRecorder.normaliseHashKey(null));
    }

    private ExperimentMetricsRecorder recorder(List<String> lines, boolean enabled) {
        ExperimentMetricsRecorder recorder = new ExperimentMetricsRecorder(
                Clock.fixed(Instant.ofEpochMilli(1700000000123L), ZoneOffset.UTC),
                lines::add
        );
        recorder.setEnabledForTest(enabled);
        return recorder;
    }

    private SDPMsgWrapper message() {
        AuthMsgWrapper auth = new AuthMsgWrapper();
        auth.setUcpId("ucp-1");
        auth.setProduct("ethereum3");
        auth.setBlockchainId("eth.id");
        auth.setDomain("eth.example");
        auth.addLedgerInfo(AuthMsgWrapper.AM_TX_ID, "0xAaBb");

        SDPMsgWrapper message = new SDPMsgWrapper();
        message.setAuthMsgWrapper(auth);
        message.setReceiverBlockchainProduct("mychain2");
        message.setReceiverBlockchainId("mychain.id");
        return message;
    }
}

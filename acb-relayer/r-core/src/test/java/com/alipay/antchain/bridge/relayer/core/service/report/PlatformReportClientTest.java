package com.alipay.antchain.bridge.relayer.core.service.report;

import java.lang.reflect.Field;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.alipay.antchain.bridge.commons.core.base.CrossChainMessageReceipt;
import com.alipay.antchain.bridge.commons.core.base.SendResponseResult;
import com.alipay.antchain.bridge.relayer.commons.model.SDPMsgCommitResult;
import com.alipay.antchain.bridge.relayer.commons.model.SDPMsgWrapper;
import com.alipay.antchain.bridge.relayer.commons.model.UniformCrosschainPacketContext;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

public class PlatformReportClientTest {

    private static final String BARE_MYCHAIN_HASH =
            "a3a63ec4e8038bad602bdf2926eda5710c00a560599be270d33bca3003133f92";

    @Test
    public void testDisabledReportingDoesNotBuildUcpPayload() throws Exception {
        PlatformReportClient client = new PlatformReportClient();
        CountingUcpReportJsonBuilder builder = new CountingUcpReportJsonBuilder();
        setField(client, "enabled", false);
        setField(client, "ucpReportJsonBuilder", builder);

        client.reportUcp(new UniformCrosschainPacketContext());

        Assert.assertEquals(0, builder.buildCount);
    }

    @Test
    public void testUcpIdIsFirstBodyFieldForInterfacesTwoToFour() throws Exception {
        CapturingRestTemplate restTemplate = new CapturingRestTemplate();
        PlatformReportClient client = new PlatformReportClient();
        setField(client, "enabled", true);
        setField(client, "apiKey", "test-api-key");
        setField(client, "endpoint", "http://platform.example");
        setField(client, "restTemplate", restTemplate);

        client.reportRegulation("ucp-123", "approved", "");

        SDPMsgWrapper message = new SDPMsgWrapper();
        message.setReceiverBlockchainProduct("ethereum");
        message.setReceiverBlockchainId("chain-2");
        SendResponseResult result = new SendResponseResult(
                "0xtx",
                true,
                true,
                "",
                "",
                123456789L,
                new byte[]{0x01, 0x02}
        );
        client.reportTargetChainSubmission("ucp-123", message, result);
        client.reportTargetChainExecution("ucp-123", result, false);

        Assert.assertEquals(3, restTemplate.requests.size());
        assertRequest(
                restTemplate.requests.get(0),
                "/api/cc-relayer/ucps/ucp-123/regulation-status"
        );
        assertRequest(
                restTemplate.requests.get(1),
                "/api/cc-relayer/ucps/ucp-123/target-chain-submission"
        );
        assertRequest(
                restTemplate.requests.get(2),
                "/api/cc-relayer/ucps/ucp-123/target-chain-execution"
        );
    }

    @Test
    public void testTargetHashNormalisationRules() {
        Assert.assertNull(PlatformReportClient.normalizeTargetTxHashForReport(null));
        Assert.assertEquals("", PlatformReportClient.normalizeTargetTxHashForReport(""));
        Assert.assertEquals(
                "0x" + BARE_MYCHAIN_HASH,
                PlatformReportClient.normalizeTargetTxHashForReport(BARE_MYCHAIN_HASH)
        );
        Assert.assertEquals(
                "0x" + BARE_MYCHAIN_HASH,
                PlatformReportClient.normalizeTargetTxHashForReport("0x" + BARE_MYCHAIN_HASH)
        );
        Assert.assertEquals(
                "0x" + BARE_MYCHAIN_HASH,
                PlatformReportClient.normalizeTargetTxHashForReport("0X" + BARE_MYCHAIN_HASH)
        );
        Assert.assertEquals(
                "anjw5kjn2kd9x5sreg1y5rxkj9yfm5vw0bgvb3zt6d88f8qzqkvg",
                PlatformReportClient.normalizeTargetTxHashForReport(
                        "anjw5kjn2kd9x5sreg1y5rxkj9yfm5vw0bgvb3zt6d88f8qzqkvg"
                )
        );
        Assert.assertEquals(
                BARE_MYCHAIN_HASH.substring(1),
                PlatformReportClient.normalizeTargetTxHashForReport(BARE_MYCHAIN_HASH.substring(1))
        );
        Assert.assertEquals(
                BARE_MYCHAIN_HASH + "0",
                PlatformReportClient.normalizeTargetTxHashForReport(BARE_MYCHAIN_HASH + "0")
        );
        Assert.assertEquals(
                BARE_MYCHAIN_HASH.substring(0, 63) + "g",
                PlatformReportClient.normalizeTargetTxHashForReport(BARE_MYCHAIN_HASH.substring(0, 63) + "g")
        );
    }

    @Test
    public void testAllTargetReportPathsNormaliseHashWithoutChangingEvidence() throws Exception {
        CapturingRestTemplate restTemplate = new CapturingRestTemplate();
        PlatformReportClient client = reportingClient(restTemplate);

        SDPMsgWrapper message = new SDPMsgWrapper();
        message.setReceiverBlockchainProduct("mychain0.10");
        message.setReceiverBlockchainId("my02.id");
        SendResponseResult sendResult = new SendResponseResult(
                BARE_MYCHAIN_HASH,
                true,
                false,
                "ERROR_CODE",
                "send failed",
                123456789L,
                new byte[]{0x01, 0x02}
        );
        client.reportTargetChainSubmission("ucp-hash", message, sendResult);
        client.reportTargetChainExecution("ucp-hash", sendResult, true);

        CrossChainMessageReceipt receipt = new CrossChainMessageReceipt();
        receipt.setTxhash(BARE_MYCHAIN_HASH);
        receipt.setConfirmed(true);
        receipt.setSuccessful(false);
        receipt.setErrorMsg("receipt failed");
        receipt.setTxTimestamp(223456789L);
        client.reportTargetChainExecution("ucp-hash", receipt, false);

        SDPMsgCommitResult commitResult = new SDPMsgCommitResult();
        commitResult.setTxHash(BARE_MYCHAIN_HASH);
        commitResult.setConfirmed(true);
        commitResult.setCommitSuccess(false);
        commitResult.setTimeout(true);
        commitResult.setFailReason("commit failed");
        commitResult.setBlockTimestamp(323456789L);
        client.reportTargetChainExecution("ucp-hash", commitResult);

        Assert.assertEquals(4, restTemplate.requests.size());
        JSONObject submission = JSON.parseObject(restTemplate.requests.get(0).body);
        Assert.assertEquals("0x" + BARE_MYCHAIN_HASH, submission.getString("txHash"));
        Assert.assertEquals("0102", submission.getString("rawTx"));
        Assert.assertEquals("1970-01-02T10:17:36.789Z", submission.getString("submittedAt"));

        JSONObject sendExecution = JSON.parseObject(restTemplate.requests.get(1).body);
        Assert.assertEquals("0x" + BARE_MYCHAIN_HASH, sendExecution.getString("txHash"));
        Assert.assertFalse(sendExecution.getBooleanValue("success"));
        Assert.assertTrue(sendExecution.getBooleanValue("timeout"));
        Assert.assertEquals("send failed", sendExecution.getString("errorMessage"));

        JSONObject receiptExecution = JSON.parseObject(restTemplate.requests.get(2).body);
        Assert.assertEquals("0x" + BARE_MYCHAIN_HASH, receiptExecution.getString("txHash"));
        Assert.assertFalse(receiptExecution.getBooleanValue("success"));
        Assert.assertFalse(receiptExecution.getBooleanValue("timeout"));
        Assert.assertEquals("receipt failed", receiptExecution.getString("errorMessage"));

        JSONObject commitExecution = JSON.parseObject(restTemplate.requests.get(3).body);
        Assert.assertEquals("0x" + BARE_MYCHAIN_HASH, commitExecution.getString("txHash"));
        Assert.assertFalse(commitExecution.getBooleanValue("success"));
        Assert.assertTrue(commitExecution.getBooleanValue("timeout"));
        Assert.assertEquals("commit failed", commitExecution.getString("errorMessage"));
    }

    private PlatformReportClient reportingClient(CapturingRestTemplate restTemplate) throws Exception {
        PlatformReportClient client = new PlatformReportClient();
        setField(client, "enabled", true);
        setField(client, "apiKey", "test-api-key");
        setField(client, "endpoint", "http://platform.example");
        setField(client, "restTemplate", restTemplate);
        return client;
    }

    private void assertRequest(CapturedRequest request, String expectedPath) {
        Assert.assertEquals(expectedPath, request.path);
        Assert.assertTrue(request.body.startsWith("{\"ucpId\":\"ucp-123\","));
        JSONObject body = JSON.parseObject(request.body);
        Assert.assertEquals("ucp-123", body.getString("ucpId"));
    }

    private void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static class CapturedRequest {

        private final String path;
        private final String body;

        private CapturedRequest(String path, String body) {
            this.path = path;
            this.body = body;
        }
    }

    private static class CountingUcpReportJsonBuilder extends UcpReportJsonBuilder {

        private int buildCount;

        @Override
        public JSONObject build(UniformCrosschainPacketContext context) {
            buildCount++;
            return new JSONObject(true);
        }
    }

    private static class CapturingRestTemplate extends RestTemplate {

        private final List<CapturedRequest> requests = new ArrayList<>();

        @Override
        @SuppressWarnings("unchecked")
        public <T> ResponseEntity<T> exchange(
                String url,
                HttpMethod method,
                HttpEntity<?> requestEntity,
                Class<T> responseType,
                Object... uriVariables
        ) {
            Assert.assertEquals(HttpMethod.POST, method);
            Assert.assertEquals("test-api-key", requestEntity.getHeaders().getFirst("x-api-key"));
            requests.add(
                    new CapturedRequest(
                            URI.create(url).getPath(),
                            String.valueOf(requestEntity.getBody())
                    )
            );
            return (ResponseEntity<T>) new ResponseEntity<>("{\"code\":0}", HttpStatus.OK);
        }
    }
}

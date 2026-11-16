package com.alipay.antchain.bridge.relayer.core.service.report;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collections;

import cn.hutool.core.util.HexUtil;
import com.alibaba.fastjson.JSONObject;
import com.alipay.antchain.bridge.commons.core.am.AuthMessageV1;
import com.alipay.antchain.bridge.commons.core.am.AuthMessageV2;
import com.alipay.antchain.bridge.commons.core.am.AuthMessageTrustLevelEnum;
import com.alipay.antchain.bridge.commons.core.am.IAuthMessage;
import com.alipay.antchain.bridge.commons.core.base.CrossChainLane;
import com.alipay.antchain.bridge.commons.core.base.CrossChainDomain;
import com.alipay.antchain.bridge.commons.core.base.CrossChainIdentity;
import com.alipay.antchain.bridge.commons.core.base.CrossChainMessage;
import com.alipay.antchain.bridge.commons.core.base.UniformCrosschainPacket;
import com.alipay.antchain.bridge.commons.core.monitor.MonitorMessageV1;
import com.alipay.antchain.bridge.commons.core.ptc.ThirdPartyProof;
import com.alipay.antchain.bridge.commons.core.sdp.AtomicFlagEnum;
import com.alipay.antchain.bridge.commons.core.sdp.ISDPMessage;
import com.alipay.antchain.bridge.commons.core.sdp.SDPMessageFactory;
import com.alipay.antchain.bridge.commons.core.sdp.SDPMessageV1;
import com.alipay.antchain.bridge.commons.core.sdp.TimeoutMeasureEnum;
import com.alipay.antchain.bridge.commons.utils.crypto.SignAlgoEnum;
import com.alipay.antchain.bridge.ptc.committee.types.basic.CommitteeEndorseProof;
import com.alipay.antchain.bridge.ptc.committee.types.basic.CommitteeNodeProof;
import com.alipay.antchain.bridge.relayer.commons.model.UniformCrosschainPacketContext;
import org.junit.Assert;
import org.junit.Test;

public class UcpReportJsonBuilderTest {

    private static final String[] MONITORED_PRODUCTS = {
            "dioxide2", "ethereum3", "mychain2", "fiscobcos3", "MYCHAIN2", "FISCOBCOS3"
    };

    @Test
    public void testBuildMonitoredUcp() {
        MonitorMessageV1 monitorMessage = new MonitorMessageV1();
        monitorMessage.setMonitorType(2);
        monitorMessage.setMonitorMsg("rule-hit");
        monitorMessage.setPayload("business-data".getBytes(StandardCharsets.UTF_8));

        SDPMessageV1 sdpMessage = new SDPMessageV1();
        sdpMessage.setTargetDomain(new CrossChainDomain("target.example"));
        sdpMessage.setTargetIdentity(new CrossChainIdentity(new byte[32]));
        sdpMessage.setSequence(-1);
        sdpMessage.setPayload(monitorMessage.encode());

        AuthMessageV1 authMessage = new AuthMessageV1();
        authMessage.setIdentity(new CrossChainIdentity(new byte[32]));
        authMessage.setUpperProtocol(0);
        authMessage.setPayload(sdpMessage.encode());

        CrossChainMessage crossChainMessage = CrossChainMessage.createCrossChainMessage(
                CrossChainMessage.CrossChainMessageType.AUTH_MSG,
                100L,
                123456789L,
                new byte[]{0x01, 0x02},
                authMessage.encode(),
                new byte[]{0x03},
                new byte[]{0x04},
                new byte[]{0x05, 0x06}
        );
        UniformCrosschainPacketContext context = new UniformCrosschainPacketContext();
        context.setUcpId("ucp-test");
        context.setProduct("dioxide2");
        context.setBlockchainId("test-chain");
        context.setUcp(new UniformCrosschainPacket(new CrossChainDomain("source.example"), crossChainMessage, null));

        for (String product : MONITORED_PRODUCTS) {
            context.setProduct(product);
            JSONObject body = new UcpReportJsonBuilder().build(context);
            JSONObject ucp = body.getJSONObject("ucp");
            JSONObject srcMessage = ucp.getJSONObject("srcMessage");
            JSONObject auth = srcMessage.getJSONObject("message").getJSONObject("authMessage");
            JSONObject sdp = auth.getJSONObject("payload").getJSONObject("sdpMessage");
            Assert.assertTrue("Expected expanded monitor payload for " + product,
                    sdp.get("payload") instanceof JSONObject);
            JSONObject monitor = sdp.getJSONObject("payload").getJSONObject("monitorMessage");

            Assert.assertEquals("ucp-test", body.getString("ucpId"));
            Assert.assertEquals(product, body.getJSONObject("source").getString("product"));
            Assert.assertEquals(HexUtil.encodeHexStr(context.getUcp().encode()), body.getString("rawUcp"));
            Assert.assertEquals(
                    Base64.getEncoder().encodeToString(context.getUcp().encode()),
                    body.getString("rawUcpBase64")
            );
            Assert.assertEquals(1, body.getJSONObject("am").getIntValue("version"));
            Assert.assertEquals("target.example", body.getJSONObject("sdp").getString("targetDomain"));
            Assert.assertEquals("source.example", ucp.getString("srcDomain"));
            Assert.assertEquals("AUTH_MSG", srcMessage.getString("type"));
            Assert.assertEquals("target.example", sdp.getString("targetDomain"));
            Assert.assertEquals(2, monitor.getIntValue("monitorType"));
            Assert.assertEquals("rule-hit", monitor.getString("monitorMsg"));
            Assert.assertEquals("business-data", monitor.getString("payload"));
            Assert.assertEquals("0102", srcMessage.getJSONObject("provableData").getString("blockHash"));
            Assert.assertEquals("03", srcMessage.getJSONObject("provableData").getString("ledgerData"));
            Assert.assertFalse(body.toJSONString().contains("\"rawHex\""));
        }
    }

    @Test
    public void testMalformedMonitorLengthFallsBackWithoutAllocation() {
        byte[] malformedPayload = new byte[68];
        malformedPayload[60] = 0x7f;
        malformedPayload[61] = (byte) 0xff;
        malformedPayload[62] = (byte) 0xff;
        malformedPayload[63] = (byte) 0xff;
        malformedPayload[67] = 1;

        SDPMessageV1 sdpMessage = new SDPMessageV1();
        sdpMessage.setTargetDomain(new CrossChainDomain("target.example"));
        sdpMessage.setTargetIdentity(new CrossChainIdentity(new byte[32]));
        sdpMessage.setSequence(-1);
        sdpMessage.setPayload(malformedPayload);

        AuthMessageV1 authMessage = new AuthMessageV1();
        authMessage.setIdentity(new CrossChainIdentity(new byte[32]));
        authMessage.setUpperProtocol(0);
        authMessage.setPayload(sdpMessage.encode());

        UniformCrosschainPacketContext context = buildContext(createAuthMessage(authMessage));
        for (String product : MONITORED_PRODUCTS) {
            context.setProduct(product);
            Object payload = new UcpReportJsonBuilder().build(context)
                    .getJSONObject("sdp")
                    .get("payload");

            Assert.assertEquals(HexUtil.encodeHexStr(malformedPayload), payload);
        }
    }

    @Test
    public void testUnmonitoredProductTreatsMonitorEnvelopeAsOpaque() {
        MonitorMessageV1 monitorMessage = new MonitorMessageV1();
        monitorMessage.setMonitorType(2);
        monitorMessage.setMonitorMsg("rule-hit");
        monitorMessage.setPayload("business-data".getBytes(StandardCharsets.UTF_8));

        SDPMessageV1 sdpMessage = new SDPMessageV1();
        sdpMessage.setTargetDomain(new CrossChainDomain("target.example"));
        sdpMessage.setTargetIdentity(new CrossChainIdentity(new byte[32]));
        sdpMessage.setSequence(-1);
        sdpMessage.setPayload(monitorMessage.encode());

        AuthMessageV1 authMessage = new AuthMessageV1();
        authMessage.setIdentity(new CrossChainIdentity(new byte[32]));
        authMessage.setUpperProtocol(0);
        authMessage.setPayload(sdpMessage.encode());

        UniformCrosschainPacketContext context = buildContext(createAuthMessage(authMessage));
        for (String product : new String[]{"dioxide", "ethereum2", "mychain", "fiscobcos", "unknown"}) {
            context.setProduct(product);
            Object payload = new UcpReportJsonBuilder().build(context)
                    .getJSONObject("sdp")
                    .get("payload");

            Assert.assertTrue(payload instanceof String);
            Assert.assertFalse(String.valueOf(payload).contains("monitorMessage"));
        }
    }

    @Test
    public void testBuildJsonLedgerDataAndBinaryFallback() {
        CrossChainMessage crossChainMessage = CrossChainMessage.createCrossChainMessage(
                CrossChainMessage.CrossChainMessageType.DEVELOPER_DESIGN,
                101L,
                123456790L,
                new byte[]{0x01},
                "[{\"kind\":\"custom\"}]".getBytes(StandardCharsets.UTF_8),
                "{\"logIndex\":1,\"nested\":{\"ok\":true}}".getBytes(StandardCharsets.UTF_8),
                "{\"receiptIndex\":0,\"proofRelatedNodes\":[\"0x01\"]}".getBytes(StandardCharsets.UTF_8),
                new byte[]{0x02}
        );

        JSONObject body = new UcpReportJsonBuilder().build(buildContext(crossChainMessage));
        JSONObject srcMessage = body.getJSONObject("ucp").getJSONObject("srcMessage");
        JSONObject provableData = srcMessage.getJSONObject("provableData");

        Assert.assertEquals("custom", srcMessage.getJSONArray("message").getJSONObject(0).getString("kind"));
        Assert.assertEquals(1, provableData.getJSONObject("ledgerData").getIntValue("logIndex"));
        Assert.assertTrue(
                provableData.getJSONObject("ledgerData").getJSONObject("nested").getBooleanValue("ok")
        );
        Assert.assertEquals(0, provableData.getJSONObject("proof").getIntValue("receiptIndex"));
        Assert.assertFalse(body.toJSONString().contains("\"rawHex\""));
    }

    @Test
    public void testBuildNormalizesSourceTxHashAsUtf8Hex() {
        String dioxideTxHash = "z4vcnp2r4wn6nyvx4gz851msx5d5yg78ber6qdvfkhxje5fhz770";
        JSONObject dioxideProvableData = buildProvableData(dioxideTxHash.getBytes(StandardCharsets.UTF_8));
        Assert.assertEquals(
                HexUtil.encodeHexStr(dioxideTxHash.getBytes(StandardCharsets.UTF_8)),
                dioxideProvableData.getString("txHash")
        );

        String mychainTxHash = "e0b6d9013195594002730aa7cdba82b2cb1aabd469b8554ea90086c9f6422d90";
        JSONObject mychainProvableData = buildProvableData(HexUtil.decodeHex(mychainTxHash));
        Assert.assertEquals(
                HexUtil.encodeHexStr(mychainTxHash.getBytes(StandardCharsets.UTF_8)),
                mychainProvableData.getString("txHash")
        );
        Assert.assertEquals(
                mychainTxHash,
                new String(HexUtil.decodeHex(mychainProvableData.getString("txHash")), StandardCharsets.UTF_8)
        );
    }

    @Test
    public void testBuildAuthV2WithSdpV2AndV3() {
        assertAuthV2AndSdpVersion(2);
        assertAuthV2AndSdpVersion(3);
    }

    @Test
    public void testBuildThirdPartyProofAndFallbackRawProof() {
        AuthMessageV1 responseAuthMessage = new AuthMessageV1();
        responseAuthMessage.setIdentity(new CrossChainIdentity(new byte[32]));
        responseAuthMessage.setUpperProtocol(9);
        responseAuthMessage.setPayload("committee-response".getBytes(StandardCharsets.UTF_8));

        CommitteeNodeProof nodeProof = CommitteeNodeProof.builder()
                .nodeId("node-1")
                .signAlgo(SignAlgoEnum.KECCAK256_WITH_SECP256K1)
                .signature(new byte[]{0x01, 0x02, 0x03})
                .build();
        CommitteeEndorseProof endorseProof = CommitteeEndorseProof.builder()
                .committeeId("committee-1")
                .sigs(Collections.singletonList(nodeProof))
                .build();

        ThirdPartyProof thirdPartyProof = ThirdPartyProof.create(
                1,
                responseAuthMessage.encode(),
                new CrossChainLane(
                        new CrossChainDomain("source.example"),
                        new CrossChainDomain("target.example"),
                        new CrossChainIdentity(new byte[32]),
                        new CrossChainIdentity(new byte[32])
                )
        );
        thirdPartyProof.setRawProof(endorseProof.encode());

        UniformCrosschainPacketContext context = buildContext(createDeveloperMessage());
        context.getUcp().setTpProof(thirdPartyProof);
        JSONObject tpProof = new UcpReportJsonBuilder().build(context)
                .getJSONObject("ucp")
                .getJSONObject("tpProof");

        Assert.assertEquals(
                "committee-response",
                tpProof.getJSONObject("resp")
                        .getJSONObject("body")
                        .getJSONObject("authMessage")
                        .getString("payload")
        );
        Assert.assertEquals(
                "source.example",
                tpProof.getJSONObject("tpbtaCrossChainLane")
                        .getJSONObject("crossChainChannel")
                        .getString("senderDomain")
        );
        Assert.assertEquals("committee-1", tpProof.getJSONObject("rawProof").getString("committeeId"));
        Assert.assertEquals(
                SignAlgoEnum.KECCAK256_WITH_SECP256K1.getName(),
                tpProof.getJSONObject("rawProof").getJSONArray("sigs").getJSONObject(0).getString("signAlgo")
        );
        Assert.assertEquals(
                "010203",
                tpProof.getJSONObject("rawProof").getJSONArray("sigs").getJSONObject(0).getString("signature")
        );

        thirdPartyProof.setRawProof(new byte[]{0x01, 0x02, 0x03});
        Object fallback = new UcpReportJsonBuilder().build(context)
                .getJSONObject("ucp")
                .getJSONObject("tpProof")
                .get("rawProof");
        Assert.assertEquals("010203", fallback);
    }

    private void assertAuthV2AndSdpVersion(int sdpVersion) {
        byte[] messageId = new byte[32];
        messageId[31] = (byte) sdpVersion;
        ISDPMessage sdpMessage = SDPMessageFactory.createSDPMessage(
                sdpVersion,
                messageId,
                "target-v" + sdpVersion + ".example",
                new byte[32],
                AtomicFlagEnum.ATOMIC_REQUEST,
                sdpVersion == 3 ? TimeoutMeasureEnum.RECEIVER_HEIGHT : TimeoutMeasureEnum.NO_TIMEOUT,
                sdpVersion == 3 ? BigInteger.valueOf(999) : BigInteger.ZERO,
                88L,
                -1,
                "dapp-payload".getBytes(StandardCharsets.UTF_8),
                null
        );
        AuthMessageV2 authMessage = new AuthMessageV2();
        authMessage.setIdentity(new CrossChainIdentity(new byte[32]));
        authMessage.setUpperProtocol(0);
        authMessage.setTrustLevel(AuthMessageTrustLevelEnum.POSITIVE_TRUST);
        authMessage.setPayload(sdpMessage.encode());

        JSONObject body = new UcpReportJsonBuilder().build(
                buildContext(createAuthMessage(authMessage))
        );
        JSONObject auth = body.getJSONObject("ucp")
                .getJSONObject("srcMessage")
                .getJSONObject("message")
                .getJSONObject("authMessage");
        JSONObject sdp = auth.getJSONObject("payload").getJSONObject("sdpMessage");

        Assert.assertEquals(2, auth.getIntValue("version"));
        Assert.assertEquals("POSITIVE_TRUST", auth.getString("trustLevel"));
        Assert.assertEquals(sdpVersion, sdp.getIntValue("version"));
        Assert.assertEquals(HexUtil.encodeHexStr(messageId), sdp.getString("messageId"));
        Assert.assertEquals("dapp-payload", sdp.getString("payload"));
        if (sdpVersion == 3) {
            Assert.assertEquals("RECEIVER_HEIGHT", sdp.getString("timeoutMeasure"));
            Assert.assertEquals("999", sdp.getString("timeout"));
        }
    }

    private CrossChainMessage createAuthMessage(IAuthMessage authMessage) {
        return CrossChainMessage.createCrossChainMessage(
                CrossChainMessage.CrossChainMessageType.AUTH_MSG,
                102L,
                123456791L,
                new byte[]{0x01},
                authMessage.encode(),
                new byte[0],
                new byte[0],
                new byte[]{0x02}
        );
    }

    private CrossChainMessage createDeveloperMessage() {
        return CrossChainMessage.createCrossChainMessage(
                CrossChainMessage.CrossChainMessageType.DEVELOPER_DESIGN,
                103L,
                123456792L,
                new byte[]{0x01},
                new byte[]{0x02},
                new byte[0],
                new byte[0],
                new byte[]{0x03}
        );
    }

    private JSONObject buildProvableData(byte[] txHash) {
        CrossChainMessage crossChainMessage = CrossChainMessage.createCrossChainMessage(
                CrossChainMessage.CrossChainMessageType.DEVELOPER_DESIGN,
                104L,
                123456793L,
                new byte[]{0x01},
                new byte[]{0x02},
                new byte[0],
                new byte[0],
                txHash
        );
        return new UcpReportJsonBuilder().build(buildContext(crossChainMessage))
                .getJSONObject("ucp")
                .getJSONObject("srcMessage")
                .getJSONObject("provableData");
    }

    private UniformCrosschainPacketContext buildContext(CrossChainMessage crossChainMessage) {
        UniformCrosschainPacketContext context = new UniformCrosschainPacketContext();
        context.setUcpId("ucp-test");
        context.setProduct("test-product");
        context.setBlockchainId("test-chain");
        context.setUcp(
                new UniformCrosschainPacket(
                        new CrossChainDomain("source.example"),
                        crossChainMessage,
                        null
                )
        );
        return context;
    }
}

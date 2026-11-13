package com.alipay.antchain.bridge.relayer.core.service.report;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;

import cn.hutool.core.util.HexUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.alipay.antchain.bridge.commons.core.am.AuthMessageFactory;
import com.alipay.antchain.bridge.commons.core.am.AuthMessageV2;
import com.alipay.antchain.bridge.commons.core.am.IAuthMessage;
import com.alipay.antchain.bridge.commons.core.base.CrossChainLane;
import com.alipay.antchain.bridge.commons.core.base.CrossChainMessage;
import com.alipay.antchain.bridge.commons.core.base.ObjectIdentity;
import com.alipay.antchain.bridge.commons.core.base.UniformCrosschainPacket;
import com.alipay.antchain.bridge.commons.core.monitor.IMonitorMessage;
import com.alipay.antchain.bridge.commons.core.monitor.MonitorMessageFactory;
import com.alipay.antchain.bridge.commons.core.ptc.ThirdPartyProof;
import com.alipay.antchain.bridge.commons.core.sdp.ISDPMessage;
import com.alipay.antchain.bridge.commons.core.sdp.SDPMessageFactory;
import com.alipay.antchain.bridge.ptc.committee.types.basic.CommitteeEndorseProof;
import com.alipay.antchain.bridge.ptc.committee.types.basic.CommitteeNodeProof;
import com.alipay.antchain.bridge.relayer.commons.model.UniformCrosschainPacketContext;
import org.springframework.stereotype.Component;

@Component
public class UcpReportJsonBuilder {

    private static final int MAX_MONITOR_MESSAGE_BYTES = 4 * 1024 * 1024;

    public JSONObject build(UniformCrosschainPacketContext context) {
        JSONObject body = new JSONObject(true);
        body.put("ucpId", context.getUcpId());
        byte[] rawUcp = context.getUcp().encode();
        body.put("rawUcpBase64", Base64.getEncoder().encodeToString(rawUcp));
        // Retained for older receivers while rawUcpBase64 is the current API contract.
        body.put("rawUcp", hex(rawUcp));

        JSONObject source = new JSONObject(true);
        source.put("product", context.getProduct());
        source.put("blockchainId", context.getBlockchainId());
        source.put("domain", context.getSrcDomain());
        body.put("source", source);
        JSONObject ucp = buildUcp(context.getUcp(), isMonitorProduct(context.getProduct()));
        body.put("ucp", ucp);
        addCompatibilityMessageFields(body, ucp);
        return body;
    }

    private JSONObject buildUcp(UniformCrosschainPacket ucp, boolean monitorProduct) {
        JSONObject json = new JSONObject(true);
        json.put("version", ucp.getVersion());
        json.put("srcDomain", ucp.getSrcDomain().getDomain());
        json.put("srcMessage", buildCrossChainMessage(ucp.getSrcMessage(), monitorProduct));
        json.put("ptcId", buildObjectIdentity(ucp.getPtcId()));
        json.put("tpProof", buildThirdPartyProof(ucp.getTpProof()));
        return json;
    }

    private JSONObject buildCrossChainMessage(CrossChainMessage message, boolean monitorProduct) {
        JSONObject json = new JSONObject(true);
        json.put("type", message.getType().name());
        json.put("message", buildMessageBody(message, monitorProduct));
        json.put("provableData", buildProvableData(message.getProvableData()));
        return json;
    }

    private Object buildMessageBody(CrossChainMessage message, boolean monitorProduct) {
        if (message.getType() != CrossChainMessage.CrossChainMessageType.AUTH_MSG) {
            return parseOpaqueData(message.getMessage());
        }
        JSONObject authMessage = tryBuildAuthMessage(message.getMessage(), monitorProduct);
        return ObjectUtil.isNull(authMessage) ? parseOpaqueData(message.getMessage()) : authMessage;
    }

    private JSONObject tryBuildAuthMessage(byte[] rawMessage, boolean monitorProduct) {
        try {
            IAuthMessage authMessage = AuthMessageFactory.createAuthMessage(rawMessage);
            JSONObject json = new JSONObject(true);
            json.put("version", authMessage.getVersion());
            json.put("identity", authMessage.getIdentity().toHex());
            json.put("upperProtocol", authMessage.getUpperProtocol());
            if (authMessage instanceof AuthMessageV2) {
                json.put("trustLevel", ((AuthMessageV2) authMessage).getTrustLevel().name());
            }
            json.put("payload", buildAuthPayload(authMessage, monitorProduct));

            JSONObject result = new JSONObject(true);
            result.put("authMessage", json);
            return result;
        } catch (RuntimeException e) {
            return null;
        }
    }

    private Object buildAuthPayload(IAuthMessage authMessage, boolean monitorProduct) {
        if (authMessage.getUpperProtocol() != 0) {
            return parseOpaqueData(authMessage.getPayload());
        }
        try {
            ISDPMessage sdpMessage = SDPMessageFactory.createSDPMessage(authMessage.getPayload());
            JSONObject sdpJson = new JSONObject(true);
            sdpJson.put("version", sdpMessage.getVersion());
            sdpJson.put("targetDomain", sdpMessage.getTargetDomain().getDomain());
            sdpJson.put("targetIdentity", sdpMessage.getTargetIdentity().toHex());
            sdpJson.put("sequence", sdpMessage.getSequence());
            if (sdpMessage.getVersion() > 1) {
                sdpJson.put("messageId", ObjectUtil.isNull(sdpMessage.getMessageId()) ? null : sdpMessage.getMessageId().toHexStr());
                sdpJson.put("nonce", sdpMessage.getNonce());
                sdpJson.put("atomic", sdpMessage.getAtomic());
                sdpJson.put("atomicFlag", sdpMessage.getAtomicFlag().name());
            }
            if (sdpMessage.getVersion() > 2) {
                sdpJson.put("timeoutMeasure", sdpMessage.getTimeoutMeasure().name());
                sdpJson.put("timeout", sdpMessage.getTimeout().toString());
            }
            sdpJson.put("payload", buildSdpPayload(sdpMessage.getPayload(), monitorProduct));

            JSONObject result = new JSONObject(true);
            result.put("sdpMessage", sdpJson);
            return result;
        } catch (RuntimeException e) {
            return parseOpaqueData(authMessage.getPayload());
        }
    }

    private Object buildSdpPayload(byte[] payload, boolean monitorProduct) {
        if (!monitorProduct || !isStructurallyValidMonitorMessage(payload)) {
            return parseOpaqueData(payload);
        }
        try {
            IMonitorMessage monitorMessage = MonitorMessageFactory.createMonitorMessage(payload);
            if (monitorMessage.getMonitorType() < 1 || monitorMessage.getMonitorType() > 4) {
                return parseOpaqueData(payload);
            }
            JSONObject monitorJson = new JSONObject(true);
            monitorJson.put("version", 1);
            monitorJson.put("monitorType", monitorMessage.getMonitorType());
            monitorJson.put("monitorMsg", monitorMessage.getMonitorMsg());
            monitorJson.put("payload", parseOpaqueData(monitorMessage.getPayload()));

            JSONObject result = new JSONObject(true);
            result.put("monitorMessage", monitorJson);
            return result;
        } catch (RuntimeException e) {
            return parseOpaqueData(payload);
        }
    }

    private boolean isMonitorProduct(String product) {
        return "dioxide2".equalsIgnoreCase(product) || "ethereum3".equalsIgnoreCase(product)
                || "mychain2".equalsIgnoreCase(product) || "fiscobcos3".equalsIgnoreCase(product);
    }

    private boolean isStructurallyValidMonitorMessage(byte[] payload) {
        if (ObjectUtil.isNull(payload)
                || payload.length < 68
                || payload.length > MAX_MONITOR_MESSAGE_BYTES) {
            return false;
        }
        int monitorType = readInt(payload, payload.length - 4);
        if (monitorType < 1 || monitorType > 4) {
            return false;
        }
        int offset = payload.length - 4;
        offset = previousVarBytesOffset(payload, offset);
        if (offset < 0) {
            return false;
        }
        offset = previousVarBytesOffset(payload, offset);
        return offset == 0;
    }

    private int previousVarBytesOffset(byte[] payload, int offset) {
        if (offset < 32 || offset > payload.length) {
            return -1;
        }
        int length = readInt(payload, offset - 4);
        if (length < 0 || length > MAX_MONITOR_MESSAGE_BYTES) {
            return -1;
        }
        long paddedLength = ((long) length + 31L) / 32L * 32L;
        long previousOffset = (long) offset - 32L - paddedLength;
        return previousOffset < 0L ? -1 : (int) previousOffset;
    }

    private int readInt(byte[] value, int offset) {
        return ((value[offset] & 0xff) << 24)
                | ((value[offset + 1] & 0xff) << 16)
                | ((value[offset + 2] & 0xff) << 8)
                | (value[offset + 3] & 0xff);
    }

    private void addCompatibilityMessageFields(JSONObject body, JSONObject ucp) {
        JSONObject srcMessage = ucp.getJSONObject("srcMessage");
        if (ObjectUtil.isNull(srcMessage)) {
            return;
        }
        Object messageValue = srcMessage.get("message");
        if (!(messageValue instanceof JSONObject)) {
            return;
        }
        JSONObject message = (JSONObject) messageValue;
        JSONObject authMessage = message.getJSONObject("authMessage");
        if (ObjectUtil.isNull(authMessage)) {
            return;
        }
        body.put("am", authMessage);
        Object authPayloadValue = authMessage.get("payload");
        if (authPayloadValue instanceof JSONObject) {
            JSONObject authPayload = (JSONObject) authPayloadValue;
            JSONObject sdpMessage = authPayload.getJSONObject("sdpMessage");
            if (ObjectUtil.isNotNull(sdpMessage)) {
                body.put("sdp", sdpMessage);
            }
        }
    }

    private JSONObject buildProvableData(CrossChainMessage.ProvableLedgerData data) {
        if (ObjectUtil.isNull(data)) {
            return null;
        }
        JSONObject json = new JSONObject(true);
        json.put("height", ObjectUtil.isNull(data.getHeightVal()) ? null : data.getHeightVal().toString());
        json.put("blockHash", hex(data.getBlockHash()));
        json.put("timestamp", data.getTimestamp());
        json.put("timestampUtc", Instant.ofEpochMilli(data.getTimestamp()).toString());
        json.put("ledgerData", parseOpaqueData(data.getLedgerData()));
        json.put("proof", parseOpaqueData(data.getProof()));
        json.put("txHash", encodeTxHashForReport(data.getTxHash()));
        return json;
    }

    /**
     * The plugin API exposes the transaction hash as bytes, but plugins do not agree on what those bytes mean:
     * chains such as Dioxide store the chain-native hash text as UTF-8, while chains such as Mychain store the
     * decoded binary hash. The platform report contract is UTF-8 HEX, so first recover a chain-native text value
     * and then hex-encode its UTF-8 bytes. This keeps Dioxide output stable and gives binary-hash chains the same
     * wire representation without changing the UCP or plugin-internal hash representation.
     */
    private String encodeTxHashForReport(byte[] value) {
        if (ObjectUtil.isNull(value)) {
            return null;
        }
        String txHash = decodeUtf8(value);
        if (ObjectUtil.isNull(txHash) || !isPrintable(txHash)) {
            txHash = hex(value);
        }
        return hex(txHash.getBytes(StandardCharsets.UTF_8));
    }

    private JSONObject buildObjectIdentity(ObjectIdentity identity) {
        if (ObjectUtil.isNull(identity)) {
            return null;
        }
        JSONObject json = new JSONObject(true);
        json.put("type", identity.getType().name());
        json.put("rawId", hex(identity.getRawId()));
        return json;
    }

    private JSONObject buildThirdPartyProof(ThirdPartyProof proof) {
        if (ObjectUtil.isNull(proof)) {
            return null;
        }
        JSONObject json = new JSONObject(true);
        json.put("tpbtaVersion", proof.getTpbtaVersion());
        JSONObject resp = new JSONObject(true);
        if (ObjectUtil.isNotNull(proof.getResp())) {
            JSONObject authMessage = tryBuildAuthMessage(proof.getResp().getBody(), false);
            resp.put(
                    "body",
                    ObjectUtil.isNull(authMessage) ? parseOpaqueData(proof.getResp().getBody()) : authMessage
            );
        }
        json.put("resp", ObjectUtil.isNull(proof.getResp()) ? null : resp);
        json.put("tpbtaCrossChainLane", buildLane(proof.getTpbtaCrossChainLane()));
        json.put("rawProof", buildRawProof(proof.getRawProof()));
        return json;
    }

    private JSONObject buildLane(CrossChainLane lane) {
        if (ObjectUtil.isNull(lane)) {
            return null;
        }
        JSONObject json = new JSONObject(true);
        JSONObject channel = new JSONObject(true);
        channel.put(
                "senderDomain",
                ObjectUtil.isNull(lane.getSenderDomain()) ? null : lane.getSenderDomain().getDomain()
        );
        channel.put(
                "receiverDomain",
                ObjectUtil.isNull(lane.getReceiverDomain()) ? null : lane.getReceiverDomain().getDomain()
        );
        json.put("crossChainChannel", channel);
        json.put("senderId", lane.getSenderIdHex());
        json.put("receiverId", lane.getReceiverIdHex());
        return json;
    }

    private Object buildRawProof(byte[] rawProof) {
        if (ObjectUtil.isEmpty(rawProof)) {
            return parseOpaqueData(rawProof);
        }
        try {
            CommitteeEndorseProof proof = CommitteeEndorseProof.decode(rawProof);
            if (!isValidCommitteeProof(proof)) {
                return parseOpaqueData(rawProof);
            }

            JSONObject json = new JSONObject(true);
            json.put("committeeId", proof.getCommitteeId());
            JSONArray signatures = new JSONArray();
            for (CommitteeNodeProof nodeProof : proof.getSigs()) {
                JSONObject signature = new JSONObject(true);
                signature.put("nodeId", nodeProof.getNodeId());
                signature.put("signAlgo", nodeProof.getSignAlgo().getName());
                signature.put("signature", hex(nodeProof.getSig()));
                signatures.add(signature);
            }
            json.put("sigs", signatures);
            return json;
        } catch (RuntimeException e) {
            return parseOpaqueData(rawProof);
        }
    }

    private boolean isValidCommitteeProof(CommitteeEndorseProof proof) {
        if (ObjectUtil.isNull(proof) || StrUtil.isEmpty(proof.getCommitteeId())) {
            return false;
        }
        List<CommitteeNodeProof> signatures = proof.getSigs();
        if (ObjectUtil.isEmpty(signatures)) {
            return false;
        }
        for (CommitteeNodeProof signature : signatures) {
            if (ObjectUtil.isNull(signature)
                    || StrUtil.isEmpty(signature.getNodeId())
                    || ObjectUtil.isNull(signature.getSignAlgo())
                    || ObjectUtil.isNull(signature.getSig())) {
                return false;
            }
        }
        return true;
    }

    private Object parseOpaqueData(byte[] value) {
        if (ObjectUtil.isNull(value)) {
            return null;
        }
        if (value.length == 0) {
            return "";
        }
        String text = decodeUtf8(value);
        if (ObjectUtil.isNotNull(text)) {
            try {
                Object json = JSON.parse(text);
                if (json instanceof JSONObject || json instanceof JSONArray) {
                    return json;
                }
            } catch (RuntimeException ignored) {
                // The data can still be a regular UTF-8 string.
            }
            if (isPrintable(text)) {
                return text;
            }
        }
        return hex(value);
    }

    private String decodeUtf8(byte[] value) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(value))
                    .toString();
        } catch (CharacterCodingException e) {
            return null;
        }
    }

    private boolean isPrintable(String value) {
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            if (Character.isISOControl(character)
                    && character != '\n'
                    && character != '\r'
                    && character != '\t') {
                return false;
            }
        }
        return true;
    }

    private String hex(byte[] value) {
        return ObjectUtil.isNull(value) ? null : HexUtil.encodeHexStr(value);
    }
}

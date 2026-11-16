package com.alipay.antchain.bridge.plugins.mychain020.utils;

import com.alipay.antchain.bridge.commons.core.am.AuthMessageFactory;
import com.alipay.antchain.bridge.commons.core.ptc.ThirdPartyProof;
import com.alipay.antchain.bridge.commons.core.sdp.SDPMessageFactory;

public class ContractUtils {

    /**
     * 从proofs中解析出am消息进一步晋西sdp消息，最终获取接收合约id
     *
     * @param proofsData
     * @return
     */
    public static String extractReceiverIdentity(byte[] proofsData) {
        int _len = proofsData.length;
        int _offset = 0;
        // hints len
        byte[] hints_len_bytes = new byte[4];
        System.arraycopy(proofsData, _offset, hints_len_bytes, 0, 4);
        _offset += 4;
        int hints_len = (int) SerializedDataUtils.extractUint32(hints_len_bytes, 4);
        // hints
        byte[] hints = new byte[hints_len];
        System.arraycopy(proofsData, _offset, hints, 0, hints_len);
        _offset += hints_len;

        // proof lens
        byte[] proof_len_bytes = new byte[4];
        System.arraycopy(proofsData, _offset, proof_len_bytes, 0, 4);
        _offset += 4;
        int proof_len = (int) SerializedDataUtils.extractUint32(proof_len_bytes, 4);
        // proof
        byte[] proof = new byte[proof_len];
        System.arraycopy(proofsData, _offset, proof, 0, proof_len);
        _offset += proof_len;

        // 解析proof
        return SDPMessageFactory.createSDPMessage(
                AuthMessageFactory.createAuthMessage(ThirdPartyProof.decode(proof).getResp().getBody()).getPayload()
        ).getTargetIdentity().toHex();
    }
}

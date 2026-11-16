
package com.alipay.antchain.bridge.plugins.mychain020.model;

import com.alipay.antchain.bridge.commons.utils.codec.tlv.TLVItem;
import com.alipay.antchain.bridge.commons.utils.codec.tlv.TLVPacket;
import org.apache.commons.lang3.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * udag证明转化结果
 */
public class UdagProofResult {

    public static short TLV_ORACLE_PUBKEY_HASH = 0;
    public static short TLV_ORACLE_REQUEST_ID = 1;
    public static short TLV_ORACLE_REQUEST_BODY = 2;
    public static short TLV_ORACLE_SIGNATURE_TYPE = 3;
    public static short TLV_ORACLE_REQUEST = 4;
    public static short TLV_ORACLE_RESPONSE_BODY = 5;  // 这里填充RESPONSE 内容
    public static short TLV_ORACLE_RESPONSE_SIGNATURE = 6;
    public static short TLV_ORACLE_ERROR_CODE = 7;
    public static short TLV_ORACLE_ERROR_MSG = 8;

    /**
     * 仅用于向异构链mock proof数据
     */
    public static short TLV_PROOF_SENDER_DOMAIN = 9;

    /**
     * 返回内容，UDAG返回内容，无AM扩展时是UDAG原始返回数据，有AM扩展插件时，填充的是AM插件处理后的数据，如果AM扩展执行发生异常，则BODY被清空填充空数组。
     */
    public static short UDAG_RESP_FIELD_BODY = 0;

    /**
     * 定长 4字节uint32_t，取值0/1, 0 代表访问AM扩展插件失败，OS层面需要重试；
     */
    public static short UDAG_RESP_FIELD_AMEXT_CALL_SUCCESS = 1;

    /**
     * 定长 4字节uint32_t，取值0/1, 0 代表调用执行AM扩展查询发生异常，详细内容见UDAG_RESP_FIELD_AMEXT_RETURN
     */
    public static short UDAG_RESP_FIELD_AMEXT_EXEC_SUCCESS = 2;

    /**
     * 字符串，执行AM扩展查询发生异常具体报错信息
     */
    public static short UDAG_RESP_FIELD_AMEXT_EXEC_OUTPUT = 3;

    // udagProof原始字节数组
    private byte[] udagProof;

    // 证明转化组件执行结果
    private boolean isSuccess;
    private int errorCode;
    private String errorMsg;

    // cid的内容
    private byte[] content;

    // AM扩展组件执行结果
    private boolean isAmExtCallSuccess;
    private boolean isAmExtExecuteSuccess;
    private String amExtErrorMsg;

    public UdagProofResult() {}

    public UdagProofResult(byte[] udagProof) {

        this.udagProof = udagProof;

        TLVPacket tlvPacket = TLVPacket.decode(udagProof);

        List<TLVItem> tlvItems = tlvPacket.getTlvItems();

        Map<Short, TLVItem> tlvItemMap = new HashMap<Short, TLVItem>();

        for (TLVItem tlvItem : tlvItems) {
            tlvItemMap.put(tlvItem.getType(), tlvItem);
        }

        // 读response
        if (!tlvItemMap.containsKey(TLV_ORACLE_RESPONSE_BODY)) {
            throw new RuntimeException("error udag proof, not TLV_ORACLE_RESPONSE_BODY");
        }
        byte[] response = tlvItemMap.get(TLV_ORACLE_RESPONSE_BODY).getValue();

        // 解response TLV Packet
        List<TLVItem> respTlvItems = TLVPacket.decode(response).getTlvItems();

        Map<Short, TLVItem> respTlvItemMap = new HashMap<Short, TLVItem>();

        for (TLVItem tlvItem : respTlvItems) {
            respTlvItemMap.put(tlvItem.getType(), tlvItem);
        }

        // 读content
        if (!respTlvItemMap.containsKey(UDAG_RESP_FIELD_BODY)) {
            throw new RuntimeException("error udag proof, not UDAG_RESP_FIELD_BODY");
        }
        this.content = respTlvItemMap.get(UDAG_RESP_FIELD_BODY).getValue();

        // 读AmExtCallSuccess
        if (!respTlvItemMap.containsKey(UDAG_RESP_FIELD_AMEXT_CALL_SUCCESS)) {
            // 向下兼容，如果没有AmExtCallSuccess属性，则设置为true
            this.isAmExtCallSuccess = true;
        } else {
            this.isAmExtCallSuccess = respTlvItemMap.get(UDAG_RESP_FIELD_AMEXT_CALL_SUCCESS).getUint32Value() == 1;
        }

        // 读AmExtExecuteSuccess
        if (!respTlvItemMap.containsKey(UDAG_RESP_FIELD_AMEXT_EXEC_SUCCESS)) {
            // 向下兼容，如果没有AmExtExecuteSuccess属性，则设置为true
            this.isAmExtExecuteSuccess = true;
        } else {
            this.isAmExtExecuteSuccess = respTlvItemMap.get(UDAG_RESP_FIELD_AMEXT_EXEC_SUCCESS).getUint32Value() == 1;
        }

        // 读amExtErrorMsg
        if (!respTlvItemMap.containsKey(UDAG_RESP_FIELD_AMEXT_EXEC_OUTPUT)) {
            // 向下兼容，如果没有amExtErrorMsg属性，则设置为空字符串
            this.amExtErrorMsg = StringUtils.EMPTY;
        } else {
            this.amExtErrorMsg = respTlvItemMap.get(UDAG_RESP_FIELD_AMEXT_EXEC_OUTPUT).getUtf8String();
        }
    }

    public byte[] getContent() {
        return content;
    }

}

package com.alipay.antchain.bridge.plugins.mychain020.common;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum SDPMsgTypeEnum {
    ORDERED(
            "getLastMsg()",
            "send(identity,string,bytes)",
            "sendV2(identity,string,bool,bytes)",
            "latest_msg_id_sent_order()",
            "GetLastOrderedMsg",
            "SendOrdered",
            "SendOrderedV2",
            "GetLatestMsgIdSentOrder"
    ),

    UNORDERED(
            "getLastUnorderedMsg()",
            "sendUnordered(identity,string,bytes)",
            "sendUnorderedV2(identity,string,bool,bytes)",
            "latest_msg_id_sent_unorder()",
            "GetLastUnorderedMsg",
            "SendUnordered",
            "SendUnorderedV2",
            "GetLatestMsgIdSentUnorder"
    );

    private final String evmMethodToGetLastMsg;

    private final String evmMethodToSendV1Msg;

    private final String evmMethodToSendV2Msg;

    private final String evmMethodToGetLastMsgIdSent;

    private final String wasmMethodToGetLastMsg;

    private final String wasmMethodToSendV1Msg;

    private final String wasmMethodToSendV2Msg;

    private final String wasmMethodToGetLastMsgIdSent;

}

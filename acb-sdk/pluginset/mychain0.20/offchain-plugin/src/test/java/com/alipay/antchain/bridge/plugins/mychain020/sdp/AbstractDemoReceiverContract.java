package com.alipay.antchain.bridge.plugins.mychain020.sdp;

import com.alipay.antchain.bridge.plugins.mychain020.common.SDPMsgTypeEnum;
import com.alipay.mychain.sdk.domain.account.Identity;

public abstract class AbstractDemoReceiverContract extends AbstractDemoContract {

    public abstract boolean setRecvSequence(String senderDomain, Identity senderContractID, int recvSeq);

    public abstract String getLastMsg(SDPMsgTypeEnum sdpType);
}

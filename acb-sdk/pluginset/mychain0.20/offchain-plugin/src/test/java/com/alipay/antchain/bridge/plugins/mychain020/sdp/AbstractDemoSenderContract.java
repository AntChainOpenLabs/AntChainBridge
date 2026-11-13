package com.alipay.antchain.bridge.plugins.mychain020.sdp;

import com.alipay.antchain.bridge.commons.core.base.SendResponseResult;
import com.alipay.antchain.bridge.plugins.mychain020.common.SDPMsgTypeEnum;
import com.alipay.mychain.sdk.domain.account.Identity;

public abstract class AbstractDemoSenderContract extends AbstractDemoContract {

    public abstract SendResponseResult sendMsgV1To(String receiverDomain, Identity receiverContractID, String msg, SDPMsgTypeEnum sdpType);

    public abstract boolean sendMsgV2To(String receiverDomain, Identity receiverContractID, String msg, boolean isAtomic, SDPMsgTypeEnum sdpType);

    public abstract String getLatestMsgIdSentUnorder();

    public abstract String getLatestMsgIdSentOrder();

    public abstract String getLatestMsgIdAckSuccess();

    public abstract String getLatestMsgIdAckError();

    public abstract String getLatestMsgError();
}

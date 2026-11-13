package com.alipay.antchain.bridge.relayer.core.manager.bbc;

import java.util.List;

import cn.hutool.core.util.ObjectUtil;
import com.alipay.antchain.bridge.commons.core.base.CrossChainMessageReceipt;
import com.alipay.antchain.bridge.commons.core.base.SendResponseResult;
import com.alipay.antchain.bridge.relayer.commons.model.AuthMsgPackage;
import com.alipay.antchain.bridge.relayer.commons.model.AuthMsgWrapper;
import com.alipay.antchain.bridge.relayer.core.types.blockchain.AbstractBlock;
import com.alipay.antchain.bridge.relayer.core.types.blockchain.HeterogeneousBlock;
import com.alipay.antchain.bridge.relayer.core.types.pluginserver.IBBCServiceClient;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AMClientContractHeteroBlockchainImpl implements IAMClientContract {

    private IBBCServiceClient bbcServiceClient;

    public AMClientContractHeteroBlockchainImpl(IBBCServiceClient bbcServiceClient) {
        this.bbcServiceClient = bbcServiceClient;
    }

    @Override
    public SendResponseResult recvPkgFromRelayer(AuthMsgPackage pkg) {
        try {
            String submissionId = "";
            if (pkg.getSdpMsgWrapper() != null && pkg.getSdpMsgWrapper().getAuthMsgWrapper() != null) {
                String ucpId = pkg.getSdpMsgWrapper().getAuthMsgWrapper().getUcpId();
                if (ucpId != null && !ucpId.isEmpty()) {
                    submissionId = stableSubmissionId(ucpId, bbcServiceClient.getDomain());
                }
            }
            CrossChainMessageReceipt receipt = bbcServiceClient.relayAuthMessage(pkg.extractProofs(), submissionId);
            if (ObjectUtil.isNull(receipt)) {
                return new SendResponseResult(
                        "",
                        false,
                        false,
                        "EMPTY RESP",
                        "EMPTY RESP",
                        0,
                        null
                );
            }
            if (!receipt.isSuccessful()) {
                return new SendResponseResult(
                        receipt.getTxhash(),
                        receipt.isConfirmed(),
                        false,
                        "HETERO COMMIT FAILED",
                        receipt.getErrorMsg(),
                        receipt.getTxTimestamp(),
                        receipt.getRawTx()
                );
            }

            log.info("successful to relay message to domain {} with tx {}", this.bbcServiceClient.getDomain(), receipt.getTxhash());
            return new SendResponseResult(
                    receipt.getTxhash(),
                    receipt.isConfirmed(),
                    receipt.isSuccessful(),
                    "SUCCESS",
                    receipt.getErrorMsg(),
                    receipt.getTxTimestamp(),
                    receipt.getRawTx()
            );
        } catch (Exception e) {
            log.error("failed to relay message to {}", this.bbcServiceClient.getDomain(), e);
            return new SendResponseResult(
                    "",
                    false,
                    false,
                    "UNKNOWN INTERNAL ERROR",
                    "UNKNOWN INTERNAL ERROR",
                    0,
                    null
            );
        }
    }

    @Override
    public void setProtocol(String protocolContract, String protocolType) {
        this.bbcServiceClient.setProtocol(protocolContract, protocolType);
    }

    static String stableSubmissionId(String ucpId, String domain) {
        return cn.hutool.crypto.digest.DigestUtil.sha256Hex("relay-am|" + ucpId + "|" + domain);
    }

    @Override
    public void setPtcContract(String ptcContractAddress) {
        this.bbcServiceClient.setPtcContract(ptcContractAddress);
    }

    @Override
    public List<AuthMsgWrapper> parseAMRequest(AbstractBlock abstractBlock) {
        if (!(abstractBlock instanceof HeterogeneousBlock)) {
            throw new RuntimeException("Invalid abstract block type");
        }
        return ((HeterogeneousBlock) abstractBlock).toAuthMsgWrappers();
    }

    @Override
    public void deployContract() {
        this.bbcServiceClient.setupAuthMessageContract();
    }

}

package com.alipay.antchain.bridge.relayer.core.service.receiver.handler;

import java.util.List;
import java.util.stream.Collectors;
import javax.annotation.Resource;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.alipay.antchain.bridge.commons.core.sdp.ISDPMessage;
import com.alipay.antchain.bridge.commons.core.sdp.SDPMessageFactory;
import com.alipay.antchain.bridge.relayer.commons.constant.AuthMsgProcessStateEnum;
import com.alipay.antchain.bridge.relayer.commons.constant.UpperProtocolTypeBeyondAMEnum;
import com.alipay.antchain.bridge.relayer.commons.model.AuthMsgWrapper;
import com.alipay.antchain.bridge.relayer.commons.model.SDPMsgCommitResult;
import com.alipay.antchain.bridge.relayer.commons.model.SDPMsgWrapper;
import com.alipay.antchain.bridge.relayer.commons.model.UniformCrosschainPacketContext;
import com.alipay.antchain.bridge.relayer.core.manager.blockchain.IBlockchainManager;
import com.alipay.antchain.bridge.relayer.core.manager.network.IRelayerNetworkManager;
import com.alipay.antchain.bridge.relayer.core.service.metrics.ExperimentMetricsRecorder;
import com.alipay.antchain.bridge.relayer.core.service.report.PlatformReportClient;
import com.alipay.antchain.bridge.relayer.dal.repository.ICrossChainMessageRepository;
import com.alipay.antchain.bridge.relayer.dal.repository.IScheduleRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Component
@Slf4j
public class AsyncReceiveHandler {

    @Resource
    private ICrossChainMessageRepository crossChainMessageRepository;

    @Resource
    private IScheduleRepository scheduleRepository;

    @Resource
    private TransactionTemplate transactionTemplate;

    @Resource
    private IBlockchainManager blockchainManager;

    @Resource
    private IRelayerNetworkManager relayerNetworkManager;

    @Resource
    private PlatformReportClient platformReportClient;

    @Resource
    private ExperimentMetricsRecorder experimentMetricsRecorder;

    public void receiveUniformCrosschainPackets(List<UniformCrosschainPacketContext> ucpContexts) {
        int rowsNum = crossChainMessageRepository.putUniformCrosschainPackets(ucpContexts);
        if (ucpContexts.size() != rowsNum) {
            throw new RuntimeException(
                    StrUtil.format(
                            "failed to save ucp messages: rows number {} inserted not equal to list size {}",
                            rowsNum, ucpContexts.size()
                    )
            );
        }
        log.info("put PENDING UCPs [ {} ] to pool success", ucpContexts.stream().map(UniformCrosschainPacketContext::getUcpId).reduce((s, s2) -> s + ", " + s2).orElse(""));

        // External reporting must never precede the Relayer's own durable UCP record.
        // PlatformReportClient contains all network/build failures, so the core receive path stays valid.
        ucpContexts.stream()
                .filter(context -> !context.isFromNetwork())
                .forEach(context -> {
                    experimentMetricsRecorder.recordSourceAccepted(context);
                    platformReportClient.reportUcp(context);
                });
    }

    public void receiveAuthMessages(List<AuthMsgWrapper> authMsgWrappers) {

        int rowsNum = crossChainMessageRepository.putAuthMessages(
                authMsgWrappers.stream()
                        .map(this::resetAuthMessageState)
                        .collect(Collectors.toList())
        );
        if (authMsgWrappers.size() != rowsNum) {
            throw new RuntimeException(
                    StrUtil.format(
                            "failed to save auth messages: rows number {} inserted not equal to list size {}",
                            rowsNum, authMsgWrappers.size()
                    )
            );
        }
        log.info("receive AuthenticMessages to pool success");
    }

    private AuthMsgWrapper resetAuthMessageState(AuthMsgWrapper authMsgWrapper) {
        if (authMsgWrapper.getProtocolType() == UpperProtocolTypeBeyondAMEnum.SDP) {
            ISDPMessage sdpMessage = SDPMessageFactory.createSDPMessage(authMsgWrapper.getAuthMessage().getPayload());
            if (
                    !blockchainManager.hasBlockchain(sdpMessage.getTargetDomain().getDomain())
                            && (
                            StrUtil.isEmpty(relayerNetworkManager.findRemoteRelayer(sdpMessage.getTargetDomain().getDomain()))
                                    || !relayerNetworkManager.hasCrossChainChannel(authMsgWrapper.getDomain(), sdpMessage.getTargetDomain().getDomain())
                    )
            ) {
                authMsgWrapper.setProcessState(AuthMsgProcessStateEnum.NOT_READY);
                scheduleRepository.markForDomainRouterQuery(authMsgWrapper.getDomain(), sdpMessage.getTargetDomain().getDomain());
            }
        }
        return authMsgWrapper;
    }

    public boolean receiveAMClientReceipt(List<SDPMsgCommitResult> commitResults) {

        // 处理空值
        if (commitResults.isEmpty()) {
            return true;
        }

        commitResults.forEach(result -> {
            if (!result.isConfirmed()) {
                return;
            }
            SDPMsgWrapper message = crossChainMessageRepository.getSDPMessage(result.getTxHash());
            if (ObjectUtil.isNotNull(message)) {
                String ucpId = crossChainMessageRepository.getUcpId(message.getAuthMsgWrapper().getAuthMsgId());
                platformReportClient.reportTargetChainExecution(
                        ucpId,
                        result
                );
                if (result.isCommitSuccess()) {
                    experimentMetricsRecorder.recordTargetConfirmed(ucpId, message, result.getTxHash());
                } else {
                    experimentMetricsRecorder.recordTargetFailed(
                            ucpId,
                            message,
                            result.getTxHash(),
                            result.isTimeout(),
                            result.getFailReason()
                    );
                }
            }
        });

        List<Integer> results = transactionTemplate.execute(
                status -> crossChainMessageRepository.updateSDPMessageResults(commitResults)
        );

        if (ObjectUtil.isEmpty(results)) {
            return false;
        }

        for (int i = 0; i < results.size(); i++) {
            if (0 == results.get(i)) {
                // sql变更行数为0，表示tx hash在DB不存在，可能有多种原因导致，可以跳过，打个warn
                log.warn(
                        "sdp msg to blockchain {} processed failed: ( tx: {}, fail_reason: {} )",
                        commitResults.get(i).getReceiveBlockchainId(),
                        commitResults.get(i).getTxHash(),
                        commitResults.get(i).getFailReason()
                );
            } else {
                log.info(
                        "sdp msg to blockchain {}-{} processed success: ( tx: {}, committed: {}, confirm: {}, )",
                        commitResults.get(i).getReceiveProduct(),
                        commitResults.get(i).getReceiveBlockchainId(),
                        commitResults.get(i).getTxHash(),
                        commitResults.get(i).isCommitSuccess(),
                        commitResults.get(i).isConfirmed()
                );
            }
        }

        return true;
    }
}

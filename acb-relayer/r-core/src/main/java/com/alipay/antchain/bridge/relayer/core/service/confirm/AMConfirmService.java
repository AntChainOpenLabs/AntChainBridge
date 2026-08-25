package com.alipay.antchain.bridge.relayer.core.service.confirm;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import javax.annotation.Resource;

import cn.hutool.core.collection.ListUtil;
import cn.hutool.core.map.MapUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.alipay.antchain.bridge.commons.core.base.BlockState;
import com.alipay.antchain.bridge.commons.core.base.CrossChainMessageReceipt;
import com.alipay.antchain.bridge.commons.core.sdp.AtomicFlagEnum;
import com.alipay.antchain.bridge.commons.core.sdp.SDPMessageFactory;
import com.alipay.antchain.bridge.commons.core.sdp.TimeoutMeasureEnum;
import com.alipay.antchain.bridge.relayer.commons.constant.SDPMsgProcessStateEnum;
import com.alipay.antchain.bridge.relayer.commons.model.AuthMsgWrapper;
import com.alipay.antchain.bridge.relayer.commons.model.SDPMsgCommitResult;
import com.alipay.antchain.bridge.relayer.commons.model.SDPMsgWrapper;
import com.alipay.antchain.bridge.relayer.commons.model.SDPNonceRecordDO;
import com.alipay.antchain.bridge.relayer.core.manager.blockchain.IBlockchainManager;
import com.alipay.antchain.bridge.relayer.core.manager.network.IRelayerNetworkManager;
import com.alipay.antchain.bridge.relayer.core.types.blockchain.AbstractBlockchainClient;
import com.alipay.antchain.bridge.relayer.core.types.blockchain.BlockchainClientPool;
import com.alipay.antchain.bridge.relayer.core.types.blockchain.HeteroBlockchainClient;
import com.alipay.antchain.bridge.relayer.core.types.network.IRelayerClientPool;
import com.alipay.antchain.bridge.relayer.core.types.network.RelayerClient;
import com.alipay.antchain.bridge.relayer.core.types.network.response.QueryCrossChainMsgReceiptsRespPayload;
import com.alipay.antchain.bridge.relayer.dal.repository.ICrossChainMessageRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class AMConfirmService {

    @Value("${relayer.service.confirm.batch_size:32}")
    private int confirmBatchSize;

    @Value("${relayer.service.confirm.sdp_msg_tx_pending_delayed_alarm_threshold:-1}")
    private long sdpMsgTxPendingDelayedAlarmThreshold;

    @Resource
    private ICrossChainMessageRepository crossChainMessageRepository;

    @Resource(name = "confirmServiceThreadsPool")
    private ExecutorService confirmServiceThreadsPool;

    @Resource
    private BlockchainClientPool blockchainClientPool;

    @Resource
    private IRelayerClientPool relayerClientPool;

    @Resource
    private IRelayerNetworkManager relayerNetworkManager;

    @Resource
    private IBlockchainManager blockchainManager;

    public void process(String product, String blockchainId) {
        List<SDPMsgWrapper> sdpMsgWrappers = crossChainMessageRepository.peekSDPMessages(
                product,
                blockchainId,
                SDPMsgProcessStateEnum.TX_PENDING,
                confirmBatchSize
        );
        if (ObjectUtil.isEmpty(sdpMsgWrappers)) {
            log.debug("none tx pending sdp message in DB for blockchain {}-{}", product, blockchainId);
            return;
        }

        if (sdpMsgTxPendingDelayedAlarmThreshold != -1) {
            sdpMsgWrappers.forEach(msg -> {
                if (msg.isTxPendingDelayed(sdpMsgTxPendingDelayedAlarmThreshold)) {
                    log.error("🚨[ALARM] sdp msg with primary id {} of {}-{} is txpending-delayed, msg last update time: {}, delay threshold: {}",
                            msg.getId(), product, blockchainId, msg.getLastUpdateTime(), sdpMsgTxPendingDelayedAlarmThreshold);
                }
            });
        }

        AbstractBlockchainClient client = blockchainClientPool.getClient(product, blockchainId);
        if (ObjectUtil.isNull(client)) {
            client = blockchainClientPool.createClient(blockchainManager.getBlockchainMeta(product, blockchainId));
        }
        HeteroBlockchainClient heteroBlockchainClient = (HeteroBlockchainClient) client;

        List<Future<ConfirmResult>> futureList = new ArrayList<>();
        sdpMsgWrappers.forEach(
                sdpMsgWrapper -> futureList.add(
                        confirmServiceThreadsPool.submit(
                                () -> new ConfirmResult(
                                        heteroBlockchainClient.queryCommittedTxReceipt(sdpMsgWrapper.getTxHash()),
                                        sdpMsgWrapper
                                )
                        )
                )
        );

        List<SDPMsgCommitResult> commitResults = new ArrayList<>();
        List<SDPNonceRecordDO> sdpNonceRecordsToSave = new ArrayList<>();
        futureList.forEach(
                future -> {
                    ConfirmResult result = resolveConfirmResult(future, product, blockchainId);
                    if (ObjectUtil.isNull(result)) {
                        return;
                    }
                    if (result.getReceipt().isConfirmed()) {
                        SDPMsgCommitResult sdpMsgCommitResult = buildCommitResult(product, blockchainId, result);
                        if (result.getSdpMsg().getVersion() > 2
                                && result.getSdpMsg().getSdpMessage().getAtomicFlag().ordinal() < AtomicFlagEnum.ACK_SUCCESS.ordinal()
                                && result.getSdpMsg().getSdpMessage().getTimeoutMeasure() != TimeoutMeasureEnum.NO_TIMEOUT
                                && !result.getReceipt().isSuccessful() && blockchainManager.checkAndProcessMessageTimeouts(result.getSdpMsg())) {
                            sdpMsgCommitResult.setTimeout(true);
                        }
                        commitResults.add(sdpMsgCommitResult);

                        log.info("sdp confirmed : (tx: {}, is_success: {}, error_msg: {})",
                                result.getReceipt().getTxhash(),
                                result.getReceipt().isSuccessful(),
                                result.getReceipt().getErrorMsg());
                        if (result.getSdpMsg().getAtomicFlag() != AtomicFlagEnum.ACK_RECEIVE_TX_FAILED.getValue() &&
                                result.getReceipt().isSuccessful() && result.getSdpMsg().getVersion() > 1 &&
                                result.getSdpMsg().isUnorderedMsg()) {
                            sdpNonceRecordsToSave.add(new SDPNonceRecordDO(
                                    result.getSdpMsg().getMessageId(),
                                    result.getSdpMsg().getSenderBlockchainDomain(),
                                    result.getSdpMsg().getMsgSender(),
                                    result.getSdpMsg().getReceiverBlockchainDomain(),
                                    result.getSdpMsg().getMsgReceiver(),
                                    result.getSdpMsg().getNonce()
                            ));
                        }
                    }
                }
        );

        crossChainMessageRepository.updateSDPMessageResults(commitResults);
        sdpNonceRecordsToSave.forEach(
                r -> crossChainMessageRepository.saveSDPNonceRecord(r)
        );
    }

    static ConfirmResult resolveConfirmResult(
            Future<ConfirmResult> future,
            String product,
            String blockchainId
    ) {
        try {
            return future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            // Interruption is a worker-lifecycle signal rather than a
            // transaction-local failure. Preserve it and abort this pass.
            throw new RuntimeException(
                    String.format("interrupted while querying cross-chain receipt for ( product: %s, bid: %s )", product, blockchainId),
                    e
            );
        } catch (ExecutionException e) {
            // Receipt availability is per transaction. One temporarily missing
            // native receipt must not discard confirmed results from the rest
            // of this batch; the failed row remains TX_PENDING for reconciliation.
            log.error(
                    "failed to query one cross-chain receipt for ( product: {}, bid: {} ), keep it pending",
                    product,
                    blockchainId,
                    e.getCause()
            );
        }
        return null;
    }

    static SDPMsgCommitResult buildCommitResult(String product, String blockchainId, ConfirmResult result) {
        // The pending row is already known. Updating by its primary key avoids
        // depending on a plugin's native transaction-hash representation
        // (notably Dioxide's non-hex hash) for terminal-state persistence.
        return new SDPMsgCommitResult(
                result.getSdpMsg().getId(),
                product,
                blockchainId,
                result.getReceipt().getTxhash(),
                result.getReceipt().isSuccessful(),
                result.getReceipt().getErrorMsg(),
                System.currentTimeMillis()
        );
    }

    public void processTimeout(String product, String blockchainId) {
        List<SDPMsgWrapper> sdpMsgWrappers = crossChainMessageRepository.peekSDPMessagesSent(
                product,
                blockchainId,
                SDPMsgProcessStateEnum.TIMEOUT,
                confirmBatchSize
        );
        if (ObjectUtil.isEmpty(sdpMsgWrappers)) {
            log.debug("none tx timeout sdp message in DB for blockchain {}-{}", product, blockchainId);
            return;
        }

        sdpMsgWrappers.forEach(
                sdpMsgWrapper -> {
                    if (sdpMsgWrapper.getSdpMessage().getVersion() >= 3) {
                        AuthMsgWrapper authMsgWrapper = crossChainMessageRepository.getAuthMessage(sdpMsgWrapper.getAuthMsgWrapper().getAuthMsgId());
                        if (ObjectUtil.isNull(authMsgWrapper)) {
                            log.error("empty auth msg for ( product: {}, blockchain_id: {}, sdp_msg_id: {} )",
                                    product, blockchainId, sdpMsgWrapper.getId());
                            return;
                        }
                        // 1. 获取发送方client
                        AbstractBlockchainClient client = blockchainClientPool.getClient(sdpMsgWrapper.getSenderBlockchainProduct(), sdpMsgWrapper.getSenderBlockchainId());
                        if (ObjectUtil.isNull(client)) {
                            client = blockchainClientPool.createClient(blockchainManager.getBlockchainMeta(product, blockchainId));
                        }
                        HeteroBlockchainClient heteroBlockchainClient = (HeteroBlockchainClient) client;

                        BlockState currBlockStateOnSdp = heteroBlockchainClient.getSDPMsgClientContract().queryValidatedBlockStateByDomain(sdpMsgWrapper.getReceiverBlockchainDomain());
                        if (ObjectUtil.isNotNull(currBlockStateOnSdp)
                                && !SDPMessageFactory.createSDPMessage(authMsgWrapper.getPayload()).isTimeout(currBlockStateOnSdp)) {
                            log.info("sdp {} can't be confirmed as timeout on SDP, so skip it!😝", sdpMsgWrapper.getId());
                            return;
                        }

                        // 2. 向发送链SDP合约发起超时回滚回调
                        CrossChainMessageReceipt recvOffChainExceptionRet = heteroBlockchainClient.getSDPMsgClientContract()
                                .recvOffChainException(
                                        sdpMsgWrapper.getMsgSender(),
                                        authMsgWrapper.getPayload()
                                );
                        if (recvOffChainExceptionRet.isConfirmed() && recvOffChainExceptionRet.isSuccessful()) {
                            crossChainMessageRepository.updateSDPMessageResult(
                                    sdpMsgWrapper.getAuthMsgWrapper().getAuthMsgId(),
                                    SDPMsgProcessStateEnum.ROLLBACK,
                                    recvOffChainExceptionRet.getTxhash(),
                                    true,
                                    "rollback success"
                            );
                            log.info("sdp {} rollback success by tx {}", sdpMsgWrapper.getMessageId(), recvOffChainExceptionRet.getTxhash());
                        } else if (recvOffChainExceptionRet.isConfirmed()) {
                            crossChainMessageRepository.updateSDPMessageResult(
                                    sdpMsgWrapper.getAuthMsgWrapper().getAuthMsgId(),
                                    SDPMsgProcessStateEnum.ROLLBACK_FAILED,
                                    recvOffChainExceptionRet.getTxhash(),
                                    false,
                                    recvOffChainExceptionRet.getErrorMsg()
                            );
                            log.warn("failed to rollback sdp for ( product: {}, blockchain_id: {}, sdp_msg_id: {}, error_msg: {} ), update it's state to tx fail",
                                    product, blockchainId, sdpMsgWrapper.getMessageId(), recvOffChainExceptionRet.getErrorMsg());
                        } else {
                            log.error("failed to call BBC to rollback for ( product: {}, blockchain_id: {}, sdp_msg_id: {}, error_msg: {} )",
                                    product, blockchainId, sdpMsgWrapper.getMessageId(), recvOffChainExceptionRet.getErrorMsg());
                        }
                    }
                }
        );

    }

    public void processFailed(String product, String blockchainId) {
        List<SDPMsgWrapper> sdpMsgWrappers = crossChainMessageRepository.peekSDPMessages(
                product,
                blockchainId,
                SDPMsgProcessStateEnum.TX_FAILED,
                confirmBatchSize
        );
        if (ObjectUtil.isEmpty(sdpMsgWrappers)) {
            log.debug("none tx failed sdp message in DB for blockchain {}-{}", product, blockchainId);
            return;
        }

        sdpMsgWrappers.forEach(
                sdpMsgWrapper -> {
                    if (sdpMsgWrapper.getSdpMessage().getVersion() >= 3) {
                        if (sdpMsgWrapper.getSdpMessage().getTimeoutMeasure() != TimeoutMeasureEnum.NO_TIMEOUT
                                && sdpMsgWrapper.getSdpMessage().getAtomicFlag().ordinal() < AtomicFlagEnum.ACK_SUCCESS.ordinal()
                                && blockchainManager.checkAndProcessMessageTimeouts(sdpMsgWrapper)) {
                            log.info("failed sdp msg {} is timeout", sdpMsgWrapper.getSdpMessage().getMessageId());
                            crossChainMessageRepository.updateSDPMessageState(sdpMsgWrapper.getAuthMsgWrapper().getAuthMsgId(), SDPMsgProcessStateEnum.TIMEOUT);
                        }
                    }
                }
        );
    }

    public void processSentToRemoteRelayer(String product, String blockchainId) {
        List<SDPMsgWrapper> sdpMsgWrappersSent = crossChainMessageRepository.peekSDPMessagesSent(
                product,
                blockchainId,
                SDPMsgProcessStateEnum.REMOTE_PENDING,
                confirmBatchSize
        );
        if (ObjectUtil.isEmpty(sdpMsgWrappersSent)) {
            return;
        }

        List<Future<Map<Long, RemoteCrossChainMsgResult>>> futureList = new ArrayList<>();
        sdpMsgWrappersSent.stream().collect(Collectors.groupingBy(SDPMsgWrapper::getReceiverBlockchainDomain))
                .forEach((key, value) -> futureList.add(
                        confirmServiceThreadsPool.submit(
                                () -> {
                                    RelayerClient relayerClient = relayerClientPool.getRelayerClientByDomain(key);
                                    if (ObjectUtil.isNull(relayerClient)) {
                                        relayerClient = relayerClientPool.getRelayerClient(
                                                relayerNetworkManager.getRelayerNode(relayerNetworkManager.findRemoteRelayer(key), false),
                                                key
                                        );
                                    }
                                    Map<String, Long> ucpIdsMap = value.stream().map(
                                                    sdpMsgWrapper -> MapUtil.entry(
                                                            sdpMsgWrapper.getId(),
                                                            crossChainMessageRepository.getUcpId(
                                                                    sdpMsgWrapper.getAuthMsgWrapper().getAuthMsgId()
                                                            )
                                                    )
                                            ).filter(entry -> StrUtil.isNotEmpty(entry.getValue()))
                                            .collect(Collectors.toMap(
                                                    Map.Entry::getValue,
                                                    Map.Entry::getKey
                                            ));
                                    if (ObjectUtil.isNotEmpty(ucpIdsMap)) {
                                        QueryCrossChainMsgReceiptsRespPayload respPayload = relayerClient.queryCrossChainMessageReceipts(
                                                ListUtil.toList(ucpIdsMap.keySet())
                                        );
                                        Map<String, RemoteCrossChainMsgResult> resultMap = respPayload.toRemoteCrossChainMsgResultMap();

                                        return resultMap.entrySet().stream()
                                                .collect(Collectors.toMap(
                                                        entry -> ucpIdsMap.get(entry.getKey()),
                                                        Map.Entry::getValue
                                                ));
                                    }
                                    return new HashMap<>();
                                }
                        )
                ));

        List<SDPMsgCommitResult> commitResults = new ArrayList<>();
        futureList.forEach(
                future -> {
                    Map<Long, RemoteCrossChainMsgResult> resultMap;
                    try {
                        resultMap = future.get();
                    } catch (InterruptedException | ExecutionException e) {
                        throw new RuntimeException(
                                String.format("failed to query cross-chain receipt for ( product: %s, bid: %s )", product, blockchainId),
                                e
                        );
                    }
                    resultMap.forEach(
                            (k, result) -> {
                                if (result.getReceipt().isConfirmed() || result.getState() == SDPMsgProcessStateEnum.TIMEOUT) {
                                    commitResults.add(
                                            new SDPMsgCommitResult(
                                                    k,
                                                    product,
                                                    blockchainId,
                                                    result.getReceipt().getTxhash(),
                                                    result.getReceipt().isSuccessful(),
                                                    result.getReceipt().getErrorMsg(),
                                                    System.currentTimeMillis(),
                                                    ObjectUtil.equal(result.getState(), SDPMsgProcessStateEnum.TIMEOUT)
                                            )
                                    );
                                    log.info("sdp {} confirmed by remote relayer: (tx: {}, is_success: {}, error_msg: {}, state: {})",
                                            k,
                                            result.getReceipt().getTxhash(),
                                            result.getReceipt().isSuccessful(),
                                            result.getReceipt().getErrorMsg(),
                                            result.getState());
                                } else {
                                    log.debug("sdp {} still not confirmed by remote relayer", k);
                                }
                            }
                    );
                }
        );

        crossChainMessageRepository.updateSDPMessageResults(commitResults);
    }
}

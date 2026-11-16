package com.alipay.antchain.bridge.relayer.core.service.committer;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.locks.Lock;
import java.util.stream.Collectors;
import javax.annotation.Resource;

import cn.hutool.core.collection.ListUtil;
import cn.hutool.core.util.HexUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.alipay.antchain.bridge.commons.core.am.AuthMessageFactory;
import com.alipay.antchain.bridge.commons.core.base.BlockState;
import com.alipay.antchain.bridge.commons.core.base.SendResponseResult;
import com.alipay.antchain.bridge.commons.core.ptc.ThirdPartyProof;
import com.alipay.antchain.bridge.commons.core.rcc.IdempotentInfo;
import com.alipay.antchain.bridge.commons.core.rcc.ReliableCrossChainMessage;
import com.alipay.antchain.bridge.commons.core.rcc.ReliableCrossChainMsgProcessStateEnum;
import com.alipay.antchain.bridge.commons.core.sdp.AtomicFlagEnum;
import com.alipay.antchain.bridge.commons.core.sdp.SDPMessageFactory;
import com.alipay.antchain.bridge.commons.core.sdp.TimeoutMeasureEnum;
import com.alipay.antchain.bridge.relayer.commons.constant.Constants;
import com.alipay.antchain.bridge.relayer.commons.constant.SDPMsgProcessStateEnum;
import com.alipay.antchain.bridge.relayer.commons.exception.AntChainBridgeRelayerException;
import com.alipay.antchain.bridge.relayer.commons.exception.RelayerErrorCodeEnum;
import com.alipay.antchain.bridge.relayer.commons.model.*;
import com.alipay.antchain.bridge.relayer.core.manager.blockchain.IBlockchainManager;
import com.alipay.antchain.bridge.relayer.core.service.metrics.ExperimentMetricsRecorder;
import com.alipay.antchain.bridge.relayer.core.types.blockchain.AbstractBlockchainClient;
import com.alipay.antchain.bridge.relayer.core.types.blockchain.BlockchainClientPool;
import com.alipay.antchain.bridge.relayer.core.types.blockchain.HeteroBlockchainClient;
import com.alipay.antchain.bridge.relayer.core.service.report.PlatformReportClient;
import com.alipay.antchain.bridge.relayer.core.utils.ProcessUtils;
import com.alipay.antchain.bridge.relayer.dal.repository.ICrossChainMessageRepository;
import com.alipay.antchain.bridge.relayer.dal.repository.ISystemConfigRepository;
import com.alipay.antchain.bridge.relayer.dal.repository.impl.BlockchainIdleDCache;
import com.google.common.collect.Lists;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallbackWithoutResult;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@Slf4j
public class CommitterService {

    @Resource(name = "committerServiceThreadsPool")
    private ExecutorService committerServiceThreadsPool;

    @Resource
    private IBlockchainManager blockchainManager;

    @Resource
    private BlockchainIdleDCache blockchainIdleDCache;

    @Resource
    private ICrossChainMessageRepository crossChainMessageRepository;

    @Resource
    private BlockchainClientPool blockchainClientPool;

    @Resource
    private ISystemConfigRepository systemConfigRepository;

    @Resource
    private TransactionTemplate transactionTemplate;

    @Resource
    private PlatformReportClient platformReportClient;

    @Resource
    private ExperimentMetricsRecorder experimentMetricsRecorder;

    @Value("${relayer.service.committer.ccmsg.batch_size:32}")
    private int commitBatchSize;

    @Value("${relayer.service.committer.threads.core_size:8}")
    private int committerServiceCoreSize;

    @Value("${relayer.service.committer.sdp_msg_commit_delayed_alarm_threshold:-1}")
    private long sdpMsgCommitDelayedAlarmThreshold;

    public void process(String blockchainProduct, String blockchainId) {

        if (isBusyBlockchain(blockchainProduct, blockchainId)) {
            log.info("blockchain {}-{} are too busy to receive new message", blockchainProduct, blockchainId);
            return;
        }

        List<SDPMsgWrapper> sdpMsgWrappers = new ArrayList<>();

        if (this.blockchainIdleDCache.ifAMCommitterIdle(blockchainProduct, blockchainId)) {
            log.debug("blockchain {}-{} has no messages processed recently, so skip it this committing process", blockchainProduct, blockchainId);
        } else {
            sdpMsgWrappers = crossChainMessageRepository.peekSDPMessages(
                    blockchainProduct,
                    blockchainId,
                    SDPMsgProcessStateEnum.PENDING,
                    commitBatchSize
            );
        }

        if (!sdpMsgWrappers.isEmpty()) {
            log.info("peek {} sdp msg for blockchain {} from pool", sdpMsgWrappers.size(), blockchainId);
            if (sdpMsgCommitDelayedAlarmThreshold != -1) {
                sdpMsgWrappers.forEach(msg -> {
                    if (msg.isCommitDelayed(sdpMsgCommitDelayedAlarmThreshold)) {
                        log.error("🚨[ALARM] sdp msg with primary id {} of {}-{} is commit-delayed, msg create time: {}, delay threshold: {}",
                                msg.getId(), blockchainProduct, blockchainId, msg.getCreateTime(), sdpMsgCommitDelayedAlarmThreshold);
                    }
                });
            }
        } else {
            this.blockchainIdleDCache.setLastEmptyAMSendQueueTime(blockchainProduct, blockchainId);
            log.debug("[committer] peek zero sdp msg for blockchain {} from pool", blockchainId);
        }

        // keyed by session key(msg.sender:msg.receiver)
        Map<String, List<SDPMsgWrapper>> sdpMsgsMap = groupSession(
                sdpMsgWrappers,
                committerServiceCoreSize
        );

        if (!sdpMsgsMap.isEmpty()) {
            log.info("peek {} sdp msg sessions for blockchain {} from pool", sdpMsgsMap.size(), blockchainId);
        } else {
            log.debug("peek zero sdp msg sessions for blockchain {} from pool", blockchainId);
        }

        List<Future> futures = new ArrayList<>();
        for (Map.Entry<String, List<SDPMsgWrapper>> entry : sdpMsgsMap.entrySet()) {
            futures.add(
                    committerServiceThreadsPool.submit(
                            wrapRequestTask(
                                    entry.getKey(),
                                    entry.getValue()
                            )
                    )
            );
        }

        // 等待执行完成
        ProcessUtils.waitAllFuturesDone(blockchainProduct, blockchainId, futures, log);
    }

    private boolean isBusyBlockchain(String blockchainProduct, String blockchainId) {

        String pendingLimit = systemConfigRepository.getSystemConfig(
                StrUtil.format("{}-{}-{}", Constants.PENDING_LIMIT, blockchainProduct, blockchainId)
        );

        boolean busy = false;
        if (!StrUtil.isEmpty(pendingLimit)) {
            long cnt = crossChainMessageRepository.countSDPMessagesByState(
                    blockchainProduct,
                    blockchainId,
                    SDPMsgProcessStateEnum.TX_PENDING
            );
            busy = cnt >= Long.parseLong(pendingLimit);
            if (busy) {
                log.error("🚨[ALARM] to many tx-pending sdp msgs for {}-{} : {} over limit {}", blockchainProduct, blockchainId, cnt, pendingLimit);
            }
        }

        return busy;
    }

    private Map<String, List<SDPMsgWrapper>> groupSession(List<SDPMsgWrapper> sdpMsgWrappers, int remainingWorkerNum) {

        // keyed by session key(msg.sender:msg.receiver)
        Map<String, List<SDPMsgWrapper>> sdpMsgsMap = new HashMap<>();

        for (SDPMsgWrapper msg : sdpMsgWrappers) {
            String sessionKey = msg.getSessionKey();
            if (!sdpMsgsMap.containsKey(sessionKey)) {
                sdpMsgsMap.put(sessionKey, new ArrayList<>());
            }
            sdpMsgsMap.get(sessionKey).add(msg);
        }

        // 当前情况下，线程池剩余的线程数
        int leftWorkerNum = remainingWorkerNum - sdpMsgsMap.size();

        // 如果线程池有资源剩余，就将Unordered类型的消息拿出来，充分利用剩余资源
        if (leftWorkerNum >= 1) {
            // 所有的Unordered消息的Map
            // - key 使用session key
            // - value 是该session的SDP消息
            Map<String, List<SDPMsgWrapper>> unorderedMap = new HashMap<>();

            // 所有的Unordered消息的总数
            int totalSize = 0;
            for (Map.Entry<String, List<SDPMsgWrapper>> entry : sdpMsgsMap.entrySet()) {
                if (StrUtil.startWith(entry.getKey(), SDPMsgWrapper.UNORDERED_SDP_MSG_SESSION)) {
                    unorderedMap.put(entry.getKey(), entry.getValue());
                    totalSize += entry.getValue().size();
                }
            }

            // 如果无序消息的总数大于0，就按各个session的消息数目比例，均分掉剩余的线程
            if (!unorderedMap.isEmpty()) {
                // 因为要重新分配后，在add回unorderedMap，所以这里先删除
                unorderedMap.keySet().forEach(sdpMsgsMap::remove);
                leftWorkerNum += unorderedMap.size();

                // sessionNum是后面要用到多少个线程，每个session一个线程
                // 如果没有那么多的消息，就把消息总数作为新分配的session数目
                int sessionNum = Math.min(leftWorkerNum, totalSize);

                // 将原先的session，拆分到一个或者多个新session，将消息均匀分到这些新的session中
                for (Map.Entry<String, List<SDPMsgWrapper>> entry : unorderedMap.entrySet()) {
                    // count 就是该session需要拆分为新session的数目
                    // 按消息占总体的比例分配，最小为1
                    int count = Math.max(sessionNum * entry.getValue().size() / totalSize, 1);

                    // 将消息均分到信息的session中，直接add到p2pMsgsMap
                    for (int i = 0; i < entry.getValue().size(); i++) {
                        // 新的session key，利用余数均匀分配消息到新的session中
                        String key = String.format("%s-%d", entry.getKey(), i % count);
                        if (!sdpMsgsMap.containsKey(key)) {
                            sdpMsgsMap.put(key, Lists.newArrayList());
                        }
                        sdpMsgsMap.get(key).add(entry.getValue().get(i));
                    }
                }
            }
        }

        return sdpMsgsMap;
    }

    private Runnable wrapRequestTask(String sessionName, List<SDPMsgWrapper> sessionMsgs) {
        return () -> {
            Lock sessionLock = crossChainMessageRepository.getSessionLock(sessionName);
            sessionLock.lock();
            log.info("get distributed lock for session {}", sessionName);
            try {
                // 这是个分布式并发任务，加了session锁后，要check下每个SDP消息的最新状态，防止重复处理
                List<SDPMsgWrapper> sessionMsgsUpdate = filterOutdatedMsg(sessionMsgs);

                // p2p按seq排序，后续需要按序提交
                sortSDPMsgList(sessionMsgsUpdate);

                for (SDPMsgWrapper sdpMsgWrapper : sessionMsgsUpdate) {
                    // 逐笔提交，但包装为数组调用批量更新接口
                    log.info("committing msg of id {} for session {}", sdpMsgWrapper.getId(), sessionName);
                    batchCommitSDPMsg(sessionName, ListUtil.toList(sdpMsgWrapper));
                }
            } catch (AntChainBridgeRelayerException e) {
                throw e;
            } catch (Exception e) {
                throw new AntChainBridgeRelayerException(
                        RelayerErrorCodeEnum.SERVICE_COMMITTER_PROCESS_CCMSG_FAILED,
                        e,
                        "failed to commit session {} with {} messages",
                        sessionName, sessionMsgs.size()
                );
            } finally {
                sessionLock.unlock();
                log.info("release distributed lock for session {}", sessionName);
            }
        };
    }

    private List<SDPMsgWrapper> filterOutdatedMsg(List<SDPMsgWrapper> sessionMsgs) {
        return sessionMsgs.stream().filter(
                sdpMsgWrapper ->
                        crossChainMessageRepository.getSDPMessage(sdpMsgWrapper.getId(), true)
                                .getProcessState() == SDPMsgProcessStateEnum.PENDING
        ).collect(Collectors.toList());
    }

    /**
     * 已经上链过的消息直接更新为已上链（可能种种原因，之前上链时候的hash丢失了未更新到db）
     *
     * @param msgSet
     */
    private void updateExpiredMsg(ParsedSDPMsgSet msgSet) {
        if (!msgSet.getExpired().isEmpty()) {
            for (SDPMsgWrapper msg : msgSet.getExpired()) {
                msg.setProcessState(SDPMsgProcessStateEnum.TX_SUCCESS);
                log.info("AMCommitter: am {} has been committed on chain", msg.getAuthMsgWrapper().getAuthMsgId());
                if (!crossChainMessageRepository.updateSDPMessage(msg)) {
                    throw new RuntimeException("database update failed");
                }
            }
        }
    }

    private void batchCommitSDPMsg(String sessionName, List<SDPMsgWrapper> msgs) {

        String receiverProduct = msgs.get(0).getReceiverBlockchainProduct();
        String receiverBlockchainId = msgs.get(0).getReceiverBlockchainId();

        ParsedSDPMsgSet msgSet = parseSDPMsgList(receiverProduct, receiverBlockchainId, msgs);

        // 处理脏数据
        updateExpiredMsg(msgSet);

        // 处理新数据
        if (msgSet.getUpload().isEmpty()) {
            return;
        }

        log.info("AMCommitter: {} messages should uploaded for session {}", sessionName, msgSet.getUpload().size());

        // 获取链客户端
        AbstractBlockchainClient client = blockchainClientPool.getClient(receiverProduct, receiverBlockchainId);
        if (ObjectUtil.isNull(client)) {
            client = blockchainClientPool.createClient(blockchainManager.getBlockchainMeta(receiverProduct, receiverBlockchainId));
        }
        HeteroBlockchainClient heteroBlockchainClient = (HeteroBlockchainClient) client;

        Map<String, BlockState> senderBlockStateMap = new HashMap<>();
        try {
            msgSet.getUpload().forEach(
                    sdpMsgWrapper -> {
                        transactionTemplate.execute(
                                new TransactionCallbackWithoutResult() {
                                    @Override
                                    protected void doInTransactionWithoutResult(TransactionStatus status) {
                                        log.info("committing msg {} for session {} now!", sdpMsgWrapper.getId(), sessionName);
                                        commitSingleMsg(sessionName, sdpMsgWrapper, heteroBlockchainClient, senderBlockStateMap);
                                    }
                                }
                        );
                    }
            );
            log.info("AMCommitter: messages for session {} status updated in database", sessionName);
        } catch (Exception e) {
            throw new AntChainBridgeRelayerException(
                    RelayerErrorCodeEnum.SERVICE_COMMITTER_PROCESS_COMMIT_SDP_FAILED,
                    e,
                    "failed to commit msg for session {}", sessionName
            );
        }
    }

    private void commitSingleMsg(String sessionName, SDPMsgWrapper sdpMsgWrapper, HeteroBlockchainClient heteroBlockchainClient, Map<String, BlockState> senderBlockStateMap) {
        // 1. 若链支持可靠上链特性，过滤已存在五元组
        boolean ifReliable = heteroBlockchainClient.ifSupportReliableCrossChain() && sdpMsgWrapper.getVersion() > 1 && sdpMsgWrapper.isUnorderedMsg();
        if (ifReliable) {
            //  1.1 取出五元组
            IdempotentInfo idempotentInfo = new IdempotentInfo(
                    sdpMsgWrapper.getSenderBlockchainDomain(),
                    HexUtil.decodeHex(sdpMsgWrapper.getMsgSender()),
                    sdpMsgWrapper.getReceiverBlockchainDomain(),
                    HexUtil.decodeHex(sdpMsgWrapper.getMsgReceiver()),
                    sdpMsgWrapper.getSdpMessage().getNonce()
            );

            // 1.2 判断消息是否已经存在
            ReliableCrossChainMessage message = crossChainMessageRepository
                    .getReliableMessagesByIdempotentInfo(idempotentInfo);
            if (sdpMsgWrapper.getAtomicFlag() != AtomicFlagEnum.ACK_RECEIVE_TX_FAILED.getValue()
                    && ObjectUtil.isNotEmpty(message)) {
                // 消息已经存在，不需要提交，更新该消息状态为已存在状态即可（重发由可靠上链分布式任务执行）
                log.info("cross chain message {} is already received, update the sdp and ignore", idempotentInfo.getInfo());

                sdpMsgWrapper.setTxSuccess(message.getStatus() == ReliableCrossChainMsgProcessStateEnum.SUCCESS);
                sdpMsgWrapper.setTxHash(message.getCurrentHash());
                sdpMsgWrapper.setTxFailReason(message.getErrorMsg());
                SDPMsgProcessStateEnum state = message.getStatus() == ReliableCrossChainMsgProcessStateEnum.PENDING ? SDPMsgProcessStateEnum.TX_PENDING :
                        (message.getStatus() == ReliableCrossChainMsgProcessStateEnum.SUCCESS ? SDPMsgProcessStateEnum.TX_SUCCESS : SDPMsgProcessStateEnum.TX_FAILED);
                sdpMsgWrapper.setProcessState(state);
                if (!crossChainMessageRepository.updateSDPMessage(sdpMsgWrapper)) {
                    throw new RuntimeException("database update failed");
                }
                return;
            }
        }

        // 2. 向接收链提交消息
        sdpMsgWrapper.setAuthMsgWrapper(
                crossChainMessageRepository.getAuthMessage(sdpMsgWrapper.getAuthMsgWrapper().getAuthMsgId())
        );

        ThirdPartyProof tpProof = crossChainMessageRepository.getTpProof(sdpMsgWrapper.getAuthMsgWrapper().getUcpId());
        if (ObjectUtil.isNotNull(tpProof)) {
            if (sdpMsgWrapper.getVersion() > 2 && sdpMsgWrapper.getSdpMessage().getAtomicFlag() == AtomicFlagEnum.ACK_RECEIVE_TX_FAILED) {
                if (!senderBlockStateMap.containsKey(sdpMsgWrapper.getSenderBlockchainDomain())) {
                    senderBlockStateMap.put(
                            sdpMsgWrapper.getSenderBlockchainDomain(),
                            heteroBlockchainClient.getSDPMsgClientContract().queryValidatedBlockStateByDomain(sdpMsgWrapper.getSenderBlockchainDomain())
                    );
                }
                BlockState blockStateInMsg = BlockState.decode(SDPMessageFactory.createSDPMessage(
                        AuthMessageFactory.createAuthMessage(tpProof.getResp().getBody()).getPayload()
                ).getPayload());
                if (blockStateInMsg.getHeight().compareTo(senderBlockStateMap.get(sdpMsgWrapper.getSenderBlockchainDomain()).getHeight()) <= 0) {
                    log.info("block state (height: {}, timestamp: {}) of {} in msg {} is not greater than block state (height: {}, timestamp: {}) in SDP contract of chain {}, skip it!😝",
                            blockStateInMsg.getHeight(), blockStateInMsg.getTimestamp(),
                            sdpMsgWrapper.getSenderBlockchainDomain(),
                            sdpMsgWrapper.getId(),
                            blockStateInMsg.getHeight(), blockStateInMsg.getTimestamp(),
                            sdpMsgWrapper.getReceiverBlockchainDomain()
                    );
                    sdpMsgWrapper.setTxSuccess(true);
                    sdpMsgWrapper.setTxHash("0000000000000000000000000000000000000000000000000000000000000000");
                    sdpMsgWrapper.setTxFailReason("block state not greater than block state in SDP contract");
                    sdpMsgWrapper.setProcessState(SDPMsgProcessStateEnum.TX_SUCCESS);
                    if (!crossChainMessageRepository.updateSDPMessage(sdpMsgWrapper)) {
                        throw new RuntimeException("database update failed");
                    }
                    return;
                }

                log.info("going to sync block state (height: {}, timestamp: {}) of {} in msg {} to SDP contract of chain {}",
                        blockStateInMsg.getHeight(), blockStateInMsg.getTimestamp(),
                        sdpMsgWrapper.getSenderBlockchainDomain(),
                        sdpMsgWrapper.getId(),
                        sdpMsgWrapper.getReceiverBlockchainDomain()
                );
            }
        }
        PTCProofResult proof = ObjectUtil.isNull(tpProof) ? null : new PTCProofResult(tpProof);

        String ucpId = sdpMsgWrapper.getAuthMsgWrapper().getUcpId();
        ExperimentMetricsRecorder.SubmissionAttempt experimentSubmission =
                experimentMetricsRecorder.beginTargetSubmission(ucpId, sdpMsgWrapper);
        SendResponseResult res;
        try {
            res = heteroBlockchainClient.getAMClientContract()
                    .recvPkgFromRelayer(
                            AuthMsgPackage.convertFrom(
                                    Collections.singletonList(sdpMsgWrapper),
                                    Collections.singletonList(proof)));
            experimentMetricsRecorder.recordTargetSubmitting(
                    experimentSubmission,
                    res == null ? null : res.getTxId(),
                    res != null && res.isSuccess()
            );
        } catch (RuntimeException e) {
            experimentMetricsRecorder.recordTargetSubmitting(experimentSubmission, null, false);
            experimentMetricsRecorder.recordTargetFailed(ucpId, sdpMsgWrapper, null, false, e.getMessage());
            throw e;
        }

        // Send tx result situations:
        //
        // - unknown_exception: unknown exception from upper operations
        // - tx_sent_failed: failed to send tx, returned with errcode and errmsg
        // - tx_success: tx has been sent successfully
        // - tx_pending: tx has been sent but pending to execute
        //   1. revert error, returned with REVERT_ERROR and errmsg
        //   2. other chain error, returned with errcode and errmsg
        //   3. success

        // 3. 若提交失败，需要进行超时判断处理
        boolean isTimeout = false;
        if (!res.isConfirmed() && !res.isSuccess()) {
            log.error("AMCommitter: amPkg for session {} commit failed, error msg: {}", sessionName, res.getErrorMessage());
            if (sdpMsgWrapper.getVersion() > 2
                    && sdpMsgWrapper.getSdpMessage().getAtomicFlag().ordinal() < AtomicFlagEnum.ACK_SUCCESS.ordinal()
                    && sdpMsgWrapper.getSdpMessage().getTimeoutMeasure() != TimeoutMeasureEnum.NO_TIMEOUT) {
                isTimeout = blockchainManager.checkAndProcessMessageTimeouts(sdpMsgWrapper);
                if (!isTimeout) {
                    experimentMetricsRecorder.recordTargetFailed(
                            ucpId, sdpMsgWrapper, res.getTxId(), false, res.getErrorMessage()
                    );
                    throw new RuntimeException(StrUtil.format("failed to commit msgs: (error_code: {}, error_msg: {})",
                            res.getErrorCode(), res.getErrorMessage()));
                }
            } else {
                experimentMetricsRecorder.recordTargetFailed(
                        ucpId, sdpMsgWrapper, res.getTxId(), false, res.getErrorMessage()
                );
                throw new RuntimeException(StrUtil.format("failed to commit msgs: (error_code: {}, error_msg: {})",
                        res.getErrorCode(), res.getErrorMessage()));
            }
        }

        platformReportClient.reportTargetChainSubmission(ucpId, sdpMsgWrapper, res);
        platformReportClient.reportTargetChainExecution(ucpId, res, isTimeout);
        if (res.isConfirmed() && res.isSuccess()) {
            experimentMetricsRecorder.recordTargetConfirmed(ucpId, sdpMsgWrapper, res.getTxId());
        } else if (!res.isSuccess() || isTimeout) {
            experimentMetricsRecorder.recordTargetFailed(
                    ucpId,
                    sdpMsgWrapper,
                    res.getTxId(),
                    isTimeout,
                    res.getErrorMessage()
            );
        }

        // 4. 更新sdp记录表（若当前未成功，等待AMComfiredService查询提交结果）
        SDPMsgProcessStateEnum state = calculateSDPProcessState(res.isConfirmed(), res.isSuccess(), isTimeout);
        sdpMsgWrapper.setTxSuccess(res.isSuccess());
        sdpMsgWrapper.setTxHash(res.getTxId());
        sdpMsgWrapper.setTxFailReason(state != SDPMsgProcessStateEnum.TIMEOUT ? res.getErrorMessage() : SDPMsgCommitResult.TIMEOUT_FAIL_REASON);
        sdpMsgWrapper.setProcessState(state);
        if (!crossChainMessageRepository.updateSDPMessage(sdpMsgWrapper)) {
            throw new RuntimeException("database update failed");
        }
        if (res.isConfirmed() && sdpMsgWrapper.getVersion() >= 2 && sdpMsgWrapper.isUnorderedMsg() && !sdpMsgWrapper.isAck()) {
            crossChainMessageRepository.saveSDPNonceRecord(
                    new SDPNonceRecordDO(
                            sdpMsgWrapper.getMessageId(),
                            sdpMsgWrapper.getSenderBlockchainDomain(),
                            sdpMsgWrapper.getMsgSender(),
                            sdpMsgWrapper.getReceiverBlockchainDomain(),
                            sdpMsgWrapper.getMsgReceiver(),
                            sdpMsgWrapper.getNonce()
                    )
            );
            log.info("sdp msg {} already confirmed after committing, update its nonce {}", sdpMsgWrapper.getId(), sdpMsgWrapper.getNonce());
        }

        // 5. 若支持可靠上链特性，添加消息到可靠上链记录表
        if (ifReliable && sdpMsgWrapper.getAtomicFlag() != AtomicFlagEnum.ACK_RECEIVE_TX_FAILED.getValue()) {
            ReliableCrossChainMessage rccMessage = new ReliableCrossChainMessage(
                    new IdempotentInfo(
                            sdpMsgWrapper.getSenderBlockchainDomain(),
                            HexUtil.decodeHex(sdpMsgWrapper.getMsgSender()),
                            sdpMsgWrapper.getReceiverBlockchainDomain(),
                            HexUtil.decodeHex(sdpMsgWrapper.getMsgReceiver()),
                            sdpMsgWrapper.getSdpMessage().getNonce()
                    ),
                    calculateRCCMsgState(res.isConfirmed(), res.isSuccess(), isTimeout),
                    res.getTxId(),
                    res.getTxTimestamp(),
                    res.getRawTx()
            );
            crossChainMessageRepository.putReliableMessage(rccMessage);
            log.info("AMCommitter: put message to rcc repository {}",
                    rccMessage.getInfo());
        }
    }

    private SDPMsgProcessStateEnum calculateSDPProcessState(boolean isConfirmed, boolean isSuccess, boolean isTimeout) {
        if (isTimeout) {
            return SDPMsgProcessStateEnum.TIMEOUT;
        }
        if (isConfirmed) {
            return isSuccess ? SDPMsgProcessStateEnum.TX_SUCCESS : SDPMsgProcessStateEnum.TX_FAILED;
        }
        if (!isSuccess) {
            return SDPMsgProcessStateEnum.PENDING;
        }
        return SDPMsgProcessStateEnum.TX_PENDING;
    }

    private ReliableCrossChainMsgProcessStateEnum calculateRCCMsgState(boolean isConfirmed, boolean isSuccess, boolean isTimeout) {
        if (isTimeout) {
            return ReliableCrossChainMsgProcessStateEnum.FAILED;
        }
        if (isConfirmed) {
            return isSuccess ? ReliableCrossChainMsgProcessStateEnum.SUCCESS : ReliableCrossChainMsgProcessStateEnum.FAILED;
        }
        return ReliableCrossChainMsgProcessStateEnum.PENDING;
    }

    public long getSDPMsgSeqOnChain(String product, String blockchainId, SDPMsgWrapper sdpMsgWrapper) {

        try {
            return blockchainClientPool.getClient(product, blockchainId)
                    .getSDPMsgClientContract()
                    .querySDPMsgSeqOnChain(
                            sdpMsgWrapper.getSenderBlockchainDomain(),
                            sdpMsgWrapper.getMsgSender(),
                            sdpMsgWrapper.getReceiverBlockchainDomain(),
                            sdpMsgWrapper.getMsgReceiver()
                    );

        } catch (Exception e) {
            log.error(
                    "getSDPMsgSeqOnChain failed for ( sender_blockchain: {}-{}, sender: {}, receiver_blockchain: {}-{}, receiver: {} ) : ",
                    sdpMsgWrapper.getSenderBlockchainProduct(),
                    sdpMsgWrapper.getSenderBlockchainDomain(),
                    sdpMsgWrapper.getMsgSender(),
                    sdpMsgWrapper.getReceiverBlockchainProduct(),
                    sdpMsgWrapper.getReceiverBlockchainDomain(),
                    sdpMsgWrapper.getMsgReceiver(),
                    e
            );
            return 0;
        }
    }

    @Getter
    @AllArgsConstructor
    public static class ParsedSDPMsgSet {

        /**
         * expired msg shouldn't upload to chain
         */
        private final List<SDPMsgWrapper> expired;

        /**
         * should upload to chain
         */
        private final List<SDPMsgWrapper> upload;

        public ParsedSDPMsgSet() {
            this.expired = new ArrayList<>();
            this.upload = new ArrayList<>();
        }
    }

    private ParsedSDPMsgSet parseSDPMsgList(String product, String blockchainId, List<SDPMsgWrapper> msgs) {
        long seqOnChain = -1;
        ParsedSDPMsgSet set = new ParsedSDPMsgSet();
        long lastIndex = seqOnChain; // NOTE: this is the first seq which need to send to blockchain

        for (SDPMsgWrapper msg : msgs) { // precondition: msgs was sorted

            if (SDPMsgWrapper.UNORDERED_SDP_MSG_SEQ == msg.getMsgSequence() || msg.isAck()) {
                set.getUpload().add(msg);
                continue;
            }

            if (seqOnChain == -1) {
                // NOTE: Always use the sequence number of session on blockchain
                seqOnChain = getSDPMsgSeqOnChain(product, blockchainId, msgs.get(0));
                lastIndex = seqOnChain; // NOTE: this is the first seq which need to send to blockchain

                log.info(
                        "session ( sender_blockchain: {}-{}, sender: {}, receiver_blockchain: {}-{}, receiver: {} ) seq on chain is {}",
                        msg.getSenderBlockchainProduct(),
                        msg.getSenderBlockchainDomain(),
                        msg.getMsgSender(),
                        msg.getReceiverBlockchainProduct(),
                        msg.getReceiverBlockchainDomain(),
                        msg.getMsgReceiver(),
                        seqOnChain
                );
            }

            if (msg.getMsgSequence() < seqOnChain) {
                log.info(
                        "ordered sdp msg ( sender_blockchain: {}-{}, sender: {}, receiver_blockchain: {}-{}, receiver: {} ) seq is expired: seq on chain is {} and seq in msg is {}",
                        msg.getSenderBlockchainProduct(),
                        msg.getSenderBlockchainDomain(),
                        msg.getMsgSender(),
                        msg.getReceiverBlockchainProduct(),
                        msg.getReceiverBlockchainDomain(),
                        msg.getMsgReceiver(),
                        seqOnChain,
                        msg.getMsgSequence()
                );
                set.getExpired().add(msg);
                continue;
            }

            if (msg.getMsgSequence() == lastIndex) { // collect consecutive msgs
                log.info(
                        "put ordered sdp msg with seq {} into upload list for session ( sender_blockchain: {}-{}, sender: {}, receiver_blockchain: {}-{}, receiver: {} )",
                        msg.getMsgSequence(),
                        msg.getSenderBlockchainProduct(),
                        msg.getSenderBlockchainDomain(),
                        msg.getMsgSender(),
                        msg.getReceiverBlockchainProduct(),
                        msg.getReceiverBlockchainDomain(),
                        msg.getMsgReceiver()
                );
                set.getUpload().add(msg);
                lastIndex++;
                continue;
            }

            log.warn(
                    "unhandled ordered msg seq {} for session ( sender_blockchain: {}-{}, sender: {}, receiver_blockchain: {}-{}, receiver: {} )",
                    msg.getMsgSequence(),
                    msg.getSenderBlockchainProduct(),
                    msg.getSenderBlockchainDomain(),
                    msg.getMsgSender(),
                    msg.getReceiverBlockchainProduct(),
                    msg.getReceiverBlockchainDomain(),
                    msg.getMsgReceiver()
            );
        }
        return set;
    }

    private void sortSDPMsgList(List<SDPMsgWrapper> msgs) {
        ListUtil.sort(
                msgs,
                Comparator.comparingInt(SDPMsgWrapper::getMsgSequence)
        );
    }
}

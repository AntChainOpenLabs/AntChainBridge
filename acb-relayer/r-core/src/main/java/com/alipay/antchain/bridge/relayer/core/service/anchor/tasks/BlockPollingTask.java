package com.alipay.antchain.bridge.relayer.core.service.anchor.tasks;

import com.alipay.antchain.bridge.relayer.commons.exception.AntChainBridgeRelayerException;
import com.alipay.antchain.bridge.relayer.commons.exception.RelayerErrorCodeEnum;
import com.alipay.antchain.bridge.relayer.core.service.anchor.context.AnchorProcessContext;
import lombok.extern.slf4j.Slf4j;

/**
 * block polling task, it is responsible for sync remote block header
 */
@Slf4j
public class BlockPollingTask extends BlockBaseTask {

    public BlockPollingTask(AnchorProcessContext processContext) {
        super(BlockTaskTypeEnum.POLLING, processContext);
    }

    @Override
    public void doProcess() {
        try {
            long latestHeight = queryRemoteBlockHeaderHeight();
            long recordedHeight = getRemoteBlockHeaderHeight();
            ensureNoRemoteHeightRollback(
                    recordedHeight,
                    latestHeight,
                    getProcessContext().getBlockchainMeta().getMetaKey()
            );
            if (recordedHeight < latestHeight) {
                log.info("polling height {} remote block header from {}", latestHeight, getProcessContext().getBlockchainMeta().getMetaKey());
                saveRemoteBlockHeaderHeight(latestHeight);
            }
        } catch (Exception e) {
            throw new AntChainBridgeRelayerException(
                    RelayerErrorCodeEnum.SERVICE_MULTI_ANCHOR_PROCESS_POLLING_TASK_FAILED,
                    e,
                    "query remote block header height failed for {}",
                    getProcessContext().getBlockchainMeta().getMetaKey()
            );
        }
    }

    private long queryRemoteBlockHeaderHeight() {
        return getProcessContext().getBlockchainClient().getLastBlockHeight();
    }

    static void ensureNoRemoteHeightRollback(long recordedHeight, long latestHeight, String metaKey) {
        if (recordedHeight <= latestHeight) {
            return;
        }
        throw new IllegalStateException(
                String.format(
                        "remote blockchain height rollback detected for %s: recorded=%d, remote=%d; "
                                + "stop the anchor and reconcile polling/sync/notify cursors before restart",
                        metaKey,
                        recordedHeight,
                        latestHeight
                )
        );
    }
}

package com.alipay.antchain.bridge.plugins.dioxide2.conf;

import java.util.List;
import java.util.Set;

public class DioxideTypes {

    // 全局常量
    public static final int GLOBAL_IDENTIFIER = 65535;

    // Scope
    public enum Scope {
        Global(0),
        Shard(1),
        Address(2);

        private final int value;

        Scope(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }
    }


    // EngineID
    public enum EngineID {
        Core(1),
        Native(2),
        GCL_NATIVE(3),
        GCL_WASM(4),
        SOLIDITY_EVM(5);

        private final int value;

        EngineID(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }
    }

    // BlockState
    public enum BlockState {
        DUS_RECEIVED(0),
        DUS_INVALID(1),
        DUS_EXCUTED(2),
        DUS_FORKED(3),
        DUS_FINALIZED(4),
        DUS_ARCHIVED(5),
        DUS_ARCHIVED_UNCLE(6);

        private final int value;

        BlockState(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }
    }

    // BlkCommitState
    public enum BlkCommitState {
        BLKCS_INIT(0),
        BLKCS_PENDING_BLOCKID(1),
        BLKCS_PENDING_COMMIT(2),
        BLKCS_PENDING_SHARD_COMMIT(3),
        BLKCS_CONFIRMED(4),
        BLKCS_IGNORED(5),
        BLKCS_COMMIT_ERROR(6),
        BLKCS_REBASING(7);

        private final int value;

        BlkCommitState(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }
    }

    // BlkFinalityState
    public enum BlkFinalityState {
        BLKFS_NONE(0),
        BLKFS_FINALIZED(1),
        BLKFS_UNCLED(2),
        BLKFS_ORPHANED(3);

        private final int value;

        BlkFinalityState(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }
    }

    // BlkArchiveState
    public enum BlkArchiveState {
        BLKAS_NONE(0),
        BLKAS_DISCARD(1),
        BLKAS_ARCHIVING(2),
        BLKAS_ARCHIVED(3);

        private final int value;

        BlkArchiveState(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }
    }

    // TxnConfirmState
    public enum TxnConfirmState {
        TXN_RELAY_INVALIDED(-3),
        TXN_READY(0),
        TXN_CONFIRMED(1),
        TXN_FINALIZED(2),
        TXN_ABORTED(3),
        TXN_EXPIRED(4),
        TXN_ARCHIVED(5);

        private final int value;

        TxnConfirmState(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }
    }

    // Txn 状态集合
    public static final Set<String> TXN_CONFIRMED_STATUS = Set.of(
            TxnConfirmState.TXN_ARCHIVED.name(),
            TxnConfirmState.TXN_CONFIRMED.name(),
            TxnConfirmState.TXN_FINALIZED.name()
    );

    public static final Set<String> TXN_FINALIZED_STATUS = Set.of(
            TxnConfirmState.TXN_ARCHIVED.name(),
            TxnConfirmState.TXN_FINALIZED.name()
    );

    public static final Set<String> TXN_ARCHIVED_STATUS = Set.of(
            TxnConfirmState.TXN_ARCHIVED.name()
    );

    // SubscribeTopic
    public enum SubscribeTopic {
        CONSENSUS_HEADER,
        TRANSACTION_BLOCK,
        TRANSACTION,
        STATE,
        RELAYS,
        FINALIZED_BLOCK_AND_TRANSACTION,
        MEMPOOL
    }

    // 各种 KeyName 列表
    public static final List<String> ScatterMapStateMsgKeyName = List.of(
            "GlobalScatteredMaps",
            "ScatteredMapOnShard_CloneScale",
            "ScatteredMapOnShard_SplitScale"
    );

    public static final List<String> KeyedStateMsgKeyName = List.of(
            "KeyedScopeStates",
            "GlobalScatteredMaps",
            "ScatteredMapOnShard_CloneScale",
            "ScatteredMapOnShard_SplitScale"
    );

    public static final List<String> NonKeyedStateMsgKeyName = List.of(
            "GlobalStates",
            "ShardStates"
    );

    public static final List<String> AllStateMsgKeyName = List.of(
            "KeyedScopeStates",
            "GlobalScatteredMaps",
            "ScatteredMapOnShard_CloneScale",
            "ScatteredMapOnShard_SplitScale",
            "GlobalStates",
            "ShardStates"
    );
}

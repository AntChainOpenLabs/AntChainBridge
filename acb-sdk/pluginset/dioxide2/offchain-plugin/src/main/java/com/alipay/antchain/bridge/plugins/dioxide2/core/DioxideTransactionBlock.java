package com.alipay.antchain.bridge.plugins.dioxide2.core;

import com.alibaba.fastjson.annotation.JSONField;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DioxideTransactionBlock {

    @JSONField(name = "Size")
    private Integer size;

    @JSONField(name = "Version")
    private Integer version;

    @JSONField(name = "Scope")
    private String scope;

    // [shard_index, shard_order]
    @JSONField(name = "Shard")
    private List<Integer> shard;

    @JSONField(name = "Prev")
    private String prev;

    @JSONField(name = "ScheduledTxnCount")
    private Integer scheduledTxnCount;

    @JSONField(name = "UserInitiatedTxnCount")
    private Integer userInitiatedTxnCount;

    @JSONField(name = "IntraRelayTxnCount")
    private Integer intraRelayTxnCount;

    @JSONField(name = "InboundRelayTxnCount")
    private Integer inboundRelayTxnCount;

    @JSONField(name = "OutboundRelayTxnCount")
    private Integer outboundRelayTxnCount;

    @JSONField(name = "DeferredRelayTxnCount")
    private Integer deferredRelayTxnCount;

    @JSONField(name = "DispatchedRelayTxnCount")
    private Integer dispatchedRelayTxnCount;

    @JSONField(name = "ExecutionCount")
    private Integer executionCount;

    @JSONField(name = "ConsensusHeaderHash")
    private String consensusHeaderHash;

    @JSONField(name = "ConfirmedTxnMerkle")
    private String confirmedTxnMerkle;

    @JSONField(name = "ChainStateMerkle")
    private String chainStateMerkle;

    @JSONField(name = "Hash")
    private String hash;

    @JSONField(name = "Height")
    private Long height;

    @JSONField(name = "Timestamp")
    private Long timestamp;

    @JSONField(name = "Miner")
    private String miner;

    @JSONField(name = "State")
    private String state;

    @JSONField(name = "Transactions")
    private Transactions transactions;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Transactions {

        @JSONField(name = "Scheduled")
        private List<String> scheduled;

        @JSONField(name = "DispatchedRelays")
        private List<String> dispatchedRelays;

        @JSONField(name = "OutboundRelays")
        private List<String> outboundRelays;

        @JSONField(name = "Deferred")
        private List<String> deferred;

        @JSONField(name = "Confirmed")
        private List<String> confirmed;
    }
}


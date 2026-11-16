package com.alipay.antchain.bridge.plugins.dioxide.core;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
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
public class DioxideTransaction {

    @JSONField(name = "Hash")
    private String txHash;

    @JSONField(name = "ISN")
    private Long isn;

    @JSONField(name = "Signers")
    private List<String> signers;

    @JSONField(name = "Timestamp")
    private Long timestamp;

    @JSONField(name = "Relays")
    private List<DioxideTransaction> embeddedRelays;

    @JSONField(name = "GasOffered")
    private Integer gasOffered;

    @JSONField(name = "GasPrice")
    private String gasPrice;

    @JSONField(name = "Grouped")
    private boolean grouped;

    @JSONField(name = "uTxnSize")
    private Integer uTxnSize;

    @JSONField(name = "Mode")
    private String mode;

    @JSONField(name = "Function")
    private String function;

    // target: only some relay tx has, represents event name
    @JSONField(name = "Target")
    private String target;

    //Input字段不嵌套
    @JSONField(name = "Input")
    private Object input;

    @JSONField(name = "Invocation")
    private Invocation invocation;

    @JSONField(name = "ExecStage")
    private String stage;

    @JSONField(name = "Height")
    private Long height;

    // Shard 字段是一个数组 [shard, shardOrder]
    @JSONField(name = "Shard")
    private List<Integer> shard;

    @JSONField(name = "ConfirmState")
    private String confirmState;

    /**
     * Dioxide nodes may expose the durable transaction state without a
     * ConfirmState (notably for the final relay transactions). Keep both
     * fields so finality can be derived from the node's actual response.
     */
    @JSONField(name = "State")
    private String state;


    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Invocation {

        @JSONField(name = "Status")
        private String status;

        @JSONField(name = "Return")
        private List<Integer> returnValues;

        @JSONField(name = "GasFee")
        private String gasFee;

        @JSONField(name = "CoinDelta")
        private String coinDelta;

        @JSONField(name = "Relays")
        private List<String> relays;
    }


    public String getInputAsString() {
        if (input instanceof String) {
            return (String) input;
        }
        return null;
    }

    public JSONObject getInputAsJson() {
        if (input instanceof JSONObject) {
            return (JSONObject) input;
        }
        // 如果 input 是 JSON 字符串，也尝试解析
        if (input instanceof String && ((String) input).trim().startsWith("{")) {
            return JSON.parseObject((String) input);
        }
        return null;
    }
}

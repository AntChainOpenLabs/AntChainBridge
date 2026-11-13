package com.alipay.antchain.bridge.plugins.ethereum3.core.eth.beacon;

import com.alibaba.fastjson.annotation.JSONField;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class ObjectAndMetaData {

    @JSONField(name = "version")
    private String version;

    @JSONField(name = "execution_optimistic")
    private Boolean executionOptimistic;

    @JSONField(name = "finalized")
    private Boolean finalized;

    @JSONField(name = "data")
    private String data;
}

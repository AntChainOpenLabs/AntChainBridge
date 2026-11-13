package com.alipay.antchain.bridge.plugins.ethereum3.core.eth.eth2spec;

import java.math.BigInteger;

import cn.hutool.core.util.ArrayUtil;
import cn.hutool.crypto.digest.DigestUtil;
import com.alibaba.fastjson.annotation.JSONField;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
public class Fork {

    public Fork(String name, BigInteger epoch, byte[] version) {
        this.name = name;
        this.epoch = epoch;
        this.version = version;
    }

    private String name;

    private BigInteger epoch;

    private byte[] version;

    @JSONField(serialize = false, deserialize = false)
    private byte[] domain;

    public void computeDomain(byte[] genesisValidatorsRoot) {
        var forkVersion32 = ArrayUtil.addAll(this.version, new byte[28]);
        var h = DigestUtil.sha256(ArrayUtil.addAll(forkVersion32, genesisValidatorsRoot));
        var prefix = new byte[]{7, 0, 0, 0};
        domain = ArrayUtil.addAll(prefix, ArrayUtil.sub(h, 0, 28));
    }
}

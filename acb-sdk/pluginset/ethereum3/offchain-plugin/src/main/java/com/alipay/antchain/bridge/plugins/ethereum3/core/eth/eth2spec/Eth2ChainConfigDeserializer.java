package com.alipay.antchain.bridge.plugins.ethereum3.core.eth.eth2spec;

import java.lang.reflect.Type;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson.parser.DefaultJSONParser;
import com.alibaba.fastjson.parser.deserializer.ObjectDeserializer;

public class Eth2ChainConfigDeserializer implements ObjectDeserializer {
    @Override
    public <T> T deserialze(DefaultJSONParser parser, Type type, Object fieldName) {
        String value = parser.parseObject(String.class);
        if (StrUtil.isEmpty(value) || StrUtil.equals(value, "{}")) {
            return null;
        }
        return (T) Eth2ChainConfig.createChainConfigFromJson(value);
    }

    @Override
    public int getFastMatchToken() {
        return 0;
    }
}

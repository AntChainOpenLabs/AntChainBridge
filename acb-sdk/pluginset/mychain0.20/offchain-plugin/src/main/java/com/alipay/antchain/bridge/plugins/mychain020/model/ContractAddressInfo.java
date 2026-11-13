/*
 * Copyright 2024 Ant Group
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.alipay.antchain.bridge.plugins.mychain020.model;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.annotation.JSONField;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class ContractAddressInfo {

    public static ContractAddressInfo decode(String json) {
        return JSON.parseObject(json, ContractAddressInfo.class);
    }

    public static String resolveEvmContractAddress(String contractAddress) {
        if (StrUtil.isBlank(contractAddress) || contractAddress.trim().charAt(0) != '{') {
            return contractAddress;
        }

        String evmContractAddress = decode(contractAddress).getEvmContractAddress();
        if (StrUtil.isBlank(evmContractAddress)) {
            throw new IllegalArgumentException("missing evm contract address: " + contractAddress);
        }
        return evmContractAddress;
    }

    @JSONField(name = "evm")
    private String evmContractAddress;

    @JSONField(name = "wasm")
    private String wasmContractAddress;

    public String toJson() {
        return JSON.toJSONString(this);
    }

    public Set<String> toSet() {
        return Stream.of(evmContractAddress, wasmContractAddress)
                .filter(StrUtil::isNotBlank)
                .collect(Collectors.toSet());
    }
}

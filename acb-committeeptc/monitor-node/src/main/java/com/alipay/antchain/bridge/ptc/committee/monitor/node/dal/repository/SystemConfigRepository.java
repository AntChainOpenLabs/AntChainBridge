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

package com.alipay.antchain.bridge.ptc.committee.monitor.node.dal.repository;

import java.math.BigInteger;
import java.util.Map;

import cn.hutool.core.codec.Base64;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson.JSON;
import com.alipay.antchain.bridge.commons.core.ptc.PTCTrustRoot;
import com.alipay.antchain.bridge.ptc.committee.monitor.node.commons.exception.DataAccessLayerException;
import com.alipay.antchain.bridge.ptc.committee.monitor.node.dal.entities.SystemConfigEntity;
import com.alipay.antchain.bridge.ptc.committee.monitor.node.dal.mapper.SystemConfigMapper;
import com.alipay.antchain.bridge.ptc.committee.monitor.node.dal.repository.interfaces.ISystemConfigRepository;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import lombok.Synchronized;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class SystemConfigRepository implements ISystemConfigRepository {

    private static final String CURRENT_PTC_ANCHOR_VERSION = "current_ptc_anchor_version";

    private static final String CURRENT_PTC_TRUST_ROOT = "current_ptc_trust_root";

    @Resource
    private SystemConfigMapper systemConfigMapper;

    @Override
    public String getSystemConfig(String key) {
        try {
            var entity = systemConfigMapper.selectOne(
                    new LambdaQueryWrapper<SystemConfigEntity>()
                            .eq(SystemConfigEntity::getConfKey, key)
            );
            if (ObjectUtil.isNull(entity)) {
                return null;
            }
            return entity.getConfValue();
        } catch (Exception e) {
            throw new DataAccessLayerException(
                    e, "Failed to get system config with key: {}", key
            );
        }
    }

    @Override
    public boolean hasSystemConfig(String key) {
        try {
            return systemConfigMapper.exists(
                    new LambdaQueryWrapper<SystemConfigEntity>()
                            .eq(SystemConfigEntity::getConfKey, key)
            );
        } catch (Exception e) {
            throw new DataAccessLayerException(
                    e, "Failed to check existence of system config with key: {}", key
            );
        }
    }

    @Override
    public void setSystemConfig(Map<String, String> configs) {
        try {
            configs.forEach((key, value) -> {
                systemConfigMapper.insert(
                        SystemConfigEntity.builder()
                                .confKey(key)
                                .confValue(value)
                                .build()
                );
            });
        } catch (Exception e) {
            throw new DataAccessLayerException(
                    e, "Failed to set system config with key: {}", JSON.toJSONString(configs)
            );
        }
    }

    @Override
    @Synchronized
    public void setSystemConfig(String key, String value) {
        try {
            systemConfigMapper.insert(
                    SystemConfigEntity.builder()
                            .confKey(key)
                            .confValue(value)
                            .build()
            );
        } catch (Exception e) {
            throw new DataAccessLayerException(
                    e, "Failed to set system config with key: {}", key
            );
        }
    }

    @Override
    public BigInteger queryCurrentPtcAnchorVersion() {
        String curr = getSystemConfig(CURRENT_PTC_ANCHOR_VERSION);
        return new BigInteger(StrUtil.isEmpty(curr) ? "-1" : curr);
    }

    @Override
    public void setCurrentPtcAnchorVersion(BigInteger version) {
        setSystemConfig(CURRENT_PTC_ANCHOR_VERSION, version.toString());
    }

    @Override
    @Transactional(rollbackFor = DataAccessLayerException.class)
    public void setPtcTrustRoot(PTCTrustRoot ptcTrustRoot) {
        setSystemConfig(CURRENT_PTC_TRUST_ROOT, Base64.encode(ptcTrustRoot.encode()));
        setCurrentPtcAnchorVersion(
                ptcTrustRoot.getVerifyAnchorMap().keySet().stream().max(BigInteger::compareTo).orElse(BigInteger.ZERO)
        );
    }

    @Override
    public PTCTrustRoot getPtcTrustRoot() {
        return PTCTrustRoot.decode(Base64.decode(getSystemConfig(CURRENT_PTC_TRUST_ROOT)));
    }
}

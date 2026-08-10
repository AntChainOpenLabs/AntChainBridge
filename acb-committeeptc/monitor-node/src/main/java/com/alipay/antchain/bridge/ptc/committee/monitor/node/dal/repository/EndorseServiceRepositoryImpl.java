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
import java.util.Comparator;
import java.util.List;

import cn.hutool.core.collection.ListUtil;
import cn.hutool.core.util.ObjectUtil;
import com.alipay.antchain.bridge.commons.core.base.CrossChainLane;
import com.alipay.antchain.bridge.ptc.committee.monitor.node.commons.exception.DataAccessLayerException;
import com.alipay.antchain.bridge.ptc.committee.monitor.node.commons.models.BtaWrapper;
import com.alipay.antchain.bridge.ptc.committee.monitor.node.commons.models.TpBtaWrapper;
import com.alipay.antchain.bridge.ptc.committee.monitor.node.commons.models.ValidatedConsensusStateWrapper;
import com.alipay.antchain.bridge.ptc.committee.monitor.node.dal.convert.ConvertUtil;
import com.alipay.antchain.bridge.ptc.committee.monitor.node.dal.entities.BtaEntity;
import com.alipay.antchain.bridge.ptc.committee.monitor.node.dal.entities.TpBtaEntity;
import com.alipay.antchain.bridge.ptc.committee.monitor.node.dal.entities.ValidatedConsensusStatesEntity;
import com.alipay.antchain.bridge.ptc.committee.monitor.node.dal.mapper.BtaMapper;
import com.alipay.antchain.bridge.ptc.committee.monitor.node.dal.mapper.TpBtaMapper;
import com.alipay.antchain.bridge.ptc.committee.monitor.node.dal.mapper.ValidatedConsensusStatesMapper;
import com.alipay.antchain.bridge.ptc.committee.monitor.node.dal.repository.interfaces.IEndorseServiceRepository;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

@Component
public class EndorseServiceRepositoryImpl implements IEndorseServiceRepository {

    @Resource
    private BtaMapper btaMapper;

    @Resource
    private TpBtaMapper tpBtaMapper;

    @Resource
    private ValidatedConsensusStatesMapper validatedConsensusStatesMapper;

    @Override
    public TpBtaWrapper getMatchedTpBta(CrossChainLane lane) {
        try {
            var entityList = searchTpBta(lane, -1);
            if (ObjectUtil.isEmpty(entityList)) {
                return null;
            }
            return ConvertUtil.convertFrom(
                    entityList.stream().max(Comparator.comparingInt(TpBtaEntity::getTpbtaVersion)).get()
            );

        } catch (Exception e) {
            throw new DataAccessLayerException(
                    e, "Failed to get tpbta for lane {}", lane.getLaneKey()
            );
        }
    }

    @Override
    public TpBtaWrapper getMatchedTpBta(CrossChainLane lane, int tpbtaVersion) {
        try {
            var entityList = searchTpBta(lane, tpbtaVersion);
            if (ObjectUtil.isEmpty(entityList)) {
                return null;
            }
            return ConvertUtil.convertFrom(
                    entityList.stream().max(Comparator.comparingInt(TpBtaEntity::getTpbtaVersion)).get()
            );
        } catch (Exception e) {
            throw new DataAccessLayerException(
                    e, "Failed to get tpbta for lane {} and version {}", lane.getLaneKey(), tpbtaVersion
            );
        }
    }

    @Override
    public TpBtaWrapper getExactTpBta(CrossChainLane lane) {
        return getExactTpBta(lane, -1);
    }

    @Override
    public TpBtaWrapper getExactTpBta(CrossChainLane lane, int tpbtaVersion) {
        try {
            var wrapper = new LambdaQueryWrapper<TpBtaEntity>()
                    .eq(TpBtaEntity::getSenderDomain, lane.getSenderDomain().getDomain())
                    .eq(TpBtaEntity::getSenderId, ObjectUtil.isNull(lane.getSenderId()) ? "" : lane.getSenderId().toHex())
                    .eq(TpBtaEntity::getReceiverDomain, ObjectUtil.isNull(lane.getReceiverDomain()) ? "" : lane.getReceiverDomain().getDomain())
                    .eq(TpBtaEntity::getReceiverId, ObjectUtil.isNull(lane.getReceiverId()) ? "" : lane.getReceiverId().toHex());
            var entityList = tpBtaMapper.selectList(
                    tpbtaVersion == -1 ? wrapper : wrapper.eq(TpBtaEntity::getTpbtaVersion, tpbtaVersion)
            );
            if (ObjectUtil.isEmpty(entityList)) {
                return null;
            }
            return ConvertUtil.convertFrom(
                    entityList.stream().max(Comparator.comparingInt(TpBtaEntity::getTpbtaVersion)).get()
            );
        } catch (Exception e) {
            throw new DataAccessLayerException(
                    e, "Failed to get tpbta for lane {} and version {}", lane.getLaneKey(), tpbtaVersion
            );
        }
    }

    @Override
    public void setTpBta(TpBtaWrapper tpBtaWrapper) {
        try {
            if (hasTpBta(tpBtaWrapper.getCrossChainLane(), tpBtaWrapper.getTpbta().getTpbtaVersion())) {
                throw new RuntimeException("tpBta already exists");
            }
            tpBtaMapper.insert((TpBtaEntity) ConvertUtil.convertFrom(tpBtaWrapper));
        } catch (Exception e) {
            throw new DataAccessLayerException(
                    e, "Failed to save tpbta for lane {}", tpBtaWrapper.getCrossChainLane().getLaneKey()
            );
        }
    }

    @Override
    public boolean hasTpBta(CrossChainLane lane, int tpbtaVersion) {
        try {
            return tpBtaMapper.exists(
                    new LambdaQueryWrapper<TpBtaEntity>()
                            .eq(TpBtaEntity::getSenderDomain, lane.getSenderDomain().getDomain())
                            .eq(TpBtaEntity::getSenderId, ObjectUtil.isNull(lane.getSenderId()) ? "" : lane.getSenderId().toHex())
                            .eq(TpBtaEntity::getReceiverDomain, ObjectUtil.isNull(lane.getReceiverDomain()) ? "" : lane.getReceiverDomain().getDomain())
                            .eq(TpBtaEntity::getReceiverId, ObjectUtil.isNull(lane.getReceiverId()) ? "" : lane.getReceiverId().toHex())
                            .eq(TpBtaEntity::getTpbtaVersion, tpbtaVersion)
            );
        } catch (Exception e) {
            throw new DataAccessLayerException(
                    e, "Failed to check if tpbta for lane {} and version {} exist", lane.getLaneKey(), tpbtaVersion
            );
        }
    }

    @Override
    public BtaWrapper getBta(String domain) {
        try {
            var entityList = btaMapper.selectList(
                    new LambdaQueryWrapper<BtaEntity>()
                            .eq(BtaEntity::getDomain, domain)
            );
            if (ObjectUtil.isEmpty(entityList)) {
                return null;
            }
            return ConvertUtil.convertFrom(
                    entityList.stream().max(Comparator.comparingInt(BtaEntity::getSubjectVersion)).get()
            );
        } catch (Exception e) {
            throw new DataAccessLayerException(
                    e, "Failed to get bta for domain {}", domain
            );
        }
    }

    @Override
    public BtaWrapper getBta(String domain, int subjectVersion) {
        try {
            var entity = btaMapper.selectOne(
                    new LambdaQueryWrapper<BtaEntity>()
                            .eq(BtaEntity::getDomain, domain)
                            .eq(BtaEntity::getSubjectVersion, subjectVersion)
            );
            if (ObjectUtil.isNull(entity)) {
                return null;
            }
            return ConvertUtil.convertFrom(entity);
        } catch (Exception e) {
            throw new DataAccessLayerException(
                    e, "Failed to get bta for domain {} and version {}", domain, subjectVersion
            );
        }
    }

    @Override
    public void setBta(BtaWrapper btaWrapper) {
        try {
            if (hasBta(btaWrapper.getDomain(), btaWrapper.getBtaVersion())) {
                throw new RuntimeException("bta already exists");
            }
            btaMapper.insert((BtaEntity) ConvertUtil.convertFrom(btaWrapper));
        } catch (Exception e) {
            throw new DataAccessLayerException(
                    e, "Failed to save bta for domain {} and subject version {}", btaWrapper.getDomain(), btaWrapper.getSubjectVersion()
            );
        }
    }

    @Override
    public boolean hasBta(String domain, int subjectVersion) {
        try {
            return btaMapper.exists(
                    new LambdaQueryWrapper<BtaEntity>()
                            .eq(BtaEntity::getDomain, domain)
                            .eq(BtaEntity::getSubjectVersion, subjectVersion)
            );
        } catch (Exception e) {
            throw new DataAccessLayerException(
                    e, "Failed to check if bta for domain {} and version {} exist", domain, subjectVersion
            );
        }
    }

    @Override
    public ValidatedConsensusStateWrapper getLatestValidatedConsensusState(String domain) {
        try {
            var entity = validatedConsensusStatesMapper.getLatestValidatedConsensusState(domain);
            if (ObjectUtil.isNull(entity)) {
                return null;
            }
            return ConvertUtil.convertFrom(entity);
        } catch (Exception e) {
            throw new DataAccessLayerException(
                    e, "Failed to get latest validated consensus state for domain {}", domain
            );
        }
    }

    @Override
    public ValidatedConsensusStateWrapper getValidatedConsensusState(String domain, BigInteger height) {
        try {
            var entity = validatedConsensusStatesMapper.selectOne(
                    new LambdaQueryWrapper<ValidatedConsensusStatesEntity>()
                            .eq(ValidatedConsensusStatesEntity::getDomain, domain)
                            .eq(ValidatedConsensusStatesEntity::getHeight, height.toString())
            );
            if (ObjectUtil.isNull(entity)) {
                return null;
            }
            return ConvertUtil.convertFrom(entity);
        } catch (Exception e) {
            throw new DataAccessLayerException(
                    e, "Failed to get validated consensus state for domain {} and height {}", domain, height
            );
        }
    }

    @Override
    public ValidatedConsensusStateWrapper getValidatedConsensusState(String domain, String hash) {
        try {
            var entity = validatedConsensusStatesMapper.selectOne(
                    new LambdaQueryWrapper<ValidatedConsensusStatesEntity>()
                            .eq(ValidatedConsensusStatesEntity::getDomain, domain)
                            .eq(ValidatedConsensusStatesEntity::getHash, hash)
            );
            if (ObjectUtil.isNull(entity)) {
                return null;
            }
            return ConvertUtil.convertFrom(entity);
        } catch (Exception e) {
            throw new DataAccessLayerException(
                    e, "Failed to get validated consensus state for domain {} and hash {}", domain, hash
            );
        }
    }

    @Override
    public void setValidatedConsensusState(ValidatedConsensusStateWrapper validatedConsensusStateWrapper) {
        try {
            if (hasValidatedConsensusState(validatedConsensusStateWrapper.getDomain(), validatedConsensusStateWrapper.getHeight())) {
                throw new RuntimeException("validated consensus state already exists");
            }
            validatedConsensusStatesMapper.insert((ValidatedConsensusStatesEntity) ConvertUtil.convertFrom(validatedConsensusStateWrapper));
        } catch (Exception e) {
            throw new DataAccessLayerException(
                    e, "Failed to save validated consensus state for domain {} and height {}",
                    validatedConsensusStateWrapper.getDomain(),
                    validatedConsensusStateWrapper.getHeight()
            );
        }
    }

    @Override
    public boolean hasValidatedConsensusState(String domain, BigInteger height) {
        try {
            return validatedConsensusStatesMapper.exists(
                    new LambdaQueryWrapper<ValidatedConsensusStatesEntity>()
                            .eq(ValidatedConsensusStatesEntity::getDomain, domain)
                            .eq(ValidatedConsensusStatesEntity::getHeight, height.toString())
            );
        } catch (Exception e) {
            throw new DataAccessLayerException(
                    e, "Failed to check if validated consensus state for domain {} and height {} exist", domain, height
            );
        }
    }

    private List<TpBtaEntity> searchTpBta(CrossChainLane lane, int tpbtaVersion) {
        // search the blockchain level first
        var wrapper = new LambdaQueryWrapper<TpBtaEntity>()
                .eq(TpBtaEntity::getSenderDomain, lane.getSenderDomain().getDomain())
                .eq(TpBtaEntity::getReceiverDomain, "")
                .eq(TpBtaEntity::getSenderId, "")
                .eq(TpBtaEntity::getReceiverId, "");
        var entityList = tpBtaMapper.selectList(
                tpbtaVersion == -1 ? wrapper : wrapper.eq(TpBtaEntity::getTpbtaVersion, tpbtaVersion)
        );
        if (ObjectUtil.isNotEmpty(entityList)) {
            return entityList;
        }

        if (ObjectUtil.isNull(lane.getReceiverDomain()) || ObjectUtil.isEmpty(lane.getReceiverDomain().getDomain())) {
            return ListUtil.empty();
        }
        // search the channel level
        wrapper = new LambdaQueryWrapper<TpBtaEntity>()
                .eq(TpBtaEntity::getSenderDomain, lane.getSenderDomain().getDomain())
                .eq(TpBtaEntity::getReceiverDomain, lane.getReceiverDomain().getDomain())
                .eq(TpBtaEntity::getSenderId, "")
                .eq(TpBtaEntity::getReceiverId, "");
        entityList = tpBtaMapper.selectList(
                tpbtaVersion == -1 ? wrapper : wrapper.eq(TpBtaEntity::getTpbtaVersion, tpbtaVersion)
        );
        if (ObjectUtil.isNotEmpty(entityList)) {
            return entityList;
        }

        if (ObjectUtil.isNull(lane.getSenderId()) || ObjectUtil.isNull(lane.getReceiverId())
                || ObjectUtil.isEmpty(lane.getSenderId().getRawID()) || ObjectUtil.isEmpty(lane.getReceiverId().getRawID())) {
            return ListUtil.empty();
        }
        // search the lane level
        wrapper = new LambdaQueryWrapper<TpBtaEntity>()
                .eq(TpBtaEntity::getSenderDomain, lane.getSenderDomain().getDomain())
                .eq(TpBtaEntity::getSenderId, lane.getSenderId().toHex())
                .eq(TpBtaEntity::getReceiverDomain, lane.getReceiverDomain().getDomain())
                .eq(TpBtaEntity::getReceiverId, lane.getReceiverId().toHex());
        entityList = tpBtaMapper.selectList(
                tpbtaVersion == -1 ? wrapper : wrapper.eq(TpBtaEntity::getTpbtaVersion, tpbtaVersion)
        );
        if (ObjectUtil.isNotEmpty(entityList)) {
            return entityList;
        }

        return ListUtil.empty();
    }
}

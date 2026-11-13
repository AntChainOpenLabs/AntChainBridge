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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import cn.hutool.core.codec.Base64;
import cn.hutool.core.collection.ListUtil;
import cn.hutool.core.util.HexUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.alipay.antchain.bridge.commons.bcdns.CrossChainCertificateFactory;
import com.alipay.antchain.bridge.commons.core.base.CrossChainDomain;
import com.alipay.antchain.bridge.commons.core.base.ObjectIdentity;
import com.alipay.antchain.bridge.ptc.committee.monitor.node.commons.enums.BCDNSStateEnum;
import com.alipay.antchain.bridge.ptc.committee.monitor.node.commons.exception.DataAccessLayerException;
import com.alipay.antchain.bridge.ptc.committee.monitor.node.commons.models.BCDNSServiceDO;
import com.alipay.antchain.bridge.ptc.committee.monitor.node.commons.models.DomainSpaceCertWrapper;
import com.alipay.antchain.bridge.ptc.committee.monitor.node.dal.convert.ConvertUtil;
import com.alipay.antchain.bridge.ptc.committee.monitor.node.dal.entities.BCDNSServiceEntity;
import com.alipay.antchain.bridge.ptc.committee.monitor.node.dal.entities.DomainSpaceCertEntity;
import com.alipay.antchain.bridge.ptc.committee.monitor.node.dal.mapper.BCDNSServiceMapper;
import com.alipay.antchain.bridge.ptc.committee.monitor.node.dal.mapper.DomainSpaceCertMapper;
import com.alipay.antchain.bridge.ptc.committee.monitor.node.dal.repository.interfaces.IBCDNSRepository;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Slf4j
public class BCDNSRepositoryImpl implements IBCDNSRepository {

    @Resource
    private BCDNSServiceMapper bcdnsServiceMapper;

    @Resource
    private DomainSpaceCertMapper domainSpaceCertMapper;

    @Override
    public long countBCDNSService() {
        return bcdnsServiceMapper.selectCount(null);
    }

    @Override
    public boolean hasDomainSpaceCert(String domainSpace) {
        return domainSpaceCertMapper.exists(
                new LambdaQueryWrapper<DomainSpaceCertEntity>()
                        .eq(DomainSpaceCertEntity::getDomainSpace, domainSpace)
        );
    }

    @Override
    public void saveDomainSpaceCert(DomainSpaceCertWrapper domainSpaceCertWrapper) {
        if (hasDomainSpaceCert(domainSpaceCertWrapper.getDomainSpace())) {
            log.info("domain space cert for {} already exists", domainSpaceCertWrapper.getDomainSpace());
            return;
        }

        try {
            domainSpaceCertMapper.insert((DomainSpaceCertEntity) ConvertUtil.convertFrom(domainSpaceCertWrapper));
        } catch (Exception e) {
            throw new DataAccessLayerException(
                    e, "save domain space cert for {} failed", domainSpaceCertWrapper.getDomainSpace()
            );
        }
    }

    @Override
    public DomainSpaceCertWrapper getDomainSpaceCert(String domainSpace) {
        try {
            var entity = domainSpaceCertMapper.selectOne(
                    new LambdaQueryWrapper<DomainSpaceCertEntity>()
                            .select(ListUtil.toList(DomainSpaceCertEntity::getDomainSpaceCert))
                            .eq(DomainSpaceCertEntity::getDomainSpace, domainSpace)
            );
            if (ObjectUtil.isNull(entity)) {
                return null;
            }
            return new DomainSpaceCertWrapper(
                    CrossChainCertificateFactory.createCrossChainCertificate(entity.getDomainSpaceCert())
            );
        } catch (Exception e) {
            throw new DataAccessLayerException(
                    e, "get domain space cert for {} failed", domainSpace
            );
        }
    }

    @Override
    public DomainSpaceCertWrapper getDomainSpaceCert(ObjectIdentity ownerOid) {
        try {
            var entityList = domainSpaceCertMapper.selectList(
                    new LambdaQueryWrapper<DomainSpaceCertEntity>()
                            .eq(DomainSpaceCertEntity::getOwnerOidHex, HexUtil.encodeHexStr(ownerOid.encode()))
            );
            if (ObjectUtil.isEmpty(entityList)) {
                return null;
            }
            return ConvertUtil.convertFrom(entityList.getFirst());
        } catch (Exception e) {
            throw new DataAccessLayerException(
                    e,
                    "failed to get domain space certificate by owner oid {}",
                    HexUtil.encodeHexStr(ownerOid.encode())
            );
        }
    }

    @Override
    public Map<String, DomainSpaceCertWrapper> getDomainSpaceCertChain(String leafDomainSpace) {
        Map<String, DomainSpaceCertWrapper> result = new HashMap<>();
        try {
            addDomainSpaceCert(leafDomainSpace, result);
        } catch (Exception e) {
            throw new DataAccessLayerException(
                    e,
                    "failed to get domain space certificate chain for space {}",
                    leafDomainSpace
            );
        }

        return result;
    }

    @Override
    public List<String> getDomainSpaceChain(String leafDomainSpace) {
        List<String> result = ListUtil.toList(leafDomainSpace);
        String currDomainSpace = leafDomainSpace;
        try {
            do {
                if (StrUtil.equals(leafDomainSpace, CrossChainDomain.ROOT_DOMAIN_SPACE)) {
                    break;
                }
                DomainSpaceCertEntity entity = domainSpaceCertMapper.selectOne(
                        new LambdaQueryWrapper<DomainSpaceCertEntity>()
                                .select(ListUtil.toList(DomainSpaceCertEntity::getParentSpace))
                                .eq(DomainSpaceCertEntity::getDomainSpace, leafDomainSpace)
                );
                if (ObjectUtil.isNull(entity)) {
                    throw new RuntimeException(StrUtil.format("none data found for domain space {}", currDomainSpace));
                }
                result.add(entity.getParentSpace());
                currDomainSpace = entity.getParentSpace();
            } while (StrUtil.isNotEmpty(currDomainSpace));
        } catch (Exception e) {
            throw new DataAccessLayerException(
                    e,
                    "failed to get domain space certificate chain for space {}",
                    leafDomainSpace
            );
        }
        return result;
    }

    @Override
    public boolean hasBCDNSService(String domainSpace) {
        try {
            return bcdnsServiceMapper.exists(
                    new LambdaQueryWrapper<BCDNSServiceEntity>()
                            .eq(BCDNSServiceEntity::getDomainSpace, domainSpace)
            );
        } catch (Exception e) {
            throw new DataAccessLayerException(
                    e, "check if bcdns service for {} exist failed", domainSpace
            );
        }
    }

    @Override
    public BCDNSServiceDO getBCDNSServiceDO(String domainSpace) {
        try {
            var entity = bcdnsServiceMapper.selectOne(
                    new LambdaQueryWrapper<BCDNSServiceEntity>()
                            .eq(BCDNSServiceEntity::getDomainSpace, domainSpace)
            );
            if (ObjectUtil.isNull(entity)) {
                return null;
            }
            BCDNSServiceDO bcdnsServiceDO = ConvertUtil.convertFrom(entity);
            bcdnsServiceDO.setDomainSpaceCertWrapper(getDomainSpaceCert(domainSpace));
            return bcdnsServiceDO;
        } catch (Exception e) {
            throw new DataAccessLayerException(
                    e, "get bcdns service for {} failed", domainSpace
            );
        }
    }

    @Override
    @Transactional(rollbackFor = DataAccessLayerException.class)
    public void deleteBCDNSServiceDO(String domainSpace) {
        try {
            domainSpaceCertMapper.delete(
                    new LambdaQueryWrapper<DomainSpaceCertEntity>()
                            .eq(DomainSpaceCertEntity::getDomainSpace, domainSpace)
            );
            bcdnsServiceMapper.delete(
                    new LambdaQueryWrapper<BCDNSServiceEntity>()
                            .eq(BCDNSServiceEntity::getDomainSpace, domainSpace)
            );
        } catch (Exception e) {
            throw new DataAccessLayerException(
                    e,
                    "failed to delete bcdns data for space [{}]",
                    domainSpace
            );
        }
    }

    @Override
    public List<String> getAllBCDNSDomainSpace() {
        try {
            var domainSpaceCertEntities = domainSpaceCertMapper.selectList(
                    new LambdaQueryWrapper<DomainSpaceCertEntity>()
                            .select(ListUtil.toList(DomainSpaceCertEntity::getDomainSpace))
            );
            if (ObjectUtil.isEmpty(domainSpaceCertEntities)) {
                return new ArrayList<>();
            }

            return domainSpaceCertEntities.stream().map(DomainSpaceCertEntity::getDomainSpace).collect(Collectors.toList());
        } catch (Exception e) {
            throw new DataAccessLayerException(
                    e,
                    "failed to get all domain space for bcdns"
            );
        }
    }

    @Override
    @Transactional(rollbackFor = DataAccessLayerException.class)
    public void saveBCDNSServiceDO(BCDNSServiceDO bcdnsServiceDO) {
        try {
            bcdnsServiceMapper.insert((BCDNSServiceEntity) ConvertUtil.convertFrom(bcdnsServiceDO));
            domainSpaceCertMapper.insert((DomainSpaceCertEntity) ConvertUtil.convertFrom(bcdnsServiceDO.getDomainSpaceCertWrapper()));
        } catch (Exception e) {
            throw new DataAccessLayerException(
                    e, "save bcdns service failed, domainSpace = {}",
                    bcdnsServiceDO.getDomainSpace()
            );
        }
    }

    @Override
    public void updateBCDNSServiceState(String domainSpace, BCDNSStateEnum stateEnum) {
        try {
            bcdnsServiceMapper.update(
                    new LambdaUpdateWrapper<BCDNSServiceEntity>()
                            .eq(BCDNSServiceEntity::getDomainSpace, domainSpace)
                            .set(BCDNSServiceEntity::getState, stateEnum.getCode())
            );
        } catch (Exception e) {
            throw new DataAccessLayerException(
                    e, "update bcdns service state to {} failed, domainSpace = {}",
                    stateEnum.name(), domainSpace
            );
        }
    }

    @Override
    public void updateBCDNSServiceProperties(String domainSpace, byte[] rawProp) {
        try {
            if (
                    bcdnsServiceMapper.update(
                            BCDNSServiceEntity.builder()
                                    .properties(rawProp)
                                    .build(),
                            new LambdaQueryWrapper<BCDNSServiceEntity>()
                                    .eq(BCDNSServiceEntity::getDomainSpace, domainSpace)
                    ) != 1
            ) {
                throw new RuntimeException("failed to update bcdns record");
            }
        } catch (Exception e) {
            throw new DataAccessLayerException(
                    e,
                    "failed to update bcdns properties to {} for space {}",
                    Base64.encode(rawProp), domainSpace
            );
        }
    }

    private void addDomainSpaceCert(String currDomainSpace, Map<String, DomainSpaceCertWrapper> result) {
        DomainSpaceCertEntity entity = domainSpaceCertMapper.selectOne(
                new LambdaQueryWrapper<DomainSpaceCertEntity>()
                        .eq(DomainSpaceCertEntity::getDomainSpace, currDomainSpace)
        );
        if (ObjectUtil.isNull(entity)) {
            throw new RuntimeException(StrUtil.format("domain space cert not found for {}", currDomainSpace));
        }
        result.put(currDomainSpace, ConvertUtil.convertFrom(entity));
        if (!StrUtil.equals(currDomainSpace, CrossChainDomain.ROOT_DOMAIN_SPACE)) {
            addDomainSpaceCert(entity.getParentSpace(), result);
        }
    }
}

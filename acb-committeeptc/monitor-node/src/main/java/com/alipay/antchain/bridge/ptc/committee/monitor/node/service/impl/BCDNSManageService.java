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

package com.alipay.antchain.bridge.ptc.committee.monitor.node.service.impl;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import cn.hutool.core.util.HexUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.alipay.antchain.bridge.bcdns.factory.BlockChainDomainNameServiceFactory;
import com.alipay.antchain.bridge.bcdns.service.BCDNSTypeEnum;
import com.alipay.antchain.bridge.bcdns.service.IBlockChainDomainNameService;
import com.alipay.antchain.bridge.bcdns.types.resp.QueryBCDNSTrustRootCertificateResponse;
import com.alipay.antchain.bridge.commons.bcdns.AbstractCrossChainCertificate;
import com.alipay.antchain.bridge.commons.bcdns.utils.CrossChainCertificateUtil;
import com.alipay.antchain.bridge.commons.core.base.CrossChainDomain;
import com.alipay.antchain.bridge.commons.core.base.ObjectIdentity;
import com.alipay.antchain.bridge.ptc.committee.monitor.node.commons.enums.BCDNSStateEnum;
import com.alipay.antchain.bridge.ptc.committee.monitor.node.commons.exception.CommitteeNodeException;
import com.alipay.antchain.bridge.ptc.committee.monitor.node.commons.exception.CommitteeNodeInternalException;
import com.alipay.antchain.bridge.ptc.committee.monitor.node.commons.models.BCDNSServiceDO;
import com.alipay.antchain.bridge.ptc.committee.monitor.node.commons.models.DomainSpaceCertWrapper;
import com.alipay.antchain.bridge.ptc.committee.monitor.node.dal.repository.interfaces.IBCDNSRepository;
import com.alipay.antchain.bridge.ptc.committee.monitor.node.service.IBCDNSManageService;
import jakarta.annotation.Resource;
import lombok.Synchronized;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Slf4j
public class BCDNSManageService implements IBCDNSManageService {

    @Resource
    private IBCDNSRepository bcdnsRepository;

    private final Map<String, IBlockChainDomainNameService> bcdnsClientMap = new ConcurrentHashMap<>();

    @Override
    public long countBCDNSService() {
        return bcdnsRepository.countBCDNSService();
    }

    @Override
    public IBlockChainDomainNameService getBCDNSClient(String domainSpace) {
        if (bcdnsClientMap.containsKey(domainSpace)) {
            return bcdnsClientMap.get(domainSpace);
        }

        BCDNSServiceDO bcdnsServiceDO = getBCDNSServiceData(domainSpace);
        if (ObjectUtil.isNull(bcdnsServiceDO)) {
            log.warn("none bcdns data found for domain space {}", domainSpace);
            return null;
        }
        if (bcdnsServiceDO.getState() != BCDNSStateEnum.WORKING) {
            throw new CommitteeNodeInternalException("BCDNS with domain space {} is not working now", domainSpace);
        }

        return startBCDNSService(bcdnsServiceDO);
    }

    @Override
    @Synchronized
    @Transactional(rollbackFor = CommitteeNodeException.class)
    public void registerBCDNSService(String domainSpace, BCDNSTypeEnum bcdnsType, byte[] config, AbstractCrossChainCertificate bcdnsRootCert) {
        try {
            if (hasBCDNSServiceData(domainSpace)) {
                throw new RuntimeException("bcdns already registered");
            }

            var bcdnsServiceDO = new BCDNSServiceDO();
            bcdnsServiceDO.setType(bcdnsType);
            bcdnsServiceDO.setDomainSpace(domainSpace);
            bcdnsServiceDO.setProperties(config);

            if (ObjectUtil.isNotNull(bcdnsRootCert)) {
                if (CrossChainCertificateUtil.isBCDNSTrustRoot(bcdnsRootCert)
                        && !StrUtil.equals(domainSpace, CrossChainDomain.ROOT_DOMAIN_SPACE)) {
                    throw new RuntimeException("the space name of bcdns trust root certificate supposed to have the root space name bug got : " + domainSpace);
                } else if (!CrossChainCertificateUtil.isBCDNSTrustRoot(bcdnsRootCert)
                        && !CrossChainCertificateUtil.isDomainSpaceCert(bcdnsRootCert)) {
                    throw new RuntimeException("expected bcdns trust root or domain space type certificate bug got : " + bcdnsRootCert.getType().name());
                }
                bcdnsServiceDO.setDomainSpaceCertWrapper(
                        new DomainSpaceCertWrapper(bcdnsRootCert)
                );
                bcdnsServiceDO.setOwnerOid(bcdnsRootCert.getCredentialSubjectInstance().getApplicant());
            }

            startBCDNSService(bcdnsServiceDO);
            saveBCDNSServiceData(bcdnsServiceDO);
        } catch (CommitteeNodeException e) {
            throw e;
        } catch (Exception e) {
            throw new CommitteeNodeInternalException(
                    e,
                    "failed to register bcdns service client for [{}]",
                    domainSpace
            );
        }
    }

    @Override
    @Synchronized
    public IBlockChainDomainNameService startBCDNSService(BCDNSServiceDO bcdnsServiceDO) {
        log.info("starting the bcdns service ( type: {}, domain_space: {} )",
                bcdnsServiceDO.getType().getCode(), bcdnsServiceDO.getDomainSpace());
        try {
            IBlockChainDomainNameService service = BlockChainDomainNameServiceFactory.create(bcdnsServiceDO.getType(), bcdnsServiceDO.getProperties());
            if (ObjectUtil.isNull(service)) {
                throw new CommitteeNodeInternalException("bcdns {} start failed", bcdnsServiceDO.getDomainSpace());
            }
            if (
                    ObjectUtil.isNull(bcdnsServiceDO.getDomainSpaceCertWrapper())
                            || ObjectUtil.isNull(bcdnsServiceDO.getDomainSpaceCertWrapper().getDomainSpaceCert())
            ) {
                QueryBCDNSTrustRootCertificateResponse response = service.queryBCDNSTrustRootCertificate();
                if (ObjectUtil.isNull(response) || ObjectUtil.isNull(response.getBcdnsTrustRootCertificate())) {
                    throw new CommitteeNodeInternalException("query empty root cert from bcdns {}", bcdnsServiceDO.getDomainSpace());
                }
                bcdnsServiceDO.setOwnerOid(response.getBcdnsTrustRootCertificate().getCredentialSubjectInstance().getApplicant());
                bcdnsServiceDO.setDomainSpaceCertWrapper(
                        new DomainSpaceCertWrapper(response.getBcdnsTrustRootCertificate())
                );
            }
            bcdnsServiceDO.setState(BCDNSStateEnum.WORKING);
            bcdnsClientMap.put(bcdnsServiceDO.getDomainSpace(), service);

            return service;
        } catch (CommitteeNodeException e) {
            throw e;
        } catch (Exception e) {
            throw new CommitteeNodeInternalException(e, "failed to start bcdns service client for [{}]", bcdnsServiceDO.getDomainSpace());
        }
    }

    @Override
    @Transactional(rollbackFor = CommitteeNodeException.class)
    @Synchronized
    public void restartBCDNSService(String domainSpace) {
        log.info("restarting the bcdns service ( domain_space: {} )", domainSpace);
        try {
            var bcdnsServiceDO = getBCDNSServiceData(domainSpace);
            if (ObjectUtil.isNull(bcdnsServiceDO)) {
                throw new RuntimeException(StrUtil.format("bcdns {} not exist", domainSpace));
            }
            if (bcdnsServiceDO.getState() != BCDNSStateEnum.FROZEN) {
                throw new RuntimeException(StrUtil.format("bcdns {} already in state {}", domainSpace, bcdnsServiceDO.getState().getCode()));
            }
            var service = BlockChainDomainNameServiceFactory.create(bcdnsServiceDO.getType(), bcdnsServiceDO.getProperties());
            if (ObjectUtil.isNull(service)) {
                throw new CommitteeNodeInternalException("bcdns {} start failed", bcdnsServiceDO.getDomainSpace());
            }
            bcdnsRepository.updateBCDNSServiceState(domainSpace, BCDNSStateEnum.WORKING);
            bcdnsClientMap.put(bcdnsServiceDO.getDomainSpace(), service);

        } catch (CommitteeNodeException e) {
            throw e;
        } catch (Exception e) {
            throw new CommitteeNodeInternalException(
                    e,
                    "failed to restart bcdns service client for {}",
                    domainSpace
            );
        }
    }

    @Override
    @Transactional(rollbackFor = CommitteeNodeException.class)
    @Synchronized
    public void stopBCDNSService(String domainSpace) {
        log.info("stopping the bcdns service ( domain_space: {} )", domainSpace);
        try {
            if (!hasBCDNSServiceData(domainSpace)) {
                throw new RuntimeException("bcdns not exist for " + domainSpace);
            }
            bcdnsRepository.updateBCDNSServiceState(domainSpace, BCDNSStateEnum.FROZEN);
            bcdnsClientMap.remove(domainSpace);
        } catch (CommitteeNodeException e) {
            throw e;
        } catch (Exception e) {
            throw new CommitteeNodeInternalException(
                    e,
                    "failed to stop bcdns service client for {}",
                    domainSpace
            );
        }
    }

    @Override
    public void saveBCDNSServiceData(BCDNSServiceDO bcdnsServiceDO) {
        if (bcdnsRepository.hasBCDNSService(bcdnsServiceDO.getDomainSpace())) {
            throw new CommitteeNodeInternalException(
                    "bcdns {} not exist or data incomplete",
                    bcdnsServiceDO.getDomainSpace()
            );
        }
        bcdnsRepository.saveBCDNSServiceDO(bcdnsServiceDO);
    }

    @Override
    public BCDNSServiceDO getBCDNSServiceData(String domainSpace) {
        return bcdnsRepository.getBCDNSServiceDO(domainSpace);
    }

    @Override
    @Synchronized
    public void deleteBCDNSServiceDate(String domainSpace) {
        bcdnsRepository.deleteBCDNSServiceDO(domainSpace);
        bcdnsClientMap.remove(domainSpace);
    }

    @Override
    public List<String> getAllBCDNSDomainSpace() {
        return bcdnsRepository.getAllBCDNSDomainSpace();
    }

    @Override
    public boolean hasBCDNSServiceData(String domainSpace) {
        return bcdnsRepository.hasBCDNSService(domainSpace);
    }

    @Override
    public Map<String, AbstractCrossChainCertificate> getTrustRootCertChain(String domainSpace) {
        return bcdnsRepository.getDomainSpaceCertChain(domainSpace).entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> entry.getValue().getDomainSpaceCert()
                ));
    }

    @Override
    public List<String> getDomainSpaceChain(String domainSpace) {
        return bcdnsRepository.getDomainSpaceChain(domainSpace);
    }

    @Override
    public AbstractCrossChainCertificate getTrustRootCertForRootDomain() {
        DomainSpaceCertWrapper wrapper = bcdnsRepository.getDomainSpaceCert(CrossChainDomain.ROOT_DOMAIN_SPACE);
        if (ObjectUtil.isNull(wrapper)) {
            return null;
        }
        return wrapper.getDomainSpaceCert();
    }

    @Override
    public boolean validateCrossChainCertificate(AbstractCrossChainCertificate certificate) {
        DomainSpaceCertWrapper trustRootCert = bcdnsRepository.getDomainSpaceCert(certificate.getIssuer());
        if (ObjectUtil.isNull(trustRootCert)) {
            log.warn(
                    "none trust root found for {} to verify for relayer cert: {}",
                    HexUtil.encodeHexStr(certificate.getIssuer().encode()),
                    CrossChainCertificateUtil.formatCrossChainCertificateToPem(certificate)
            );
            return false;
        }
        return trustRootCert.getDomainSpaceCert().getCredentialSubjectInstance().verifyIssueProof(
                certificate.getEncodedToSign(),
                certificate.getProof()
        );
    }

    @Override
    public DomainSpaceCertWrapper getDomainSpaceCert(String domainSpace) {
        return bcdnsRepository.getDomainSpaceCert(domainSpace);
    }

    @Override
    public DomainSpaceCertWrapper getDomainSpaceCert(ObjectIdentity ownerOid) {
        return bcdnsRepository.getDomainSpaceCert(ownerOid);
    }

    @Override
    public boolean validateCrossChainCertificate(AbstractCrossChainCertificate certificate, Map<String, AbstractCrossChainCertificate> domainSpaceCertPath) {
        try {
            List<AbstractCrossChainCertificate> sequentialCerts = domainSpaceCertPath.entrySet().stream().sorted(
                            Comparator.comparing(o -> StrUtil.reverse(o.getKey()))
                    ).map(Map.Entry::getValue)
                    .collect(Collectors.toList());

            DomainSpaceCertWrapper bcdnsRootCert = bcdnsRepository.getDomainSpaceCert(CrossChainDomain.ROOT_DOMAIN_SPACE);
            if (ObjectUtil.isNull(bcdnsRootCert)) {
                throw new RuntimeException("none root domain space cert set in DB");
            }
            sequentialCerts.set(0, bcdnsRootCert.getDomainSpaceCert());

            if (sequentialCerts.size() > 1) {
                verifyCertPath(sequentialCerts, 0);
                saveDomainSpaceCerts(domainSpaceCertPath);
            }

            return sequentialCerts.get(sequentialCerts.size() - 1)
                    .getCredentialSubjectInstance().verifyIssueProof(
                            certificate.getEncodedToSign(),
                            certificate.getProof()
                    );
        } catch (Exception e) {
            log.error("failed to verify crosschain cert (type: {}, cert_id: {})", certificate.getType().name(), certificate.getId(), e);
            return false;
        }
    }

    @Override
    public void saveDomainSpaceCerts(Map<String, AbstractCrossChainCertificate> domainSpaceCerts) {
        for (Map.Entry<String, AbstractCrossChainCertificate> entry : domainSpaceCerts.entrySet()) {
            try {
                if (bcdnsRepository.hasDomainSpaceCert(entry.getKey())) {
                    log.info("DomainSpace {} already exists", entry.getKey());
                    continue;
                }
                bcdnsRepository.saveDomainSpaceCert(new DomainSpaceCertWrapper(entry.getValue()));
                log.info("successful to save domain space cert for {}", entry.getKey());
            } catch (Exception e) {
                log.error("failed to save domain space certs for space {} : ", entry.getKey(), e);
            }
        }
    }

    private void verifyCertPath(List<AbstractCrossChainCertificate> certPath, int currIndex) {
        if (currIndex == certPath.size() - 2) {
            if (
                    !certPath.get(currIndex).getCredentialSubjectInstance().verifyIssueProof(
                            certPath.get(currIndex + 1).getEncodedToSign(),
                            certPath.get(currIndex + 1).getProof()
                    )
            ) {
                throw new RuntimeException(
                        StrUtil.format(
                                "failed to verify {} cert with its parent {}",
                                CrossChainCertificateUtil.getCrossChainDomain(certPath.get(currIndex)),
                                CrossChainCertificateUtil.getCrossChainDomain(certPath.get(currIndex + 1))
                        )
                );
            }
            return;
        }

        verifyCertPath(certPath, currIndex + 1);
    }
}

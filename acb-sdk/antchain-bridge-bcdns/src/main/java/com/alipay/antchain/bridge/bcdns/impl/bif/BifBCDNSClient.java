/*
 * Copyright 2023 Ant Group
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

package com.alipay.antchain.bridge.bcdns.impl.bif;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import cn.bif.api.BIFSDK;
import cn.bif.model.request.BIFAccountGetNonceRequest;
import cn.bif.model.request.BIFContractCallRequest;
import cn.bif.model.request.BIFContractInvokeRequest;
import cn.bif.model.response.BIFAccountGetNonceResponse;
import cn.bif.model.response.BIFContractCallResponse;
import cn.bif.model.response.BIFContractInvokeResponse;
import cn.hutool.core.util.HexUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson.JSON;
import com.alipay.antchain.bridge.bcdns.impl.bif.conf.BifBCNDSConfig;
import com.alipay.antchain.bridge.bcdns.impl.bif.conf.BifCertificationServiceConfig;
import com.alipay.antchain.bridge.bcdns.impl.bif.conf.BifChainConfig;
import com.alipay.antchain.bridge.bcdns.impl.bif.resp.QueryStatusRespDto;
import com.alipay.antchain.bridge.bcdns.impl.bif.resp.VcInfoRespDto;
import com.alipay.antchain.bridge.bcdns.service.IBlockChainDomainNameService;
import com.alipay.antchain.bridge.bcdns.types.base.DomainRouter;
import com.alipay.antchain.bridge.bcdns.types.base.Relayer;
import com.alipay.antchain.bridge.bcdns.types.exception.AntChainBridgeBCDNSException;
import com.alipay.antchain.bridge.bcdns.types.exception.BCDNSErrorCodeEnum;
import com.alipay.antchain.bridge.bcdns.types.req.*;
import com.alipay.antchain.bridge.bcdns.types.resp.*;
import com.alipay.antchain.bridge.commons.bcdns.AbstractCrossChainCertificate;
import com.alipay.antchain.bridge.commons.bcdns.CrossChainCertificateFactory;
import com.alipay.antchain.bridge.commons.core.base.ObjectIdentity;
import com.alipay.antchain.bridge.commons.core.ptc.PTCTrustRoot;
import com.alipay.antchain.bridge.commons.core.ptc.ThirdPartyBlockchainTrustAnchor;
import com.alipay.antchain.bridge.commons.utils.crypto.HashAlgoEnum;
import lombok.Getter;
import lombok.NonNull;

@Getter
public class BifBCDNSClient implements IBlockChainDomainNameService {

    private static final String DOMAIN_CALL_GET_CERT_BY_NAME_TEMPLATE
            = "{\"function\":\"getCertByName(string)\",\"args\":\"'{}'\",\"return\":\"returns(bytes)\"}";

    private static final String PTC_CALL_GET_CERT_BY_ID_TEMPLATE
            = "{\"function\":\"getCertById(string)\",\"args\":\"'{}'\",\"return\":\"returns(bytes)\"}";

    private static final String RELAY_CALL_BINDING_DOMAIN_NAME_WITH_RELAY_TEMPLATE
            = "{\"function\":\"bindingDomainNameWithRelay(string,string,bytes)\",\"args\":\"'{}','{}','{}'\"}";

    private static final String RELAY_CALL_BINDING_DOMAIN_NAME_WITH_TPBTA_TEMPLATE
            = "{\"function\":\"bindingDomainNameWithTPBTA(string,bytes)\",\"args\":\"'{}','{}'\"}";

    private static final String RELAY_CALL_GET_TPBTA_BY_LANE_TEMPLATE
            = "{\"function\":\"getTPBTAByLane(string,uint16)\",\"args\":\"'{}',{}\",\"return\":\"returns(bytes)\"}";

    private static final String RELAY_CALL_GET_CERT_BY_ID_TEMPLATE
            = "{\"function\":\"getCertById(string)\",\"args\":\"'{}'\",\"return\":\"returns(bytes)\"}";

    private static final String RELAY_CALL_GET_RELAY_BY_DOMAIN_NAME_TEMPLATE
            = "{\"function\":\"getRelayByDomainName(string)\",\"args\":\"'{}'\",\"return\":\"returns(bytes,bytes)\"}";

    private static final String RELAY_CALL_GET_PTCTRUSTROOT_BY_PTCOID_TEMPLATE
            = "{\"function\":\"getPTCTrustRootById(bytes32)\",\"args\":\"'{}'\",\"return\":\"returns(bytes)\"}";

    public static BifBCDNSClient generateFrom(byte[] rawConf) {
        BifBCNDSConfig config = JSON.parseObject(rawConf, BifBCNDSConfig.class);
        return new BifBCDNSClient(
                config.getCertificationServiceConfig(),
                config.getChainConfig()
        );
    }

    private final BifCertificationServiceClient certificationServiceClient;

    private final BIFSDK bifsdk;

    private final BifChainConfig bifChainConfig;

    public BifBCDNSClient(
            BifCertificationServiceConfig bifCertificationServiceConfig,
            BifChainConfig bifChainConfig
    ) {
        certificationServiceClient = new BifCertificationServiceClient(
                bifCertificationServiceConfig.getUrl(),
                new BifBCDNSClientCredential(
                        bifCertificationServiceConfig.getClientCrossChainCertPem(),
                        bifCertificationServiceConfig.getClientPrivateKeyPem(),
                        bifCertificationServiceConfig.getSigAlgo(),
                        bifCertificationServiceConfig.getAuthorizedKeyPem(),
                        bifCertificationServiceConfig.getAuthorizedPublicKeyPem(),
                        bifCertificationServiceConfig.getAuthorizedSigAlgo()
                )
        );
        this.bifChainConfig = bifChainConfig;
        bifsdk = ObjectUtil.isNull(bifChainConfig.getBifChainRpcPort()) ?
                BIFSDK.getInstance(bifChainConfig.getBifChainRpcUrl()) :
                BIFSDK.getInstance(bifChainConfig.getBifChainRpcUrl(), bifChainConfig.getBifChainRpcPort());
    }

    @Override
    public QueryBCDNSTrustRootCertificateResponse queryBCDNSTrustRootCertificate() {
        return new QueryBCDNSTrustRootCertificateResponse(
                CrossChainCertificateFactory.createCrossChainCertificate(
                        certificationServiceClient.queryRootCert().getBcdnsRootCredential()
                )
        );
    }

    @Override
    public ApplyRelayerCertificateResponse applyRelayerCertificate(AbstractCrossChainCertificate certSigningRequest) {
        return new ApplyRelayerCertificateResponse(
                certificationServiceClient.applyCrossChainCertificate(
                        certSigningRequest
                ).getApplyNo()
        );
    }

    @Override
    public ApplicationResult queryRelayerCertificateApplicationResult(String applyReceipt) {
        return queryApplicationResult(applyReceipt);
    }

    @Override
    public ApplyPTCCertificateResponse applyPTCCertificate(AbstractCrossChainCertificate certSigningRequest) {
        return new ApplyPTCCertificateResponse(
                certificationServiceClient.applyCrossChainCertificate(
                        certSigningRequest
                ).getApplyNo()
        );
    }

    @Override
    public ApplicationResult queryPTCCertificateApplicationResult(String applyReceipt) {
        return queryApplicationResult(applyReceipt);
    }

    @Override
    public ApplyDomainNameCertificateResponse applyDomainNameCertificate(AbstractCrossChainCertificate certSigningRequest) {
        return new ApplyDomainNameCertificateResponse(
                certificationServiceClient.applyCrossChainCertificate(
                        certSigningRequest
                ).getApplyNo()
        );
    }

    @Override
    public ApplicationResult queryDomainNameCertificateApplicationResult(String applyReceipt) {
        return queryApplicationResult(applyReceipt);
    }

    @Override
    public QueryRelayerCertificateResponse queryRelayerCertificate(QueryRelayerCertificateRequest request) {
        try {
            BIFContractCallRequest bifContractCallRequest = new BIFContractCallRequest();
            bifContractCallRequest.setContractAddress(bifChainConfig.getRelayerGovernContract());
            bifContractCallRequest.setInput(
                    StrUtil.format(
                            RELAY_CALL_GET_CERT_BY_ID_TEMPLATE,
                            request.getRelayerCertId()
                    )
            );
            // BIF test net has some problems about gas calculation
            // So we just set gas manually here.
            // would delete it in the future.
            bifContractCallRequest.setGasPrice(1L);

            BIFContractCallResponse response = bifsdk.getBIFContractService().contractQuery(bifContractCallRequest);
            if (0 != response.getErrorCode()) {
                throw new AntChainBridgeBCDNSException(
                        BCDNSErrorCodeEnum.BCDNS_QUERY_RELAYER_INFO_FAILED,
                        StrUtil.format(
                                "failed to query relayer info from BIF chain ( err_code: {}, err_msg: {} )",
                                response.getErrorCode(), response.getErrorDesc()
                        )
                );
            }

            String res = decodeResultFromResponse(response);
            boolean exist = StrUtil.isNotEmpty(res);
            return new QueryRelayerCertificateResponse(
                    exist,
                    exist ? CrossChainCertificateFactory.createCrossChainCertificate(HexUtil.decodeHex(res)) : null
            );
        } catch (AntChainBridgeBCDNSException e) {
            throw e;
        } catch (Exception e) {
            throw new AntChainBridgeBCDNSException(
                    BCDNSErrorCodeEnum.BCDNS_QUERY_RELAYER_INFO_FAILED,
                    StrUtil.format(
                            "failed to query relayer {} : {} info from BIF chain",
                            request.getName(), HexUtil.encodeHexStr(request.getApplicant().encode())
                    ),
                    e
            );
        }
    }

    @Override
    public QueryPTCCertificateResponse queryPTCCertificate(QueryPTCCertificateRequest request) {
        try {
            BIFContractCallRequest bifContractCallRequest = new BIFContractCallRequest();
            bifContractCallRequest.setContractAddress(bifChainConfig.getPtcGovernContract());
            bifContractCallRequest.setInput(
                    StrUtil.format(
                            PTC_CALL_GET_CERT_BY_ID_TEMPLATE,
                            request.getPtcCertId()
                    )
            );
            // BIF test net has some problems about gas calculation
            // So we just set gas manually here.
            // would delete it in the future.
            bifContractCallRequest.setGasPrice(1L);

            BIFContractCallResponse response = bifsdk.getBIFContractService().contractQuery(bifContractCallRequest);
            if (0 != response.getErrorCode()) {
                throw new AntChainBridgeBCDNSException(
                        BCDNSErrorCodeEnum.BCDNS_QUERY_PTC_CERT_FAILED,
                        StrUtil.format(
                                "failed to call getCertByName to BIF chain ( err_code: {}, err_msg: {} )",
                                response.getErrorCode(), response.getErrorDesc()
                        )
                );
            }

            String res = decodeResultFromResponse(response);
            boolean exist = StrUtil.isNotEmpty(res);
            return new QueryPTCCertificateResponse(
                    exist,
                    exist ? CrossChainCertificateFactory.createCrossChainCertificate(HexUtil.decodeHex(res)) : null
            );
        } catch (AntChainBridgeBCDNSException e) {
            throw e;
        } catch (Exception e) {
            throw new AntChainBridgeBCDNSException(
                    BCDNSErrorCodeEnum.BCDNS_QUERY_PTC_CERT_FAILED,
                    StrUtil.format(
                            "failed to query PTC certificate for (cert_id: {}, name: {}) from BIF chain",
                            request.getPtcCertId(), request.getName()
                    ),
                    e
            );
        }
    }

    @Override
    public QueryDomainNameCertificateResponse queryDomainNameCertificate(QueryDomainNameCertificateRequest request) {
        try {
            BIFContractCallRequest bifContractCallRequest = new BIFContractCallRequest();
            bifContractCallRequest.setContractAddress(bifChainConfig.getDomainGovernContract());
            bifContractCallRequest.setInput(
                    StrUtil.format(
                            DOMAIN_CALL_GET_CERT_BY_NAME_TEMPLATE,
                            request.getDomain().getDomain()
                    )
            );
            // BIF test net has some problems about gas calculation
            // So we just set gas manually here.
            // would delete it in the future.
            bifContractCallRequest.setGasPrice(1L);

            BIFContractCallResponse response = bifsdk.getBIFContractService().contractQuery(bifContractCallRequest);
            if (0 != response.getErrorCode()) {
                throw new AntChainBridgeBCDNSException(
                        BCDNSErrorCodeEnum.BCDNS_QUERY_DOMAIN_CERT_FAILED,
                        StrUtil.format(
                                "failed to call getCertByName to BIF chain ( err_code: {}, err_msg: {} )",
                                response.getErrorCode(), response.getErrorDesc()
                        )
                );
            }

            String res = decodeResultFromResponse(response);
            boolean exist = StrUtil.isNotEmpty(res);
            return new QueryDomainNameCertificateResponse(
                    exist,
                    exist ? CrossChainCertificateFactory.createCrossChainCertificate(HexUtil.decodeHex(res)) : null
            );
        } catch (AntChainBridgeBCDNSException e) {
            throw e;
        } catch (Exception e) {
            throw new AntChainBridgeBCDNSException(
                    BCDNSErrorCodeEnum.BCDNS_QUERY_DOMAIN_CERT_FAILED,
                    StrUtil.format(
                            "failed to query domain certificate for domain {} from BIF chain",
                            request.getDomain()
                    ),
                    e
            );
        }
    }

    @Override
    public void registerDomainRouter(RegisterDomainRouterRequest request) throws AntChainBridgeBCDNSException {
        try {
            BIFContractInvokeRequest bifContractInvokeRequest = new BIFContractInvokeRequest();
            bifContractInvokeRequest.setSenderAddress(bifChainConfig.getBifAddress());
            bifContractInvokeRequest.setPrivateKey(bifChainConfig.getBifPrivateKey());
            bifContractInvokeRequest.setContractAddress(bifChainConfig.getRelayerGovernContract());
            bifContractInvokeRequest.setBIFAmount(0L);
            bifContractInvokeRequest.setGasPrice(1L);
            bifContractInvokeRequest.setInput(
                    StrUtil.format(
                            RELAY_CALL_BINDING_DOMAIN_NAME_WITH_RELAY_TEMPLATE,
                            request.getRouter().getDestDomain().getDomain(),
                            request.getRouter().getDestRelayer().getRelayerCertId(),
                            "0x" + HexUtil.encodeHexStr(StrUtil.join("^", request.getRouter().getDestRelayer().getNetAddressList()).getBytes())
                    )
            );

            BIFContractInvokeResponse response = bifsdk.getBIFContractService().contractInvoke(bifContractInvokeRequest);
            if (0 != response.getErrorCode()) {
                throw new AntChainBridgeBCDNSException(
                        BCDNSErrorCodeEnum.BCDNS_REGISTER_DOMAIN_ROUTER_FAILED,
                        StrUtil.format(
                                "failed to call bindingDomainNameWithRelay to BIF chain ( err_code: {}, err_msg: {} )",
                                response.getErrorCode(), response.getErrorDesc()
                        )
                );
            }
        } catch (AntChainBridgeBCDNSException e) {
            throw e;
        } catch (Exception e) {
            throw new AntChainBridgeBCDNSException(
                    BCDNSErrorCodeEnum.BCDNS_REGISTER_DOMAIN_ROUTER_FAILED,
                    StrUtil.format(
                            "failed to registerDomainRouter (domain: {}, relayer_cert_id: {}, net_addresses: [{}]) to BIF chain",
                            request.getRouter().getDestDomain().getDomain(),
                            request.getRouter().getDestRelayer().getRelayerCert(),
                            StrUtil.join(",", request.getRouter().getDestRelayer().getNetAddressList())
                    ),
                    e
            );
        }
    }

/*
    @Override
    public void registerThirdPartyBlockchainTrustAnchor(RegisterThirdPartyBlockchainTrustAnchorRequest request) throws AntChainBridgeBCDNSException {
        try {
            BIFContractInvokeRequest bifContractInvokeRequest = new BIFContractInvokeRequest();
            bifContractInvokeRequest.setSenderAddress(bifChainConfig.getBifAddress());
            bifContractInvokeRequest.setPrivateKey(bifChainConfig.getBifPrivateKey());
            bifContractInvokeRequest.setContractAddress(bifChainConfig.getRelayerGovernContract());
            bifContractInvokeRequest.setBIFAmount(0L);
            bifContractInvokeRequest.setGasPrice(1L);
            bifContractInvokeRequest.setInput(
                    StrUtil.format(
                            RELAY_CALL_BINDING_DOMAIN_NAME_WITH_TPBTA_TEMPLATE,
                            request.getDomain().getDomain(),
                            "0x" + HexUtil.encodeHexStr(request.getTpbta().encode())
                    )
            );

            BIFContractInvokeResponse response = bifsdk.getBIFContractService().contractInvoke(bifContractInvokeRequest);
            if (0 != response.getErrorCode()) {
                throw new AntChainBridgeBCDNSException(
                        BCDNSErrorCodeEnum.BCDNS_REGISTER_TPBTA_FAILED,
                        StrUtil.format(
                                "failed to call bindingDomainNameWithTPBTA to BIF chain ( err_code: {}, err_msg: {} )",
                                response.getErrorCode(), response.getErrorDesc()
                        )
                );
            }
        } catch (AntChainBridgeBCDNSException e) {
            throw e;
        } catch (Exception e) {
            throw new AntChainBridgeBCDNSException(
                    BCDNSErrorCodeEnum.BCDNS_REGISTER_TPBTA_FAILED,
                    StrUtil.format(
                            "failed to register tpbta {} for domain {} to BIF chain",
                            Base64.encode(request.getTpbta().encode()),
                            request.getDomain().getDomain()
                    ),
                    e
            );
        }
    }
*/

    @Override
    public void registerThirdPartyBlockchainTrustAnchor(RegisterThirdPartyBlockchainTrustAnchorRequest request) throws AntChainBridgeBCDNSException {
        certificationServiceClient.addThirdPartyBlockchainTrustAnchor(request.getTpbta());
    }

    @Override
    public DomainRouter queryDomainRouter(QueryDomainRouterRequest request) {
        try {
            BIFContractCallRequest bifContractCallRequest = new BIFContractCallRequest();
            bifContractCallRequest.setContractAddress(bifChainConfig.getRelayerGovernContract());
            bifContractCallRequest.setInput(
                    StrUtil.format(
                            RELAY_CALL_GET_RELAY_BY_DOMAIN_NAME_TEMPLATE,
                            request.getDestDomain().getDomain()
                    )
            );
            // BIF test net has some problems about gas calculation
            // So we just set gas manually here.
            // would delete it in the future.
            bifContractCallRequest.setGasPrice(1L);

            BIFContractCallResponse response = bifsdk.getBIFContractService().contractQuery(bifContractCallRequest);
            if (0 != response.getErrorCode() || ObjectUtil.isNull(response.getResult())) {
                throw new RuntimeException(StrUtil.format("call BIF chain failed: ( err_code: {}, err_msg: {} )",
                        response.getErrorCode(), response.getErrorDesc()));
            }
            List<String> res = decodeResultsFromResponse(response);
            if (ObjectUtil.isNull(res) || res.size() < 2 || StrUtil.isEmpty(res.get(0))) {
                return null;
            }
            AbstractCrossChainCertificate relayerCert = CrossChainCertificateFactory.createCrossChainCertificate(
                    HexUtil.decodeHex(res.get(0))
            );
            List<String> netAddresses = new ArrayList<>(StrUtil.split(HexUtil.decodeHexStr(StrUtil.removePrefix(res.get(1), "0x")), "^"));
            return new DomainRouter(
                    request.getDestDomain(),
                    new Relayer(
                            relayerCert.getId(),
                            relayerCert,
                            netAddresses
                    )
            );
        } catch (AntChainBridgeBCDNSException e) {
            throw e;
        } catch (Exception e) {
            throw new AntChainBridgeBCDNSException(
                    BCDNSErrorCodeEnum.BCDNS_QUERY_DOMAIN_ROUTER_FAILED,
                    StrUtil.format(
                            "failed to query domain router for domain {} from BIF chain",
                            request.getDestDomain().getDomain()
                    ),
                    e
            );
        }
    }

    @Override
    public ThirdPartyBlockchainTrustAnchor queryThirdPartyBlockchainTrustAnchor(QueryThirdPartyBlockchainTrustAnchorRequest request) {
        try {
            BIFContractCallRequest bifContractCallRequest = new BIFContractCallRequest();
            bifContractCallRequest.setContractAddress(bifChainConfig.getTpBtaGovernContract());
            bifContractCallRequest.setInput(
                    StrUtil.format(
                            RELAY_CALL_GET_TPBTA_BY_LANE_TEMPLATE,
                            request.getTpBtaCrossChainLane().getLaneKey(), request.getTpbtaVersion()
                    )
            );
            // BIF test net has some problems about gas calculation
            // So we just set gas manually here.
            // would delete it in the future.
            bifContractCallRequest.setGasPrice(1L);

            BIFContractCallResponse response = bifsdk.getBIFContractService().contractQuery(bifContractCallRequest);
            if (0 != response.getErrorCode()) {
                throw new AntChainBridgeBCDNSException(
                        BCDNSErrorCodeEnum.BCDNS_QUERY_TPBTA_FAILED,
                        StrUtil.format(
                                "failed to call getTPBTAByDomainName to BIF chain ( err_code: {}, err_msg: {} )",
                                response.getErrorCode(), response.getErrorDesc()
                        )
                );
            }
            String res = decodeResultFromResponse(response);
            return StrUtil.isEmpty(res) ? null : ThirdPartyBlockchainTrustAnchor.decode(HexUtil.decodeHex(res));
        } catch (AntChainBridgeBCDNSException e) {
            throw e;
        } catch (Exception e) {
            throw new AntChainBridgeBCDNSException(
                    BCDNSErrorCodeEnum.BCDNS_QUERY_TPBTA_FAILED,
                    StrUtil.format(
                            "failed to query TPBTA for domain {} and version {} from BIF chain",
                            request.getTpBtaCrossChainLane().getLaneKey(), request.getTpbtaVersion()
                    ),
                    e
            );
        }
    }

    @Override
    public PTCTrustRoot queryPTCTrustRoot(@NonNull ObjectIdentity ptcOid) {
        try {
            BIFContractCallRequest bifContractCallRequest = new BIFContractCallRequest();
            bifContractCallRequest.setContractAddress(bifChainConfig.getPtcTrustRootGovernContract()); // yuechi: ???
            bifContractCallRequest.setInput(
                    StrUtil.format(
                            RELAY_CALL_GET_PTCTRUSTROOT_BY_PTCOID_TEMPLATE,
                            "0x" + HexUtil.encodeHexStr(HashAlgoEnum.KECCAK_256.hash(ptcOid.encode()))
                    )
            );
            // BIF test net has some problems about gas calculation
            // So we just set gas manually here.
            // would delete it in the future.
            bifContractCallRequest.setGasPrice(1L);

            BIFContractCallResponse response = bifsdk.getBIFContractService().contractQuery(bifContractCallRequest);
            if (0 != response.getErrorCode()) {
                throw new AntChainBridgeBCDNSException(
                        BCDNSErrorCodeEnum.BCDNS_QUERY_PTCTRUSTROOT_FAILED,
                        StrUtil.format(
                                "failed to call getPTCTrustRootById to BIF chain ( err_code: {}, err_msg: {} )",
                                response.getErrorCode(), response.getErrorDesc()
                        )
                );
            }
            String res = decodeResultFromResponse(response);
            return StrUtil.isEmpty(res) ? null : PTCTrustRoot.decode(HexUtil.decodeHex(res));
        } catch (AntChainBridgeBCDNSException e) {
            throw e;
        } catch (Exception e) {
            throw new AntChainBridgeBCDNSException(
                    BCDNSErrorCodeEnum.BCDNS_QUERY_PTCTRUSTROOT_FAILED,
                    StrUtil.format(
                            "failed to query ptcTrustRoot for ptcOid {} from BIF chain",
                            HexUtil.encodeHexStr(ptcOid.encode())
                    ),
                    e
            );
        }
    }

    @Override
    public void addPTCTrustRoot(PTCTrustRoot ptcTrustRoot) {
        certificationServiceClient.addPTCTrustRoot(ptcTrustRoot);
    }

    private ApplicationResult queryApplicationResult(String applyReceipt) {
        QueryStatusRespDto queryStatusRespDto = certificationServiceClient.queryApplicationStatus(applyReceipt);
        switch (queryStatusRespDto.getStatus()) {
            case 1:
                return new ApplicationResult(false, null);
            case 2:
                VcInfoRespDto vcInfoRespDto = certificationServiceClient.downloadCrossChainCert(queryStatusRespDto.getCredentialId());
                return new ApplicationResult(
                        true,
                        CrossChainCertificateFactory.createCrossChainCertificate(vcInfoRespDto.getCredential())
                );
            case 3:
                return new ApplicationResult(true, null);
            default:
                throw new RuntimeException(
                        StrUtil.format(
                                "unexpected status {} for application receipt {}",
                                queryStatusRespDto.getStatus(), applyReceipt
                        )
                );
        }
    }

    private long queryBifAccNonce() {
        BIFAccountGetNonceRequest request = new BIFAccountGetNonceRequest();
        request.setAddress(bifChainConfig.getBifAddress());
        BIFAccountGetNonceResponse response = bifsdk.getBIFAccountService().getNonce(request);
        if (0 != response.getErrorCode()) {
            throw new AntChainBridgeBCDNSException(
                    BCDNSErrorCodeEnum.BCDNS_CLIENT_INIT_FAILED,
                    StrUtil.format(
                            "failed to query nonce for bif account ( err_code: {}, err_msg: {}, acc: {} )",
                            response.getErrorCode(), response.getErrorDesc(), bifChainConfig.getBifAddress()
                    )
            );
        }
        return response.getResult().getNonce();
    }

    private String decodeResultFromResponse(BIFContractCallResponse response) {
        Map<String, Map<String, String>> resMap = (Map<String, Map<String, String>>) (response.getResult().getQueryRets().get(0));
        String res = resMap.get("result").get("data").trim();
        res = StrUtil.removeSuffix(
                StrUtil.removePrefix(res, "[").trim(),
                "]"
        ).trim();
        if (HexUtil.isHexNumber(res)) {
            res = StrUtil.removePrefix(res.trim(), "0x");
        }
        return res;
    }

    private List<String> decodeResultsFromResponse(BIFContractCallResponse response) {
        Map<String, Map<String, String>> resMap = (Map<String, Map<String, String>>) (response.getResult().getQueryRets().get(0));
        String res = resMap.get("result").get("data").trim();
        res = StrUtil.removeSuffix(
                StrUtil.removePrefix(res, "[").trim(),
                "]"
        ).trim();
        return StrUtil.split(res, ",").stream().map(
                s -> {
                    if (HexUtil.isHexNumber(s)) {
                        s = StrUtil.removePrefix(s.trim(), "0x");
                    }
                    return s.trim();
                }
        ).collect(Collectors.toList());
    }
}

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

package com.alipay.antchain.bridge.ptc.committee.monitor.node.server;

import cn.hutool.core.codec.Base64;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson.JSON;
import com.alipay.antchain.bridge.bcdns.service.BCDNSTypeEnum;
import com.alipay.antchain.bridge.commons.bcdns.utils.CrossChainCertificateUtil;
import com.alipay.antchain.bridge.commons.core.ptc.PTCTrustRoot;
import com.alipay.antchain.bridge.commons.core.ptc.PTCTypeEnum;
import com.alipay.antchain.bridge.ptc.committee.monitor.node.dal.repository.interfaces.ISystemConfigRepository;
import com.alipay.antchain.bridge.ptc.committee.monitor.node.server.grpc.*;
import com.alipay.antchain.bridge.ptc.committee.monitor.node.service.IBCDNSManageService;
import io.grpc.stub.StreamObserver;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class AdminServiceImpl extends AdminServiceGrpc.AdminServiceImplBase {

    @Resource
    private IBCDNSManageService bcdnsManageService;

    @Resource
    private ISystemConfigRepository systemConfigRepository;

    @Override
    public void registerBcdnsService(RegisterBcdnsServiceRequest request, StreamObserver<Response> responseObserver) {
        try {
            log.info("register bcdns service {}", request.getDomainSpace());
            bcdnsManageService.registerBCDNSService(
                    request.getDomainSpace(),
                    BCDNSTypeEnum.parseFromValue(request.getBcdnsType()),
                    request.getConfig().toByteArray(),
                    StrUtil.isEmpty(request.getBcdnsRootCert()) ? null : CrossChainCertificateUtil.readCrossChainCertificateFromPem(request.getBcdnsRootCert().getBytes())
            );
            responseObserver.onNext(Response.newBuilder().setCode(0).build());
        } catch (Throwable t) {
            log.error("register bcdns service {} failed", request.getDomainSpace(), t);
            responseObserver.onNext(Response.newBuilder().setCode(-1).setErrorMsg(t.getMessage()).build());
        } finally {
            responseObserver.onCompleted();
        }
    }

    @Override
    public void getBcdnsServiceInfo(GetBcdnsServiceInfoRequest request, StreamObserver<Response> responseObserver) {
        try {
            log.info("get bcdns service info {}", request.getDomainSpace());
            var bcdnsServiceDO = bcdnsManageService.getBCDNSServiceData(request.getDomainSpace());
            responseObserver.onNext(
                    Response.newBuilder()
                            .setCode(0)
                            .setGetBcdnsServiceInfoResp(
                                    GetBcdnsServiceInfoResp.newBuilder()
                                            .setInfoJson(ObjectUtil.isNull(bcdnsServiceDO) ? "not found" : JSON.toJSONString(bcdnsServiceDO))
                            ).build()
            );
        } catch (Throwable t) {
            log.error("get bcdns service info {} failed", request.getDomainSpace(), t);
            responseObserver.onNext(Response.newBuilder().setCode(-1).setErrorMsg(t.getMessage()).build());
        } finally {
            responseObserver.onCompleted();
        }
    }

    @Override
    public void deleteBcdnsService(DeleteBcdnsServiceRequest request, StreamObserver<Response> responseObserver) {
        try {
            log.info("delete bcdns service {}", request.getDomainSpace());
            bcdnsManageService.deleteBCDNSServiceDate(request.getDomainSpace());
            responseObserver.onNext(Response.newBuilder().setCode(0).build());
        } catch (Throwable t) {
            log.error("delete bcdns service {} failed", request.getDomainSpace(), t);
            responseObserver.onNext(Response.newBuilder().setCode(-1).setErrorMsg(t.getMessage()).build());
        } finally {
            responseObserver.onCompleted();
        }
    }

    @Override
    public void getBcdnsCertificate(GetBcdnsCertificateRequest request, StreamObserver<Response> responseObserver) {
        try {
            log.info("get bcdns service cert {}", request.getDomainSpace());
            String cert = "";
            var domainSpaceCertWrapper = bcdnsManageService.getDomainSpaceCert(request.getDomainSpace());
            if (ObjectUtil.isNull(domainSpaceCertWrapper) || ObjectUtil.isNull(domainSpaceCertWrapper.getDomainSpaceCert())) {
                cert = "not found";
            } else {
                cert = CrossChainCertificateUtil.formatCrossChainCertificateToPem(domainSpaceCertWrapper.getDomainSpaceCert());
            }
            responseObserver.onNext(
                    Response.newBuilder()
                            .setCode(0)
                            .setGetBcdnsCertificateResp(
                                    GetBcdnsCertificateResp.newBuilder()
                                            .setCertificate(cert)
                            ).build()
            );
        } catch (Throwable t) {
            log.error("get bcdns service cert {} failed", request.getDomainSpace(), t);
            responseObserver.onNext(Response.newBuilder().setCode(-1).setErrorMsg(t.getMessage()).build());
        } finally {
            responseObserver.onCompleted();
        }
    }

    @Override
    public void stopBcdnsService(StopBcdnsServiceRequest request, StreamObserver<Response> responseObserver) {
        try {
            log.info("stop bcdns service {}", request.getDomainSpace());
            bcdnsManageService.stopBCDNSService(request.getDomainSpace());
            responseObserver.onNext(Response.newBuilder().setCode(0).build());
        } catch (Throwable t) {
            log.error("stop bcdns service {} failed", request.getDomainSpace(), t);
            responseObserver.onNext(Response.newBuilder().setCode(-1).setErrorMsg(t.getMessage()).build());
        } finally {
            responseObserver.onCompleted();
        }
    }

    @Override
    public void restartBcdnsService(RestartBcdnsServiceRequest request, StreamObserver<Response> responseObserver) {
        try {
            log.info("restart bcdns service {}", request.getDomainSpace());
            bcdnsManageService.restartBCDNSService(request.getDomainSpace());
            responseObserver.onNext(Response.newBuilder().setCode(0).build());
        } catch (Throwable t) {
            log.error("restart bcdns service {} failed", request.getDomainSpace(), t);
            responseObserver.onNext(Response.newBuilder().setCode(-1).setErrorMsg(t.getMessage()).build());
        } finally {
            responseObserver.onCompleted();
        }
    }

    @Override
    public void addPtcTrustRoot(AddPtcTrustRootRequest request, StreamObserver<Response> responseObserver) {
        try {
            log.info("adding ptc trust root");
            var trustRoot = PTCTrustRoot.decode(request.getRawTrustRoot().toByteArray());
            if (trustRoot.getPtcCredentialSubject().getType() != PTCTypeEnum.COMMITTEE) {
                throw new RuntimeException("ptc trust root type must be committee");
            }
            systemConfigRepository.setPtcTrustRoot(trustRoot);
            responseObserver.onNext(Response.newBuilder().setCode(0).build());
        } catch (Throwable t) {
            log.error("add ptc root failed: {}", Base64.encode(request.getRawTrustRoot().toByteArray()), t);
            responseObserver.onNext(Response.newBuilder().setCode(-1).setErrorMsg(t.getMessage()).build());
        } finally {
            responseObserver.onCompleted();
        }
    }
}

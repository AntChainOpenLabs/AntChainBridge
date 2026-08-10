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

package com.alipay.antchain.bridge.pluginserver.server;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.alipay.antchain.bridge.commons.bbc.AbstractBBCContext;
import com.alipay.antchain.bridge.commons.bbc.DefaultBBCContext;
import com.alipay.antchain.bridge.commons.bbc.syscontract.MonitorContract;
import com.alipay.antchain.bridge.commons.bbc.syscontract.PTCContract;
import com.alipay.antchain.bridge.commons.bbc.syscontract.SDPContract;
import com.alipay.antchain.bridge.commons.core.base.CrossChainDomain;
import com.alipay.antchain.bridge.commons.core.base.CrossChainLane;
import com.alipay.antchain.bridge.commons.core.base.CrossChainMessageReceipt;
import com.alipay.antchain.bridge.commons.core.base.ObjectIdentity;
import com.alipay.antchain.bridge.commons.core.ptc.PTCTrustRoot;
import com.alipay.antchain.bridge.commons.core.ptc.ThirdPartyBlockchainTrustAnchor;
import com.alipay.antchain.bridge.commons.core.rcc.ReliableCrossChainMessage;
import com.alipay.antchain.bridge.plugins.spi.bbc.IBBCService;
import com.alipay.antchain.bridge.plugins.spi.bbc.core.BBCVersionEnum;
import com.alipay.antchain.bridge.plugins.spi.utils.pf4j.Utils;
import com.alipay.antchain.bridge.pluginserver.pluginmanager.IPluginManagerWrapper;
import com.alipay.antchain.bridge.pluginserver.server.exception.ServerErrorCodeEnum;
import com.alipay.antchain.bridge.pluginserver.server.interceptor.RequestTraceInterceptor;
import com.alipay.antchain.bridge.pluginserver.service.*;
import com.google.protobuf.ByteString;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;

import java.math.BigInteger;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Resource;

@GrpcService(interceptors = RequestTraceInterceptor.class)
@Slf4j
public class CrossChainServiceImpl extends CrossChainServiceGrpc.CrossChainServiceImplBase {
    @Resource
    private IPluginManagerWrapper pluginManagerWrapper;

    @Override
    public void heartbeat(Empty request, StreamObserver<Response> responseObserver) {
        responseObserver.onNext(
                ResponseBuilder.buildHeartbeatSuccessResp(
                        HeartbeatResponse.newBuilder()
                                .addAllDomains(pluginManagerWrapper.allRunningDomains())
                                .addAllProducts(pluginManagerWrapper.allSupportProducts())
                )
        );
        responseObserver.onCompleted();
    }

    @Override
    public void ifProductSupport(IfProductSupportRequest request, StreamObserver<Response> responseObserver) {
        responseObserver.onNext(
                ResponseBuilder.buildIfProductSupportSuccessResp(
                        IfProductSupportResponse.newBuilder()
                                .putAllResults(request.getProductsList().stream().distinct().collect(Collectors.toMap(p -> p, p -> pluginManagerWrapper.hasPlugin(p))))
                )
        );
        responseObserver.onCompleted();
    }

    @Override
    public void ifDomainAlive(IfDomainAliveRequest request, StreamObserver<Response> responseObserver) {
        responseObserver.onNext(
                ResponseBuilder.buildIfDomainAliveSuccessResp(
                        IfDomainAliveResponse.newBuilder()
                                .putAllResults(request.getDomainsList().stream().distinct().collect(Collectors.toMap(d -> d, d -> pluginManagerWrapper.hasDomain(d))))
                )
        );
        responseObserver.onCompleted();
    }

    @Override
    public void bbcCall(CallBBCRequest request, StreamObserver<Response> responseObserver) {
        String product = request.getProduct();
        String domain = request.getDomain();
        Response resp;

        // 1. Startup request needs to be handled separately， because it may need create a service first.
        if (request.hasStartUpReq()) {
            responseObserver.onNext(handleStartUp(product, domain, request.getStartUpReq()));
            responseObserver.onCompleted();
            return;
        }

        if (!pluginManagerWrapper.hasPlugin(product)) {
            responseObserver.onNext(ResponseBuilder.buildFailResp(ServerErrorCodeEnum.BBC_PLUGIN_NOT_SUPPORT, "product not supported"));
            responseObserver.onCompleted();
            return;
        }

        if (!pluginManagerWrapper.hasDomain(domain)) {
            responseObserver.onNext(ResponseBuilder.buildFailResp(ServerErrorCodeEnum.BBC_OBJECT_NOT_STARTED, "call startup plz"));
            responseObserver.onCompleted();
            return;
        }

        // 2. Other bbc requests need to be processed based on an existing service.
        IBBCService bbcService;
        try {
            bbcService = pluginManagerWrapper.getBBCService(product, domain);
            if (ObjectUtil.isNull(bbcService)) {
                throw new RuntimeException("null bbc service object");
            }
        } catch (Exception e) {
            log.error("BBCCall fail when getting the bbc object [product: {}, domain: {}, request: {}, errorCode: {}, errorMsg: {}]",
                    product, domain, request.getRequestCase(), ServerErrorCodeEnum.BBC_GET_SERVICE_ERROR.getErrorCode(), ServerErrorCodeEnum.BBC_GET_SERVICE_ERROR.getShortMsg(), e);
            responseObserver.onNext(ResponseBuilder.buildFailResp(ServerErrorCodeEnum.BBC_GET_SERVICE_ERROR, e.toString()));
            responseObserver.onCompleted();
            return;
        }

        try {
            // 3. Other bbc requests handler.
            switch (request.getRequestCase()) {
                case SHUTDOWNREQ:
                    resp = handleShutDown(bbcService, product, domain);
                    break;
                case GETCONTEXTREQ:
                    resp = handleGetContext(bbcService, product, domain);
                    break;
                case SETUPSDPMESSAGECONTRACTREQ:
                    resp = handleSetupSDPMessageContract(bbcService, product, domain);
                    break;
                case SETUPAUTHMESSAGECONTRACTREQ:
                    resp = handleSetupAuthMessageContract(bbcService, product, domain);
                    break;
                case SETUPMONITORMESSAGECONTRACTREQ:
                    resp = handleSetupMonitorMessageContract(bbcService, product, domain);
                    break;
                case SETPROTOCOLREQ:
                    resp = handleSetProtocol(bbcService, request.getSetProtocolReq(), product, domain);
                    break;
                case SETPROTOCOLINMONITORREQ:
                    resp = handleSetProtocolInMonitor(bbcService, request.getSetProtocolInMonitorReq(), product, domain);
                    break;
                case SETMONITORCONTROLREQ:
                    resp = handleSetMonitorControl(bbcService, request.getSetMonitorControlReq(), product, domain);
                    break;
                case SETPTCHUBINMONITORVERIFIERREQ:
                    resp = handleSetPtcHubInMonitorVerifier(bbcService, request.getSetPtcHubInMonitorVerifierReq(), product, domain);
                    break;
                case SETMONITORCONTRACTREQ:
                    resp = handleSetMonitorContract(bbcService, request.getSetMonitorContractReq(), product, domain);
                    break;
                case SETAMCONTRACTREQ:
                    resp = handleSetAmContract(bbcService, request.getSetAmContractReq(), product, domain);
                    break;
                case SETPTCCONTRACTREQ:
                    resp = handleSetPtcContract(bbcService, request.getSetPtcContractReq(), product, domain);
                    break;
                case RELAYAUTHMESSAGEREQ:
                    resp = handleRelayAuthMessage(bbcService, request.getRelayAuthMessageReq(), product, domain);
                    break;
                case RELAYMONITORORDERREQ:
                    resp = handleRelayMonitorOrder(bbcService, request.getRelayMonitorOrderReq(), product, domain);
                    break;
                case READCROSSCHAINMESSAGERECEIPTREQ:
                    resp = handleReadCrossChainMessageReceiptRequest(bbcService, request.getReadCrossChainMessageReceiptReq(), product, domain);
                    break;
                case READCROSSCHAINMESSAGESBYHEIGHTREQ:
                    resp = handleReadCrossChainMessagesByHeight(bbcService, request.getReadCrossChainMessagesByHeightReq(), product, domain);
                    break;
                case QUERYSDPMESSAGESEQREQ:
                    resp = handleQuerySDPMessageSeq(bbcService, request.getQuerySDPMessageSeqReq(), product, domain);
                    break;
                case QUERYLATESTHEIGHTREQ:
                    resp = handleQueryLatestHeight(bbcService, product, domain);
                    break;
                case SETLOCALDOMAINREQ:
                    resp = handleSetLocalDomain(bbcService, request.getSetLocalDomainReq(), product, domain);
                    break;
                case READCONSENSUSSTATEREQ:
                    resp = handleReadConsensusState(bbcService, request.getReadConsensusStateReq(), product, domain);
                    break;
                case HASTPBTAREQ:
                    resp = handleHasTpBta(bbcService, request.getHasTpBtaReq(), product, domain);
                    break;
                case GETTPBTAREQ:
                    resp = handleGetTpBta(bbcService, request.getGetTpBtaReq(), product, domain);
                    break;
                case GETSUPPORTEDPTCTYPEREQ:
                    resp = handleGetSupportedPtcType(bbcService, product, domain);
                    break;
                case GETPTCTRUSTROOTREQ:
                    resp = handleGetPtcTrustRoot(bbcService, request.getGetPTCTrustRootReq(), product, domain);
                    break;
                case HASPTCTRUSTROOTREQ:
                    resp = handleHasPtcTrustRoot(bbcService, request.getHasPTCTrustRootReq(), product, domain);
                    break;
                case GETPTCVERIFYANCHORREQ:
                    resp = handleGetPtcVerifyAnchor(bbcService, request.getGetPTCVerifyAnchorReq(), product, domain);
                    break;
                case HASPTCVERIFYANCHORREQ:
                    resp = handleHasPtcVerifyAnchor(bbcService, request.getHasPTCVerifyAnchorReq(), product, domain);
                    break;
                case SETUPPTCCONTRACTREQ:
                    resp = handleSetupPtcContract(bbcService, request.getSetupPTCContractReq(), product, domain);
                    break;
                case UPDATEPTCTRUSTROOTREQ:
                    resp = handleUpdatePtcTrustRoot(bbcService, request.getUpdatePTCTrustRootReq(), product, domain);
                    break;
                case ADDTPBTAREQ:
                    resp = handleAddTpBta(bbcService, request.getAddTpBtaReq(), product, domain);
                    break;
                case QUERYVALIDATEDBLOCKSTATEREQUEST:
                    resp = handleQueryValidatedBlockStateReq(bbcService, request.getQueryValidatedBlockStateRequest(), product, domain);
                    break;
                case RECVOFFCHAINEXCEPTIONREQUEST:
                    resp = handleRecvOffChainExceptionReq(bbcService, request.getRecvOffChainExceptionRequest(), product, domain);
                    break;
                case RELIABLERETRYREQUEST:
                    resp = handleReliableRetryRequest(bbcService, request.getReliableRetryRequest(), product, domain);
                    break;
                default:
                    log.error("BBCCall fail [product: {}, domain: {}, request: {}, errorCode: {}, errorMsg: {}]", product, domain, request.getRequestCase(), ServerErrorCodeEnum.UNSUPPORT_BBC_REQUEST_ERROR.getErrorCode(), ServerErrorCodeEnum.UNSUPPORT_BBC_REQUEST_ERROR.getShortMsg());
                    resp = ResponseBuilder.buildFailResp(ServerErrorCodeEnum.UNSUPPORT_BBC_REQUEST_ERROR);
                    break;
            }
        } catch (Error e) {
            log.error(
                    "BBCCall has internal error [product: {}, domain: {}, request: {}]",
                    product,
                    domain,
                    request.getRequestCase(),
                    e
            );
            resp = ResponseBuilder.buildFailResp(ServerErrorCodeEnum.UNKNOWN_ERROR);
        }

        responseObserver.onNext(resp);
        responseObserver.onCompleted();
    }

    private Response handleStartUp(String product, String domain, StartUpRequest request) {
        IBBCService bbcService;

        // 1. get service
        if (pluginManagerWrapper.hasDomain(domain)) {
            log.info("get service for blockchain ( product: {} , domain: {} )", product, domain);
            try {
                bbcService = pluginManagerWrapper.getBBCService(product, domain);
            } catch (Exception e) {
                log.error("BBCCall(handleStartUp) fail [product: {}, domain: {}, errorCode: {}, errorMsg: {}]", product, domain, ServerErrorCodeEnum.BBC_GET_SERVICE_ERROR.getErrorCode(), ServerErrorCodeEnum.BBC_GET_SERVICE_ERROR.getShortMsg(), e);
                return ResponseBuilder.buildFailResp(ServerErrorCodeEnum.BBC_GET_SERVICE_ERROR, e.toString());
            }
        } else {
            log.info("create service for blockchain ( product: {} , domain: {} )", product, domain);
            try {
                bbcService = pluginManagerWrapper.createBBCService(product, domain);
            } catch (Throwable t) {
                log.error("BBCCall(handleStartUp) fail [product: {}, domain: {}, errorCode: {}, errorMsg: {}]", product, domain, ServerErrorCodeEnum.BBC_CREATE_ERROR.getErrorCode(), ServerErrorCodeEnum.BBC_CREATE_ERROR.getShortMsg(), t);
                return ResponseBuilder.buildFailResp(ServerErrorCodeEnum.BBC_CREATE_ERROR, t.toString());
            }
        }

        log.info("startup service for blockchain ( product: {} , domain: {} )", product, domain);
        // 2. start service
        try {
            DefaultBBCContext ctx = new DefaultBBCContext();
            ctx.decodeFromBytes(request.getRawContext().toByteArray());
            bbcService.startup(ctx);

            return ResponseBuilder.buildBBCSuccessResp(CallBBCResponse.newBuilder());
        } catch (Throwable t) {
            log.error("BBCCall(handleStartUp) fail [product: {}, domain: {}, errorCode: {}, errorMsg: {}]", product, domain, ServerErrorCodeEnum.BBC_STARTUP_ERROR.getErrorCode(), ServerErrorCodeEnum.BBC_STARTUP_ERROR.getShortMsg(), t);
            return ResponseBuilder.buildFailResp(ServerErrorCodeEnum.BBC_STARTUP_ERROR, t.toString());
        }
    }

    private Response handleShutDown(IBBCService bbcService, String product, String domain) {
        try {
            bbcService.shutdown();
            return ResponseBuilder.buildBBCSuccessResp(CallBBCResponse.newBuilder());
        } catch (Exception e) {
            log.error("BBCCall(handleShutDown) fail [product: {}, domain: {}, errorCode: {}, errorMsg: {}]", product, domain, ServerErrorCodeEnum.BBC_SHUTDOWN_ERROR.getErrorCode(), ServerErrorCodeEnum.BBC_SHUTDOWN_ERROR.getShortMsg(), e);
            return ResponseBuilder.buildFailResp(ServerErrorCodeEnum.BBC_SHUTDOWN_ERROR, e.toString());
        }
    }

    private Response handleGetContext(IBBCService bbcService, String product, String domain) {
        try {
            AbstractBBCContext ctx = bbcService.getContext();
            return ResponseBuilder.buildBBCSuccessResp(CallBBCResponse.newBuilder()
                    .setGetContextResp(GetContextResponse.newBuilder()
                            .setRawContext(ByteString.copyFrom(ctx.encodeToBytes()))
                    )
            );
        } catch (Exception e) {
            log.error("BBCCall(handleGetContext) fail [product: {}, domain: {}, errorCode: {}, errorMsg: {}]", product, domain, ServerErrorCodeEnum.BBC_GETCONTEXT_ERROR.getErrorCode(), ServerErrorCodeEnum.BBC_GETCONTEXT_ERROR.getShortMsg(), e);
            return ResponseBuilder.buildFailResp(ServerErrorCodeEnum.BBC_GETCONTEXT_ERROR, e.toString());
        }
    }

    private Response handleSetupMonitorMessageContract(IBBCService bbcService, String product, String domain) {
        try {
            bbcService.setupMonitorContract();
            MonitorContract monitor = bbcService.getContext().getMonitorContract();
            log.info("BBCCall(handleSetupMonitorMessageContract) success [product: {}, domain: {}]: monitorAddr: {}, monitorStatus: {}", product, domain, monitor.getContractAddress(), monitor.getStatus());
            return ResponseBuilder.buildBBCSuccessResp(CallBBCResponse.newBuilder()
                    .setSetupMonitorResp(SetupMonitorMessageContractResponse.newBuilder()
                            .setMonitorContract(
                                    MonitorMessageContract.newBuilder()
                                            .setContractAddress(monitor.getContractAddress())
                                            .setStatusValue(monitor.getStatus().ordinal())
                            )
                    )
            );
        } catch (Exception e) {
            log.error("BBCCall(handleSetupMonitorMessageContract) fail [product: {}, domain: {}, errorCode: {}, errorMsg: {}]", product, domain, ServerErrorCodeEnum.BBC_SETUPMONITORMESSAGECONTRACT_ERROR.getErrorCode(), ServerErrorCodeEnum.BBC_SETUPMONITORMESSAGECONTRACT_ERROR.getShortMsg(), e);
            return ResponseBuilder.buildFailResp(ServerErrorCodeEnum.BBC_SETUPMONITORMESSAGECONTRACT_ERROR, e.toString());
        }
    }

    private Response handleSetupSDPMessageContract(IBBCService bbcService, String product, String domain) {
        try {
            bbcService.setupSDPMessageContract();
            SDPContract sdp = bbcService.getContext().getSdpContract();
            return ResponseBuilder.buildBBCSuccessResp(CallBBCResponse.newBuilder()
                    .setSetupSDPResp(SetupSDPMessageContractResponse.newBuilder()
                            .setSdpContract(
                                    SDPMessageContract.newBuilder()
                                            .setContractAddress(sdp.getContractAddress())
                                            .setStatusValue(sdp.getStatus().ordinal())
                            )
                    )
            );
        } catch (Exception e) {
            log.error("BBCCall(handleSetupSDPMessageContract) fail [product: {}, domain: {}, errorCode: {}, errorMsg: {}]", product, domain, ServerErrorCodeEnum.BBC_SETUPSDPMESSAGECONTRACT_ERROR.getErrorCode(), ServerErrorCodeEnum.BBC_SETUPSDPMESSAGECONTRACT_ERROR.getShortMsg(), e);
            return ResponseBuilder.buildFailResp(ServerErrorCodeEnum.BBC_SETUPSDPMESSAGECONTRACT_ERROR, e.toString());
        }
    }

    private Response handleSetupAuthMessageContract(IBBCService bbcService, String product, String domain) {
        try {
            bbcService.setupAuthMessageContract();
            return ResponseBuilder.buildBBCSuccessResp(CallBBCResponse.newBuilder()
                    .setSetupAMResp(SetupAuthMessageContractResponse.newBuilder()
                            .setAmContract(
                                    AuthMessageContract.newBuilder()
                                            .setContractAddress(bbcService.getContext().getAuthMessageContract().getContractAddress())
                                            .setStatusValue(bbcService.getContext().getAuthMessageContract().getStatus().ordinal())
                            )
                    )
            );
        } catch (Exception e) {
            log.error("BBCCall(handleSetupAuthMessageContract) fail [product: {}, domain: {}, errorCode: {}, errorMsg: {}]", product, domain, ServerErrorCodeEnum.BBC_SETUPAUTHMESSAGECONTRACT_ERROR.getErrorCode(), ServerErrorCodeEnum.BBC_SETUPAUTHMESSAGECONTRACT_ERROR.getShortMsg(), e);
            return ResponseBuilder.buildFailResp(ServerErrorCodeEnum.BBC_SETUPAUTHMESSAGECONTRACT_ERROR, e.toString());
        }
    }

    private Response handleSetProtocol(IBBCService bbcService, SetProtocolRequest request, String product, String domain) {
        try {
            bbcService.setProtocol(request.getProtocolAddress(), request.getProtocolType());
            return ResponseBuilder.buildBBCSuccessResp(CallBBCResponse.newBuilder());
        } catch (Exception e) {
            log.error("BBCCall(handleSetProtocol) fail [product: {}, domain: {}, errorCode: {}, errorMsg: {}]", product, domain, ServerErrorCodeEnum.BBC_SETPROTOCOL_ERROR.getErrorCode(), ServerErrorCodeEnum.BBC_SETPROTOCOL_ERROR.getShortMsg(), e);
            return ResponseBuilder.buildFailResp(ServerErrorCodeEnum.BBC_SETPROTOCOL_ERROR, e.toString());
        }
    }

    private Response handleSetProtocolInMonitor(IBBCService bbcService, SetProtocolInMonitorRequest request, String product, String domain) {
        try {
            bbcService.setProtocolInMonitor(request.getProtocolAddress());
            return ResponseBuilder.buildBBCSuccessResp(CallBBCResponse.newBuilder());
        } catch (Exception e) {
            log.error("BBCCall(handleSetProtocolInMonitor) fail [product: {}, domain: {}, errorCode: {}, errorMsg: {}]", product, domain, ServerErrorCodeEnum.BBC_SETPROTOCOLINMONITOR_ERROR.getErrorCode(), ServerErrorCodeEnum.BBC_SETPROTOCOLINMONITOR_ERROR.getShortMsg(), e);
            return ResponseBuilder.buildFailResp(ServerErrorCodeEnum.BBC_SETPROTOCOLINMONITOR_ERROR, e.toString());
        }
    }

    private Response handleSetMonitorControl(IBBCService bbcService, SetMonitorControlRequest request, String product, String domain) {
        try {
            bbcService.setMonitorControl(request.getMonitorType());
            return ResponseBuilder.buildBBCSuccessResp(CallBBCResponse.newBuilder());
        } catch (Exception e) {
            log.error("BBCCall(handleSetMonitorControl) fail [product: {}, domain: {}, errorCode: {}, errorMsg: {}]", product, domain, ServerErrorCodeEnum.BBC_SETMONITORCONTROL_ERROR.getErrorCode(), ServerErrorCodeEnum.BBC_SETMONITORCONTROL_ERROR.getShortMsg(), e);
            return ResponseBuilder.buildFailResp(ServerErrorCodeEnum.BBC_SETMONITORCONTROL_ERROR, e.toString());
        }
    }

    private Response handleSetPtcHubInMonitorVerifier(IBBCService bbcService, SetPtcHubInMonitorVerifierRequest request, String product, String domain) {
        try {
            bbcService.setPtcHubInMonitorVerifier(request.getContractAddress());
            return ResponseBuilder.buildBBCSuccessResp(CallBBCResponse.newBuilder());
        } catch (Exception e) {
            log.error("BBCCall(handleSetPtcHubInMonitorVerifier) fail [product: {}, domain: {}, errorCode: {}, errorMsg: {}]", product, domain, ServerErrorCodeEnum.BBC_SETPTCHUBINMONITORVERIFIER_ERROR.getErrorCode(), ServerErrorCodeEnum.BBC_SETPTCHUBINMONITORVERIFIER_ERROR.getShortMsg(), e);
            return ResponseBuilder.buildFailResp(ServerErrorCodeEnum.BBC_SETPTCHUBINMONITORVERIFIER_ERROR, e.toString());
        }
    }

    private Response handleSetMonitorContract(IBBCService bbcService, SetMonitorContractRequest request, String product, String domain) {
        try {
            bbcService.setMonitorContract(request.getContractAddress());
            return ResponseBuilder.buildBBCSuccessResp(CallBBCResponse.newBuilder());
        } catch (Exception e) {
            log.error("BBCCall(handleSetMonitorContract) fail [product: {}, domain: {}, errorCode: {}, errorMsg: {}]", product, domain, ServerErrorCodeEnum.BBC_SETMONITORCONTRACT_ERROR.getErrorCode(), ServerErrorCodeEnum.BBC_SETMONITORCONTRACT_ERROR.getShortMsg(), e);
            return ResponseBuilder.buildFailResp(ServerErrorCodeEnum.BBC_SETMONITORCONTRACT_ERROR, e.toString());
        }
    }

    private Response handleSetAmContract(IBBCService bbcService, SetAmContractRequest request, String product, String domain) {
        try {
            bbcService.setAmContract(request.getContractAddress());
            return ResponseBuilder.buildBBCSuccessResp(CallBBCResponse.newBuilder());
        } catch (Exception e) {
            log.error("BBCCall(handleSetAmContract) fail [product: {}, domain: {}, errorCode: {}, errorMsg: {}]", product, domain, ServerErrorCodeEnum.BBC_SETAMCONTRACT_ERROR.getErrorCode(), ServerErrorCodeEnum.BBC_SETAMCONTRACT_ERROR.getShortMsg(), e);
            return ResponseBuilder.buildFailResp(ServerErrorCodeEnum.BBC_SETAMCONTRACT_ERROR, e.toString());
        }
    }

    private Response handleSetPtcContract(IBBCService bbcService, SetPtcContractRequest request, String product, String domain) {
        if (!checkBBCVersionSatisfiedV1(bbcService)) {
            return ResponseBuilder.buildFailResp(ServerErrorCodeEnum.BBC_VERSION_NOT_SUPPORTED,
                    StrUtil.format("bbc version not supported, please check plugin code for product {}", product));
        }
        try {
            bbcService.setPtcContract(request.getPtcContractAddress());
            return ResponseBuilder.buildBBCSuccessResp(CallBBCResponse.newBuilder());
        } catch (Exception e) {
            log.error("BBCCall(handleSetPtcContract) fail [product: {}, domain: {}, errorCode: {}, errorMsg: {}]", product, domain, ServerErrorCodeEnum.BBC_SETAMCONTRACT_ERROR.getErrorCode(), ServerErrorCodeEnum.BBC_SETAMCONTRACT_ERROR.getShortMsg(), e);
            return ResponseBuilder.buildFailResp(ServerErrorCodeEnum.BBC_SETAMCONTRACT_ERROR, e.toString());
        }
    }

    private Response handleRelayAuthMessage(IBBCService bbcService, RelayAuthMessageRequest request, String product, String domain) {
        try {
            CrossChainMessageReceipt ret = bbcService.relayAuthMessage(request.getRawMessage().toByteArray());
            return ResponseBuilder.buildBBCSuccessResp(CallBBCResponse.newBuilder()
                    .setRelayAuthMessageResponse(RelayAuthMessageResponse.newBuilder()
                            .setReceipt(
                                    com.alipay.antchain.bridge.pluginserver.service.CrossChainMessageReceipt.newBuilder()
                                            .setTxhash(ObjectUtil.defaultIfNull(ret.getTxhash(), ""))
                                            .setConfirmed(ret.isConfirmed())
                                            .setSuccessful(ret.isSuccessful())
                                            .setErrorMsg(ObjectUtil.defaultIfNull(ret.getErrorMsg(), ""))
                                            .setTxTimestamp(ret.getTxTimestamp())
                                            .setRawTx(ByteString.copyFrom(ObjectUtil.defaultIfNull(ret.getRawTx(), new byte[]{})))
                            )
                    )
            );
        } catch (Exception e) {
            log.error("BBCCall(handleRelayAuthMessage) fail [product: {}, domain: {}, errorCode: {}, errorMsg: {}]", product, domain, ServerErrorCodeEnum.BBC_RELAYAUTHMESSAGE_ERROR.getErrorCode(), ServerErrorCodeEnum.BBC_RELAYAUTHMESSAGE_ERROR.getShortMsg(), e);
            return ResponseBuilder.buildFailResp(ServerErrorCodeEnum.BBC_RELAYAUTHMESSAGE_ERROR, e.toString());
        }
    }

    private Response handleRelayMonitorOrder(IBBCService bbcService, RelayMonitorOrderRequest request, String product, String domain) {
        try {
            CrossChainMessageReceipt ret = bbcService.relayMonitorOrder(request.getCommitteeId(), request.getSignAlgo(), request.getRawProof().toByteArray(), request.getRawMonitorOrder().toByteArray());
            return ResponseBuilder.buildBBCSuccessResp(CallBBCResponse.newBuilder()
                    .setRelayMonitorOrderResp(RelayMonitorOrderResponse.newBuilder()
                            .setReceipt(
                                    com.alipay.antchain.bridge.pluginserver.service.CrossChainMessageReceipt.newBuilder()
                                            .setTxhash(ObjectUtil.defaultIfNull(ret.getTxhash(), ""))
                                            .setConfirmed(ret.isConfirmed())
                                            .setSuccessful(ret.isSuccessful())
                                            .setErrorMsg(ObjectUtil.defaultIfNull(ret.getErrorMsg(), ""))
                                            .setTxTimestamp(ret.getTxTimestamp())
                                            .setRawTx(ByteString.copyFrom(ObjectUtil.defaultIfNull(ret.getRawTx(), new byte[]{})))
                            )
                    )
            );
        } catch (Exception e) {
            log.error("BBCCall(handleRelayMonitorOrder) fail [product: {}, domain: {}, errorCode: {}, errorMsg: {}]", product, domain, ServerErrorCodeEnum.BBC_RELAYMONITORORDER_ERROR.getErrorCode(), ServerErrorCodeEnum.BBC_RELAYMONITORORDER_ERROR.getShortMsg(), e);
            return ResponseBuilder.buildFailResp(ServerErrorCodeEnum.BBC_RELAYMONITORORDER_ERROR, e.toString());
        }
    }

    private Response handleReadCrossChainMessageReceiptRequest(IBBCService bbcService, ReadCrossChainMessageReceiptRequest request, String product, String domain) {
        try {
            CrossChainMessageReceipt receipt = bbcService.readCrossChainMessageReceipt(request.getTxhash());
            if (ObjectUtil.isNull(receipt)) {
                throw new RuntimeException("empty receipt for tx " + request.getTxhash());
            }

            return ResponseBuilder.buildBBCSuccessResp(
                    CallBBCResponse.newBuilder().setReadCrossChainMessageReceiptResp(
                            ReadCrossChainMessageReceiptResponse.newBuilder()
                                    .setReceipt(
                                            com.alipay.antchain.bridge.pluginserver.service.CrossChainMessageReceipt.newBuilder()
                                                    .setConfirmed(receipt.isConfirmed())
                                                    .setSuccessful(receipt.isSuccessful())
                                                    .setTxhash(StrUtil.nullToDefault(receipt.getTxhash(), ""))
                                                    .setErrorMsg(StrUtil.nullToDefault(receipt.getErrorMsg(), ""))
                                                    .setTxTimestamp(receipt.getTxTimestamp())
                                                    .setRawTx(ByteString.copyFrom(ObjectUtil.defaultIfNull(receipt.getRawTx(), new byte[]{})))
                                    )
                    )
            );
        } catch (Exception e) {
            log.error("BBCCall(handleIsCrossChainMessageConfirmed) fail [product: {}, domain: {}, errorCode: {}, errorMsg: {}]", product, domain, ServerErrorCodeEnum.BBC_READ_CCMSG_RET_ERROR.getErrorCode(), ServerErrorCodeEnum.BBC_READ_CCMSG_RET_ERROR.getShortMsg(), e);
            return ResponseBuilder.buildFailResp(ServerErrorCodeEnum.BBC_READ_CCMSG_RET_ERROR, e.toString());
        }
    }

    private Response handleReadCrossChainMessagesByHeight(IBBCService bbcService, ReadCrossChainMessagesByHeightRequest request, String product, String domain) {
        try {
            return ResponseBuilder.buildBBCSuccessResp(CallBBCResponse.newBuilder()
                    .setReadCrossChainMessagesByHeightResp(ReadCrossChainMessagesByHeightResponse.newBuilder()
                            .addAllMessageList(
                                    bbcService.readCrossChainMessagesByHeight(request.getHeight()).stream()
                                            .map(m -> CrossChainMessage.newBuilder()
                                                    .setType(CrossChainMessageType.forNumber(m.getType().ordinal()))
                                                    .setMessage(ByteString.copyFrom(m.getMessage()))
                                                    .setProvableData(ProvableLedgerData.newBuilder()
                                                            .setHeight(m.getProvableData().getHeight())
                                                            .setLedgerData(ByteString.copyFrom(
                                                                    ObjectUtil.defaultIfNull(m.getProvableData().getLedgerData(), new byte[]{})))
                                                            .setProof(ByteString.copyFrom(
                                                                    ObjectUtil.defaultIfNull(m.getProvableData().getProof(), new byte[]{})))
                                                            .setBlockHash(ByteString.copyFrom(
                                                                    ObjectUtil.defaultIfNull(m.getProvableData().getBlockHash(), new byte[]{})))
                                                            .setTimestamp(m.getProvableData().getTimestamp())
                                                            .setTxHash(ByteString.copyFrom(
                                                                    ObjectUtil.defaultIfNull(m.getProvableData().getTxHash(), new byte[]{})))
                                                    ).build()
                                            ).collect(Collectors.toList())
                            )
                    )
            );
        } catch (Exception e) {
            log.error("BBCCall(handleReadCrossChainMessagesByHeight) fail [product: {}, domain: {}, errorCode: {}, errorMsg: {}]", product, domain, ServerErrorCodeEnum.BBC_READCROSSCHAINMESSAGESBYHEIGHT_ERROR.getErrorCode(), ServerErrorCodeEnum.BBC_READCROSSCHAINMESSAGESBYHEIGHT_ERROR.getShortMsg(), e);
            return ResponseBuilder.buildFailResp(ServerErrorCodeEnum.BBC_READCROSSCHAINMESSAGESBYHEIGHT_ERROR, e.toString());
        }
    }

    private Response handleQuerySDPMessageSeq(IBBCService bbcService, QuerySDPMessageSeqRequest request, String product, String domain) {
        try {
            return ResponseBuilder.buildBBCSuccessResp(CallBBCResponse.newBuilder()
                    .setQuerySDPMsgSeqResp(QuerySDPMessageSeqResponse.newBuilder()
                            .setSequence(
                                    bbcService.querySDPMessageSeq(
                                            request.getSenderDomain(),
                                            request.getFromAddress(),
                                            request.getReceiverDomain(),
                                            request.getToAddress()
                                    )
                            )
                    )
            );
        } catch (Exception e) {
            log.error("BBCCall(handleQuerySDPMessageSeq) fail [product: {}, domain: {}, errorCode: {}, errorMsg: {}]", product, domain, ServerErrorCodeEnum.BBC_QUERYSDPMESSAGESEQ_ERROR.getErrorCode(), ServerErrorCodeEnum.BBC_QUERYSDPMESSAGESEQ_ERROR.getShortMsg(), e);
            return ResponseBuilder.buildFailResp(ServerErrorCodeEnum.BBC_QUERYSDPMESSAGESEQ_ERROR, e.toString());
        }
    }

    private Response handleQueryLatestHeight(IBBCService bbcService, String product, String domain) {
        try {
            return ResponseBuilder.buildBBCSuccessResp(CallBBCResponse.newBuilder()
                    .setQueryLatestHeightResponse(QueryLatestHeightResponse.newBuilder()
                            .setHeight(bbcService.queryLatestHeight())
                    )
            );
        } catch (Exception e) {
            log.error(
                    "BBCCall(handleQueryLatestHeight) fail [product: {}, domain: {}, errorCode: {}, errorMsg: {}]",
                    product, domain,
                    ServerErrorCodeEnum.BBC_QUERYLATESTHEIGHT_ERROR.getErrorCode(),
                    ServerErrorCodeEnum.BBC_QUERYLATESTHEIGHT_ERROR.getShortMsg(),
                    e
            );
            return ResponseBuilder.buildFailResp(ServerErrorCodeEnum.BBC_QUERYLATESTHEIGHT_ERROR, e.toString());
        }
    }

    private Response handleSetLocalDomain(IBBCService bbcService, SetLocalDomainRequest request, String product, String domain) {
        try {
            bbcService.setLocalDomain(request.getDomain());
            return ResponseBuilder.buildBBCSuccessResp(CallBBCResponse.newBuilder());
        } catch (Exception e) {
            log.error(
                    "BBCCall(handleSetLocalDomain) fail [product: {}, domain: {}, errorCode: {}, errorMsg: {}]",
                    product, domain,
                    ServerErrorCodeEnum.BBC_SETLOCALDOMAIN_ERROR.getErrorCode(),
                    ServerErrorCodeEnum.BBC_SETLOCALDOMAIN_ERROR.getShortMsg(),
                    e
            );
            return ResponseBuilder.buildFailResp(ServerErrorCodeEnum.BBC_SETLOCALDOMAIN_ERROR, e.toString());
        }
    }

    private Response handleReadConsensusState(IBBCService bbcService, ReadConsensusStateRequest request, String product, String domain) {
        if (!checkBBCVersionSatisfiedV1(bbcService)) {
            return ResponseBuilder.buildFailResp(ServerErrorCodeEnum.BBC_VERSION_NOT_SUPPORTED,
                    StrUtil.format("bbc version not supported, please check plugin code for product {}", product));
        }
        try {
            return ResponseBuilder.buildBBCSuccessResp(
                    CallBBCResponse.newBuilder()
                            .setReadConsensusStateResponse(
                                    ReadConsensusStateResponse.newBuilder()
                                            .setConsensusState(
                                                    ByteString.copyFrom(bbcService.readConsensusState(new BigInteger(request.getHeight())).encode())
                                            )
                            )
            );
        } catch (Exception e) {
            log.error(
                    "BBCCall(handleReadConsensusState) fail [product: {}, domain: {}, errorCode: {}, errorMsg: {}]",
                    product, domain,
                    ServerErrorCodeEnum.BBC_READ_CONSENSUS_STATE_ERROR.getErrorCode(),
                    ServerErrorCodeEnum.BBC_READ_CONSENSUS_STATE_ERROR.getShortMsg(),
                    e
            );
            return ResponseBuilder.buildFailResp(ServerErrorCodeEnum.BBC_READ_CONSENSUS_STATE_ERROR, e.toString());
        }
    }

    private Response handleHasTpBta(IBBCService bbcService, HasTpBtaRequest request, String product, String domain) {
        if (!checkBBCVersionSatisfiedV1(bbcService)) {
            return ResponseBuilder.buildFailResp(ServerErrorCodeEnum.BBC_VERSION_NOT_SUPPORTED,
                    StrUtil.format("bbc version not supported, please check plugin code for product {}", product));
        }
        try {
            return ResponseBuilder.buildBBCSuccessResp(
                    CallBBCResponse.newBuilder()
                            .setHasTpBtaResp(
                                    HasTpBtaResponse.newBuilder()
                                            .setResult(bbcService.hasTpBta(
                                                    CrossChainLane.fromLaneKey(request.getTpbtaLane()),
                                                    request.getTpBtaVersion()
                                            ))
                            )
            );
        } catch (Exception e) {
            log.error(
                    "BBCCall(handleHasTpBta) fail [product: {}, domain: {}, errorCode: {}, errorMsg: {}]",
                    product, domain,
                    ServerErrorCodeEnum.BBC_HAS_TPBTA_ERROR.getErrorCode(),
                    ServerErrorCodeEnum.BBC_HAS_TPBTA_ERROR.getShortMsg(),
                    e
            );
            return ResponseBuilder.buildFailResp(ServerErrorCodeEnum.BBC_HAS_TPBTA_ERROR, e.toString());
        }
    }

    private Response handleGetTpBta(IBBCService bbcService, GetTpBtaRequest request, String product, String domain) {
        if (!checkBBCVersionSatisfiedV1(bbcService)) {
            return ResponseBuilder.buildFailResp(ServerErrorCodeEnum.BBC_VERSION_NOT_SUPPORTED,
                    StrUtil.format("bbc version not supported, please check plugin code for product {}", product));
        }
        try {
            return ResponseBuilder.buildBBCSuccessResp(
                    CallBBCResponse.newBuilder()
                            .setGetTpBtaResp(
                                    GetTpBtaResponse.newBuilder()
                                            .setTpBta(ByteString.copyFrom(
                                                    bbcService.getTpBta(
                                                            CrossChainLane.fromLaneKey(request.getTpbtaLane()),
                                                            request.getTpBtaVersion()
                                                    ).encode()
                                            ))
                            )
            );
        } catch (Exception e) {
            log.error(
                    "BBCCall(handleGetTpBta) fail [product: {}, domain: {}, errorCode: {}, errorMsg: {}]",
                    product, domain,
                    ServerErrorCodeEnum.BBC_GET_TPBTA_ERROR.getErrorCode(),
                    ServerErrorCodeEnum.BBC_GET_TPBTA_ERROR.getShortMsg(),
                    e
            );
            return ResponseBuilder.buildFailResp(ServerErrorCodeEnum.BBC_GET_TPBTA_ERROR, e.toString());
        }
    }

    private Response handleGetSupportedPtcType(IBBCService bbcService, String product, String domain) {
        if (!checkBBCVersionSatisfiedV1(bbcService)) {
            return ResponseBuilder.buildFailResp(ServerErrorCodeEnum.BBC_VERSION_NOT_SUPPORTED,
                    StrUtil.format("bbc version not supported, please check plugin code for product {}", product));
        }
        try {
            return ResponseBuilder.buildBBCSuccessResp(
                    CallBBCResponse.newBuilder()
                            .setGetSupportedPTCTypeResp(
                                    GetSupportedPTCTypeResponse.newBuilder()
                                            .addAllPtcTypes(bbcService.getSupportedPTCType().stream().map(Enum::name).collect(Collectors.toList()))
                            )
            );
        } catch (Exception e) {
            log.error(
                    "BBCCall(handleGetSupportedPtcType) fail [product: {}, domain: {}, errorCode: {}, errorMsg: {}]",
                    product, domain,
                    ServerErrorCodeEnum.BBC_GET_SUPPORTED_PTC_TYPE_ERROR.getErrorCode(),
                    ServerErrorCodeEnum.BBC_GET_SUPPORTED_PTC_TYPE_ERROR.getShortMsg(),
                    e
            );
            return ResponseBuilder.buildFailResp(ServerErrorCodeEnum.BBC_GET_SUPPORTED_PTC_TYPE_ERROR, e.toString());
        }
    }

    private Response handleGetPtcTrustRoot(IBBCService bbcService, GetPTCTrustRootRequest request, String product, String domain) {
        if (!checkBBCVersionSatisfiedV1(bbcService)) {
            return ResponseBuilder.buildFailResp(ServerErrorCodeEnum.BBC_VERSION_NOT_SUPPORTED,
                    StrUtil.format("bbc version not supported, please check plugin code for product {}", product));
        }
        try {
            return ResponseBuilder.buildBBCSuccessResp(
                    CallBBCResponse.newBuilder()
                            .setGetPTCTrustRootResp(
                                    GetPTCTrustRootResponse.newBuilder()
                                            .setPtcTrustRoot(ByteString.copyFrom(
                                                    bbcService.getPTCTrustRoot(ObjectIdentity.decode(request.getPtcOwnerOid().toByteArray())).encode()
                                            ))
                            )
            );
        } catch (Exception e) {
            log.error(
                    "BBCCall(handleGetPtcTrustRoot) fail [product: {}, domain: {}, errorCode: {}, errorMsg: {}]",
                    product, domain,
                    ServerErrorCodeEnum.BBC_GET_PTC_TRUST_ROOT_ERROR.getErrorCode(),
                    ServerErrorCodeEnum.BBC_GET_PTC_TRUST_ROOT_ERROR.getShortMsg(),
                    e
            );
            return ResponseBuilder.buildFailResp(ServerErrorCodeEnum.BBC_GET_PTC_TRUST_ROOT_ERROR, e.toString());
        }
    }

    private Response handleHasPtcTrustRoot(IBBCService bbcService, HasPTCTrustRootRequest request, String product, String domain) {
        if (!checkBBCVersionSatisfiedV1(bbcService)) {
            return ResponseBuilder.buildFailResp(ServerErrorCodeEnum.BBC_VERSION_NOT_SUPPORTED,
                    StrUtil.format("bbc version not supported, please check plugin code for product {}", product));
        }
        try {
            return ResponseBuilder.buildBBCSuccessResp(
                    CallBBCResponse.newBuilder()
                            .setHasPTCTrustRootResp(
                                    HasPTCTrustRootResponse.newBuilder()
                                            .setResult(bbcService.hasPTCTrustRoot(ObjectIdentity.decode(request.getPtcOwnerOid().toByteArray())))
                            )
            );
        } catch (Exception e) {
            log.error(
                    "BBCCall(handleHasPtcTrustRoot) fail [product: {}, domain: {}, errorCode: {}, errorMsg: {}]",
                    product, domain,
                    ServerErrorCodeEnum.BBC_HAS_PTC_TRUST_ROOT_ERROR.getErrorCode(),
                    ServerErrorCodeEnum.BBC_HAS_PTC_TRUST_ROOT_ERROR.getShortMsg(),
                    e
            );
            return ResponseBuilder.buildFailResp(ServerErrorCodeEnum.BBC_HAS_PTC_TRUST_ROOT_ERROR, e.toString());
        }
    }

    private Response handleGetPtcVerifyAnchor(IBBCService bbcService, GetPTCVerifyAnchorRequest request, String product, String domain) {
        if (!checkBBCVersionSatisfiedV1(bbcService)) {
            return ResponseBuilder.buildFailResp(ServerErrorCodeEnum.BBC_VERSION_NOT_SUPPORTED,
                    StrUtil.format("bbc version not supported, please check plugin code for product {}", product));
        }
        try {
            return ResponseBuilder.buildBBCSuccessResp(
                    CallBBCResponse.newBuilder()
                            .setGetPTCVerifyAnchorResp(
                                    GetPTCVerifyAnchorResponse.newBuilder()
                                            .setPtcVerifyAnchor(ByteString.copyFrom(
                                                    bbcService.getPTCVerifyAnchor(
                                                            ObjectIdentity.decode(request.getPtcOwnerOid().toByteArray()),
                                                            new BigInteger(request.getVerifyAnchorVersion())
                                                    ).encode()
                                            ))
                            )
            );
        } catch (Exception e) {
            log.error(
                    "BBCCall(handleGetPtcVerifyAnchor) fail [product: {}, domain: {}, errorCode: {}, errorMsg: {}]",
                    product, domain,
                    ServerErrorCodeEnum.BBC_GET_PTC_VERIFY_ANCHOR_ERROR.getErrorCode(),
                    ServerErrorCodeEnum.BBC_GET_PTC_VERIFY_ANCHOR_ERROR.getShortMsg(),
                    e
            );
            return ResponseBuilder.buildFailResp(ServerErrorCodeEnum.BBC_GET_PTC_VERIFY_ANCHOR_ERROR, e.toString());
        }
    }

    private Response handleHasPtcVerifyAnchor(IBBCService bbcService, HasPTCVerifyAnchorRequest request, String product, String domain) {
        if (!checkBBCVersionSatisfiedV1(bbcService)) {
            return ResponseBuilder.buildFailResp(ServerErrorCodeEnum.BBC_VERSION_NOT_SUPPORTED,
                    StrUtil.format("bbc version not supported, please check plugin code for product {}", product));
        }
        try {
            return ResponseBuilder.buildBBCSuccessResp(
                    CallBBCResponse.newBuilder()
                            .setHasPTCVerifyAnchorResp(
                                    HasPTCVerifyAnchorResponse.newBuilder()
                                            .setResult(bbcService.hasPTCVerifyAnchor(
                                                    ObjectIdentity.decode(request.getPtcOwnerOid().toByteArray()),
                                                    new BigInteger(request.getVerifyAnchorVersion())
                                            ))
                            )
            );
        } catch (Exception e) {
            log.error(
                    "BBCCall(handleHasPtcVerifyAnchor) fail [product: {}, domain: {}, errorCode: {}, errorMsg: {}]",
                    product, domain,
                    ServerErrorCodeEnum.BBC_HAS_PTC_VERIFY_ANCHOR_ERROR.getErrorCode(),
                    ServerErrorCodeEnum.BBC_HAS_PTC_VERIFY_ANCHOR_ERROR.getShortMsg(),
                    e
            );
            return ResponseBuilder.buildFailResp(ServerErrorCodeEnum.BBC_HAS_PTC_VERIFY_ANCHOR_ERROR, e.toString());
        }
    }

    private Response handleSetupPtcContract(IBBCService bbcService, SetupPTCContractRequest request, String product, String domain) {
        if (!checkBBCVersionSatisfiedV1(bbcService)) {
            return ResponseBuilder.buildFailResp(ServerErrorCodeEnum.BBC_VERSION_NOT_SUPPORTED,
                    StrUtil.format("bbc version not supported, please check plugin code for product {}", product));
        }
        try {
            bbcService.setupPTCContract();
            PTCContract ptcContract = bbcService.getContext().getPtcContract();
            return ResponseBuilder.buildBBCSuccessResp(CallBBCResponse.newBuilder()
                    .setSetupPtcContractResp(SetupPtcContractResponse.newBuilder()
                            .setPtcContract(
                                    PtcContract.newBuilder()
                                            .setContractAddress(ptcContract.getContractAddress())
                                            .setStatusValue(ptcContract.getStatus().ordinal())
                            )
                    )
            );
        } catch (Exception e) {
            log.error(
                    "BBCCall(handleSetupPtcContract) fail [product: {}, domain: {}, errorCode: {}, errorMsg: {}]",
                    product, domain,
                    ServerErrorCodeEnum.BBC_SETUP_PTC_CONTRACT_ERROR.getErrorCode(),
                    ServerErrorCodeEnum.BBC_SETUP_PTC_CONTRACT_ERROR.getShortMsg(),
                    e
            );
            return ResponseBuilder.buildFailResp(ServerErrorCodeEnum.BBC_SETUP_PTC_CONTRACT_ERROR, e.toString());
        }
    }

    private Response handleUpdatePtcTrustRoot(IBBCService bbcService, UpdatePTCTrustRootRequest request, String product, String domain) {
        if (!checkBBCVersionSatisfiedV1(bbcService)) {
            return ResponseBuilder.buildFailResp(ServerErrorCodeEnum.BBC_VERSION_NOT_SUPPORTED,
                    StrUtil.format("bbc version not supported, please check plugin code for product {}", product));
        }
        try {
            bbcService.updatePTCTrustRoot(PTCTrustRoot.decode(request.getPtcTrustRoot().toByteArray()));
            return ResponseBuilder.buildBBCSuccessResp(CallBBCResponse.newBuilder());
        } catch (Exception e) {
            log.error(
                    "BBCCall(handleUpdatePtcTrustRoot) fail [product: {}, domain: {}, errorCode: {}, errorMsg: {}]",
                    product, domain,
                    ServerErrorCodeEnum.BBC_UPDATE_PTC_TRUST_ROOT_ERROR.getErrorCode(),
                    ServerErrorCodeEnum.BBC_UPDATE_PTC_TRUST_ROOT_ERROR.getShortMsg(),
                    e
            );
            return ResponseBuilder.buildFailResp(ServerErrorCodeEnum.BBC_UPDATE_PTC_TRUST_ROOT_ERROR, e.toString());
        }
    }

    private Response handleAddTpBta(IBBCService bbcService, AddTpBtaRequest request, String product, String domain) {
        if (!checkBBCVersionSatisfiedV1(bbcService)) {
            return ResponseBuilder.buildFailResp(ServerErrorCodeEnum.BBC_VERSION_NOT_SUPPORTED,
                    StrUtil.format("bbc version not supported, please check plugin code for product {}", product));
        }
        try {
            bbcService.addTpBta(ThirdPartyBlockchainTrustAnchor.decode(request.getTpBta().toByteArray()));
            return ResponseBuilder.buildBBCSuccessResp(CallBBCResponse.newBuilder());
        } catch (Exception e) {
            log.error(
                    "BBCCall(handleAddTpBta) fail [product: {}, domain: {}, errorCode: {}, errorMsg: {}]",
                    product, domain,
                    ServerErrorCodeEnum.BBC_ADD_TPBTA_ERROR.getErrorCode(),
                    ServerErrorCodeEnum.BBC_ADD_TPBTA_ERROR.getShortMsg(),
                    e
            );
            return ResponseBuilder.buildFailResp(ServerErrorCodeEnum.BBC_ADD_TPBTA_ERROR, e.toString());
        }
    }

    private boolean checkBBCVersionSatisfiedV1(IBBCService bbcService) {
        return Utils.getBBCVersion(bbcService).ordinal() >= BBCVersionEnum.V1.ordinal();
    }

    private Response handleQueryValidatedBlockStateReq(IBBCService bbcService, QueryValidatedBlockStateRequest request, String product, String domain) {
        if (!checkBBCVersionSatisfiedV1(bbcService)) {
            return ResponseBuilder.buildFailResp(ServerErrorCodeEnum.BBC_VERSION_NOT_SUPPORTED,
                    StrUtil.format("bbc version not supported, please check plugin code for product {}", product));
        }
        try {
            return ResponseBuilder.buildBBCSuccessResp(
                    CallBBCResponse.newBuilder()
                            .setQueryValidatedBlockStateResponse(
                                    QueryValidatedBlockStateResponse.newBuilder()
                                            .setBlockStateData(ByteString.copyFrom(
                                                    bbcService.queryValidatedBlockStateByDomain(
                                                            new CrossChainDomain(request.getReceiverDomain())
                                                    ).encode()
                                            ))
                            )
            );

        } catch (Exception e) {
            log.error(
                    "BBCCall(handleQueryValidatedBlockStateReq) fail [product: {}, domain: {}, errorCode: {}, errorMsg: {}]",
                    product, domain,
                    ServerErrorCodeEnum.BBC_QUERY_VALIDATED_BLOCK_STATE_ERROR.getErrorCode(),
                    ServerErrorCodeEnum.BBC_QUERY_VALIDATED_BLOCK_STATE_ERROR.getShortMsg(),
                    e
            );
            return ResponseBuilder.buildFailResp(ServerErrorCodeEnum.BBC_QUERY_VALIDATED_BLOCK_STATE_ERROR, e.toString());
        }
    }

    private Response handleRecvOffChainExceptionReq(IBBCService bbcService, RecvOffChainExceptionRequest request, String product, String domain) {
        if (!checkBBCVersionSatisfiedV1(bbcService)) {
            return ResponseBuilder.buildFailResp(ServerErrorCodeEnum.BBC_VERSION_NOT_SUPPORTED,
                    StrUtil.format("bbc version not supported, please check plugin code for product {}", product));
        }
        try {
            CrossChainMessageReceipt receipt = bbcService.recvOffChainException(request.getExceptionMsgAuthor(),
                    request.getExceptionMsgPkg().toByteArray());
            return ResponseBuilder.buildBBCSuccessResp(
                    CallBBCResponse.newBuilder()
                            .setRecvOffChainExceptionResponse(
                                    RecvOffChainExceptionResponse.newBuilder()
                                            .setReceipt(
                                                    com.alipay.antchain.bridge.pluginserver.service.CrossChainMessageReceipt.newBuilder()
                                                            .setConfirmed(receipt.isConfirmed())
                                                            .setSuccessful(receipt.isSuccessful())
                                                            .setTxhash(StrUtil.nullToDefault(receipt.getTxhash(), ""))
                                                            .setErrorMsg(StrUtil.nullToDefault(receipt.getErrorMsg(), ""))
                                                            .setTxTimestamp(receipt.getTxTimestamp())
                                                            .setRawTx(ByteString.copyFrom(ObjectUtil.defaultIfNull(receipt.getRawTx(), new byte[]{})))
                                            ).build()
                            )
            );
        } catch (Exception e) {
            log.error(
                    "BBCCall(handleRecvOffChainExceptionReq) fail [product: {}, domain: {}, errorCode: {}, errorMsg: {}]",
                    product, domain,
                    ServerErrorCodeEnum.BBC_RECV_OFF_CHAIN_EXCEPTION_ERROR.getErrorCode(),
                    ServerErrorCodeEnum.BBC_RECV_OFF_CHAIN_EXCEPTION_ERROR.getShortMsg(),
                    e
            );
            return ResponseBuilder.buildFailResp(ServerErrorCodeEnum.BBC_RECV_OFF_CHAIN_EXCEPTION_ERROR, e.toString());
        }
    }

    private Response handleReliableRetryRequest(IBBCService bbcService, ReliableRetryRequest request, String product, String domain) {
        if (!checkBBCVersionSatisfiedV1(bbcService)) {
            return ResponseBuilder.buildFailResp(ServerErrorCodeEnum.BBC_VERSION_NOT_SUPPORTED,
                    StrUtil.format("bbc version not supported, please check plugin code for product {}", product));
        }
        if (!bbcService.getContext().isReliable()) {
            return ResponseBuilder.buildFailResp(ServerErrorCodeEnum.BBC_RELIABLE_NOT_SUPPORTED,
                    StrUtil.format("bbc not support reliable cross chain, please check plugin code for product {}", product));
        }

        try {
            CrossChainMessageReceipt receipt = bbcService.reliableRetry(
                    ReliableCrossChainMessage.decode(request.getReliableCrossChainMessageData().toByteArray()));
            return ResponseBuilder.buildBBCSuccessResp(
                    CallBBCResponse.newBuilder()
                            .setReliableRetryResponse(
                                    ReliableRetryResponse.newBuilder()
                                            .setReceipt(
                                                    com.alipay.antchain.bridge.pluginserver.service.CrossChainMessageReceipt.newBuilder()
                                                            .setConfirmed(receipt.isConfirmed())
                                                            .setSuccessful(receipt.isSuccessful())
                                                            .setTxhash(StrUtil.nullToDefault(receipt.getTxhash(), ""))
                                                            .setErrorMsg(StrUtil.nullToDefault(receipt.getErrorMsg(), ""))
                                                            .setTxTimestamp(receipt.getTxTimestamp())
                                                            .setRawTx(ByteString.copyFrom(ObjectUtil.defaultIfNull(receipt.getRawTx(), new byte[]{})))
                                            ).build()
                            )
            );
        } catch (Exception e) {
            log.error(
                    "BBCCall(handleReliableRetryRequest) fail [product: {}, domain: {}, errorCode: {}, errorMsg: {}]",
                    product, domain,
                    ServerErrorCodeEnum.BBC_RELIABLE_RETRY_ERROR.getErrorCode(),
                    ServerErrorCodeEnum.BBC_RELIABLE_RETRY_ERROR.getShortMsg(),
                    e
            );
            return ResponseBuilder.buildFailResp(ServerErrorCodeEnum.BBC_RELIABLE_RETRY_ERROR, e.toString());
        }
    }
}

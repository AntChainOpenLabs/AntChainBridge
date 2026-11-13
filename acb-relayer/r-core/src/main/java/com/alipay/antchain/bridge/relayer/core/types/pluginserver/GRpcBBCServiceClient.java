package com.alipay.antchain.bridge.relayer.core.types.pluginserver;

import java.math.BigInteger;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import com.alibaba.fastjson.JSON;
import com.alipay.antchain.bridge.commons.bbc.AbstractBBCContext;
import com.alipay.antchain.bridge.commons.bbc.DefaultBBCContext;
import com.alipay.antchain.bridge.commons.core.base.CrossChainMessage;
import com.alipay.antchain.bridge.commons.core.base.CrossChainMessageReceipt;
import com.alipay.antchain.bridge.commons.core.base.*;
import com.alipay.antchain.bridge.commons.core.ptc.PTCTrustRoot;
import com.alipay.antchain.bridge.commons.core.ptc.PTCTypeEnum;
import com.alipay.antchain.bridge.commons.core.ptc.PTCVerifyAnchor;
import com.alipay.antchain.bridge.commons.core.ptc.ThirdPartyBlockchainTrustAnchor;
import com.alipay.antchain.bridge.commons.core.rcc.ReliableCrossChainMessage;
import com.alipay.antchain.bridge.pluginserver.service.*;
import com.alipay.antchain.bridge.relayer.commons.exception.AntChainBridgeRelayerException;
import com.alipay.antchain.bridge.relayer.core.types.exception.BbcInterfaceNotSupportException;
import com.alipay.antchain.bridge.relayer.core.utils.PluginServerUtils;
import com.google.protobuf.ByteString;

public class GRpcBBCServiceClient implements IBBCServiceClient {

    static final long RECEIPT_QUERY_DEADLINE_SECONDS = 30L;

    private String psId;

    private final String product;

    private final String domain;

    private CrossChainServiceGrpc.CrossChainServiceBlockingStub blockingStub;

    private AbstractBBCContext bbcContext;

    public GRpcBBCServiceClient(String psId, String product, String domain, CrossChainServiceGrpc.CrossChainServiceBlockingStub blockingStub) {
        this.psId = psId;
        this.product = product;
        this.domain = domain;
        this.blockingStub = blockingStub;
    }

    @Override
    public String getProduct() {
        return this.product;
    }

    @Override
    public String getDomain() {
        return this.domain;
    }

    @Override
    public void startup(AbstractBBCContext abstractBBCContext) {
        Response response = this.blockingStub.bbcCall(
                CallBBCRequest.newBuilder()
                        .setProduct(this.getProduct())
                        .setDomain(this.getDomain())
                        .setStartUpReq(
                                StartUpRequest.newBuilder()
                                        .setRawContext(ByteString.copyFrom(JSON.toJSONBytes(abstractBBCContext)))
                        ).build()
        );
        if (response.getCode() != 0) {
            throw new RuntimeException(String.format("[GRpcBBCServiceClient (domain: %s, product: %s)] startup request failed for plugin server %s: %s",
                    this.domain, this.product, this.psId, response.getErrorMsg()));
        }

        this.bbcContext = abstractBBCContext;
    }

    @Override
    public void shutdown() {
        Response response = this.blockingStub.bbcCall(
                CallBBCRequest.newBuilder()
                        .setProduct(this.getProduct())
                        .setDomain(this.getDomain())
                        .setShutdownReq(ShutdownRequest.getDefaultInstance())
                        .build()
        );
        if (response.getCode() != 0) {
            throw new RuntimeException(String.format("[GRpcBBCServiceClient (domain: %s, product: %s)] shutdown request failed for plugin server %s: %s",
                    this.domain, this.product, this.psId, response.getErrorMsg()));
        }
    }

    @Override
    public AbstractBBCContext getContext() {
        Response response = this.blockingStub.bbcCall(
                CallBBCRequest.newBuilder()
                        .setProduct(this.getProduct())
                        .setDomain(this.getDomain())
                        .setGetContextReq(GetContextRequest.getDefaultInstance())
                        .build()
        );
        if (response.getCode() != 0) {
            throw new RuntimeException(String.format("[GRpcBBCServiceClient (domain: %s, product: %s)] getContext request failed for plugin server %s: %s",
                    this.domain, this.product, this.psId, response.getErrorMsg()));
        }
        AbstractBBCContext bbcContext = new DefaultBBCContext();
        bbcContext.decodeFromBytes(response.getBbcResp().getGetContextResp().getRawContext().toByteArray());
        this.bbcContext = bbcContext;

        return bbcContext;
    }

    @Override
    public CrossChainMessageReceipt readCrossChainMessageReceipt(String txhash) {
        Response response = withReceiptQueryDeadline(this.blockingStub).bbcCall(
                CallBBCRequest.newBuilder()
                        .setProduct(this.getProduct())
                        .setDomain(this.getDomain())
                        .setReadCrossChainMessageReceiptReq(
                                ReadCrossChainMessageReceiptRequest.newBuilder().setTxhash(txhash)
                        ).build()
        );
        if (response.getCode() != 0) {
            throw new RuntimeException(
                    String.format("[GRpcBBCServiceClient (domain: %s, product: %s)] isCrossChainMessageConfirmed request failed for plugin server %s: %s",
                            this.domain, this.product, this.psId, response.getErrorMsg())
            );
        }
        return PluginServerUtils.convertFromGRpcCrossChainMessageReceipt(
                response.getBbcResp().getReadCrossChainMessageReceiptResp().getReceipt()
        );
    }

    static CrossChainServiceGrpc.CrossChainServiceBlockingStub withReceiptQueryDeadline(
            CrossChainServiceGrpc.CrossChainServiceBlockingStub stub
    ) {
        return stub.withDeadlineAfter(RECEIPT_QUERY_DEADLINE_SECONDS, TimeUnit.SECONDS);
    }

    @Override
    public List<CrossChainMessage> readCrossChainMessagesByHeight(long height) {
        Response response = this.blockingStub.bbcCall(
                CallBBCRequest.newBuilder()
                        .setProduct(this.getProduct())
                        .setDomain(this.getDomain())
                        .setReadCrossChainMessagesByHeightReq(
                                ReadCrossChainMessagesByHeightRequest.newBuilder()
                                        .setHeight(height)
                        ).build()
        );
        if (response.getCode() != 0) {
            try {
                handleErrorCode(response);
            } catch (Exception e) {
                throw new RuntimeException(
                        String.format("[GRpcBBCServiceClient (domain: %s, product: %s)] readCrossChainMessagesByHeight request failed :",
                                this.domain, this.product), e
                );
            }
        }
        return response.getBbcResp().getReadCrossChainMessagesByHeightResp().getMessageListList().stream()
                .map(PluginServerUtils::convertFromGRpcCrossChainMessage)
                .collect(Collectors.toList());
    }

    @Override
    public long querySDPMessageSeq(String senderDomain, String fromAddress, String receiverDomain, String toAddress) {
        Response response = this.blockingStub.bbcCall(
                CallBBCRequest.newBuilder()
                        .setProduct(this.getProduct())
                        .setDomain(this.getDomain())
                        .setQuerySDPMessageSeqReq(
                                QuerySDPMessageSeqRequest.newBuilder()
                                        .setSenderDomain(senderDomain)
                                        .setFromAddress(fromAddress)
                                        .setReceiverDomain(receiverDomain)
                                        .setToAddress(toAddress)
                        ).build()
        );
        if (response.getCode() != 0) {
            throw new RuntimeException(
                    String.format("[GRpcBBCServiceClient (domain: %s, product: %s)] querySDPMessageSeq request failed for plugin server %s: %s",
                            this.domain, this.product, this.psId, response.getErrorMsg())
            );
        }
        return response.getBbcResp().getQuerySDPMsgSeqResp().getSequence();
    }

    @Override
    public void setupAuthMessageContract() {
        Response response = this.blockingStub.bbcCall(
                CallBBCRequest.newBuilder()
                        .setProduct(this.getProduct())
                        .setDomain(this.getDomain())
                        .setSetupAuthMessageContractReq(SetupAuthMessageContractRequest.getDefaultInstance())
                        .build()
        );
        if (response.getCode() != 0) {
            throw new RuntimeException(
                    String.format("[GRpcBBCServiceClient (domain: %s, product: %s)] setupAuthMessageContract request failed for plugin server %s: %s",
                            this.domain, this.product, this.psId, response.getErrorMsg())
            );
        }
    }

    @Override
    public void setupSDPMessageContract() {
        Response response = this.blockingStub.bbcCall(
                CallBBCRequest.newBuilder()
                        .setProduct(this.getProduct())
                        .setDomain(this.getDomain())
                        .setSetupSDPMessageContractReq(SetupSDPMessageContractRequest.getDefaultInstance())
                        .build()
        );
        if (response.getCode() != 0) {
            throw new RuntimeException(
                    String.format("[GRpcBBCServiceClient (domain: %s, product: %s)] setupSDPMessageContract request failed for plugin server %s: %s",
                            this.domain, this.product, this.psId, response.getErrorMsg())
            );
        }
    }

    @Override
    public void setupMonitorContract() {
        Response response = this.blockingStub.bbcCall(
                CallBBCRequest.newBuilder()
                        .setProduct(this.getProduct())
                        .setDomain(this.getDomain())
                        .setSetupMonitorMessageContractReq(SetupMonitorMessageContractRequest.getDefaultInstance())
                        .build()
        );
        if (response.getCode() != 0) {
            try {
                handleErrorCode(response);
            } catch (AntChainBridgeRelayerException e) {
                throw e;
            } catch (Exception e) {
                throw new RuntimeException(
                        String.format("[GRpcBBCServiceClient (domain: %s, product: %s)] setupMonitorContract request failed for plugin server %s: %s",
                                this.domain, this.product, this.psId, response.getErrorMsg())
                );
            }
        }
    }

    @Override
    public void setMonitorContract(String monitorContract) {
        Response response = this.blockingStub.bbcCall(
                CallBBCRequest.newBuilder()
                        .setProduct(this.getProduct())
                        .setDomain(this.getDomain())
                        .setSetMonitorContractReq(SetMonitorContractRequest.newBuilder().setContractAddress(monitorContract))
                        .build()
        );
        if (response.getCode() != 0) {
            throw new RuntimeException(
                    String.format("[GRpcBBCServiceClient (domain: %s, product: %s)] setMonitorContract request failed for plugin server %s: %s",
                            this.domain, this.product, this.psId, response.getErrorMsg())
            );
        }
    }

    @Override
    public void setProtocolInMonitor(String protocolContract) {
        Response response = this.blockingStub.bbcCall(
                CallBBCRequest.newBuilder()
                        .setProduct(this.getProduct())
                        .setDomain(this.getDomain())
                        .setSetProtocolInMonitorReq(SetProtocolInMonitorRequest.newBuilder().setProtocolAddress(protocolContract))
                        .build()
        );
        if (response.getCode() != 0) {
            throw new RuntimeException(
                    String.format("[GRpcBBCServiceClient (domain: %s, product: %s)] setProtocolInMonitor request failed for plugin server %s: %s",
                            this.domain, this.product, this.psId, response.getErrorMsg())
            );
        }
    }

    @Override
    public void setMonitorControl(int monitorType) {
        Response response = this.blockingStub.bbcCall(
                CallBBCRequest.newBuilder()
                        .setProduct(this.getProduct())
                        .setDomain(this.getDomain())
                        .setSetMonitorControlReq(SetMonitorControlRequest.newBuilder().setMonitorType(monitorType))
                        .build()
        );
        if (response.getCode() != 0) {
            throw new RuntimeException(
                    String.format("[GRpcBBCServiceClient (domain: %s, product: %s)] setMonitorControl request failed for plugin server %s: %s",
                            this.domain, this.product, this.psId, response.getErrorMsg())
            );
        }
    }

    @Override
    public void setPtcHubInMonitorVerifier(String contractAddress) {
        Response response = this.blockingStub.bbcCall(
                CallBBCRequest.newBuilder()
                        .setProduct(this.getProduct())
                        .setDomain(this.getDomain())
                        .setSetPtcHubInMonitorVerifierReq(SetPtcHubInMonitorVerifierRequest.newBuilder().setContractAddress(contractAddress))
                        .build()
        );
        if (response.getCode() != 0) {
            throw new RuntimeException(
                    String.format("[GRpcBBCServiceClient (domain: %s, product: %s)] setPtcHubInMonitorVerifier request failed for plugin server %s: %s",
                            this.domain, this.product, this.psId, response.getErrorMsg())
            );
        }
    }

    @Override
    public void setProtocol(String protocolAddress, String protocolType) {
        Response response = this.blockingStub.bbcCall(
                CallBBCRequest.newBuilder()
                        .setProduct(this.getProduct())
                        .setDomain(this.getDomain())
                        .setSetProtocolReq(
                                SetProtocolRequest.newBuilder()
                                        .setProtocolType(protocolType)
                                        .setProtocolAddress(protocolAddress)
                        ).build()
        );
        if (response.getCode() != 0) {
            throw new RuntimeException(
                    String.format("[GRpcBBCServiceClient (domain: %s, product: %s)] setProtocol request failed for plugin server %s: %s",
                            this.domain, this.product, this.psId, response.getErrorMsg())
            );
        }
    }

    @Override
    public void setPtcContract(String ptcContractAddress) {
        Response response = this.blockingStub.bbcCall(
                CallBBCRequest.newBuilder()
                        .setProduct(this.getProduct())
                        .setDomain(this.getDomain())
                        .setSetPtcContractReq(
                                SetPtcContractRequest.newBuilder()
                                        .setPtcContractAddress(ptcContractAddress)
                        ).build()
        );
        if (response.getCode() != 0) {
            throw new RuntimeException(
                    String.format("[GRpcBBCServiceClient (domain: %s, product: %s)] setPtcContract request failed for plugin server %s: %s",
                            this.domain, this.product, this.psId, response.getErrorMsg())
            );
        }
    }

    @Override
    public CrossChainMessageReceipt relayAuthMessage(byte[] rawMessage) {
        return relayAuthMessage(rawMessage, "");
    }

    @Override
    public CrossChainMessageReceipt relayAuthMessage(byte[] rawMessage, String submissionId) {
        Response response = this.blockingStub.withDeadlineAfter(30, TimeUnit.SECONDS).bbcCall(
                CallBBCRequest.newBuilder()
                        .setProduct(this.getProduct())
                        .setDomain(this.getDomain())
                        .setRelayAuthMessageReq(
                                RelayAuthMessageRequest.newBuilder()
                                        .setRawMessage(ByteString.copyFrom(rawMessage))
                                        .setSubmissionId(submissionId == null ? "" : submissionId)
                        ).build()
        );
        if (response.getCode() != 0) {
            throw new RuntimeException(
                    String.format("[GRpcBBCServiceClient (domain: %s, product: %s)] relayAuthMessage request failed for plugin server %s: %s",
                            this.domain, this.product, this.psId, response.getErrorMsg())
            );
        }

        return PluginServerUtils.convertFromGRpcCrossChainMessageReceipt(response.getBbcResp().getRelayAuthMessageResponse().getReceipt());
    }

    @Override
    public CrossChainMessageReceipt relayMonitorOrder(String committeeId, String signAlgo, byte[] rawProof, byte[] rawMonitorOrder) {
        return new CrossChainMessageReceipt();
    }

    @Override
    public void setAmContract(String contractAddress) {
        Response response = this.blockingStub.bbcCall(
                CallBBCRequest.newBuilder()
                        .setProduct(this.getProduct())
                        .setDomain(this.getDomain())
                        .setSetAmContractReq(SetAmContractRequest.newBuilder().setContractAddress(contractAddress))
                        .build()
        );
        if (response.getCode() != 0) {
            throw new RuntimeException(
                    String.format("[GRpcBBCServiceClient (domain: %s, product: %s)] setAmContract request failed for plugin server %s: %s",
                            this.domain, this.product, this.psId, response.getErrorMsg())
            );
        }
    }

    @Override
    public void setLocalDomain(String domain) {
        Response response = this.blockingStub.bbcCall(
                CallBBCRequest.newBuilder()
                        .setProduct(this.getProduct())
                        .setDomain(this.getDomain())
                        .setSetLocalDomainReq(SetLocalDomainRequest.newBuilder().setDomain(domain))
                        .build()
        );
        if (response.getCode() != 0) {
            throw new RuntimeException(
                    String.format("[GRpcBBCServiceClient (domain: %s, product: %s)] setLocalDomain request failed for plugin server %s: %s",
                            this.domain, this.product, this.psId, response.getErrorMsg())
            );
        }
    }

    @Override
    public Long queryLatestHeight() {
        Response response = this.blockingStub.bbcCall(
                CallBBCRequest.newBuilder()
                        .setProduct(this.getProduct())
                        .setDomain(this.getDomain())
                        .setQueryLatestHeightReq(
                                QueryLatestHeightRequest.getDefaultInstance()
                        ).build()
        );
        if (response.getCode() != 0) {
            try {
                handleErrorCode(response);
            } catch (Exception e) {
                throw new RuntimeException(
                        String.format("[GRpcBBCServiceClient (domain: %s, product: %s)] queryLatestHeight request failed :",
                                this.domain, this.product), e
                );
            }
        }
        return response.getBbcResp().getQueryLatestHeightResponse().getHeight();
    }

    @Override
    public ConsensusState readConsensusState(BigInteger height) {
        Response response = this.blockingStub.bbcCall(
                CallBBCRequest.newBuilder()
                        .setProduct(this.getProduct())
                        .setDomain(this.getDomain())
                        .setReadConsensusStateReq(
                                ReadConsensusStateRequest.newBuilder()
                                        .setHeight(height.toString())
                        ).build()
        );
        if (response.getCode() != 0) {
            try {
                handleErrorCode(response);
            } catch (Exception e) {
                throw new RuntimeException(
                        String.format("[GRpcBBCServiceClient (domain: %s, product: %s)] readConsensusState request failed :",
                                this.domain, this.product), e
                );
            }
        }
        return ConsensusState.decode(
                response.getBbcResp().getReadConsensusStateResponse().getConsensusState().toByteArray()
        );
    }

    @Override
    public boolean hasTpBta(CrossChainLane tpbtaLane, int tpBtaVersion) {
        Response response = this.blockingStub.bbcCall(
                CallBBCRequest.newBuilder()
                        .setProduct(this.getProduct())
                        .setDomain(this.getDomain())
                        .setHasTpBtaReq(
                                HasTpBtaRequest.newBuilder()
                                        .setTpbtaLane(tpbtaLane.getLaneKey())
                                        .setTpBtaVersion(tpBtaVersion)
                        ).build()
        );
        if (response.getCode() != 0) {
            try {
                handleErrorCode(response);
            } catch (Exception e) {
                throw new RuntimeException(
                        String.format("[GRpcBBCServiceClient (domain: %s, product: %s)] hasTpBta request failed :",
                                this.domain, this.product), e
                );
            }
        }
        return response.getBbcResp().getHasTpBtaResp().getResult();
    }

    @Override
    public ThirdPartyBlockchainTrustAnchor getTpBta(CrossChainLane tpbtaLane, int tpBtaVersion) {
        Response response = this.blockingStub.bbcCall(
                CallBBCRequest.newBuilder()
                        .setProduct(this.getProduct())
                        .setDomain(this.getDomain())
                        .setGetTpBtaReq(
                                GetTpBtaRequest.newBuilder()
                                        .setTpbtaLane(tpbtaLane.getLaneKey())
                                        .setTpBtaVersion(tpBtaVersion)
                        ).build()
        );
        if (response.getCode() != 0) {
            try {
                handleErrorCode(response);
            } catch (Exception e) {
                throw new RuntimeException(
                        String.format("[GRpcBBCServiceClient (domain: %s, product: %s)] getTpBta request failed :",
                                this.domain, this.product), e
                );
            }
        }
        return ThirdPartyBlockchainTrustAnchor.decode(response.getBbcResp().getGetTpBtaResp().getTpBta().toByteArray());
    }

    @Override
    public Set<PTCTypeEnum> getSupportedPTCType() {
        Response response = this.blockingStub.bbcCall(
                CallBBCRequest.newBuilder()
                        .setProduct(this.getProduct())
                        .setDomain(this.getDomain())
                        .setGetSupportedPTCTypeReq(
                                GetSupportedPTCTypeRequest.getDefaultInstance()
                        ).build()
        );
        if (response.getCode() != 0) {
            try {
                handleErrorCode(response);
            } catch (Exception e) {
                throw new RuntimeException(
                        String.format("[GRpcBBCServiceClient (domain: %s, product: %s)] getSupportedPTCType request failed :",
                                this.domain, this.product), e
                );
            }
        }
        return response.getBbcResp().getGetSupportedPTCTypeResp().getPtcTypesList().stream()
                .map(PTCTypeEnum::parseFrom)
                .collect(Collectors.toSet());
    }

    @Override
    public PTCTrustRoot getPTCTrustRoot(ObjectIdentity ptcOwnerOid) {
        Response response = this.blockingStub.bbcCall(
                CallBBCRequest.newBuilder()
                        .setProduct(this.getProduct())
                        .setDomain(this.getDomain())
                        .setGetPTCTrustRootReq(
                                GetPTCTrustRootRequest.newBuilder()
                                        .setPtcOwnerOid(ByteString.copyFrom(ptcOwnerOid.encode()))
                        ).build()
        );
        if (response.getCode() != 0) {
            try {
                handleErrorCode(response);
            } catch (Exception e) {
                throw new RuntimeException(
                        String.format("[GRpcBBCServiceClient (domain: %s, product: %s)] getPTCTrustRoot request failed :",
                                this.domain, this.product), e
                );
            }
        }
        return PTCTrustRoot.decode(response.getBbcResp().getGetPTCTrustRootResp().getPtcTrustRoot().toByteArray());
    }

    @Override
    public boolean hasPTCTrustRoot(ObjectIdentity ptcOwnerOid) {
        Response response = this.blockingStub.bbcCall(
                CallBBCRequest.newBuilder()
                        .setProduct(this.getProduct())
                        .setDomain(this.getDomain())
                        .setHasPTCTrustRootReq(
                                HasPTCTrustRootRequest.newBuilder()
                                        .setPtcOwnerOid(ByteString.copyFrom(ptcOwnerOid.encode()))
                        ).build()
        );
        if (response.getCode() != 0) {
            try {
                handleErrorCode(response);
            } catch (Exception e) {
                throw new RuntimeException(
                        String.format("[GRpcBBCServiceClient (domain: %s, product: %s)] hasPTCTrustRoot request failed :",
                                this.domain, this.product), e
                );
            }
        }
        return response.getBbcResp().getHasPTCTrustRootResp().getResult();
    }

    @Override
    public PTCVerifyAnchor getPTCVerifyAnchor(ObjectIdentity ptcOwnerOid, BigInteger version) {
        Response response = this.blockingStub.bbcCall(
                CallBBCRequest.newBuilder()
                        .setProduct(this.getProduct())
                        .setDomain(this.getDomain())
                        .setGetPTCVerifyAnchorReq(
                                GetPTCVerifyAnchorRequest.newBuilder()
                                        .setPtcOwnerOid(ByteString.copyFrom(ptcOwnerOid.encode()))
                                        .setVerifyAnchorVersion(version.toString())
                        ).build()
        );
        if (response.getCode() != 0) {
            try {
                handleErrorCode(response);
            } catch (Exception e) {
                throw new RuntimeException(
                        String.format("[GRpcBBCServiceClient (domain: %s, product: %s)] getPTCVerifyAnchor request failed :",
                                this.domain, this.product), e
                );
            }
        }
        return PTCVerifyAnchor.decode(
                response.getBbcResp().getGetPTCVerifyAnchorResp().getPtcVerifyAnchor().toByteArray()
        );
    }

    @Override
    public boolean hasPTCVerifyAnchor(ObjectIdentity ptcOwnerOid, BigInteger version) {
        Response response = this.blockingStub.bbcCall(
                CallBBCRequest.newBuilder()
                        .setProduct(this.getProduct())
                        .setDomain(this.getDomain())
                        .setHasPTCVerifyAnchorReq(
                                HasPTCVerifyAnchorRequest.newBuilder()
                                        .setPtcOwnerOid(ByteString.copyFrom(ptcOwnerOid.encode()))
                                        .setVerifyAnchorVersion(version.toString())
                        ).build()
        );
        if (response.getCode() != 0) {
            try {
                handleErrorCode(response);
            } catch (Exception e) {
                throw new RuntimeException(
                        String.format("[GRpcBBCServiceClient (domain: %s, product: %s)] hasPTCVerifyAnchor request failed :",
                                this.domain, this.product), e
                );
            }
        }
        return response.getBbcResp().getHasPTCVerifyAnchorResp().getResult();
    }

    @Override
    public void setupPTCContract() {
        Response response = this.blockingStub.bbcCall(
                CallBBCRequest.newBuilder()
                        .setProduct(this.getProduct())
                        .setDomain(this.getDomain())
                        .setSetupPTCContractReq(SetupPTCContractRequest.getDefaultInstance())
                        .build()
        );
        if (response.getCode() != 0) {
            try {
                handleErrorCode(response);
            } catch (AntChainBridgeRelayerException e) {
                throw e;
            } catch (Exception e) {
                throw new RuntimeException(
                        String.format("[GRpcBBCServiceClient (domain: %s, product: %s)] setupPTCContract request failed :",
                                this.domain, this.product), e
                );
            }
        }
    }


    @Override
    public void updatePTCTrustRoot(PTCTrustRoot ptcTrustRoot) {
        Response response = this.blockingStub.bbcCall(
                CallBBCRequest.newBuilder()
                        .setProduct(this.getProduct())
                        .setDomain(this.getDomain())
                        .setUpdatePTCTrustRootReq(
                                UpdatePTCTrustRootRequest.newBuilder()
                                        .setPtcTrustRoot(ByteString.copyFrom(ptcTrustRoot.encode()))
                        ).build()
        );
        if (response.getCode() != 0) {
            try {
                handleErrorCode(response);
            } catch (Exception e) {
                throw new RuntimeException(
                        String.format("[GRpcBBCServiceClient (domain: %s, product: %s)] updatePTCTrustRoot request failed :",
                                this.domain, this.product), e
                );
            }
        }
    }

    @Override
    public void addTpBta(ThirdPartyBlockchainTrustAnchor tpbta) {
        Response response = this.blockingStub.bbcCall(
                CallBBCRequest.newBuilder()
                        .setProduct(this.getProduct())
                        .setDomain(this.getDomain())
                        .setAddTpBtaReq(
                                AddTpBtaRequest.newBuilder()
                                        .setTpBta(ByteString.copyFrom(tpbta.encode()))
                        ).build()
        );
        if (response.getCode() != 0) {
            try {
                handleErrorCode(response);
            } catch (Exception e) {
                throw new RuntimeException(
                        String.format("[GRpcBBCServiceClient (domain: %s, product: %s)] addTpBta request failed :",
                                this.domain, this.product), e
                );
            }
        }
    }

    private void handleErrorCode(Response response) {
        if (response.getCode() == 217) {
            response = this.blockingStub.bbcCall(
                    CallBBCRequest.newBuilder()
                            .setProduct(this.getProduct())
                            .setDomain(this.getDomain())
                            .setStartUpReq(
                                    StartUpRequest.newBuilder()
                                            .setRawContext(ByteString.copyFrom(JSON.toJSONBytes(this.bbcContext)))
                            ).build()
            );
            if (response.getCode() != 0) {
                throw new RuntimeException(String.format("restart request failed for plugin server %s: %s",
                        this.psId, response.getErrorMsg()));
            }
            return;
        }
        if (response.getCode() == 219 || response.getCode() == 256) {
            throw new BbcInterfaceNotSupportException();
        }
        throw new RuntimeException(
                String.format("error code %d for plugin server %s: %s", response.getCode(), this.psId, response.getErrorMsg())
        );
    }

    @Override
    public BlockState queryValidatedBlockStateByDomain(CrossChainDomain recvDomain) {
        Response response = this.blockingStub.bbcCall(
                CallBBCRequest.newBuilder()
                        .setProduct(this.getProduct())
                        .setDomain(this.getDomain())
                        .setQueryValidatedBlockStateRequest(
                                QueryValidatedBlockStateRequest.newBuilder()
                                        .setReceiverDomain(recvDomain.getDomain())
                                        .build()
                        ).build()
        );
        if (response.getCode() != 0) {
            throw new RuntimeException(
                    String.format("[GRpcBBCServiceClient (domain: %s, product: %s)] queryValidatedBlockStateRequest request failed for plugin server %s: %s",
                            this.domain, this.product, this.psId, response.getErrorMsg())
            );
        }

        return BlockState.decode(response.getBbcResp().getQueryValidatedBlockStateResponse().getBlockStateData().toByteArray());
    }

    @Override
    public CrossChainMessageReceipt recvOffChainException(String exceptionMsgAuthor, byte[] exceptionMsgPkg) {
        Response response = this.blockingStub.bbcCall(
                CallBBCRequest.newBuilder()
                        .setProduct(this.getProduct())
                        .setDomain(this.getDomain())
                        .setRecvOffChainExceptionRequest(
                                RecvOffChainExceptionRequest.newBuilder()
                                        .setExceptionMsgAuthor(exceptionMsgAuthor)
                                        .setExceptionMsgPkg(ByteString.copyFrom(exceptionMsgPkg))
                        ).build()
        );
        if (response.getCode() != 0) {
            throw new RuntimeException(
                    String.format("[GRpcBBCServiceClient (domain: %s, product: %s)] recvOffChainException request failed for plugin server %s: %s",
                            this.domain, this.product, this.psId, response.getErrorMsg())
            );
        }

        return PluginServerUtils.convertFromGRpcCrossChainMessageReceipt(response.getBbcResp().getRecvOffChainExceptionResponse().getReceipt());
    }

    @Override
    public CrossChainMessageReceipt reliableRetry(ReliableCrossChainMessage reliableCrossChainMessage) {
        Response response = this.blockingStub.bbcCall(
                CallBBCRequest.newBuilder()
                        .setProduct(this.getProduct())
                        .setDomain(this.getDomain())
                        .setReliableRetryRequest(
                                ReliableRetryRequest.newBuilder()
                                        .setReliableCrossChainMessageData(ByteString.copyFrom(reliableCrossChainMessage.encode()))
                        ).build()
        );
        if (response.getCode() != 0) {
            throw new RuntimeException(
                    String.format("[GRpcBBCServiceClient (domain: %s, product: %s)] reliableRetry request failed for plugin server %s: %s",
                            this.domain, this.product, this.psId, response.getErrorMsg())
            );
        }

        return PluginServerUtils.convertFromGRpcCrossChainMessageReceipt(response.getBbcResp().getReliableRetryResponse().getReceipt());
    }
}

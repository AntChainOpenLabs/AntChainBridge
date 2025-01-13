package com.alipay.antchain.bridge.plugins.mychain;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.collection.ListUtil;
import cn.hutool.core.map.MapUtil;
import cn.hutool.core.util.HexUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.ReflectUtil;
import cn.hutool.crypto.PemUtil;
import com.alibaba.fastjson.JSON;
import com.alipay.antchain.bridge.commons.bbc.AbstractBBCContext;
import com.alipay.antchain.bridge.commons.bbc.DefaultBBCContext;
import com.alipay.antchain.bridge.commons.bbc.syscontract.ContractStatusEnum;
import com.alipay.antchain.bridge.commons.bcdns.AbstractCrossChainCertificate;
import com.alipay.antchain.bridge.commons.bcdns.CrossChainCertificateFactory;
import com.alipay.antchain.bridge.commons.bcdns.PTCCredentialSubject;
import com.alipay.antchain.bridge.commons.bcdns.utils.CrossChainCertificateUtil;
import com.alipay.antchain.bridge.commons.core.am.AuthMessageFactory;
import com.alipay.antchain.bridge.commons.core.am.IAuthMessage;
import com.alipay.antchain.bridge.commons.core.base.*;
import com.alipay.antchain.bridge.commons.core.ptc.*;
import com.alipay.antchain.bridge.commons.core.sdp.ISDPMessage;
import com.alipay.antchain.bridge.commons.core.sdp.SDPMessageFactory;
import com.alipay.antchain.bridge.commons.utils.crypto.HashAlgoEnum;
import com.alipay.antchain.bridge.commons.utils.crypto.SignAlgoEnum;
import com.alipay.antchain.bridge.plugins.mychain.common.SDPMsgTypeEnum;
import com.alipay.antchain.bridge.plugins.mychain.model.ConsensusNodeInfo;
import com.alipay.antchain.bridge.plugins.mychain.model.ContractAddressInfo;
import com.alipay.antchain.bridge.plugins.mychain.model.CrossChainMsgLedgerData;
import com.alipay.antchain.bridge.plugins.mychain.sdk.Mychain010Client;
import com.alipay.antchain.bridge.plugins.mychain.sdk.Mychain010Config;
import com.alipay.antchain.bridge.plugins.mychain.sdp.ReceiverEvm;
import com.alipay.antchain.bridge.plugins.mychain.sdp.ReceiverWasm;
import com.alipay.antchain.bridge.plugins.mychain.sdp.SenderEvm;
import com.alipay.antchain.bridge.plugins.mychain.sdp.SenderWasm;
import com.alipay.antchain.bridge.plugins.mychain.utils.ContractUtils;
import com.alipay.antchain.bridge.ptc.committee.types.basic.CommitteeEndorseProof;
import com.alipay.antchain.bridge.ptc.committee.types.basic.CommitteeNodeProof;
import com.alipay.antchain.bridge.ptc.committee.types.basic.NodePublicKeyEntry;
import com.alipay.antchain.bridge.ptc.committee.types.network.CommitteeNetworkInfo;
import com.alipay.antchain.bridge.ptc.committee.types.tpbta.CommitteeEndorseRoot;
import com.alipay.antchain.bridge.ptc.committee.types.tpbta.NodeEndorseInfo;
import com.alipay.antchain.bridge.ptc.committee.types.tpbta.OptionalEndorsePolicy;
import com.alipay.antchain.bridge.ptc.committee.types.trustroot.CommitteeVerifyAnchor;
import com.alipay.mychain.sdk.api.utils.Utils;
import com.alipay.mychain.sdk.crypto.hash.HashTypeEnum;
import com.alipay.mychain.sdk.domain.account.Identity;
import com.alipay.mychain.sdk.domain.block.BlockHeader;
import com.alipay.mychain.sdk.domain.spv.BlockHeaderInfo;
import com.alipay.mychain.sdk.errorcode.ErrorCode;
import com.alipay.mychain.sdk.message.query.QueryTransactionReceiptResponse;
import com.alipay.mychain.sdk.vm.EVMOutput;
import com.alipay.mychain.sdk.vm.EVMParameter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.math.BigInteger;
import java.security.PrivateKey;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
public class Mychain010BBCServiceTest {

    public static final String PTC_CERT = "-----BEGIN PROOF TRANSFORMATION COMPONENT CERTIFICATE-----\n" +
            "AAD4AQAAAAABAAAAMQEADAAAAGFudGNoYWluLXB0YwIAAQAAAAIDAGsAAAAAAGUA\n" +
            "AAAAAAEAAAAAAQBYAAAAMFYwEAYHKoZIzj0CAQYFK4EEAAoDQgAEULzSDPM4mhRk\n" +
            "GpCiVirHZTWfzuZ/PfO6+/RfVgGpZxs4EUQZhw5SnYWIwK99KxDPNxtYG7OEiWDA\n" +
            "7+EKZUWBIwQACAAAAJTZ/2YAAAAABQAIAAAAFA3haAAAAAAGAKAAAAAAAJoAAAAA\n" +
            "AAMAAAAxLjABAA0AAABjb21taXR0ZWUtcHRjAgABAAAAAQMAawAAAAAAZQAAAAAA\n" +
            "AQAAAAABAFgAAAAwVjAQBgcqhkjOPQIBBgUrgQQACgNCAARQvNIM8ziaFGQakKJW\n" +
            "KsdlNZ/O5n8987r79F9WAalnGzgRRBmHDlKdhYjAr30rEM83G1gbs4SJYMDv4Qpl\n" +
            "RYEjBAAAAAAABwCfAAAAAACZAAAAAAAKAAAAS0VDQ0FLLTI1NgEAIAAAAM87/iLc\n" +
            "e6uD6qD6prxj4z75IoGzydOhd68+3Y8dODHxAgAWAAAAS2VjY2FrMjU2V2l0aFNl\n" +
            "Y3AyNTZrMQMAQQAAAMK+DN7gXmDRv8nfXwWZe3XCZQQu5mO86LNZxXcp7BgMPfJj\n" +
            "y1wKW5yD51nhMEW2K1AfwEG6n8RWk5Z2jFDE8GMA\n" +
            "-----END PROOF TRANSFORMATION COMPONENT CERTIFICATE-----\n";

    public static final AbstractCrossChainCertificate NODE_PTC_CERT = CrossChainCertificateUtil.readCrossChainCertificateFromPem(PTC_CERT.getBytes());

    public static final byte[] RAW_NODE_PTC_PUBLIC_KEY = PemUtil.readPem(new ByteArrayInputStream(
                    ("-----BEGIN PUBLIC KEY-----\n" +
                            "MFYwEAYHKoZIzj0CAQYFK4EEAAoDQgAEULzSDPM4mhRkGpCiVirHZTWfzuZ/PfO6\n" +
                            "+/RfVgGpZxs4EUQZhw5SnYWIwK99KxDPNxtYG7OEiWDA7+EKZUWBIw==\n" +
                            "-----END PUBLIC KEY-----\n").getBytes()
            )
    );

    public static final PrivateKey NODE_PTC_PRIVATE_KEY = SignAlgoEnum.KECCAK256_WITH_SECP256K1.getSigner().readPemPrivateKey(
            ("-----BEGIN EC PRIVATE KEY-----\n" +
                    "MHQCAQEEINtcJsfWygsBn4u8sscy/04yPSpafFwCW4yVg1Vrb8looAcGBSuBBAAK\n" +
                    "oUQDQgAEULzSDPM4mhRkGpCiVirHZTWfzuZ/PfO6+/RfVgGpZxs4EUQZhw5SnYWI\n" +
                    "wK99KxDPNxtYG7OEiWDA7+EKZUWBIw==\n" +
                    "-----END EC PRIVATE KEY-----\n").getBytes()
    );

    private static Mychain010BBCService mychain010BBCService;

    private Mychain010Client mychain010Client;

    private static final ThirdPartyBlockchainTrustAnchorV1 tpbta;

    private static final CrossChainLane crossChainLane;

    private static final ObjectIdentity oid;

    private static final String COMMITTEE_ID = "committee";

    private static final String CHAIN_DOMAIN = "test.domain";

    private static final PTCTrustRoot ptcTrustRoot;

    static {
        oid = new X509PubkeyInfoObjectIdentity(RAW_NODE_PTC_PUBLIC_KEY);

        OptionalEndorsePolicy policy = new OptionalEndorsePolicy();
        policy.setThreshold(new OptionalEndorsePolicy.Threshold(OptionalEndorsePolicy.OperatorEnum.GREATER_OR_EQUALS, 1));

        NodeEndorseInfo nodeEndorseInfo = new NodeEndorseInfo();
        nodeEndorseInfo.setNodeId("node1");
        nodeEndorseInfo.setRequired(true);
        NodePublicKeyEntry nodePubkeyEntry = new NodePublicKeyEntry("default", ((X509PubkeyInfoObjectIdentity) oid).getPublicKey());
        nodeEndorseInfo.setPublicKey(nodePubkeyEntry);

        NodeEndorseInfo nodeEndorseInfo2 = new NodeEndorseInfo();
        nodeEndorseInfo2.setNodeId("node2");
        nodeEndorseInfo2.setRequired(false);
        nodeEndorseInfo2.setPublicKey(nodePubkeyEntry);

        NodeEndorseInfo nodeEndorseInfo3 = new NodeEndorseInfo();
        nodeEndorseInfo3.setNodeId("node3");
        nodeEndorseInfo3.setRequired(false);
        nodeEndorseInfo3.setPublicKey(nodePubkeyEntry);

        NodeEndorseInfo nodeEndorseInfo4 = new NodeEndorseInfo();
        nodeEndorseInfo4.setNodeId("node4");
        nodeEndorseInfo4.setRequired(false);
        nodeEndorseInfo4.setPublicKey(nodePubkeyEntry);

        crossChainLane = new CrossChainLane(new CrossChainDomain("test"), new CrossChainDomain(CHAIN_DOMAIN));
        tpbta = new ThirdPartyBlockchainTrustAnchorV1(
                1,
                BigInteger.ONE,
                (PTCCredentialSubject) NODE_PTC_CERT.getCredentialSubjectInstance(),
                crossChainLane,
                1,
                HashAlgoEnum.KECCAK_256,
                new CommitteeEndorseRoot(
                        COMMITTEE_ID,
                        policy,
                        ListUtil.toList(nodeEndorseInfo, nodeEndorseInfo2, nodeEndorseInfo3, nodeEndorseInfo4)
                ).encode(),
                null
        );
        tpbta.setEndorseProof(
                CommitteeEndorseProof.builder()
                        .committeeId(COMMITTEE_ID)
                        .sigs(ListUtil.toList(
                                new CommitteeNodeProof(
                                        "node1",
                                        SignAlgoEnum.KECCAK256_WITH_SECP256K1,
                                        SignAlgoEnum.KECCAK256_WITH_SECP256K1.getSigner()
                                                .sign(NODE_PTC_PRIVATE_KEY, tpbta.getEncodedToSign())
                                ),
                                new CommitteeNodeProof(
                                        "node2",
                                        SignAlgoEnum.KECCAK256_WITH_SECP256K1,
                                        SignAlgoEnum.KECCAK256_WITH_SECP256K1.getSigner()
                                                .sign(NODE_PTC_PRIVATE_KEY, tpbta.getEncodedToSign())
                                ),
                                new CommitteeNodeProof(
                                        "node3",
                                        SignAlgoEnum.KECCAK256_WITH_SECP256K1,
                                        SignAlgoEnum.KECCAK256_WITH_SECP256K1.getSigner()
                                                .sign(NODE_PTC_PRIVATE_KEY, tpbta.getEncodedToSign())
                                )
                        )).build().encode()
        );
        Assert.assertEquals(ThirdPartyBlockchainTrustAnchor.TypeEnum.CHANNEL_LEVEL, tpbta.type());

        CommitteeVerifyAnchor verifyAnchor = new CommitteeVerifyAnchor("committee");
        verifyAnchor.addNode("node1", "default", ((X509PubkeyInfoObjectIdentity) oid).getPublicKey());
        verifyAnchor.addNode("node2", "default", ((X509PubkeyInfoObjectIdentity) oid).getPublicKey());
        verifyAnchor.addNode("node3", "default", ((X509PubkeyInfoObjectIdentity) oid).getPublicKey());
        verifyAnchor.addNode("node4", "default", ((X509PubkeyInfoObjectIdentity) oid).getPublicKey());

        // prepare the network stuff
        CommitteeNetworkInfo committeeNetworkInfo = new CommitteeNetworkInfo("committee");
        committeeNetworkInfo.addEndpoint("node1", "grpcs://0.0.0.0:8080", "");
        committeeNetworkInfo.addEndpoint("node2", "grpcs://0.0.0.0:8080", "");
        committeeNetworkInfo.addEndpoint("node3", "grpcs://0.0.0.0:8080", "");
        committeeNetworkInfo.addEndpoint("node4", "grpcs://0.0.0.0:8080", "");

        // build it first
        ptcTrustRoot = PTCTrustRoot.builder()
                .ptcCrossChainCert(NODE_PTC_CERT)
                .networkInfo(committeeNetworkInfo.encode())
                .issuerBcdnsDomainSpace(new CrossChainDomain(""))
                .sigAlgo(SignAlgoEnum.KECCAK256_WITH_SECP256K1)
                .verifyAnchorMap(MapUtil.builder(
                        BigInteger.ONE,
                        new PTCVerifyAnchor(
                                BigInteger.ONE,
                                verifyAnchor.encode()
                        )
                ).build())
                .build();

        // sign it with ptc private key which applied PTC certificate
        ptcTrustRoot.sign(NODE_PTC_PRIVATE_KEY);
    }

    @Before
    public void init() throws Exception {
        mychain010BBCService = new Mychain010BBCService();

        Method setLoggerMethod = ReflectUtil.getMethod(Mychain010BBCService.class, "setLogger", Logger.class);
        setLoggerMethod.setAccessible(true);
        setLoggerMethod.invoke(mychain010BBCService, log);

        AbstractBBCContext mockCtx = mychainNoContractCtx();
        mychain010BBCService.startup(mockCtx);
        Field mychain010ClientField = ReflectUtil.getField(Mychain010BBCService.class, "mychain010Client");
        mychain010ClientField.setAccessible(true);
        mychain010Client = (Mychain010Client) mychain010ClientField.get(mychain010BBCService);
    }

    @After
    public void clear() {
        mychain010BBCService.shutdown();
    }

    @Test
    public void setupAuthMessageContractTest() {
        AbstractBBCContext contextBefore = mychain010BBCService.getContext();
        Assert.assertNull(contextBefore.getAuthMessageContract());

        mychain010BBCService.setupAuthMessageContract();

        AbstractBBCContext contextAfter = mychain010BBCService.getContext();
        Assert.assertNotNull(contextAfter.getAuthMessageContract());
    }

    @Test
    public void setupSDPMessageContractTest() {
        AbstractBBCContext contextBefore = mychain010BBCService.getContext();
        Assert.assertNull(contextBefore.getSdpContract());

        mychain010BBCService.setupSDPMessageContract();

        AbstractBBCContext contextAfter = mychain010BBCService.getContext();
        Assert.assertNotNull(contextAfter.getSdpContract());
    }

    @Test
    public void setupPtcContractTest() {
        AbstractBBCContext contextBefore = mychain010BBCService.getContext();
        Assert.assertNull(contextBefore.getPtcContract());

        mychain010BBCService.setupPTCContract();

        AbstractBBCContext contextAfter = mychain010BBCService.getContext();
        Assert.assertNotNull(contextAfter.getPtcContract());

        Assert.assertArrayEquals(
                CrossChainCertificateUtil.readCrossChainCertificateFromPem(mychain010Client.getConfig().getBcdnsRootCertPem().getBytes()).encode(),
                getBcdnsCertFromPtcContract().encode()
        );
        Assert.assertNotEquals("0000000000000000000000000000000000000000000000000000000000000000",
                ((Mychain010BBCContext) contextAfter).getPtcContractEvm().getCommitteeVerifier());
    }

    @Test
    public void setProtocolAndPtcHubTest() {
        AbstractBBCContext context = mychain010BBCService.getContext();
        Assert.assertNull(context.getAuthMessageContract());
        Assert.assertNull(context.getSdpContract());

        mychain010BBCService.setupAuthMessageContract();
        mychain010BBCService.setupSDPMessageContract();
        mychain010BBCService.setupPTCContract();

        context = mychain010BBCService.getContext();
        Assert.assertEquals(ContractStatusEnum.CONTRACT_DEPLOYED, context.getAuthMessageContract().getStatus());
        Assert.assertEquals(ContractStatusEnum.CONTRACT_DEPLOYED, context.getSdpContract().getStatus());
        Assert.assertEquals(ContractStatusEnum.CONTRACT_READY, context.getPtcContract().getStatus());

        // 第一个参数不重要，根据上下文中的合约地址进行 set 操作
        mychain010BBCService.setProtocol("", "0");

        mychain010BBCService.setPtcContract("");

        context = mychain010BBCService.getContext();
        Assert.assertEquals(ContractStatusEnum.CONTRACT_READY, context.getAuthMessageContract().getStatus());
        Assert.assertEquals(ContractStatusEnum.CONTRACT_DEPLOYED, context.getSdpContract().getStatus());
    }

    @Test
    public void setAmContractAndDomainTest() {
        AbstractBBCContext context1 = mychain010BBCService.getContext();
        Assert.assertNull(context1.getAuthMessageContract());
        Assert.assertNull(context1.getSdpContract());

        mychain010BBCService.setupAuthMessageContract();
        mychain010BBCService.setupSDPMessageContract();

        AbstractBBCContext context2 = mychain010BBCService.getContext();
        Assert.assertEquals(ContractStatusEnum.CONTRACT_DEPLOYED, context2.getAuthMessageContract().getStatus());
        Assert.assertEquals(ContractStatusEnum.CONTRACT_DEPLOYED, context2.getSdpContract().getStatus());

        // 参数不重要，根据上下文中的合约地址进行 set 操作
        mychain010BBCService.setAmContract("");

        AbstractBBCContext context3 = mychain010BBCService.getContext();
        Assert.assertEquals(ContractStatusEnum.CONTRACT_DEPLOYED, context3.getAuthMessageContract().getStatus());
        Assert.assertEquals(ContractStatusEnum.CONTRACT_DEPLOYED, context3.getSdpContract().getStatus());

        String testDomain = CHAIN_DOMAIN;
        mychain010BBCService.setLocalDomain(testDomain);

        AbstractBBCContext context4 = mychain010BBCService.getContext();
        Assert.assertEquals(ContractStatusEnum.CONTRACT_DEPLOYED, context4.getAuthMessageContract().getStatus());
        Assert.assertEquals(ContractStatusEnum.CONTRACT_READY, context4.getSdpContract().getStatus());
    }

    @Test
    public void readCrossChainMessagesByHeightTest() {
        AbstractBBCContext context = mychain010BBCService.getContext();
        Assert.assertNull(context.getAuthMessageContract());

        mychain010BBCService.setupAuthMessageContract();
        mychain010BBCService.setupSDPMessageContract();

        mychain010BBCService.setAmContract("");
        mychain010BBCService.setLocalDomain(CHAIN_DOMAIN);
        mychain010BBCService.setProtocol("", "0");

        context = mychain010BBCService.getContext();
        sendUnorderedMsgToEvmSender((Mychain010BBCContext) context);
        sendUnorderedMsgToWasmSender((Mychain010BBCContext) context);
    }

    private CrossChainMessage sendUnorderedMsgToEvmSender(Mychain010BBCContext context) {
        SenderEvm senderEvm = new SenderEvm(CHAIN_DOMAIN, mychain010Client, context.getSdpContractClientEVM().getContractAddress());

        Identity receiverId = new Identity(RandomUtil.randomBytes(32));
        SendResponseResult sendResponseResult = senderEvm.sendMsgV1To("receiver.domain", receiverId, "test", SDPMsgTypeEnum.UNORDERED);
        Assert.assertTrue(sendResponseResult.isSuccess());
        Assert.assertEquals(String.valueOf(ErrorCode.SUCCESS.getErrorCode()), sendResponseResult.getErrorCode());

        String txHash = sendResponseResult.getTxId();
        QueryTransactionReceiptResponse queryTransactionReceiptResponse = mychain010Client.getTxReceiptByTxhash(txHash);

        List<CrossChainMessage> messageList = mychain010BBCService.readCrossChainMessagesByHeight(queryTransactionReceiptResponse.getBlockNumber().longValue());
        Assert.assertEquals(1, messageList.size());

        CrossChainMsgLedgerData crossChainMsgLedgerData = CrossChainMsgLedgerData.decode(messageList.get(0).getProvableData().getLedgerData());
        Assert.assertArrayEquals(
                messageList.get(0).getMessage(),
                crossChainMsgLedgerData.getCrossChainMessage()
        );
        Assert.assertEquals(
                receiverId.hexStrValue(),
                SDPMessageFactory.createSDPMessage(AuthMessageFactory.createAuthMessage(messageList.get(0).getMessage()).getPayload()).getTargetIdentity().toHex()
        );
        Assert.assertEquals(0, crossChainMsgLedgerData.getReceiptIndex());
        Assert.assertEquals(
                Utils.getIdentityByName(context.getAmContractClientEVM().getContractAddress(), mychain010Client.getConfig().getMychainHashType()).hexStrValue(),
                crossChainMsgLedgerData.getAmContractIdHex()
        );
        Assert.assertArrayEquals(queryTransactionReceiptResponse.getTransactionReceipt().toRlp(), crossChainMsgLedgerData.getReceipt().toRlp());

        return messageList.get(0);
    }

    private CrossChainMessage sendUnorderedMsgToWasmSender(Mychain010BBCContext context) {
        SenderWasm senderWasm = new SenderWasm(CHAIN_DOMAIN, mychain010Client, context.getSdpContractClientWASM().getContractAddress());

        Identity receiverId = new Identity(RandomUtil.randomBytes(32));
        SendResponseResult sendResponseResult = senderWasm.sendMsgV1To("receiver.domain", receiverId, "test", SDPMsgTypeEnum.UNORDERED);
        Assert.assertTrue(sendResponseResult.isSuccess());
        Assert.assertEquals(String.valueOf(ErrorCode.SUCCESS.getErrorCode()), sendResponseResult.getErrorCode());

        String txHash = sendResponseResult.getTxId();
        QueryTransactionReceiptResponse queryTransactionReceiptResponse = mychain010Client.getTxReceiptByTxhash(txHash);

        List<CrossChainMessage> messageList = mychain010BBCService.readCrossChainMessagesByHeight(queryTransactionReceiptResponse.getBlockNumber().longValue());
        Assert.assertEquals(1, messageList.size());

        CrossChainMsgLedgerData crossChainMsgLedgerData = CrossChainMsgLedgerData.decode(messageList.get(0).getProvableData().getLedgerData());
        Assert.assertArrayEquals(
                messageList.get(0).getMessage(),
                crossChainMsgLedgerData.getCrossChainMessage()
        );
        Assert.assertEquals(
                receiverId.hexStrValue(),
                SDPMessageFactory.createSDPMessage(AuthMessageFactory.createAuthMessage(messageList.get(0).getMessage()).getPayload()).getTargetIdentity().toHex()
        );
        Assert.assertEquals(0, crossChainMsgLedgerData.getReceiptIndex());
        Assert.assertEquals(
                Utils.getIdentityByName(context.getAmContractClientWASM().getContractAddress(), mychain010Client.getConfig().getMychainHashType()).hexStrValue(),
                crossChainMsgLedgerData.getAmContractIdHex()
        );
        Assert.assertArrayEquals(queryTransactionReceiptResponse.getTransactionReceipt().toRlp(), crossChainMsgLedgerData.getReceipt().toRlp());

        return messageList.get(0);
    }

    @Test
    public void testReadConsensusState() {
        mychain010BBCService.setupAuthMessageContract();

        ConsensusState consensusState = mychain010BBCService.readConsensusState(BigInteger.valueOf(mychain010BBCService.queryLatestHeight()));

        BlockHeaderInfo blockHeaderInfo = mychain010Client.getRawBlockHeaderInfoByHeight(consensusState.getHeight());
        BlockHeader header = new BlockHeader();
        header.fromJson(JSON.parseObject(new String(consensusState.getStateData())));
        Assert.assertEquals(
                blockHeaderInfo.getBlockHeader().toString(),
                new String(consensusState.getStateData())
        );
        Assert.assertEquals(
                blockHeaderInfo.getProof().toString(),
                new String(consensusState.getEndorsements())
        );

        ConsensusNodeInfo consensusNodeInfo = ConsensusNodeInfo.decode(consensusState.getConsensusNodeInfo());
        Assert.assertEquals(HashTypeEnum.SHA256, consensusNodeInfo.getMychainHashType());
        ContractAddressInfo amAddressInfo = ContractAddressInfo.decode(mychain010BBCService.getContext().getAuthMessageContract().getContractAddress());
        Assert.assertTrue(
                CollectionUtil.isEqualList(
                        consensusNodeInfo.getAmContractIds().stream().sorted().collect(Collectors.toList()),
                        ListUtil.toList(
                                amAddressInfo.getEvmContractAddress(), amAddressInfo.getWasmContractAddress()
                        ).stream().sorted().collect(Collectors.toList())
                )
        );
    }

    @Test
    @SneakyThrows
    public void testRelayAuthMsg() {
        mychain010BBCService.setupAuthMessageContract();
        mychain010BBCService.setupSDPMessageContract();
        mychain010BBCService.setupPTCContract();

        mychain010BBCService.setAmContract("");
        mychain010BBCService.setLocalDomain(CHAIN_DOMAIN);
        mychain010BBCService.setProtocol("", "0");
        mychain010BBCService.setPtcContract("");

        mychain010BBCService.updatePTCTrustRoot(ptcTrustRoot);
        mychain010BBCService.addTpBta(tpbta);

        Assert.assertNotNull(mychain010BBCService.getPTCTrustRoot(oid));
        Assert.assertTrue(mychain010BBCService.hasPTCTrustRoot(oid));
        Assert.assertNotNull(mychain010BBCService.getPTCVerifyAnchor(oid, BigInteger.ONE));
        Assert.assertTrue(mychain010BBCService.hasPTCVerifyAnchor(oid, BigInteger.ONE));

        Assert.assertNotNull(mychain010BBCService.getTpBta(tpbta.getCrossChainLane(), tpbta.getTpbtaVersion()));
        Assert.assertTrue(mychain010BBCService.hasTpBta(tpbta.getCrossChainLane(), tpbta.getTpbtaVersion()));

        CrossChainMessageReceipt crossChainMessageReceipt = relayToEvmReceiver((Mychain010BBCContext) mychain010BBCService.getContext());
        checkCrossChainReceiptResult(crossChainMessageReceipt);

        relayToWasmReceiver((Mychain010BBCContext) mychain010BBCService.getContext());
        checkCrossChainReceiptResult(crossChainMessageReceipt);
    }

    private void checkCrossChainReceiptResult(CrossChainMessageReceipt crossChainMessageReceipt) throws InterruptedException {
        Assert.assertTrue(crossChainMessageReceipt.isSuccessful());
        int tryCount = 0;
        while (tryCount++ < 10) {
            crossChainMessageReceipt = mychain010BBCService.readCrossChainMessageReceipt(crossChainMessageReceipt.getTxhash());
            if (!crossChainMessageReceipt.isConfirmed()) {
                Thread.sleep(1000);
            } else {
                break;
            }
        }
        Assert.assertTrue(crossChainMessageReceipt.isSuccessful() && crossChainMessageReceipt.isConfirmed());
    }

    private CrossChainMessageReceipt relayToEvmReceiver(Mychain010BBCContext context) {
        ReceiverEvm receiverEvm = new ReceiverEvm(CHAIN_DOMAIN, mychain010Client, context.getSdpContractClientEVM().getContractAddress());
        return mychain010BBCService.relayAuthMessage(makeRelayMsg(receiverEvm.getContractId()));
    }

    private CrossChainMessageReceipt relayToWasmReceiver(Mychain010BBCContext context) {
        ReceiverWasm receiverWasm = new ReceiverWasm(CHAIN_DOMAIN, mychain010Client, context.getSdpContractClientEVM().getContractAddress());
        return mychain010BBCService.relayAuthMessage(makeRelayMsg(receiverWasm.getContractId()));
    }

    @SneakyThrows
    private byte[] makeRelayMsg(Identity receiverId) {
        ISDPMessage sdpMessage = SDPMessageFactory.createSDPMessage(
                1,
                new byte[32],
                crossChainLane.getReceiverDomain().getDomain(),
                receiverId.getData(),
                -1,
                "awesome antchain-bridge".getBytes()
        );
        IAuthMessage am = AuthMessageFactory.createAuthMessage(
                1,
                RandomUtil.randomBytes(32),
                0,
                sdpMessage.encode()
        );

        ThirdPartyProof thirdPartyProof = ThirdPartyProof.create(
                tpbta.getTpbtaVersion(),
                am.encode(),
                tpbta.getCrossChainLane()
        );

        CommitteeNodeProof node1Proof = CommitteeNodeProof.builder()
                .nodeId("node1")
                .signAlgo(SignAlgoEnum.KECCAK256_WITH_SECP256K1)
                .signature(SignAlgoEnum.KECCAK256_WITH_SECP256K1.getSigner().sign(
                        NODE_PTC_PRIVATE_KEY,
                        thirdPartyProof.getEncodedToSign()
                )).build();
        CommitteeNodeProof node2Proof = CommitteeNodeProof.builder()
                .nodeId("node2")
                .signAlgo(SignAlgoEnum.KECCAK256_WITH_SECP256K1)
                .signature(SignAlgoEnum.KECCAK256_WITH_SECP256K1.getSigner().sign(
                        NODE_PTC_PRIVATE_KEY,
                        thirdPartyProof.getEncodedToSign()
                )).build();

        CommitteeEndorseProof endorseProof = new CommitteeEndorseProof();
        endorseProof.setCommitteeId(COMMITTEE_ID);
        endorseProof.setSigs(ListUtil.toList(node1Proof, node2Proof));

        thirdPartyProof.setRawProof(endorseProof.encode());

        byte[] rawProof = thirdPartyProof.encode();

        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        stream.write(new byte[]{0, 0, 0, 0});

        int len = rawProof.length;
        stream.write((len >>> 24) & 0xFF);
        stream.write((len >>> 16) & 0xFF);
        stream.write((len >>> 8) & 0xFF);
        stream.write((len) & 0xFF);

        stream.write(rawProof);

        return stream.toByteArray();
    }

    @Test
    public void queryLatestHeightTest() {
        Assert.assertNotEquals(0L, (long) mychain010BBCService.queryLatestHeight());
    }

    private AbstractBBCContext mychainNoContractCtx() throws IOException {
        String jsonStr = Mychain010Config.readFileJson("test.domain.json");
        Mychain010Config mockConf = Mychain010Config.fromJsonString(jsonStr);

        AbstractBBCContext mockCtx = new DefaultBBCContext();
        mockCtx.setConfForBlockchainClient(mockConf.toJsonString().getBytes());
        return mockCtx;
    }

    @Test
    public void extractReceiverVmTypeFromAmPkgTest() throws Exception {
        String proofsHex = "000000000000014200003c01000005001401000000000e0100000000080100000000001c0000000000000000000000000000000000000000000000000000000000000080000000000000000000000000000000000000000000000000000000007be8324c6d797465737431342e32303233303931332e746573742e636861696effffffff10ad6f177909939380fe92d11ac827d59cb47d5d04ca578f6091a53e000000000000000000000000000000000000000000000000000000000000000268696e64206d65737361676520746f000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000a40000000028353428dd9f052694da89f991c90ed69907c138f7ab96bc0b240f711a0f604e0000000109001c0000006d797465737431342e32303233303931332e746573742e636861696e";
        byte[] proofs = HexUtil.decodeHex(proofsHex);

        String receiver = ContractUtils.extractReceiverIdentity(proofs);
        Assert.assertNotNull(receiver);
    }

    private AbstractCrossChainCertificate getBcdnsCertFromPtcContract() {
        EVMParameter parameter = new EVMParameter("bcdnsCertMap(string)");
        parameter.addString("root");
        return CrossChainCertificateFactory.createCrossChainCertificate(
                new EVMOutput(HexUtil.encodeHexStr(
                        mychain010Client.localCallContract(
                                ContractAddressInfo.decode(mychain010BBCService.getContext().getPtcContract().getContractAddress()).getEvmContractAddress(),
                                parameter
                        ).getOutput()
                )).getBytes()
        );
    }

    @Test
    public void queryValidatedBlockStateByDomainTest() {
        AbstractBBCContext context = mychain010BBCService.getContext();
        Assert.assertNull(context.getAuthMessageContract());

        mychain010BBCService.setupAuthMessageContract();
        mychain010BBCService.setupSDPMessageContract();

        mychain010BBCService.setAmContract("");
        mychain010BBCService.setLocalDomain(CHAIN_DOMAIN);
        mychain010BBCService.setProtocol("", "0");

        context = mychain010BBCService.getContext();
//        sendUnorderedMsgToEvmSender((Mychain010BBCContext) context);

        mychain010BBCService.queryValidatedBlockStateByDomain(new CrossChainDomain(""));
    }


}
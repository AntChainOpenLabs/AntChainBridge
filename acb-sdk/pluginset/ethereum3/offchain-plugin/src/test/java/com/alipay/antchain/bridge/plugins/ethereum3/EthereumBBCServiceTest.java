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

package com.alipay.antchain.bridge.plugins.ethereum3;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.math.BigInteger;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import cn.hutool.core.collection.ListUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.map.MapUtil;
import cn.hutool.core.util.ByteUtil;
import cn.hutool.core.util.HexUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.PemUtil;
import cn.hutool.crypto.digest.DigestUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import com.alipay.antchain.bridge.commons.bbc.AbstractBBCContext;
import com.alipay.antchain.bridge.commons.bbc.DefaultBBCContext;
import com.alipay.antchain.bridge.commons.bbc.syscontract.AuthMessageContract;
import com.alipay.antchain.bridge.commons.bbc.syscontract.ContractStatusEnum;
import com.alipay.antchain.bridge.commons.bbc.syscontract.MonitorContract;
import com.alipay.antchain.bridge.commons.bbc.syscontract.SDPContract;
import com.alipay.antchain.bridge.commons.bcdns.AbstractCrossChainCertificate;
import com.alipay.antchain.bridge.commons.bcdns.PTCCredentialSubject;
import com.alipay.antchain.bridge.commons.bcdns.utils.CrossChainCertificateUtil;
import com.alipay.antchain.bridge.commons.core.am.AuthMessageFactory;
import com.alipay.antchain.bridge.commons.core.am.IAuthMessage;
import com.alipay.antchain.bridge.commons.core.base.*;
import com.alipay.antchain.bridge.commons.core.monitor.*;
import com.alipay.antchain.bridge.commons.core.ptc.*;
import com.alipay.antchain.bridge.commons.core.sdp.ISDPMessage;
import com.alipay.antchain.bridge.commons.core.sdp.SDPMessageFactory;
import com.alipay.antchain.bridge.commons.utils.codec.tlv.TLVTypeEnum;
import com.alipay.antchain.bridge.commons.utils.codec.tlv.TLVUtils;
import com.alipay.antchain.bridge.commons.utils.codec.tlv.annotation.TLVField;
import com.alipay.antchain.bridge.commons.utils.crypto.HashAlgoEnum;
import com.alipay.antchain.bridge.commons.utils.crypto.SignAlgoEnum;
import com.alipay.antchain.bridge.plugins.ethereum3.abi.AppContract;
import com.alipay.antchain.bridge.plugins.ethereum3.abi.AuthMsg;
import com.alipay.antchain.bridge.plugins.ethereum3.abi.Monitor;
import com.alipay.antchain.bridge.plugins.ethereum3.abi.SDPMsg;
import com.alipay.antchain.bridge.plugins.ethereum3.conf.Eth2NetworkEnum;
import com.alipay.antchain.bridge.plugins.ethereum3.conf.EthereumConfig;
import com.alipay.antchain.bridge.plugins.ethereum3.core.EthAuthMessageLog;
import com.alipay.antchain.bridge.plugins.ethereum3.core.EthConsensusEndorsements;
import com.alipay.antchain.bridge.plugins.ethereum3.core.EthConsensusStateData;
import com.alipay.antchain.bridge.plugins.ethereum3.core.eth.EthReceiptProof;
import com.alipay.antchain.bridge.plugins.ethereum3.core.eth.eth2spec.Eth2ChainConfig;
import com.alipay.antchain.bridge.plugins.ethereum3.helper.*;
import com.alipay.antchain.bridge.plugins.spi.bbc.AbstractBBCService;
import com.alipay.antchain.bridge.ptc.committee.types.basic.CommitteeEndorseProof;
import com.alipay.antchain.bridge.ptc.committee.types.basic.CommitteeNodeProof;
import com.alipay.antchain.bridge.ptc.committee.types.basic.NodePublicKeyEntry;
import com.alipay.antchain.bridge.ptc.committee.types.network.CommitteeNetworkInfo;
import com.alipay.antchain.bridge.ptc.committee.types.tpbta.CommitteeEndorseRoot;
import com.alipay.antchain.bridge.ptc.committee.types.tpbta.NodeEndorseInfo;
import com.alipay.antchain.bridge.ptc.committee.types.tpbta.OptionalEndorsePolicy;
import com.alipay.antchain.bridge.ptc.committee.types.trustroot.CommitteeVerifyAnchor;
import lombok.Getter;
import lombok.Setter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.methods.response.EthGetTransactionReceipt;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.protocol.http.HttpService;
import org.web3j.tx.RawTransactionManager;
import org.web3j.tx.gas.DefaultGasProvider;
import org.web3j.utils.Numeric;

@Slf4j
public class EthereumBBCServiceTest {

    private static final String VALID_URL = "http://127.0.0.1:32002";

    private static final String VALID_BEACON_URL = "http:/127.0.0.1:33001";

    private static final String INVALID_URL = "http://localhost:6545";

    // it's a key for test, please don't use it in production.
    private static final String APP_USER_ETH_PRIVATE_KEY = "bcdf20249abf0ed6d944c0288fad489e33f66b3960d9e6229c1cd214ed3bbe31";

    // NOTICE: Better use different private keys down here, especially with EthNoncePolicyEnum#FAST
    private static final String BBC_ETH_PRIVATE_KEY = APP_USER_ETH_PRIVATE_KEY;

    private static final String BBC_ETH_PRIVATE_KEY_2 = APP_USER_ETH_PRIVATE_KEY;

    private static final String BBC_ETH_PRIVATE_KEY_3 = APP_USER_ETH_PRIVATE_KEY;

    private static final String REMOTE_APP_CONTRACT = "0xdd11AA371492B94AB8CDEdf076F84ECCa72820e1";

    private static EthereumConfig.CrossChainMessageScanPolicyEnum scanPolicy = EthereumConfig.CrossChainMessageScanPolicyEnum.LOG_FILTER;

    private static final int MAX_TX_RESULT_QUERY_TIME = 100;

    private static EthereumBBCService ethereumBBCService;

    private static AppContract appContract;

    private static boolean setupBBC;

    private static GasPricePolicyEnum gasPricePolicy = GasPricePolicyEnum.FROM_API;

    private static GasLimitPolicyEnum gasLimitPolicy = GasLimitPolicyEnum.ESTIMATE;

    // if you want to deploy a local eth2 env, please reach to `https://github.com/ethpandaops/ethereum-package`
    private static Eth2NetworkEnum eth2NetworkTesting = Eth2NetworkEnum.PRIVATE_NET;

    // if private net, you need to fill the spec.json file under resources with variables from your net.
    // you can get the spec config from beacon rpc: https://ethereum.github.io/beacon-APIs/#/Config/getSpec
    private static final Map<String, String> REMOTE_SPEC_CONFIG = JSON.parseObject(FileUtil.readString("spec.json", StandardCharsets.UTF_8), new TypeReference<>(){});

    // if private net, you need to replace the below two variables with variables from your net
    // which can get from beacon rpc: https://ethereum.github.io/beacon-APIs/#/Beacon/getGenesis
    private static final Long GENESIS_TIME = 1773209047L;
    private static final String GENESIS_VALIDATOR_ROOT = "0xd61ea484febacfae5298d52a2b581f3e305a51f3112a9241b968dccf019f7b11";

    public static final String PTC_CERT = """
            -----BEGIN PROOF TRANSFORMATION COMPONENT CERTIFICATE-----
            AAD4AQAAAAABAAAAMQEADAAAAGFudGNoYWluLXB0YwIAAQAAAAIDAGsAAAAAAGUA
            AAAAAAEAAAAAAQBYAAAAMFYwEAYHKoZIzj0CAQYFK4EEAAoDQgAEULzSDPM4mhRk
            GpCiVirHZTWfzuZ/PfO6+/RfVgGpZxs4EUQZhw5SnYWIwK99KxDPNxtYG7OEiWDA
            7+EKZUWBIwQACAAAAJTZ/2YAAAAABQAIAAAAFA3haAAAAAAGAKAAAAAAAJoAAAAA
            AAMAAAAxLjABAA0AAABjb21taXR0ZWUtcHRjAgABAAAAAQMAawAAAAAAZQAAAAAA
            AQAAAAABAFgAAAAwVjAQBgcqhkjOPQIBBgUrgQQACgNCAARQvNIM8ziaFGQakKJW
            KsdlNZ/O5n8987r79F9WAalnGzgRRBmHDlKdhYjAr30rEM83G1gbs4SJYMDv4Qpl
            RYEjBAAAAAAABwCfAAAAAACZAAAAAAAKAAAAS0VDQ0FLLTI1NgEAIAAAAM87/iLc
            e6uD6qD6prxj4z75IoGzydOhd68+3Y8dODHxAgAWAAAAS2VjY2FrMjU2V2l0aFNl
            Y3AyNTZrMQMAQQAAAMK+DN7gXmDRv8nfXwWZe3XCZQQu5mO86LNZxXcp7BgMPfJj
            y1wKW5yD51nhMEW2K1AfwEG6n8RWk5Z2jFDE8GMA
            -----END PROOF TRANSFORMATION COMPONENT CERTIFICATE-----
            """;

    public static final AbstractCrossChainCertificate NODE_PTC_CERT = CrossChainCertificateUtil.readCrossChainCertificateFromPem(PTC_CERT.getBytes());

    public static final byte[] RAW_NODE_PTC_PUBLIC_KEY = PemUtil.readPem(new ByteArrayInputStream(
                    ("""
                            -----BEGIN PUBLIC KEY-----
                            MFYwEAYHKoZIzj0CAQYFK4EEAAoDQgAEULzSDPM4mhRkGpCiVirHZTWfzuZ/PfO6
                            +/RfVgGpZxs4EUQZhw5SnYWIwK99KxDPNxtYG7OEiWDA7+EKZUWBIw==
                            -----END PUBLIC KEY-----
                            """).getBytes()
            )
    );

    public static final PrivateKey NODE_PTC_PRIVATE_KEY = SignAlgoEnum.KECCAK256_WITH_SECP256K1.getSigner().readPemPrivateKey(
            ("""
                    -----BEGIN EC PRIVATE KEY-----
                    MHQCAQEEINtcJsfWygsBn4u8sscy/04yPSpafFwCW4yVg1Vrb8looAcGBSuBBAAK
                    oUQDQgAEULzSDPM4mhRkGpCiVirHZTWfzuZ/PfO6+/RfVgGpZxs4EUQZhw5SnYWI
                    wK99KxDPNxtYG7OEiWDA7+EKZUWBIw==
                    -----END EC PRIVATE KEY-----
                    """).getBytes()
    );

    private static final ThirdPartyBlockchainTrustAnchorV1 tpbta;

    private static final CrossChainLane crossChainLane;

    private static final ObjectIdentity oid;

    private static final String COMMITTEE_ID = "committee";

    private static final String CHAIN_DOMAIN = "test.domain";

    private static final PTCTrustRoot ptcTrustRoot;

    public static final String BCDNS_CERT = """
            -----BEGIN BCDNS TRUST ROOT CERTIFICATE-----
            AADWAQAAAAABAAAAMQEABAAAAHRlc3QCAAEAAAAAAwBrAAAAAABlAAAAAAABAAAA
            AAEAWAAAADBWMBAGByqGSM49AgEGBSuBBAAKA0IABFC80gzzOJoUZBqQolYqx2U1
            n87mfz3zuvv0X1YBqWcbOBFEGYcOUp2FiMCvfSsQzzcbWBuzhIlgwO/hCmVFgSME
            AAgAAACU2f9mAAAAAAUACAAAABQN4WgAAAAABgCGAAAAAACAAAAAAAADAAAAYmlm
            AQBrAAAAAABlAAAAAAABAAAAAAEAWAAAADBWMBAGByqGSM49AgEGBSuBBAAKA0IA
            BFC80gzzOJoUZBqQolYqx2U1n87mfz3zuvv0X1YBqWcbOBFEGYcOUp2FiMCvfSsQ
            zzcbWBuzhIlgwO/hCmVFgSMCAAAAAAAHAJ8AAAAAAJkAAAAAAAoAAABLRUNDQUst
            MjU2AQAgAAAA1/SncCIPlAQGRJ4Zp2WPBmrk5poje12brhJatwWR5BwCABYAAABL
            ZWNjYWsyNTZXaXRoU2VjcDI1NmsxAwBBAAAAR23ngOzN3b8gaJY9ikvNtdqzwF6K
            zAkr89qnHDJQei9iXVds+7Padq41StiQShIiB9yWtx8/3Qu878R9zmJbZAA=
            -----END BCDNS TRUST ROOT CERTIFICATE-----
            """;

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
        nodeEndorseInfo2.setNodeId("monitor-node");
        nodeEndorseInfo2.setRequired(true);
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
                                        "monitor-node",
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
        verifyAnchor.addNode("monitor-node", "default", ((X509PubkeyInfoObjectIdentity) oid).getPublicKey());
        verifyAnchor.addNode("node3", "default", ((X509PubkeyInfoObjectIdentity) oid).getPublicKey());
        verifyAnchor.addNode("node4", "default", ((X509PubkeyInfoObjectIdentity) oid).getPublicKey());

        // prepare the network stuff
        CommitteeNetworkInfo committeeNetworkInfo = new CommitteeNetworkInfo("committee");
        committeeNetworkInfo.addEndpoint("node1", "grpcs://0.0.0.0:8080", "");
        committeeNetworkInfo.addEndpoint("monitor-node", "grpcs://0.0.0.0:8080", "");
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

    // ===================kms intergration===================
    private static final String endpoint = "";

    private static final String accessKeyId = "";

    private static final String accessKeySecret = "";

    // --------------account 1--------------
    private static final String privateKeyId1 = "";

    @BeforeClass
    public static void init() throws Exception {
        if (StrUtil.equals(APP_USER_ETH_PRIVATE_KEY, "YourPrivateKey")) {
            throw new IllegalArgumentException(
                    "You must set the variable `APP_USER_ETH_PRIVATE_KEY` a valid blockchain account private key. "
            );
        }

        Web3j web3j = Web3j.build(new HttpService(VALID_URL));
        Credentials credentials = Credentials.create(APP_USER_ETH_PRIVATE_KEY);

        RawTransactionManager rawTransactionManager = new RawTransactionManager(
                web3j, credentials, web3j.ethChainId().send().getChainId().longValue());

        appContract = AppContract.deploy(
                web3j,
                rawTransactionManager,
                new AcbGasProvider(
                        new StaticGasPriceProvider(BigInteger.valueOf(15000000000L)),
                        new StaticGasLimitProvider(BigInteger.valueOf(3000000))
                )
        ).send();

        ethereumBBCService = new EthereumBBCService();
        Method method = AbstractBBCService.class.getDeclaredMethod("setLogger", Logger.class);
        method.setAccessible(true);
        method.invoke(ethereumBBCService, log);
    }

    @Test
    public void testStartup() {
        // start up success
        AbstractBBCContext mockValidCtx = mockValidCtx();
        EthereumBBCService ethereumBBCService = new EthereumBBCService();
        ethereumBBCService.startup(mockValidCtx);
        Assert.assertNull(ethereumBBCService.getBbcContext().getAuthMessageContract());
        Assert.assertNull(ethereumBBCService.getBbcContext().getSdpContract());

        // start up failed
        AbstractBBCContext mockInvalidCtx = mockInvalidCtx();
        try {
            ethereumBBCService.startup(mockInvalidCtx);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testStartupWithDeployedContract() {
        // start up a tmp
        AbstractBBCContext mockValidCtx = mockValidCtx();
        EthereumBBCService bbcServiceTmp = new EthereumBBCService();
        bbcServiceTmp.startup(mockValidCtx);

        // set up am and sdp and monitor
        bbcServiceTmp.setupAuthMessageContract();
        bbcServiceTmp.setupSDPMessageContract();
        bbcServiceTmp.setupMonitorContract();
        bbcServiceTmp.setupPTCContract();
        String amAddr = bbcServiceTmp.getContext().getAuthMessageContract().getContractAddress();
        String sdpAddr = bbcServiceTmp.getContext().getSdpContract().getContractAddress();
        String monitorAddr = bbcServiceTmp.getContext().getMonitorContract().getContractAddress();
        String ptcAddr = bbcServiceTmp.getContext().getPtcContract().getContractAddress();

        // start up success
        AbstractBBCContext ctx = mockValidCtxWithPreDeployedContracts(amAddr, sdpAddr, monitorAddr, ptcAddr);
        EthereumBBCService ethereumBBCService = new EthereumBBCService();
        ethereumBBCService.startup(ctx);

        Assert.assertEquals(amAddr, ethereumBBCService.getBbcContext().getAuthMessageContract().getContractAddress());
        Assert.assertEquals(ContractStatusEnum.CONTRACT_DEPLOYED, ethereumBBCService.getBbcContext().getAuthMessageContract().getStatus());
        Assert.assertEquals(sdpAddr, ethereumBBCService.getBbcContext().getSdpContract().getContractAddress());
        Assert.assertEquals(ContractStatusEnum.CONTRACT_DEPLOYED, ethereumBBCService.getBbcContext().getSdpContract().getStatus());
        Assert.assertEquals(monitorAddr, ethereumBBCService.getBbcContext().getMonitorContract().getContractAddress());
        Assert.assertEquals(ContractStatusEnum.CONTRACT_DEPLOYED, ethereumBBCService.getBbcContext().getMonitorContract().getStatus());
        Assert.assertEquals(ptcAddr, ethereumBBCService.getBbcContext().getPtcContract().getContractAddress());
        Assert.assertEquals(ContractStatusEnum.CONTRACT_READY, ethereumBBCService.getBbcContext().getPtcContract().getStatus());
    }

    @Test
    public void testStartupWithReadyContract() {
        // start up a tmp ethereumBBCService to set up contract
        AbstractBBCContext mockValidCtx = mockValidCtx();
        EthereumBBCService bbcServiceTmp = new EthereumBBCService();
        bbcServiceTmp.startup(mockValidCtx);

        // set up am and sdp and monitor
        bbcServiceTmp.setupAuthMessageContract();
        bbcServiceTmp.setupSDPMessageContract();
        bbcServiceTmp.setupMonitorContract();
        bbcServiceTmp.setupPTCContract();
        String amAddr = bbcServiceTmp.getContext().getAuthMessageContract().getContractAddress();
        String sdpAddr = bbcServiceTmp.getContext().getSdpContract().getContractAddress();
        String monitorAddr = bbcServiceTmp.getContext().getMonitorContract().getContractAddress();
        String ptcAddr = bbcServiceTmp.getContext().getPtcContract().getContractAddress();

        // start up success
        EthereumBBCService ethereumBBCService = new EthereumBBCService();
        AbstractBBCContext ctx = mockValidCtxWithPreReadyContracts(amAddr, sdpAddr, monitorAddr, ptcAddr);
        ethereumBBCService.startup(ctx);
        Assert.assertEquals(amAddr, ethereumBBCService.getBbcContext().getAuthMessageContract().getContractAddress());
        Assert.assertEquals(ContractStatusEnum.CONTRACT_READY, ethereumBBCService.getBbcContext().getAuthMessageContract().getStatus());
        Assert.assertEquals(sdpAddr, ethereumBBCService.getBbcContext().getSdpContract().getContractAddress());
        Assert.assertEquals(ContractStatusEnum.CONTRACT_READY, ethereumBBCService.getBbcContext().getSdpContract().getStatus());
        Assert.assertEquals(monitorAddr, ethereumBBCService.getBbcContext().getMonitorContract().getContractAddress());
        Assert.assertEquals(ContractStatusEnum.CONTRACT_READY, ethereumBBCService.getBbcContext().getMonitorContract().getStatus());
        Assert.assertEquals(ptcAddr, ethereumBBCService.getBbcContext().getPtcContract().getContractAddress());
        Assert.assertEquals(ContractStatusEnum.CONTRACT_READY, ethereumBBCService.getBbcContext().getPtcContract().getStatus());
    }

    @Test
    public void testShutdown() {
        AbstractBBCContext mockValidCtx = mockValidCtx();
        EthereumBBCService ethereumBBCService = new EthereumBBCService();
        ethereumBBCService.startup(mockValidCtx);
        ethereumBBCService.shutdown();
    }

    @Test
    public void testGetContext() {
        AbstractBBCContext mockValidCtx = mockValidCtx();
        EthereumBBCService ethereumBBCService = new EthereumBBCService();
        ethereumBBCService.startup(mockValidCtx);
        AbstractBBCContext ctx = ethereumBBCService.getContext();
        Assert.assertNotNull(ctx);
        Assert.assertNull(ctx.getAuthMessageContract());
    }

    @Test
    public void testSetupAuthMessageContract() {
        EthereumBBCService ethereumBBCService = new EthereumBBCService();
        // start up
        AbstractBBCContext mockValidCtx = mockValidCtx();
        ethereumBBCService.startup(mockValidCtx);

        // set up am
        ethereumBBCService.setupAuthMessageContract();

        // get context
        AbstractBBCContext ctx = ethereumBBCService.getContext();
        Assert.assertEquals(ContractStatusEnum.CONTRACT_DEPLOYED, ctx.getAuthMessageContract().getStatus());
    }

    @Test
    public void testSetupSDPMessageContract() {
        EthereumBBCService ethereumBBCService = new EthereumBBCService();
        // start up
        AbstractBBCContext mockValidCtx = mockValidCtx();
        ethereumBBCService.startup(mockValidCtx);

        // set up sdp
        ethereumBBCService.setupSDPMessageContract();

        // get context
        AbstractBBCContext ctx = ethereumBBCService.getContext();
        Assert.assertEquals(ContractStatusEnum.CONTRACT_DEPLOYED, ctx.getSdpContract().getStatus());
    }

    @Test
    public void testSetupMonitorContract() {
        EthereumBBCService ethereumBBCService = new EthereumBBCService();
        // start up
        AbstractBBCContext mockValidCtx = mockValidCtx();
        ethereumBBCService.startup(mockValidCtx);

        // set up monitor
        ethereumBBCService.setupMonitorContract();

        // get context
        AbstractBBCContext ctx = ethereumBBCService.getContext();
        Assert.assertEquals(ContractStatusEnum.CONTRACT_DEPLOYED, ctx.getMonitorContract().getStatus());
    }

    @Test
    public void testMonitorAndPtcContractAll() throws Exception {
        EthereumBBCService ethereumBBCService = new EthereumBBCService();
        // start up
        AbstractBBCContext mockValidCtx = mockValidCtx();
        ethereumBBCService.startup(mockValidCtx);

        // set up sdp monitor and ptc
        ethereumBBCService.setupSDPMessageContract();
        ethereumBBCService.setupMonitorContract();
        ethereumBBCService.setupPTCContract();

        var ctx = ethereumBBCService.getContext();
        Assert.assertEquals(ContractStatusEnum.CONTRACT_READY, ctx.getPtcContract().getStatus());
        Assert.assertEquals(ContractStatusEnum.CONTRACT_DEPLOYED, ctx.getSdpContract().getStatus());
        Assert.assertEquals(ContractStatusEnum.CONTRACT_DEPLOYED, ctx.getMonitorContract().getStatus());

        // init monitor
        ethereumBBCService.setPtcHubInMonitorVerifier(ethereumBBCService.getBbcContext().getPtcContract().getContractAddress());

        ethereumBBCService.setProtocolInMonitor(ctx.getSdpContract().getContractAddress());
        String addr = Monitor.load(
                ethereumBBCService.getBbcContext().getMonitorContract().getContractAddress(),
                ethereumBBCService.getAcbEthClient().getWeb3j(),
                ethereumBBCService.getAcbEthClient().getCredentials(),
                new DefaultGasProvider()
        ).getProtocol().send();
        log.info("protocol in monitor: {}", addr);

        Assert.assertEquals(ContractStatusEnum.CONTRACT_DEPLOYED, ctx.getMonitorContract().getStatus());

        int monitorType = 2; // 2 denotes MONITOR_OPEN
        ethereumBBCService.setMonitorControl(monitorType);
        int monitorControl = Monitor.load(
                ethereumBBCService.getBbcContext().getMonitorContract().getContractAddress(),
                ethereumBBCService.getAcbEthClient().getWeb3j(),
                ethereumBBCService.getAcbEthClient().getCredentials(),
                new DefaultGasProvider()
        ).getMonitorControl().send().intValue();
        log.info("monitor control in monitor: {}", monitorControl);

        Assert.assertEquals(ContractStatusEnum.CONTRACT_READY, ctx.getMonitorContract().getStatus());


        // init ptc
        ethereumBBCService.updatePTCTrustRoot(ptcTrustRoot);

        var root = ethereumBBCService.getPTCTrustRoot(oid);
        Assert.assertNotNull(root);
        Assert.assertArrayEquals(ptcTrustRoot.getNetworkInfo(), root.getNetworkInfo());

        Assert.assertTrue(ethereumBBCService.hasPTCTrustRoot(oid));

        var resultPtcVa = ethereumBBCService.getPTCVerifyAnchor(oid, BigInteger.ONE);
        Assert.assertNotNull(resultPtcVa);
        Assert.assertArrayEquals(ptcTrustRoot.getVerifyAnchorMap().get(BigInteger.ONE).encode(), resultPtcVa.encode());

        Assert.assertTrue(ethereumBBCService.hasPTCVerifyAnchor(oid, BigInteger.ONE));

        ethereumBBCService.addTpBta(tpbta);

        var resultTpBta = ethereumBBCService.getTpBta(tpbta.getCrossChainLane(), tpbta.getTpbtaVersion());
        Assert.assertNotNull(resultTpBta);
        Assert.assertArrayEquals(tpbta.encode(), resultTpBta.encode());

        Assert.assertTrue(ethereumBBCService.hasTpBta(tpbta.getCrossChainLane(), tpbta.getTpbtaVersion()));
    }

    @Test
    public void testQuerySDPMessageSeq() {
        setupBbc();

        // query seq
        long seq = ethereumBBCService.querySDPMessageSeq(
                "senderDomain",
                DigestUtil.sha256Hex("senderID"),
                CHAIN_DOMAIN,
                DigestUtil.sha256Hex("receiverID")
        );
        Assert.assertEquals(0L, seq);
    }

    @Test
    public void testSetProtocol() throws Exception {
        EthereumBBCService ethereumBBCService = new EthereumBBCService();
        // start up
        AbstractBBCContext mockValidCtx = mockValidCtx();
        ethereumBBCService.startup(mockValidCtx);

        // set up am
        ethereumBBCService.setupAuthMessageContract();

        // set up sdp
        ethereumBBCService.setupSDPMessageContract();

        // get context
        AbstractBBCContext ctx = ethereumBBCService.getContext();
        Assert.assertEquals(ContractStatusEnum.CONTRACT_DEPLOYED, ctx.getAuthMessageContract().getStatus());
        Assert.assertEquals(ContractStatusEnum.CONTRACT_DEPLOYED, ctx.getSdpContract().getStatus());

        // set protocol to am (sdp type: 0)
        ethereumBBCService.setProtocol(
                ctx.getSdpContract().getContractAddress(),
                "0");

        // setup-bbccontracts is intentionally repeatable for in-place SDP/Monitor upgrades.
        ethereumBBCService.setProtocol(
                ctx.getSdpContract().getContractAddress(),
                "0");

        String addr = AuthMsg.load(
                ethereumBBCService.getBbcContext().getAuthMessageContract().getContractAddress(),
                ethereumBBCService.getAcbEthClient().getWeb3j(),
                ethereumBBCService.getAcbEthClient().getCredentials(),
                new DefaultGasProvider()
        ).getProtocol(BigInteger.ZERO).send();
        log.info("protocol: {}", addr);

        ethereumBBCService.setPtcContract(addr);

        // check am status
        ctx = ethereumBBCService.getContext();
        Assert.assertEquals(ContractStatusEnum.CONTRACT_READY, ctx.getAuthMessageContract().getStatus());
    }

    @Test
//    public void testSetAmContractAndLocalDomain() throws Exception {
    public void testSetSDPContractAll() throws Exception {
        EthereumBBCService ethereumBBCService = new EthereumBBCService();
        // start up
        AbstractBBCContext mockValidCtx = mockValidCtx();
        ethereumBBCService.startup(mockValidCtx);

        // set up am
        ethereumBBCService.setupAuthMessageContract();

        // set up sdp
        ethereumBBCService.setupSDPMessageContract();

        // set up monitor
        ethereumBBCService.setupMonitorContract();

        // get context
        AbstractBBCContext ctx = ethereumBBCService.getContext();
        Assert.assertEquals(ContractStatusEnum.CONTRACT_DEPLOYED, ctx.getAuthMessageContract().getStatus());
        Assert.assertEquals(ContractStatusEnum.CONTRACT_DEPLOYED, ctx.getSdpContract().getStatus());
        Assert.assertEquals(ContractStatusEnum.CONTRACT_DEPLOYED, ctx.getMonitorContract().getStatus());

        // set am to sdp
        ethereumBBCService.setAmContract(ctx.getAuthMessageContract().getContractAddress());

        String amAddr = ethereumBBCService.getAcbEthClient().getAmContractFromSdp(ctx.getSdpContract().getContractAddress());
        log.info("amAddr: {}", amAddr);

        // check contract status
        ctx = ethereumBBCService.getContext();
        Assert.assertEquals(ContractStatusEnum.CONTRACT_DEPLOYED, ctx.getSdpContract().getStatus());

        // set the domain
        ethereumBBCService.setLocalDomain(CHAIN_DOMAIN);

//        byte[] rawDomain = SDPMsg.load(
//                ethereumBBCService.getBbcContext().getSdpContract().getContractAddress(),
//                ethereumBBCService.getAcbEthClient().getWeb3j(),
//                ethereumBBCService.getAcbEthClient().getCredentials(),
//                new DefaultGasProvider()
//        ).getLocalDomain().send();
        byte[] rawDomain = ethereumBBCService.getAcbEthClient().getLocalDomainFromSdp(ctx.getSdpContract().getContractAddress());
        log.info("domain: {}", HexUtil.encodeHexStr(rawDomain));

//        String monitorAddr0 = ethereumBBCService.getAcbEthClient().getMonitorContractFromSdp(ctx.getSdpContract().getContractAddress());
//        log.info("monitorAddr: {}", monitorAddr0);

        // check contract status
        ctx = ethereumBBCService.getContext();
        Assert.assertEquals(ContractStatusEnum.CONTRACT_DEPLOYED, ctx.getSdpContract().getStatus());

        // set monitor to sdp
        ethereumBBCService.setMonitorContract(ctx.getMonitorContract().getContractAddress());
        String monitorAddr = SDPMsg.load(
                ethereumBBCService.getBbcContext().getSdpContract().getContractAddress(),
                ethereumBBCService.getAcbEthClient().getWeb3j(),
                ethereumBBCService.getAcbEthClient().getCredentials(),
                new DefaultGasProvider()
        ).getMonitorAddress().send();
        log.info("monitorAddr: {}", monitorAddr);

        // check contract status
        ctx = ethereumBBCService.getContext();
        Assert.assertEquals(ContractStatusEnum.CONTRACT_READY, ctx.getSdpContract().getStatus());
    }

    @Test
    public void testRelayAuthMessage() throws Exception {
        setupBbc();

        // relay am msg
        CrossChainMessageReceipt receipt = ethereumBBCService.relayAuthMessage(getRawMsgFromRelayer(appContract.getContractAddress()));
        Assert.assertTrue(receipt.isSuccessful());

        waitForTxConfirmed(receipt.getTxhash(), ethereumBBCService.getAcbEthClient().getWeb3j());

        EthGetTransactionReceipt ethGetTransactionReceipt = ethereumBBCService.getAcbEthClient().getWeb3j().ethGetTransactionReceipt(receipt.getTxhash()).send();
        TransactionReceipt transactionReceipt = ethGetTransactionReceipt.getTransactionReceipt().get();
        Assert.assertNotNull(transactionReceipt);
        Assert.assertTrue(transactionReceipt.isStatusOK());
    }

    @Test
    public void testReadCrossChainMessageReceipt() throws IOException, InterruptedException {
        setupBbc();

        // relay am msg
        CrossChainMessageReceipt crossChainMessageReceipt = ethereumBBCService.relayAuthMessage(getRawMsgFromRelayer(appContract.getContractAddress()));

        waitForTxConfirmed(crossChainMessageReceipt.getTxhash(), ethereumBBCService.getAcbEthClient().getWeb3j());

        // read receipt by txHash
        CrossChainMessageReceipt crossChainMessageReceipt1 = ethereumBBCService.readCrossChainMessageReceipt(crossChainMessageReceipt.getTxhash());
//        Assert.assertTrue(crossChainMessageReceipt1.isConfirmed());
        // Assert.assertEquals(crossChainMessageReceipt.isSuccessful(), crossChainMessageReceipt1.isSuccessful());
        System.out.println("crossChainMessageReceipt1: " + JSON.toJSONString(crossChainMessageReceipt1));
        System.out.println("crossChainMessageReceipt: " + JSON.toJSONString(crossChainMessageReceipt));
    }

    @Test
    public void testReadCrossChainMessageReceiptWithNoProofInUcp() throws IOException, InterruptedException {
        setupBbc();

        // relay am msg
        CrossChainMessageReceipt crossChainMessageReceipt = ethereumBBCService.relayAuthMessage(getRawMsgFromRelayerWithoutProof(appContract.getContractAddress()));

        waitForTxConfirmed(crossChainMessageReceipt.getTxhash(), ethereumBBCService.getAcbEthClient().getWeb3j());

        // read receipt by txHash
        CrossChainMessageReceipt crossChainMessageReceipt1 = ethereumBBCService.readCrossChainMessageReceipt(crossChainMessageReceipt.getTxhash());
        // Assert.assertTrue(crossChainMessageReceipt1.isConfirmed());
        // Assert.assertEquals(crossChainMessageReceipt.isSuccessful(), crossChainMessageReceipt1.isSuccessful());
    }

    @Test
    public void testRelayMonitorOrder() throws Exception {
        setupBbc();

        // relay monitor order: add app contract to blacklist
        log.info("[TEST-1]: relay monitor order to ADD app contract to BLACKLIST");

        IMonitorOrder monitorOrder = MonitorOrderFactory.createMonitorOrder(
                1,
                "ethereum",
                "testDomain1",
                Long.parseLong("1000"+"0000000000000000000000000000", 2),
                "testDomain1",
                StrUtil.replace(appContract.getContractAddress(), "0x", "000000000000000000000000"),
                "testDomain2",
                StrUtil.replace(REMOTE_APP_CONTRACT, "0x", "000000000000000000000000"),
                "this is a test monitor order",
                "nothing in extra"
        );

        byte[] rawMonitorOrder = monitorOrder.encode();
        byte[] rawProof = SignAlgoEnum.KECCAK256_WITH_SECP256K1.getSigner().sign(
                NODE_PTC_PRIVATE_KEY,
                rawMonitorOrder
        );

        CrossChainMessageReceipt crossChainMessageReceipt = ethereumBBCService.relayMonitorOrder(
                COMMITTEE_ID,
                SignAlgoEnum.KECCAK256_WITH_SECP256K1.getName(),
                rawProof,
                rawMonitorOrder
        );

//        CrossChainMessageReceipt crossChainMessageReceipt = ethereumBBCService.relayMonitorOrder(
//                Long.parseLong("1000"+"0000000000000000000000000000", 2),
//                "testDomain1",
//                StrUtil.replace(appContract.getContractAddress(), "0x", "000000000000000000000000"),
//                "testDomain2",
//                StrUtil.replace(REMOTE_APP_CONTRACT, "0x", "000000000000000000000000"),
//                "this is a test monitor order",
//                "nothing in extra"
//        );
        Assert.assertTrue(crossChainMessageReceipt.isSuccessful());
        waitForTxConfirmed(crossChainMessageReceipt.getTxhash(), ethereumBBCService.getAcbEthClient().getWeb3j());
        CrossChainMessageReceipt crossChainMessageReceipt1 = ethereumBBCService.readCrossChainMessageReceipt(crossChainMessageReceipt.getTxhash());
        Assert.assertEquals(crossChainMessageReceipt.isSuccessful(), crossChainMessageReceipt1.isSuccessful());

        // test app contract: it should be unable to send message
        try {
            appContract.sendUnorderedMessage("remoteDomain", DigestUtil.sha256(REMOTE_APP_CONTRACT), "UnorderedCrossChainMessage".getBytes()).send();
            Assert.fail("[appContract.sendUnorderedMessage]: expected transaction to revert, but it succeeded.");
        } catch (Exception e) {
            log.info("[appContract.sendUnorderedMessage]: reverted as expected: {}", e.getMessage());
        }

        // relay monitor order: remove app contract from blacklist
        log.info("[TEST-2]: relay monitor order to REMOVE app contract to BLACKLIST");
        IMonitorOrder monitorOrder2 = MonitorOrderFactory.createMonitorOrder(
                1,
                "ethereum",
                "testDomain1",
                Long.parseLong("1001"+"0000000000000000000000000000", 2),
                "testDomain1",
                StrUtil.replace(appContract.getContractAddress(), "0x", "000000000000000000000000"),
                "testDomain2",
                StrUtil.replace(REMOTE_APP_CONTRACT, "0x", "000000000000000000000000"),
                "this is a test monitor order",
                "nothing in extra"
        );

        byte[] rawMonitorOrder2 = monitorOrder2.encode();
        byte[] rawProof2 = SignAlgoEnum.KECCAK256_WITH_SECP256K1.getSigner().sign(
                NODE_PTC_PRIVATE_KEY,
                rawMonitorOrder2
        );

        CrossChainMessageReceipt crossChainMessageReceipt2 = ethereumBBCService.relayMonitorOrder(
                COMMITTEE_ID,
                SignAlgoEnum.KECCAK256_WITH_SECP256K1.getName(),
                rawProof2,
                rawMonitorOrder2
        );
//        CrossChainMessageReceipt crossChainMessageReceipt2 = ethereumBBCService.relayMonitorOrder(
//                Long.parseLong("1001"+"0000000000000000000000000000", 2),
//                "testDomain1",
//                StrUtil.replace(appContract.getContractAddress(), "0x", "000000000000000000000000"),
//                "testDomain2",
//                StrUtil.replace(REMOTE_APP_CONTRACT, "0x", "000000000000000000000000"),
//                "this is a test monitor order",
//                "nothing in extra"
//        );
        Assert.assertTrue(crossChainMessageReceipt2.isSuccessful());
        waitForTxConfirmed(crossChainMessageReceipt2.getTxhash(), ethereumBBCService.getAcbEthClient().getWeb3j());
        CrossChainMessageReceipt crossChainMessageReceipt3 = ethereumBBCService.readCrossChainMessageReceipt(crossChainMessageReceipt2.getTxhash());
        Assert.assertEquals(crossChainMessageReceipt2.isSuccessful(), crossChainMessageReceipt3.isSuccessful());

        // test app contract: it should be able to send message
        try {
            var receipt = appContract.sendUnorderedMessage("remoteDomain", DigestUtil.sha256(REMOTE_APP_CONTRACT), "UnorderedCrossChainMessage".getBytes()).send();
            Assert.assertTrue(receipt.isStatusOK());
        } catch (Exception e) {
            log.error("[appContract.sendUnorderedMessage]: unexpected revert: {}", e.getMessage());
            Assert.fail("[appContract.sendUnorderedMessage]: expected transaction to execute successfully, but it failed");
        }
    }


    @Test
    public void testQueryLatestBlockNumber() throws Exception {
        EthereumBBCService ethereumBBCService = new EthereumBBCService();
        // start up
        AbstractBBCContext mockValidCtx = mockValidCtx();
        ethereumBBCService.startup(mockValidCtx);

        BigInteger l = ethereumBBCService.getAcbEthClient().queryLatestBlockNumber();
        Assert.assertTrue(l.compareTo(BigInteger.ZERO) > 0);
    }

    @Test
    public void testReadCrossChainMessagesByHeight_sendUnordered() throws Exception {
        setupBbc();

        var receipt = appContract.sendUnorderedMessage("remoteDomain", DigestUtil.sha256(REMOTE_APP_CONTRACT), "UnorderedCrossChainMessage".getBytes()).send();
        var msgOnHeight = receipt.getBlockNumber();

        List<CrossChainMessage> messageList = ListUtil.toList();
        for (BigInteger i = msgOnHeight.subtract(BigInteger.valueOf(10)); i.compareTo(msgOnHeight) < 1; i = i.add(BigInteger.ONE)) {
            messageList.addAll(ethereumBBCService.readCrossChainMessagesByHeight(i.longValue()));
        }
        Assert.assertFalse(messageList.isEmpty());
        Assert.assertEquals(CrossChainMessage.CrossChainMessageType.AUTH_MSG, messageList.get(0).getType());

        var provableData = messageList.getLast().getProvableData();
        Assert.assertNotNull(provableData);
        Assert.assertEquals(msgOnHeight, provableData.getHeightVal());
        Assert.assertEquals(receipt.getTransactionHash(), Numeric.toHexString(provableData.getTxHash()));
        Assert.assertEquals(receipt.getBlockHash(), Numeric.toHexString(provableData.getBlockHash()));
        var proofObj = EthReceiptProof.decodeFromJson(new String(provableData.getProof()));
        Assert.assertNotNull(proofObj);
        Assert.assertNotNull(proofObj.getEthTransactionReceipt());
        Assert.assertNotNull(proofObj.validateAndGetRoot());
        var amLog = EthAuthMessageLog.decodeFromJson(new String(provableData.getLedgerData()));
        Assert.assertNotNull(amLog);
        Assert.assertNotNull(amLog.getSendAuthMessageLog());
        Assert.assertNotNull(amLog.getLogIndex());
        Assert.assertTrue(StrUtil.isNotEmpty(amLog.getSendAuthMessageLog().getData()));
    }

    @Test
    public void testReadCrossChainMessagesByHeight_sendOrdered() throws Exception {
        setupBbc();

        var receipt = appContract.sendMessage("remoteDomain", DigestUtil.sha256(REMOTE_APP_CONTRACT), "UnorderedCrossChainMessage".getBytes()).send();
        var msgOnHeight = receipt.getBlockNumber();

        List<CrossChainMessage> messageList = ListUtil.toList();
        for (BigInteger i = msgOnHeight.subtract(BigInteger.valueOf(10)); i.compareTo(msgOnHeight) < 1; i = i.add(BigInteger.ONE)) {
            messageList.addAll(ethereumBBCService.readCrossChainMessagesByHeight(i.longValue()));
        }

        Assert.assertEquals(1, messageList.size());
        Assert.assertEquals(CrossChainMessage.CrossChainMessageType.AUTH_MSG, messageList.get(0).getType());

        var provableData = messageList.getFirst().getProvableData();
        Assert.assertNotNull(provableData);
        Assert.assertEquals(msgOnHeight, provableData.getHeightVal());
        Assert.assertEquals(receipt.getTransactionHash(), Numeric.toHexString(provableData.getTxHash()));
       Assert.assertEquals(receipt.getBlockHash(), Numeric.toHexString(provableData.getBlockHash()));
        var proofObj = EthReceiptProof.decodeFromJson(new String(provableData.getProof()));
        Assert.assertNotNull(proofObj);
        Assert.assertNotNull(proofObj.getEthTransactionReceipt());
        Assert.assertNotNull(proofObj.validateAndGetRoot());
        var amLog = EthAuthMessageLog.decodeFromJson(new String(provableData.getLedgerData()));
        Assert.assertNotNull(amLog);
        Assert.assertNotNull(amLog.getSendAuthMessageLog());
        Assert.assertNotNull(amLog.getLogIndex());
        Assert.assertTrue(StrUtil.isNotEmpty(amLog.getSendAuthMessageLog().getData()));
    }

    @Test
    public void testReadConsensusState() {
        setupBbc();

        var currSlot = BigInteger.valueOf(ethereumBBCService.queryLatestHeight());

        var currBlock = ethereumBBCService.getAcbEthClient().getBeaconBlockBySlot(currSlot);

        var cs = ethereumBBCService.readConsensusState(currSlot);
        Assert.assertArrayEquals(currBlock.getRoot().toArray(), cs.getHash());
        Assert.assertEquals(currBlock.getSlot().bigIntegerValue(), cs.getHeight());
        Assert.assertEquals(currBlock.getBody().getOptionalExecutionPayloadHeader().get().getTimestamp().longValue() * 1000, cs.getStateTimestamp());

        var schemaDefinitions = ethereumBBCService.getConfig().getEth2ChainConfig().getCurrentSchemaDefinitions(currSlot);
        var specConfig = ethereumBBCService.getConfig().getEth2ChainConfig().getSpecConfig();

        var stateData = EthConsensusStateData.fromJson(new String(cs.getStateData()), schemaDefinitions, specConfig);
        Assert.assertEquals(ethereumBBCService.getContext().getAuthMessageContract().getContractAddress(), stateData.getAmContract().toHexString());
        Assert.assertNotNull(stateData.getExecutionPayloadHeader());
        Assert.assertNull(stateData.getLightClientUpdateWrapper());
        Assert.assertNotNull(stateData.getBeaconBlockHeader());
        Assert.assertNotNull(stateData.getExecutionPayloadBranches());

        var syncPeriodLength = ethereumBBCService.getConfig().getEth2ChainConfig().getSyncPeriodLength();
        var period = currSlot.divide(BigInteger.valueOf(syncPeriodLength));
        Assert.assertEquals(period, stateData.getCurrSyncPeriod(syncPeriodLength).bigIntegerValue());

        var endorsements = EthConsensusEndorsements.fromJson(new String(cs.getEndorsements()), specConfig.getSyncCommitteeSize());
        Assert.assertNotNull(endorsements.getSyncAggregate());

        if (period.compareTo(BigInteger.ZERO) > 0) {
            var currCommittee = ethereumBBCService.getAcbEthClient()
                    .getLightClientUpdate(period.subtract(BigInteger.ONE).multiply(BigInteger.valueOf(syncPeriodLength)))
                    .getNextSyncCommittee();
            stateData.validate(currCommittee, endorsements, ethereumBBCService.getConfig().getEth2ChainConfig());
        }

        if (currSlot.compareTo(BigInteger.valueOf(syncPeriodLength - 1)) > 0) {
            log.info("slot bigger than one period, so we can test about light update fetch");

            period = currSlot.divide(BigInteger.valueOf(syncPeriodLength));
            log.info("found period {}", period);

            currSlot = period.multiply(BigInteger.valueOf(syncPeriodLength));
            cs = ethereumBBCService.readConsensusState(currSlot);

            currBlock = ethereumBBCService.getAcbEthClient().getBeaconBlockBySlot(currSlot);

            Assert.assertEquals(currSlot, cs.getHeight());
            Assert.assertEquals(currBlock.getBody().getOptionalExecutionPayloadHeader().get().getTimestamp().longValue() * 1000, cs.getStateTimestamp());
            Assert.assertNotNull(cs.getStateData());

            stateData = EthConsensusStateData.fromJson(new String(cs.getStateData()), schemaDefinitions, specConfig);

            Assert.assertEquals(ethereumBBCService.getContext().getAuthMessageContract().getContractAddress(), stateData.getAmContract().toHexString());
            Assert.assertNotNull(stateData.getExecutionPayloadHeader());
            Assert.assertNotNull(stateData.getLightClientUpdateWrapper());
            Assert.assertNotNull(stateData.getBeaconBlockHeader());
            Assert.assertNotNull(stateData.getExecutionPayloadBranches());
            Assert.assertEquals(period, stateData.getCurrSyncPeriod(syncPeriodLength).bigIntegerValue());

            endorsements = EthConsensusEndorsements.fromJson(new String(cs.getEndorsements()), specConfig.getSyncCommitteeSize());
            Assert.assertNotNull(endorsements.getSyncAggregate());

            if (period.compareTo(BigInteger.ZERO) > 0) {
                var currCommittee = ethereumBBCService.getAcbEthClient()
                        .getLightClientUpdate(period.subtract(BigInteger.ONE).multiply(BigInteger.valueOf(syncPeriodLength)))
                        .getNextSyncCommittee();
                stateData.validate(currCommittee, endorsements, ethereumBBCService.getConfig().getEth2ChainConfig());
            }
        }
    }

    @SneakyThrows
    private void setupBbc() {
        if (setupBBC) {
            return;
        }
        // start up
        AbstractBBCContext mockValidCtx = mockValidCtx();
        ethereumBBCService.startup(mockValidCtx);

        // set up am
        ethereumBBCService.setupAuthMessageContract();

        // set up sdp
        ethereumBBCService.setupSDPMessageContract();

        // set up monitor
        ethereumBBCService.setupMonitorContract();

        ethereumBBCService.setupPTCContract();

        // set protocol to am (sdp type: 0)
        ethereumBBCService.setProtocol(
                mockValidCtx.getSdpContract().getContractAddress(),
                "0");

        // set am to sdp
        ethereumBBCService.setAmContract(mockValidCtx.getAuthMessageContract().getContractAddress());

        // set local domain to sdp
        ethereumBBCService.setLocalDomain(CHAIN_DOMAIN);

        // set monitor to sdp
        ethereumBBCService.setMonitorContract(mockValidCtx.getMonitorContract().getContractAddress());

        // set sdp to monitor
        ethereumBBCService.setProtocolInMonitor(mockValidCtx.getSdpContract().getContractAddress());

        // set monitorControl to monitor
        ethereumBBCService.setMonitorControl(2);

        // set ptc hub to monitor verifier
        ethereumBBCService.setPtcHubInMonitorVerifier(mockValidCtx.getPtcContract().getContractAddress());

        // set ptc tp am
        ethereumBBCService.setPtcContract(mockValidCtx.getPtcContract().getContractAddress());

        ethereumBBCService.updatePTCTrustRoot(ptcTrustRoot);

        ethereumBBCService.addTpBta(tpbta);

        // check contract ready
        AbstractBBCContext ctxCheck = ethereumBBCService.getContext();
        Assert.assertEquals(ContractStatusEnum.CONTRACT_READY, ctxCheck.getAuthMessageContract().getStatus());
        Assert.assertEquals(ContractStatusEnum.CONTRACT_READY, ctxCheck.getSdpContract().getStatus());
        Assert.assertEquals(ContractStatusEnum.CONTRACT_READY, ctxCheck.getMonitorContract().getStatus());

        TransactionReceipt receipt = appContract.setProtocol(ethereumBBCService.getBbcContext().getSdpContract().getContractAddress()).send();
        if (receipt.isStatusOK()) {
            log.info("set protocol({}) to app contract({})",
                    ethereumBBCService.getBbcContext().getSdpContract().getContractAddress(),
                    appContract.getContractAddress());
        } else {
            throw new Exception(String.format("failed to set protocol(%s) to app contract(%s)",
                    appContract.getContractAddress(),
                    ethereumBBCService.getBbcContext().getSdpContract().getContractAddress()));
        }

        TransactionReceipt receipt1 = appContract.setMonitorContract(ethereumBBCService.getBbcContext().getMonitorContract().getContractAddress()).send();
        if (receipt1.isStatusOK()) {
            log.info("set monitor contract({}) to app contract({})",
                    ethereumBBCService.getBbcContext().getMonitorContract().getContractAddress(),
                    appContract.getContractAddress());
        } else {
            throw new Exception(String.format("failed to set monitor contract(%s) to app contract(%s)",
                    ethereumBBCService.getBbcContext().getMonitorContract().getContractAddress(),
                    appContract.getContractAddress()));
        }

        setupBBC = true;
    }

    private AbstractBBCContext mockValidCtx() {
        EthereumConfig mockConf = new EthereumConfig();
        mockConf.setUrl(VALID_URL);
        mockConf.setBeaconApiUrl(VALID_BEACON_URL);
        mockConf.setPrivateKey(BBC_ETH_PRIVATE_KEY);
        mockConf.setGasPrice(8000000000L);
        mockConf.setGasLimit(3000000);
        mockConf.setMsgScanPolicy(scanPolicy);
        mockConf.setGasLimitPolicy(gasLimitPolicy);
        mockConf.setGasPricePolicy(gasPricePolicy);
        mockConf.setGasPriceProviderSupplier(GasPriceProviderSupplierEnum.ETHEREUM);
//        mockConf.setGasProviderUrl("");
//        mockConf.setGasProviderApiKey("");
//        mockConf.setGasUpdateInterval(15000);
        mockConf.setKmsService(false);
        mockConf.setKmsEndpoint(endpoint);
        mockConf.setKmsAccessKeyId(accessKeyId);
        mockConf.setKmsAccessKeySecret(accessKeySecret);
        mockConf.setKmsPrivateKeyId(privateKeyId1);
        // mockConf.setEthNoncePolicy(EthNoncePolicyEnum.FAST);
        mockConf.setEthNetwork(eth2NetworkTesting);

        if (eth2NetworkTesting == Eth2NetworkEnum.PRIVATE_NET) {
            Eth2ChainConfig eth2ChainConfig = new Eth2ChainConfig(
                    eth2NetworkTesting,
                    GENESIS_TIME,
                    Numeric.hexStringToByteArray(GENESIS_VALIDATOR_ROOT)
            );
            eth2ChainConfig.setRemoteSpecConfig(REMOTE_SPEC_CONFIG);
            mockConf.setEth2ChainConfig(eth2ChainConfig);
        }

        mockConf.setBcdnsRootCertPem(BCDNS_CERT);
        AbstractBBCContext mockCtx = new DefaultBBCContext();
        mockCtx.setConfForBlockchainClient(mockConf.toJsonString().getBytes());
        return mockCtx;
    }

    private AbstractBBCContext mockValidCtxWithPreDeployedContracts(String amAddr, String sdpAddr, String monitorAddr, String ptcAddr) {
        EthereumConfig mockConf = new EthereumConfig();
        mockConf.setUrl(VALID_URL);
        mockConf.setBeaconApiUrl(VALID_BEACON_URL);
        mockConf.setPrivateKey(BBC_ETH_PRIVATE_KEY_2);
        mockConf.setAmContractAddressDeployed(amAddr);
        mockConf.setSdpContractAddressDeployed(sdpAddr);
        mockConf.setMonitorContractAddressDeployed(monitorAddr);
        mockConf.setPtcHubContractAddressDeployed(ptcAddr);
        mockConf.setMsgScanPolicy(scanPolicy);
        mockConf.setGasLimitPolicy(gasLimitPolicy);
        mockConf.setGasPricePolicy(gasPricePolicy);
        mockConf.setEthNetwork(eth2NetworkTesting);
        if (eth2NetworkTesting == Eth2NetworkEnum.PRIVATE_NET) {
            Eth2ChainConfig eth2ChainConfig = new Eth2ChainConfig(
                    eth2NetworkTesting,
                    GENESIS_TIME,
                    Numeric.hexStringToByteArray(GENESIS_VALIDATOR_ROOT)
            );
            eth2ChainConfig.setRemoteSpecConfig(REMOTE_SPEC_CONFIG);
            mockConf.setEth2ChainConfig(eth2ChainConfig);
        }
        mockConf.setBcdnsRootCertPem(BCDNS_CERT);
        mockConf.setGasPriceProviderSupplier(GasPriceProviderSupplierEnum.ETHEREUM);
        AbstractBBCContext mockCtx = new DefaultBBCContext();
        mockCtx.setConfForBlockchainClient(mockConf.toJsonString().getBytes());
        return mockCtx;
    }

    private AbstractBBCContext mockValidCtxWithPreReadyContracts(String amAddr, String sdpAddr, String monitorAddr, String ptcAddr) {
        EthereumConfig mockConf = new EthereumConfig();
        mockConf.setUrl(VALID_URL);
        mockConf.setBeaconApiUrl(VALID_BEACON_URL);
        mockConf.setPrivateKey(BBC_ETH_PRIVATE_KEY_3);
        mockConf.setAmContractAddressDeployed(amAddr);
        mockConf.setSdpContractAddressDeployed(sdpAddr);
        mockConf.setMonitorContractAddressDeployed(monitorAddr);
        mockConf.setPtcHubContractAddressDeployed(ptcAddr);
        mockConf.setMsgScanPolicy(scanPolicy);
        mockConf.setGasLimitPolicy(gasLimitPolicy);
        mockConf.setGasPricePolicy(gasPricePolicy);
        mockConf.setEthNetwork(eth2NetworkTesting);
        mockConf.setBcdnsRootCertPem(BCDNS_CERT);
        if (eth2NetworkTesting == Eth2NetworkEnum.PRIVATE_NET) {
            Eth2ChainConfig eth2ChainConfig = new Eth2ChainConfig(
                    eth2NetworkTesting,
                    GENESIS_TIME,
                    Numeric.hexStringToByteArray(GENESIS_VALIDATOR_ROOT)
            );
            eth2ChainConfig.setRemoteSpecConfig(REMOTE_SPEC_CONFIG);
            mockConf.setEth2ChainConfig(eth2ChainConfig);
        }
        mockConf.setGasPriceProviderSupplier(GasPriceProviderSupplierEnum.ETHEREUM);
        AbstractBBCContext mockCtx = new DefaultBBCContext();
        mockCtx.setConfForBlockchainClient(mockConf.toJsonString().getBytes());

        AuthMessageContract authMessageContract = new AuthMessageContract();
        authMessageContract.setContractAddress(amAddr);
        authMessageContract.setStatus(ContractStatusEnum.CONTRACT_READY);
        mockCtx.setAuthMessageContract(authMessageContract);

        SDPContract sdpContract = new SDPContract();
        sdpContract.setContractAddress(sdpAddr);
        sdpContract.setStatus(ContractStatusEnum.CONTRACT_READY);
        mockCtx.setSdpContract(sdpContract);

        MonitorContract monitorContract = new MonitorContract();
        monitorContract.setContractAddress(monitorAddr);
        monitorContract.setStatus(ContractStatusEnum.CONTRACT_READY);
        mockCtx.setMonitorContract(monitorContract);

        return mockCtx;
    }

    private AbstractBBCContext mockInvalidCtx() {
        EthereumConfig mockConf = new EthereumConfig();
        mockConf.setUrl(INVALID_URL);
        mockConf.setPrivateKey(APP_USER_ETH_PRIVATE_KEY);
        AbstractBBCContext mockCtx = new DefaultBBCContext();
        mockCtx.setConfForBlockchainClient(mockConf.toJsonString().getBytes());
        return mockCtx;
    }

    @SneakyThrows
    private void waitForTxConfirmed(String txhash, Web3j web3j) {
        for (int i = 0; i < MAX_TX_RESULT_QUERY_TIME; i++) {
            Optional<TransactionReceipt> receiptOptional = web3j.ethGetTransactionReceipt(txhash).send().getTransactionReceipt();
            if (receiptOptional.isPresent() && receiptOptional.get().getBlockNumber().longValue() > 0L) {
                if (receiptOptional.get().getStatus().equals("0x1")) {
                    log.info("tx {} has been confirmed as success", txhash);
                } else {
                    log.error("tx {} has been confirmed as failed", txhash);
                }
                break;
            }
            Thread.sleep(1_000);
        }
    }

    @SneakyThrows
    @Test
    public void testGetRawMsgFromRelayer() {
        // IMonitorMessage monitorMessage = MonitorMessageFactory.createMonitorMessage(
        //         1,
        //         1,
        //         "hello",
        //         "123".getBytes()
        // );
        // IMonitorMessage monitorMessage2 = MonitorMessageFactory.createMonitorMessage(monitorMessage.encode());
        // System.out.println("monitorMessage2.monitorType: " + monitorMessage2.getMonitorType());
        // System.out.println("monitorMessage2.monitorMsg: " + monitorMessage2.getMonitorMsg());
        // System.out.println("monitorMessage2.payload: " + new String(monitorMessage2.getPayload()));
        System.out.println("rawMsg: " + HexUtil.encodeHexStr(getRawMsgFromRelayerWithoutProof(appContract.getContractAddress())));
    }

    private byte[] getRawMsgFromRelayerWithoutProof(String receiverAddr) throws IOException {
        // need to replace "awesome antchain-bridge" with monitor message
        IMonitorMessage monitorMessage = MonitorMessageFactory.createMonitorMessage(
                1,
                1, // MONITOR_CLOSE
                "this is a monitorMsg",
                "123".getBytes()
        );
        ISDPMessage sdpMessage = SDPMessageFactory.createSDPMessage(
                1,
                new byte[32],
                crossChainLane.getReceiverDomain().getDomain(),
                Numeric.hexStringToByteArray(StrUtil.replace(receiverAddr, "0x", "000000000000000000000000")),
                -1,
                monitorMessage.encode()
        );
        IAuthMessage am = AuthMessageFactory.createAuthMessage(
                1,
                RandomUtil.randomBytes(32),
                0,
                sdpMessage.encode()
        );

        MockResp resp = new MockResp();
        resp.setRawResponse(am.encode());

        MockProof proof = new MockProof();
        proof.setResp(resp);
        proof.setDomain("senderDomain");

        byte[] rawProof = TLVUtils.encode(proof);

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

    private byte[] getRawMsgFromRelayer(String receiverAddr) throws IOException {
        // need to replace "awesome antchain-bridge" with monitor message
        IMonitorMessage monitorMessage = MonitorMessageFactory.createMonitorMessage(
                1,
                1,
                "this is a monitorMsg",
                "123".getBytes()
        );
        ISDPMessage sdpMessage = SDPMessageFactory.createSDPMessage(
                1,
                new byte[32],
                crossChainLane.getReceiverDomain().getDomain(),
                Numeric.hexStringToByteArray(StrUtil.replace(receiverAddr, "0x", "000000000000000000000000")),
                -1,
                monitorMessage.encode()
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
                .nodeId("monitor-node")
                .signAlgo(SignAlgoEnum.KECCAK256_WITH_SECP256K1)
                .signature(SignAlgoEnum.KECCAK256_WITH_SECP256K1.getSigner().sign(
                        NODE_PTC_PRIVATE_KEY,
                        thirdPartyProof.getEncodedToSign()
                )).build();
        CommitteeNodeProof node3Proof = CommitteeNodeProof.builder()
                .nodeId("node3")
                .signAlgo(SignAlgoEnum.KECCAK256_WITH_SECP256K1)
                .signature(SignAlgoEnum.KECCAK256_WITH_SECP256K1.getSigner().sign(
                        NODE_PTC_PRIVATE_KEY,
                        thirdPartyProof.getEncodedToSign()
                )).build();

        CommitteeEndorseProof endorseProof = new CommitteeEndorseProof();
        endorseProof.setCommitteeId(COMMITTEE_ID);
        endorseProof.setSigs(ListUtil.toList(node1Proof, node2Proof, node3Proof));

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

    /**
     * Get the sdp message payload from the raw bytes
     * which is the input for {@link com.alipay.antchain.bridge.plugins.spi.bbc.IBBCService#relayAuthMessage(byte[])}
     *
     * @param raw the input for {@link com.alipay.antchain.bridge.plugins.spi.bbc.IBBCService#relayAuthMessage(byte[])}
     * @return {@code byte[]} sdp payload
     */
    private static byte[] getSDPPayloadFromRawMsg(byte[] raw) {
        ByteArrayInputStream stream = new ByteArrayInputStream(raw);

        byte[] zeros = new byte[4];
        stream.read(zeros, 0, 4);

        byte[] rawLen = new byte[4];
        stream.read(rawLen, 0, 4);

        int len = ByteUtil.bytesToInt(rawLen, ByteOrder.BIG_ENDIAN);

        byte[] rawProof = new byte[len];
        stream.read(rawProof, 0, len);

        MockProof proof = TLVUtils.decode(rawProof, MockProof.class);
        IAuthMessage authMessage = AuthMessageFactory.createAuthMessage(proof.getResp().getRawResponse());
        ISDPMessage sdpMessage = SDPMessageFactory.createSDPMessage(authMessage.getPayload());

        return sdpMessage.getPayload();
    }

    @Getter
    @Setter
    public static class MockProof {

        @TLVField(tag = 5, type = TLVTypeEnum.BYTES)
        private MockResp resp;

        @TLVField(tag = 9, type = TLVTypeEnum.STRING)
        private String domain;
    }

    @Getter
    @Setter
    public static class MockResp {

        @TLVField(tag = 0, type = TLVTypeEnum.BYTES)
        private byte[] rawResponse;
    }
}

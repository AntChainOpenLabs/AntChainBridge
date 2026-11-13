package com.alipay.antchain.bridge.plugins.mychain020;

import cn.hutool.core.collection.ListUtil;
import cn.hutool.core.map.MapUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.ReflectUtil;
import cn.hutool.core.util.StrUtil;
import com.alipay.antchain.bridge.commons.bbc.AbstractBBCContext;
import com.alipay.antchain.bridge.commons.bbc.DefaultBBCContext;
import com.alipay.antchain.bridge.commons.bbc.syscontract.ContractStatusEnum;
import com.alipay.antchain.bridge.commons.bcdns.PTCCredentialSubject;
import com.alipay.antchain.bridge.commons.bcdns.utils.CrossChainCertificateUtil;
import com.alipay.antchain.bridge.commons.core.am.AuthMessageFactory;
import com.alipay.antchain.bridge.commons.core.am.IAuthMessage;
import com.alipay.antchain.bridge.commons.core.base.CrossChainDomain;
import com.alipay.antchain.bridge.commons.core.base.CrossChainIdentity;
import com.alipay.antchain.bridge.commons.core.base.CrossChainLane;
import com.alipay.antchain.bridge.commons.core.base.CrossChainMessage;
import com.alipay.antchain.bridge.commons.core.base.ObjectIdentity;
import com.alipay.antchain.bridge.commons.core.base.SendResponseResult;
import com.alipay.antchain.bridge.commons.core.base.X509PubkeyInfoObjectIdentity;
import com.alipay.antchain.bridge.commons.core.monitor.AbstractMonitorMessage;
import com.alipay.antchain.bridge.commons.core.monitor.IMonitorMessage;
import com.alipay.antchain.bridge.commons.core.monitor.MonitorMessageFactory;
import com.alipay.antchain.bridge.commons.core.ptc.PTCTrustRoot;
import com.alipay.antchain.bridge.commons.core.ptc.PTCVerifyAnchor;
import com.alipay.antchain.bridge.commons.core.ptc.ThirdPartyBlockchainTrustAnchor;
import com.alipay.antchain.bridge.commons.core.ptc.ThirdPartyBlockchainTrustAnchorV1;
import com.alipay.antchain.bridge.commons.core.ptc.ThirdPartyProof;
import com.alipay.antchain.bridge.commons.core.sdp.AtomicFlagEnum;
import com.alipay.antchain.bridge.commons.core.sdp.ISDPMessage;
import com.alipay.antchain.bridge.commons.core.sdp.SDPMessageFactory;
import com.alipay.antchain.bridge.commons.core.sdp.SDPMessageV2;
import com.alipay.antchain.bridge.commons.core.sdp.SDPMessageV3;
import com.alipay.antchain.bridge.commons.core.sdp.TimeoutMeasureEnum;
import com.alipay.antchain.bridge.commons.utils.crypto.HashAlgoEnum;
import com.alipay.antchain.bridge.commons.utils.crypto.SignAlgoEnum;
import com.alipay.antchain.bridge.plugins.mychain020.common.SDPMsgTypeEnum;
import com.alipay.antchain.bridge.plugins.mychain020.contract.AMContractClientEVM;
import com.alipay.antchain.bridge.plugins.mychain020.model.ContractAddressInfo;
import com.alipay.antchain.bridge.plugins.mychain020.sdk.Mychain020Client;
import com.alipay.antchain.bridge.plugins.mychain020.sdk.Mychain020Config;
import com.alipay.antchain.bridge.plugins.mychain020.sdp.ReceiverEvm;
import com.alipay.antchain.bridge.ptc.committee.types.basic.CommitteeEndorseProof;
import com.alipay.antchain.bridge.ptc.committee.types.basic.CommitteeNodeProof;
import com.alipay.antchain.bridge.ptc.committee.types.basic.NodePublicKeyEntry;
import com.alipay.antchain.bridge.ptc.committee.types.network.CommitteeNetworkInfo;
import com.alipay.antchain.bridge.ptc.committee.types.tpbta.CommitteeEndorseRoot;
import com.alipay.antchain.bridge.ptc.committee.types.tpbta.NodeEndorseInfo;
import com.alipay.antchain.bridge.ptc.committee.types.tpbta.OptionalEndorsePolicy;
import com.alipay.antchain.bridge.ptc.committee.types.trustroot.CommitteeVerifyAnchor;
import com.alipay.mychain.sdk.api.utils.Utils;
import com.alipay.mychain.sdk.common.VMTypeEnum;
import com.alipay.mychain.sdk.domain.account.Identity;
import com.alipay.mychain.sdk.domain.transaction.LogEntry;
import com.alipay.mychain.sdk.domain.transaction.TransactionReceipt;
import com.alipay.mychain.sdk.errorcode.ErrorCode;
import com.alipay.mychain.sdk.message.query.QueryTransactionReceiptResponse;
import com.alipay.mychain.sdk.vm.EVMOutput;
import com.alipay.mychain.sdk.vm.EVMParameter;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.util.encoders.Hex;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.Security;
import java.security.spec.ECGenParameterSpec;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Explicit, serial integration test for a shared Mychain node.
 *
 * <p>This class deliberately contains one test method so Maven cannot execute
 * setup repeatedly. It deploys only uniquely named contracts and never reads
 * or upgrades contract names supplied by the external configuration.</p>
 */
public class Mychain020MonitorOnChainIntegrationTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(Mychain020MonitorOnChainIntegrationTest.class);

    private static final String ENABLE_PROPERTY = "mychain.integration.enabled";
    private static final String CONFIG_PROPERTY = "mychain.config.path";
    private static final String EXPECTED_PRIMARY_PROPERTY = "mychain.integration.expectedPrimary";
    private static final String CONNECTION_PRIMARY_PROPERTY = "mychain.integration.connectionPrimary";
    private static final String RESUME_RUN_ID_PROPERTY = "mychain.integration.resumeRunId";
    private static final String RESUME_AM_PROPERTY = "mychain.integration.resumeAm";
    private static final String RESUME_SDP_PROPERTY = "mychain.integration.resumeSdp";
    private static final String RESUME_PTC_PROPERTY = "mychain.integration.resumePtc";
    private static final String RESUME_MONITOR_PROPERTY = "mychain.integration.resumeMonitor";
    private static final String RESUME_MONITOR_VERIFIER_PROPERTY = "mychain.integration.resumeMonitorVerifier";
    private static final String RESUME_RECEIVER_PROPERTY = "mychain.integration.resumeReceiver";
    private static final String RESUME_SUBMITTED_TRANSACTIONS_PROPERTY =
            "mychain.integration.resumeSubmittedTransactions";
    private static final String EXPECTED_PRIMARY = "47.116.166.95 18130";
    private static final int MAX_SHARED_CHAIN_TRANSACTIONS = 29;

    private static final String TEST_BCDNS_ROOT_CERT =
            "-----BEGIN BCDNS TRUST ROOT CERTIFICATE-----\n" +
            "AADWAQAAAAABAAAAMQEABAAAAHRlc3QCAAEAAAAAAwBrAAAAAABlAAAAAAABAAAA\n" +
            "AAEAWAAAADBWMBAGByqGSM49AgEGBSuBBAAKA0IABFC80gzzOJoUZBqQolYqx2U1\n" +
            "n87mfz3zuvv0X1YBqWcbOBFEGYcOUp2FiMCvfSsQzzcbWBuzhIlgwO/hCmVFgSME\n" +
            "AAgAAACU2f9mAAAAAAUACAAAABQN4WgAAAAABgCGAAAAAACAAAAAAAADAAAAYmlm\n" +
            "AQBrAAAAAABlAAAAAAABAAAAAAEAWAAAADBWMBAGByqGSM49AgEGBSuBBAAKA0IA\n" +
            "BFC80gzzOJoUZBqQolYqx2U1n87mfz3zuvv0X1YBqWcbOBFEGYcOUp2FiMCvfSsQ\n" +
            "zzcbWBuzhIlgwO/hCmVFgSMCAAAAAAAHAJ8AAAAAAJkAAAAAAAoAAABLRUNDQUst\n" +
            "MjU2AQAgAAAA1/SncCIPlAQGRJ4Zp2WPBmrk5poje12brhJatwWR5BwCABYAAABL\n" +
            "ZWNjYWsyNTZXaXRoU2VjcDI1NmsxAwBBAAAAR23ngOzN3b8gaJY9ikvNtdqzwF6K\n" +
            "zAkr89qnHDJQei9iXVds+7Padq41StiQShIiB9yWtx8/3Qu878R9zmJbZAA=\n" +
            "-----END BCDNS TRUST ROOT CERTIFICATE-----\n";

    private static final String COMMITTEE_ID = "committee-mychain020-monitor";
    private static final String NORMAL_NODE_ID = "node-normal-1";
    private static final String MONITOR_NODE_ID = "monitor-node-1";

    private static final String MONITOR_VERIFIER_RUNTIME = "/contract/v1/solidity/MonitorVerifier.bin-runtime";
    private static final String COMMITTEE_VERIFIER_RUNTIME = "/contract/v1/solidity/CommitteePtcVerifier.bin-runtime";
    private static final String PTC_HUB_RUNTIME = "/contract/v1/solidity/PtcHub.bin-runtime";
    private static final String MONITOR_RUNTIME = "/contract/v1/solidity/Monitor.bin-runtime";

    private enum ProofMode {
        VALID,
        EMPTY_MONITOR,
        OMIT_MONITOR,
        BAD_NORMAL
    }

    @Test
    public void completeMonitorFlowOnSharedChainWithOneContractSet() throws Exception {
        requireExplicitOptIn();

        // The supplied Mychain 3.7.2 SDK is a fat jar and contains the same
        // native resource name as the module's test-only tcnative artifact.
        // This is also the compatibility switch used by the deployed plugin
        // server; it must be set before Netty initializes OpenSsl.
        System.setProperty("io.netty.native.detectNativeLibraryDuplicates", "false");

        String rawConfig = new String(
                Files.readAllBytes(Paths.get(requiredProperty(CONFIG_PROPERTY))),
                StandardCharsets.UTF_8);
        Mychain020Config config = Mychain020Config.fromJsonString(rawConfig);
        assertSafeFreshConfiguration(config);
        applyLoopbackConnectionOverride(config);

        ResumeContracts resume = ResumeContracts.fromSystemProperties();
        if (resume != null) {
            applyResumeConfiguration(config, resume);
        } else {
            // The fixture PTC certificate is issued by this repository's test
            // BCDNS root. Use it only in memory for the isolated contract set.
            config.setBcdnsRootCertPem(TEST_BCDNS_ROOT_CERT);
        }

        // The regulatory contracts are EVM-only. Avoid two unrelated WASM
        // deployments on a shared chain; the external file is never modified.
        config.setWasmSupported(false);

        String runId = resume == null ? UUID.randomUUID().toString() : resume.runId;
        String localDomain = "mychain020-monitor-" + runId + ".test";
        String sourceDomain = "source-" + runId + ".test";
        LOGGER.info("MYCHAIN_MONITOR_TEST_RUN_ID={}", runId);

        Mychain020BBCService service = new Mychain020BBCService();
        Mychain020Client client = null;
        int submittedTransactions = 0;
        try {
            ReflectUtil.invoke(service, "setLogger", LOGGER);
            AbstractBBCContext context = new DefaultBBCContext();
            context.setConfForBlockchainClient(config.toJsonString().getBytes(StandardCharsets.UTF_8));
            service.startup(context);
            client = (Mychain020Client) ReflectUtil.getFieldValue(service, "mychain020Client");

            Assert.assertTrue("configured account is not valid on the target Mychain", client.ifAccountValid());
            Assert.assertFalse("monitor integration does not support a TEE chain", client.isTeeChain());
            long heightBefore = client.queryLatestHeight();
            Thread.sleep(3500L);
            Assert.assertTrue("target Mychain is not producing blocks", client.queryLatestHeight() > heightBefore);

            Mychain020BBCContext mychainContext;
            if (resume == null) {
                // Exactly one fresh EVM system-contract set.
                service.setupAuthMessageContract();
                submittedTransactions += 2; // deploy AM + add relayer
                service.setupSDPMessageContract();
                submittedTransactions += 1;
                service.setupPTCContract();
                submittedTransactions += 4; // verifier + hub + binding + root reconciliation
                service.setLocalDomain(localDomain);
                service.setProtocol("", "0");
                service.setPtcContract("");
                submittedTransactions += 3;
                service.setupMonitorContract();
                submittedTransactions += 7; // two deploys and five bindings
                mychainContext = (Mychain020BBCContext) service.getContext();
            } else {
                // A failed assertion may leave a complete, uniquely named
                // contract set on a shared chain. Resume that exact set rather
                // than deploying replacements or repeating setup transactions.
                mychainContext = (Mychain020BBCContext) service.getContext();
                mychainContext.getAuthMessageContract().setStatus(ContractStatusEnum.CONTRACT_READY);
                mychainContext.getSdpContract().setStatus(ContractStatusEnum.CONTRACT_READY);
                mychainContext.getPtcContract().setStatus(ContractStatusEnum.CONTRACT_READY);
                mychainContext.getMonitorContract().setStatus(ContractStatusEnum.CONTRACT_READY);
                submittedTransactions = resume.submittedTransactions;
                LOGGER.info("MYCHAIN_MONITOR_TEST_RESUMED_WRITES={}", submittedTransactions);
            }
            assertContractVersionsAndBindings(client, mychainContext);
            Assert.assertEquals(BigInteger.valueOf(AbstractMonitorMessage.MONITOR_OPEN),
                    localCallUint(client, mychainContext.getMonitorContractClientEVM().getContractAddress(),
                            "getMonitorControl()"));

            ProofFixture fixture = createProofFixture(sourceDomain, localDomain);
            if (ensureTestBcdnsRoot(client, mychainContext)) {
                submittedTransactions++;
            }
            service.updatePTCTrustRoot(fixture.ptcTrustRoot);
            submittedTransactions++;
            service.addTpBta(fixture.tpbta);
            submittedTransactions++;
            Assert.assertTrue(service.hasPTCTrustRoot(fixture.ptcOwnerOid));
            Assert.assertTrue(service.hasPTCVerifyAnchor(fixture.ptcOwnerOid, BigInteger.ONE));
            Assert.assertTrue(service.hasTpBta(fixture.lane, fixture.tpbta.getTpbtaVersion()));

            ReceiverEvm receiver = new ReceiverEvm(
                    localDomain,
                    client,
                    mychainContext.getSdpContractClientEVM().getContractAddress(),
                    resume == null ? null : resume.receiver);
            if (resume == null || StrUtil.isEmpty(resume.receiver)) {
                submittedTransactions++;
            }

            // Exercise Mychain update-contract by identity/name and prove that
            // all state appended by the regulatory contracts survives.
            Identity committeeVerifier = mychainContext.getPtcContractEvm().getCommitteeVerifierIdentity();
            Identity monitorVerifier = mychainContext.getMonitorContractClientEVM().getMonitorVerifierIdentity();
            Assert.assertTrue(client.upgradeContract(MONITOR_VERIFIER_RUNTIME, monitorVerifier, VMTypeEnum.EVM));
            Assert.assertTrue(client.upgradeContract(COMMITTEE_VERIFIER_RUNTIME, committeeVerifier, VMTypeEnum.EVM));
            Assert.assertTrue(client.upgradeContract(
                    PTC_HUB_RUNTIME,
                    mychainContext.getPtcContractEvm().getContractAddress(),
                    VMTypeEnum.EVM));
            Assert.assertTrue(client.upgradeContract(
                    MONITOR_RUNTIME,
                    mychainContext.getMonitorContractClientEVM().getContractAddress(),
                    VMTypeEnum.EVM));
            submittedTransactions += 4;

            assertContractVersionsAndBindings(client, mychainContext);
            Assert.assertEquals(BigInteger.valueOf(AbstractMonitorMessage.MONITOR_OPEN),
                    localCallUint(client, mychainContext.getMonitorContractClientEVM().getContractAddress(),
                            "getMonitorControl()"));
            Assert.assertTrue(service.hasPTCTrustRoot(fixture.ptcOwnerOid));
            Assert.assertTrue(service.hasTpBta(fixture.lane, fixture.tpbta.getTpbtaVersion()));

            byte[] validPayload = "valid-nonword-17".getBytes(StandardCharsets.UTF_8);
            TransactionReceipt validReceipt = submitAndWait(
                    client,
                    mychainContext.getAmContractClientEVM().getContractAddress(),
                    makeRelayerPackage(fixture, receiver.getContractId(), 1, -1,
                            AtomicFlagEnum.NONE_ATOMIC, validPayload, AbstractMonitorMessage.MONITOR_OPEN,
                            ProofMode.VALID));
            submittedTransactions++;
            Assert.assertEquals(ErrorCode.SUCCESS.getErrorCode(), validReceipt.getResult());
            Assert.assertEquals(new String(validPayload, StandardCharsets.UTF_8),
                    receiver.getLastMsg(SDPMsgTypeEnum.UNORDERED));

            byte[] rollbackPayload = "rollback-nonword-19".getBytes(StandardCharsets.UTF_8);
            TransactionReceipt rollbackReceipt = submitAndWait(
                    client,
                    mychainContext.getAmContractClientEVM().getContractAddress(),
                    makeRelayerPackage(fixture, receiver.getContractId(), 1, 0,
                            AtomicFlagEnum.NONE_ATOMIC, rollbackPayload, AbstractMonitorMessage.MONITOR_OPEN,
                            ProofMode.EMPTY_MONITOR));
            submittedTransactions++;
            Assert.assertEquals(ErrorCode.SUCCESS.getErrorCode(), rollbackReceipt.getResult());
            Assert.assertNotEquals(new String(rollbackPayload, StandardCharsets.UTF_8),
                    receiver.getLastMsg(SDPMsgTypeEnum.ORDERED));
            assertV1Rollback(
                    extractOutboundAuthMessages(rollbackReceipt, client, mychainContext),
                    sourceDomain,
                    rollbackPayload);

            byte[] v2Payload = "v2-atomic-monitor-fail".getBytes(StandardCharsets.UTF_8);
            TransactionReceipt v2Receipt = submitAndWait(
                    client,
                    mychainContext.getAmContractClientEVM().getContractAddress(),
                    makeRelayerPackage(fixture, receiver.getContractId(), 2, -1,
                            AtomicFlagEnum.ATOMIC_REQUEST, v2Payload, AbstractMonitorMessage.MONITOR_OPEN,
                            ProofMode.EMPTY_MONITOR));
            submittedTransactions++;
            Assert.assertEquals(ErrorCode.SUCCESS.getErrorCode(), v2Receipt.getResult());
            assertV2ErrorAck(extractOutboundAuthMessages(v2Receipt, client, mychainContext), sourceDomain);

            // The remaining matrix runs as Mychain local calls. It executes the
            // real contracts, including nested PTC/SDP/Monitor calls, but does
            // not create blocks or persist state on the shared chain.
            TransactionReceipt v3Local = simulateRelayerCall(
                    client,
                    mychainContext.getAmContractClientEVM().getContractAddress(),
                    makeRelayerPackage(fixture, receiver.getContractId(), 3, -1,
                            AtomicFlagEnum.ATOMIC_REQUEST, "v3-fail".getBytes(StandardCharsets.UTF_8),
                            AbstractMonitorMessage.MONITOR_OPEN, ProofMode.EMPTY_MONITOR));
            assertSuccess(v3Local, "V3 empty monitor proof should become ACK_ERROR");
            assertV3ErrorAck(extractOutboundAuthMessages(v3Local, client, mychainContext), sourceDomain);

            TransactionReceipt closeLocal = simulateRelayerCall(
                    client,
                    mychainContext.getAmContractClientEVM().getContractAddress(),
                    makeRelayerPackage(fixture, receiver.getContractId(), 1, -1,
                            AtomicFlagEnum.NONE_ATOMIC, "close-bypass".getBytes(StandardCharsets.UTF_8),
                            AbstractMonitorMessage.MONITOR_CLOSE, ProofMode.EMPTY_MONITOR));
            assertSuccess(closeLocal, "MONITOR_CLOSE must not require a valid monitor signature");

            assertReverted(simulateRelayerCall(
                    client,
                    mychainContext.getAmContractClientEVM().getContractAddress(),
                    makeRelayerPackage(fixture, receiver.getContractId(), 1, -1,
                            AtomicFlagEnum.NONE_ATOMIC, "missing-monitor".getBytes(StandardCharsets.UTF_8),
                            AbstractMonitorMessage.MONITOR_OPEN, ProofMode.OMIT_MONITOR)),
                    "required monitor proof must be present");
            assertReverted(simulateRelayerCall(
                    client,
                    mychainContext.getAmContractClientEVM().getContractAddress(),
                    makeRelayerPackage(fixture, receiver.getContractId(), 1, -1,
                            AtomicFlagEnum.NONE_ATOMIC, "bad-normal".getBytes(StandardCharsets.UTF_8),
                            AbstractMonitorMessage.MONITOR_OPEN, ProofMode.BAD_NORMAL)),
                    "normal committee threshold must still be verified");

            assertMonitorControlLocalMatrix(client, mychainContext.getMonitorContractClientEVM().getContractAddress());
            Assert.assertTrue("unexpected transaction count: " + submittedTransactions,
                    submittedTransactions <= MAX_SHARED_CHAIN_TRANSACTIONS);
            LOGGER.info("MYCHAIN_MONITOR_TEST_SUBMITTED_TRANSACTIONS={}", submittedTransactions);
            LOGGER.info("MYCHAIN_MONITOR_TEST_AM={}", mychainContext.getAmContractClientEVM().getContractAddress());
            LOGGER.info("MYCHAIN_MONITOR_TEST_SDP={}", mychainContext.getSdpContractClientEVM().getContractAddress());
            LOGGER.info("MYCHAIN_MONITOR_TEST_PTC={}", mychainContext.getPtcContractEvm().getContractAddress());
            LOGGER.info("MYCHAIN_MONITOR_TEST_MONITOR={}", mychainContext.getMonitorContractClientEVM().getContractAddress());
            LOGGER.info("MYCHAIN_MONITOR_TEST_MONITOR_VERIFIER={}",
                    mychainContext.getMonitorVerifierContractEVM().getContractAddress());
        } finally {
            if (client != null) {
                service.shutdown();
            }
        }
    }

    private static void requireExplicitOptIn() {
        Assume.assumeTrue(
                "set -D" + ENABLE_PROPERTY + "=true to run this shared-chain integration test",
                Boolean.getBoolean(ENABLE_PROPERTY));
        Assert.assertEquals(
                "the expected primary must be explicitly pinned before writing to the shared chain",
                EXPECTED_PRIMARY,
                requiredProperty(EXPECTED_PRIMARY_PROPERTY));
    }

    private static void assertSafeFreshConfiguration(Mychain020Config config) {
        Assert.assertEquals(EXPECTED_PRIMARY, config.getMychainPrimary());
        Assert.assertFalse("TEE configuration is not supported", StrUtil.isNotEmpty(config.getMychainTeePublicKey()));
        Assert.assertTrue("AM address must be empty", StrUtil.isEmpty(config.getAmContractName()));
        Assert.assertTrue("SDP address must be empty", StrUtil.isEmpty(config.getSdpContractName()));
        Assert.assertTrue("PTC address must be empty", StrUtil.isEmpty(config.getPtcContractName()));
        Assert.assertTrue("Monitor address must be empty", StrUtil.isEmpty(config.getMonitorContractName()));
        Assert.assertTrue("MonitorVerifier address must be empty", StrUtil.isEmpty(config.getMonitorVerifierContractName()));
    }

    private static void applyLoopbackConnectionOverride(Mychain020Config config) {
        String connectionPrimary = System.getProperty(CONNECTION_PRIMARY_PROPERTY);
        if (StrUtil.isEmpty(connectionPrimary)) {
            return;
        }

        String[] endpoint = connectionPrimary.trim().split("\\s+");
        Assert.assertEquals(
                "connection override must use '<loopback-host> <port>'",
                2,
                endpoint.length);
        Assert.assertTrue(
                "connection override is restricted to an SSH tunnel on loopback",
                "127.0.0.1".equals(endpoint[0]) || "localhost".equals(endpoint[0]));
        int port = Integer.parseInt(endpoint[1]);
        Assert.assertTrue("connection override port is invalid", port > 0 && port <= 65535);

        LOGGER.info("MYCHAIN_MONITOR_TEST_CONNECTION_PRIMARY={}", connectionPrimary);
        config.setMychainPrimary(connectionPrimary.trim());
    }

    private static void applyResumeConfiguration(Mychain020Config config, ResumeContracts resume) {
        config.setAmContractName("{\"evm\":\"" + resume.am + "\"}");
        config.setSdpContractName("{\"evm\":\"" + resume.sdp + "\"}");
        config.setPtcContractName("{\"evm\":\"" + resume.ptc + "\"}");
        config.setMonitorContractName(resume.monitor);
        config.setMonitorVerifierContractName(resume.monitorVerifier);
        LOGGER.info("MYCHAIN_MONITOR_TEST_RESUME_RUN_ID={}", resume.runId);
    }

    private static void assertGeneratedContractName(String property, String value, String prefix) {
        Assert.assertTrue(property + " must start with " + prefix, value.startsWith(prefix));
        UUID.fromString(value.substring(prefix.length()));
    }

    private static class ResumeContracts {
        private final String runId;
        private final String am;
        private final String sdp;
        private final String ptc;
        private final String monitor;
        private final String monitorVerifier;
        private final String receiver;
        private final int submittedTransactions;

        private ResumeContracts(
                String runId,
                String am,
                String sdp,
                String ptc,
                String monitor,
                String monitorVerifier,
                String receiver,
                int submittedTransactions) {
            this.runId = runId;
            this.am = am;
            this.sdp = sdp;
            this.ptc = ptc;
            this.monitor = monitor;
            this.monitorVerifier = monitorVerifier;
            this.receiver = receiver;
            this.submittedTransactions = submittedTransactions;
        }

        private static ResumeContracts fromSystemProperties() {
            String runId = System.getProperty(RESUME_RUN_ID_PROPERTY);
            if (StrUtil.isEmpty(runId)) {
                Assert.assertNull("resume AM requires resumeRunId", System.getProperty(RESUME_AM_PROPERTY));
                Assert.assertNull("resume SDP requires resumeRunId", System.getProperty(RESUME_SDP_PROPERTY));
                Assert.assertNull("resume PTC requires resumeRunId", System.getProperty(RESUME_PTC_PROPERTY));
                Assert.assertNull("resume Monitor requires resumeRunId", System.getProperty(RESUME_MONITOR_PROPERTY));
                Assert.assertNull("resume MonitorVerifier requires resumeRunId",
                        System.getProperty(RESUME_MONITOR_VERIFIER_PROPERTY));
                Assert.assertNull("resume receiver requires resumeRunId",
                        System.getProperty(RESUME_RECEIVER_PROPERTY));
                Assert.assertNull("resume transaction count requires resumeRunId",
                        System.getProperty(RESUME_SUBMITTED_TRANSACTIONS_PROPERTY));
                return null;
            }

            UUID.fromString(runId);
            ResumeContracts resume = new ResumeContracts(
                    runId,
                    requiredProperty(RESUME_AM_PROPERTY),
                    requiredProperty(RESUME_SDP_PROPERTY),
                    requiredProperty(RESUME_PTC_PROPERTY),
                    requiredProperty(RESUME_MONITOR_PROPERTY),
                    requiredProperty(RESUME_MONITOR_VERIFIER_PROPERTY),
                    System.getProperty(RESUME_RECEIVER_PROPERTY),
                    Integer.parseInt(requiredProperty(RESUME_SUBMITTED_TRANSACTIONS_PROPERTY)));
            assertGeneratedContractName(RESUME_AM_PROPERTY, resume.am, "AM_EVM_CONTRACT_");
            assertGeneratedContractName(RESUME_SDP_PROPERTY, resume.sdp, "SDP_EVM_CONTRACT_");
            assertGeneratedContractName(RESUME_PTC_PROPERTY, resume.ptc, "PTC_HUB_EVM_CONTRACT_");
            assertGeneratedContractName(RESUME_MONITOR_PROPERTY, resume.monitor, "MONITOR_EVM_CONTRACT_");
            assertGeneratedContractName(
                    RESUME_MONITOR_VERIFIER_PROPERTY,
                    resume.monitorVerifier,
                    "MONITOR_VERIFIER_EVM_CONTRACT_");
            if (StrUtil.isNotEmpty(resume.receiver)) {
                assertGeneratedContractName(RESUME_RECEIVER_PROPERTY, resume.receiver, "ReceiverEvm_");
            }
            Assert.assertTrue("resume transaction count must include the 17 setup writes",
                    resume.submittedTransactions >= 17);
            Assert.assertTrue("resume transaction count already exceeds the shared-chain limit",
                    resume.submittedTransactions <= MAX_SHARED_CHAIN_TRANSACTIONS);
            return resume;
        }
    }

    private static boolean ensureTestBcdnsRoot(
            Mychain020Client client,
            Mychain020BBCContext context) {
        byte[] expected = CrossChainCertificateUtil
                .readCrossChainCertificateFromPem(TEST_BCDNS_ROOT_CERT.getBytes(StandardCharsets.UTF_8))
                .encode();
        byte[] current = localCallBytes(
                client,
                context.getPtcContractEvm().getContractAddress(),
                "bcdnsCertMap(string)",
                "root");
        if (Arrays.equals(expected, current)) {
            return false;
        }
        Assert.assertTrue(
                "failed to reconcile the isolated PTC Hub with the fixture BCDNS root",
                context.getPtcContractEvm().reconcileRootBcdnsCert(TEST_BCDNS_ROOT_CERT));
        Assert.assertArrayEquals(
                "fixture BCDNS root was not persisted",
                expected,
                localCallBytes(
                        client,
                        context.getPtcContractEvm().getContractAddress(),
                        "bcdnsCertMap(string)",
                        "root"));
        return true;
    }

    private static void assertContractVersionsAndBindings(
            Mychain020Client client,
            Mychain020BBCContext context) {
        String sdp = context.getSdpContractClientEVM().getContractAddress();
        String ptc = context.getPtcContractEvm().getContractAddress();
        String monitor = context.getMonitorContractClientEVM().getContractAddress();
        String monitorVerifier = context.getMonitorVerifierContractEVM().getContractAddress();
        Identity committeeVerifier = context.getPtcContractEvm().getCommitteeVerifierIdentity();

        Assert.assertEquals(BigInteger.valueOf(2), localCallUint(client, monitorVerifier, "getImplementationVersion()"));
        Assert.assertEquals(BigInteger.valueOf(2), localCallUint(client, committeeVerifier, "getImplementationVersion()"));
        Assert.assertEquals(BigInteger.valueOf(3), localCallUint(client, ptc, "getImplementationVersion()"));
        Assert.assertEquals(BigInteger.valueOf(7), localCallUint(client, monitor, "getImplementationVersion()"));
        Assert.assertEquals(BigInteger.valueOf(4), localCallUint(client, sdp, "getMonitorRoutingVersion()"));

        Assert.assertEquals(identityOf(client, sdp), localCallIdentity(client, monitor, "getProtocol()"));
        Assert.assertEquals(identityOf(client, monitorVerifier), localCallIdentity(client, monitor, "getMonitorVerifier()"));
        Assert.assertEquals(identityOf(client, monitor), localCallIdentity(client, sdp, "monitorAddress()"));
        Assert.assertEquals(identityOf(client, ptc), localCallIdentity(client, monitorVerifier, "getPtcHubAddress()"));
        Assert.assertEquals(identityOf(client, monitorVerifier), localCallIdentity(client, ptc, "getMonitorVerifier()"));
    }

    private static ProofFixture createProofFixture(String sourceDomain, String localDomain) throws Exception {
        Mychain020BBCServiceTest.NODE_PTC_CERT.validate(
                CrossChainCertificateUtil
                        .readCrossChainCertificateFromPem(TEST_BCDNS_ROOT_CERT.getBytes(StandardCharsets.UTF_8))
                        .getCredentialSubjectInstance());
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("EC", BouncyCastleProvider.PROVIDER_NAME);
        keyPairGenerator.initialize(new ECGenParameterSpec("secp256k1"));
        KeyPair normalNode = keyPairGenerator.generateKeyPair();
        KeyPair monitorNode = keyPairGenerator.generateKeyPair();

        NodeEndorseInfo normalInfo = new NodeEndorseInfo();
        normalInfo.setNodeId(NORMAL_NODE_ID);
        normalInfo.setRequired(false);
        normalInfo.setPublicKey(new NodePublicKeyEntry("normal-key", normalNode.getPublic().getEncoded()));

        NodeEndorseInfo monitorInfo = new NodeEndorseInfo();
        monitorInfo.setNodeId(MONITOR_NODE_ID);
        monitorInfo.setRequired(true);
        monitorInfo.setPublicKey(new NodePublicKeyEntry("monitor-key", monitorNode.getPublic().getEncoded()));

        OptionalEndorsePolicy policy = new OptionalEndorsePolicy();
        policy.setThreshold(new OptionalEndorsePolicy.Threshold(
                OptionalEndorsePolicy.OperatorEnum.GREATER_OR_EQUALS,
                1));

        CrossChainLane lane = new CrossChainLane(
                new CrossChainDomain(sourceDomain),
                new CrossChainDomain(localDomain));
        ThirdPartyBlockchainTrustAnchorV1 tpbta = new ThirdPartyBlockchainTrustAnchorV1(
                1,
                BigInteger.ONE,
                (PTCCredentialSubject) Mychain020BBCServiceTest.NODE_PTC_CERT.getCredentialSubjectInstance(),
                lane,
                1,
                HashAlgoEnum.KECCAK_256,
                new CommitteeEndorseRoot(COMMITTEE_ID, policy, ListUtil.toList(normalInfo, monitorInfo)).encode(),
                null);
        tpbta.setEndorseProof(new CommitteeEndorseProof(
                COMMITTEE_ID,
                ListUtil.toList(nodeProof(NORMAL_NODE_ID, normalNode.getPrivate(), tpbta.getEncodedToSign())))
                .encode());
        Assert.assertEquals(ThirdPartyBlockchainTrustAnchor.TypeEnum.CHANNEL_LEVEL, tpbta.type());

        CommitteeVerifyAnchor verifyAnchor = new CommitteeVerifyAnchor(COMMITTEE_ID);
        verifyAnchor.addNode(NORMAL_NODE_ID, "normal-key", normalNode.getPublic());
        CommitteeNetworkInfo networkInfo = new CommitteeNetworkInfo(COMMITTEE_ID);
        networkInfo.addEndpoint(NORMAL_NODE_ID, "grpcs://127.0.0.1:1", "");
        networkInfo.addEndpoint(MONITOR_NODE_ID, "grpcs://127.0.0.1:2", "");

        ObjectIdentity ptcOwnerOid = new X509PubkeyInfoObjectIdentity(
                Mychain020BBCServiceTest.RAW_NODE_PTC_PUBLIC_KEY);
        PTCTrustRoot trustRoot = PTCTrustRoot.builder()
                .ptcCrossChainCert(Mychain020BBCServiceTest.NODE_PTC_CERT)
                .networkInfo(networkInfo.encode())
                .issuerBcdnsDomainSpace(new CrossChainDomain(""))
                .sigAlgo(SignAlgoEnum.KECCAK256_WITH_SECP256K1)
                .verifyAnchorMap(MapUtil.builder(
                        BigInteger.ONE,
                        new PTCVerifyAnchor(BigInteger.ONE, verifyAnchor.encode())).build())
                .build();
        trustRoot.sign(Mychain020BBCServiceTest.NODE_PTC_PRIVATE_KEY);

        return new ProofFixture(
                lane,
                ptcOwnerOid,
                tpbta,
                trustRoot,
                normalNode.getPrivate(),
                monitorNode.getPrivate());
    }

    private static byte[] makeRelayerPackage(
            ProofFixture fixture,
            Identity receiver,
            int sdpVersion,
            int sequence,
            AtomicFlagEnum atomicFlag,
            byte[] businessPayload,
            int monitorType,
            ProofMode proofMode) throws Exception {
        byte[] monitorPayload = MonitorMessageFactory.createMonitorMessage(
                1,
                monitorType,
                "",
                businessPayload).encode();

        ISDPMessage sdpMessage;
        if (sdpVersion == 3) {
            sdpMessage = SDPMessageFactory.createSDPMessage(
                    3,
                    RandomUtil.randomBytes(32),
                    fixture.lane.getReceiverDomain().getDomain(),
                    receiver.getData(),
                    atomicFlag,
                    TimeoutMeasureEnum.NO_TIMEOUT,
                    BigInteger.ZERO,
                    Math.abs(RandomUtil.randomLong()),
                    sequence,
                    monitorPayload,
                    null);
        } else if (sdpVersion == 2) {
            sdpMessage = SDPMessageFactory.createSDPMessage(
                    2,
                    RandomUtil.randomBytes(32),
                    fixture.lane.getReceiverDomain().getDomain(),
                    receiver.getData(),
                    atomicFlag,
                    Math.abs(RandomUtil.randomLong()),
                    sequence,
                    monitorPayload,
                    null);
        } else {
            sdpMessage = SDPMessageFactory.createSDPMessage(
                    1,
                    new byte[32],
                    fixture.lane.getReceiverDomain().getDomain(),
                    receiver.getData(),
                    sequence,
                    monitorPayload);
        }

        IAuthMessage authMessage = AuthMessageFactory.createAuthMessage(
                1,
                RandomUtil.randomBytes(32),
                0,
                sdpMessage.encode());
        ThirdPartyProof proof = ThirdPartyProof.create(
                fixture.tpbta.getTpbtaVersion(),
                authMessage.encode(),
                fixture.lane);

        List<CommitteeNodeProof> signatures = new ArrayList<CommitteeNodeProof>();
        signatures.add(proofMode == ProofMode.BAD_NORMAL
                ? new CommitteeNodeProof(NORMAL_NODE_ID, SignAlgoEnum.KECCAK256_WITH_SECP256K1, new byte[65])
                : nodeProof(NORMAL_NODE_ID, fixture.normalPrivateKey, proof.getEncodedToSign()));
        if (proofMode != ProofMode.OMIT_MONITOR) {
            signatures.add(proofMode == ProofMode.EMPTY_MONITOR
                    ? new CommitteeNodeProof(MONITOR_NODE_ID, SignAlgoEnum.KECCAK256_WITH_SECP256K1, new byte[65])
                    : nodeProof(MONITOR_NODE_ID, fixture.monitorPrivateKey, proof.getEncodedToSign()));
        }
        proof.setRawProof(new CommitteeEndorseProof(COMMITTEE_ID, signatures).encode());

        byte[] rawProof = proof.encode();
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        stream.write(new byte[]{0, 0, 0, 0});
        stream.write((rawProof.length >>> 24) & 0xff);
        stream.write((rawProof.length >>> 16) & 0xff);
        stream.write((rawProof.length >>> 8) & 0xff);
        stream.write(rawProof.length & 0xff);
        stream.write(rawProof);
        return stream.toByteArray();
    }

    private static CommitteeNodeProof nodeProof(String nodeId, PrivateKey privateKey, byte[] data) {
        return new CommitteeNodeProof(
                nodeId,
                SignAlgoEnum.KECCAK256_WITH_SECP256K1,
                SignAlgoEnum.KECCAK256_WITH_SECP256K1.getSigner().sign(privateKey, data));
    }

    private static TransactionReceipt submitAndWait(Mychain020Client client, String amContract, byte[] pkg)
            throws InterruptedException {
        EVMParameter parameter = new EVMParameter(AMContractClientEVM.AM_RECV_PKG_METHOD);
        parameter.addBytes(pkg);
        SendResponseResult result = client.callContract(amContract, parameter, true);
        Assert.assertNotNull(result);
        Assert.assertTrue("transaction submission failed: " + result.getErrorMessage(), result.isConfirmed());
        Assert.assertTrue("contract call failed: " + result.getErrorMessage(), result.isSuccess());
        LOGGER.info("MYCHAIN_MONITOR_TEST_TX={}", result.getTxId());

        for (int attempt = 0; attempt < 10; attempt++) {
            QueryTransactionReceiptResponse response = client.getTxReceiptByTxhash(result.getTxId());
            if (response != null && response.isSuccess() && response.getTransactionReceipt() != null) {
                return response.getTransactionReceipt();
            }
            Thread.sleep(500L);
        }
        Assert.fail("transaction receipt remained unavailable; not resubmitting: " + result.getTxId());
        return null;
    }

    private static TransactionReceipt simulateRelayerCall(Mychain020Client client, String amContract, byte[] pkg) {
        EVMParameter parameter = new EVMParameter(AMContractClientEVM.AM_RECV_PKG_METHOD);
        parameter.addBytes(pkg);
        return client.localCallContract(amContract, parameter);
    }

    private static List<byte[]> extractOutboundAuthMessages(
            TransactionReceipt receipt,
            Mychain020Client client,
            Mychain020BBCContext context) {
        List<byte[]> messages = new ArrayList<byte[]>();
        String amIdentity = identityOf(client, context.getAmContractClientEVM().getContractAddress());
        for (LogEntry entry : receipt.getLogs()) {
            if (entry.getTo() == null
                    || !amIdentity.equals(entry.getTo().hexStrValue())
                    || entry.getTopics() == null
                    || entry.getTopics().isEmpty()
                    || !AMContractClientEVM.AM_MSG_SEND_SIGN_HEX.equals(entry.getTopics().get(0))) {
                continue;
            }
            messages.add(new EVMOutput(Hex.toHexString(entry.getLogData())).getBytes());
        }
        return messages;
    }

    private static void assertV1Rollback(List<byte[]> messages, String sourceDomain, byte[] originalPayload) {
        for (byte[] rawAuth : messages) {
            ISDPMessage sdp = SDPMessageFactory.createSDPMessage(
                    AuthMessageFactory.createAuthMessage(rawAuth).getPayload());
            if (sdp.getVersion() != 1 || !sourceDomain.equals(sdp.getTargetDomain().getDomain())) {
                continue;
            }
            IMonitorMessage monitor = MonitorMessageFactory.createMonitorMessage(sdp.getPayload());
            Assert.assertEquals(AbstractMonitorMessage.MONITOR_ROLLBACK, monitor.getMonitorType());
            Assert.assertEquals("MidMonitoring not pass", monitor.getMonitorMsg());
            Assert.assertArrayEquals(originalPayload, monitor.getPayload());
            return;
        }
        Assert.fail("V1 monitor rollback message was not emitted");
    }

    private static void assertV2ErrorAck(List<byte[]> messages, String sourceDomain) {
        for (byte[] rawAuth : messages) {
            ISDPMessage decoded = SDPMessageFactory.createSDPMessage(
                    AuthMessageFactory.createAuthMessage(rawAuth).getPayload());
            if (decoded instanceof SDPMessageV2 && sourceDomain.equals(decoded.getTargetDomain().getDomain())) {
                SDPMessageV2 v2 = (SDPMessageV2) decoded;
                Assert.assertEquals(AtomicFlagEnum.ACK_ERROR, v2.getAtomicFlag());
                Assert.assertTrue(v2.getErrorMsg().contains("Monitor: mid monitoring not pass"));
                return;
            }
        }
        Assert.fail("V2 ACK_ERROR message was not emitted");
    }

    private static void assertV3ErrorAck(List<byte[]> messages, String sourceDomain) {
        for (byte[] rawAuth : messages) {
            ISDPMessage decoded = SDPMessageFactory.createSDPMessage(
                    AuthMessageFactory.createAuthMessage(rawAuth).getPayload());
            if (decoded instanceof SDPMessageV3 && sourceDomain.equals(decoded.getTargetDomain().getDomain())) {
                SDPMessageV3 v3 = (SDPMessageV3) decoded;
                Assert.assertEquals(AtomicFlagEnum.ACK_ERROR, v3.getAtomicFlag());
                Assert.assertTrue(v3.getErrorMsg().contains("Monitor: mid monitoring not pass"));
                return;
            }
        }
        Assert.fail("V3 ACK_ERROR message was not emitted");
    }

    private static void assertMonitorControlLocalMatrix(Mychain020Client client, String monitorContract) {
        for (int control = AbstractMonitorMessage.MONITOR_CLOSE;
             control <= AbstractMonitorMessage.MONITOR_ROLLBACK;
             control++) {
            EVMParameter parameter = new EVMParameter("setMonitorControl(uint32)");
            parameter.addUint(BigInteger.valueOf(control));
            assertSuccess(client.localCallContract(monitorContract, parameter), "valid monitor control " + control);
        }
        EVMParameter invalid = new EVMParameter("setMonitorControl(uint32)");
        invalid.addUint(BigInteger.valueOf(4));
        assertReverted(client.localCallContract(monitorContract, invalid), "invalid monitor control must revert");
    }

    private static void assertSuccess(TransactionReceipt receipt, String message) {
        Assert.assertNotNull(message, receipt);
        Assert.assertEquals(message, ErrorCode.SUCCESS.getErrorCode(), receipt.getResult());
    }

    private static void assertReverted(TransactionReceipt receipt, String message) {
        Assert.assertNotNull(message, receipt);
        Assert.assertNotEquals(message, ErrorCode.SUCCESS.getErrorCode(), receipt.getResult());
    }

    private static BigInteger localCallUint(Mychain020Client client, String contract, String method) {
        return localCallUint(client, Utils.getIdentityByName(contract, client.getConfig().getMychainHashType()), method);
    }

    private static BigInteger localCallUint(Mychain020Client client, Identity contract, String method) {
        TransactionReceipt receipt = client.localCallContract(contract, new EVMParameter(method));
        assertSuccess(receipt, "local uint call failed: " + method);
        return new EVMOutput(Hex.toHexString(receipt.getOutput())).getUint();
    }

    private static String localCallIdentity(Mychain020Client client, String contract, String method) {
        TransactionReceipt receipt = client.localCallContract(contract, new EVMParameter(method));
        assertSuccess(receipt, "local identity call failed: " + method);
        return new EVMOutput(Hex.toHexString(receipt.getOutput())).getIdentity().hexStrValue();
    }

    private static byte[] localCallBytes(
            Mychain020Client client,
            String contract,
            String method,
            String argument) {
        EVMParameter parameter = new EVMParameter(method);
        parameter.addString(argument);
        TransactionReceipt receipt = client.localCallContract(contract, parameter);
        assertSuccess(receipt, "local bytes call failed: " + method);
        return new EVMOutput(Hex.toHexString(receipt.getOutput())).getBytes();
    }

    private static String identityOf(Mychain020Client client, String contract) {
        return Utils.getIdentityByName(contract, client.getConfig().getMychainHashType()).hexStrValue();
    }

    private static String requiredProperty(String name) {
        String value = System.getProperty(name);
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("missing system property: " + name);
        }
        return value.trim();
    }

    private static final class ProofFixture {
        private final CrossChainLane lane;
        private final ObjectIdentity ptcOwnerOid;
        private final ThirdPartyBlockchainTrustAnchorV1 tpbta;
        private final PTCTrustRoot ptcTrustRoot;
        private final PrivateKey normalPrivateKey;
        private final PrivateKey monitorPrivateKey;

        private ProofFixture(
                CrossChainLane lane,
                ObjectIdentity ptcOwnerOid,
                ThirdPartyBlockchainTrustAnchorV1 tpbta,
                PTCTrustRoot ptcTrustRoot,
                PrivateKey normalPrivateKey,
                PrivateKey monitorPrivateKey) {
            this.lane = lane;
            this.ptcOwnerOid = ptcOwnerOid;
            this.tpbta = tpbta;
            this.ptcTrustRoot = ptcTrustRoot;
            this.normalPrivateKey = normalPrivateKey;
            this.monitorPrivateKey = monitorPrivateKey;
        }
    }
}

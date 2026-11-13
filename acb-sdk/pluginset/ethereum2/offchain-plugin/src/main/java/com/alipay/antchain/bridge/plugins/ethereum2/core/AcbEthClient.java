package com.alipay.antchain.bridge.plugins.ethereum2.core;

import java.io.IOException;
import java.math.BigInteger;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import cn.hutool.core.collection.ListUtil;
import cn.hutool.core.util.HexUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson.JSON;
import com.alipay.antchain.bridge.commons.bcdns.AbstractCrossChainCertificate;
import com.alipay.antchain.bridge.commons.core.base.*;
import com.alipay.antchain.bridge.commons.core.ptc.PTCTrustRoot;
import com.alipay.antchain.bridge.commons.core.ptc.PTCTypeEnum;
import com.alipay.antchain.bridge.commons.core.ptc.PTCVerifyAnchor;
import com.alipay.antchain.bridge.commons.core.ptc.ThirdPartyBlockchainTrustAnchor;
import com.alipay.antchain.bridge.plugins.ethereum2.abi.*;
import com.alipay.antchain.bridge.plugins.ethereum2.conf.EthereumConfig;
import com.alipay.antchain.bridge.plugins.ethereum2.core.eth.*;
import com.alipay.antchain.bridge.plugins.ethereum2.core.eth.beacon.AcbBeaconClient;
import com.alipay.antchain.bridge.plugins.ethereum2.core.eth.beacon.BeaconNodeApi;
import com.alipay.antchain.bridge.plugins.ethereum2.helper.*;
import com.alipay.antchain.bridge.plugins.ethereum2.helper.model.GasPriceProviderConfig;
import com.alipay.antchain.bridge.plugins.ethereum2.kms.service.TxKMSSignService;
import com.aliyun.kms20160120.Client;
import lombok.Getter;
import lombok.SneakyThrows;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.datatypes.Address;
import org.slf4j.Logger;
import org.slf4j.helpers.NOPLogger;
import org.web3j.abi.DefaultFunctionEncoder;
import org.web3j.abi.EventEncoder;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.datatypes.DynamicBytes;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.Utf8String;
import org.web3j.abi.datatypes.generated.Uint32;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.DefaultBlockParameterNumber;
import org.web3j.protocol.core.methods.request.EthFilter;
import org.web3j.protocol.core.methods.response.EthLog;
import org.web3j.protocol.core.methods.response.*;
import org.web3j.protocol.http.HttpService;
import org.web3j.tx.RawTransactionManager;
import org.web3j.tx.gas.DefaultGasProvider;
import org.web3j.utils.Numeric;
import tech.pegasys.teku.infrastructure.ssz.tree.MerkleUtil;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlockHeader;
import tech.pegasys.teku.spec.datastructures.blocks.blockbody.common.BlockBodyFields;

public class AcbEthClient {

    private static final String SEND_AUTH_MESSAGE_LOG_TOPIC = "0x79b7516b1b7a6a39fb4b7b22e8667cd3744e5c27425292f8a9f49d1042c0c651";

    private static final LogsBloomFilter SEND_AUTH_MESSAGE_LOG_TOPIC_FILTER = LogsBloomFilter.builder()
            .insertBytes(EthLogTopic.create(Bytes32.fromHexString(SEND_AUTH_MESSAGE_LOG_TOPIC))).build();

    private static final String SDP_NONCE_HAS_BEEN_PROCESSED_REVERT_REASON = "SDPMsg: nonce has been processed";

    @Getter
    private final Web3j web3j;

    @Getter
    private Credentials credentials;

    @Getter
    private final RawTransactionManager rawTransactionManager;

    private TxKMSSignService txKMSSignService;

    @Getter
    private final IGasPriceProvider contractGasPriceProvider;

    private final BeaconNodeApi beaconNodeClient;

    private final Logger bbcLogger;

    private final EthereumConfig config;

    public AcbEthClient(EthereumConfig config, Logger bbcLogger) {
        this.bbcLogger = bbcLogger;
        this.config = config;

        // 2. Connect to the Ethereum network
        BigInteger chainId;
        try {
            web3j = Web3j.build(new HttpService(config.getUrl()));
            chainId = web3j.ethChainId().send().getChainId();
        } catch (Exception e) {
            throw new RuntimeException(String.format("failed to connect ethereum (url: %s)", config.getUrl()), e);
        }

        // 3. Connect to the specified gas price supplier
        try {
            GasPriceProviderConfig gasPriceProviderConfig = new GasPriceProviderConfig();
            gasPriceProviderConfig.setGasPriceProviderSupplier(config.getGasPriceProviderSupplier());
            gasPriceProviderConfig.setGasProviderUrl(config.getGasProviderUrl());
            gasPriceProviderConfig.setApiKey(config.getGasProviderApiKey());
            gasPriceProviderConfig.setGasUpdateInterval(config.getGasUpdateInterval());
            this.contractGasPriceProvider = createGasPriceProvider(gasPriceProviderConfig);
        } catch (Exception e) {
            throw new RuntimeException("failed to create gas price provider", e);
        }

        // 4. Connect to the specified wallet account
        if (!config.isKmsService()) {
            this.credentials = Credentials.create(config.getPrivateKey());
        }

        // 5. Create tx manager with web3j and credentials or kmsService
        try {
            this.rawTransactionManager = createTransactionManager(chainId);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        beaconNodeClient = new AcbBeaconClient(config, this.bbcLogger);
    }

    @SneakyThrows
    public TransactionReceipt queryTransactionReceipt(String txHash) {
        return this.web3j.ethGetTransactionReceipt(txHash).send().getTransactionReceipt().orElse(null);
    }

    public BigInteger queryLatestSlot() {
        return beaconNodeClient.getLatestFinalizedSlot();
    }

    public BigInteger queryLatestBlockNumber() {
        BigInteger l;
        try {
            l = web3j.ethGetBlockByNumber(config.getBlockHeightPolicy().getDefaultBlockParameterName(), false)
                    .send()
                    .getBlock()
                    .getNumber();
        } catch (IOException e) {
            throw new RuntimeException("failed to query latest block number", e);
        }
        return l;
    }

    public ProxyAdmin deployProxyAdmin() {
        getBbcLogger().info("deploy proxy admin contract now!");
        try {
            return ProxyAdmin.deploy(
                    web3j,
                    rawTransactionManager,
                    new AcbGasProvider(
                            this.contractGasPriceProvider,
                            createDeployGasLimitProvider(ProxyAdmin.BINARY)
                    )
            ).send();
        } catch (Exception e) {
            throw new RuntimeException("failed to deploy proxy admin", e);
        }
    }

    public String deployAuthMsg() {
        AuthMsg authMsg;
        try {
            authMsg = AuthMsg.deploy(
                    web3j,
                    rawTransactionManager,
                    new AcbGasProvider(
                            this.contractGasPriceProvider,
                            createDeployGasLimitProvider(AuthMsg.BINARY)
                    )
            ).send();
        } catch (Exception e) {
            throw new RuntimeException("failed to deploy authMsg", e);
        }

        if (this.config.isUpgradableContracts()) {
            TransparentUpgradeableProxy proxy;
            try {
                proxy = TransparentUpgradeableProxy.deploy(
                        web3j,
                        rawTransactionManager,
                        new AcbGasProvider(
                                this.contractGasPriceProvider,
                                createDeployGasLimitProvider(
                                        TransparentUpgradeableProxy.BINARY +
                                        FunctionEncoder.encodeConstructor(ListUtil.toList(
                                                new org.web3j.abi.datatypes.Address(authMsg.getContractAddress()),
                                                new org.web3j.abi.datatypes.Address(this.config.getProxyAdmin()),
                                                new DynamicBytes(HexUtil.decodeHex("e1c7392a"))
                                        ))
                                )
                        ),
                        BigInteger.ZERO,
                        authMsg.getContractAddress(),
                        this.config.getProxyAdmin(),
                        HexUtil.decodeHex("e1c7392a")
                ).send();
            } catch (Exception e) {
                throw new RuntimeException("failed to deploy authMsg", e);
            }
            return proxy.getContractAddress();
        }

        return authMsg.getContractAddress();
    }

    public String deploySdpContract() {
        SDPMsg sdpMsg;
        try {
            sdpMsg = SDPMsg.deploy(
                    web3j,
                    rawTransactionManager,
                    new AcbGasProvider(
                            this.contractGasPriceProvider,
                            createDeployGasLimitProvider(SDPMsg.BINARY)
                    )
            ).send();
        } catch (Exception e) {
            throw new RuntimeException("failed to deploy sdpMsg", e);
        }

        if (this.config.isUpgradableContracts()) {
            TransparentUpgradeableProxy proxy;
            try {
                proxy = TransparentUpgradeableProxy.deploy(
                        web3j,
                        rawTransactionManager,
                        new AcbGasProvider(
                                this.contractGasPriceProvider,
                                createDeployGasLimitProvider(
                                        TransparentUpgradeableProxy.BINARY +
                                        FunctionEncoder.encodeConstructor(ListUtil.toList(
                                                new org.web3j.abi.datatypes.Address(sdpMsg.getContractAddress()),
                                                new org.web3j.abi.datatypes.Address(this.config.getProxyAdmin()),
                                                new DynamicBytes(HexUtil.decodeHex("e1c7392a"))
                                        ))
                                )
                        ),
                        BigInteger.ZERO,
                        sdpMsg.getContractAddress(),
                        this.config.getProxyAdmin(),
                        HexUtil.decodeHex("e1c7392a")
                ).send();
            } catch (Exception e) {
                throw new RuntimeException("failed to deploy sdp contract", e);
            }
            getBbcLogger().info("deploy proxy contract for sdp: {}", proxy.getContractAddress());
            return proxy.getContractAddress();
        }

        return sdpMsg.getContractAddress();
    }

    public long querySdpSeq(String sdpContractAddress, String senderDomain, String senderID, String receiverDomain, String receiverID) {
        // 2. load sdpMsg
        SDPMsg sdpMsg = SDPMsg.load(sdpContractAddress, web3j, rawTransactionManager, new DefaultGasProvider());

        // 3. query sequence
        long seq;
        try {
            seq = sdpMsg.querySDPMessageSeq(
                    senderDomain,
                    HexUtil.decodeHex(senderID),
                    receiverDomain,
                    HexUtil.decodeHex(receiverID)
            ).send().longValue();

            getBbcLogger().info("sdpMsg seq: {} (senderDomain: {}, senderID: {}, receiverDomain: {}, receiverID: {})",
                    seq, senderDomain, senderID, receiverDomain, receiverID);
        } catch (Exception e) {
            throw new RuntimeException(
                    String.format("failed to query sdpMsg seq (senderDomain: %s, senderID: %s, receiverDomain: %s, receiverID: %s)",
                            senderDomain, senderID, receiverDomain, receiverID), e
            );
        }

        return seq;
    }

    public void setProtocolToAuthMsg(String amContractAddress, String protocolAddress, String protocolType) {
        // 2. load am contract
        AuthMsg am = AuthMsg.load(
                amContractAddress,
                this.web3j,
                this.rawTransactionManager,
                new AcbGasProvider(
                        this.contractGasPriceProvider,
                        createEthCallGasLimitProvider(
                                amContractAddress,
                                new Function(
                                        AuthMsg.FUNC_SETPROTOCOL,
                                        ListUtil.toList(new org.web3j.abi.datatypes.Address(protocolAddress), new Uint32(Long.parseLong(protocolType))), // inputs
                                        Collections.emptyList()
                                )
                        )
                )
        );

        // 3. set protocol to am
        try {
            var receipt = am.setProtocol(protocolAddress, BigInteger.valueOf(Long.parseLong(protocolType))).send();
            getBbcLogger().info(
                    "set protocol (address: {}, type: {}) to AM {} by tx {} ",
                    protocolAddress, protocolType, amContractAddress, receipt.getTransactionHash()
            );
        } catch (Exception e) {
            throw new RuntimeException(
                    String.format(
                            "failed to set protocol (address: %s, type: %s) to AM %s",
                            protocolAddress, protocolType, amContractAddress
                    ), e
            );
        }
    }

    @SneakyThrows
    public String getProtocolFromAuthMsg(String amContractAddress, String protocolType) {
        AuthMsg am = AuthMsg.load(amContractAddress, web3j, rawTransactionManager, new DefaultGasProvider());
        return am.getProtocol(new BigInteger(protocolType)).send();
    }

    public void setAmContractToSdp(String sdpContractAddress, String amContractAddress) {
        // 2. load sdp contract
        SDPMsg sdp = SDPMsg.load(
                sdpContractAddress,
                this.web3j,
                this.rawTransactionManager,
                new AcbGasProvider(
                        this.contractGasPriceProvider,
                        createEthCallGasLimitProvider(
                                sdpContractAddress,
                                new Function(
                                        SDPMsg.FUNC_SETAMCONTRACT,
                                        ListUtil.toList(new org.web3j.abi.datatypes.Address(amContractAddress)), // inputs
                                        Collections.emptyList()
                                )
                        )
                )
        );

        // 3. set am to sdp
        try {
            var receipt = sdp.setAmContract(amContractAddress).send();
            getBbcLogger().info(
                    "set am contract (address: {}) to SDP {} by tx {}",
                    amContractAddress, sdpContractAddress, receipt.getTransactionHash()
            );
        } catch (Exception e) {
            throw new RuntimeException(
                    String.format("failed to set am contract (address: %s) to SDP %s", amContractAddress, sdpContractAddress), e
            );
        }
    }

    @SneakyThrows
    public String getAmContractFromSdp(String sdpContractAddress) {
        var sdp = SDPMsg.load(sdpContractAddress, web3j, rawTransactionManager, new DefaultGasProvider());
        return sdp.getAmAddress().send();
    }

    @SneakyThrows
    public byte[] getLocalDomainFromSdp(String sdpContractAddress) {
        var sdp = SDPMsg.load(sdpContractAddress, web3j, rawTransactionManager, new DefaultGasProvider());
        return sdp.getLocalDomain().send();
    }

    public void setLocalDomainToSdp(String sdpContractAddress, String localDomain) {
        // 2. load sdp contract
        var sdp = SDPMsg.load(
                sdpContractAddress,
                this.web3j,
                this.rawTransactionManager,
                new AcbGasProvider(
                        this.contractGasPriceProvider,
                        createEthCallGasLimitProvider(
                                sdpContractAddress,
                                new Function(
                                        SDPMsg.FUNC_SETLOCALDOMAIN,
                                        ListUtil.toList(new Utf8String(localDomain)),
                                        Collections.emptyList()
                                )
                        )
                )
        );

        // 3. set domain to sdp
        try {
            TransactionReceipt receipt = sdp.setLocalDomain(localDomain).send();
            getBbcLogger().info("set domain ({}) to SDP {} by tx {}", localDomain, sdpContractAddress, receipt.getTransactionHash());
        } catch (Exception e) {
            throw new RuntimeException(
                    String.format(
                            "failed to set domain (%s) to SDP %s", localDomain, sdpContractAddress
                    ), e
            );
        }
    }

    public CrossChainMessageReceipt relayMsgToAuthMsg(String amContractAddress, byte[] rawMessage) {
        // 2. create Transaction
        try {
            // 2.1 create function
            Function function = new Function(
                    AuthMsg.FUNC_RECVPKGFROMRELAYER, // function name
                    Collections.singletonList(new DynamicBytes(rawMessage)), // inputs
                    Collections.emptyList() // outputs
            );
            String encodedFunc = FunctionEncoder.encode(function);

            // 2.2 pre-execute before commit tx
            EthCall call = this.web3j.ethCall(
                    org.web3j.protocol.core.methods.request.Transaction.createEthCallTransaction(
                            config.isKmsService() ? this.txKMSSignService.getAddress() : this.credentials.getAddress(),
                            amContractAddress,
                            encodedFunc
                    ),
                    DefaultBlockParameterName.LATEST
            ).send();

            // 2.3 set `confirmed` and `successful` to false if reverted
            CrossChainMessageReceipt crossChainMessageReceipt = new CrossChainMessageReceipt();
            if (call.isReverted()) {
                getBbcLogger().error("call am contract {} reverted, reason: {}", amContractAddress, call.getRevertReason());
                crossChainMessageReceipt.setSuccessful(false);
                crossChainMessageReceipt.setConfirmed(StrUtil.contains(call.getRevertReason(), SDP_NONCE_HAS_BEEN_PROCESSED_REVERT_REASON));
                crossChainMessageReceipt.setErrorMsg(call.getRevertReason());
                return crossChainMessageReceipt;
            }

            // 2.4 async send tx
            EthSendTransaction ethSendTransaction = rawTransactionManager.sendTransaction(
                    this.contractGasPriceProvider.getGasPrice(encodedFunc),
                    createEthCallGasLimitProvider(amContractAddress, function).getGasLimit(encodedFunc),
                    amContractAddress,
                    encodedFunc,
                    BigInteger.ZERO
            );
            if (ObjectUtil.isNull(ethSendTransaction)) {
                throw new RuntimeException("send tx with null result");
            }
            if (ethSendTransaction.hasError()) {
                throw new RuntimeException(StrUtil.format("tx error: {} - {}",
                        ethSendTransaction.getError().getCode(), ethSendTransaction.getError().getMessage()));
            }
            if (StrUtil.isEmpty(ethSendTransaction.getTransactionHash())) {
                throw new RuntimeException("tx hash is empty");
            }

            // 2.5 return crossChainMessageReceipt
            crossChainMessageReceipt.setConfirmed(false);
            crossChainMessageReceipt.setSuccessful(true);
            crossChainMessageReceipt.setTxhash(ethSendTransaction.getTransactionHash());
            crossChainMessageReceipt.setErrorMsg("");

            getBbcLogger().info("relay msg by tx {}", ethSendTransaction.getTransactionHash());

            return crossChainMessageReceipt;
        } catch (Exception e) {
            throw new RuntimeException(
                    String.format("failed to relay AM %s to %s",
                            HexUtil.encodeHexStr(rawMessage), amContractAddress
                    ), e
            );
        }
    }

    @SneakyThrows
    public List<CrossChainMessage> readAuthMessagesFromBlock(BigInteger slot, String amContractAddressHex) {
        var beaconBlock = getBeaconBlockBySlot(slot);
        if (ObjectUtil.isNull(beaconBlock)) {
            getBbcLogger().info("none beacon block returned, estimated that beacon slot {} missed", slot);
            return ListUtil.empty();
        }

        if (beaconBlock.getBody().getOptionalExecutionPayloadHeader().isEmpty() && beaconBlock.getBody().getOptionalExecutionPayload().isEmpty()) {
            throw new RuntimeException("no execution payload found in beacon block, fallback to get exec header by hash");
        }

        LogsBloomFilter logsBloomFilter;
        BigInteger blockNumber;
        if (beaconBlock.getBody().getOptionalExecutionPayloadHeader().isPresent()) {
            var executionPayloadHeader = beaconBlock.getBody().getOptionalExecutionPayloadHeader().get();
            logsBloomFilter = LogsBloomFilter.fromHexString(executionPayloadHeader.getLogsBloom().toHexString());
            blockNumber = executionPayloadHeader.getBlockNumber().bigIntegerValue();
        } else {
            var executionPayload = beaconBlock.getBody().getOptionalExecutionPayload().get();
            logsBloomFilter = LogsBloomFilter.fromHexString(executionPayload.getLogsBloom().toHexString());
            blockNumber = executionPayload.getBlockNumber().bigIntegerValue();
        }

        if (logsBloomFilter.couldContain(SEND_AUTH_MESSAGE_LOG_TOPIC_FILTER)
                && logsBloomFilter.couldContain(LogsBloomFilter.builder().insertBytes(Address.fromHexString(amContractAddressHex)).build())
        ) {
            getBbcLogger().info("send am log found in execution payload, block slot: {}, block number: {}, contract address: {}",
                    slot, blockNumber, amContractAddressHex);
            return switch (this.config.getMsgScanPolicy()) {
                case LOG_FILTER -> readMessagesByFilter(beaconBlock, blockNumber, amContractAddressHex);
                case BLOCK_SCAN -> readMessagesFromEntireBlock(beaconBlock, blockNumber, amContractAddressHex);
            };
        }

        return ListUtil.empty();
    }

    public EthConsensusStateData getEthConsensusStateData(BigInteger slot, String amContract) {
        var ethConsensusStateData = new EthConsensusStateData();
        ethConsensusStateData.setAmContractHex(amContract);
        // last slot for this period
        var currPeriod = currentSyncCommitteePeriod(slot);
        var currPeriodEndSlot = currPeriod.multiply(BigInteger.valueOf(config.getEth2ChainConfig().getSyncPeriodLength()));
        if (currPeriodEndSlot.equals(slot)) {
            // fetch the sync committee update
            getBbcLogger().info("get light client update for next period: {}", currPeriod.add(BigInteger.ONE));
            var lightClientUpdate = getLightClientUpdate(slot);
            if (ObjectUtil.isNull(lightClientUpdate)) {
                getBbcLogger().error("none update found for period: {}", currPeriod);
                throw new RuntimeException(StrUtil.format("none update found for period: {}", currPeriod.toString()));
            }
            ethConsensusStateData.setLightClientUpdateWrapper(lightClientUpdate);
        }

        getBbcLogger().info("has ccmsg on slot {} or already has no cache, will fetch the whole beacon block...", slot);
        var signedBeaconBlock = getBeaconBlockBySlot(slot);
        if (ObjectUtil.isNull(signedBeaconBlock)) {
            return ethConsensusStateData;
        }
        if (signedBeaconBlock.getBeaconBlock().isEmpty()) {
            getBbcLogger().warn("slot {} has no beacon block, could be empty", slot);
            return ethConsensusStateData;
        }
        var beaconBlock = signedBeaconBlock.getBeaconBlock().get();
        if (beaconBlock.getBody().getOptionalExecutionPayloadHeader().isEmpty()) {
            throw new RuntimeException("no execution payload found in beacon block as slot " + slot);
        }

        ethConsensusStateData.setBeaconBlockHeader(BeaconBlockHeader.fromBlock(beaconBlock));
        ethConsensusStateData.setExecutionPayloadHeader(beaconBlock.getBody().getOptionalExecutionPayloadHeader().get());

        var bodySchema = config.getEth2ChainConfig().getCurrentSchemaDefinitions(slot).getBlindedBeaconBlockBodySchema();
        ethConsensusStateData.setExecutionPayloadBranches(
                MerkleUtil.constructMerkleProof(
                        beaconBlock.getBody().getBackingNode(),
                        bodySchema.getChildGeneralizedIndex(bodySchema.getFieldIndex(BlockBodyFields.EXECUTION_PAYLOAD_HEADER))
                )
        );

        return ethConsensusStateData;
    }

    public boolean hasTpBtaOnPtcHub(String ptcHubAddress, CrossChainLane tpbtaLane, int tpBtaVersion) {
        try {
            PtcHub ptcHub = PtcHub.load(ptcHubAddress, this.web3j, this.rawTransactionManager, null);
            getBbcLogger().info("call to check if tpbta exist {}:{}", tpbtaLane.getLaneKey(), tpBtaVersion);

            return ptcHub.hasTpBta(tpbtaLane.encode(), BigInteger.valueOf(tpBtaVersion)).send();
        } catch (Exception e) {
            throw new RuntimeException(StrUtil.format(
                    "unexpected exception when checking tpbta {}:{} from ptc hub",
                    tpbtaLane.getLaneKey(), tpBtaVersion, e
            ));
        }
    }

    public ThirdPartyBlockchainTrustAnchor getTpBtaFromPtcHub(String ptcHubAddress, CrossChainLane tpbtaLane, int tpBtaVersion) {
        try {
            PtcHub ptcHub = PtcHub.load(ptcHubAddress, this.web3j, this.rawTransactionManager, null);
            getBbcLogger().info("call to get tpbta {}:{}", tpbtaLane.getLaneKey(), tpBtaVersion);

            byte[] raw = ptcHub.getTpBta(tpbtaLane.encode(), BigInteger.valueOf(tpBtaVersion)).send();
            if (ObjectUtil.isNotEmpty(raw)) {
                return ThirdPartyBlockchainTrustAnchor.decode(raw);
            }
        } catch (Exception e) {
            throw new RuntimeException(StrUtil.format(
                    "unexpected exception when get tpbta {}:{} from ptc hub",
                    tpbtaLane.getLaneKey(), tpBtaVersion, e
            ));
        }
        return null;
    }

    public Set<PTCTypeEnum> getSupportedPTCTypesFromPtcHub(String ptcHubAddress) {
        try {
            PtcHub ptcHub = PtcHub.load(ptcHubAddress, this.web3j, this.rawTransactionManager, null);
            getBbcLogger().info("call to get supported ptc types");

            List<BigInteger> types = ptcHub.getSupportedPTCType().send();
            return types.stream().map(x -> PTCTypeEnum.valueOf(x.byteValueExact())).collect(Collectors.toSet());
        } catch (Exception e) {
            throw new RuntimeException(StrUtil.format(
                    "unexpected exception when getting supported ptc types from ptc hub", e
            ));
        }
    }

    public PTCTrustRoot getPTCTrustRootFromPtcHub(String ptcHubAddress, ObjectIdentity ptcOwnerOid) {
        try {
            PtcHub ptcHub = PtcHub.load(ptcHubAddress, this.web3j, this.rawTransactionManager, null);
            byte[] rawOid = ptcOwnerOid.encode();
            getBbcLogger().info("call to get ptc trust root for {}", HexUtil.encodeHexStr(rawOid));

            byte[] raw = ptcHub.getPTCTrustRoot(ptcOwnerOid.encode()).send();
            if (ObjectUtil.isEmpty(raw)) {
                return null;
            }
            return PTCTrustRoot.decode(raw);
        } catch (Exception e) {
            throw new RuntimeException(StrUtil.format(
                    "unexpected exception when getting ptc trust root for oid {} from ptc hub",
                    HexUtil.encodeHexStr(ptcOwnerOid.encode()), e
            ));
        }
    }

    public boolean hasPTCTrustRootFromPtcHub(String ptcHubAddress, ObjectIdentity ptcOwnerOid) {
        try {
            PtcHub ptcHub = PtcHub.load(
                    ptcHubAddress,
                    this.web3j,
                    this.rawTransactionManager,
                    null
            );
            byte[] rawOid = ptcOwnerOid.encode();
            getBbcLogger().info("call to check if has ptc trust root for {}", HexUtil.encodeHexStr(rawOid));

            return ptcHub.hasPTCTrustRoot(rawOid).send();
        } catch (Exception e) {
            throw new RuntimeException(StrUtil.format(
                    "unexpected exception when checking ptc trust root for oid {} from ptc hub",
                    HexUtil.encodeHexStr(ptcOwnerOid.encode()), e
            ));
        }
    }

    public PTCVerifyAnchor getPTCVerifyAnchorFromPtcHub(String ptcHubAddress, ObjectIdentity ptcOwnerOid, BigInteger version) {
        try {
            PtcHub ptcHub = PtcHub.load(
                    ptcHubAddress,
                    this.web3j,
                    this.rawTransactionManager,
                    null
            );
            getBbcLogger().info("get ptc verify anchor for {}", HexUtil.encodeHexStr(ptcOwnerOid.encode()));

            byte[] raw = ptcHub.getPTCVerifyAnchor(ptcOwnerOid.encode(), version).send();
            if (ObjectUtil.isEmpty(raw)) {
                return null;
            }
            return PTCVerifyAnchor.decode(raw);
        } catch (Exception e) {
            throw new RuntimeException(StrUtil.format(
                    "unexpected exception when checking ptc trust root for oid {} from ptc hub",
                    HexUtil.encodeHexStr(ptcOwnerOid.encode()), e
            ));
        }
    }

    public boolean hasPTCVerifyAnchor(String ptcHubAddress, ObjectIdentity ptcOwnerOid, BigInteger version) {
        try {
            PtcHub ptcHub = PtcHub.load(ptcHubAddress, this.web3j, this.rawTransactionManager, null);
            getBbcLogger().info("call to check if ptc verify anchor exists for {}", HexUtil.encodeHexStr(ptcOwnerOid.encode()));

            return ptcHub.hasPTCVerifyAnchor(ptcOwnerOid.encode(), version).send();
        } catch (Exception e) {
            throw new RuntimeException(StrUtil.format(
                    "unexpected exception when checking ptc trust root for oid {} from ptc hub",
                    HexUtil.encodeHexStr(ptcOwnerOid.encode()), e
            ));
        }
    }

    public String deployPtcHubContract(AbstractCrossChainCertificate bcdnsRootCert, String committeePtcVerifier) {
        PtcHub ptcHub;
        try {
            var rawBcdnsRootCert = bcdnsRootCert.encode();
            ptcHub = PtcHub.deploy(
                    web3j,
                    rawTransactionManager,
                    new AcbGasProvider(
                            this.contractGasPriceProvider,
                            createDeployGasLimitProvider(
                                    PtcHub.BINARY +
                                    FunctionEncoder.encodeConstructor(ListUtil.toList(new DynamicBytes(rawBcdnsRootCert)))
                            )
                    ),
                    bcdnsRootCert.encode()
            ).send();
        } catch (Exception e) {
            throw new RuntimeException("failed to deploy PtcHub", e);
        }

        getBbcLogger().info("deploy contract ptc hub: {}", ptcHub.getContractAddress());

        String ptcHubContractAddr = ptcHub.getContractAddress();
        if (this.config.isUpgradableContracts()) {
            TransparentUpgradeableProxy proxy;
            try {
                byte[] initData = Numeric.hexStringToByteArray(
                        DefaultFunctionEncoder.encode(
                                new Function("init", ListUtil.toList(new DynamicBytes(bcdnsRootCert.encode())), ListUtil.empty())
                        )
                );
                proxy = TransparentUpgradeableProxy.deploy(
                        web3j,
                        rawTransactionManager,
                        new AcbGasProvider(
                                this.contractGasPriceProvider,
                                createDeployGasLimitProvider(
                                        TransparentUpgradeableProxy.BINARY +
                                        FunctionEncoder.encodeConstructor(ListUtil.toList(
                                                new org.web3j.abi.datatypes.Address(ptcHub.getContractAddress()),
                                                new org.web3j.abi.datatypes.Address(this.config.getProxyAdmin()),
                                                new DynamicBytes(initData)
                                        ))
                                )
                        ),
                        BigInteger.ZERO,
                        ptcHub.getContractAddress(),
                        this.config.getProxyAdmin(),
                        initData
                ).send();
            } catch (Exception e) {
                throw new RuntimeException("failed to deploy ptc hub contract", e);
            }
            ptcHubContractAddr = proxy.getContractAddress();
            getBbcLogger().info("deploy proxy contract for ptc hub: {}", proxy.getContractAddress());
        }

        ptcHub = PtcHub.load(
                ptcHubContractAddr,
                web3j,
                rawTransactionManager,
                new AcbGasProvider(
                        this.contractGasPriceProvider,
                        createEthCallGasLimitProvider(
                                ptcHubContractAddr,
                                new Function(
                                        PtcHub.FUNC_ADDPTCVERIFIER,
                                        ListUtil.toList(new org.web3j.abi.datatypes.Address(committeePtcVerifier)),
                                        ListUtil.empty()
                                )
                        )
                )
        );

        try {
            TransactionReceipt receipt = ptcHub.addPtcVerifier(committeePtcVerifier).send();
            if (!receipt.isStatusOK()) {
                throw new RuntimeException(
                        StrUtil.format(
                                "transaction {} shows failed when set committee verifier {} to ptc hub {}: {}",
                                receipt.getTransactionHash(), committeePtcVerifier, ptcHubContractAddr, receipt.getRevertReason()
                        )
                );
            }
            getBbcLogger().info(
                    "set committee verifier {} to ptc hub {} by tx {} ", committeePtcVerifier, ptcHubContractAddr, receipt.getTransactionHash()
            );
        } catch (Exception e) {
            throw new RuntimeException(
                    String.format(
                            "unexpected failure when setting committee verifier %s to ptc hub %s",
                            committeePtcVerifier, ptcHubContractAddr
                    ), e
            );
        }

        return ptcHubContractAddr;
    }

    public String deployCommitteeVerifierContract() {
        CommitteePtcVerifier verifier;
        try {
            verifier = CommitteePtcVerifier.deploy(
                    web3j,
                    rawTransactionManager,
                    new AcbGasProvider(
                            this.contractGasPriceProvider,
                            createDeployGasLimitProvider(CommitteePtcVerifier.BINARY)
                    )
            ).send();
        } catch (Exception e) {
            throw new RuntimeException("failed to deploy CommitteePtcVerifier", e);
        }

        getBbcLogger().info("deploy contract committee verifier: {}", verifier.getContractAddress());
        return verifier.getContractAddress();
    }

    public void setPtcContractToAuthMsg(String amContractAddress, String ptcContractAddress) {
        // 2. load am contract
        AuthMsg am = AuthMsg.load(
                amContractAddress,
                this.web3j,
                this.rawTransactionManager,
                new AcbGasProvider(
                        this.contractGasPriceProvider,
                        createEthCallGasLimitProvider(
                                amContractAddress,
                                new Function(
                                        AuthMsg.FUNC_SETPTCHUB,
                                        ListUtil.toList(new org.web3j.abi.datatypes.Address(ptcContractAddress)),
                                        ListUtil.empty()
                                )
                        )
                )
        );

        // 3. set protocol to am
        try {
            TransactionReceipt receipt = am.setPtcHub(ptcContractAddress).send();
            if (!receipt.isStatusOK()) {
                throw new RuntimeException(
                        StrUtil.format(
                                "transaction {} shows failed when set ptc hub {} to am {}: {}",
                                receipt.getTransactionHash(), ptcContractAddress, am.getContractAddress(), receipt.getRevertReason()
                        )
                );
            }
            getBbcLogger().info("set ptc hub {} to AM {} by tx {} ", ptcContractAddress, amContractAddress, receipt.getTransactionHash());
        } catch (Exception e) {
            throw new RuntimeException(
                    String.format("failed to set ptc hub %s to AM %s", ptcContractAddress, amContractAddress), e
            );
        }
    }

    @SneakyThrows
    public String getPtcHubAddrFromAuthMsg(String amContractAddress) {
        AuthMsg am = AuthMsg.load(amContractAddress, web3j, rawTransactionManager, new DefaultGasProvider());
        return am.ptcHubAddr().send();
    }

    public void updatePTCTrustRootToPtcHub(String ptcHubAddress, PTCTrustRoot ptcTrustRoot) {
        try {
            byte[] rawPtcTrustRoot = ptcTrustRoot.encode();
            PtcHub ptcHub = PtcHub.load(
                    ptcHubAddress,
                    this.web3j,
                    this.rawTransactionManager,
                    new AcbGasProvider(
                            this.contractGasPriceProvider,
                            createEthCallGasLimitProvider(
                                    ptcHubAddress,
                                    new Function(
                                            PtcHub.FUNC_UPDATEPTCTRUSTROOT,
                                            ListUtil.toList(new DynamicBytes(rawPtcTrustRoot)),
                                            ListUtil.empty()
                                    )
                            )
                    )
            );
            TransactionReceipt receipt = ptcHub.updatePTCTrustRoot(rawPtcTrustRoot).send();
            if (!receipt.isStatusOK()) {
                throw new RuntimeException(
                        StrUtil.format(
                                "transaction {} shows failed when update ptc trust root {} to ptc hub {}: {}",
                                receipt.getTransactionHash(),
                                HexUtil.encodeHexStr(ptcTrustRoot.getPtcCredentialSubject().getApplicant().encode()),
                                ptcHub.getContractAddress(),
                                receipt.getRevertReason()
                        )
                );
            }
            getBbcLogger().info("upload ptc trust root for ptc owner {} to ptc hub", HexUtil.encodeHexStr(ptcTrustRoot.getPtcCredentialSubject().getApplicant().encode()));
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(StrUtil.format(
                    "unexpected exception when upload ptc trust root for ptc owner {} to ptc hub",
                    HexUtil.encodeHexStr(ptcTrustRoot.getPtcCredentialSubject().getApplicant().encode()), e
            ));
        }
    }

    public void addTpBtaToPtcHub(String ptcHubAddress, ThirdPartyBlockchainTrustAnchor tpbta) {
        try {
            byte[] rawTpbta = tpbta.encode();
            PtcHub ptcHub = PtcHub.load(
                    ptcHubAddress,
                    this.web3j,
                    this.rawTransactionManager,
                    new AcbGasProvider(
                            this.contractGasPriceProvider,
                            createEthCallGasLimitProvider(
                                    ptcHubAddress,
                                    new Function(
                                            PtcHub.FUNC_ADDTPBTA,
                                            ListUtil.toList(new DynamicBytes(rawTpbta)),
                                            ListUtil.empty()
                                    )
                            )
                    )
            );
            TransactionReceipt receipt = ptcHub.addTpBta(rawTpbta).send();
            if (!receipt.isStatusOK()) {
                throw new RuntimeException(
                        StrUtil.format(
                                "transaction {} shows failed when adding tpbta {}:{} to ptc hub {}: {}",
                                receipt.getTransactionHash(),
                                tpbta.getCrossChainLane().getLaneKey(),
                                tpbta.getTpbtaVersion(),
                                ptcHub.getContractAddress(),
                                receipt.getRevertReason()
                        )
                );
            }
            getBbcLogger().info("adding tpbta {}:{} to ptc hub", tpbta.getCrossChainLane().getLaneKey(), tpbta.getTpbtaVersion());
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(StrUtil.format(
                    "unexpected exception when adding tpbta {}:{} to ptc hub",
                    tpbta.getCrossChainLane().getLaneKey(), tpbta.getTpbtaVersion(), e
            ));
        }
    }

    @SneakyThrows
    public BlockState queryValidatedBlockStateFromSdp(String sdpAddress, CrossChainDomain domain) {
        var sdp = SDPMsg.load(sdpAddress, web3j, rawTransactionManager, new DefaultGasProvider());
        var result = sdp.queryValidatedBlockStateByDomain(domain.getDomain()).send();
        if (ObjectUtil.isNull(result)) {
            return null;
        }
        return new BlockState(domain, result.blockHash, result.blockHeight, result.blockTimestamp.longValue());
    }

    public CrossChainMessageReceipt recvOffChainException(String sdpContractAddress, String exceptionMsgAuthor, byte[] exceptionMsgPkg) {
        getBbcLogger().info("rollback sdp msg from {} now", exceptionMsgAuthor);
        getBbcLogger().debug("exceptionMsgPkg: {}", HexUtil.encodeHexStr(exceptionMsgPkg));
        var sender = HexUtil.decodeHex(exceptionMsgAuthor);
        var sdp = SDPMsg.load(
                sdpContractAddress,
                this.web3j,
                this.rawTransactionManager,
                new AcbGasProvider(
                        this.contractGasPriceProvider,
                        createEthCallGasLimitProvider(
                                sdpContractAddress,
                                new Function(
                                        SDPMsg.FUNC_RECVOFFCHAINEXCEPTION,
                                        ListUtil.toList(
                                                new org.web3j.abi.datatypes.generated.Bytes32(sender),
                                                new DynamicBytes(exceptionMsgPkg)
                                        ), Collections.emptyList()
                                )
                        )
                )
        );

        // 3. set domain to sdp
        try {
            CrossChainMessageReceipt crossChainMessageReceipt = new CrossChainMessageReceipt();
            TransactionReceipt receipt = sdp.recvOffChainException(sender, exceptionMsgPkg).send();
            if (ObjectUtil.isNull(receipt)) {
                crossChainMessageReceipt.setErrorMsg("null receipt after sent tx");
                return crossChainMessageReceipt;
            }
            crossChainMessageReceipt.setTxhash(receipt.getTransactionHash());
            if (receipt.isStatusOK()) {
                getBbcLogger().info("successful to rollback sdp msg from {} to sdp {} by tx {}", exceptionMsgAuthor, sdpContractAddress, receipt.getTransactionHash());
                crossChainMessageReceipt.setSuccessful(true);
                crossChainMessageReceipt.setConfirmed(true);
                return crossChainMessageReceipt;
            }
            getBbcLogger().error("failed to rollback sdp msg from {} to sdp {} by tx {}", exceptionMsgAuthor, sdpContractAddress, receipt.getTransactionHash());
            crossChainMessageReceipt.setSuccessful(false);
            crossChainMessageReceipt.setConfirmed(true);
            return crossChainMessageReceipt;
        } catch (Exception e) {
            throw new RuntimeException(
                    StrUtil.format("failed to process rollback sdp msg from {} to sdp {}", exceptionMsgAuthor, sdpContractAddress), e
            );
        }
    }

    public LightClientUpdateWrapper getLightClientUpdate(BigInteger slot) {
        var update = this.beaconNodeClient.getLightClientUpdate(slot);
        if (ObjectUtil.isNull(update)) {
            return null;
        }
        return new LightClientUpdateWrapper(update);
    }

    public BigInteger currentSyncCommitteePeriod(BigInteger slot) {
        return slot.divide(BigInteger.valueOf(config.getEth2ChainConfig().getSyncPeriodLength()));
    }

    public BeaconBlock getBeaconBlockBySlot(BigInteger slot) {
        var signedBeaconBlock = this.beaconNodeClient.getBlindedBlockBySlot(slot);
        if (ObjectUtil.isNull(signedBeaconBlock)) {
            return null;
        }
        return signedBeaconBlock.getBeaconBlock().orElseThrow(() -> new RuntimeException("null beacon block on slot " + slot.toString()));
    }

    @SneakyThrows
    private EthBlock getExecHeaderByHash(String blockHash) {
        var block = web3j.ethGetBlockByHash(blockHash, false).send();
        if (ObjectUtil.isNull(block)) {
            throw new RuntimeException("get null block by hash " + blockHash);
        }

        return block;
    }

    @SneakyThrows
    private List<CrossChainMessage> readMessagesFromEntireBlock(BeaconBlock beaconBlock, BigInteger blockNumber, String amContractAddressHex) {
        var block = web3j.ethGetBlockByNumber(new DefaultBlockParameterNumber(blockNumber), true).send().getBlock();

        List<CrossChainMessage> messageList = ListUtil.toList();
        List<TransactionReceipt> allReceiptsInBlock = null;
        for (EthBlock.TransactionResult transactionResult : block.getTransactions()) {
            Transaction transaction = (Transaction) transactionResult.get();
            TransactionReceipt receipt = web3j.ethGetTransactionReceipt(transaction.getHash())
                    .send()
                    .getTransactionReceipt()
                    .orElseThrow(() -> new RuntimeException("failed to get receipt for tx " + transaction.getHash()));
            if (ObjectUtil.isNull(receipt)) {
                throw new RuntimeException("empty receipt for tx " + transaction.getHash());
            }
            if (ObjectUtil.isNull(allReceiptsInBlock)) {
                allReceiptsInBlock = web3j.ethGetBlockReceipts(new DefaultBlockParameterNumber(blockNumber)).send().getResult();
            }

            List<TransactionReceipt> finalAllReceiptsInBlock = allReceiptsInBlock;
            messageList.addAll(AuthMsg.getSendAuthMessageEvents(receipt).stream()
                    .filter(x -> StrUtil.equalsIgnoreCase(x.log.getAddress(), amContractAddressHex))
                    .map(
                            response -> CrossChainMessage.createCrossChainMessage(
                                    CrossChainMessage.CrossChainMessageType.AUTH_MSG,
                                    beaconBlock.getSlot().bigIntegerValue(),
                                    block.getTimestamp().longValue() * 1000,
                                    beaconBlock.getRoot().toArray(),
                                    response.pkg,
                                    EthAuthMessageLog.fromReceipt(receipt, response.log).encodeToJson().getBytes(),
                                    getReceiptProof(finalAllReceiptsInBlock, response.log.getTransactionIndex().intValue()).encodeToJson().getBytes(),
                                    Numeric.hexStringToByteArray(receipt.getTransactionHash())
                            )
                    ).toList());
        }

        if (!messageList.isEmpty()) {
            getBbcLogger().info("read cross chain messages (blockNumber: {}, msg_size: {})", blockNumber, messageList.size());
            getBbcLogger().debug("read cross chain messages (blockNumber: {}, msgs: {})",
                    blockNumber,
                    messageList.stream().map(JSON::toJSONString).collect(Collectors.joining(","))
            );
        }

        return messageList;
    }

    @SneakyThrows
    private List<CrossChainMessage> readMessagesByFilter(BeaconBlock beaconBlock, BigInteger blockNumber, String amContractAddressHex) {
        var logs = web3j.ethGetLogs(
                new EthFilter(
                        new DefaultBlockParameterNumber(blockNumber),
                        new DefaultBlockParameterNumber(blockNumber),
                        amContractAddressHex
                ).addSingleTopic(EventEncoder.encode(AuthMsg.SENDAUTHMESSAGE_EVENT))
        ).send().getLogs();

        EthBlock.Block block = null;
        List<TransactionReceipt> allReceiptsInBlock = null;

        List<CrossChainMessage> messageList = ListUtil.toList();
        for (EthLog.LogResult logResult : logs) {
            var logObject = (EthLog.LogObject) logResult.get();

            if (!StrUtil.equalsIgnoreCase(logObject.getAddress(), amContractAddressHex)) {
                getBbcLogger().warn("log from node has wrong contract address: {}, expected: {}", logObject.getAddress(), amContractAddressHex);
                continue;
            }
            if (logObject.getTopics() == null || logObject.getTopics().size() != 1 || !StrUtil.equalsIgnoreCase(logObject.getTopics().getFirst(), SEND_AUTH_MESSAGE_LOG_TOPIC)) {
                getBbcLogger().warn("log from node has wrong topics: {}, expected: {}", logObject.getTopics(), SEND_AUTH_MESSAGE_LOG_TOPIC);
                continue;
            }

            if (ObjectUtil.isNull(block)) {
                block = web3j.ethGetBlockByNumber(new DefaultBlockParameterNumber(blockNumber), false).send().getBlock();
            }
            if (ObjectUtil.isNull(allReceiptsInBlock)) {
                allReceiptsInBlock = web3j.ethGetBlockReceipts(new DefaultBlockParameterNumber(blockNumber)).send().getResult();
            }
            var transactionReceipt = allReceiptsInBlock.get(logObject.getTransactionIndex().intValue());
            var blockTimestamp = block.getTimestamp().longValue() * 1000;
            var receiptProof = getReceiptProof(allReceiptsInBlock, logObject.getTransactionIndex().intValue());

            // One filter result denotes ONE event, not every AM event in the same transaction.
            var ledgerLog = EthAuthMessageLog.fromReceipt(transactionReceipt, logObject);
            messageList.add(CrossChainMessage.createCrossChainMessage(
                    CrossChainMessage.CrossChainMessageType.AUTH_MSG,
                    beaconBlock.getSlot().bigIntegerValue(),
                    blockTimestamp,
                    beaconBlock.getRoot().toArray(),
                    ledgerLog.decodeMessage(),
                    ledgerLog.encodeToJson().getBytes(),
                    receiptProof.encodeToJson().getBytes(),
                    Numeric.hexStringToByteArray(transactionReceipt.getTransactionHash())
            ));
        }

        if (!messageList.isEmpty()) {
            getBbcLogger().info("read cross chain messages (blockNumber: {}, msg_size: {})", blockNumber, messageList.size());
            getBbcLogger().debug("read cross chain messages (blockNumber: {}, msgs: {})",
                    blockNumber,
                    messageList.stream().map(JSON::toJSONString).collect(Collectors.joining(","))
            );
        }

        return messageList;
    }

    private EthReceiptProof getReceiptProof(List<TransactionReceipt> receiptsWeb3j, int receiptIndex) {
        if (ObjectUtil.isEmpty(receiptsWeb3j)) {
            throw new RuntimeException("receipts is empty");
        }
        return EthDataValidator.getReceiptProof(receiptIndex, receiptsWeb3j.stream().map(EthTransactionReceipt::generateFrom).toList());
    }

    public void shutdown() {
        this.web3j.shutdown();
        this.beaconNodeClient.shutdown();
    }

    private IGasLimitProvider createDeployGasLimitProvider(String data) {
        switch (config.getGasLimitPolicy()) {
            case ESTIMATE:
                if (config.isKmsService()) {
                    return new EstimateGasLimitProvider(web3j, txKMSSignService.getAddress(), null, data, config.getExtraGasLimit());
                }
                return new EstimateGasLimitProvider(web3j, credentials.getAddress(), null, data, config.getExtraGasLimit());
            case STATIC:
            default:
                return new StaticGasLimitProvider(BigInteger.valueOf(config.getGasLimit()));
        }
    }

    private IGasPriceProvider createGasPriceProvider(GasPriceProviderConfig gasPriceProviderConfig) {
        return switch (config.getGasPricePolicy()) {
            case FROM_API -> GasPriceProvider.create(this.web3j, gasPriceProviderConfig, getBbcLogger());
            default -> new StaticGasPriceProvider(BigInteger.valueOf(config.getGasPrice()));
        };
    }

    private IGasLimitProvider createEthCallGasLimitProvider(String toAddr, Function function) {
        switch (config.getGasLimitPolicy()) {
            case ESTIMATE:
                if (config.isKmsService()) {
                    return new EstimateGasLimitProvider(web3j, txKMSSignService.getAddress(), toAddr, FunctionEncoder.encode(function), config.getExtraGasLimit());
                }
                return new EstimateGasLimitProvider(web3j, credentials.getAddress(), toAddr, FunctionEncoder.encode(function), config.getExtraGasLimit());
            case STATIC:
            default:
                return new StaticGasLimitProvider(BigInteger.valueOf(config.getGasLimit()));
        }
    }

    private RawTransactionManager createTransactionManager(BigInteger chainId) throws Exception {
        if (config.isKmsService()) {
            com.aliyun.teaopenapi.models.Config kmsConfig = new com.aliyun.teaopenapi.models.Config()
                    .setAccessKeyId(config.getKmsAccessKeyId())
                    .setAccessKeySecret(config.getKmsAccessKeySecret())
                    .setEndpoint(config.getKmsEndpoint());
            Client kmsClient = new Client(kmsConfig);
            txKMSSignService = new TxKMSSignService(kmsClient, config.getKmsPrivateKeyId());

            return config.getEthNoncePolicy() == EthNoncePolicyEnum.FAST ?
                    new AcbFastRawTransactionManager(this.web3j, txKMSSignService, chainId.longValue())
                    : new AcbRawTransactionManager(this.web3j, txKMSSignService, chainId.longValue());
        } else {
            return config.getEthNoncePolicy() == EthNoncePolicyEnum.FAST ?
                    new AcbFastRawTransactionManager(this.web3j, this.credentials, chainId.longValue())
                    : new AcbRawTransactionManager(this.web3j, this.credentials, chainId.longValue());
        }
    }

    private Logger getBbcLogger() {
        return ObjectUtil.isNull(this.bbcLogger) ? NOPLogger.NOP_LOGGER : this.bbcLogger;
    }
}

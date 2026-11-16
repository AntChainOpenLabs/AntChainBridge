package com.alipay.antchain.bridge.plugins.ethereum3.tools;

import java.io.File;
import java.math.BigInteger;
import java.nio.file.Paths;
import java.util.Collections;

import cn.hutool.core.codec.Base64;
import cn.hutool.core.collection.ListUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.serializer.SerializerFeature;
import com.alipay.antchain.bridge.plugins.ethereum3.abi.AppContract;
import com.alipay.antchain.bridge.plugins.ethereum3.conf.Eth2NetworkEnum;
import com.alipay.antchain.bridge.plugins.ethereum3.conf.EthereumConfig;
import com.alipay.antchain.bridge.plugins.ethereum3.core.EthSubjectIdentity;
import com.alipay.antchain.bridge.plugins.ethereum3.core.eth.LightClientUpdateWrapper;
import com.alipay.antchain.bridge.plugins.ethereum3.core.eth.beacon.AcbBeaconClient;
import com.alipay.antchain.bridge.plugins.ethereum3.core.eth.eth2spec.Eth2ChainConfig;
import com.alipay.antchain.bridge.plugins.ethereum3.helper.*;
import com.alipay.antchain.bridge.plugins.ethereum3.helper.model.GasPriceProviderConfig;
import org.slf4j.helpers.NOPLogger;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.datatypes.Bool;
import org.web3j.abi.datatypes.DynamicBytes;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.Utf8String;
import org.web3j.abi.datatypes.generated.Bytes32;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.protocol.http.HttpService;
import org.web3j.tx.RawTransactionManager;
import org.web3j.utils.Files;
import org.web3j.utils.Numeric;

public class EthBbcTools {

    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Usage: java -cp plugins/ethereum3-acb-plugin-${version}-plugin.jar com.alipay.antchain.bridge.plugins.ethereum3.tools.EthBbcTools <method> [<arg1> <arg2> ...]");
            System.exit(1);
        }

        try {
            switch (args[0]) {
                case "sendUnorderedSdpV2" -> {
                    if (args.length != 8) {
                        System.out.println("Usage: java -cp plugins/ethereum3-acb-plugin-${version}-plugin.jar com.alipay.antchain.bridge.plugins.ethereum3.tools.EthBbcTools " +
                                           "sendUnorderedSdpV2 <path-to-plugin-config-json> <loop-limit:int> <gap:ms> <sender-contract:hex-address> <receiver:hex-bytes32> <domain:string> <atomic:bool>");
                        System.exit(1);
                    }

                    String pluginConfigJson = args[1];
                    int loopLimit = Integer.parseInt(args[2]);
                    long gap = Long.parseLong(args[3]);
                    String senderContract = args[4];
                    String receiver = args[5];
                    String domain = args[6];
                    boolean atomic = Boolean.parseBoolean(args[7]);

                    System.out.println(StrUtil.format("sendUnorderedV2: {} - {} - {} - {} : loopLimit: {}", senderContract, receiver, domain, atomic, loopLimit));

                    EthereumConfig config = EthereumConfig.fromJsonString(Files.readString(new File(Paths.get(pluginConfigJson).toUri())));

                    Web3j web3j = Web3j.build(new HttpService(config.getUrl()));

                    Credentials credentials = Credentials.create(config.getPrivateKey());

                    RawTransactionManager transactionManager = new RawTransactionManager(web3j, credentials, web3j.ethChainId().send().getChainId().longValue());

                    IGasPriceProvider gasPriceProvider = createGasPriceProvider(web3j, config);
                    AppContract appContract = AppContract.load(
                            senderContract, web3j, transactionManager,
                            new AcbGasProvider(
                                    gasPriceProvider,
                                    createEthCallGasLimitProvider(
                                            web3j,
                                            credentials,
                                            senderContract,
                                            new Function(
                                                    AppContract.FUNC_SENDUNORDEREDV2,
                                                    ListUtil.toList(
                                                            new Bytes32(Numeric.hexStringToByteArray(receiver)),
                                                            new Utf8String(domain),
                                                            new Bool(atomic),
                                                            new DynamicBytes(Long.toString(System.currentTimeMillis()).getBytes())
                                                    ),
                                                    Collections.emptyList()
                                            ), config
                                    )
                            )
                    );

                    while (loopLimit != 0) {
                        byte[] msg = Long.toString(System.currentTimeMillis()).getBytes();
                        System.out.println(StrUtil.format("sendUnorderedV2: {} - {} - {} - {} - {} : loopLimit: {}",
                                senderContract, receiver, domain, atomic, msg, loopLimit));
                        TransactionReceipt receipt = appContract.sendUnorderedV2(Numeric.hexStringToByteArray(receiver), domain, atomic, msg).send();
                        if (receipt.isStatusOK()) {
                            System.out.println(StrUtil.format("success to send transaction: {}", receipt.getTransactionHash()));
                        } else {
                            System.err.println(StrUtil.format("failed to send transaction: {}", receipt.getTransactionHash()));
                            System.exit(1);
                        }
                        if (loopLimit > 0) {
                            Thread.sleep(gap);
                            loopLimit--;
                        }
                    }

                    System.out.println("sendUnorderedV2 done.");
                }
                case "getEth2Config" -> {
                    if (args.length != 3) {
                        System.out.println("Usage: java -cp plugins/ethereum3-acb-plugin-${version}-plugin.jar com.alipay.antchain.bridge.plugins.ethereum3.tools.EthBbcTools " +
                                           "getEth2Config <beacon-url> <eth2-network>");
                        System.exit(1);
                    }

                    String beaconUrl = args[1];
                    var eth2Network = Eth2NetworkEnum.fromString(args[2]);

                    var beaconClient = new AcbBeaconClient(beaconUrl);

                    var genesisData = beaconClient.getGenesisData();
                    var rawSpecConfig = beaconClient.getRemoteSpec();
                    System.out.println(JSON.toJSONString(
                            new Eth2ChainConfig(eth2Network, genesisData.getGenesisTime().longValue(), genesisData.getGenesisValidatorsRoot().toArray(), rawSpecConfig),
                            SerializerFeature.PrettyFormat
                    ));
                }

                case "buildEthSubjectIdentity" -> {
                    if (args.length != 4) {
                        System.out.println("Usage: java -cp plugins/ethereum3-acb-plugin-${version}-plugin.jar com.alipay.antchain.bridge.plugins.ethereum3.tools.EthBbcTools " +
                                           "buildEthSubjectIdentity <beacon-url> <eth2-network> <curr_slot>");
                        System.exit(1);
                    }

                    String beaconUrl = args[1];
                    var eth2Network = Eth2NetworkEnum.fromString(args[2]);
                    var slot = new BigInteger(args[3]);

                    var beaconClient = new AcbBeaconClient(beaconUrl);

                    var genesisData = beaconClient.getGenesisData();
                    var rawSpecConfig = beaconClient.getRemoteSpec();

                    var eth2Config = new Eth2ChainConfig(eth2Network, genesisData.getGenesisTime().longValue(), genesisData.getGenesisValidatorsRoot().toArray(), rawSpecConfig);
                    beaconClient.setEth2ChainConfig(eth2Config);

                    if (slot.compareTo(BigInteger.valueOf(eth2Config.getSyncPeriodLength())) <= 0) {
                        System.err.println("period must greater than zero");
                    }

                    var period = slot.divide(BigInteger.valueOf(eth2Config.getSyncPeriodLength()));
                    var updateList = beaconClient.getLightClientUpdates(period.subtract(BigInteger.ONE).longValue(), 1);

                    System.out.println(Base64.encode(
                            JSON.toJSONString(
                                    new EthSubjectIdentity(new LightClientUpdateWrapper(updateList.getFirst()).getNextSyncCommittee(), eth2Config),
                                    SerializerFeature.PrettyFormat
                            ).getBytes()
                    ));
                }
                default -> {
                    System.out.println("Unknown method: " + args[0]);
                    System.exit(1);
                }
            }
        } catch (Throwable t) {
            t.printStackTrace(System.out);
        }
    }

    private static IGasPriceProvider createGasPriceProvider(Web3j web3j, EthereumConfig config) {
        switch (config.getGasPricePolicy()) {
            case FROM_API:
                GasPriceProviderConfig gasPriceProviderConfig = new GasPriceProviderConfig();
                gasPriceProviderConfig.setGasPriceProviderSupplier(config.getGasPriceProviderSupplier());
                gasPriceProviderConfig.setGasProviderUrl(config.getGasProviderUrl());
                gasPriceProviderConfig.setApiKey(config.getPrivateKey());
                gasPriceProviderConfig.setGasUpdateInterval(config.getGasUpdateInterval());
                return GasPriceProvider.create(web3j, gasPriceProviderConfig, NOPLogger.NOP_LOGGER);
            case STATIC:
            default:
                return new StaticGasPriceProvider(BigInteger.valueOf(config.getGasPrice()));
        }
    }

    private static IGasLimitProvider createEthCallGasLimitProvider(Web3j web3j, Credentials credentials, String toAddr, Function function, EthereumConfig config) {
        switch (config.getGasLimitPolicy()) {
            case ESTIMATE:
                return new EstimateGasLimitProvider(web3j, credentials.getAddress(), toAddr, FunctionEncoder.encode(function), config.getExtraGasLimit());
            case STATIC:
            default:
                return new StaticGasLimitProvider(BigInteger.valueOf(config.getGasLimit()));
        }
    }
}

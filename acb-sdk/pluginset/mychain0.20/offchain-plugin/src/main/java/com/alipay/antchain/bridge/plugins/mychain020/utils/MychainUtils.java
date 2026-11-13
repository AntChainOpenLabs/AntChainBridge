package com.alipay.antchain.bridge.plugins.mychain020.utils;

import java.io.ByteArrayOutputStream;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Security;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;

import cn.hutool.core.collection.ListUtil;
import cn.hutool.core.util.StrUtil;
import com.alipay.antchain.bridge.plugins.mychain020.crypto.CryptoSuiteEnum;
import com.alipay.antchain.bridge.plugins.mychain020.model.ContractAddressInfo;
import com.alipay.mychain.sdk.api.service.spv.LogFilter;
import com.alipay.mychain.sdk.bloom.BloomFilter;
import com.alipay.mychain.sdk.common.Parameters;
import com.alipay.mychain.sdk.common.VMTypeEnum;
import com.alipay.mychain.sdk.crypto.hash.Hash;
import com.alipay.mychain.sdk.crypto.hash.HashFactory;
import com.alipay.mychain.sdk.crypto.hash.HashTypeEnum;
import com.alipay.mychain.sdk.domain.block.Block;
import com.alipay.mychain.sdk.domain.transaction.TransactionReceipt;
import com.alipay.mychain.sdk.vm.EVMParameter;
import com.alipay.mychain.sdk.vm.WASMParameter;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.asn1.sec.ECPrivateKey;
import org.bouncycastle.jcajce.util.DefaultJcaJceHelper;
import org.bouncycastle.jcajce.util.JcaJceHelper;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.util.encoders.Hex;

public class MychainUtils {

    public final static String MYCHAIN_SYSTEM_NODE_DELETE_SIGN_RAW = "NodeDelete(bytes32,bytes,uint8)";
    public final static String MYCHAIN_SYSTEM_NODE_ACTIVE_SIGN_RAW = "NodeActive(bytes32,bytes,uint8)";
    public final static String MYCHAIN_V2_SYSTEM_NODE_DELETE_SIGN_HEX
            = "3f9cd4381b3cc4b69a3c0503f011996a6e19b7974dc79c26a9b1df8e649f70fd";
    public final static String MYCHAIN_V2_SYSTEM_NODE_ACTIVE_SIGN_HEX
            = "73aa8928bcd0f00d27bd78b12108de6f9947ac4a355d3f9daae0c938a2875719";
    public final static String MYCHAIN_V2_SYSTEM_NODE_ADD_SIGN_HEX
            = "a0f8505c8551b2bce0573312850e79d4363d85a02c62f11808a12ce5b5fa7127";
    public final static String MYCHAIN_V2_SYSTEM_NODE_UPDATE_SIGN_HEX
            = "3e5c641be0df19286c6bd2ddf27d3f8fd883f729490823be9108d990a47dc1d6";

    public final static String MYCHAIN_V3_SYSTEM_NODE_ADD_SIGN_HEX
            = "6fa554dac40e4eb50e428865d72ec8308c65ca47841bf2a24e1ac00c708e861d";
    public final static String MYCHAIN_V3_SYSTEM_NODE_DELETE_SIGN_HEX
            = "8728f0c3c2031e441541762fcc606c6d5874edd3fe84923167feda1aacc99255";
    public final static String MYCHAIN_V3_SYSTEM_NODE_UPDATE_SIGN_HEX
            = "751ab311758e964c0b8d18ef92cb85b4101f0abdfd782c02d6e95d9af823f22b";
    public final static String MYCHAIN_V3_SYSTEM_NODE_ACTIVATE_SIGN_HEX
            = "bbae54d5318e0e048125a66375762551ed76a86fcdcffeb1615aaa2e759192f1";

    public static final Hash SYSTEM_CONTRACT_NODE = new Hash(
            "e93372533f323b2f12783aa3a586135cf421486439c2cdcde47411b78f9839ec");

    public static final String SYSTEM_ACTIVE_NODE_OUTPUT
            = "0000000000000000000000000000000000000000000000000000000000000000";

    public final static List<String> CONSENSUS_UPDATE_EVENT_TOPICS_LIST = ListUtil.toList(
            MYCHAIN_V2_SYSTEM_NODE_DELETE_SIGN_HEX,
            MYCHAIN_V2_SYSTEM_NODE_ACTIVE_SIGN_HEX,
//            MYCHAIN_V2_SYSTEM_NODE_ADD_SIGN_HEX,
//            MYCHAIN_V2_SYSTEM_NODE_UPDATE_SIGN_HEX,
            MYCHAIN_V3_SYSTEM_NODE_ADD_SIGN_HEX,
            MYCHAIN_V3_SYSTEM_NODE_DELETE_SIGN_HEX,
            MYCHAIN_V3_SYSTEM_NODE_UPDATE_SIGN_HEX,
            MYCHAIN_V3_SYSTEM_NODE_ACTIVATE_SIGN_HEX
    );

    public static boolean bloomTopicsMatch(List<String> topicList, HashTypeEnum hashType, byte[] bloom) {
        LogFilter filter = new LogFilter();
        filter.setHashTypeEnum(hashType);
        topicList.forEach(filter::addTopic);
        /* 深拷贝bloom filter数组出来单独检查 */
        // NOTE: 临时修补mychainx-0.10.2.10 sdk的bug，每次初始化bloomFilter对象会翻转传入数组，
        //       由于java 数组传参是浅拷贝，因此会修改blockHeader中的值，影响后续奇数次事件匹配的结果
        return filter.isMatch(new BloomFilter(Arrays.copyOf(bloom, bloom.length)));
    }

    public static String contractAddrFormat(String evmContractName, String wasmContractName) {
        return new ContractAddressInfo(evmContractName, wasmContractName).toJson();
    }

    public static VMTypeEnum getSupportedParameterType(Parameters parameters) {
        if (parameters instanceof EVMParameter) {
            return VMTypeEnum.EVM;
        } else if (parameters instanceof WASMParameter) {
            return VMTypeEnum.WASM;
        } else {
            throw new RuntimeException(
                    StrUtil.format("Unsupported parameter type: {}",
                            parameters.getClass().getTypeName()));
        }
    }

    public static void checkKeyPair(String priKey, String pubKey, CryptoSuiteEnum cryptoSuiteEnum) {

        try {
            priKey = extract(priKey);
            JcaJceHelper jcaJceHelper = new DefaultJcaJceHelper();
            KeyFactory keyFactory;
            if (cryptoSuiteEnum == CryptoSuiteEnum.CRYPTO_SUITE_SM) {
                Security.addProvider(new BouncyCastleProvider());
                keyFactory = KeyFactory.getInstance("EC", "BC");
            } else {
                keyFactory = jcaJceHelper.createKeyFactory("ECDSA");
            }

            PrivateKey privateKey = keyFactory.generatePrivate(
                    new PKCS8EncodedKeySpec(Base64.getDecoder().decode(priKey)));
            PrivateKeyInfo privateKeyInfo = PrivateKeyInfo.getInstance(privateKey.getEncoded());
            ECPrivateKey ecPrivateKey = ECPrivateKey.getInstance(privateKeyInfo.parsePrivateKey());
            byte[] originalBytes = ecPrivateKey.getPublicKey().getBytes();
            byte[] transform = new byte[originalBytes.length - 1];
            System.arraycopy(originalBytes, 1, transform, 0, transform.length);

            if (!StrUtil.equalsIgnoreCase(
                    Hex.toHexString(transform),
                    pubKey.replaceFirst("^" + "000304", ""))) {
                throw new RuntimeException("pubkey in hex not equal");
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static String extract(String original) {
        int start = 0;
        int end = 0;
        boolean strip = false;
        StringBuilder sb = new StringBuilder();
        while (start < original.length()) {
            end = start;
            if (original.charAt(end) == '-') {
                strip = true;
            }
            while (end < original.length() && original.charAt(end) != '\n') {
                end++;
            }
            if (!strip) {
                if (end > 0 && original.charAt(end - 1) == '\r') {
                    sb.append(original, start, end - 1);
                } else {
                    sb.append(original, start, end);
                }
            }
            start = end + 1;
            strip = false;
        }
        return sb.toString();
    }

    public static byte[] getReceiptProof(
            Block mycBlock,
            HashTypeEnum hashTypeEnum
    ) {
        ByteArrayOutputStream hashStream = new ByteArrayOutputStream();
        for (TransactionReceipt receipt : mycBlock.getBlockBody().getReceiptList()) {
            byte[] hash = HashFactory.getHash(hashTypeEnum).hash(receipt.toRlp());
            try {
                hashStream.write(hash);
            } catch (Exception e) {
                throw new RuntimeException("[TEEClusterAdaptor] MychainProcess failed writing to hashStream.");
            }
        }
        return hashStream.toByteArray();
    }
}

package com.alipay.antchain.bridge.plugins.mychain020;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.UUID;

import com.alipay.antchain.bridge.commons.core.base.SendResponseResult;
import com.alipay.antchain.bridge.plugins.mychain020.sdk.Mychain020Client;
import com.alipay.antchain.bridge.plugins.mychain020.sdk.Mychain020Config;
import com.alipay.mychain.sdk.api.utils.Utils;
import com.alipay.mychain.sdk.common.VMTypeEnum;
import com.alipay.mychain.sdk.domain.account.Identity;
import com.alipay.mychain.sdk.vm.EVMParameter;
import org.bouncycastle.util.encoders.Hex;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MychainMonitorAppFlowTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(MychainMonitorAppFlowTest.class);
    private static final String CONTRACT_BINARY = "/contracts/monitor_demo/MonitorSenderContract.bin";

    private static Mychain020Client client;
    private static String monitorContract;
    private static String receiverDomain;
    private static Identity receiverIdentity;

    @BeforeClass
    public static void startup() throws Exception {
        Assume.assumeTrue(
                "set Mychain monitor flow system properties to run this integration test",
                hasRequiredProperties());

        String configPath = requiredProperty("mychain.config.path");
        Mychain020Config config = Mychain020Config.fromJsonString(
                new String(Files.readAllBytes(Paths.get(configPath)), StandardCharsets.UTF_8));

        monitorContract = requiredProperty("mychain.monitor.contract");
        receiverDomain = requiredProperty("mychain.receiver.domain");
        String receiverIdentityHex = normalizeIdentity(requiredProperty("mychain.receiver.identity"));
        receiverIdentity = new Identity(Hex.decode(receiverIdentityHex));

        client = new Mychain020Client(config.toJsonString().getBytes(StandardCharsets.UTF_8), LOGGER);
        Assert.assertTrue("startup mychain sdk failed", client.startup());
    }

    @AfterClass
    public static void shutdown() {
        if (client != null) {
            client.shutdown();
        }
    }

    @Test
    public void deployConfigureAndSendNormally() {
        String senderContract = "MonitorSender_" + UUID.randomUUID();
        Assert.assertTrue(
                "deploy monitor sender failed",
                client.deployContract(CONTRACT_BINARY, senderContract, VMTypeEnum.EVM, new EVMParameter()));

        EVMParameter configureParameters = new EVMParameter("setMonitorContract(identity)");
        configureParameters.addIdentity(Utils.getIdentityByName(
                monitorContract,
                client.getConfig().getMychainHashType()));
        assertSuccess(client.callContract(senderContract, configureParameters, true));

        String message = System.getProperty(
                "mychain.monitor.message",
                "mychain monitored demo");
        EVMParameter sendParameters = new EVMParameter("sendUnordered(identity,string,bytes)");
        sendParameters.addIdentity(receiverIdentity);
        sendParameters.addString(receiverDomain);
        sendParameters.addBytes(message.getBytes(StandardCharsets.UTF_8));
        SendResponseResult sendResult = client.callContract(senderContract, sendParameters, true);
        assertSuccess(sendResult);

        LOGGER.info("MONITOR_APP_SENDER_CONTRACT={}", senderContract);
        LOGGER.info("MONITOR_APP_SENDER_IDENTITY={}", Utils.getIdentityByName(
                senderContract,
                client.getConfig().getMychainHashType()).hexStrValue());
        LOGGER.info("MONITOR_APP_TX_HASH={}", sendResult.getTxId());
        LOGGER.info("MONITOR_APP_RECEIVER_DOMAIN={}", receiverDomain);
        LOGGER.info("MONITOR_APP_RECEIVER_IDENTITY={}", receiverIdentity.hexStrValue());
        LOGGER.info("MONITOR_APP_MESSAGE={}", message);
    }

    private static void assertSuccess(SendResponseResult result) {
        Assert.assertNotNull(result);
        Assert.assertTrue("transaction failed: " + result.getErrorMessage(), result.isSuccess());
    }

    private static boolean hasRequiredProperties() {
        return hasProperty("mychain.config.path")
                && hasProperty("mychain.monitor.contract")
                && hasProperty("mychain.receiver.domain")
                && hasProperty("mychain.receiver.identity");
    }

    private static boolean hasProperty(String name) {
        String value = System.getProperty(name);
        return value != null && !value.trim().isEmpty();
    }

    private static String normalizeIdentity(String identity) {
        String normalized = identity.startsWith("0x") ? identity.substring(2) : identity;
        if (normalized.length() != 64) {
            throw new IllegalArgumentException("identity must contain exactly 32 bytes");
        }
        return normalized;
    }

    private static String requiredProperty(String name) {
        String value = System.getProperty(name);
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("missing system property: " + name);
        }
        return value.trim();
    }
}

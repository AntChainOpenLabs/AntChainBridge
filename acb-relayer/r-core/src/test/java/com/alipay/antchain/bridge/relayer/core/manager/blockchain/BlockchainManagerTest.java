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

package com.alipay.antchain.bridge.relayer.core.manager.blockchain;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import com.alipay.antchain.bridge.commons.bbc.DefaultBBCContext;
import com.alipay.antchain.bridge.commons.bbc.syscontract.PTCContract;
import com.alipay.antchain.bridge.commons.core.base.CrossChainDomain;
import com.alipay.antchain.bridge.commons.core.ptc.ThirdPartyProof;
import com.alipay.antchain.bridge.commons.core.sdp.SDPMessageV1;
import com.alipay.antchain.bridge.relayer.commons.constant.BlockchainStateEnum;
import com.alipay.antchain.bridge.relayer.commons.model.AuthMsgWrapper;
import com.alipay.antchain.bridge.relayer.commons.model.BlockchainMeta;
import com.alipay.antchain.bridge.relayer.commons.model.SDPMsgWrapper;
import com.alipay.antchain.bridge.relayer.dal.repository.IBlockchainRepository;
import com.alipay.antchain.bridge.relayer.dal.repository.ICrossChainMessageRepository;
import org.junit.Assert;
import org.junit.Test;

public class BlockchainManagerTest {

    @Test
    public void hasConfiguredPtcContractShouldRejectLegacyTargetWithoutPtcHub() {
        Assert.assertFalse(BlockchainManager.hasConfiguredPtcContract(null));
        Assert.assertFalse(BlockchainManager.hasConfiguredPtcContract(new DefaultBBCContext()));

        DefaultBBCContext emptyContext = new DefaultBBCContext();
        PTCContract emptyPtc = new PTCContract();
        emptyPtc.setContractAddress("empty");
        emptyContext.setPtcContract(emptyPtc);
        Assert.assertFalse(BlockchainManager.hasConfiguredPtcContract(emptyContext));

        DefaultBBCContext configuredContext = new DefaultBBCContext();
        PTCContract configuredPtc = new PTCContract();
        configuredPtc.setContractAddress("ptc-contract-address");
        configuredContext.setPtcContract(configuredPtc);
        Assert.assertTrue(BlockchainManager.hasConfiguredPtcContract(configuredContext));
    }

    @Test
    public void checkTpBtaReadyShouldCheckSendingBlockchainBta() throws Exception {
        AtomicReference<CrossChainDomain> checkedDomain = new AtomicReference<>();
        IBlockchainRepository blockchainRepository = (IBlockchainRepository) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class[]{IBlockchainRepository.class},
                (proxy, method, args) -> {
                    if ("hasBta".equals(method.getName()) && args.length == 1) {
                        checkedDomain.set((CrossChainDomain) args[0]);
                        return false;
                    }
                    return defaultValue(method.getReturnType());
                }
        );
        ICrossChainMessageRepository crossChainMessageRepository =
                (ICrossChainMessageRepository) Proxy.newProxyInstance(
                        getClass().getClassLoader(),
                        new Class[]{ICrossChainMessageRepository.class},
                        (proxy, method, args) -> "getTpProof".equals(method.getName())
                                ? new ThirdPartyProof()
                                : defaultValue(method.getReturnType())
                );

        AuthMsgWrapper authMsgWrapper = new AuthMsgWrapper();
        authMsgWrapper.setDomain("source.example");
        authMsgWrapper.setUcpId("ucp-test");
        SDPMessageV1 sdpMessage = new SDPMessageV1();
        sdpMessage.setTargetDomain(new CrossChainDomain("target.example"));
        SDPMsgWrapper sdpMsgWrapper = new SDPMsgWrapper();
        sdpMsgWrapper.setAuthMsgWrapper(authMsgWrapper);
        sdpMsgWrapper.setSdpMessage(sdpMessage);

        BlockchainManager blockchainManager = new BlockchainManager();
        setField(blockchainManager, "blockchainRepository", blockchainRepository);
        setField(blockchainManager, "crossChainMessageRepository", crossChainMessageRepository);

        blockchainManager.checkTpBtaReadyOnReceivingChain(sdpMsgWrapper);

        Assert.assertEquals(new CrossChainDomain("source.example"), checkedDomain.get());
    }

    @Test
    public void updateBlockchainShouldPersistMergedProperties() {
        BlockchainMeta.BlockchainProperties originalProperties = new BlockchainMeta.BlockchainProperties();
        originalProperties.setPluginServerId("ps01");
        originalProperties.setBbcContext(new DefaultBBCContext());
        originalProperties.setAnchorRuntimeStatus(BlockchainStateEnum.INIT);
        originalProperties.getExtraProperties().put("preserved_property", "preserved_value");

        BlockchainMeta originalMeta = new BlockchainMeta(
                "mychain2",
                "my02.id",
                "old_alias",
                "old_desc",
                originalProperties
        );
        TestBlockchainManager blockchainManager = new TestBlockchainManager(originalMeta);

        Map<String, String> partialConfig = new HashMap<>();
        partialConfig.put("ptc_contract_address", "ptc_contract");
        partialConfig.put("monitor_contract_address", "monitor_contract");
        partialConfig.put("monitor_verifier_contract_address", "monitor_verifier_contract");

        blockchainManager.updateBlockchain(
                "mychain2",
                "my02.id",
                "",
                "new_alias",
                "new_desc",
                partialConfig
        );

        BlockchainMeta updatedMeta = blockchainManager.updatedMeta;
        Assert.assertSame(originalMeta, updatedMeta);
        Assert.assertEquals("new_alias", updatedMeta.getAlias());
        Assert.assertEquals("new_desc", updatedMeta.getDesc());
        Assert.assertEquals("ps01", updatedMeta.getPluginServerId());
        Assert.assertNotNull(updatedMeta.getProperties().getBbcContext());
        Assert.assertEquals("ptc_contract", updatedMeta.getProperties().getPtcContractAddress());
        Assert.assertEquals("monitor_contract", updatedMeta.getProperties().getMonitorContractAddress());
        Assert.assertEquals(
                "monitor_verifier_contract",
                updatedMeta.getProperties().getExtraProperties().get("monitor_verifier_contract_address")
        );
        Assert.assertEquals(
                "preserved_value",
                updatedMeta.getProperties().getExtraProperties().get("preserved_property")
        );
    }

    private static class TestBlockchainManager extends BlockchainManager {

        private final BlockchainMeta originalMeta;

        private BlockchainMeta updatedMeta;

        private TestBlockchainManager(BlockchainMeta originalMeta) {
            this.originalMeta = originalMeta;
        }

        @Override
        public BlockchainMeta getBlockchainMeta(String product, String blockchainId) {
            return originalMeta;
        }

        @Override
        public boolean updateBlockchainMeta(BlockchainMeta blockchainMeta) {
            updatedMeta = blockchainMeta;
            return true;
        }
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = BlockchainManager.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == char.class) {
            return '\0';
        }
        return 0;
    }
}

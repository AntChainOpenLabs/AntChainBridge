/*
 * Copyright 2024 Ant Group
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

package com.alipay.antchain.bridge.ptc.committee.monitor.node.service;

import java.util.List;

import com.alipay.antchain.bridge.plugins.manager.core.AntChainBridgePluginState;
import com.alipay.antchain.bridge.ptc.committee.monitor.node.TestBase;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.junit.Assert;
import org.junit.Test;

@Slf4j
public class HCDVSServiceTest extends TestBase {

    private static final String PLUGIN_PRODUCT = "testchain";

    private static final String PLUGIN_PATH = "./src/test/resources/plugins/plugin-testchain2-0.1-SNAPSHOT-plugin.jar";

    @Resource
    private IHcdvsPluginService hcdvsPluginService;

    @Test
    public void testHCDVSService() {
        List<String> supportProducts = hcdvsPluginService.getAvailableProducts();
        Assert.assertFalse(supportProducts.isEmpty());

        Assert.assertTrue(hcdvsPluginService.hasPlugin(PLUGIN_PRODUCT));
        hcdvsPluginService.stopPlugin(PLUGIN_PRODUCT);
        Assert.assertEquals(hcdvsPluginService.getPlugin(PLUGIN_PRODUCT).getCurrState(), AntChainBridgePluginState.STOP);
        hcdvsPluginService.startPluginFromStop(PLUGIN_PRODUCT);
        Assert.assertEquals(hcdvsPluginService.getPlugin(PLUGIN_PRODUCT).getCurrState(), AntChainBridgePluginState.START);
    }

    @Test
    public void testGetAvailableProduct() {
        List<String> supportProducts = hcdvsPluginService.getAvailableProducts();

        for (String product:supportProducts) {
            log.info("Committee-node's HcdvsService Contains products: {}", product);
        }
    }

    /*
    @Test
    public void testGetHCDVSService() {
        IHeteroChainDataVerifierService hcdvsService = hcdvsPluginService.getHCDVSService(PLUGIN_PRODUCT);
        hcdvsPluginService.stopPlugin(PLUGIN_PRODUCT); // stopPlugin逻辑里只是把state=STOP，要load指定PLUGIN需要一个unloadPlugin的接口，暂缺
        hcdvsPluginService.loadPlugin(PLUGIN_PATH);
        hcdvsPluginService.startPlugin(PLUGIN_PATH);
    }
     */

    @Test
    public void testReloadPluginWithoutPath() {
        if(hcdvsPluginService.hasPlugin(PLUGIN_PRODUCT)) {
            hcdvsPluginService.stopPlugin(PLUGIN_PRODUCT);
            Assert.assertEquals(hcdvsPluginService.getPlugin(PLUGIN_PRODUCT).getCurrState(), AntChainBridgePluginState.STOP);
        }
        hcdvsPluginService.reloadPlugin(PLUGIN_PRODUCT);
        Assert.assertEquals(hcdvsPluginService.getPlugin(PLUGIN_PRODUCT).getCurrState(), AntChainBridgePluginState.START);
    }

    @Test
    public void testReloadPluginWithPath() {
        if(hcdvsPluginService.hasPlugin(PLUGIN_PRODUCT)) {
            hcdvsPluginService.stopPlugin(PLUGIN_PRODUCT);
            Assert.assertEquals(hcdvsPluginService.getPlugin(PLUGIN_PRODUCT).getCurrState(), AntChainBridgePluginState.STOP);
        }
        hcdvsPluginService.reloadPlugin(PLUGIN_PRODUCT, PLUGIN_PATH);
        Assert.assertEquals(hcdvsPluginService.getPlugin(PLUGIN_PRODUCT).getCurrState(), AntChainBridgePluginState.START);
    }
}

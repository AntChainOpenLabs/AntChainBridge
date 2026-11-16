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

import com.alipay.antchain.bridge.plugins.manager.core.IAntChainBridgePlugin;
import com.alipay.antchain.bridge.plugins.spi.ptc.IHeteroChainDataVerifierService;

import java.util.List;

public interface IHcdvsPluginService {
    void loadPlugins();

    void startPlugins();

    void loadPlugin(String path);

    void startPlugin(String path);

    void stopPlugin(String product);

    void startPluginFromStop(String product);

    void reloadPlugin(String product);

    void reloadPlugin(String product, String path);

    IAntChainBridgePlugin getPlugin(String product);

    boolean hasPlugin(String product);

    IHeteroChainDataVerifierService createHCDVSService(String product);

    IHeteroChainDataVerifierService getHCDVSService(String product);

    boolean hasProduct(String product);

    List<String> getAvailableProducts();
}

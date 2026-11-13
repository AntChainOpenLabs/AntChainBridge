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

package com.alipay.antchain.bridge.plugins.spi.utils.pf4j;

import java.lang.reflect.Method;
import java.util.Set;
import java.util.stream.Collectors;

import cn.hutool.core.collection.ListUtil;
import cn.hutool.core.util.StrUtil;
import com.alipay.antchain.bridge.plugins.spi.bbc.IBBCService;
import com.alipay.antchain.bridge.plugins.spi.bbc.core.BBCVersionEnum;

public class Utils {

    private static final String FUNC_NAME_UPDATE_PTC_TRUST_ROOT = "updatePTCTrustRoot(com.alipay.antchain.bridge.commons.core.ptc.PTCTrustRoot)";
    private static final String FUNC_NAME_SETUP_MONITOR_CONTRACT = "setupMonitorContract()";

    public static BBCVersionEnum getBBCVersion(IBBCService bbcService) {
        Set<String> methods = ListUtil.toList(bbcService.getClass().getDeclaredMethods()).stream()
                .map(Method::toGenericString).collect(Collectors.toSet());
        if (methods.stream().anyMatch(x -> StrUtil.endWith(x, FUNC_NAME_UPDATE_PTC_TRUST_ROOT))) {
            return BBCVersionEnum.V1;
        }
        return BBCVersionEnum.V0;
    }

    public static boolean checkBBCSupportMonitorContract(IBBCService bbcService) {
        Set<String> methods = ListUtil.toList(bbcService.getClass().getDeclaredMethods()).stream()
                .map(Method::toGenericString).collect(Collectors.toSet());
        if (methods.stream().anyMatch(x -> StrUtil.endWith(x, FUNC_NAME_SETUP_MONITOR_CONTRACT))) {
            return true;
        }
        return false;
    }
}

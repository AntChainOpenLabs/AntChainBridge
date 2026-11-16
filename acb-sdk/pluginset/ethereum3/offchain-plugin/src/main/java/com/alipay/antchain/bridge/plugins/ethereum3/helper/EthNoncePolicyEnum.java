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

package com.alipay.antchain.bridge.plugins.ethereum3.helper;

public enum EthNoncePolicyEnum {

    FAST,

    /**
     * <p>
     *     Get nonce dynamically from ethereum node synchronously.
     * </p>
     * <p>
     *     Please check {@link AcbRawTransactionManager#getNonce() here}.
     * </p>
     */
    NORMAL
}

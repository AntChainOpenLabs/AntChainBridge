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

package com.alipay.antchain.bridge.plugins.spi.bbc.core.write;

import com.alipay.antchain.bridge.commons.core.base.CrossChainMessageReceipt;

/**
 * Through {@code IAMWriter}, you can write data
 * to the storage of the AMContract.
 */
public interface IAMWriter {

    /**
     * The SDP protocol is based on the AM protocol, so here
     * we need to set the address of the SDP contract to the AM contract.
     * The set protocol contract can call the interface of the AM contract.
     *
     * @param protocolAddress protocol contract address
     * @param protocolType    type of the protocol. sdp protocol is zero.
     */
    void setProtocol(String protocolAddress, String protocolType);

    /**
     * Set the address of the PTC contract into the AM contract.
     */
    void setPtcContract(String ptcContractAddress);

    /**
     * Relayer would commit the cross-chain messages through the method {@code relayAuthMessage}.
     *
     * @param rawMessage messages serialized from {@code AuthMessage}.
     * @return {@link CrossChainMessageReceipt}
     */
    CrossChainMessageReceipt relayAuthMessage(byte[] rawMessage);

    /**
     * Stable caller identity for durable submission. Legacy plugins retain their existing behavior.
     * Implementations must not derive this identity from the (possibly identical) business payload.
     */
    default CrossChainMessageReceipt relayAuthMessage(byte[] rawMessage, String submissionId) {
        return relayAuthMessage(rawMessage);
    }
}

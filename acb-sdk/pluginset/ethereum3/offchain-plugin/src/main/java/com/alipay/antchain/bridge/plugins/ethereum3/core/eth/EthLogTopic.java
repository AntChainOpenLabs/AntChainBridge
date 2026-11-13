/*
 * Copyright ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.alipay.antchain.bridge.plugins.ethereum3.core.eth;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.DelegatingBytes32;
import org.hyperledger.besu.ethereum.rlp.RLPInput;
import org.hyperledger.besu.ethereum.rlp.RLPOutput;

/**
 * The EthLog topic.
 */
public class EthLogTopic extends DelegatingBytes32 {

    /**
     * Instantiates a new EthLog topic.
     *
     * @param bytes the bytes
     */
    protected EthLogTopic(final Bytes bytes) {
        super(bytes);
    }

    /**
     * Create log topic.
     *
     * @param bytes the bytes
     * @return the log topic
     */
    public static EthLogTopic create(final Bytes bytes) {
        return new EthLogTopic(bytes);
    }

    /**
     * Wrap log topic.
     *
     * @param bytes the bytes
     * @return the log topic
     */
    public static EthLogTopic wrap(final Bytes bytes) {
        return new EthLogTopic(bytes);
    }

    /**
     * Instantiate EthLog Topic from copy of bytes.
     *
     * @param bytes the bytes
     * @return the log topic
     */
    public static EthLogTopic of(final Bytes bytes) {
        return new EthLogTopic(bytes.copy());
    }

    /**
     * Instantiate EthLog Topic from hex string
     *
     * @param str the str
     * @return the log topic
     */
    public static EthLogTopic fromHexString(final String str) {
        return str == null ? null : EthLogTopic.create(Bytes.fromHexString(str));
    }

    /**
     * Reads the log topic from the provided RLP input.
     *
     * @param in the input from which to decode the log topic.
     * @return the read log topic.
     */
    public static EthLogTopic readFrom(final RLPInput in) {
        return new EthLogTopic(in.readBytes());
    }

    /**
     * Writes the log topic to the provided RLP output.
     *
     * @param out the output in which to encode the log topic.
     */
    public void writeTo(final RLPOutput out) {
        out.writeBytes(this);
    }
}

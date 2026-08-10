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

/**
 * Through {@code ISDPWriter}, you can write data
 * to the storage of the SDPContract.
 */
public interface ISDPWriter {

    /**
     * Set the {@code AuthMessage} contract for our SDP contract.
     *
     * <p>
     *     We know that the SDP contract are based on AM contract.
     *     So before SDP process start, we need to set the
     *     contract address of AM contract.
     * </p>
     *
     * @param contractAddress am contract address
     */
    void setAmContract(String contractAddress);

    /**
     * Set the {@code domain} of this blockchain to {@code SDPMessage} contract.
     * 
     * @param domain the domain value
     */
    void setLocalDomain(String domain);

    void setMonitorContract(String contractAddress);
}

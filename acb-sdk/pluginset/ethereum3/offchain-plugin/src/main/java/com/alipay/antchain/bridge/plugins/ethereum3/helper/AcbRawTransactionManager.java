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

import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.methods.response.EthSendTransaction;
import org.web3j.service.TxSignService;
import org.web3j.tx.RawTransactionManager;

import java.io.IOException;
import java.math.BigInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class AcbRawTransactionManager extends RawTransactionManager {

    private final Lock sendTxLock;

    public AcbRawTransactionManager(Web3j web3j, Credentials credentials, long chainId) {
        super(web3j, credentials, chainId);
        sendTxLock = new ReentrantLock();
    }

    public AcbRawTransactionManager(Web3j web3j, TxSignService txSignService, long chainId) {
        super(web3j, txSignService, chainId);
        sendTxLock = new ReentrantLock();
    }

    protected synchronized BigInteger getNonce() throws IOException {
        return super.getNonce();
    }

    @Override
    public EthSendTransaction sendTransaction(BigInteger gasPrice, BigInteger gasLimit, String to, String data, BigInteger value, boolean constructor) throws IOException {
        sendTxLock.lock();
        try {
            return super.sendTransaction(gasPrice, gasLimit, to, data, value, constructor);
        } finally {
            sendTxLock.unlock();
        }
    }
}

package com.alipay.antchain.bridge.plugins.ethereum3.helper;

import cn.hutool.core.util.ObjectUtil;
import lombok.Getter;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.RawTransaction;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.methods.response.EthSendTransaction;
import org.web3j.service.TxSignService;
import org.web3j.tx.FastRawTransactionManager;

import java.io.IOException;
import java.math.BigInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

@Getter
public class AcbFastRawTransactionManager extends FastRawTransactionManager {

    private final Lock sendTxLock;

    public AcbFastRawTransactionManager(Web3j web3j, Credentials credentials, long chainId) {
        super(web3j, credentials, chainId);
        sendTxLock = new ReentrantLock();
    }

    public AcbFastRawTransactionManager(Web3j web3j, TxSignService txSignService, long chainId) {
        super(web3j, txSignService, chainId, BigInteger.valueOf(-1));
        sendTxLock = new ReentrantLock();
    }

    @Override
    public EthSendTransaction sendTransaction(BigInteger gasPrice, BigInteger gasLimit, String to, String data, BigInteger value, boolean constructor) throws IOException {
        sendTxLock.lock();
        try {
            BigInteger nonce = getNonce();
            RawTransaction rawTransaction =
                    RawTransaction.createTransaction(nonce, gasPrice, gasLimit, to, value, data);
            EthSendTransaction result = signAndSend(rawTransaction);
            if (ObjectUtil.isNull(result) || result.hasError()) {
                setNonce(nonce);
            }
            return result;
        } finally {
            sendTxLock.unlock();
        }
    }
}

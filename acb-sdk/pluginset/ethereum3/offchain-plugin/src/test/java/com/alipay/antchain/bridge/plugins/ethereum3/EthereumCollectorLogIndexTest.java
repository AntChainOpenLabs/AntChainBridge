package com.alipay.antchain.bridge.plugins.ethereum3;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.math.BigInteger;
import java.util.List;

import com.alipay.antchain.bridge.commons.core.base.CrossChainMessage;
import com.alipay.antchain.bridge.plugins.ethereum3.core.AcbEthClient;
import com.alipay.antchain.bridge.plugins.ethereum3.core.EthAuthMessageLog;
import com.alipay.antchain.bridge.plugins.ethereum3.core.eth.EthReceiptProof;
import org.apache.tuweni.bytes.Bytes32;
import org.junit.Test;
import org.slf4j.LoggerFactory;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.methods.response.*;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blocks.BeaconBlock;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

public class EthereumCollectorLogIndexTest {
    private static void field(Object object, String name, Object value) throws Exception {
        Field field = object.getClass().getSuperclass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(object, value);
    }

    private static TransactionReceipt receipt(int index, Log... logs) {
        TransactionReceipt receipt = new TransactionReceipt();
        receipt.setTransactionHash("0x" + (index == 0 ? "aa" : "bb").repeat(32));
        receipt.setTransactionIndex("0x" + index);
        receipt.setType("0x2");
        receipt.setStatus("0x1");
        receipt.setCumulativeGasUsed("0x100");
        receipt.setLogsBloom("0x" + "00".repeat(256));
        receipt.setLogs(List.of(logs));
        for (Log log : logs) {
            log.setTransactionHash(receipt.getTransactionHash());
            log.setTransactionIndex("0x" + index);
        }
        return receipt;
    }

    private static EthLog.LogObject log(int index, String body, boolean am) {
        EthLog.LogObject log = new EthLog.LogObject();
        log.setLogIndex("0x" + index);
        log.setBlockHash("0x" + "cd".repeat(32));
        log.setAddress("0x" + (am ? "11" : "22").repeat(20));
        log.setTopics(List.of("0x79b7516b1b7a6a39fb4b7b22e8667cd3744e5c27425292f8a9f49d1042c0c651"));
        log.setData("0x" + org.web3j.abi.FunctionEncoder.encodeConstructor(List.of(
                new org.web3j.abi.datatypes.DynamicBytes(body.getBytes()))).replaceFirst("^0x", ""));
        return log;
    }

    @Test public void blockScanAndFilterProduceTheSameTwoDistinctEvents() throws Exception {
        Web3j web3j = mock(Web3j.class, RETURNS_DEEP_STUBS);
        AcbEthClient client = mock(AcbEthClient.class, CALLS_REAL_METHODS);
        field(client, "web3j", web3j);
        field(client, "bbcLogger", LoggerFactory.getLogger(getClass()));

        var unrelated = log(0, "unrelated", false);
        var first = log(1, "first", true);
        var second = log(2, "second", true);
        var receipts = List.of(receipt(0, unrelated), receipt(1, first, second));

        var rpcReceipts = new EthGetBlockReceipts();
        rpcReceipts.setResult(receipts);
        when(web3j.ethGetBlockReceipts(any()).send()).thenReturn(rpcReceipts);
        var rpcLogs = new EthLog();
        rpcLogs.setResult(List.of(first, second));
        when(web3j.ethGetLogs(any()).send()).thenReturn(rpcLogs);
        var block = new EthBlock.Block();
        block.setTimestamp("0x123");
        var tx0 = new EthBlock.TransactionObject();
        tx0.setHash(receipts.get(0).getTransactionHash());
        var tx1 = new EthBlock.TransactionObject();
        tx1.setHash(receipts.get(1).getTransactionHash());
        block.setTransactions(List.of(tx0, tx1));
        var rpcBlock = new EthBlock();
        rpcBlock.setResult(block);
        when(web3j.ethGetBlockByNumber(any(), anyBoolean()).send()).thenReturn(rpcBlock);
        for (var receipt : receipts) {
            var response = new EthGetTransactionReceipt();
            response.setResult(receipt);
            when(web3j.ethGetTransactionReceipt(receipt.getTransactionHash()).send()).thenReturn(response);
        }
        BeaconBlock beacon = mock(BeaconBlock.class);
        when(beacon.getSlot()).thenReturn(UInt64.valueOf(123));
        when(beacon.getRoot()).thenReturn(Bytes32.fromHexString("0x" + "ef".repeat(32)));

        List<CrossChainMessage> previous = null;
        for (String methodName : List.of("readMessagesByFilter", "readMessagesFromEntireBlock")) {
            Method method = AcbEthClient.class.getDeclaredMethod(methodName, BeaconBlock.class, BigInteger.class, String.class);
            method.setAccessible(true);
            @SuppressWarnings("unchecked")
            var messages = (List<CrossChainMessage>) method.invoke(client, beacon, BigInteger.ONE, "0x" + "11".repeat(20));
            assertEquals(2, messages.size()); // The former filter path emitted 4 and mismatched payloads.
            for (int i = 0; i < messages.size(); i++) {
                var message = messages.get(i);
                var ledger = EthAuthMessageLog.decodeFromJson(new String(message.getProvableData().getLedgerData()));
                assertEquals(Integer.valueOf(i), ledger.getReceiptLogIndex());
                assertEquals(BigInteger.valueOf(i + 1), ledger.getSendAuthMessageLog().getLogIndex());
                assertArrayEquals((i == 0 ? "first" : "second").getBytes(), message.getMessage());
                var proof = EthReceiptProof.decodeFromJson(new String(message.getProvableData().getProof()));
                assertEquals(1, proof.getReceiptIndex());
                assertNotNull(proof.validateAndGetRoot());
                ledger.verifyReceiptLog(proof.getEthTransactionReceipt().getLogs(),
                        org.hyperledger.besu.datatypes.Address.fromHexString("0x" + "11".repeat(20)), message.getMessage());
                if (previous != null) assertArrayEquals(previous.get(i).encode(), message.encode());
            }
            previous = messages;
        }
    }
}

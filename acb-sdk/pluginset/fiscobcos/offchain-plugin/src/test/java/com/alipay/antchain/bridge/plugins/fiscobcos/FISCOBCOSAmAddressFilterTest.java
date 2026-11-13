package com.alipay.antchain.bridge.plugins.fiscobcos;

import com.alibaba.fastjson.JSON;
import org.fisco.bcos.sdk.v3.model.TransactionReceipt;
import org.fisco.bcos.sdk.v3.transaction.codec.decode.TransactionDecoderInterface;
import org.fisco.bcos.sdk.v3.transaction.codec.decode.TransactionDecoderService;
import org.junit.Test;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;

/** Offline decoding only: no SDK client, native hash library or chain connection. */
public class FISCOBCOSAmAddressFilterTest {
    private static final String AM = "0x1234567890abcdef1234567890abcdef12345678";
    private static final String OTHER_AM = "0xabcdefabcdefabcdefabcdefabcdefabcdefabcd";
    private static final byte[] PAYLOAD = "auth-message".getBytes(StandardCharsets.UTF_8);

    private final TransactionDecoderInterface decoder =
            new TransactionDecoderService(new PureJavaKeccak256(), false);

    private static TransactionReceipt.Logs event(String address, boolean stringAbi, byte[] message) {
        TransactionReceipt.Logs log = new TransactionReceipt.Logs();
        log.setAddress(address);
        String type = stringAbi ? "string" : "bytes";
        log.setTopics(List.of("0x" + new PureJavaKeccak256().hash("SendAuthMessage(" + type + ")")));
        byte[] data = stringAbi ? hex(message).getBytes(StandardCharsets.UTF_8) : message;
        ByteBuffer abiData = ByteBuffer.allocate(64 + ((data.length + 31) / 32) * 32);
        abiData.position(28);
        abiData.putInt(32);
        abiData.position(60);
        abiData.putInt(data.length);
        abiData.put(data);
        log.setData("0x" + hex(abiData.array()));
        return log;
    }

    private static String hex(byte[] bytes) {
        StringBuilder out = new StringBuilder();
        for (byte value : bytes) out.append(String.format("%02x", value & 255));
        return out.toString();
    }

    private List<byte[]> decode(List<TransactionReceipt.Logs> logs, String am) throws Exception {
        return FISCOBCOSBBCService.decodeSendAuthMessages(decoder, logs, am);
    }

    @Test
    public void blockContainingBothLogicalChainsReturnsOnlyCurrentAmMessages() throws Exception {
        List<List<TransactionReceipt.Logs>> receipts = List.of(
                List.of(event(OTHER_AM, false, PAYLOAD)),
                List.of(event(AM, false, PAYLOAD)),
                List.of(event(OTHER_AM, true, PAYLOAD)));
        List<byte[]> captured = new ArrayList<>();
        for (var logs : receipts) captured.addAll(decode(logs, AM));
        assertEquals(1, captured.size());
        assertArrayEquals(PAYLOAD, captured.get(0));
    }

    @Test
    public void mixedReceiptDoesNotAcceptAnotherAmWithIdenticalPayload() throws Exception {
        var foreign = event(OTHER_AM, false, PAYLOAD);
        var current = event(AM, false, PAYLOAD);
        assertEquals(List.of(current), FISCOBCOSBBCService.filterLogsByAmAddress(List.of(foreign, current), AM));
        assertEquals(1, decode(List.of(foreign, current), AM).size());
    }

    @Test
    public void applicationEntryPointDoesNotExcludeInternalAmEvent() throws Exception {
        TransactionReceipt receipt = new TransactionReceipt();
        receipt.setTo(OTHER_AM);
        receipt.setLogEntries(List.of(event(AM, false, PAYLOAD)));
        assertArrayEquals(PAYLOAD, decode(receipt.getLogEntries(), AM).get(0));
    }

    @Test
    public void addressesMatchAcrossCaseAndPrefixFormats() throws Exception {
        for (String configured : List.of(AM, AM.substring(2), AM.toUpperCase(), " " + AM + " ")) {
            for (String emitter : List.of(AM, AM.substring(2), AM.toUpperCase())) {
                assertEquals(1, decode(List.of(event(emitter, true, PAYLOAD)), configured).size());
            }
        }
    }

    @Test
    public void invalidConfiguredAddressIsRejectedEvenWithoutLogs() throws Exception {
        for (String address : Arrays.asList(null, "", "0x", AM.substring(0, AM.length() - 1), "g".repeat(40))) {
            try {
                decode(Collections.emptyList(), address);
                fail("invalid AM address must not enable an unfiltered scan");
            } catch (IllegalArgumentException expected) {
                assertTrue(expected.getMessage().contains("AM contract address"));
            }
        }
    }

    @Test
    public void missingOrMalformedEmitterIsNeverAccepted() throws Exception {
        List<TransactionReceipt.Logs> logs = new ArrayList<>();
        logs.add(null);
        for (String address : Arrays.asList(null, "", "0x1234", "z".repeat(40))) {
            logs.add(event(address, false, PAYLOAD));
        }
        assertTrue(decode(logs, AM).isEmpty());
        assertTrue(decode(null, AM).isEmpty());
    }

    @Test
    public void bytesAndHexStringAbisRemainSupported() throws Exception {
        for (boolean stringAbi : List.of(false, true)) {
            List<byte[]> messages = decode(List.of(event(AM, stringAbi, PAYLOAD)), AM);
            assertEquals(1, messages.size());
            assertArrayEquals(PAYLOAD, messages.get(0));
        }
    }

    @Test
    public void receiptProofAndOriginalLogOrderRemainUntouched() throws Exception {
        var first = event(AM, false, PAYLOAD);
        var foreign = event(OTHER_AM, true, PAYLOAD);
        var last = event(AM, true, new byte[]{1, 2, 3});
        TransactionReceipt receipt = new TransactionReceipt();
        receipt.setLogEntries(List.of(first, foreign, last));
        receipt.setTxReceiptProof(List.of("receipt-proof-unchanged"));
        String original = JSON.toJSONString(receipt);
        var selected = FISCOBCOSBBCService.filterLogsByAmAddress(receipt.getLogEntries(), AM);
        assertEquals(List.of(first, last), selected);
        assertSame(first, selected.get(0));
        assertSame(last, selected.get(1));
        List<byte[]> messages = decode(receipt.getLogEntries(), AM);
        assertEquals(2, messages.size());
        assertArrayEquals(PAYLOAD, messages.get(0));
        assertArrayEquals(new byte[]{1, 2, 3}, messages.get(1));
        assertEquals(original, JSON.toJSONString(receipt));
    }
}

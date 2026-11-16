package org.antchainbridge.experiment;
import org.junit.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.math.BigInteger;
import java.util.*;

public class ReceptionCollectorTest {
    static byte[] word(int n){byte[] b=new byte[32],v=BigInteger.valueOf(n).toByteArray();System.arraycopy(v,0,b,32-v.length,v.length);return b;}
    static byte[] pad(byte[] b){return Arrays.copyOf(b,((b.length+31)/32)*32);}
    static String event(String profile,byte[] payload)throws Exception {
        var bytes=new java.io.ByteArrayOutputStream();int head=profile.equals("mychain-legacy")?96:128;byte[] domain="eth.example".getBytes(StandardCharsets.UTF_8);
        bytes.write(word(head));bytes.write(HexFormat.of().parseHex(OpenLoopWorkloadTest.SOURCE));bytes.write(word(head+32+pad(domain).length));
        if(head==128)bytes.write(word(0));bytes.write(word(domain.length));bytes.write(pad(domain));bytes.write(word(payload.length));bytes.write(pad(payload));return HexFormat.of().formatHex(bytes.toByteArray());
    }
    static ReceptionCollector collector(){return new ReceptionCollector(new ReceptionCollector.Timer(){long t;public long millis(){return t;}public void sleep(long ms){t+=ms;}});}
    @Test public void allExistingReceiveAbisPreserveExact512Bytes()throws Exception {
        byte[] payload=ExperimentPayload.create("exp","Xun","ETH-MYC",1,0);
        for(String profile:List.of("evm-app","mychain-legacy","mychain-monitor")) {
            var e=ReceptionDecoder.decode(profile,new ChainReader.Log(OpenLoopWorkloadTest.TARGET,List.of("00"),event(profile,payload),0));
            Assert.assertArrayEquals(payload,Base64.getDecoder().decode((String)e.get("payloadBase64")));Assert.assertEquals("eth.example",e.get("sourceDomain"));
        }
    }
    @Test public void malformedAbiIsRejected()throws Exception {
        Assert.assertThrows(IllegalArgumentException.class,()->ReceptionDecoder.decode("evm-app",new ChainReader.Log(OpenLoopWorkloadTest.TARGET,List.of("00"),"00",0)));
    }
    @Test public void fullScanRetainsDuplicatesAcrossTransactions()throws Exception {
        Path root=Files.createTempDirectory("paper-scan-");var c=OpenLoopWorkloadTest.config(root,"run",1);var session=OpenLoopWorkloadTest.ready(c);String data=event("mychain-legacy",session.payload(0));
        OpenLoopWorkloadTest.SpyReader reader=new OpenLoopWorkloadTest.SpyReader(){
            public Head finalizedHead(){return new Head(3,"h3");}
            public List<Receipt> receipts(long h){return h==1?List.of():List.of(new Receipt("tx"+h,h,"h"+h,true,List.of(new Log(OpenLoopWorkloadTest.TARGET,List.of("00"),data,0))));}
        };
        Assert.assertTrue(collector().collect(session.plan,reader,root.resolve("scan"),()->true,100));
        Assert.assertEquals(0,reader.broadcasts);
        Assert.assertEquals(2,EvidenceIO.lines(root.resolve("scan/receptions.jsonl"),false).size());
        var coverage=EvidenceIO.read(root.resolve("scan/reception-coverage.json"));Assert.assertEquals(60,((Number)coverage.get("observationSeconds")).intValue());Assert.assertEquals(3,((Number)coverage.get("scannedBlocks")).intValue());
    }
    @Test public void rpcFailureIsIncompleteAndNeverPublishesComplete()throws Exception {
        Path root=Files.createTempDirectory("paper-scan-");var s=OpenLoopWorkloadTest.ready(OpenLoopWorkloadTest.config(root,"run",1));
        ChainReader reader=new OpenLoopWorkloadTest.FakeReader(){public List<Receipt> receipts(long h){throw new IllegalStateException("RPC down");}};
        Assert.assertFalse(collector().collect(s.plan,reader,root.resolve("scan"),()->true,100));Assert.assertEquals(false,EvidenceIO.read(root.resolve("scan/reception-coverage.json")).get("complete"));
    }
    @Test public void baselineReorgStopsVerification()throws Exception {
        Path root=Files.createTempDirectory("paper-scan-");var s=OpenLoopWorkloadTest.ready(OpenLoopWorkloadTest.config(root,"run",1));
        ChainReader reader=new OpenLoopWorkloadTest.FakeReader(){public String blockHash(long h){return "changed";}};
        Assert.assertFalse(collector().collect(s.plan,reader,root.resolve("scan"),()->true,100));
    }
    @Test public void terminalTimeoutIsIncomplete()throws Exception {
        Path root=Files.createTempDirectory("paper-scan-");var s=OpenLoopWorkloadTest.ready(OpenLoopWorkloadTest.config(root,"run",1));
        Assert.assertFalse(collector().collect(s.plan,new OpenLoopWorkloadTest.FakeReader(),root.resolve("scan"),()->false,100));
    }
    @Test public void midScanReorgPreservesPartialEvidenceButFails()throws Exception {
        Path root=Files.createTempDirectory("paper-scan-");var s=OpenLoopWorkloadTest.ready(OpenLoopWorkloadTest.config(root,"run",1));String data=event("mychain-legacy",s.payload(0));
        OpenLoopWorkloadTest.SpyReader reader=new OpenLoopWorkloadTest.SpyReader(){
            int hash2Calls;
            public Head finalizedHead(){return new Head(3,"h3");}
            public String blockHash(long h){return h==2 && ++hash2Calls>1?"reorg":"h"+h;}
            public List<Receipt> receipts(long h){return h==2?List.of(new Receipt("tx2",2,"h2",true,List.of(new Log(OpenLoopWorkloadTest.TARGET,List.of("00"),data,0)))):List.of();}
        };
        Assert.assertFalse(collector().collect(s.plan,reader,root.resolve("scan"),()->true,100));
        Assert.assertEquals(0,reader.broadcasts);Assert.assertEquals(false,EvidenceIO.read(root.resolve("scan/reception-coverage.json")).get("complete"));
        Assert.assertTrue(Files.exists(root.resolve("scan/receptions.jsonl")));
    }
}

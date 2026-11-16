package org.antchainbridge.experiment;

import org.junit.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.io.IOException;

public class OpenLoopWorkloadTest {
    static final String SOURCE="a".repeat(64),TARGET="b".repeat(64);
    static ExperimentConfig config(Path root,String output,int count)throws Exception {
        Path p=root.resolve(UUID.randomUUID()+".properties");
        Files.writeString(p,"experiment.id=exp\nexperiment.scheme=Xun\nexperiment.route=ETH-MYC\nexperiment.round=1\nexperiment.state.dir="+root.resolve("state")+
            "\nsource.product=ethereum3\nsource.blockchain.id=eth.id\nsource.domain=eth.example\nsource.app=0x"+SOURCE.substring(24)+
            "\ntarget.app.mode=identity\ntarget.product=mychain2\ntarget.blockchain.id=my.id\ntarget.domain=my.example\ntarget.app="+TARGET+"\ntarget.event.profile=mychain-legacy\ncode.revision=test\noutput.dir="+root.resolve(output)+"\nworkload.rate=1\nworkload.duration.seconds="+count+"\nworkload.workers=1\n");
        return ExperimentConfig.load(p);
    }
    static RunSession ready(ExperimentConfig c)throws Exception {var s=RunSession.claim(c);s.ready(SOURCE,TARGET,new ChainReader.Head(1,"h1"));return s;}
    static final class FakeTime implements OpenLoopWorkload.TimeSource {
        long n;
        public long currentTimeMillis(){return 1700000000000L+n/1000000;}
        public long nanoTime(){return n;}public void waitUntil(long deadline){n=deadline;}
    }
    @Test public void scheduleHas150Unique512ByteSamples()throws Exception {
        Path root=Files.createTempDirectory("paper-v2-");ExperimentConfig c=config(root,"run",150);var s=ready(c);
        AtomicInteger calls=new AtomicInteger();Set<String> payloads=ConcurrentHashMap.newKeySet();
        var result=new OpenLoopWorkload(new FakeTime()).run(c,s,bytes->{Assert.assertEquals(512,bytes.length);payloads.add(Base64.getEncoder().encodeToString(bytes));return String.format("%064x",calls.incrementAndGet());});
        Assert.assertEquals(150,calls.get());Assert.assertEquals(150,payloads.size());Assert.assertEquals(150,result.getRecords().size());
        Assert.assertTrue(Files.exists(s.output.resolve("run-complete.json")));
        Assert.assertEquals(300,EvidenceIO.lines(s.output.resolve("attempts.jsonl"),false).size());
    }
    @Test public void formalScheduleUses200msAndFiveRounds()throws Exception {
        Path root=Files.createTempDirectory("paper-v2-");ExperimentConfig original=config(root,"run",1);
        // Build a formal properties file with a harmless artifact; no chain connection.
        Path artifact=root.resolve("artifact");Files.writeString(artifact,"mock");Path props=Files.list(root).filter(p->p.toString().endsWith(".properties")).findFirst().orElseThrow();
        Files.writeString(props,"\nexperiment.formal=true\nworkload.rate=5\nworkload.duration.seconds=30\nclock.offset.ms=0\nrelayer.clock.offset.ms=0\ncode.revision=aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\nplugin.artifact="+artifact+"\n",StandardOpenOption.APPEND);
        var c=ExperimentConfig.load(props);var s=ready(c);var r=new OpenLoopWorkload(new FakeTime()).run(c,s,p->"1".repeat(64));
        Assert.assertEquals(150,r.getRecords().size());Assert.assertEquals(200,r.getIntervalMillis());
        Assert.assertEquals(29800,r.getRecords().get(149).getScheduledAtEpochMs()-r.getRecords().get(0).getScheduledAtEpochMs());
        Assert.assertEquals(5,s.plan.get("roundsPerScheme"));
    }
    @Test public void existingOutputStopsBeforeSdkConstruction()throws Exception {
        Path root=Files.createTempDirectory("paper-v2-");var c=config(root,"run",1);Files.createDirectory(root.resolve("run"));AtomicInteger opened=new AtomicInteger();
        Assert.assertThrows(FileAlreadyExistsException.class,()->RunCommands.send(c,()->{opened.incrementAndGet();return p->"hash";},()->{opened.incrementAndGet();return null;}));
        Assert.assertEquals(0,opened.get());
    }
    @Test public void sameLogicalRoundCannotChangeOutputAndRetry()throws Exception {
        Path root=Files.createTempDirectory("paper-v2-");ready(config(root,"one",1));
        Assert.assertThrows(FileAlreadyExistsException.class,()->RunSession.claim(config(root,"two",1)));
    }
    @Test public void concurrentClaimHasExactlyOneWinner()throws Exception {
        Path root=Files.createTempDirectory("paper-v2-");var a=config(root,"one",1);var b=config(root,"two",1);var pool=Executors.newFixedThreadPool(2);AtomicInteger wins=new AtomicInteger();
        try {List<Future<?>> f=List.of(pool.submit(()->{try{RunSession.claim(a);wins.incrementAndGet();}catch(Exception ignored){}}),pool.submit(()->{try{RunSession.claim(b);wins.incrementAndGet();}catch(Exception ignored){}}));for(var x:f)x.get();}finally{pool.shutdownNow();}
        Assert.assertEquals(1,wins.get());
    }
    @Test public void intentForceFailurePreventsBroadcast()throws Exception {
        Path root=Files.createTempDirectory("paper-v2-");var c=config(root,"run",1);var s=ready(c);AtomicInteger calls=new AtomicInteger();
        OpenLoopWorkload.WriterFactory broken=path->new EvidenceIO.Sink(){public void append(Object o)throws IOException{throw new IOException("simulated force failure");}public void close(){}};
        Assert.assertThrows(ExecutionException.class,()->new OpenLoopWorkload(new FakeTime(),broken).run(c,s,p->{calls.incrementAndGet();return "hash";}));
        Assert.assertEquals(0,calls.get());Assert.assertFalse(Files.exists(s.output.resolve("run-complete.json")));
    }
    @Test public void responseLossIsUnknownAndNeverRetried()throws Exception {
        Path root=Files.createTempDirectory("paper-v2-");var c=config(root,"run",1);var s=ready(c);AtomicInteger calls=new AtomicInteger();
        var r=new OpenLoopWorkload(new FakeTime()).run(c,s,p->{calls.incrementAndGet();throw new IOException("response lost");});
        Assert.assertEquals("UNKNOWN",r.getRecords().get(0).getSendStatus());Assert.assertEquals(1,calls.get());
        Assert.assertThrows(FileAlreadyExistsException.class,()->RunSession.claim(c));
    }
    @Test public void definitiveFailureKeepsOriginalHash()throws Exception {
        Path root=Files.createTempDirectory("paper-v2-");var c=config(root,"run",1);var s=ready(c);
        var r=new OpenLoopWorkload(new FakeTime()).run(c,s,p->{throw new TransactionSender.SubmissionFailure("1".repeat(64),true);});
        Assert.assertEquals("SEND_FAILED",r.getRecords().get(0).getSendStatus());
        Assert.assertEquals("1".repeat(64),EvidenceIO.lines(s.output.resolve("sender.jsonl"),false).get(0).get("sourceTxHashNative"));
    }
    @Test public void simulationFailureMakesZeroSourceCalls()throws Exception {
        Path root=Files.createTempDirectory("paper-v2-");var c=config(root,"run",1);AtomicInteger calls=new AtomicInteger();
        TransactionSender sender=new TransactionSender(){public String appIdentity(){return SOURCE;}public void preflight(byte[] b,String target){throw new IllegalStateException("simulation rejected");}public String send(byte[] b){calls.incrementAndGet();return "hash";}};
        Assert.assertThrows(IllegalStateException.class,()->RunCommands.send(c,()->sender,()->new FakeReader()));
        Assert.assertEquals(0,calls.get());Assert.assertTrue(Files.exists(root.resolve("run/run-plan.json")));Assert.assertFalse(Files.exists(root.resolve("run/attempts.jsonl")));
    }
    @Test public void readOnlyReconcilePreservesTornEvidenceAndUnknownHash()throws Exception {
        Path root=Files.createTempDirectory("paper-v2-");var c=config(root,"run",2);var s=ready(c);
        var row=s.record(0);row.put("event","STARTED");row.put("sourceTxHashNative","1".repeat(64));
        EvidenceIO.create(s.output.resolve("attempts.jsonl"),row);Files.writeString(s.output.resolve("attempts.jsonl"),"{\"torn\":",StandardOpenOption.APPEND);
        byte[] before=Files.readAllBytes(s.output.resolve("attempts.jsonl"));SpyReader reader=new SpyReader(){public String appIdentity(){return SOURCE;}public Receipt receipt(String h){return new Receipt(h,1,"h1",true,List.of());}};
        RunCommands.reconcile(c,s.output,root.resolve("reconcile"),()->reader);
        var rows=EvidenceIO.lines(root.resolve("reconcile/reconciliation.jsonl"),false);Assert.assertEquals("SOURCE_CONFIRMED",rows.get(0).get("reconciliationStatus"));Assert.assertEquals("NOT_SENT",rows.get(1).get("reconciliationStatus"));
        Assert.assertEquals(0,reader.broadcasts);
        Assert.assertArrayEquals(before,Files.readAllBytes(s.output.resolve("attempts.jsonl")));Assert.assertFalse(Files.exists(s.output.resolve("run-complete.json")));
    }
    @Test public void missingHashDoesNotOpenSourceSdk()throws Exception {
        Path root=Files.createTempDirectory("paper-v2-");var c=config(root,"run",1);var s=ready(c);var row=s.record(0);row.put("event","STARTED");EvidenceIO.create(s.output.resolve("attempts.jsonl"),row);
        RunCommands.reconcile(c,s.output,root.resolve("reconcile"),()->{throw new AssertionError("must not open source");});
        Assert.assertEquals("UNKNOWN",EvidenceIO.lines(root.resolve("reconcile/reconciliation.jsonl"),false).get(0).get("reconciliationStatus"));
    }
    static class FakeReader implements ChainReader {
        public String appIdentity(){return TARGET;}public String eventTopic(String s){return "00";}
        public Head finalizedHead(){return new Head(1,"h1");}public String blockHash(long h){return "h"+h;}
        public List<Receipt> receipts(long h){return List.of();}public Receipt receipt(String h){return null;}
    }
    static class SpyReader extends FakeReader implements TransactionSender {
        int broadcasts;
        public String send(byte[] p){broadcasts++;throw new AssertionError("read-only command attempted broadcast");}
        public void close(){}
    }
}

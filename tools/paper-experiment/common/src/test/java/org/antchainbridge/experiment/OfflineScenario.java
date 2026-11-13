package org.antchainbridge.experiment;

import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Explicitly synthetic end-to-end file-format fixture. No SDK, RPC or blockchain is used. */
public final class OfflineScenario {
    public static void main(String[] args)throws Exception {
        Path root=Path.of(args[0]);EvidenceIO.directory(root);Path props=root.resolve("synthetic.properties");
        Files.writeString(props,"experiment.id=SYNTHETIC-DO-NOT-PUBLISH\nexperiment.scheme=Xun\nexperiment.route=ETH-MYC\nexperiment.round=1\nexperiment.state.dir="+root.resolve("state")+
            "\nsource.product=ethereum3\nsource.blockchain.id=synthetic.eth\nsource.domain=eth.example\nsource.app=0x"+"a".repeat(40)+
            "\ntarget.product=mychain2\ntarget.blockchain.id=synthetic.my\ntarget.domain=my.example\ntarget.app="+"b".repeat(64)+"\ntarget.app.mode=identity\ntarget.event.profile=mychain-legacy\ncode.revision=synthetic-fixture\noutput.dir="+root.resolve("run")+"\nworkload.rate=5\nworkload.duration.seconds=30\nworkload.workers=1\n");
        ExperimentConfig c=ExperimentConfig.load(props);RunSession session=RunSession.claim(c);String author="0".repeat(24)+"a".repeat(40),receiver="b".repeat(64);
        session.ready(author,receiver,new ChainReader.Head(1,"h1"));AtomicInteger time=new AtomicInteger(-1),sends=new AtomicInteger();
        OpenLoopWorkload.TimeSource clock=new OpenLoopWorkload.TimeSource(){public long currentTimeMillis(){return 1700000000000L+Math.max(0,time.getAndIncrement())*200L;}public long nanoTime(){return 0;}public void waitUntil(long n){}};
        new OpenLoopWorkload(clock).run(c,session,p->String.format("%064x",sends.incrementAndGet()));
        try(EvidenceIO.Journal metrics=new EvidenceIO.Journal(root.resolve("metrics.jsonl"))) {
            int i=0;for(var row:EvidenceIO.lines(session.output.resolve("sender.jsonl"),false)) {
                String u=String.format("%064x",i+10000),target=String.format("%064x",i+1000);long t=((Number)row.get("t0EpochMs")).longValue();
                for(int stage=0;stage<3;stage++) {
                    Map<String,Object> e=new LinkedHashMap<>();e.put("schemaVersion",1);e.put("sourceProduct","ethereum3");e.put("sourceBlockchainId","synthetic.eth");
                    e.put("targetProduct","mychain2");e.put("targetBlockchainId","synthetic.my");e.put("ucpId",u);e.put("sourceTxHashKey",row.get("sourceTxHashKey"));e.put("success",true);
                    e.put("event",List.of("SOURCE_ACCEPTED","TARGET_SUBMITTING","TARGET_CONFIRMED").get(stage));e.put("eventTimeEpochMs",t+List.of(20,60,100).get(stage));
                    if(stage>0)e.put("targetTxHash",target);metrics.append(e);
                }i++;
            }
        }
        // Prefix the synthetic metric JSONL exactly as the production metrics appender does.
        Path metricFile=root.resolve("metrics.log");StringBuilder prefixed=new StringBuilder();
        for(String line:Files.readAllLines(root.resolve("metrics.jsonl")))prefixed.append("EXPERIMENT_METRIC ").append(line).append('\n');Files.writeString(metricFile,prefixed,StandardOpenOption.CREATE_NEW);
        ChainReader reader=new ChainReader(){
            public String appIdentity(){return receiver;}public String eventTopic(String s){return "00";}
            public Head finalizedHead(){return new Head(2,"h2");}public String blockHash(long h){return "h"+h;}
            public Receipt receipt(String h){return null;}
            public List<Receipt> receipts(long h)throws Exception {
                if(h==1)return List.of();List<Receipt> rows=new ArrayList<>();
                for(int i=0;i<150;i++) {
                    byte[] payload=session.payload(i),domain="eth.example".getBytes(StandardCharsets.UTF_8);
                    var abi=new java.io.ByteArrayOutputStream();abi.write(word(96));abi.write(HexFormat.of().parseHex(author));abi.write(word(160));abi.write(word(domain.length));abi.write(Arrays.copyOf(domain,32));abi.write(word(payload.length));abi.write(payload);
                    rows.add(new Receipt(String.format("%064x",i+1000),2,"h2",true,List.of(new Log(receiver,List.of("00"),HexFormat.of().formatHex(abi.toByteArray()),0))));
                }return rows;
            }
        };
        ReceptionCollector.Timer timer=new ReceptionCollector.Timer(){long t;public long millis(){return t;}public void sleep(long ms){t+=ms;}};
        if(!new ReceptionCollector(timer).collect(session.plan,reader,root.resolve("reception"),()->true,100))throw new IllegalStateException("synthetic scan failed");
        System.out.println("SYNTHETIC ONLY: "+sends.get()+" source calls; root="+root);
    }
    private static byte[] word(int n){byte[] w=new byte[32],v=java.math.BigInteger.valueOf(n).toByteArray();System.arraycopy(v,0,w,32-v.length,v.length);return w;}
}

package org.antchainbridge.experiment;

import java.nio.file.*;
import java.util.*;
import java.util.function.BooleanSupplier;

/** Complete block/receipt scan of a bounded finalized observation window. */
public final class ReceptionCollector {
    public interface Timer {long millis();void sleep(long ms)throws Exception;}
    private final Timer timer;
    public ReceptionCollector(){this(new Timer(){public long millis(){return System.nanoTime()/1000000;}public void sleep(long ms)throws Exception{Thread.sleep(ms);}});}
    public ReceptionCollector(Timer timer){this.timer=timer;}
    public boolean collect(Map<String,Object> m,ChainReader reader,Path output,BooleanSupplier terminal,long waitMs)throws Exception {
        EvidenceIO.directory(output);
        Map<String,Object> coverage=new LinkedHashMap<>();
        for(String k:List.of("runId","targetProduct","targetBlockchainId","targetAppIdentity","targetStartHeight","targetStartHash"))coverage.put(k,m.get(k));
        coverage.put("schemaVersion",2);coverage.put("complete",false);coverage.put("terminalReached",false);coverage.put("observationSeconds",0);
        String expected=Identities.canonical((String)m.get("targetAppIdentity"));
        long start=((Number)m.get("targetStartHeight")).longValue();String startHash=(String)m.get("targetStartHash");
        String profile=(String)m.get("eventProfile");String topic=Identities.hex(reader.eventTopic(ReceptionDecoder.signature(profile)));
        try(EvidenceIO.Journal events=new EvidenceIO.Journal(output.resolve("receptions.jsonl"))) {
            if(!expected.equals(Identities.canonical(reader.appIdentity())))throw new IllegalStateException("target identity changed");
            if(!startHash.equalsIgnoreCase(reader.blockHash(start)))throw new IllegalStateException("baseline block changed");
            long deadline=timer.millis()+waitMs;
            while(!terminal.getAsBoolean()) {
                if(timer.millis()>=deadline)throw new IllegalStateException("terminal observation timeout");timer.sleep(Math.min(1000,deadline-timer.millis()));
            }
            coverage.put("terminalReached",true);
            long settled=timer.millis();while(timer.millis()-settled<60000)timer.sleep(Math.min(1000,60000-(timer.millis()-settled)));
            coverage.put("observationSeconds",(timer.millis()-settled)/1000);
            ChainReader.Head end=reader.finalizedHead();coverage.put("endHeight",end.height());coverage.put("endHash",end.hash());
            if(end.height()<start)throw new IllegalStateException("chain regressed");
            long blocks=0;
            for(long height=start;height<=end.height();height++) {
                String hash=reader.blockHash(height);
                for(var receipt:reader.receipts(height)) {
                    if(receipt.height()!=height || !hash.equalsIgnoreCase(receipt.blockHash()))throw new IllegalStateException("receipt/block mismatch");
                    Set<Integer> indices=new HashSet<>();
                    for(var log:receipt.logs()) {
                        if(!indices.add(log.index()))throw new IllegalStateException("duplicate receipt log position");
                        if(!Identities.canonical(log.address()).equals(expected) || log.topics().isEmpty() || !Identities.hex(log.topics().get(0)).equals(topic))continue;
                        if(log.topics().size()!=1)throw new IllegalStateException("unexpected indexed receive arguments");
                        Map<String,Object> event=ReceptionDecoder.decode(profile,log);
                        event.put("schemaVersion",2);event.put("runId",m.get("runId"));event.put("targetProduct",m.get("targetProduct"));
                        event.put("targetBlockchainId",m.get("targetBlockchainId"));event.put("appIdentity",expected);
                        event.put("blockHeight",height);event.put("blockHash",hash);event.put("targetTxHash",receipt.hash());event.put("logIndex",log.index());
                        event.put("receiptSuccessful",receipt.successful());event.put("finalized",true);events.append(event);
                    }
                }
                if(!hash.equalsIgnoreCase(reader.blockHash(height)))throw new IllegalStateException("block changed during scan");blocks++;
            }
            if(!startHash.equalsIgnoreCase(reader.blockHash(start)) || !end.hash().equalsIgnoreCase(reader.blockHash(end.height())))
                throw new IllegalStateException("canonical window changed");
            coverage.put("scannedBlocks",blocks);coverage.put("complete",true);return true;
        } catch(Exception e) {coverage.put("error",e.getClass().getSimpleName());return false;}
        finally {EvidenceIO.create(output.resolve("reception-coverage.json"),coverage);}
    }
}

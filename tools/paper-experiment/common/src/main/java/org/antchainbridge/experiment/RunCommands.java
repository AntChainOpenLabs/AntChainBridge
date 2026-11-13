package org.antchainbridge.experiment;

import java.nio.file.*;
import java.util.*;

/** Read-only recovery never receives a TransactionSender. */
public final class RunCommands {
    @FunctionalInterface public interface Factory<T>{T open()throws Exception;}
    private RunCommands(){}
    public static void send(ExperimentConfig c,Factory<TransactionSender> source,Factory<ChainReader> target)throws Exception {
        RunSession session=RunSession.claim(c); // MUST precede both SDK constructors
        try(ChainReader reader=target.open();TransactionSender sender=source.open()) {
            sender.preflight(session.payload(0),reader.appIdentity());
            session.ready(sender.appIdentity(),reader.appIdentity(),reader.finalizedHead());
            new OpenLoopWorkload().run(c,session,sender);
        } catch(Exception failure) {
            try {EvidenceIO.create(session.output.resolve("run-stopped.json"),Map.of("schemaVersion",2,"runId",session.runId,"error",failure.getClass().getSimpleName(),"automaticRetry",false));}
            catch(Exception loggingFailure){failure.addSuppressed(loggingFailure);}
            throw failure;
        }
    }
    public static void check(ExperimentConfig c,Factory<TransactionSender> source,Factory<ChainReader> target,Path output)throws Exception {
        RunSession.validateRouting(c);
        EvidenceIO.directory(output);
        try(ChainReader reader=target.open();TransactionSender sender=source.open()) {
            byte[] first=ExperimentPayload.create(c.required("experiment.id"),c.required("experiment.scheme"),c.required("experiment.route"),c.integer("experiment.round",1),0);
            sender.preflight(first,reader.appIdentity());var h=reader.finalizedHead();
            EvidenceIO.create(output.resolve("check.json"),Map.of("schemaVersion",2,"sourceIdentity",sender.appIdentity(),"targetIdentity",reader.appIdentity(),"targetHeight",h.height(),"targetHash",h.hash(),"broadcasts",0));
        }
    }
    public static Map<String,Object> manifest(Path run)throws Exception{return EvidenceIO.read(run.resolve("run-manifest.json"));}
    public static void binding(ExperimentConfig c,Map<String,Object> m)throws Exception {
        for(var pair:Map.of("source.product","sourceProduct","source.blockchain.id","sourceBlockchainId","target.product","targetProduct","target.blockchain.id","targetBlockchainId","source.app","sourceAppConfigured","target.app","targetAppConfigured").entrySet())
            if(!c.required(pair.getKey()).equals(m.get(pair.getValue())))throw new IllegalArgumentException("configuration differs from original manifest: "+pair.getKey());
    }
    @SuppressWarnings("unchecked")
    public static void reconcile(ExperimentConfig c,Path run,Path output,Factory<ChainReader> source)throws Exception {
        Map<String,Object> m=Files.exists(run.resolve("run-manifest.json"))?manifest(run):EvidenceIO.read(run.resolve("run-plan.json"));binding(c,m);
        EvidenceIO.directory(output);List<Map<String,Object>> journal=Files.exists(run.resolve("attempts.jsonl"))?EvidenceIO.lines(run.resolve("attempts.jsonl"),true):List.of();
        Map<String,Map<String,Object>> latest=new HashMap<>();for(var row:journal)latest.put((String)row.get("sampleId"),row);
        // The source reader has no submission surface. No SDK is opened if every hash is unknown.
        ChainReader reader=null;
        try(EvidenceIO.Journal out=new EvidenceIO.Journal(output.resolve("reconciliation.jsonl"))) {
            for(var sample:(List<Map<String,Object>>)m.get("expectedSamples")) {
                Map<String,Object> old=latest.get(sample.get("sampleId"));Map<String,Object> result=new LinkedHashMap<>();
                result.put("schemaVersion",2);result.put("runId",m.get("runId"));result.put("sampleId",sample.get("sampleId"));
                String hash=old==null?"":(String)old.getOrDefault("sourceTxHashNative","");
                result.put("sourceTxHash",hash);result.put("originalT0EpochMs",old==null?null:old.get("t0EpochMs"));
                String state=old==null?"NOT_SENT":"UNKNOWN";
                boolean matches=old==null || (Objects.equals(old.get("runId"),m.get("runId")) && Objects.equals(old.get("attemptId"),sample.get("attemptId")) && Objects.equals(old.get("payloadSha256"),sample.get("payloadSha256")));
                if(!matches)state="EVIDENCE_CONFLICT";
                if(matches && !hash.isBlank())try {
                    if(reader==null)reader=source.open();
                    if(m.containsKey("sourceAppIdentity")&&!Identities.canonical(reader.appIdentity()).equals(m.get("sourceAppIdentity")))throw new IllegalStateException("source changed");
                    var receipt=reader.receipt(hash);var head=reader.finalizedHead();
                    if(receipt!=null && Identities.hex(receipt.hash()).equals(Identities.hex(hash)) && receipt.height()<=head.height()
                        && receipt.blockHash().equalsIgnoreCase(reader.blockHash(receipt.height())))state=receipt.successful()?"SOURCE_CONFIRMED":"SOURCE_FAILED";
                } catch(Exception e){result.put("queryError",e.getClass().getSimpleName());}
                result.put("reconciliationStatus",state);out.append(result);
            }
            EvidenceIO.create(output.resolve("reconciliation-summary.json"),Map.of("schemaVersion",2,"runId",m.get("runId"),"resumed",false,"broadcasts",0,"repairsOriginalTiming",false));
        } finally {if(reader!=null)reader.close();}
    }
    @SuppressWarnings("unchecked")
    public static boolean terminal(Path run,List<Path> metrics)throws Exception {
        var m=manifest(run);if(!Files.exists(run.resolve("run-complete.json")))return false;
        List<Map<String,Object>> sender=EvidenceIO.lines(run.resolve("sender.jsonl"),false);
        if(sender.size()!=((Number)m.get("samples")).intValue())return false;
        Map<String,Set<String>> ucps=new HashMap<>();Set<String> finished=new HashSet<>();
        for(Path path:metrics) for(String line:Files.readAllLines(path)) {
            int at=line.indexOf("EXPERIMENT_METRIC ");if(at<0)continue;
            Map<String,Object> e;
            try{e=EvidenceIO.parse(line.substring(at+18));}catch(Exception ignored){continue;}
            if(!Objects.equals(e.get("sourceProduct"),m.get("sourceProduct")) || !Objects.equals(e.get("sourceBlockchainId"),m.get("sourceBlockchainId")))continue;
            String u=(String)e.get("ucpId");if(u==null)continue;
            if("SOURCE_ACCEPTED".equals(e.get("event")))ucps.computeIfAbsent(Identities.hex((String)e.get("sourceTxHashKey")),key->new HashSet<>()).add(u);
            if(Set.of("TARGET_CONFIRMED","TARGET_FAILED").contains(e.get("event")))finished.add(u);
        }
        for(var s:sender) {
            if("SEND_FAILED".equals(s.get("sendStatus")))continue;
            if(!"SENT".equals(s.get("sendStatus")))return false;
            Set<String> u=ucps.get((String)s.get("sourceTxHashKey"));if(u==null || u.size()!=1 || !finished.contains(u.iterator().next()))return false;
        }return true;
    }
}

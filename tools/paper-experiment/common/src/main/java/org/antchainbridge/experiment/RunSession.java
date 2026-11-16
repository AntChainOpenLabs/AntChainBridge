package org.antchainbridge.experiment;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** A claimed logical round is never reused, including when startup fails. */
public final class RunSession {
    public final Path output;
    public final String runId;
    public final Map<String,Object> plan;
    public final List<Map<String,Object>> samples;
    private RunSession(Path output, String runId, Map<String,Object> plan, List<Map<String,Object>> samples) {
        this.output=output; this.runId=runId; this.plan=plan; this.samples=samples;
    }
    public static void validateRouting(ExperimentConfig c) {
        String route=c.required("experiment.route"),scheme=c.required("experiment.scheme");
        if(!Set.of("Base","Xun").contains(scheme) || !Set.of("ETH-MYC","ETH-FISCO","MYC-ETH","MYC-FISCO","FISCO-ETH","FISCO-MYC").contains(route))
            throw new IllegalArgumentException("six directed routes and Base/Xun required");
        Map<String,String> products=scheme.equals("Base")?Map.of("ETH","ethereum2","MYC","mychain","FISCO","fiscobcos"):Map.of("ETH","ethereum3","MYC","mychain2","FISCO","fiscobcos3");
        String[] chains=route.split("-");
        for(int i=0;i<2;i++) {
            String prefix=i==0?"source":"target";
            if(!products.get(chains[i]).equals(c.required(prefix+".product")))throw new IllegalArgumentException("scheme/route/product mismatch: "+prefix);
            if(chains[i].equals("MYC")) {
                if(!Set.of("name","identity").contains(c.required(prefix+".app.mode")))throw new IllegalArgumentException("explicit Mychain app mode required");
            } else Identities.evmAddress(c.required(prefix+".app"));
        }
        String profile=c.required("target.event.profile");
        if(chains[1].equals("MYC")?!Set.of("mychain-legacy","mychain-monitor").contains(profile):!profile.equals("evm-app"))
            throw new IllegalArgumentException("target event profile does not match chain");
    }
    public static RunSession claim(ExperimentConfig c) throws Exception {
        validateRouting(c);
        int rate=c.integer("workload.rate",5), duration=c.integer("workload.duration.seconds",30);
        if(rate<=0 || duration<=0 || 1000%rate!=0 || c.integer("workload.workers",16)<=0)
            throw new IllegalArgumentException("invalid workload");
        if(c.longValue("source.wait.seconds",900)<=0 || c.longValue("terminal.wait.seconds",900)<=0)throw new IllegalArgumentException("positive source/terminal wait required");
        int count=Math.multiplyExact(rate,duration),round=c.integer("experiment.round",1);
        if(count>100000) throw new IllegalArgumentException("workload too large");
        if(!Set.of("Base","Xun").contains(c.required("experiment.scheme"))) throw new IllegalArgumentException("invalid scheme");
        if(!Set.of("ETH-MYC","ETH-FISCO","MYC-ETH","MYC-FISCO","FISCO-ETH","FISCO-MYC").contains(c.required("experiment.route")))
            throw new IllegalArgumentException("invalid route");
        if(c.bool("experiment.formal",false)) {
            if(rate!=5 || duration!=30 || round<1 || round>5) throw new IllegalArgumentException("formal runs require 5 TPS, 30 seconds, rounds 1..5");
            for(String key:List.of("clock.offset.ms","relayer.clock.offset.ms")) {
                long offset=c.longValue(key,Long.MIN_VALUE);
                if(offset < -20 || offset > 20) throw new IllegalArgumentException("sender and Relayer clock offsets must be <=20ms");
            }
            if(!c.required("code.revision").matches("[0-9a-fA-F]{40}"))throw new IllegalArgumentException("formal code.revision requires full Git commit");
            if(!Files.isRegularFile(Path.of(c.required("plugin.artifact")))) throw new IllegalArgumentException("plugin artifact missing");
        }
        if(round<1 || !c.optional("experiment.protocol","unordered-sdp-v1").equals("unordered-sdp-v1"))
            throw new IllegalArgumentException("positive round and unordered-sdp-v1 required");
        Path state=Path.of(c.required("experiment.state.dir")).toAbsolutePath().normalize();
        Path out=Path.of(c.required("output.dir")).toAbsolutePath().normalize();
        if(Files.exists(out,LinkOption.NOFOLLOW_LINKS)) throw new FileAlreadyExistsException(out.toString());
        // Only selected public fields enter the evidence. Never serialize chain.config contents.
        Map<String,Object> plan=new LinkedHashMap<>();plan.put("schemaVersion",2);plan.put("runId",UUID.randomUUID().toString());
        Map<String,String> fields=Map.ofEntries(Map.entry("experiment.id","experimentId"),Map.entry("experiment.scheme","scheme"),
            Map.entry("experiment.route","route"),Map.entry("source.product","sourceProduct"),Map.entry("source.blockchain.id","sourceBlockchainId"),
            Map.entry("source.domain","sourceDomain"),Map.entry("source.app","sourceAppConfigured"),Map.entry("target.product","targetProduct"),
            Map.entry("target.blockchain.id","targetBlockchainId"),Map.entry("target.domain","targetDomain"),Map.entry("target.app","targetAppConfigured"),
            Map.entry("target.event.profile","eventProfile"));
        for(var f:fields.entrySet()) plan.put(f.getValue(),c.required(f.getKey()));
        ReceptionDecoder.signature(c.required("target.event.profile"));
        plan.put("round",round);plan.put("roundsPerScheme",5);plan.put("samples",count);plan.put("formal",c.bool("experiment.formal",false));
        plan.put("ratePerSecond",rate);plan.put("durationSeconds",duration);plan.put("intervalMillis",1000/rate);
        plan.put("protocol","unordered-sdp-v1");plan.put("codeRevision",c.required("code.revision"));
        plan.put("clockOffsetMillis",c.optional("clock.offset.ms","unknown"));
        plan.put("relayerClockOffsetMillis",c.optional("relayer.clock.offset.ms","unknown"));
        if(!c.optional("plugin.artifact","").isEmpty())plan.put("pluginChecksum",EvidenceIO.sha256(Files.readAllBytes(Path.of(c.required("plugin.artifact")))));
        List<Map<String,Object>> samples=new ArrayList<>();
        for(int i=0;i<count;i++) {
            byte[] bytes=ExperimentPayload.create(c.required("experiment.id"),c.required("experiment.scheme"),c.required("experiment.route"),round,i);
            samples.add(Map.of("sampleIndex",i,"sampleId",ExperimentPayload.sampleId(c.required("experiment.id"),c.required("experiment.scheme"),c.required("experiment.route"),round,i),
                "payloadBase64",Base64.getEncoder().encodeToString(bytes),"payloadSha256",EvidenceIO.sha256(bytes),"attemptId",UUID.randomUUID().toString()));
        }
        plan.put("expectedSamples",samples);
        Files.createDirectories(state,java.nio.file.attribute.PosixFilePermissions.asFileAttribute(java.nio.file.attribute.PosixFilePermissions.fromString("rwx------"))); Files.createDirectories(out.getParent());
        String logical=EvidenceIO.json(List.of(plan.get("experimentId"),plan.get("scheme"),plan.get("route"),round));
        EvidenceIO.create(state.resolve(EvidenceIO.sha256(logical.getBytes(StandardCharsets.UTF_8))+".json"),
            Map.of("schemaVersion",2,"logicalRound",logical,"runId",plan.get("runId"),"output",out.toString()));
        EvidenceIO.directory(out);EvidenceIO.create(out.resolve("run-plan.json"),plan);
        return new RunSession(out,(String)plan.get("runId"),plan,samples);
    }
    public void ready(String sourceIdentity, String targetIdentity, ChainReader.Head baseline) throws Exception {
        plan.put("sourceAppIdentity",Identities.canonical(sourceIdentity));plan.put("targetAppIdentity",Identities.canonical(targetIdentity));
        plan.put("targetStartHeight",baseline.height());plan.put("targetStartHash",baseline.hash());
        EvidenceIO.create(output.resolve("run-manifest.json"),plan);
    }
    public byte[] payload(int i) {return Base64.getDecoder().decode((String)samples.get(i).get("payloadBase64"));}
    public Map<String,Object> record(int i) {
        Map<String,Object> r=new LinkedHashMap<>(samples.get(i));
        for(String k:List.of("schemaVersion","runId","experimentId","scheme","route","round","sourceProduct","sourceBlockchainId"))r.put(k,plan.get(k));
        return r;
    }
}

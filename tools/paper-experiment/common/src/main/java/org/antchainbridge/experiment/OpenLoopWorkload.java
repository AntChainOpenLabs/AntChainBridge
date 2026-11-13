package org.antchainbridge.experiment;

import java.util.*;
import java.util.concurrent.*;

public final class OpenLoopWorkload {
    public static final int FORMAL_RATE_PER_SECOND=5, FORMAL_DURATION_SECONDS=30, FORMAL_ROUNDS=5;
    public static final long FORMAL_INTERVAL_MILLIS=200,MAX_ACCEPTABLE_LATENESS_MILLIS=200;
    interface TimeSource {
        long currentTimeMillis();long nanoTime();void waitUntil(long deadlineNanos)throws InterruptedException;
    }
    private static final TimeSource SYSTEM_TIME=new TimeSource() {
        public long currentTimeMillis(){return System.currentTimeMillis();}public long nanoTime(){return System.nanoTime();}
        public void waitUntil(long deadline)throws InterruptedException {for(long n;(n=deadline-System.nanoTime())>0;)TimeUnit.NANOSECONDS.sleep(n);}
    };
    private final TimeSource clock;
    interface WriterFactory {EvidenceIO.Sink open(java.nio.file.Path path)throws Exception;}
    private final WriterFactory writers;
    public OpenLoopWorkload(){this(SYSTEM_TIME);}OpenLoopWorkload(TimeSource clock){this(clock,EvidenceIO.Journal::new);}
    OpenLoopWorkload(TimeSource clock,WriterFactory writers){this.clock=clock;this.writers=writers;}
    /** The caller must claim the session BEFORE constructing any SDK. */
    public RunResult run(ExperimentConfig c,RunSession session,TransactionSender sender)throws Exception {
        if(!java.nio.file.Files.exists(session.output.resolve("run-manifest.json")))throw new IllegalStateException("preflight and target baseline required");
        int count=session.samples.size();long interval=((Number)session.plan.get("intervalMillis")).longValue();
        ExecutorService workers=Executors.newFixedThreadPool(c.integer("workload.workers",16),task->{Thread t=new Thread(task,"experiment-source-worker");t.setDaemon(true);return t;});List<Future<SenderRecord>> futures=new ArrayList<>();
        long epoch=clock.currentTimeMillis(),mono=clock.nanoTime();
        try(EvidenceIO.Sink journal=writers.open(session.output.resolve("attempts.jsonl"));
            EvidenceIO.Sink results=writers.open(session.output.resolve("sender.jsonl"))) {
            for(int i=0;i<count;i++) {
                int index=i;long planned=epoch+i*interval;clock.waitUntil(mono+TimeUnit.MILLISECONDS.toNanos(i*interval));
                futures.add(workers.submit(()->{
                    Map<String,Object> intent=session.record(index);intent.put("event","STARTED");intent.put("scheduledAtEpochMs",planned);
                    // If this write/force fails, send is never entered.
                    journal.append(intent);
                    long t0=clock.currentTimeMillis();Map<String,Object> row=session.record(index);
                    row.put("scheduledAtEpochMs",planned);row.put("t0EpochMs",t0);row.put("scheduleLatenessMs",Math.max(0,t0-planned));
                    String hash="",status;
                    try {hash=sender.send(session.payload(index));status=hash==null||hash.isBlank()?"UNKNOWN":"SENT";}
                    catch(TransactionSender.SubmissionFailure e){hash=e.txHash==null?"":e.txHash;status=e.definitive?"SEND_FAILED":"UNKNOWN";}
                    catch(Exception e){status="UNKNOWN";row.put("error",e.getClass().getSimpleName());}
                    row.put("sourceTxHashNative",hash==null?"":hash);row.put("sourceTxHashKey",normaliseHashKey(hash));row.put("sendStatus",status);
                    journal.append(new LinkedHashMap<>(row));results.append(row);return new SenderRecord(row);
                }));
            }
            workers.shutdown();List<SenderRecord> records=new ArrayList<>();
            // Bound source waits separately from later target/Relayer collection.
            long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(c.longValue("source.wait.seconds",900));
            for(var f:futures)records.add(f.get(Math.max(1,deadline-System.nanoTime()),TimeUnit.NANOSECONDS));
            if(!workers.awaitTermination(1,TimeUnit.SECONDS))throw new IllegalStateException("workers did not terminate");
            EvidenceIO.create(session.output.resolve("run-complete.json"),Map.of("schemaVersion",2,"runId",session.runId,"samples",records.size()));
            return new RunResult(records,interval);
        } finally {workers.shutdownNow();}
    }
    public static String normaliseHashKey(String hash){return hash==null?"":Identities.hex(hash.trim());}
    public static final class RunResult {
        private final List<SenderRecord> records;private final long interval;
        RunResult(List<SenderRecord> records,long interval){this.records=records;this.interval=interval;}
        public List<SenderRecord> getRecords(){return records;}public long getIntervalMillis(){return interval;}
    }
    public static final class SenderRecord {
        final Map<String,Object> row;SenderRecord(Map<String,Object> row){this.row=row;}
        public long getScheduledAtEpochMs(){return ((Number)row.get("scheduledAtEpochMs")).longValue();}
        public long getScheduleLatenessMs(){return ((Number)row.get("scheduleLatenessMs")).longValue();}
        public String getSampleId(){return (String)row.get("sampleId");}public String getSendStatus(){return (String)row.get("sendStatus");}
    }
}

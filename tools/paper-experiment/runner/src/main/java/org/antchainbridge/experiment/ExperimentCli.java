package org.antchainbridge.experiment;
import java.nio.file.*;
import java.util.*;
import org.antchainbridge.experiment.ethereum.EthereumExperimentSender;
import org.antchainbridge.experiment.mychain.MychainExperimentSender;
import org.antchainbridge.experiment.fisco.FiscoExperimentSender;

/** Explicit commands. verify/reconcile have no path to send(). */
public final class ExperimentCli {
    private ExperimentCli(){}
    static ChainReader reader(ExperimentConfig c)throws Exception {
        return switch(c.required("source.product")) {
            case "ethereum2","ethereum3" -> new EthereumExperimentSender(c,false);
            case "mychain","mychain2" -> new MychainExperimentSender(c,false);
            case "fiscobcos","fiscobcos3" -> new FiscoExperimentSender(c,false);
            default -> throw new IllegalArgumentException("unsupported product");
        };
    }
    static TransactionSender sender(ExperimentConfig c)throws Exception {
        return switch(c.required("source.product")) {
            case "ethereum2","ethereum3" -> new EthereumExperimentSender(c,true);
            case "mychain","mychain2" -> new MychainExperimentSender(c,true);
            case "fiscobcos","fiscobcos3" -> new FiscoExperimentSender(c,true);
            default -> throw new IllegalArgumentException("unsupported product");
        };
    }
    public static void execute(String[] args)throws Exception {
        if(args.length<2)throw new IllegalArgumentException("check CONFIG OUTPUT | send CONFIG | reconcile CONFIG RUN OUTPUT | verify CONFIG RUN OUTPUT METRICS...");
        String command=args[0];ExperimentConfig c=ExperimentConfig.load(Path.of(args[1]));
        switch(command) {
            case "send" -> {if(args.length!=2)throw new IllegalArgumentException("send CONFIG");RunCommands.send(c,()->sender(c),()->reader(c.targetConnection()));}
            case "check" -> {if(args.length!=3)throw new IllegalArgumentException("check CONFIG OUTPUT");RunCommands.check(c,()->sender(c),()->reader(c.targetConnection()),Path.of(args[2]));}
            case "reconcile" -> {if(args.length!=4)throw new IllegalArgumentException("reconcile CONFIG RUN OUTPUT");RunCommands.reconcile(c,Path.of(args[2]),Path.of(args[3]),()->reader(c));}
            case "verify" -> {
                if(args.length<5)throw new IllegalArgumentException("verify CONFIG RUN OUTPUT METRICS...");
                Path run=Path.of(args[2]);var m=RunCommands.manifest(run);RunCommands.binding(c,m);
                List<Path> metrics=Arrays.stream(args).skip(4).map(Path::of).toList();
                try(ChainReader target=reader(c.targetConnection())) {
                    boolean success=new ReceptionCollector().collect(m,target,Path.of(args[3]),()->{
                        try{return RunCommands.terminal(run,metrics);}catch(Exception e){return false;}
                    },c.longValue("terminal.wait.seconds",900)*1000);
                    if(!success)throw new IllegalStateException("incomplete reception evidence; inspect coverage");
                }
            }
            default -> throw new IllegalArgumentException("unknown command");
        }
    }
    public static void main(String[] args) {
        try{execute(args);}catch(Exception e){
            System.err.println("Experiment command stopped: "+e.getClass().getSimpleName()+"; no automatic retry. Inspect durable run evidence.");
            if(e.getStackTrace().length>0 && e.getStackTrace()[0].getClassName().startsWith("org.antchainbridge.experiment"))System.err.println(e.getMessage());
            System.exit(2);
        }
    }
}

package org.antchainbridge.experiment.mychain;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import com.alipay.antchain.bridge.commons.core.base.SendResponseResult;
import com.alipay.antchain.bridge.plugins.mychain020.sdk.*;
import com.alipay.mychain.sdk.api.utils.Utils;
import com.alipay.mychain.sdk.common.VMTypeEnum;
import com.alipay.mychain.sdk.crypto.hash.*;
import com.alipay.mychain.sdk.domain.account.Identity;
import com.alipay.mychain.sdk.domain.block.Block;
import com.alipay.mychain.sdk.vm.EVMParameter;
import org.antchainbridge.experiment.*;
import org.slf4j.helpers.NOPLogger;

public final class MychainExperimentSender implements TransactionSender,ChainReader {
    private final Mychain020Client client;
    interface Operations {
        VMTypeEnum type(String identity);
        com.alipay.mychain.sdk.domain.transaction.TransactionReceipt local(Identity app,EVMParameter parameters);
        SendResponseResult call(Identity app,EVMParameter parameters);
    }
    private final Operations operations;
    private final Identity sourceApp;
    private final String domain;
    private Identity targetApp;
    private boolean preflightPassed;
    public static Identity resolve(String mode,String value,HashTypeEnum algorithm) {
        return switch(mode) {
            case "name" -> Utils.getIdentityByName(value,algorithm);
            case "identity" -> {
                String h=Identities.hex(value);
                if(!h.matches("[0-9a-f]{64}"))throw new IllegalArgumentException("Mychain identity requires 32 bytes");
                yield new Identity(new Hash(HexFormat.of().parseHex(h)));
            }
            default -> throw new IllegalArgumentException("Mychain source/target.app.mode must be name or identity; migrate old config explicitly");
        };
    }
    public MychainExperimentSender(ExperimentConfig c,boolean sending)throws Exception {
        String mode=c.required("source.app.mode");
        if(!Set.of("name","identity").contains(mode))throw new IllegalArgumentException("explicit Mychain app mode required");
        Mychain020Config cfg=Mychain020Config.fromJsonString(Files.readString(Path.of(c.required("chain.config")),StandardCharsets.UTF_8));
        sourceApp=resolve(mode,c.required("source.app"),cfg.getMychainHashType());
        client=new Mychain020Client(cfg.toJsonString().getBytes(StandardCharsets.UTF_8),NOPLogger.NOP_LOGGER);
        operations=new Operations(){
            public VMTypeEnum type(String id){return client.getContractType(id);}
            public com.alipay.mychain.sdk.domain.transaction.TransactionReceipt local(Identity id,EVMParameter p){return client.localCallContract(id,p);}
            public SendResponseResult call(Identity id,EVMParameter p){return client.callContract(id,p,true);}
        };
        try {
            if(!client.startup())throw new IllegalStateException("Mychain startup failed");
            if(operations.type(sourceApp.hexStrValue())!=VMTypeEnum.EVM)throw new IllegalArgumentException("Mychain EVM application missing");
            domain=c.required("target.domain");
        } catch(Exception e){client.shutdown();throw e;}
    }
    MychainExperimentSender(Operations operations,Identity source,String domain) {
        this.client=null;this.operations=operations;this.sourceApp=source;this.domain=domain;
        if(operations.type(source.hexStrValue())!=VMTypeEnum.EVM)throw new IllegalArgumentException("Mychain EVM application missing");
    }
    public String appIdentity(){return sourceApp.hexStrValue();}
    public String eventTopic(String signature){return HexFormat.of().formatHex(HashFactory.getHash(HashTypeEnum.Keccak).hash(signature.getBytes(StandardCharsets.UTF_8)));}
    private EVMParameter parameters(byte[] payload) {
        EVMParameter p=new EVMParameter("sendUnordered(identity,string,bytes)");p.addIdentity(targetApp);p.addString(domain);p.addBytes(payload);return p;
    }
    public void preflight(byte[] payload,String target)throws Exception {
        preflightPassed=false;
        targetApp=resolve("identity",target,HashTypeEnum.Keccak);
        // Identity overload builds request.setLocal(); this cannot broadcast or hash the Identity again.
        var r=operations.local(sourceApp,parameters(payload));
        if(r==null || r.getResult()!=0)throw new IllegalStateException("Mychain local sendUnordered simulation failed");
        preflightPassed=true;
    }
    public String send(byte[] payload)throws Exception {
        if(!preflightPassed)throw new IllegalStateException("successful preflight required");
        SendResponseResult r=operations.call(sourceApp,parameters(payload));
        if(r==null)throw new SubmissionFailure("",false);
        if(!r.isConfirmed())throw new SubmissionFailure(r.getTxId(),false);
        if(!r.isSuccess())throw new SubmissionFailure(r.getTxId(),true);
        return r.getTxId();
    }
    public Head finalizedHead() {
        long h=client.queryLatestHeight();Block b=checkedBlock(h);return new Head(h,b.getBlockHeader().getHash().hexStrValue());
    }
    private Block checkedBlock(long height) {
        Block b=client.getBlockByHeight(height);
        if(b==null || b.getBlockHeader()==null || b.getBlockHeader().getNumber().longValueExact()!=height)throw new IllegalStateException("Mychain block unavailable");
        return b;
    }
    public String blockHash(long h){return checkedBlock(h).getBlockHeader().getHash().hexStrValue();}
    private Receipt convert(String hash,long height,String blockHash,com.alipay.mychain.sdk.domain.transaction.TransactionReceipt r) {
        List<ChainReader.Log> logs=new ArrayList<>();int i=0;
        for(var l:r.getLogs())logs.add(new ChainReader.Log(l.getTo().hexStrValue(),l.getTopics(),HexFormat.of().formatHex(l.getLogData()),i++));
        return new Receipt(hash,height,blockHash,r.getResult()==0,logs);
    }
    public List<Receipt> receipts(long h) {
        Block b=checkedBlock(h);var tx=b.getBlockBody().getTransactionList();var receipts=b.getBlockBody().getReceiptList();
        if(tx==null || receipts==null || tx.size()!=receipts.size())throw new IllegalStateException("incomplete Mychain block receipts");
        List<Receipt> out=new ArrayList<>();for(int i=0;i<tx.size();i++)out.add(convert(tx.get(i).getHash().hexStrValue(),h,b.getBlockHeader().getHash().hexStrValue(),receipts.get(i)));return out;
    }
    public Receipt receipt(String hash) {
        var r=client.getTxReceiptByTxhash(Identities.hex(hash));
        if(!r.isSuccess())return null;
        if(r.getTransactionReceipt()==null || r.getBlockNumber()==null)throw new IllegalStateException("incomplete Mychain receipt");
        long h=r.getBlockNumber().longValueExact();
        for(Receipt item:receipts(h))if(Identities.hex(item.hash()).equals(Identities.hex(hash)))return item;
        throw new IllegalStateException("Mychain receipt missing from canonical block");
    }
    public void close(){if(client!=null)client.shutdown();}
}

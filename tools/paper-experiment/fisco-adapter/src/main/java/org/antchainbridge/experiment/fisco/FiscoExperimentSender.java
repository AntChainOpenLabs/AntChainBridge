package org.antchainbridge.experiment.fisco;

import java.nio.file.Files;
import java.nio.file.Path;

import com.alipay.antchain.bridge.commons.bbc.DefaultBBCContext;
import org.antchainbridge.experiment.ExperimentConfig;
import org.antchainbridge.experiment.TransactionSender;
import org.antchainbridge.experiment.ChainReader;
import org.antchainbridge.experiment.Identities;
import java.util.*;
import java.math.BigInteger;
import org.fisco.bcos.sdk.v3.client.Client;
import org.fisco.bcos.sdk.v3.model.TransactionReceipt;

public final class FiscoExperimentSender implements TransactionSender, ChainReader {

    private final FiscoMessageSender messageSender;
    private final Runnable closeAction;
    private final String targetDomain;
    private byte[] targetApp;
    private final Client client;
    private final String address;

    public FiscoExperimentSender(ExperimentConfig config, boolean sending) throws Exception {
        DefaultBBCContext context = new DefaultBBCContext();
        context.setConfForBlockchainClient(Files.readAllBytes(Path.of(config.required("chain.config"))));
        FiscoVariant variant = requireSourceProduct(config.required("source.product"));
        String sourceApp = Identities.evmAddress(config.required("source.app"));
        this.address=sourceApp;
        if (variant == FiscoVariant.BASE) {
            com.alipay.antchain.bridge.plugins.fiscobcos.FISCOBCOSBBCService service =
                    new com.alipay.antchain.bridge.plugins.fiscobcos.FISCOBCOSBBCService();
            service.startup(context);
            com.alipay.antchain.bridge.plugins.fiscobcos.abi.AppContract app =
                    com.alipay.antchain.bridge.plugins.fiscobcos.abi.AppContract.load(
                            sourceApp, service.getClient(), service.getKeyPair());
            this.client=service.getClient();
            this.messageSender = app::sendUnorderedMessage;
            this.closeAction = service::shutdown;
        } else {
            com.alipay.antchain.bridge.plugins.fiscobcos3.FISCOBCOSBBCService service =
                    new com.alipay.antchain.bridge.plugins.fiscobcos3.FISCOBCOSBBCService();
            service.startup(context);
            com.alipay.antchain.bridge.plugins.fiscobcos3.abi.AppContract app =
                    com.alipay.antchain.bridge.plugins.fiscobcos3.abi.AppContract.load(
                            sourceApp, service.getClient(), service.getKeyPair());
            this.client=service.getClient();
            this.messageSender = app::sendUnorderedMessage;
            this.closeAction = service::shutdown;
        }
        this.targetDomain = config.required("target.domain");

    }

    @Override
    public String send(byte[] payload) throws Exception {
        if(targetApp==null)throw new IllegalStateException("preflight required");
        TransactionReceipt receipt = messageSender.send(targetDomain, targetApp, payload);
        if(receipt==null)throw new SubmissionFailure("",false);
        if(!receipt.isStatusOK())throw new SubmissionFailure(receipt.getTransactionHash(),true);
        return receipt.getTransactionHash();
    }

    @Override
    public void close() {
        closeAction.run();
    }

    public String appIdentity(){return Identities.canonical(address);}
    public String eventTopic(String signature){return client.getCryptoSuite().hash(signature);}
    public void preflight(byte[] payload,String target){targetApp=Identities.bytes(target);}
    public Head finalizedHead(){long h=client.getBlockNumber().getBlockNumber().longValueExact();return new Head(h,blockHash(h));}
    public String blockHash(long h){
        var b=client.getBlockByNumber(BigInteger.valueOf(h),true,true).getBlock();
        if(b==null || b.getNumber()!=h)throw new IllegalStateException("FISCO block unavailable");return b.getHash();
    }
    public Receipt receipt(String hash){
        var r=client.getTransactionReceipt("0x"+Identities.hex(hash),false).getTransactionReceipt();
        if(r==null)return null;
        List<ChainReader.Log> logs=new ArrayList<>();int i=0;
        for(var l:r.getLogEntries())logs.add(new ChainReader.Log(l.getAddress(),l.getTopics(),l.getData(),i++));
        long h=r.getBlockNumber().longValueExact();return new Receipt(r.getTransactionHash(),h,blockHash(h),r.isStatusOK(),logs);
    }
    public List<Receipt> receipts(long h){
        var b=client.getBlockByNumber(BigInteger.valueOf(h),false,true).getBlock();
        if(b==null || b.getNumber()!=h || b.getTransactions()==null)throw new IllegalStateException("FISCO block unavailable");
        List<Receipt> out=new ArrayList<>();
        for(var tx:b.getTransactions()){
            var r=receipt((String)tx.get());
            if(r==null || r.height()!=h || !r.blockHash().equalsIgnoreCase(b.getHash()))throw new IllegalStateException("incomplete FISCO receipts");out.add(r);
        }return out;
    }

    static FiscoVariant requireSourceProduct(String product) {
        if ("fiscobcos".equals(product)) {
            return FiscoVariant.BASE;
        }
        if ("fiscobcos3".equals(product)) {
            return FiscoVariant.XUN;
        }
        throw new IllegalArgumentException(
                "source.product must be fiscobcos (Base) or fiscobcos3 (Xun)");
    }

    enum FiscoVariant {
        BASE,
        XUN
    }

    @FunctionalInterface
    private interface FiscoMessageSender {
        TransactionReceipt send(String receiverDomain, byte[] receiverIdentity, byte[] payload);
    }
}

package org.antchainbridge.experiment.ethereum;

import java.nio.file.*;
import java.math.BigInteger;
import java.util.*;
import com.alipay.antchain.bridge.commons.bbc.DefaultBBCContext;
import com.alipay.antchain.bridge.plugins.ethereum3.EthereumBBCService;
import com.alipay.antchain.bridge.plugins.ethereum3.abi.AppContract;
import com.alipay.antchain.bridge.plugins.ethereum3.helper.AcbFastRawTransactionManager;
import org.antchainbridge.experiment.*;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.*;
import org.web3j.protocol.core.methods.response.*;
import org.web3j.tx.gas.DefaultGasProvider;

/** The reader methods only issue eth_get* queries. */
public final class EthereumExperimentSender implements TransactionSender,ChainReader {
    private final EthereumBBCService service;
    private final Web3j web3j;
    private final AppContract app;
    private final String targetDomain,address;
    private byte[] targetApp;
    public EthereumExperimentSender(ExperimentConfig c,boolean sending)throws Exception {
        service=new EthereumBBCService();DefaultBBCContext ctx=new DefaultBBCContext();
        ctx.setConfForBlockchainClient(Files.readAllBytes(Path.of(c.required("chain.config"))));
        try {
            service.startup(ctx);web3j=service.getAcbEthClient().getWeb3j();
            if(sending && !(service.getAcbEthClient().getRawTransactionManager() instanceof AcbFastRawTransactionManager))
                throw new IllegalArgumentException("ethNoncePolicy must be FAST");
            address=Identities.evmAddress(c.required("source.app"));
            app=AppContract.load(address,web3j,service.getAcbEthClient().getRawTransactionManager(),new DefaultGasProvider());
            targetDomain=c.required("target.domain");
            var code=web3j.ethGetCode(address,DefaultBlockParameterName.LATEST).send();
            if(code.hasError() || code.getCode()==null || code.getCode().equals("0x"))throw new IllegalArgumentException("application code missing");
        } catch(Exception e){service.shutdown();throw e;}
    }
    public String appIdentity(){return Identities.canonical(address);}
    public String eventTopic(String signature){return org.web3j.crypto.Hash.sha3String(signature);}
    public void preflight(byte[] bytes,String target){targetApp=Identities.bytes(target);}
    public String send(byte[] payload)throws Exception {
        if(targetApp==null)throw new IllegalStateException("preflight required");
        TransactionReceipt r=app.sendUnorderedMessage(targetDomain,targetApp,payload).send();
        if(r==null)throw new SubmissionFailure("",false);
        if(!r.isStatusOK())throw new SubmissionFailure(r.getTransactionHash(),true);
        return r.getTransactionHash();
    }
    private EthBlock.Block block(long h,boolean full)throws Exception {
        var r=web3j.ethGetBlockByNumber(DefaultBlockParameter.valueOf(BigInteger.valueOf(h)),full).send();
        if(r.hasError() || r.getBlock()==null)throw new IllegalStateException("block unavailable");return r.getBlock();
    }
    public Head finalizedHead()throws Exception {
        var r=web3j.ethGetBlockByNumber(DefaultBlockParameterName.FINALIZED,false).send();
        if(r.hasError() || r.getBlock()==null)throw new IllegalStateException("finalized head unavailable");
        return new Head(r.getBlock().getNumber().longValueExact(),r.getBlock().getHash());
    }
    public String blockHash(long h)throws Exception{return block(h,false).getHash();}
    public Receipt receipt(String hash)throws Exception {
        var response=web3j.ethGetTransactionReceipt("0x"+Identities.hex(hash)).send();
        if(response.hasError())throw new IllegalStateException("receipt RPC failed");
        var r=response.getTransactionReceipt().orElse(null);if(r==null)return null;
        List<ChainReader.Log> logs=new ArrayList<>();int i=0;
        for(var l:r.getLogs())logs.add(new ChainReader.Log(l.getAddress(),l.getTopics(),l.getData(),i++));
        return new Receipt(r.getTransactionHash(),r.getBlockNumber().longValueExact(),r.getBlockHash(),r.isStatusOK(),logs);
    }
    public List<Receipt> receipts(long h)throws Exception {
        var b=block(h,false);List<Receipt> out=new ArrayList<>();
        for(var tx:b.getTransactions()) {
            var r=receipt((String)tx.get());
            if(r==null || r.height()!=h || !r.blockHash().equalsIgnoreCase(b.getHash()))throw new IllegalStateException("incomplete block receipts");
            out.add(r);
        }return out;
    }
    public void close(){service.shutdown();}
}

package com.alipay.antchain.bridge.relayer.core.types.blockchain;
import java.lang.reflect.Proxy;
import java.math.BigInteger;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;
import com.alipay.antchain.bridge.commons.bbc.DefaultBBCContext;
import com.alipay.antchain.bridge.commons.core.base.*;
import com.alipay.antchain.bridge.relayer.commons.model.BlockchainMeta;
import com.alipay.antchain.bridge.relayer.core.types.pluginserver.IBBCServiceClient;
import com.alipay.antchain.bridge.relayer.core.service.anchor.context.AnchorProcessContext;
import com.alipay.antchain.bridge.relayer.dal.repository.IBlockchainRepository;
import org.junit.Test;
import static org.junit.Assert.*;
public class PlainDioxideConsensusCollectionTest {
 private static class Fixture {
  final AtomicInteger reads=new AtomicInteger();final BlockchainMeta meta;final HeteroBlockchainClient client;
  Fixture(String product,String ptc){
   BlockchainMeta.BlockchainProperties props=new BlockchainMeta.BlockchainProperties();props.setPtcContractAddress(ptc);props.setBbcContext(new DefaultBBCContext());meta=new BlockchainMeta(product,"fixture.id","fixture","fixture",props);
   IBBCServiceClient bbc=(IBBCServiceClient)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{IBBCServiceClient.class},(proxy,m,args)->{
    if(m.getName().equals("getDomain"))return "fixture";
    if(m.getName().equals("getProduct"))return product;
    if(m.getName().equals("readCrossChainMessagesByHeight"))return Collections.emptyList();
    if(m.getName().equals("readConsensusState")){reads.incrementAndGet();return new ConsensusState((BigInteger)args[0],new byte[]{1},new byte[]{0},1L,new byte[0],new byte[0],new byte[0]);}
    throw new AssertionError("Unexpected call "+m.getName());
   });client=new HeteroBlockchainClient(bbc,meta);
  }
  AnchorProcessContext context(final boolean hasBta){
   final IBlockchainRepository repository=(IBlockchainRepository)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{IBlockchainRepository.class},(p,m,a)->{if(m.getName().equals("hasBta"))return hasBta;throw new AssertionError(m.getName());});
   BlockchainClientPool pool=new BlockchainClientPool(){
    @Override public AbstractBlockchainClient getClient(String p,String id){return client;}
    @Override public AbstractBlockchainClient createClient(BlockchainMeta m){return client;}
    @Override public IBlockchainRepository getBlockchainRepository(){return repository;}
   };
   return new AnchorProcessContext(meta,null,pool,null,null,null,null,16,1000,16,1,100,16,-1);
  }
 }
 @Test public void plainDioxideRetainsV0BehaviorWithoutPtcHub(){Fixture f=new Fixture("dioxide",null);AbstractBlock b=f.client.getEssentialBlockByHeight(12);assertNull(b.getConsensusState());assertEquals(0,f.reads.get());}
 @Test public void otherV0ProductsKeepExistingBehavior(){Fixture f=new Fixture("other-product",null);assertNull(f.client.getEssentialBlockByHeight(12).getConsensusState());assertEquals(0,f.reads.get());}
 @Test public void existingPtcChainsStillCollectConsensus(){Fixture f=new Fixture("fiscobcos","0x123");assertNotNull(f.client.getEssentialBlockByHeight(12).getConsensusState());assertEquals(1,f.reads.get());}
 @Test public void plainDioxideBtaDoesNotAdvertisePtcSupport(){Fixture f=new Fixture("dioxide",null);assertFalse(f.context(true).isPtcSupport());assertFalse(f.context(false).isPtcSupport());}
 @Test public void otherProductsDoNotGainPtcSupportFromThisException(){Fixture f=new Fixture("other-product",null);assertFalse(f.context(true).isPtcSupport());}
}

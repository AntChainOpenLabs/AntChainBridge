package org.antchainbridge.experiment.mychain;
import org.junit.*;
import com.alipay.mychain.sdk.api.utils.Utils;
import com.alipay.mychain.sdk.crypto.hash.HashTypeEnum;
public class MychainIdentityTest {
    @Test public void rawIdentityIsNotHashedAgain(){String id="2c8363f71d100ad44ff3ef2e8dba3e333a0df426d7200a3b9fb39913f601c3db";Assert.assertEquals(id,MychainExperimentSender.resolve("identity","0x"+id,HashTypeEnum.Keccak).hexStrValue());Assert.assertNotEquals(Utils.getIdentityByName(id,HashTypeEnum.Keccak),MychainExperimentSender.resolve("identity",id,HashTypeEnum.Keccak));}
    @Test public void nameUsesConfiguredHash(){Assert.assertEquals(Utils.getIdentityByName("Sender",HashTypeEnum.Keccak),MychainExperimentSender.resolve("name","Sender",HashTypeEnum.Keccak));}
    @Test public void missingModeIsRejected(){Assert.assertThrows(IllegalArgumentException.class,()->MychainExperimentSender.resolve("","Sender",HashTypeEnum.Keccak));}
    @Test public void malformedOrShortIdentityIsRejected(){for(String id:new String[]{"ab","g".repeat(64),"a".repeat(40)})Assert.assertThrows(IllegalArgumentException.class,()->MychainExperimentSender.resolve("identity",id,HashTypeEnum.Keccak));}
    static class MockOps implements MychainExperimentSender.Operations {
        int sends,locals;com.alipay.mychain.sdk.common.VMTypeEnum vm=com.alipay.mychain.sdk.common.VMTypeEnum.EVM;
        long localResult;com.alipay.mychain.sdk.domain.account.Identity localApp,sentApp;
        public com.alipay.mychain.sdk.common.VMTypeEnum type(String id){return vm;}
        public com.alipay.mychain.sdk.domain.transaction.TransactionReceipt local(com.alipay.mychain.sdk.domain.account.Identity id,com.alipay.mychain.sdk.vm.EVMParameter p){locals++;Assert.assertEquals("sendUnordered(identity,string,bytes)",p.getMethodSignature());localApp=id;var receipt=new com.alipay.mychain.sdk.domain.transaction.TransactionReceipt();receipt.setResult(localResult);return receipt;}
        public com.alipay.antchain.bridge.commons.core.base.SendResponseResult call(com.alipay.mychain.sdk.domain.account.Identity id,com.alipay.mychain.sdk.vm.EVMParameter p){sends++;sentApp=id;return new com.alipay.antchain.bridge.commons.core.base.SendResponseResult("1".repeat(64),true,true,"0","",0,new byte[0]);}
    }
    @Test public void missingApplicationStopsBeforeSend(){var ops=new MockOps();ops.vm=null;Assert.assertThrows(IllegalArgumentException.class,()->new MychainExperimentSender(ops,MychainExperimentSender.resolve("identity","a".repeat(64),HashTypeEnum.Keccak),"target"));Assert.assertEquals(0,ops.sends);}
    @Test public void localSimulationFailurePreventsSend(){var ops=new MockOps();ops.localResult=1;var sender=new MychainExperimentSender(ops,MychainExperimentSender.resolve("identity","a".repeat(64),HashTypeEnum.Keccak),"target");Assert.assertThrows(IllegalStateException.class,()->sender.preflight(new byte[512],"b".repeat(64)));Assert.assertEquals(0,ops.sends);}
    @Test public void simulationAndSendUseOriginalIdentity()throws Exception {var ops=new MockOps();var id=MychainExperimentSender.resolve("identity","a".repeat(64),HashTypeEnum.Keccak);var sender=new MychainExperimentSender(ops,id,"target");sender.preflight(new byte[512],"b".repeat(64));sender.send(new byte[512]);Assert.assertEquals(id,ops.localApp);Assert.assertEquals(id,ops.sentApp);Assert.assertEquals(1,ops.sends);}
}

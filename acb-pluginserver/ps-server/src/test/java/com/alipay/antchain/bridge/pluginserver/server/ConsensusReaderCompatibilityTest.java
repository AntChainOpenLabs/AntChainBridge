package com.alipay.antchain.bridge.pluginserver.server;

import com.alipay.antchain.bridge.commons.core.base.ConsensusState;
import com.alipay.antchain.bridge.plugins.spi.bbc.AbstractBBCService;
import com.alipay.antchain.bridge.plugins.spi.bbc.IBBCService;
import com.alipay.antchain.bridge.plugins.spi.bbc.core.BBCVersionEnum;
import com.alipay.antchain.bridge.plugins.spi.utils.pf4j.Utils;
import com.alipay.antchain.bridge.pluginserver.service.ReadConsensusStateRequest;
import com.alipay.antchain.bridge.pluginserver.service.Response;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.math.BigInteger;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.tools.ToolProvider;
import static org.junit.jupiter.api.Assertions.*;

/** Real V0/V1 method declarations, without mocking or connecting a chain. */
public class ConsensusReaderCompatibilityTest {
    private IBBCService reader(boolean v1, ConsensusState state) throws Exception {
        String name=v1?"V1ConsensusFixture":"V0ConsensusFixture";
        StringBuilder source=new StringBuilder("public class "+name+" extends "+AbstractBBCService.class.getName()+" { public static "+ConsensusState.class.getName()+" state; ");
        for(Method method:IBBCService.class.getMethods()) {
            if(!Modifier.isAbstract(method.getModifiers()))continue;
            source.append("public ").append(method.getReturnType().getCanonicalName()).append(" ").append(method.getName()).append("(");
            Class<?>[] parameters=method.getParameterTypes();
            for(int i=0;i<parameters.length;i++){if(i>0)source.append(",");source.append(parameters[i].getCanonicalName()).append(" p").append(i);}
            source.append("){ ");
            if(method.getName().equals("readConsensusState"))source.append("return state;");
            else if(method.getReturnType()!=void.class)source.append(method.getReturnType()==boolean.class?"return false;":method.getReturnType().isPrimitive()?"return 0;":"return null;");
            source.append(" }");
        }
        if(v1)source.append("public void updatePTCTrustRoot(com.alipay.antchain.bridge.commons.core.ptc.PTCTrustRoot r){throw new UnsupportedOperationException();}");
        source.append("}");
        Path folder=Files.createTempDirectory("consensus-reader-fixture-");Path sourceFile=folder.resolve(name+".java");Files.write(sourceFile,source.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertEquals(0,ToolProvider.getSystemJavaCompiler().run(null,null,null,"-proc:none","-classpath",System.getProperty("java.class.path"),"-d",folder.toString(),sourceFile.toString()));
        try(URLClassLoader loader=new URLClassLoader(new URL[]{folder.toUri().toURL()},getClass().getClassLoader())){
            Class<?> type=Class.forName(name,true,loader);type.getField("state").set(null,state);return (IBBCService)type.getConstructor().newInstance();
        }
    }
    private Response invoke(IBBCService reader) throws Exception {
        Method m=CrossChainServiceImpl.class.getDeclaredMethod("handleReadConsensusState",IBBCService.class,ReadConsensusStateRequest.class,String.class,String.class);m.setAccessible(true);
        return (Response)m.invoke(new CrossChainServiceImpl(),reader,ReadConsensusStateRequest.newBuilder().setHeight("12").build(),"fixture","fixture-domain");
    }
    private ConsensusState state(){return new ConsensusState(BigInteger.valueOf(12),new byte[]{1,2},new byte[]{0,1},123L,new byte[0],new byte[0],new byte[0]);}
    @Test public void v0ReaderWorksWithoutClaimingPtcSupport() throws Exception {
        IBBCService reader=reader(false,state());assertEquals(BBCVersionEnum.V0,Utils.getBBCVersion(reader));Response response=invoke(reader);assertEquals(0,response.getCode());
        assertArrayEquals(state().encode(),response.getBbcResp().getReadConsensusStateResponse().getConsensusState().toByteArray());
    }
    @Test public void nullReaderCannotReportSuccess() throws Exception {assertNotEquals(0,invoke(reader(false,null)).getCode());}
    @Test public void v1ReaderStillWorks() throws Exception {IBBCService reader=reader(true,state());assertEquals(BBCVersionEnum.V1,Utils.getBBCVersion(reader));assertEquals(0,invoke(reader).getCode());}
}

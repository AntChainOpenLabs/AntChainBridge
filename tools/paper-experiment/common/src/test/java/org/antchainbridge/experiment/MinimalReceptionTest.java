package org.antchainbridge.experiment;
import org.junit.*;
import java.util.*;

public class MinimalReceptionTest {
    @Test public void minimalMychainPreservesRawBytesAndOrderFlag() throws Exception {
        byte[] payload=new byte[512];new Random(7).nextBytes(payload);
        String body=ReceptionCollectorTest.event("mychain-minimal",payload);
        var e=ReceptionDecoder.decode("mychain-minimal",new ChainReader.Log("app",List.of("topic"),body,0));
        Assert.assertArrayEquals(payload,Base64.getDecoder().decode((String)e.get("payloadBase64")));
        Assert.assertEquals(false,e.get("isOrdered"));
        Assert.assertEquals("recvCrosschainMsg(string,identity,bytes,bool)",ReceptionDecoder.signature("mychain-minimal"));
    }
    @Test public void minimalMychainRejectsInvalidOrderFlag() throws Exception {
        byte[] body=HexFormat.of().parseHex(ReceptionCollectorTest.event("mychain-minimal",new byte[512]));body[127]=2;
        Assert.assertThrows(IllegalArgumentException.class,()->ReceptionDecoder.decode("mychain-minimal",new ChainReader.Log("app",List.of(),HexFormat.of().formatHex(body),0)));
    }
}

package org.antchainbridge.experiment;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.util.*;

/** Strict ABI decoding of the existing non-indexed application receive events. */
public final class ReceptionDecoder {
    private ReceptionDecoder() {}
    public static String signature(String profile) {
        return switch(profile) {
            case "evm-app" -> "recvCrosschainMsg(string,bytes32,bytes,bool)";
            case "mychain-legacy" -> "amNotify(string,identity,string)";
            case "mychain-monitor" -> "UnorderedMessageReceived(string,identity,bytes,uint256)";
            case "mychain-minimal" -> "recvCrosschainMsg(string,identity,bytes,bool)";
            default -> throw new IllegalArgumentException("unsupported target.event.profile");
        };
    }
    static int integer(byte[] bytes,int pos) {
        if(pos<0 || pos+32>bytes.length)throw new IllegalArgumentException("ABI word out of bounds");
        return new BigInteger(1,Arrays.copyOfRange(bytes,pos,pos+32)).intValueExact();
    }
    static byte[] dynamic(byte[] bytes,int word,int head) {
        int pos=integer(bytes,word),len=integer(bytes,pos);
        if(pos<head || pos%32!=0 || len<0 || (long)pos+32+len>bytes.length)throw new IllegalArgumentException("invalid ABI dynamic bounds");
        return Arrays.copyOfRange(bytes,pos+32,pos+32+len);
    }
    public static Map<String,Object> decode(String profile,ChainReader.Log log) throws Exception {
        byte[] bytes=HexFormat.of().parseHex(Identities.hex(log.data()));int head=profile.equals("mychain-legacy")?96:128;
        if(bytes.length<head)throw new IllegalArgumentException("short receive event");
        String domain=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(dynamic(bytes,0,head))).toString();
        byte[] payload=dynamic(bytes,64,head);Map<String,Object> out=new LinkedHashMap<>();
        out.put("sourceDomain",domain);out.put("author",HexFormat.of().formatHex(Arrays.copyOfRange(bytes,32,64)));
        out.put("payloadBase64",Base64.getEncoder().encodeToString(payload));out.put("payloadSha256",EvidenceIO.sha256(payload));
        if(profile.equals("evm-app") || profile.equals("mychain-minimal")) {int flag=integer(bytes,96);if(flag>1)throw new IllegalArgumentException("invalid bool");out.put("isOrdered",flag==1);}
        // Legacy event has no order flag. The run is pinned to unordered V1 and the original target receipt is separately checked.
        out.put("orderEvidence",profile.equals("mychain-legacy")?"SOURCE_UNORDERED_V1":"APP_EVENT");
        out.put("eventProfile",profile);return out;
    }
}

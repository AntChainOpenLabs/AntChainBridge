/* SPDX-License-Identifier: Apache-2.0 */
package tools.regulatory;

import com.alipay.antchain.bridge.commons.core.am.AuthMessageFactory;
import com.alipay.antchain.bridge.commons.core.am.IAuthMessage;
import com.alipay.antchain.bridge.commons.core.sdp.ISDPMessage;
import com.alipay.antchain.bridge.commons.core.sdp.SDPMessageFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.bouncycastle.util.encoders.Hex;

/** Decodes a captured AuthMessage and prints only its protocol payload. */
public final class CrossChainPayloadInspect {

    private CrossChainPayloadInspect() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException("expected AUTH_MESSAGE_FILE");
        }
        IAuthMessage authMessage = AuthMessageFactory.createAuthMessage(
                Files.readAllBytes(Path.of(args[0])));
        ISDPMessage sdpMessage = SDPMessageFactory.createSDPMessage(authMessage.getPayload());
        byte[] payload = sdpMessage.getPayload();
        System.out.println("auth.version=" + authMessage.getVersion());
        System.out.println("sdp.version=" + sdpMessage.getVersion());
        System.out.println("sdp.targetDomain=" + sdpMessage.getTargetDomain().getDomain());
        System.out.println("sdp.payload.length=" + payload.length);
        System.out.println("sdp.payload.hex=" + Hex.toHexString(payload));
        System.out.println("sdp.payload.utf8=" + new String(payload, StandardCharsets.UTF_8));
    }
}

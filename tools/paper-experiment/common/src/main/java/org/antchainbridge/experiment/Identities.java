package org.antchainbridge.experiment;
import java.util.HexFormat;
public final class Identities {
    private Identities() {}
    public static String hex(String value) {return value.replaceFirst("(?i)^0x", "").toLowerCase(java.util.Locale.ROOT);}
    public static String canonical(String value) {
        String h=hex(value);if(!h.matches("[0-9a-f]{40}|[0-9a-f]{64}"))throw new IllegalArgumentException("20-byte address or 32-byte identity required");
        return h.length()==40?"0".repeat(24)+h:h;
    }
    public static String evmAddress(String value) {
        String h=hex(value);
        if(h.length()==64 && h.startsWith("0".repeat(24)))h=h.substring(24);
        if(!h.matches("[0-9a-f]{40}"))throw new IllegalArgumentException("EVM application must be a 20-byte address or zero-padded identity");
        return "0x"+h;
    }
    public static byte[] bytes(String value) {return HexFormat.of().parseHex(canonical(value));}
}

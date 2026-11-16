package org.antchainbridge.experiment;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.util.*;

/** Exclusive, durable evidence writes. Never repairs or truncates input evidence. */
public final class EvidenceIO {
    private static final ObjectMapper JSON = new ObjectMapper();
    private EvidenceIO() {}
    public static Map<String,Object> read(Path path) throws IOException {
        return JSON.readValue(Files.readAllBytes(path), new TypeReference<Map<String,Object>>() {});
    }
    public static Map<String,Object> parse(String line) throws IOException {
        return JSON.readValue(line, new TypeReference<Map<String,Object>>() {});
    }
    public static String json(Object value) throws IOException { return JSON.writeValueAsString(value); }
    public static String sha256(byte[] value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value)); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }
    public static void create(Path path, Object value) throws IOException {
        try (Journal file = new Journal(path)) { file.append(value); }
    }
    public static void directory(Path path) throws IOException {
        Files.createDirectory(path, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
        forceDirectory(path.getParent());
    }
    static void forceDirectory(Path dir) throws IOException {
        try (FileChannel channel = FileChannel.open(dir, StandardOpenOption.READ)) { channel.force(true); }
    }
    public static List<Map<String,Object>> lines(Path path, boolean allowTornTail) throws IOException {
        byte[] raw=Files.readAllBytes(path);
        if(allowTornTail && raw.length>0 && raw[raw.length-1]!='\n') {
            int end=raw.length-1;while(end>=0 && raw[end]!='\n')end--;
            raw=Arrays.copyOf(raw,end+1);
        }
        String text=StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(raw)).toString();
        String[] lines = text.split("\n", -1);
        List<Map<String,Object>> out = new ArrayList<>();
        for (int i=0;i<lines.length;i++) {
            if (lines[i].isBlank()) continue;
            // Only inspection may tolerate a torn final record. Never truncate the original.
            if (i==lines.length-1 && !text.endsWith("\n") && allowTornTail) break;
            out.add(parse(lines[i]));
        }
        return out;
    }
    public interface Sink extends AutoCloseable {
        void append(Object value) throws IOException;
        void close() throws IOException;
    }
    public static final class Journal implements Sink {
        private final FileChannel channel;
        public Journal(Path path) throws IOException {
            channel=FileChannel.open(path, EnumSet.of(StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE),
                    PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
            channel.force(true); forceDirectory(path.getParent());
        }
        public synchronized void append(Object value) throws IOException {
            ByteBuffer bytes=StandardCharsets.UTF_8.encode(json(value)+"\n");
            while(bytes.hasRemaining()) channel.write(bytes);
            channel.force(true);
        }
        public void close() throws IOException { channel.close(); }
    }
}

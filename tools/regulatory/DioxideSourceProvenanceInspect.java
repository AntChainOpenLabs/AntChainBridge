package tools.regulatory;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;

/** Checks the actual plugin JAR's embedded GCL against the selected source tree. */
public final class DioxideSourceProvenanceInspect {
    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            throw new IllegalArgumentException("expected PLUGIN_JAR ONCHAIN_SOURCE_ROOT");
        }
        Path jar = Path.of(args[0]).toRealPath();
        Path root = Path.of(args[1]).toRealPath();
        List<Path> sources;
        try (var paths = Files.walk(root)) {
            sources = paths.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".gcl"))
                    .sorted().toList();
        }
        if (sources.isEmpty()) {
            throw new IllegalArgumentException("source tree contains no GCL files");
        }
        try (var loader = new URLClassLoader(
                new URL[] {jar.toUri().toURL()}, ClassLoader.getPlatformClassLoader())) {
            for (Path source : sources) {
                Path relative = root.relativize(source);
                String resourceClass = relative.getNameCount() == 1 ? "Contracts"
                        : relative.getName(0).toString().equals("interfaces") ? "Interfaces"
                        : relative.getName(0).toString().equals("lib") ? "Libs" : null;
                if (resourceClass == null) {
                    throw new IllegalArgumentException("unexpected GCL location: " + relative);
                }
                String field = source.getFileName().toString().replace(".gcl", "")
                        .replaceAll("[^a-zA-Z0-9]", "_").toUpperCase(java.util.Locale.ROOT);
                Class<?> type = loader.loadClass(
                        "com.alipay.antchain.bridge.plugins.dioxide2.gcl." + resourceClass);
                byte[] embedded = Base64.getDecoder().decode((String) type.getField(field).get(null));
                byte[] original = Files.readAllBytes(source);
                if (!Arrays.equals(original, embedded)) {
                    throw new IllegalStateException("JAR/source mismatch: " + relative);
                }
                String hash = HexFormat.of().formatHex(
                        MessageDigest.getInstance("SHA-256").digest(original));
                System.out.println("source.sha256=" + hash + " path=" + relative + " jarMatch=true");
            }
        }
        System.out.println("source.fileCount=" + sources.size());
        System.out.println("source.allJarMatches=true");
    }
}

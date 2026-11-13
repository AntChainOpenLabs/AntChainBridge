/*
 * Copyright 2024 Ant Group
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.alipay.antchain.bridge.ptc.committee.node.dal.tools;

import com.alipay.antchain.bridge.commons.core.ptc.ValidatedConsensusState;
import com.alipay.antchain.bridge.ptc.committee.node.dal.utils.DioxideConsensusHash;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;
import java.time.Instant;
import java.util.*;

/** Explicit, offline maintenance CLI. It never opens an SDK or submits chain transactions. */
public final class DioxideHashMigration {
    private DioxideHashMigration() {}

    public static void main(String[] args) throws Exception {
        if (args.length < 4 || args.length > 5 || !Set.of("audit", "apply", "verify").contains(args[0])) {
            throw new IllegalArgumentException("audit|apply|verify PRIVATE_PROPERTIES DATABASE NEW_JOURNAL [BATCH_SIZE]");
        }
        Properties config = new Properties();
        try (InputStream in = Files.newInputStream(Path.of(args[1]))) { config.load(in); }
        int batch = args.length == 5 ? Integer.parseInt(args[4]) : 250;
        if (batch < 1 || batch > 5000) throw new IllegalArgumentException("Batch must be 1..5000");
        try (FileChannel journal = FileChannel.open(Path.of(args[3]), StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
             Connection connection = DriverManager.getConnection(config.getProperty("url"), config.getProperty("user"), config.getProperty("password"))) {
            connection.setCatalog(args[2]);
            record(journal, "BEGIN action=" + args[0] + " database=" + args[2]);
            try {
                long changed = run(connection, args[0], batch, journal, () -> {});
                record(journal, "SUCCESS changed=" + changed);
                System.out.println(args[0] + " complete; changed=" + changed);
            } catch (Exception e) {
                // Do not log JDBC credentials or SQL parameter values.
                record(journal, "FAILED type=" + e.getClass().getSimpleName());
                throw new IllegalStateException("Migration stopped; preserve journal and reconcile before retry", e);
            }
        }
    }

    private static void record(FileChannel out, String message) throws Exception {
        ByteBuffer bytes = StandardCharsets.UTF_8.encode(Instant.now() + " " + message + "\n");
        while (bytes.hasRemaining()) out.write(bytes);
        out.force(true);
    }

    private static Map<String, String> products(Connection c, boolean lock) throws SQLException {
        Map<String, String> products = new HashMap<>();
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery("SELECT domain,product FROM bta" + (lock ? " FOR SHARE" : ""))) {
            while (rs.next()) {
                String domain = rs.getString(1), product = rs.getString(2);
                if (product == null || product.isBlank()) throw new IllegalStateException("Missing BTA product");
                product = product.toLowerCase(Locale.ROOT);
                String prior = products.putIfAbsent(domain, product);
                if (prior != null && !prior.equals(product)) throw new IllegalStateException("Conflicting BTA products");
            }
        }
        return products;
    }

    private record Row(long id, String hash, String parent, byte[] raw, String expectedHash, String expectedParent, boolean dioxide) {
        boolean changed() { return !Objects.equals(hash, expectedHash) || !Objects.equals(parent, expectedParent); }
    }

    private static List<Row> page(Connection c, long after, int size, boolean lock, Map<String, String> products, boolean requireDigest) throws Exception {
        List<Row> rows = new ArrayList<>();
        try (PreparedStatement st = c.prepareStatement("SELECT id,domain,height,cs_version,hash,parent_hash,raw_vcs FROM validated_consensus_states WHERE id>? ORDER BY id LIMIT ?" + (lock ? " FOR UPDATE" : ""))) {
            st.setLong(1, after); st.setInt(2, size);
            try (ResultSet rs = st.executeQuery()) {
                while (rs.next()) {
                    String domain = rs.getString("domain"), product = products.get(domain);
                    if (product == null) throw new IllegalStateException("No BTA for consensus state domain " + domain);
                    String hash = rs.getString("hash"), parent = rs.getString("parent_hash");
                    byte[] raw = rs.getBytes("raw_vcs");
                    String expectedHash = hash, expectedParent = parent;
                    boolean dioxide = "dioxide2".equals(product);
                    if (dioxide) {
                        var vcs = ValidatedConsensusState.decode(raw);
                        if (!domain.equals(vcs.getDomain().getDomain()) || !rs.getBigDecimal("height").toBigIntegerExact().equals(vcs.getHeight()) || rs.getInt("cs_version") != vcs.getCsVersion()) {
                            throw new IllegalStateException("Consensus metadata disagrees with raw_vcs at id " + rs.getLong("id"));
                        }
                        expectedHash = DioxideConsensusHash.digest(vcs.getHash());
                        expectedParent = DioxideConsensusHash.digest(vcs.getParentHash());
                        if (!(Objects.equals(hash, expectedHash) || (!requireDigest && Objects.equals(hash, vcs.getHashHex()))) ||
                            !(Objects.equals(parent, expectedParent) || (!requireDigest && Objects.equals(parent, vcs.getParentHashHex())))) {
                            throw new IllegalStateException("Stored hash disagrees with raw_vcs at id " + rs.getLong("id"));
                        }
                    } else if ((hash != null && hash.length() > 64) || (parent != null && parent.length() > 64)) {
                        throw new IllegalStateException("Non-dioxide column exceeds 64 characters at id " + rs.getLong("id"));
                    }
                    rows.add(new Row(rs.getLong("id"), hash, parent, raw, expectedHash, expectedParent, dioxide));
                }
            }
        }
        return rows;
    }

    /** Hook is used only by isolated tests to prove whole-batch rollback. */
    public static long run(Connection c, String action, int batch, FileChannel journal, Runnable beforeCommit) throws Exception {
        if (!Set.of("audit", "apply", "verify").contains(action) || batch < 1 || batch > 5000 || !c.getAutoCommit()) {
            throw new IllegalArgumentException("Invalid migration mode, batch, or connection transaction state");
        }
        Map<String, String> expectedProducts = products(c, false);
        long after = 0, count = 0, dioxide = 0, pending = 0;
        // Validate the entire table before the first write.
        while (true) {
            List<Row> rows = page(c, after, batch, false, expectedProducts, "verify".equals(action));
            if (rows.isEmpty()) break;
            for (Row row : rows) { count++; if (row.dioxide) dioxide++; if (row.changed()) pending++; }
            after = rows.getLast().id;
        }
        record(journal, "PREFLIGHT rows=" + count + " dioxide=" + dioxide + " pending=" + pending);
        if (!"apply".equals(action)) return 0;
        after = 0; long changed = 0;
        while (true) {
            c.setAutoCommit(false);
            try {
                if (!expectedProducts.equals(products(c, true))) throw new IllegalStateException("BTA snapshot changed");
                List<Row> rows = page(c, after, batch, true, expectedProducts, false);
                if (rows.isEmpty()) { c.rollback(); break; }
                long last = rows.getLast().id;
                record(journal, "BATCH_INTENT after=" + after + " through=" + last);
                long batchChanges = 0;
                try (PreparedStatement update = c.prepareStatement("UPDATE validated_consensus_states SET hash=?,parent_hash=?,gmt_modified=gmt_modified WHERE id=? AND BINARY hash<=>BINARY ? AND BINARY parent_hash<=>BINARY ? AND raw_vcs<=>?")) {
                    for (Row row : rows) {
                        if (!row.changed()) continue;
                        update.setString(1, row.expectedHash); update.setString(2, row.expectedParent);
                        update.setLong(3, row.id); update.setString(4, row.hash); update.setString(5, row.parent); update.setBytes(6, row.raw);
                        if (update.executeUpdate() != 1) throw new IllegalStateException("Row snapshot changed at id " + row.id);
                        batchChanges++;
                    }
                }
                beforeCommit.run();
                c.commit();
                changed += batchChanges; after = last;
                record(journal, "BATCH_COMMIT through=" + after + " changed=" + batchChanges);
            } catch (Exception e) {
                c.rollback();
                throw e;
            } finally { c.setAutoCommit(true); }
        }
        return changed;
    }
}

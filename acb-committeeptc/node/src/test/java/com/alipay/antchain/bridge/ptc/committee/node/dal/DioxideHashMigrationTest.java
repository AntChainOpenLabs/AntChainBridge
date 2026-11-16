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

package com.alipay.antchain.bridge.ptc.committee.node.dal;

import com.alipay.antchain.bridge.commons.core.base.CrossChainDomain;
import com.alipay.antchain.bridge.commons.core.ptc.ValidatedConsensusStateV1;
import com.alipay.antchain.bridge.ptc.committee.node.dal.tools.DioxideHashMigration;
import com.alipay.antchain.bridge.ptc.committee.node.dal.utils.DioxideConsensusHash;
import java.math.BigInteger;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/** Deliberately runs only against newly-created local fixture schemas, never business tables. */
public class DioxideHashMigrationTest {
    private Connection db;
    @BeforeEach public void setup() throws Exception {
        String path = System.getProperty("diox.hash.test.config");
        Assumptions.assumeTrue(path != null, "Explicit local MySQL fixture configuration required");
        Properties config = new Properties();
        try (var in=Files.newInputStream(Path.of(path))) { config.load(in); }
        String url=config.getProperty("url");
        if (!url.matches("jdbc:mysql://(?:127\\.0\\.0\\.1|localhost):[0-9]+/diox_hash_fixture_[a-z0-9_]+(?:\\?.*)?")) throw new IllegalArgumentException("Local fixtures only");
        db=DriverManager.getConnection(url,config.getProperty("user"),config.getProperty("password"));
        String schema="diox_hash_fixture_migration_"+UUID.randomUUID().toString().replace("-", "");
        try(var st=db.createStatement()) { st.execute("CREATE DATABASE "+schema); }
        db.setCatalog(schema);
        try(var st=db.createStatement()) {
            st.execute("CREATE TABLE bta(id INT AUTO_INCREMENT PRIMARY KEY,domain VARCHAR(128),product VARCHAR(64)) ENGINE=InnoDB");
            st.execute("INSERT INTO bta(domain,product) VALUES('test','dioxide2'),('other','fiscobcos3')");
            st.execute("CREATE TABLE validated_consensus_states(id INT AUTO_INCREMENT PRIMARY KEY,domain VARCHAR(128),height BIGINT,cs_version INT,hash VARCHAR(128),parent_hash VARCHAR(128),raw_vcs BLOB,gmt_modified DATETIME DEFAULT CURRENT_TIMESTAMP) ENGINE=InnoDB");
        }
        insert("test",1);insert("test",2);insert("other",3);
    }
    @AfterEach public void close() throws Exception { if(db!=null)db.close(); }
    private void insert(String domain,int n) throws Exception {
        var vcs=new ValidatedConsensusStateV1();vcs.setDomain(new CrossChainDomain(domain));vcs.setHeight(BigInteger.valueOf(n));
        byte[] h=new byte[domain.equals("test")?52:32];Arrays.fill(h,(byte)n);vcs.setHash(h);vcs.setParentHash(h);
        try(var st=db.prepareStatement("INSERT INTO validated_consensus_states(domain,height,cs_version,hash,parent_hash,raw_vcs) VALUES(?,?,?,?,?,?)")) {
            st.setString(1,domain);st.setInt(2,n);st.setInt(3,vcs.getCsVersion());st.setString(4,vcs.getHashHex());st.setString(5,vcs.getParentHashHex());st.setBytes(6,vcs.encode());st.executeUpdate();
        }
    }
    private long run(String mode,int batch,Runnable hook) throws Exception {
        var file=Files.createTempFile("dioxide-migration-test-", ".log");
        try(var journal=FileChannel.open(file,StandardOpenOption.WRITE)) { return DioxideHashMigration.run(db,mode,batch,journal,hook); }
    }
    private String value(int id,String expression) throws Exception {
        try(var st=db.createStatement();var rs=st.executeQuery("SELECT "+expression+" FROM validated_consensus_states WHERE id="+id)){assertTrue(rs.next());return rs.getString(1);}
    }
    @Test public void auditApplyVerifyAndRepeatedApply() throws Exception {
        String raw=value(1,"HEX(raw_vcs)"),other=value(3,"hash"),time=value(1,"gmt_modified");
        assertEquals(0,run("audit",1,()->{}));assertEquals(104,value(1,"hash").length());
        assertThrows(IllegalStateException.class,()->run("verify",1,()->{}));
        assertEquals(2,run("apply",1,()->{}));assertEquals(0,run("apply",1,()->{}));assertEquals(0,run("verify",1,()->{}));
        assertEquals(64,value(1,"hash").length());assertEquals(raw,value(1,"HEX(raw_vcs)"));assertEquals(other,value(3,"hash"));assertEquals(time,value(1,"gmt_modified"));
    }
    @Test public void failedTransactionRollsBackWholeBatch() throws Exception {
        assertThrows(IllegalStateException.class,()->run("apply",2,()->{throw new IllegalStateException("injected before commit");}));
        assertEquals(104,value(1,"hash").length());assertEquals(104,value(2,"hash").length());
        assertEquals(2,run("apply",2,()->{}));
    }
    @Test public void interruptedRunResumesWithoutDoubleHashing() throws Exception {
        var calls=new AtomicInteger();
        assertThrows(IllegalStateException.class,()->run("apply",1,()->{if(calls.incrementAndGet()==2)throw new IllegalStateException("injected interruption");}));
        String committed=value(1,"hash");assertEquals(64,committed.length());assertEquals(104,value(2,"hash").length());
        assertEquals(1,run("apply",1,()->{}));assertEquals(committed,value(1,"hash"));
    }
    @Test public void badEvidenceIsRejectedBeforeAnyWrite() throws Exception {
        try(var st=db.createStatement()){st.execute("UPDATE validated_consensus_states SET parent_hash=REPEAT('0',104) WHERE id=2");}
        assertThrows(IllegalStateException.class,()->run("apply",1,()->{}));
        assertEquals(104,value(1,"hash").length());
    }
    @Test public void journalFailurePreventsWrites() throws Exception {
        var file=Files.createTempFile("dioxide-closed-journal-", ".log");
        var closed=FileChannel.open(file,StandardOpenOption.WRITE);closed.close();
        assertThrows(java.nio.channels.ClosedChannelException.class,()->DioxideHashMigration.run(db,"apply",1,closed,()->{}));
        assertEquals(104,value(1,"hash").length());
    }
    @Test public void btaSnapshotChangeStopsNextBatch() throws Exception {
        assertThrows(IllegalStateException.class,()->run("apply",1,()->{
            try(var st=db.createStatement()){st.execute("UPDATE bta SET product='mychain2' WHERE domain='other'");}
            catch(SQLException e){throw new IllegalStateException(e);}
        }));
        assertEquals(64,value(1,"hash").length());assertEquals(104,value(2,"hash").length());
    }
}

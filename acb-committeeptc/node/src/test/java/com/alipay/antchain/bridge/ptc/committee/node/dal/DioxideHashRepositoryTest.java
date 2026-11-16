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

import com.alipay.antchain.bridge.ptc.committee.node.dal.repository.EndorseServiceRepositoryImpl;
import com.alipay.antchain.bridge.ptc.committee.node.dal.mapper.*;
import com.alipay.antchain.bridge.ptc.committee.node.dal.utils.DioxideConsensusHash;
import com.alipay.antchain.bridge.commons.core.base.*;
import com.alipay.antchain.bridge.commons.core.ptc.ValidatedConsensusStateV1;
import com.alipay.antchain.bridge.ptc.committee.node.commons.models.ValidatedConsensusStateWrapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.apache.ibatis.session.SqlSession;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;
import org.junit.jupiter.api.*;
import java.math.BigInteger;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Uses real MyBatis mappers. Optional MySQL fixture schema must be explicitly named diox_hash_*. */
public class DioxideHashRepositoryTest {
    private Connection db;
    private SqlSession session;
    private EndorseServiceRepositoryImpl repo;
    private String domain;

    @BeforeEach public void setup() throws Exception {
        Properties config = new Properties();
        String configPath = System.getProperty("diox.hash.test.config");
        String url, user, password;
        if (configPath == null) {
            url = "jdbc:h2:mem:hash_" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1";
            user = "sa"; password = "";
        } else {
            try (var in = Files.newInputStream(Path.of(configPath))) { config.load(in); }
            url = config.getProperty("url"); user = config.getProperty("user"); password = config.getProperty("password");
            if (!url.matches("jdbc:mysql://(?:127\\.0\\.0\\.1|localhost):[0-9]+/diox_hash_fixture_[a-z0-9_]+(?:\\?.*)?")) {
                throw new IllegalArgumentException("Only explicit local diox_hash_fixture_* schemas may receive fixtures");
            }
        }
        var ds = new DriverManagerDataSource(url, user, password);
        db = ds.getConnection();
        try (var st = db.createStatement()) {
            st.execute("CREATE TABLE IF NOT EXISTS bta (id INT AUTO_INCREMENT PRIMARY KEY,domain VARCHAR(128),product VARCHAR(64),subject_version INT,bta_version INT,raw_bta BLOB,gmt_create DATETIME,gmt_modified DATETIME)");
            st.execute("CREATE TABLE IF NOT EXISTS validated_consensus_states (id INT AUTO_INCREMENT PRIMARY KEY,cs_version INT NOT NULL,domain VARCHAR(128) NOT NULL,height BIGINT NOT NULL,hash VARCHAR(64),parent_hash VARCHAR(64),raw_vcs LONGBLOB,gmt_create DATETIME DEFAULT CURRENT_TIMESTAMP,gmt_modified DATETIME DEFAULT CURRENT_TIMESTAMP,UNIQUE(domain,height))");
        }
        var configuration = new MybatisConfiguration();
        configuration.setEnvironment(new Environment("isolated-test", new JdbcTransactionFactory(), ds));
        configuration.addMapper(BtaMapper.class);
        configuration.addMapper(ValidatedConsensusStatesMapper.class);
        session = new MybatisSqlSessionFactoryBuilder().build(configuration).openSession(true);
        repo = new EndorseServiceRepositoryImpl();
        ReflectionTestUtils.setField(repo, "btaMapper", session.getMapper(BtaMapper.class));
        ReflectionTestUtils.setField(repo, "validatedConsensusStatesMapper", session.getMapper(ValidatedConsensusStatesMapper.class));
        domain = "test-" + UUID.randomUUID();
    }
    @AfterEach public void close() throws Exception { if (session != null) session.close(); if (db != null) db.close(); }

    private void register(String product) throws Exception {
        try (var st=db.prepareStatement("INSERT INTO bta(domain,product,subject_version,bta_version) VALUES(?,?,0,1)")) {
            st.setString(1,domain); st.setString(2,product); st.executeUpdate();
        }
    }
    private ValidatedConsensusStateWrapper state(int height, int length) {
        var vcs = new ValidatedConsensusStateV1();
        vcs.setDomain(new CrossChainDomain(domain)); vcs.setHeight(BigInteger.valueOf(height));
        byte[] hash = new byte[length], parent = new byte[length];
        Arrays.fill(hash,(byte)height); Arrays.fill(parent,(byte)(height-1));
        vcs.setHash(hash); vcs.setParentHash(parent); vcs.setStateTimestamp(123456L);
        vcs.setStateData(new byte[0]); vcs.setConsensusNodeInfo(new byte[0]); vcs.setEndorsements(new byte[0]);
        return new ValidatedConsensusStateWrapper(vcs);
    }
    private String stored(String column,int height) throws Exception {
        try (var st=db.prepareStatement("SELECT "+column+" FROM validated_consensus_states WHERE domain=? AND height=?")) {
            st.setString(1,domain);st.setInt(2,height);try(var rs=st.executeQuery()){assertTrue(rs.next());return rs.getString(1);}
        }
    }
    private void rawInsert(ValidatedConsensusStateWrapper row,String key) throws Exception {
        try (var st=db.prepareStatement("INSERT INTO validated_consensus_states(domain,height,cs_version,hash,parent_hash,raw_vcs) VALUES(?,?,?,?,?,?)")) {
            st.setString(1,domain); st.setString(2,row.getHeight().toString()); st.setInt(3,row.getValidatedConsensusState().getCsVersion());
            st.setString(4,key); st.setString(5,DioxideConsensusHash.digest(row.getValidatedConsensusState().getParentHash()));
            st.setBytes(6,row.getValidatedConsensusState().encode()); st.executeUpdate();
        }
    }
    @Test public void knownHashUsesBytes() {
        String raw="34637277717a626a7a72657767353837386b713262703872356a333865323030377737763562783373726433646a763872683867";
        assertEquals("173b7ce14a3ec24b8481f52201233b18c277ac588901db8af521598b2ee92617",DioxideConsensusHash.digest(DioxideConsensusHash.decode(raw)));
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",DioxideConsensusHash.digest(new byte[0]));
        assertThrows(IllegalArgumentException.class,()->DioxideConsensusHash.decode("xyz"));
        assertThrows(IllegalArgumentException.class,()->DioxideConsensusHash.digest(null));
    }
    @Test public void dioxideWritesDigestAndPreservesEncodedState() throws Exception {
        register("dioxide2"); var state=state(7,52); byte[] raw=state.getValidatedConsensusState().encode();
        repo.setValidatedConsensusState(state);
        assertEquals(DioxideConsensusHash.digest(state.getValidatedConsensusState().getHash()),stored("hash",7));
        assertEquals(DioxideConsensusHash.digest(state.getValidatedConsensusState().getParentHash()),stored("parent_hash",7));
        assertArrayEquals(raw,repo.getValidatedConsensusState(domain,BigInteger.valueOf(7)).getValidatedConsensusState().encode());
        assertArrayEquals(raw,repo.getValidatedConsensusState(domain,state.getValidatedConsensusState().getHashHex().toUpperCase(Locale.ROOT)).getValidatedConsensusState().encode());
        assertArrayEquals(raw,repo.getLatestValidatedConsensusState(domain).getValidatedConsensusState().encode());
        assertTrue(repo.hasValidatedConsensusState(domain,BigInteger.valueOf(7)));
        assertNull(repo.getValidatedConsensusState(domain,"ab".repeat(52)));
    }
    @Test public void plainDioxideWritesDigestAndReadsOriginalHash() throws Exception {
        register("dioxide"); var state=state(12,52); byte[] raw=state.getValidatedConsensusState().encode();
        repo.setValidatedConsensusState(state);
        assertEquals(DioxideConsensusHash.digest(state.getValidatedConsensusState().getHash()),stored("hash",12));
        assertEquals(DioxideConsensusHash.digest(state.getValidatedConsensusState().getParentHash()),stored("parent_hash",12));
        assertArrayEquals(raw,repo.getValidatedConsensusState(domain,state.getValidatedConsensusState().getHashHex()).getValidatedConsensusState().encode());
        assertArrayEquals(raw,repo.getValidatedConsensusState(domain,BigInteger.valueOf(12)).getValidatedConsensusState().encode());
        assertArrayEquals(raw,repo.getLatestValidatedConsensusState(domain).getValidatedConsensusState().encode());
    }
    @Test public void otherProductsPreserveOriginalIncludingDioxLikeDomain() throws Exception {
        for(String product:List.of("ethereum3","fiscobcos3","mychain2","unknown-product")) {
            domain="diox-looking-"+UUID.randomUUID(); register(product); var state=state(8,32);
            repo.setValidatedConsensusState(state);
            assertEquals(state.getValidatedConsensusState().getHashHex(),stored("hash",8));
            assertEquals(state.getParentHash(),stored("parent_hash",8));
            assertNotNull(repo.getValidatedConsensusState(domain,state.getValidatedConsensusState().getHashHex()));
        }
    }
    @Test public void missingAndConflictingProductsRejectWrites() throws Exception {
        assertThrows(RuntimeException.class,()->repo.setValidatedConsensusState(state(1,52)));
        register(null); assertThrows(RuntimeException.class,()->repo.setValidatedConsensusState(state(1,52)));
        domain="conflict-"+UUID.randomUUID(); register("dioxide2");register("ethereum3");
        assertThrows(RuntimeException.class,()->repo.setValidatedConsensusState(state(1,52)));
        assertFalse(repo.hasValidatedConsensusState(domain,BigInteger.ONE));
        assertThrows(RuntimeException.class,()->repo.getValidatedConsensusState(domain,"01".repeat(52)));
    }
    @Test public void productQueryFailureNeverWrites() {
        BtaMapper broken=mock(BtaMapper.class); when(broken.selectList(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenThrow(new RuntimeException("DB unavailable"));
        ReflectionTestUtils.setField(repo,"btaMapper",broken);
        var mapper=mock(ValidatedConsensusStatesMapper.class);
        ReflectionTestUtils.setField(repo,"validatedConsensusStatesMapper",mapper);
        assertThrows(RuntimeException.class,()->repo.setValidatedConsensusState(state(1,52)));
        verify(mapper,never()).insert(any(com.alipay.antchain.bridge.ptc.committee.node.dal.entities.ValidatedConsensusStatesEntity.class));
    }
    @Test public void legacyAndNewRowsRemainReadable() throws Exception {
        register("dioxide2");var legacy=state(2,52);var modern=state(3,52);
        try(var st=db.createStatement()){st.execute("ALTER TABLE validated_consensus_states MODIFY COLUMN hash VARCHAR(128)");}
        try {
            rawInsert(legacy,legacy.getValidatedConsensusState().getHashHex()); repo.setValidatedConsensusState(modern);
            assertNotNull(repo.getValidatedConsensusState(domain,legacy.getValidatedConsensusState().getHashHex()));
            assertNotNull(repo.getValidatedConsensusState(domain,modern.getValidatedConsensusState().getHashHex()));
        } finally {
            try(var st=db.prepareStatement("UPDATE validated_consensus_states SET hash=? WHERE domain=? AND height=2")) {
                st.setString(1,DioxideConsensusHash.digest(legacy.getValidatedConsensusState().getHash()));st.setString(2,domain);st.executeUpdate();
            }
            try(var st=db.createStatement()){st.execute("ALTER TABLE validated_consensus_states MODIFY COLUMN hash VARCHAR(64)");}
        }
    }
    @Test public void contradictoryRawStateIsRejected() throws Exception {
        register("dioxide2"); var first=state(4,52); var second=state(5,52);
        rawInsert(second,DioxideConsensusHash.digest(first.getValidatedConsensusState().getHash()));
        assertThrows(RuntimeException.class,()->repo.getValidatedConsensusState(domain,first.getValidatedConsensusState().getHashHex()));
    }
    @Test public void duplicateCandidatesAreRejected() throws Exception {
        register("dioxide2");var first=state(6,52); var second=state(9,52);second.getValidatedConsensusState().setHash(first.getValidatedConsensusState().getHash());
        repo.setValidatedConsensusState(first); repo.setValidatedConsensusState(second);
        assertThrows(RuntimeException.class,()->repo.getValidatedConsensusState(domain,first.getValidatedConsensusState().getHashHex()));
    }
}

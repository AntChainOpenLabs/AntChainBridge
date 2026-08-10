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

package com.alipay.antchain.bridge.ptc.committee.monitor.node.dal;

import java.math.BigInteger;
import java.security.KeyPairGenerator;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.ListUtil;
import cn.hutool.core.util.HexUtil;
import cn.hutool.core.util.RandomUtil;
import com.alipay.antchain.bridge.commons.bcdns.PTCCredentialSubject;
import com.alipay.antchain.bridge.commons.core.base.*;
import com.alipay.antchain.bridge.commons.core.bta.BlockchainTrustAnchorV1;
import com.alipay.antchain.bridge.commons.core.ptc.PTCTypeEnum;
import com.alipay.antchain.bridge.commons.core.ptc.ThirdPartyBlockchainTrustAnchorV1;
import com.alipay.antchain.bridge.commons.core.ptc.ValidatedConsensusStateV1;
import com.alipay.antchain.bridge.commons.utils.crypto.HashAlgoEnum;
import com.alipay.antchain.bridge.commons.utils.crypto.SignAlgoEnum;
import com.alipay.antchain.bridge.ptc.committee.monitor.node.TestBase;
import com.alipay.antchain.bridge.ptc.committee.monitor.node.commons.models.BtaWrapper;
import com.alipay.antchain.bridge.ptc.committee.monitor.node.commons.models.TpBtaWrapper;
import com.alipay.antchain.bridge.ptc.committee.monitor.node.commons.models.ValidatedConsensusStateWrapper;
import com.alipay.antchain.bridge.ptc.committee.monitor.node.dal.repository.interfaces.IEndorseServiceRepository;
import com.alipay.antchain.bridge.ptc.committee.types.basic.CommitteeEndorseProof;
import com.alipay.antchain.bridge.ptc.committee.types.basic.CommitteeNodeProof;
import com.alipay.antchain.bridge.ptc.committee.types.basic.NodePublicKeyEntry;
import com.alipay.antchain.bridge.ptc.committee.types.tpbta.CommitteeEndorseRoot;
import com.alipay.antchain.bridge.ptc.committee.types.tpbta.NodeEndorseInfo;
import com.alipay.antchain.bridge.ptc.committee.types.tpbta.OptionalEndorsePolicy;
import jakarta.annotation.Resource;
import lombok.SneakyThrows;
import org.junit.Assert;
import org.junit.Test;

public class EndorseServiceRepositoryTest extends TestBase {

    @Resource
    private IEndorseServiceRepository endorseServiceRepository;

    @SneakyThrows
    @Test
    public void testBta() {
        var oid = new ObjectIdentity(
                ObjectIdentityType.X509_PUBLIC_KEY_INFO,
                KeyPairGenerator.getInstance("RSA").generateKeyPair().getPublic().getEncoded()
        );
        var bta = new BlockchainTrustAnchorV1();
        bta.setBcOwnerPublicKey("test".getBytes());
        bta.setBcOwnerSig("test".getBytes());
        bta.setDomain(new CrossChainDomain("test"));
        bta.setSubjectIdentity("test".getBytes());
        bta.setBcOwnerSigAlgo(SignAlgoEnum.SHA256_WITH_ECDSA);
        bta.setPtcOid(oid);
        bta.setSubjectProduct("test");
        bta.setExtension("test".getBytes());
        bta.setSubjectVersion(0);

        var btaWrapper = new BtaWrapper();
        btaWrapper.setBta(bta);
        endorseServiceRepository.setBta(btaWrapper);

        var btaWrapperFromDB = endorseServiceRepository.getBta(btaWrapper.getDomain());
        Assert.assertEquals(
                HexUtil.encodeHexStr(btaWrapper.getBta().getSubjectIdentity()),
                HexUtil.encodeHexStr(btaWrapperFromDB.getBta().getSubjectIdentity())
        );
    }

    @Test
    @SneakyThrows
    public void testTpBta() {
        var policy = new OptionalEndorsePolicy();
        policy.setThreshold(new OptionalEndorsePolicy.Threshold(OptionalEndorsePolicy.OperatorEnum.GREATER_THAN, 1));
        var nodeEndorseInfo = new NodeEndorseInfo();
        nodeEndorseInfo.setNodeId("test");
        nodeEndorseInfo.setRequired(true);
        var nodePubkeyEntry = new NodePublicKeyEntry("default", SignAlgoEnum.KECCAK256_WITH_SECP256K1.getSigner().generateKeyPair().getPublic());
        nodeEndorseInfo.setPublicKey(nodePubkeyEntry);
        var crossChainLane = new CrossChainLane(new CrossChainDomain("test"), new CrossChainDomain("test"), CrossChainIdentity.fromHexStr("0000000000000000000000000000000000000000000000000000000000000001"), CrossChainIdentity.fromHexStr("0000000000000000000000000000000000000000000000000000000000000001"));
        var tpbta = new ThirdPartyBlockchainTrustAnchorV1(
                1,
                BigInteger.ONE,
                new PTCCredentialSubject(
                        "1",
                        "ptc",
                        PTCTypeEnum.COMMITTEE,
                        new X509PubkeyInfoObjectIdentity(
                                SignAlgoEnum.KECCAK256_WITH_SECP256K1.getSigner().generateKeyPair().getPublic().getEncoded()
                        ),
                        new byte[]{}
                ),
                crossChainLane,
                1,
                HashAlgoEnum.KECCAK_256,
                new CommitteeEndorseRoot(
                        "committee",
                        policy,
                        ListUtil.toList(nodeEndorseInfo)
                ).encode(),
                CommitteeEndorseProof.builder()
                        .committeeId("committee")
                        .sigs(ListUtil.toList(new CommitteeNodeProof(
                                "test",
                                SignAlgoEnum.KECCAK256_WITH_SECP256K1,
                                "".getBytes()
                        ))).build().encode()
        );
        var tpBtaWrapper = new TpBtaWrapper(tpbta);

        endorseServiceRepository.setTpBta(tpBtaWrapper);

        Assert.assertNotNull(endorseServiceRepository.getMatchedTpBta(crossChainLane));
        Assert.assertTrue(endorseServiceRepository.hasTpBta(crossChainLane, 1));
        Assert.assertNotNull(endorseServiceRepository.getMatchedTpBta(crossChainLane));
    }

    @Test
    public void testValidatedConsensusState() {
        var cs = new ConsensusState(
                new CrossChainDomain("test"),
                BigInteger.valueOf(100L),
                RandomUtil.randomBytes(32),
                RandomUtil.randomBytes(32),
                System.currentTimeMillis(),
                new byte[]{},
                new byte[]{},
                new byte[]{}
        );
        var vcs = BeanUtil.copyProperties(cs, ValidatedConsensusStateV1.class);

        endorseServiceRepository.setValidatedConsensusState(new ValidatedConsensusStateWrapper(vcs));

        Assert.assertTrue(endorseServiceRepository.hasValidatedConsensusState("test", BigInteger.valueOf(100L)));
        Assert.assertEquals(
                cs.getHashHex(),
                endorseServiceRepository.getValidatedConsensusState("test", BigInteger.valueOf(100L)).getValidatedConsensusState().getHashHex()
        );
        Assert.assertEquals(
                cs.getHeight(),
                endorseServiceRepository.getValidatedConsensusState("test", BigInteger.valueOf(100L)).getHeight()
        );
        Assert.assertEquals(
                cs.getParentHashHex(),
                endorseServiceRepository.getValidatedConsensusState("test", cs.getHashHex()).getParentHash()
        );
    }
}

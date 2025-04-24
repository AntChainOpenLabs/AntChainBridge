/*
 * Copyright 2023 Ant Group
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

package com.alipay.antchain.bridge.plugins.fiscobcos;

import cn.hutool.core.collection.ListUtil;
import cn.hutool.core.map.MapUtil;
import cn.hutool.core.util.ByteUtil;
import cn.hutool.core.util.HexUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.crypto.PemUtil;
import cn.hutool.crypto.digest.DigestUtil;
import com.alipay.antchain.bridge.commons.bbc.AbstractBBCContext;
import com.alipay.antchain.bridge.commons.bbc.DefaultBBCContext;
import com.alipay.antchain.bridge.commons.bbc.syscontract.AuthMessageContract;
import com.alipay.antchain.bridge.commons.bbc.syscontract.ContractStatusEnum;
import com.alipay.antchain.bridge.commons.bbc.syscontract.PTCContract;
import com.alipay.antchain.bridge.commons.bbc.syscontract.SDPContract;
import com.alipay.antchain.bridge.commons.bcdns.AbstractCrossChainCertificate;
import com.alipay.antchain.bridge.commons.bcdns.PTCCredentialSubject;
import com.alipay.antchain.bridge.commons.bcdns.utils.CrossChainCertificateUtil;
import com.alipay.antchain.bridge.commons.core.am.AuthMessageFactory;
import com.alipay.antchain.bridge.commons.core.am.IAuthMessage;
import com.alipay.antchain.bridge.commons.core.base.*;
import com.alipay.antchain.bridge.commons.core.ptc.*;
import com.alipay.antchain.bridge.commons.core.sdp.ISDPMessage;
import com.alipay.antchain.bridge.commons.core.sdp.SDPMessageFactory;
import com.alipay.antchain.bridge.commons.utils.codec.tlv.TLVTypeEnum;
import com.alipay.antchain.bridge.commons.utils.codec.tlv.TLVUtils;
import com.alipay.antchain.bridge.commons.utils.codec.tlv.annotation.TLVField;
import com.alipay.antchain.bridge.commons.utils.crypto.HashAlgoEnum;
import com.alipay.antchain.bridge.commons.utils.crypto.SignAlgoEnum;
import com.alipay.antchain.bridge.plugins.fiscobcos.abi.AppContract;
import com.alipay.antchain.bridge.plugins.fiscobcos.abi.AuthMsg;
import com.alipay.antchain.bridge.plugins.fiscobcos.abi.SDPMsg;
import com.alipay.antchain.bridge.plugins.spi.bbc.AbstractBBCService;
import com.alipay.antchain.bridge.ptc.committee.types.basic.CommitteeEndorseProof;
import com.alipay.antchain.bridge.ptc.committee.types.basic.CommitteeNodeProof;
import com.alipay.antchain.bridge.ptc.committee.types.basic.NodePublicKeyEntry;
import com.alipay.antchain.bridge.ptc.committee.types.network.CommitteeNetworkInfo;
import com.alipay.antchain.bridge.ptc.committee.types.tpbta.CommitteeEndorseRoot;
import com.alipay.antchain.bridge.ptc.committee.types.tpbta.NodeEndorseInfo;
import com.alipay.antchain.bridge.ptc.committee.types.tpbta.OptionalEndorsePolicy;
import com.alipay.antchain.bridge.ptc.committee.types.trustroot.CommitteeVerifyAnchor;
import lombok.Getter;
import lombok.Setter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.fisco.bcos.sdk.v3.BcosSDK;
import org.fisco.bcos.sdk.v3.client.Client;
import org.fisco.bcos.sdk.v3.config.ConfigOption;
import org.fisco.bcos.sdk.v3.config.model.*;
import org.fisco.bcos.sdk.v3.model.TransactionReceipt;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Method;
import java.math.BigInteger;
import java.nio.ByteOrder;
import java.security.PrivateKey;
import java.util.*;

@Slf4j
public class FISCOBCOSBBCServiceTest {
    private static final String CA_CERT =
            "-----BEGIN CERTIFICATE-----\n" +
                    "MIIDITCCAgkCFEVsj5CJ/L0eQGo0lch0GGfcK+EwMA0GCSqGSIb3DQEBCwUAMEwx\n" +
                    "HDAaBgNVBAMME0ZJU0NPLUJDT1MtMzU0YjAxODUxHDAaBgNVBAoME0ZJU0NPLUJD\n" +
                    "T1MtMzU0YjAxODUxDjAMBgNVBAsMBWNoYWluMCAXDTI1MDMxNDA5MTA1NVoYDzIx\n" +
                    "MjUwMjE4MDkxMDU1WjBMMRwwGgYDVQQDDBNGSVNDTy1CQ09TLTM1NGIwMTg1MRww\n" +
                    "GgYDVQQKDBNGSVNDTy1CQ09TLTM1NGIwMTg1MQ4wDAYDVQQLDAVjaGFpbjCCASIw\n" +
                    "DQYJKoZIhvcNAQEBBQADggEPADCCAQoCggEBAKXS3OFzQD5y4hS7Wt1DLuNXuzh9\n" +
                    "wnOVW4HNUVZfIg5XtYp0nTlaZLiyCtmEdUHbOr15wIn4lRB1RygwM1L5NO7BKkCH\n" +
                    "Lzj8xsWf0G0OH9HDk5sLAW/Dusct6EupVFVHuRZq4Rj+MOQfYvPHWjTV52pWq+W1\n" +
                    "0BfULvrTmwcteerXxwVnFMVC7qDReOvaUsMsPEQx4NZDLxZ1H0+Dsb5lyXRHVE0J\n" +
                    "zwxlCE42oez5sPc3+6rHL0t0bdq/DfC/V4E5NAtOf/gZP62TTzrCkrp3w+FxptgP\n" +
                    "QohAnrV664ztJla6qzTuY1QvbDmYFxA5pTvXVEOpq9PRTvxDV9osrAge5z0CAwEA\n" +
                    "ATANBgkqhkiG9w0BAQsFAAOCAQEAjSo08bzQLZw6G1f2u+Mp1HGGLF1KOYm88aCD\n" +
                    "tm9PyCuLXk5RvclqpuBfe5iBXIx9H0IoaeeEyMhjShKCZw7UJRJJ6RVBymft7272\n" +
                    "QO6JuJ+lafDmtaOkr8O03MGqcIBi0lxrWFZ8JY9zuR11wxyfNMySQidbp/SG4wnU\n" +
                    "ZHMbdRiWJAzvdHQsx0RX9oCMocBQtfv/HAQGPjOw/8aRLJtW12a+kUAvIb34upFn\n" +
                    "N2vTdVPutKNt/ZJcHSWPnC6JwiJO62Z+yIAeQv3Jv/T3Rt+hZUUs8AIdXweSkS2j\n" +
                    "oFtdL1UxhJTpWgz3NsYuvWHyFf0KLrppITkqYYYelzUku6RbDw==\n" +
                    "-----END CERTIFICATE-----";

    private static final String SSL_CERT =
            "-----BEGIN CERTIFICATE-----\n" +
                    "MIIDLTCCAhWgAwIBAgIUcjAyggN8LDe5ofcNWAPzLFE3XmkwDQYJKoZIhvcNAQEL\n" +
                    "BQAwTDEcMBoGA1UEAwwTRklTQ08tQkNPUy0zNTRiMDE4NTEcMBoGA1UECgwTRklT\n" +
                    "Q08tQkNPUy0zNTRiMDE4NTEOMAwGA1UECwwFY2hhaW4wIBcNMjUwMzE0MDkxMDU1\n" +
                    "WhgPMjEyNTAyMTgwOTEwNTVaMEQxHDAaBgNVBAMME0ZJU0NPLUJDT1MtMzU0YjAx\n" +
                    "ODUxEzARBgNVBAoMCmZpc2NvLWJjb3MxDzANBgNVBAsMBmFnZW5jeTCCASIwDQYJ\n" +
                    "KoZIhvcNAQEBBQADggEPADCCAQoCggEBAMPVdwiCW6VoWZ9rAS+u39Q2hgMZDTQV\n" +
                    "DBXW0YMcqTkQ/bCmgps/obMH3jlZkFGu2ablwumqsznUcU/0ztLSfCK9JAAxplMv\n" +
                    "8LxRbxl5wdF0HCEbLh5br5R10Ua/AzrCPxqHpHnfHm6/h+eBF3aicflx1WG/Fwfh\n" +
                    "/31MepKHkXRPA/7NIODBK6baQgVulqA+VUFXT/WwPdqTgb7h29PBJm8JOn1fiVtJ\n" +
                    "6+BVAka8OIcSO+v+m7RyWKIVYU450LNOPwe3fxmjhey0/nuh43hkZxn/pQkWkPf5\n" +
                    "0t+4Lv8kYIe5PGUyHmDVhiO58GQKDu+LngqRmBGeQQ28E6CWjUxwjhkCAwEAAaMN\n" +
                    "MAswCQYDVR0TBAIwADANBgkqhkiG9w0BAQsFAAOCAQEAMF+WQie9d8/i/eHAa5DT\n" +
                    "g7uxw6qO8IiZFGyxKtjpNo2k/Rc6y2LUgnCW3nRTAbpAdLu93JNZwLAbwZcfAjMe\n" +
                    "obxw2Jl1q5O5TJwQthYRnDrg6SE6aDmuMl3a/dF+ItBs25i5+a98DB7r/wqnq+ov\n" +
                    "T7y4nYGa5vvMymzQ1x+IhkTJbJ5S14L8DLPjfZ749Kve9uLSaHvX8GJQbh1RoJii\n" +
                    "xb0pQb3Iadp4q31vkYA7aCRNas1bFu4A14ekAuXpU291By3EVX4PT9chXMMSxHq8\n" +
                    "TUyrvOOObOcyJ2xf3IZUugRSV5IRuR+Q0sbk8YymtjCrw6j1TmODR9MTTefcnwyt\n" +
                    "iA==\n" +
                    "-----END CERTIFICATE-----";

    private static final String SSL_KEY =
            "-----BEGIN PRIVATE KEY-----\n" +
                    "MIIEvwIBADANBgkqhkiG9w0BAQEFAASCBKkwggSlAgEAAoIBAQDD1XcIglulaFmf\n" +
                    "awEvrt/UNoYDGQ00FQwV1tGDHKk5EP2wpoKbP6GzB945WZBRrtmm5cLpqrM51HFP\n" +
                    "9M7S0nwivSQAMaZTL/C8UW8ZecHRdBwhGy4eW6+UddFGvwM6wj8ah6R53x5uv4fn\n" +
                    "gRd2onH5cdVhvxcH4f99THqSh5F0TwP+zSDgwSum2kIFbpagPlVBV0/1sD3ak4G+\n" +
                    "4dvTwSZvCTp9X4lbSevgVQJGvDiHEjvr/pu0cliiFWFOOdCzTj8Ht38Zo4XstP57\n" +
                    "oeN4ZGcZ/6UJFpD3+dLfuC7/JGCHuTxlMh5g1YYjufBkCg7vi54KkZgRnkENvBOg\n" +
                    "lo1McI4ZAgMBAAECggEBAInQ6xEg9DAGrLPFETOmZLyqlksN0T3q5fNkl1Mm02xm\n" +
                    "qbIsrFNnR3t/uQMxJaBmZaPPpAjfaXv0Jr43MWoqWNP1uaUtS7jVTxyoToBmMGEf\n" +
                    "zj/6Kc2RhpH7DAk2maY7Vz9rX/OocnlL7u+b6JBDp+P9GUbNvP5+LFfYf5YpM9Le\n" +
                    "IQMoKc/PnL+Z6hUnfvbkph4K/GCA7eXV9BRIHCEnJtSTUfi20af/leNHaNuwLrdF\n" +
                    "9Krp2DcgfKg4BlroGd4Sz3tFS2iIgOceNgtqjyFTWKvLXZgFIhp1D7PPOpnv88Ji\n" +
                    "GGsToBUtLt8b3JNB/W9rhWati9sir+JIN1YczB0N2VUCgYEA7ARRxKdBfCiyL/cq\n" +
                    "GiSJgl2PFqoEELPsbBtDtdjMf1eZPbFAO9hwgK2TRzuztie6dqW+yqEN40Tw2vzi\n" +
                    "gmtrNytjx8pFR7Gq/th9PYC1zJbPBRSUMV4C85wI8pDUXoZNvfonrSZdqTVYZBIK\n" +
                    "QyccEgKfBL1U+x9FBu0B2kQxeocCgYEA1GotC/lTY3QmkAEgQbjkl6N+iudSPjhu\n" +
                    "n8apztQfO0iPQEaoqtnv56kWtbbGT3Q/kI+ZDDZRjHeWa48AFD+GGtDIcRcTA15R\n" +
                    "cfn8GRfvHOPe0VNhxiPm3P6yP21kw2X1gtGdrsCluNE2yyFjqhgPQBYeWxYr+4JO\n" +
                    "HxmW37oCul8CgYAysQrO7g2GmUcMPk3wp4BRW77r40BURhC1d3WnjRT/FNV5BqUB\n" +
                    "NY+UU7OaTRxgN1A5Q4gjBUxyT9BbeI097cxtYQhhVPRkXaiYa+8aUpa5hnqYYL8j\n" +
                    "i0mfARh64Nh8JOR1tVDoQ6FCQo7lj2pc2f2RcLau0et6tFCjGCyZsKPf6QKBgQCL\n" +
                    "za4EWX8W0BWpZfRTDVv2qfbZeVJZ/U5h/qE4rcg4fpM6HMdaW8JYWKPHyZpQJRTJ\n" +
                    "EpoKvZ2CtBreg+nrabvb3lpuhF9RMjyspXnVEjmgbCH058pMXMjP3xp2QIu3R14F\n" +
                    "Ue1UXRs1vw0vOLSd+OPgrC4iiT89dA/yzCbO7WBqRQKBgQDi0lCnXCi/lpL+A/Bc\n" +
                    "qSF2ei8f7MKDYDc4wm6VQXfbT+6JjHlgu6o2R/bg7TSE+qHn2hXuJ3tlB7BNoxBN\n" +
                    "LM2Iwnacj+A2RYQ1VyXjk+rh/4Zr4x0guufUqykGl3noAbJzaWUYn9ypFmvcA+UX\n" +
                    "XY3Uzfm/Rm5clPW8RCwjTix0iQ==\n" +
                    "-----END PRIVATE KEY-----";

    private static final String VALID_GROUPID = "group0";

    private static final String INVALID_GROUPID = "group5";

    private static FISCOBCOSBBCService fiscobcosBBCService;

    private static AppContract appContract;

    private static final String REMOTE_APP_CONTRACT = "0xdd60594eb43d5ab947d722a7275e28e41916edec";

    private static boolean setupBBC;

    // PTC 相关常量
    public static final String PTC_CERT = "-----BEGIN PROOF TRANSFORMATION COMPONENT CERTIFICATE-----\n" +
            "AAAiAgAAAAABAAAAMQEAQAAAADVhYmUyNThlYTgyNDAzY2FiOTIwZWYxMWU2NGQw\n" +
            "NGNlOTllYWZkMTE0NDg0ODhlZjQwZDZjNjk4MWE5MDQ5OGYCAAEAAAACAwBrAAAA\n" +
            "AABlAAAAAAABAAAAAAEAWAAAADBWMBAGByqGSM49AgEGBSuBBAAKA0IABKL5N/Ct\n" +
            "0AdTcdxz+4K0y4J+KWLCejoeWiF9xmA00lkWvEFISa803EoJ3595y32+hvLyezBw\n" +
            "L74ZIA+tRWhF+NwEAAgAAABuouNnAAAAAAUACAAAAO7VxGkAAAAABgCWAAAAAACQ\n" +
            "AAAAAAABAAAAMQEABQAAAG15cHRjAgABAAAAAQMAawAAAAAAZQAAAAAAAQAAAAAB\n" +
            "AFgAAAAwVjAQBgcqhkjOPQIBBgUrgQQACgNCAAQXfOXC4r4VGINdAWcW82Mo8/mJ\n" +
            "1EYbj1VIxU9k/I44BBZ1EvTOTsXs3MFOfKX6giPYZIQKB2jHY3ed6ybJBoqTBAAA\n" +
            "AAAABwCfAAAAAACZAAAAAAAKAAAAS0VDQ0FLLTI1NgEAIAAAADVZUya/PaepkU8L\n" +
            "OpiZLuhgAdfp1JT9m+5Z31/KrqWIAgAWAAAAS2VjY2FrMjU2V2l0aFNlY3AyNTZr\n" +
            "MQMAQQAAAJRTyowp7cXsanhc9qMVlzzZH9j8c/5ycHLvGeniTsmaJtLRYAh9GS++\n" +
            "syaxW3+8jmy9LfkY7R8MzU4DxLNfUGYA\n" +
            "-----END PROOF TRANSFORMATION COMPONENT CERTIFICATE-----\n";

    public static final AbstractCrossChainCertificate NODE_PTC_CERT = CrossChainCertificateUtil.readCrossChainCertificateFromPem(PTC_CERT.getBytes());

    public static final byte[] RAW_NODE_PTC_PUBLIC_KEY = PemUtil.readPem(new ByteArrayInputStream(
                    ("-----BEGIN PUBLIC KEY-----\n" +
                            "MFYwEAYHKoZIzj0CAQYFK4EEAAoDQgAEF3zlwuK+FRiDXQFnFvNjKPP5idRGG49V\n" +
                            "SMVPZPyOOAQWdRL0zk7F7NzBTnyl+oIj2GSECgdox2N3nesmyQaKkw==\n" +
                            "-----END PUBLIC KEY-----\n").getBytes()
            )
    );

    public static final PrivateKey NODE_PTC_PRIVATE_KEY = SignAlgoEnum.KECCAK256_WITH_SECP256K1.getSigner().readPemPrivateKey(
            ("-----BEGIN EC PRIVATE KEY-----\n" +
                    "MHQCAQEEIAlrtbHspE27NHVwGvrswcZdIIcwnmDRFrX7xOi0kbpnoAcGBSuBBAAK\n" +
                    "oUQDQgAEF3zlwuK+FRiDXQFnFvNjKPP5idRGG49VSMVPZPyOOAQWdRL0zk7F7NzB\n" +
                    "Tnyl+oIj2GSECgdox2N3nesmyQaKkw==\n" +
                    "-----END EC PRIVATE KEY-----\n").getBytes()
    );

    private static ThirdPartyBlockchainTrustAnchorV1 tpbta;

    private static CrossChainLane crossChainLane;

    private static ObjectIdentity oid;

    private static final String COMMITTEE_ID = "default";

    private static final String CHAIN_DOMAIN = "test.domain";

    private static final PTCTrustRoot ptcTrustRoot;

    public static final String BCDNS_CERT = "-----BEGIN BCDNS TRUST ROOT CERTIFICATE-----\n" +
            "AADdAQAAAAABAAAAMQEABwAAAG15YmNkbnMCAAEAAAAAAwBrAAAAAABlAAAAAAAB\n" +
            "AAAAAAEAWAAAADBWMBAGByqGSM49AgEGBSuBBAAKA0IABKL5N/Ct0AdTcdxz+4K0\n" +
            "y4J+KWLCejoeWiF9xmA00lkWvEFISa803EoJ3595y32+hvLyezBwL74ZIA+tRWhF\n" +
            "+NwEAAgAAAD2neNnAAAAAAUACAAAAHbRxGkAAAAABgCKAAAAAACEAAAAAAAHAAAA\n" +
            "bXliY2RucwEAawAAAAAAZQAAAAAAAQAAAAABAFgAAAAwVjAQBgcqhkjOPQIBBgUr\n" +
            "gQQACgNCAASi+TfwrdAHU3Hcc/uCtMuCfiliwno6HlohfcZgNNJZFrxBSEmvNNxK\n" +
            "Cd+fect9voby8nswcC++GSAPrUVoRfjcAgAAAAAABwCfAAAAAACZAAAAAAAKAAAA\n" +
            "S0VDQ0FLLTI1NgEAIAAAAEui59gKyqkdfwB8tquDWMGg57I+9Z5RAivUOJNuOK9W\n" +
            "AgAWAAAAS2VjY2FrMjU2V2l0aFNlY3AyNTZrMQMAQQAAADa+ZDAmA1E6gWaozxXE\n" +
            "Uhy5bhajXMqQr+7rYMsl7OFNcErzDQlvhWx8iwQj6/u5/uVZeJnRY5tYnNmOewiy\n" +
            "CDEB\n" +
            "-----END BCDNS TRUST ROOT CERTIFICATE-----\n";

    static {
        oid = new X509PubkeyInfoObjectIdentity(RAW_NODE_PTC_PUBLIC_KEY);

        OptionalEndorsePolicy policy = new OptionalEndorsePolicy();
        policy.setThreshold(new OptionalEndorsePolicy.Threshold(OptionalEndorsePolicy.OperatorEnum.GREATER_OR_EQUALS, 1));

        NodeEndorseInfo nodeEndorseInfo = new NodeEndorseInfo();
        nodeEndorseInfo.setNodeId("node1");
        nodeEndorseInfo.setRequired(true);
        NodePublicKeyEntry nodePubkeyEntry = new NodePublicKeyEntry("default", ((X509PubkeyInfoObjectIdentity) oid).getPublicKey());
        nodeEndorseInfo.setPublicKey(nodePubkeyEntry);

        NodeEndorseInfo nodeEndorseInfo2 = new NodeEndorseInfo();
        nodeEndorseInfo2.setNodeId("node2");
        nodeEndorseInfo2.setRequired(false);
        nodeEndorseInfo2.setPublicKey(nodePubkeyEntry);

        NodeEndorseInfo nodeEndorseInfo3 = new NodeEndorseInfo();
        nodeEndorseInfo3.setNodeId("node3");
        nodeEndorseInfo3.setRequired(false);
        nodeEndorseInfo3.setPublicKey(nodePubkeyEntry);

        NodeEndorseInfo nodeEndorseInfo4 = new NodeEndorseInfo();
        nodeEndorseInfo4.setNodeId("node4");
        nodeEndorseInfo4.setRequired(false);
        nodeEndorseInfo4.setPublicKey(nodePubkeyEntry);

        crossChainLane = new CrossChainLane(new CrossChainDomain("test"), new CrossChainDomain(CHAIN_DOMAIN));
        tpbta = new ThirdPartyBlockchainTrustAnchorV1(
                1,
                BigInteger.ONE,
                (PTCCredentialSubject) NODE_PTC_CERT.getCredentialSubjectInstance(),
                crossChainLane,
                1,
                HashAlgoEnum.KECCAK_256,
                new CommitteeEndorseRoot(
                        COMMITTEE_ID,
                        policy,
                        ListUtil.toList(nodeEndorseInfo, nodeEndorseInfo2, nodeEndorseInfo3, nodeEndorseInfo4)
                ).encode(),
                null
        );
        tpbta.setEndorseProof(
                CommitteeEndorseProof.builder()
                        .committeeId(COMMITTEE_ID)
                        .sigs(ListUtil.toList(
                                new CommitteeNodeProof(
                                        "node1",
                                        SignAlgoEnum.KECCAK256_WITH_SECP256K1,
                                        SignAlgoEnum.KECCAK256_WITH_SECP256K1.getSigner()
                                                .sign(NODE_PTC_PRIVATE_KEY, tpbta.getEncodedToSign())
                                ),
                                new CommitteeNodeProof(
                                        "node2",
                                        SignAlgoEnum.KECCAK256_WITH_SECP256K1,
                                        SignAlgoEnum.KECCAK256_WITH_SECP256K1.getSigner()
                                                .sign(NODE_PTC_PRIVATE_KEY, tpbta.getEncodedToSign())
                                ),
                                new CommitteeNodeProof(
                                        "node3",
                                        SignAlgoEnum.KECCAK256_WITH_SECP256K1,
                                        SignAlgoEnum.KECCAK256_WITH_SECP256K1.getSigner()
                                                .sign(NODE_PTC_PRIVATE_KEY, tpbta.getEncodedToSign())
                                )
                        )).build().encode()
        );
        Assert.assertEquals(ThirdPartyBlockchainTrustAnchor.TypeEnum.CHANNEL_LEVEL, tpbta.type());

        CommitteeVerifyAnchor verifyAnchor = new CommitteeVerifyAnchor("default");
        verifyAnchor.addNode("node1", "default", ((X509PubkeyInfoObjectIdentity) oid).getPublicKey());
        verifyAnchor.addNode("node2", "default", ((X509PubkeyInfoObjectIdentity) oid).getPublicKey());
        verifyAnchor.addNode("node3", "default", ((X509PubkeyInfoObjectIdentity) oid).getPublicKey());
        verifyAnchor.addNode("node4", "default", ((X509PubkeyInfoObjectIdentity) oid).getPublicKey());

        // prepare the network stuff
        CommitteeNetworkInfo committeeNetworkInfo = new CommitteeNetworkInfo("default");
        committeeNetworkInfo.addEndpoint("node1", "grpcs://0.0.0.0:8080", "");
        committeeNetworkInfo.addEndpoint("node2", "grpcs://0.0.0.0:8080", "");
        committeeNetworkInfo.addEndpoint("node3", "grpcs://0.0.0.0:8080", "");
        committeeNetworkInfo.addEndpoint("node4", "grpcs://0.0.0.0:8080", "");

        // build it first
        ptcTrustRoot = PTCTrustRoot.builder()
                .ptcCrossChainCert(NODE_PTC_CERT)
                .networkInfo(committeeNetworkInfo.encode())
                .issuerBcdnsDomainSpace(new CrossChainDomain(""))
                .sigAlgo(SignAlgoEnum.KECCAK256_WITH_SECP256K1)
                .verifyAnchorMap(MapUtil.builder(
                        BigInteger.ONE,
                        new PTCVerifyAnchor(
                                BigInteger.ONE,
                                verifyAnchor.encode()
                        )
                ).build())
                .build();

        // sign it with ptc private key which applied PTC certificate
        ptcTrustRoot.sign(NODE_PTC_PRIVATE_KEY);
    }

    @Before
    public void init() throws Exception {
        FISCOBCOSConfig config = new FISCOBCOSConfig();

        ConfigProperty configProperty = new ConfigProperty();

        // 实例化 cryptoMaterial
        Map<String, Object> cryptoMaterial = new HashMap<>();
        cryptoMaterial.put("useSMCrypto", config.getUseSMCrypto());
        cryptoMaterial.put("disableSsl", config.getDisableSsl());
        configProperty.cryptoMaterial = cryptoMaterial;

        // 实例化 network
        Map<String, Object> network = new HashMap<>();
        network.put("messageTimeout", config.getMessageTimeout());
        network.put("defaultGroup", config.getDefaultGroup());
        network.put("peers", new ArrayList<>(Collections.singletonList(config.getConnectPeer())));
        configProperty.network = network;

        // 实例化 account
        Map<String, Object> account = new HashMap<>();
        account.put("keyStoreDir", config.getKeyStoreDir());
        account.put("accountFileFormat", config.getAccountFileFormat());
        configProperty.account = account;

        // 实例化 threadPool
        Map<String, Object> threadPool = new HashMap<>();
        configProperty.threadPool = threadPool;

        // 实例化 amop
        List<AmopTopic> amop = new ArrayList<>();
        configProperty.amop = amop;

        ConfigOption configOption = new ConfigOption();

        CryptoMaterialConfig cryptoMaterialConfig = new CryptoMaterialConfig();
        cryptoMaterialConfig.setCaCert(CA_CERT);
        cryptoMaterialConfig.setSdkCert(SSL_CERT);
        cryptoMaterialConfig.setSdkPrivateKey(SSL_KEY);
        configOption.setCryptoMaterialConfig(cryptoMaterialConfig);

        configOption.setAccountConfig(new AccountConfig(configProperty));
        configOption.setAmopConfig(new AmopConfig(configProperty));
        configOption.setNetworkConfig(new NetworkConfig(configProperty));
        configOption.setThreadPoolConfig(new ThreadPoolConfig(configProperty));

        configOption.setJniConfig(configOption.generateJniConfig());
        configOption.setConfigProperty(configProperty);

        // Initialize BcosSDK
        BcosSDK sdk = new BcosSDK(configOption);
        // Initialize the client for the group
        Client client = sdk.getClient(VALID_GROUPID);

        appContract = AppContract.deploy(client, client.getCryptoSuite().getCryptoKeyPair());

        fiscobcosBBCService = new FISCOBCOSBBCService();
        // set logger
        Method method = AbstractBBCService.class.getDeclaredMethod("setLogger", Logger.class);
        method.setAccessible(true);
        method.invoke(fiscobcosBBCService, log);
    }

    @Test
    public void testStartup() {
        // start up success
        AbstractBBCContext mockValidCtx = mockValidCtx();
        fiscobcosBBCService.startup(mockValidCtx);
        Assert.assertEquals(null, fiscobcosBBCService.getBbcContext().getAuthMessageContract());
        Assert.assertEquals(null, fiscobcosBBCService.getBbcContext().getSdpContract());
        // start up failed
        AbstractBBCContext mockInvalidCtx = mockInvalidCtx();
        try {
            fiscobcosBBCService.startup(mockInvalidCtx);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testStartupWithDeployedContract() {
        // start up a tmp
        AbstractBBCContext mockValidCtx = mockValidCtx();
        FISCOBCOSBBCService fiscobcosBBCServiceTmp = new FISCOBCOSBBCService();
        fiscobcosBBCServiceTmp.startup(mockValidCtx);

        // set up am and sdp
        fiscobcosBBCServiceTmp.setupAuthMessageContract();
        fiscobcosBBCServiceTmp.setupSDPMessageContract();
        fiscobcosBBCServiceTmp.setupPTCContract();
        String amAddr = fiscobcosBBCServiceTmp.getContext().getAuthMessageContract().getContractAddress();
        String sdpAddr = fiscobcosBBCServiceTmp.getContext().getSdpContract().getContractAddress();
        String ptcAddr = fiscobcosBBCServiceTmp.getContext().getPtcContract().getContractAddress();

        // start up success
        AbstractBBCContext ctx = mockValidCtxWithPreDeployedContracts(amAddr, sdpAddr, ptcAddr);
        fiscobcosBBCService.startup(ctx);
        Assert.assertEquals(amAddr, fiscobcosBBCService.getBbcContext().getAuthMessageContract().getContractAddress());
        Assert.assertEquals(ContractStatusEnum.CONTRACT_DEPLOYED, fiscobcosBBCService.getBbcContext().getAuthMessageContract().getStatus());
        Assert.assertEquals(sdpAddr, fiscobcosBBCService.getBbcContext().getSdpContract().getContractAddress());
        Assert.assertEquals(ContractStatusEnum.CONTRACT_DEPLOYED, fiscobcosBBCService.getBbcContext().getSdpContract().getStatus());
        Assert.assertEquals(ptcAddr, fiscobcosBBCService.getBbcContext().getPtcContract().getContractAddress());
        Assert.assertEquals(ContractStatusEnum.CONTRACT_DEPLOYED, fiscobcosBBCService.getBbcContext().getPtcContract().getStatus());
    }

    @Test
    public void testStartupWithReadyContract() {
        // start up a tmp fiscobcosBBCService to set up contract
        AbstractBBCContext mockValidCtx = mockValidCtx();
        FISCOBCOSBBCService fiscobcosBBCServiceTmp = new FISCOBCOSBBCService();
        fiscobcosBBCServiceTmp.startup(mockValidCtx);

        // set up am and sdp
        fiscobcosBBCServiceTmp.setupAuthMessageContract();
        fiscobcosBBCServiceTmp.setupSDPMessageContract();
        fiscobcosBBCServiceTmp.setupPTCContract();
        String amAddr = fiscobcosBBCServiceTmp.getContext().getAuthMessageContract().getContractAddress();
        String sdpAddr = fiscobcosBBCServiceTmp.getContext().getSdpContract().getContractAddress();
        String ptcAddr = fiscobcosBBCServiceTmp.getContext().getPtcContract().getContractAddress();

        // start up success
        FISCOBCOSBBCService fiscobcosBBCService = new FISCOBCOSBBCService();
        AbstractBBCContext ctx = mockValidCtxWithPreReadyContracts(amAddr, sdpAddr, ptcAddr);
        fiscobcosBBCService.startup(ctx);
        Assert.assertEquals(amAddr, fiscobcosBBCService.getBbcContext().getAuthMessageContract().getContractAddress());
        Assert.assertEquals(ContractStatusEnum.CONTRACT_READY, fiscobcosBBCService.getBbcContext().getAuthMessageContract().getStatus());
        Assert.assertEquals(sdpAddr, fiscobcosBBCService.getBbcContext().getSdpContract().getContractAddress());
        Assert.assertEquals(ContractStatusEnum.CONTRACT_READY, fiscobcosBBCService.getBbcContext().getSdpContract().getStatus());
        Assert.assertEquals(ptcAddr, fiscobcosBBCService.getBbcContext().getPtcContract().getContractAddress());
        Assert.assertEquals(ContractStatusEnum.CONTRACT_READY, fiscobcosBBCService.getBbcContext().getPtcContract().getStatus());
    }


    @Test
    public void testShutdown() {
        AbstractBBCContext mockValidCtx = mockValidCtx();
        fiscobcosBBCService.startup(mockValidCtx);
        fiscobcosBBCService.shutdown();
    }

    @Test
    public void testGetContext() {
        AbstractBBCContext mockValidCtx = mockValidCtx();
        FISCOBCOSBBCService fiscobcosBBCService = new FISCOBCOSBBCService();
        fiscobcosBBCService.startup(mockValidCtx);
        AbstractBBCContext ctx = fiscobcosBBCService.getContext();
        Assert.assertNotNull(ctx);
        Assert.assertNull(ctx.getAuthMessageContract());
    }

    @Test
    public void testSetupAuthMessageContract() {
        // start up
        AbstractBBCContext mockValidCtx = mockValidCtx();
        fiscobcosBBCService.startup(mockValidCtx);

        // setup auth message contract
        fiscobcosBBCService.setupAuthMessageContract();

        // get context
        AbstractBBCContext ctx = fiscobcosBBCService.getContext();
        Assert.assertEquals(ContractStatusEnum.CONTRACT_DEPLOYED, ctx.getAuthMessageContract().getStatus());
    }

    @Test
    public void testSetupSDPMessageContract() {
        // start up
        AbstractBBCContext mockValidCtx = mockValidCtx();
        fiscobcosBBCService.startup(mockValidCtx);

        // setup sdp message contract
        fiscobcosBBCService.setupSDPMessageContract();

        // get context
        AbstractBBCContext ctx = fiscobcosBBCService.getContext();
        Assert.assertEquals(ContractStatusEnum.CONTRACT_DEPLOYED, ctx.getSdpContract().getStatus());
    }

    @Test
    public void testPtcContractAll() {
        // start up
        AbstractBBCContext mockValidCtx = mockValidCtx();
        fiscobcosBBCService.startup(mockValidCtx);

        // setup ptc contract
        fiscobcosBBCService.setupPTCContract();

        // verify contract status
        var ctx = fiscobcosBBCService.getContext();
        Assert.assertEquals(ContractStatusEnum.CONTRACT_READY, ctx.getPtcContract().getStatus());

        // test update and get PTC trust root
        fiscobcosBBCService.updatePTCTrustRoot(ptcTrustRoot);
        var root = fiscobcosBBCService.getPTCTrustRoot(oid);
        Assert.assertNotNull(root);
        Assert.assertArrayEquals(ptcTrustRoot.getNetworkInfo(), root.getNetworkInfo());
        Assert.assertTrue(fiscobcosBBCService.hasPTCTrustRoot(oid));

        // test get PTC verify anchor
        var resultPtcVa = fiscobcosBBCService.getPTCVerifyAnchor(oid, BigInteger.ONE);
        Assert.assertNotNull(resultPtcVa);
        Assert.assertArrayEquals(ptcTrustRoot.getVerifyAnchorMap().get(BigInteger.ONE).encode(), resultPtcVa.encode());
        Assert.assertTrue(fiscobcosBBCService.hasPTCVerifyAnchor(oid, BigInteger.ONE));

        // test add and get TP-BTA
        fiscobcosBBCService.addTpBta(tpbta);
        var resultTpBta = fiscobcosBBCService.getTpBta(tpbta.getCrossChainLane(), tpbta.getTpbtaVersion());
        Assert.assertNotNull(resultTpBta);
        Assert.assertArrayEquals(tpbta.encode(), resultTpBta.encode());
        Assert.assertTrue(fiscobcosBBCService.hasTpBta(tpbta.getCrossChainLane(), tpbta.getTpbtaVersion()));
    }

    @Test
    public void testQuerySDPMessageSeq() {
        setupBbc();

        // query seq
        long seq = fiscobcosBBCService.querySDPMessageSeq(
                "senderDomain",
                DigestUtil.sha256Hex("senderID".getBytes()),
                CHAIN_DOMAIN,
                DigestUtil.sha256Hex("receiverID".getBytes())
        );
        Assert.assertEquals(0L, seq);
    }

    @Test
    public void testSetProtocol() throws Exception {
        FISCOBCOSBBCService fiscobcosBBCService = new FISCOBCOSBBCService();
        // start up
        AbstractBBCContext mockValidCtx = mockValidCtx();
        fiscobcosBBCService.startup(mockValidCtx);

        // set up am
        fiscobcosBBCService.setupAuthMessageContract();

        // set up sdp
        fiscobcosBBCService.setupSDPMessageContract();

        // get context
        AbstractBBCContext ctx = fiscobcosBBCService.getContext();
        Assert.assertEquals(ContractStatusEnum.CONTRACT_DEPLOYED, ctx.getAuthMessageContract().getStatus());
        Assert.assertEquals(ContractStatusEnum.CONTRACT_DEPLOYED, ctx.getSdpContract().getStatus());

        // set protocol to am (sdp type: 0)
        fiscobcosBBCService.setProtocol(
                ctx.getSdpContract().getContractAddress(),
                "0");

        String addr = AuthMsg.load(
                fiscobcosBBCService.getBbcContext().getAuthMessageContract().getContractAddress(),
                fiscobcosBBCService.getClient(),
                fiscobcosBBCService.getKeyPair()
        ).getProtocol(BigInteger.ZERO);
        System.out.println("protocol: " + addr);

        fiscobcosBBCService.setPtcContract(addr);

        // check am status
        ctx = fiscobcosBBCService.getContext();
        Assert.assertEquals(ContractStatusEnum.CONTRACT_READY, ctx.getAuthMessageContract().getStatus());
    }

    @Test
    public void testSetAmContractAndLocalDomain() throws Exception {
        FISCOBCOSBBCService fiscobcosBBCService = new FISCOBCOSBBCService();
        // start up
        AbstractBBCContext mockValidCtx = mockValidCtx();
        fiscobcosBBCService.startup(mockValidCtx);

        // set up am
        fiscobcosBBCService.setupAuthMessageContract();

        // set up sdp
        fiscobcosBBCService.setupSDPMessageContract();

        // get context
        AbstractBBCContext ctx = fiscobcosBBCService.getContext();
        Assert.assertEquals(ContractStatusEnum.CONTRACT_DEPLOYED, ctx.getAuthMessageContract().getStatus());
        Assert.assertEquals(ContractStatusEnum.CONTRACT_DEPLOYED, ctx.getSdpContract().getStatus());

        // set am to sdp
        fiscobcosBBCService.setAmContract(ctx.getAuthMessageContract().getContractAddress());

        String amAddr = SDPMsg.load(
                fiscobcosBBCService.getBbcContext().getSdpContract().getContractAddress(),
                fiscobcosBBCService.getClient(),
                fiscobcosBBCService.getKeyPair()
        ).getAmAddress();
        System.out.println("amAddr: " + amAddr);

        // check contract status
        ctx = fiscobcosBBCService.getContext();
        Assert.assertEquals(ContractStatusEnum.CONTRACT_DEPLOYED, ctx.getSdpContract().getStatus());

        // set the domain
        fiscobcosBBCService.setLocalDomain(CHAIN_DOMAIN);

        byte[] rawDomain = SDPMsg.load(
                fiscobcosBBCService.getBbcContext().getSdpContract().getContractAddress(),
                fiscobcosBBCService.getClient(),
                fiscobcosBBCService.getKeyPair()
        ).getLocalDomain();
        System.out.println("domain: " + HexUtil.encodeHexStr(rawDomain));

        // check contract status
        ctx = fiscobcosBBCService.getContext();
        Assert.assertEquals(ContractStatusEnum.CONTRACT_READY, ctx.getSdpContract().getStatus());
    }

    @Test
    public void testRelayAuthMessage() {
        setupBbc();

        // prepare auth message
        byte[] rawMsg = getRawMsgFromRelayer(appContract.getContractAddress());

        // relay auth message
        CrossChainMessageReceipt receipt = fiscobcosBBCService.relayAuthMessage(rawMsg);
        System.out.println("Transaction hash: " + receipt.getTxhash());
        System.out.println("Transaction successful: " + receipt.isSuccessful());
        System.out.println("Transaction confirmed: " + receipt.isConfirmed());
        Assert.assertTrue(receipt.isSuccessful());

        // verify receipt
        TransactionReceipt transactionReceipt = fiscobcosBBCService.getClient().getTransactionReceipt(receipt.getTxhash(), true).getTransactionReceipt();
        System.out.println("Transaction hash: " + transactionReceipt.getTransactionHash());
        System.out.println("Transaction status: " + transactionReceipt.getStatus());
        Assert.assertNotNull(transactionReceipt);
        Assert.assertTrue(transactionReceipt.isStatusOK());
    }

    @Test
    public void testReadCrossChainMessageReceipt() {
        setupBbc();

        // prepare auth message
        byte[] rawMsg = getRawMsgFromRelayer(appContract.getContractAddress());

        // relay auth message
        CrossChainMessageReceipt crossChainMessageReceipt = fiscobcosBBCService.relayAuthMessage(rawMsg);
        System.out.println("crossChainMessageReceipt hash: " + crossChainMessageReceipt.getTxhash());
        System.out.println("crossChainMessageReceipt successful: " + crossChainMessageReceipt.isSuccessful());
        System.out.println("crossChainMessageReceipt confirmed: " + crossChainMessageReceipt.isConfirmed());

        // read receipt by txHash
        CrossChainMessageReceipt crossChainMessageReceipt1 = fiscobcosBBCService.readCrossChainMessageReceipt(crossChainMessageReceipt.getTxhash());
        System.out.println("crossChainMessageReceipt1 hash: " + crossChainMessageReceipt1.getTxhash());
        System.out.println("crossChainMessageReceipt1 successful: " + crossChainMessageReceipt1.isSuccessful());
        System.out.println("crossChainMessageReceipt1 confirmed: " + crossChainMessageReceipt1.isConfirmed());

        // verify receipt
        Assert.assertNotNull(crossChainMessageReceipt1);
        Assert.assertEquals(crossChainMessageReceipt.isSuccessful(), crossChainMessageReceipt1.isSuccessful());
    }

    @Test
    public void testReadCrossChainMessagesByHeight_sendUnordered() {
        setupBbc();

        // 1. Query latest height before sending message
        long height1 = fiscobcosBBCService.queryLatestHeight();

        // 2. Send unordered message through app contract
        TransactionReceipt receipt = appContract.sendUnorderedMessage(
                "remoteDomain",
                DigestUtil.sha256(REMOTE_APP_CONTRACT),
                "UnorderedCrossChainMessage".getBytes()
        );

        if (!receipt.isStatusOK()) {
            throw new RuntimeException("Failed to send unordered message");
        }

        String txHash = receipt.getTransactionHash();
        System.out.println("Transaction hash: " + txHash);

        // 3. Query latest height after sending message
        long height2 = fiscobcosBBCService.queryLatestHeight();

        // 4. Read cross chain messages between the two heights
        List<CrossChainMessage> messageList = new ArrayList<>();
        for (long i = height1; i <= height2; i++) {
            messageList.addAll(fiscobcosBBCService.readCrossChainMessagesByHeight(i));
        }
        for (CrossChainMessage message : messageList) {
            System.out.println("CrossChain MSG：" + HexUtil.encodeHexStr(message.encode()));
        }

        // 5. Verify results
        Assert.assertFalse(messageList.isEmpty());
        Assert.assertEquals(CrossChainMessage.CrossChainMessageType.AUTH_MSG, messageList.get(0).getType());

        // 6. Verify message content
        IAuthMessage authMsg = AuthMessageFactory.createAuthMessage(messageList.get(0).getMessage());
        ISDPMessage sdpMsg = SDPMessageFactory.createSDPMessage(authMsg.getPayload());
        Assert.assertEquals("UnorderedCrossChainMessage", new String(sdpMsg.getPayload()));
        Assert.assertEquals(-1, sdpMsg.getSequence()); // Unordered messages have sequence -1
    }

    @Test
    public void testReadCrossChainMessagesByHeight_sendOrdered() {
        setupBbc();

        // 1. Query latest height before sending message
        long height1 = fiscobcosBBCService.queryLatestHeight();

        // 2. Send ordered message through app contract
        TransactionReceipt receipt = appContract.sendMessage(
                "remoteDomain",
                DigestUtil.sha256(REMOTE_APP_CONTRACT),
                "OrderedCrossChainMessage".getBytes()
        );

        if (!receipt.isStatusOK()) {
            throw new RuntimeException("Failed to send ordered message");
        }

        String txHash = receipt.getTransactionHash();
        System.out.println("Transaction hash: " + txHash);

        // 3. Query latest height after sending message
        long height2 = fiscobcosBBCService.queryLatestHeight();

        // 4. Read cross chain messages between the two heights
        List<CrossChainMessage> messageList = new ArrayList<>();
        for (long i = height1; i <= height2; i++) {
            messageList.addAll(fiscobcosBBCService.readCrossChainMessagesByHeight(i));
        }

        // 5. Verify results
        Assert.assertFalse(messageList.isEmpty());
        Assert.assertEquals(CrossChainMessage.CrossChainMessageType.AUTH_MSG, messageList.get(0).getType());

        // 6. Verify message content
        IAuthMessage authMsg = AuthMessageFactory.createAuthMessage(messageList.get(0).getMessage());
        ISDPMessage sdpMsg = SDPMessageFactory.createSDPMessage(authMsg.getPayload());
        Assert.assertEquals("OrderedCrossChainMessage", new String(sdpMsg.getPayload()));
        Assert.assertNotEquals(-1, sdpMsg.getSequence()); // Ordered messages have sequence >= 0
    }

    @Test
    public void testReadConsensusState() {
        setupBbc();

        // 1. Send a message to create a block
        TransactionReceipt receipt = appContract.sendMessage(
                "remoteDomain",
                DigestUtil.sha256(REMOTE_APP_CONTRACT),
                "MessageForConsensusTest".getBytes()
        );

        if (!receipt.isStatusOK()) {
            throw new RuntimeException("Failed to send message for consensus test");
        }

        String txHash = receipt.getTransactionHash();
        System.out.println("Transaction hash: " + txHash);

        // 2. Get the block height
        BigInteger blockNumber = receipt.getBlockNumber();

        // 3. Query consensus state for this block
        ConsensusState consensusState = fiscobcosBBCService.readConsensusState(blockNumber);

        // 4. Verify consensus state
        Assert.assertNotNull(consensusState);
        Assert.assertEquals(blockNumber.longValue(), consensusState.getHeight().longValue());
        Assert.assertNotNull(consensusState.getHash());
        Assert.assertNotNull(consensusState.getParentHash());
        Assert.assertTrue(consensusState.getStateTimestamp() > 0);

        // 5. Verify state data contains the expected fields
        String stateDataStr = new String(consensusState.getStateData());
        Assert.assertTrue(stateDataStr.contains("transactionsRoot"));
        Assert.assertTrue(stateDataStr.contains("receiptsRoot"));
        Assert.assertTrue(stateDataStr.contains("stateRoot"));
        System.out.println("stateData: " + stateDataStr);

        // 6. Verify validator set (consensus node info)
        Assert.assertNotNull(consensusState.getConsensusNodeInfo());
        System.out.println("consensusNodeInfo: " + consensusState.getConsensusNodeInfo());

        // 7. Verify endorsements
        Assert.assertNotNull(consensusState.getEndorsements());
        System.out.println("endorsements: " + consensusState.getEndorsements());
    }

    @Test
    public void testConsensusStateWithQueryLatestHeight() {
        setupBbc();

        // 获取锚定区块
        ConsensusState anchor_cs = fiscobcosBBCService.readConsensusState(BigInteger.valueOf(1));
        System.out.println("anchor_cs: " + HexUtil.encodeHexStr(anchor_cs.encode()));

        // 获取656的父区块
        ConsensusState parent_cs = fiscobcosBBCService.readConsensusState(BigInteger.valueOf(655));
        System.out.println("parent_cs: " + HexUtil.encodeHexStr(parent_cs.encode()));

        // 获取656号区块
        ConsensusState cs = fiscobcosBBCService.readConsensusState(BigInteger.valueOf(656));
        System.out.println("cs: " + HexUtil.encodeHexStr(cs.encode()));

        // 获取656中的跨链消息
        List<CrossChainMessage> messageList = new ArrayList<>();
        messageList.addAll(fiscobcosBBCService.readCrossChainMessagesByHeight(656));
        for (CrossChainMessage message : messageList) {
            System.out.println("CrossChain MSG：" + HexUtil.encodeHexStr(message.encode()));
        }
    }


    @SneakyThrows
    private void setupBbc() {
        if (setupBBC) {
            return;
        }
        // start up
        AbstractBBCContext mockValidCtx = mockValidCtx();
        fiscobcosBBCService.startup(mockValidCtx);

        // setup am
        fiscobcosBBCService.setupAuthMessageContract();

        // setup sdp
        fiscobcosBBCService.setupSDPMessageContract();

        // setup ptc
        fiscobcosBBCService.setupPTCContract();

        System.out.println("AM address: " + fiscobcosBBCService.getBbcContext().getAuthMessageContract().getContractAddress());
        System.out.println("SDP address: " + fiscobcosBBCService.getBbcContext().getSdpContract().getContractAddress());
        System.out.println("PTC address: " + fiscobcosBBCService.getBbcContext().getPtcContract().getContractAddress());

        // set protocol to am (sdp type: 0)
        fiscobcosBBCService.setProtocol(mockValidCtx.getSdpContract().getContractAddress(), "0");

        // set am to sdp
        fiscobcosBBCService.setAmContract(mockValidCtx.getAuthMessageContract().getContractAddress());

        // set local domain to sdp
        fiscobcosBBCService.setLocalDomain(CHAIN_DOMAIN);

        fiscobcosBBCService.setPtcContract(mockValidCtx.getPtcContract().getContractAddress());

        fiscobcosBBCService.updatePTCTrustRoot(ptcTrustRoot);

        fiscobcosBBCService.addTpBta(tpbta);

        // check contract ready
        AbstractBBCContext ctxCheck = fiscobcosBBCService.getContext();
        Assert.assertEquals(ContractStatusEnum.CONTRACT_READY, ctxCheck.getAuthMessageContract().getStatus());
        Assert.assertEquals(ContractStatusEnum.CONTRACT_READY, ctxCheck.getSdpContract().getStatus());
        Assert.assertEquals(ContractStatusEnum.CONTRACT_READY, ctxCheck.getPtcContract().getStatus());


        TransactionReceipt receipt = appContract.setProtocol(fiscobcosBBCService.getBbcContext().getSdpContract().getContractAddress());
        if (receipt.isStatusOK()) {
            log.info("set protocol({}) to app contract({})",
                    appContract.getContractAddress(),
                    fiscobcosBBCService.getBbcContext().getSdpContract().getContractAddress());
        } else {
            throw new Exception(String.format("failed to set protocol(%s) to app contract(%s)",
                    appContract.getContractAddress(),
                    fiscobcosBBCService.getBbcContext().getSdpContract().getContractAddress()));
        }
        System.out.println("App Contract address: " + appContract.getContractAddress());

        setupBBC = true;
    }

    private AbstractBBCContext mockValidCtx() {
        FISCOBCOSConfig mockConf = new FISCOBCOSConfig();
        mockConf.setCaCert(CA_CERT);
        mockConf.setSslCert(SSL_CERT);
        mockConf.setSslKey(SSL_KEY);
        mockConf.setConnectPeer("127.0.0.1:20200");
        mockConf.setGroupID(VALID_GROUPID);
        mockConf.setBcdnsRootCertPem(BCDNS_CERT);
        System.out.println("mockConf JSON: " + mockConf.toJsonString());
        AbstractBBCContext mockCtx = new DefaultBBCContext();
        mockCtx.setConfForBlockchainClient(mockConf.toJsonString().getBytes());
        return mockCtx;
    }

    private AbstractBBCContext mockValidCtxWithPreDeployedContracts(String amAddr, String sdpAddr, String ptcAddr) {
        FISCOBCOSConfig mockConf = new FISCOBCOSConfig();
        mockConf.setCaCert(CA_CERT);
        mockConf.setSslCert(SSL_CERT);
        mockConf.setSslKey(SSL_KEY);
        mockConf.setConnectPeer("127.0.0.1:20200");
        mockConf.setGroupID(VALID_GROUPID);
        AbstractBBCContext mockCtx = new DefaultBBCContext();
        mockCtx.setConfForBlockchainClient(mockConf.toJsonString().getBytes());

        AuthMessageContract authMessageContract = new AuthMessageContract();
        authMessageContract.setContractAddress(amAddr);
        authMessageContract.setStatus(ContractStatusEnum.CONTRACT_DEPLOYED);
        mockCtx.setAuthMessageContract(authMessageContract);

        SDPContract sdpContract = new SDPContract();
        sdpContract.setContractAddress(sdpAddr);
        sdpContract.setStatus(ContractStatusEnum.CONTRACT_DEPLOYED);
        mockCtx.setSdpContract(sdpContract);

        PTCContract ptcContract = new PTCContract();
        ptcContract.setContractAddress(ptcAddr);
        ptcContract.setStatus(ContractStatusEnum.CONTRACT_DEPLOYED);
        mockCtx.setPtcContract(ptcContract);

        return mockCtx;
    }

    private AbstractBBCContext mockValidCtxWithPreReadyContracts(String amAddr, String sdpAddr, String ptcAddr) {
        FISCOBCOSConfig mockConf = new FISCOBCOSConfig();
        mockConf.setCaCert(CA_CERT);
        mockConf.setSslCert(SSL_CERT);
        mockConf.setSslKey(SSL_KEY);
        mockConf.setConnectPeer("127.0.0.1:20200");
        mockConf.setGroupID(VALID_GROUPID);
        mockConf.setBcdnsRootCertPem(BCDNS_CERT);
        AbstractBBCContext mockCtx = new DefaultBBCContext();
        mockCtx.setConfForBlockchainClient(mockConf.toJsonString().getBytes());

        AuthMessageContract authMessageContract = new AuthMessageContract();
        authMessageContract.setContractAddress(amAddr);
        authMessageContract.setStatus(ContractStatusEnum.CONTRACT_READY);
        mockCtx.setAuthMessageContract(authMessageContract);

        SDPContract sdpContract = new SDPContract();
        sdpContract.setContractAddress(sdpAddr);
        sdpContract.setStatus(ContractStatusEnum.CONTRACT_READY);
        mockCtx.setSdpContract(sdpContract);

        PTCContract ptcContract = new PTCContract();
        ptcContract.setContractAddress(ptcAddr);
        ptcContract.setStatus(ContractStatusEnum.CONTRACT_READY);
        mockCtx.setPtcContract(ptcContract);

        return mockCtx;
    }

    private AbstractBBCContext mockInvalidCtx() {
        FISCOBCOSConfig mockConf = new FISCOBCOSConfig();
        mockConf.setCaCert(CA_CERT);
        mockConf.setSslCert(SSL_CERT);
        mockConf.setSslKey(SSL_KEY);
        mockConf.setConnectPeer("127.0.0.1:20200");
        mockConf.setGroupID(INVALID_GROUPID);
        System.out.println("mockConf JSON: " + mockConf.toJsonString());
        AbstractBBCContext mockCtx = new DefaultBBCContext();
        mockCtx.setConfForBlockchainClient(mockConf.toJsonString().getBytes());
        return mockCtx;
    }

    @SneakyThrows
    private byte[] getRawMsgFromRelayer(String receiverAddr) {
        ISDPMessage sdpMessage = SDPMessageFactory.createSDPMessage(
                1,
                new byte[32],
                crossChainLane.getReceiverDomain().getDomain(),
                HexUtil.decodeHex(receiverAddr.replace("0x", "000000000000000000000000")),
                -1,
                "awesome antchain-bridge".getBytes()
        );
        IAuthMessage am = AuthMessageFactory.createAuthMessage(
                1,
                RandomUtil.randomBytes(32),
                0,
                sdpMessage.encode()
        );

        ThirdPartyProof thirdPartyProof = ThirdPartyProof.create(
                tpbta.getVersion(),
                am.encode(),
                tpbta.getCrossChainLane()
        );

        CommitteeNodeProof node1Proof = CommitteeNodeProof.builder()
                .nodeId("node1")
                .signAlgo(SignAlgoEnum.KECCAK256_WITH_SECP256K1)
                .signature(SignAlgoEnum.KECCAK256_WITH_SECP256K1.getSigner().sign(
                        NODE_PTC_PRIVATE_KEY,
                        thirdPartyProof.getEncodedToSign()
                )).build();

        CommitteeEndorseProof endorseProof = new CommitteeEndorseProof();
        endorseProof.setCommitteeId(COMMITTEE_ID);
        endorseProof.setSigs(ListUtil.toList(node1Proof));

        thirdPartyProof.setRawProof(endorseProof.encode());

        byte[] rawProof = thirdPartyProof.encode();

        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        stream.write(new byte[]{0, 0, 0, 0});

        int len = rawProof.length;
        stream.write((len >>> 24) & 0xFF);
        stream.write((len >>> 16) & 0xFF);
        stream.write((len >>> 8) & 0xFF);
        stream.write((len) & 0xFF);

        stream.write(rawProof);

        return stream.toByteArray();
    }

    /**
     * Get the sdp message payload from the raw bytes
     * which is the input for {@link com.alipay.antchain.bridge.plugins.spi.bbc.IBBCService#relayAuthMessage(byte[])}
     *
     * @param raw the input for {@link com.alipay.antchain.bridge.plugins.spi.bbc.IBBCService#relayAuthMessage(byte[])}
     * @return {@code byte[]} sdp payload
     */
    private static byte[] getSDPPayloadFromRawMsg(byte[] raw) {
        ByteArrayInputStream stream = new ByteArrayInputStream(raw);

        byte[] zeros = new byte[4];
        stream.read(zeros, 0, 4);

        byte[] rawLen = new byte[4];
        stream.read(rawLen, 0, 4);

        int len = ByteUtil.bytesToInt(rawLen, ByteOrder.BIG_ENDIAN);

        byte[] rawProof = new byte[len];
        stream.read(rawProof, 0, len);

        MockProof proof = TLVUtils.decode(rawProof, MockProof.class);
        IAuthMessage authMessage = AuthMessageFactory.createAuthMessage(proof.getResp().getRawResponse());
        ISDPMessage sdpMessage = SDPMessageFactory.createSDPMessage(authMessage.getPayload());

        return sdpMessage.getPayload();
    }

    @Getter
    @Setter
    public static class MockProof {

        @TLVField(tag = 5, type = TLVTypeEnum.BYTES)
        private MockResp resp;

        @TLVField(tag = 9, type = TLVTypeEnum.STRING)
        private String domain;
    }

    @Getter
    @Setter
    public static class MockResp {

        @TLVField(tag = 0, type = TLVTypeEnum.BYTES)
        private byte[] rawResponse;
    }
}

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

package com.alipay.antchain.bridge.ptc.committee;

import java.io.ByteArrayInputStream;
import java.lang.reflect.Method;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.security.PrivateKey;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.ListUtil;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.map.MapUtil;
import cn.hutool.core.net.NetUtil;
import cn.hutool.core.util.HexUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.crypto.PemUtil;
import com.alipay.antchain.bridge.commons.bcdns.AbstractCrossChainCertificate;
import com.alipay.antchain.bridge.commons.bcdns.PTCCredentialSubject;
import com.alipay.antchain.bridge.commons.bcdns.utils.CrossChainCertificateUtil;
import com.alipay.antchain.bridge.commons.core.am.AuthMessageFactory;
import com.alipay.antchain.bridge.commons.core.am.IAuthMessage;
import com.alipay.antchain.bridge.commons.core.base.*;
import com.alipay.antchain.bridge.commons.core.bta.BlockchainTrustAnchorV1;
import com.alipay.antchain.bridge.commons.core.ptc.*;
import com.alipay.antchain.bridge.commons.core.sdp.ISDPMessage;
import com.alipay.antchain.bridge.commons.core.sdp.SDPMessageFactory;
import com.alipay.antchain.bridge.commons.utils.crypto.HashAlgoEnum;
import com.alipay.antchain.bridge.commons.utils.crypto.SignAlgoEnum;
import com.alipay.antchain.bridge.ptc.committee.grpc.*;
import com.alipay.antchain.bridge.ptc.committee.types.basic.CommitteeEndorseProof;
import com.alipay.antchain.bridge.ptc.committee.types.basic.CommitteeNodeProof;
import com.alipay.antchain.bridge.ptc.committee.types.basic.EndorseBlockStateResp;
import com.alipay.antchain.bridge.ptc.committee.types.basic.NodePublicKeyEntry;
import com.alipay.antchain.bridge.ptc.committee.types.network.CommitteeNetworkInfo;
import com.alipay.antchain.bridge.ptc.committee.types.network.nodeclient.Node;
import com.alipay.antchain.bridge.ptc.committee.types.network.nodeclient.NodeVerifyCrossChainMessageResult;
import com.alipay.antchain.bridge.ptc.committee.types.tpbta.CommitteeEndorseRoot;
import com.alipay.antchain.bridge.ptc.committee.types.tpbta.NodeEndorseInfo;
import com.alipay.antchain.bridge.ptc.committee.types.tpbta.OptionalEndorsePolicy;
import com.alipay.antchain.bridge.ptc.committee.types.tpbta.VerifyBtaExtension;
import com.alipay.antchain.bridge.ptc.committee.types.trustroot.CommitteeVerifyAnchor;
import com.alipay.antchain.bridge.ptc.types.PTCVerifyCrossChainMessageResult;
import com.google.protobuf.ByteString;
import lombok.SneakyThrows;
import org.junit.*;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class CommitteePTCServiceTest {

    public static final String BCDNS_CERT = "-----BEGIN BCDNS TRUST ROOT CERTIFICATE-----\n" +
            "AADWAQAAAAABAAAAMQEABAAAAHRlc3QCAAEAAAAAAwBrAAAAAABlAAAAAAABAAAA\n" +
            "AAEAWAAAADBWMBAGByqGSM49AgEGBSuBBAAKA0IABFC80gzzOJoUZBqQolYqx2U1\n" +
            "n87mfz3zuvv0X1YBqWcbOBFEGYcOUp2FiMCvfSsQzzcbWBuzhIlgwO/hCmVFgSME\n" +
            "AAgAAACU2f9mAAAAAAUACAAAABQN4WgAAAAABgCGAAAAAACAAAAAAAADAAAAYmlm\n" +
            "AQBrAAAAAABlAAAAAAABAAAAAAEAWAAAADBWMBAGByqGSM49AgEGBSuBBAAKA0IA\n" +
            "BFC80gzzOJoUZBqQolYqx2U1n87mfz3zuvv0X1YBqWcbOBFEGYcOUp2FiMCvfSsQ\n" +
            "zzcbWBuzhIlgwO/hCmVFgSMCAAAAAAAHAJ8AAAAAAJkAAAAAAAoAAABLRUNDQUst\n" +
            "MjU2AQAgAAAA1/SncCIPlAQGRJ4Zp2WPBmrk5poje12brhJatwWR5BwCABYAAABL\n" +
            "ZWNjYWsyNTZXaXRoU2VjcDI1NmsxAwBBAAAAR23ngOzN3b8gaJY9ikvNtdqzwF6K\n" +
            "zAkr89qnHDJQei9iXVds+7Padq41StiQShIiB9yWtx8/3Qu878R9zmJbZAA=\n" +
            "-----END BCDNS TRUST ROOT CERTIFICATE-----\n";

    public static final String PTC_CERT = "-----BEGIN PROOF TRANSFORMATION COMPONENT CERTIFICATE-----\n" +
            "AAD4AQAAAAABAAAAMQEADAAAAGFudGNoYWluLXB0YwIAAQAAAAIDAGsAAAAAAGUA\n" +
            "AAAAAAEAAAAAAQBYAAAAMFYwEAYHKoZIzj0CAQYFK4EEAAoDQgAEULzSDPM4mhRk\n" +
            "GpCiVirHZTWfzuZ/PfO6+/RfVgGpZxs4EUQZhw5SnYWIwK99KxDPNxtYG7OEiWDA\n" +
            "7+EKZUWBIwQACAAAAJTZ/2YAAAAABQAIAAAAFA3haAAAAAAGAKAAAAAAAJoAAAAA\n" +
            "AAMAAAAxLjABAA0AAABjb21taXR0ZWUtcHRjAgABAAAAAQMAawAAAAAAZQAAAAAA\n" +
            "AQAAAAABAFgAAAAwVjAQBgcqhkjOPQIBBgUrgQQACgNCAARQvNIM8ziaFGQakKJW\n" +
            "KsdlNZ/O5n8987r79F9WAalnGzgRRBmHDlKdhYjAr30rEM83G1gbs4SJYMDv4Qpl\n" +
            "RYEjBAAAAAAABwCfAAAAAACZAAAAAAAKAAAAS0VDQ0FLLTI1NgEAIAAAAM87/iLc\n" +
            "e6uD6qD6prxj4z75IoGzydOhd68+3Y8dODHxAgAWAAAAS2VjY2FrMjU2V2l0aFNl\n" +
            "Y3AyNTZrMQMAQQAAAMK+DN7gXmDRv8nfXwWZe3XCZQQu5mO86LNZxXcp7BgMPfJj\n" +
            "y1wKW5yD51nhMEW2K1AfwEG6n8RWk5Z2jFDE8GMA\n" +
            "-----END PROOF TRANSFORMATION COMPONENT CERTIFICATE-----\n";

    public static final AbstractCrossChainCertificate NODE_PTC_CERT = CrossChainCertificateUtil.readCrossChainCertificateFromPem(PTC_CERT.getBytes());

    public static final String NODE_SERVER_TLS_CERT = "-----BEGIN CERTIFICATE-----\n" +
            "MIIDSjCCAjICCQC7UpnZnD+2AjANBgkqhkiG9w0BAQsFADBmMQswCQYDVQQGEwJD\n" +
            "TjEOMAwGA1UECAwFbXlrZXkxDjAMBgNVBAcMBW15a2V5MQ4wDAYDVQQKDAVteWtl\n" +
            "eTEOMAwGA1UECwwFbXlrZXkxFzAVBgNVBAMMDkNPTU1JVFRFRS1OT0RFMCAXDTI0\n" +
            "MDgyNzExMzgxMloYDzIxMjQwODAzMTEzODEyWjBmMQswCQYDVQQGEwJDTjEOMAwG\n" +
            "A1UECAwFbXlrZXkxDjAMBgNVBAcMBW15a2V5MQ4wDAYDVQQKDAVteWtleTEOMAwG\n" +
            "A1UECwwFbXlrZXkxFzAVBgNVBAMMDkNPTU1JVFRFRS1OT0RFMIIBIjANBgkqhkiG\n" +
            "9w0BAQEFAAOCAQ8AMIIBCgKCAQEAyBDWvnDIBfCg0Bpb1Zzyu1/TI9W/eVO9S/n9\n" +
            "TyfVoWHOd4vLuHs3EmP6QtPWBtS/wSx579Fm+qhg+Px5TdrPIPceK3CgFlWqesz1\n" +
            "+qrPCfuHJ0yRpQb1yQ77l23JPTb4ze7zmeG2mCBImpCl2TFnUgNDrK8VsqALZ5fE\n" +
            "O3eFkwn8zpKaqEmRMOY8PJJh76/PNYYu5XobS6Sie08gb7Vr5BKCC1aAvM6yeapC\n" +
            "ZRZ1QoAgbgOz5wQgOVmni5zn9UWeSYe2xeI6geqKLVc/1Jr5zA3nLItWF+KveUix\n" +
            "V3oKO43nfO3e+fPli9jJkFM0HZD5FhMxQIpPjuF7aecUZG0GwwIDAQABMA0GCSqG\n" +
            "SIb3DQEBCwUAA4IBAQCXF83k4Tdlfcc456agxHUDW4kCgsQVZ0mcfJLVL0+UBMLj\n" +
            "VFz50Su2Vtq8Pe/N4T2BHB1zZgQzs+T7oRtEb65H2uTxO8AFtang9OgpFJYxEkgH\n" +
            "9oRgAeD32xIwob5cj2znf3Ct03FKKkrpaXvGuPlSYV/qfy1Gittb3UFi2y7+LDk2\n" +
            "q6Qd7jpMYK6HdtVwj0FgyxRIzzGQT6/d9soyI8a8bW/VKsJ/+DEANIZFSw9Y80Ye\n" +
            "EVQJHi2LfmEXaxvD9m1vO50Dmspc5dy4J6QKY6HgzXcdxZbjetwaJ9ilG7LgLbh8\n" +
            "XOL/r9jTK9//UMFPq27xZ5PMrsGR+PjV/9NasLac\n" +
            "-----END CERTIFICATE-----\n";

    public static CommitteeNodeServiceGrpc.CommitteeNodeServiceBlockingStub mockStubNode1;
    public static CommitteeNodeServiceGrpc.CommitteeNodeServiceBlockingStub mockStubNode2;
    public static CommitteeNodeServiceGrpc.CommitteeNodeServiceBlockingStub mockStubNode3;
    public static CommitteeNodeServiceGrpc.CommitteeNodeServiceBlockingStub mockStubNode4;

    public static MockedStatic<CommitteeNodeServiceGrpc> mockStaticCommitteeNodeServiceBlockingStub;

    public static MockedStatic<NetUtil> mockStaticNetUtil;

    public static final String SERVICE_CONF = "{\n" +
            "  \"network\": {\n" +
            "    \"committee_id\": \"committee\",\n" +
            "    \"nodes\": [\n" +
            "      {\n" +
            "        \"endpoint\": \"grpcs://127.0.0.1:10080\",\n" +
            "        \"node_id\": \"node1\",\n" +
            "        \"tls_cert\": \"-----BEGIN CERTIFICATE-----\\nMIIDSjCCAjICCQC7UpnZnD+2AjANBgkqhkiG9w0BAQsFADBmMQswCQYDVQQGEwJD\\nTjEOMAwGA1UECAwFbXlrZXkxDjAMBgNVBAcMBW15a2V5MQ4wDAYDVQQKDAVteWtl\\neTEOMAwGA1UECwwFbXlrZXkxFzAVBgNVBAMMDkNPTU1JVFRFRS1OT0RFMCAXDTI0\\nMDgyNzExMzgxMloYDzIxMjQwODAzMTEzODEyWjBmMQswCQYDVQQGEwJDTjEOMAwG\\nA1UECAwFbXlrZXkxDjAMBgNVBAcMBW15a2V5MQ4wDAYDVQQKDAVteWtleTEOMAwG\\nA1UECwwFbXlrZXkxFzAVBgNVBAMMDkNPTU1JVFRFRS1OT0RFMIIBIjANBgkqhkiG\\n9w0BAQEFAAOCAQ8AMIIBCgKCAQEAyBDWvnDIBfCg0Bpb1Zzyu1/TI9W/eVO9S/n9\\nTyfVoWHOd4vLuHs3EmP6QtPWBtS/wSx579Fm+qhg+Px5TdrPIPceK3CgFlWqesz1\\n+qrPCfuHJ0yRpQb1yQ77l23JPTb4ze7zmeG2mCBImpCl2TFnUgNDrK8VsqALZ5fE\\nO3eFkwn8zpKaqEmRMOY8PJJh76/PNYYu5XobS6Sie08gb7Vr5BKCC1aAvM6yeapC\\nZRZ1QoAgbgOz5wQgOVmni5zn9UWeSYe2xeI6geqKLVc/1Jr5zA3nLItWF+KveUix\\nV3oKO43nfO3e+fPli9jJkFM0HZD5FhMxQIpPjuF7aecUZG0GwwIDAQABMA0GCSqG\\nSIb3DQEBCwUAA4IBAQCXF83k4Tdlfcc456agxHUDW4kCgsQVZ0mcfJLVL0+UBMLj\\nVFz50Su2Vtq8Pe/N4T2BHB1zZgQzs+T7oRtEb65H2uTxO8AFtang9OgpFJYxEkgH\\n9oRgAeD32xIwob5cj2znf3Ct03FKKkrpaXvGuPlSYV/qfy1Gittb3UFi2y7+LDk2\\nq6Qd7jpMYK6HdtVwj0FgyxRIzzGQT6/d9soyI8a8bW/VKsJ/+DEANIZFSw9Y80Ye\\nEVQJHi2LfmEXaxvD9m1vO50Dmspc5dy4J6QKY6HgzXcdxZbjetwaJ9ilG7LgLbh8\\nXOL/r9jTK9//UMFPq27xZ5PMrsGR+PjV/9NasLac\\n-----END CERTIFICATE-----\\n\"\n" +
            "      },\n" +
            "      {\n" +
            "        \"endpoint\": \"grpcs://127.0.0.1:10081\",\n" +
            "        \"node_id\": \"node2\",\n" +
            "        \"tls_cert\": \"-----BEGIN CERTIFICATE-----\\nMIIDSjCCAjICCQC7UpnZnD+2AjANBgkqhkiG9w0BAQsFADBmMQswCQYDVQQGEwJD\\nTjEOMAwGA1UECAwFbXlrZXkxDjAMBgNVBAcMBW15a2V5MQ4wDAYDVQQKDAVteWtl\\neTEOMAwGA1UECwwFbXlrZXkxFzAVBgNVBAMMDkNPTU1JVFRFRS1OT0RFMCAXDTI0\\nMDgyNzExMzgxMloYDzIxMjQwODAzMTEzODEyWjBmMQswCQYDVQQGEwJDTjEOMAwG\\nA1UECAwFbXlrZXkxDjAMBgNVBAcMBW15a2V5MQ4wDAYDVQQKDAVteWtleTEOMAwG\\nA1UECwwFbXlrZXkxFzAVBgNVBAMMDkNPTU1JVFRFRS1OT0RFMIIBIjANBgkqhkiG\\n9w0BAQEFAAOCAQ8AMIIBCgKCAQEAyBDWvnDIBfCg0Bpb1Zzyu1/TI9W/eVO9S/n9\\nTyfVoWHOd4vLuHs3EmP6QtPWBtS/wSx579Fm+qhg+Px5TdrPIPceK3CgFlWqesz1\\n+qrPCfuHJ0yRpQb1yQ77l23JPTb4ze7zmeG2mCBImpCl2TFnUgNDrK8VsqALZ5fE\\nO3eFkwn8zpKaqEmRMOY8PJJh76/PNYYu5XobS6Sie08gb7Vr5BKCC1aAvM6yeapC\\nZRZ1QoAgbgOz5wQgOVmni5zn9UWeSYe2xeI6geqKLVc/1Jr5zA3nLItWF+KveUix\\nV3oKO43nfO3e+fPli9jJkFM0HZD5FhMxQIpPjuF7aecUZG0GwwIDAQABMA0GCSqG\\nSIb3DQEBCwUAA4IBAQCXF83k4Tdlfcc456agxHUDW4kCgsQVZ0mcfJLVL0+UBMLj\\nVFz50Su2Vtq8Pe/N4T2BHB1zZgQzs+T7oRtEb65H2uTxO8AFtang9OgpFJYxEkgH\\n9oRgAeD32xIwob5cj2znf3Ct03FKKkrpaXvGuPlSYV/qfy1Gittb3UFi2y7+LDk2\\nq6Qd7jpMYK6HdtVwj0FgyxRIzzGQT6/d9soyI8a8bW/VKsJ/+DEANIZFSw9Y80Ye\\nEVQJHi2LfmEXaxvD9m1vO50Dmspc5dy4J6QKY6HgzXcdxZbjetwaJ9ilG7LgLbh8\\nXOL/r9jTK9//UMFPq27xZ5PMrsGR+PjV/9NasLac\\n-----END CERTIFICATE-----\\n\"\n" +
            "      },\n" +
            "      {\n" +
            "        \"endpoint\": \"grpcs://127.0.0.1:10082\",\n" +
            "        \"node_id\": \"node3\",\n" +
            "        \"tls_cert\": \"-----BEGIN CERTIFICATE-----\\nMIIDSjCCAjICCQC7UpnZnD+2AjANBgkqhkiG9w0BAQsFADBmMQswCQYDVQQGEwJD\\nTjEOMAwGA1UECAwFbXlrZXkxDjAMBgNVBAcMBW15a2V5MQ4wDAYDVQQKDAVteWtl\\neTEOMAwGA1UECwwFbXlrZXkxFzAVBgNVBAMMDkNPTU1JVFRFRS1OT0RFMCAXDTI0\\nMDgyNzExMzgxMloYDzIxMjQwODAzMTEzODEyWjBmMQswCQYDVQQGEwJDTjEOMAwG\\nA1UECAwFbXlrZXkxDjAMBgNVBAcMBW15a2V5MQ4wDAYDVQQKDAVteWtleTEOMAwG\\nA1UECwwFbXlrZXkxFzAVBgNVBAMMDkNPTU1JVFRFRS1OT0RFMIIBIjANBgkqhkiG\\n9w0BAQEFAAOCAQ8AMIIBCgKCAQEAyBDWvnDIBfCg0Bpb1Zzyu1/TI9W/eVO9S/n9\\nTyfVoWHOd4vLuHs3EmP6QtPWBtS/wSx579Fm+qhg+Px5TdrPIPceK3CgFlWqesz1\\n+qrPCfuHJ0yRpQb1yQ77l23JPTb4ze7zmeG2mCBImpCl2TFnUgNDrK8VsqALZ5fE\\nO3eFkwn8zpKaqEmRMOY8PJJh76/PNYYu5XobS6Sie08gb7Vr5BKCC1aAvM6yeapC\\nZRZ1QoAgbgOz5wQgOVmni5zn9UWeSYe2xeI6geqKLVc/1Jr5zA3nLItWF+KveUix\\nV3oKO43nfO3e+fPli9jJkFM0HZD5FhMxQIpPjuF7aecUZG0GwwIDAQABMA0GCSqG\\nSIb3DQEBCwUAA4IBAQCXF83k4Tdlfcc456agxHUDW4kCgsQVZ0mcfJLVL0+UBMLj\\nVFz50Su2Vtq8Pe/N4T2BHB1zZgQzs+T7oRtEb65H2uTxO8AFtang9OgpFJYxEkgH\\n9oRgAeD32xIwob5cj2znf3Ct03FKKkrpaXvGuPlSYV/qfy1Gittb3UFi2y7+LDk2\\nq6Qd7jpMYK6HdtVwj0FgyxRIzzGQT6/d9soyI8a8bW/VKsJ/+DEANIZFSw9Y80Ye\\nEVQJHi2LfmEXaxvD9m1vO50Dmspc5dy4J6QKY6HgzXcdxZbjetwaJ9ilG7LgLbh8\\nXOL/r9jTK9//UMFPq27xZ5PMrsGR+PjV/9NasLac\\n-----END CERTIFICATE-----\\n\"\n" +
            "      },\n" +
            "      {\n" +
            "        \"endpoint\": \"grpcs://127.0.0.1:10083\",\n" +
            "        \"node_id\": \"node4\",\n" +
            "        \"tls_cert\": \"-----BEGIN CERTIFICATE-----\\nMIIDSjCCAjICCQC7UpnZnD+2AjANBgkqhkiG9w0BAQsFADBmMQswCQYDVQQGEwJD\\nTjEOMAwGA1UECAwFbXlrZXkxDjAMBgNVBAcMBW15a2V5MQ4wDAYDVQQKDAVteWtl\\neTEOMAwGA1UECwwFbXlrZXkxFzAVBgNVBAMMDkNPTU1JVFRFRS1OT0RFMCAXDTI0\\nMDgyNzExMzgxMloYDzIxMjQwODAzMTEzODEyWjBmMQswCQYDVQQGEwJDTjEOMAwG\\nA1UECAwFbXlrZXkxDjAMBgNVBAcMBW15a2V5MQ4wDAYDVQQKDAVteWtleTEOMAwG\\nA1UECwwFbXlrZXkxFzAVBgNVBAMMDkNPTU1JVFRFRS1OT0RFMIIBIjANBgkqhkiG\\n9w0BAQEFAAOCAQ8AMIIBCgKCAQEAyBDWvnDIBfCg0Bpb1Zzyu1/TI9W/eVO9S/n9\\nTyfVoWHOd4vLuHs3EmP6QtPWBtS/wSx579Fm+qhg+Px5TdrPIPceK3CgFlWqesz1\\n+qrPCfuHJ0yRpQb1yQ77l23JPTb4ze7zmeG2mCBImpCl2TFnUgNDrK8VsqALZ5fE\\nO3eFkwn8zpKaqEmRMOY8PJJh76/PNYYu5XobS6Sie08gb7Vr5BKCC1aAvM6yeapC\\nZRZ1QoAgbgOz5wQgOVmni5zn9UWeSYe2xeI6geqKLVc/1Jr5zA3nLItWF+KveUix\\nV3oKO43nfO3e+fPli9jJkFM0HZD5FhMxQIpPjuF7aecUZG0GwwIDAQABMA0GCSqG\\nSIb3DQEBCwUAA4IBAQCXF83k4Tdlfcc456agxHUDW4kCgsQVZ0mcfJLVL0+UBMLj\\nVFz50Su2Vtq8Pe/N4T2BHB1zZgQzs+T7oRtEb65H2uTxO8AFtang9OgpFJYxEkgH\\n9oRgAeD32xIwob5cj2znf3Ct03FKKkrpaXvGuPlSYV/qfy1Gittb3UFi2y7+LDk2\\nq6Qd7jpMYK6HdtVwj0FgyxRIzzGQT6/d9soyI8a8bW/VKsJ/+DEANIZFSw9Y80Ye\\nEVQJHi2LfmEXaxvD9m1vO50Dmspc5dy4J6QKY6HgzXcdxZbjetwaJ9ilG7LgLbh8\\nXOL/r9jTK9//UMFPq27xZ5PMrsGR+PjV/9NasLac\\n-----END CERTIFICATE-----\\n\"\n" +
            "      }\n" +
            "    ]\n" +
            "  },\n" +
            "  \"ptc_certificate\": \"-----BEGIN PROOF TRANSFORMATION COMPONENT CERTIFICATE-----\\nAAD4AQAAAAABAAAAMQEADAAAAGFudGNoYWluLXB0YwIAAQAAAAIDAGsAAAAAAGUA\\nAAAAAAEAAAAAAQBYAAAAMFYwEAYHKoZIzj0CAQYFK4EEAAoDQgAE9LJYmJcZBSEj\\nN2rNGruF1zIIq5JLjSqlwZQ2xNcm2TL0prWFdapt0BVrACpaw0MitKnYjkcv16qm\\n+XUbFK/E3wQACAAAANIlv2YAAAAABQAIAAAAUlmgaAAAAAAGAKAAAAAAAJoAAAAA\\nAAMAAAAxLjABAA0AAABjb21taXR0ZWUtcHRjAgABAAAAAQMAawAAAAAAZQAAAAAA\\nAQAAAAABAFgAAAAwVjAQBgcqhkjOPQIBBgUrgQQACgNCAAT0sliYlxkFISM3as0a\\nu4XXMgirkkuNKqXBlDbE1ybZMvSmtYV1qm3QFWsAKlrDQyK0qdiORy/Xqqb5dRsU\\nr8TfBAAAAAAABwCfAAAAAACZAAAAAAAKAAAAS0VDQ0FLLTI1NgEAIAAAAMMQP7r7\\nzn4cC2mvoPTZOFzcfakEvZMwQykDEQv9al5fAgAWAAAAS2VjY2FrMjU2V2l0aFNl\\nY3AyNTZrMQMAQQAAAOKPO60UaQlG2KkvfR9QQmM94G2vojyH2RAtWBbewOIqGtgk\\nOZuajWhHiipbzTT8Ssb/LS65C/5HeVMAYNkc/gAA\\n-----END PROOF TRANSFORMATION COMPONENT CERTIFICATE-----\\n\"" +
            "}";

    public static final byte[] RAW_NODE_PTC_PUBLIC_KEY = PemUtil.readPem(new ByteArrayInputStream(
                    ("-----BEGIN PUBLIC KEY-----\n" +
                            "MFYwEAYHKoZIzj0CAQYFK4EEAAoDQgAEULzSDPM4mhRkGpCiVirHZTWfzuZ/PfO6\n" +
                            "+/RfVgGpZxs4EUQZhw5SnYWIwK99KxDPNxtYG7OEiWDA7+EKZUWBIw==\n" +
                            "-----END PUBLIC KEY-----\n").getBytes()
            )
    );

    public static final PrivateKey NODE_PTC_PRIVATE_KEY = SignAlgoEnum.KECCAK256_WITH_SECP256K1.getSigner().readPemPrivateKey(
            ("-----BEGIN EC PRIVATE KEY-----\n" +
                    "MHQCAQEEINtcJsfWygsBn4u8sscy/04yPSpafFwCW4yVg1Vrb8looAcGBSuBBAAK\n" +
                    "oUQDQgAEULzSDPM4mhRkGpCiVirHZTWfzuZ/PfO6+/RfVgGpZxs4EUQZhw5SnYWI\n" +
                    "wK99KxDPNxtYG7OEiWDA7+EKZUWBIw==\n" +
                    "-----END EC PRIVATE KEY-----\n").getBytes()
    );

    public static final String ANTCHAIN_DOT_COM_CERT = "-----BEGIN DOMAIN NAME CERTIFICATE-----\n" +
            "AAD/AQAAAAABAAAAMQEACgAAAHRlc3Rkb21haW4CAAEAAAABAwBrAAAAAABlAAAA\n" +
            "AAABAAAAAAEAWAAAADBWMBAGByqGSM49AgEGBSuBBAAKA0IABFC80gzzOJoUZBqQ\n" +
            "olYqx2U1n87mfz3zuvv0X1YBqWcbOBFEGYcOUp2FiMCvfSsQzzcbWBuzhIlgwO/h\n" +
            "CmVFgSMEAAgAAACU2f9mAAAAAAUACAAAABQN4WgAAAAABgCpAAAAAACjAAAAAAAD\n" +
            "AAAAMS4wAQABAAAAAAIABAAAAC5jb20DAAwAAABhbnRjaGFpbi5jb20EAGsAAAAA\n" +
            "AGUAAAAAAAEAAAAAAQBYAAAAMFYwEAYHKoZIzj0CAQYFK4EEAAoDQgAEULzSDPM4\n" +
            "mhRkGpCiVirHZTWfzuZ/PfO6+/RfVgGpZxs4EUQZhw5SnYWIwK99KxDPNxtYG7OE\n" +
            "iWDA7+EKZUWBIwUAAAAAAAcAnwAAAAAAmQAAAAAACgAAAEtFQ0NBSy0yNTYBACAA\n" +
            "AAByc8F3QCrg/WOAS2gkl0OQ8JQjmgHpdYwIBuCz5U0jBQIAFgAAAEtlY2NhazI1\n" +
            "NldpdGhTZWNwMjU2azEDAEEAAAB6carDDLYBMcX33uwIQzitOeYeccX96aEJbmz0\n" +
            "jZju+T2XSMfPCd2GpMsrQAFbr0BvvSPAofWFgVDEyN+J815jAQ==\n" +
            "-----END DOMAIN NAME CERTIFICATE-----\n";

    private static final ThirdPartyBlockchainTrustAnchorV1 tpbta;

    private static final CrossChainLane crossChainLane;

    private static final ObjectIdentity oid;

    private static final BlockchainTrustAnchorV1 bta;

    private static final AbstractCrossChainCertificate domainCert;

    private static final ConsensusState anchorState;

    private static final ConsensusState currState;

    private static final UniformCrosschainPacket ucp;

    private static final ValidatedConsensusStateV1 anchorVcs;

    private static final ValidatedConsensusStateV1 currVcs;

    private static final String COMMITTEE_ID = "committee";

    static {
        AbstractCrossChainCertificate bcdnsCert = CrossChainCertificateUtil.readCrossChainCertificateFromPem(BCDNS_CERT.getBytes());
        System.out.println("bcdns cert : " + HexUtil.encodeHexStr(bcdnsCert.encode()));

        oid = new X509PubkeyInfoObjectIdentity(RAW_NODE_PTC_PUBLIC_KEY);

        OptionalEndorsePolicy policy = new OptionalEndorsePolicy();
        policy.setThreshold(new OptionalEndorsePolicy.Threshold(OptionalEndorsePolicy.OperatorEnum.GREATER_OR_EQUALS, 0));
        NodeEndorseInfo nodeEndorseInfo = new NodeEndorseInfo();
        nodeEndorseInfo.setNodeId("node1");
        nodeEndorseInfo.setRequired(true);
        NodePublicKeyEntry nodePubkeyEntry = new NodePublicKeyEntry("default", ((X509PubkeyInfoObjectIdentity) oid).getPublicKey());
        nodeEndorseInfo.setPublicKey(nodePubkeyEntry);
        crossChainLane = new CrossChainLane(new CrossChainDomain("test"), new CrossChainDomain("test"), CrossChainIdentity.fromHexStr("0000000000000000000000000000000000000000000000000000000000000001"), CrossChainIdentity.fromHexStr("0000000000000000000000000000000000000000000000000000000000000001"));
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
                        ListUtil.toList(nodeEndorseInfo)
                ).encode(),
                null
        );
        tpbta.setEndorseProof(
                CommitteeEndorseProof.builder()
                        .committeeId(COMMITTEE_ID)
                        .sigs(ListUtil.toList(new CommitteeNodeProof(
                                "node1",
                                SignAlgoEnum.KECCAK256_WITH_SECP256K1,
                                SignAlgoEnum.KECCAK256_WITH_SECP256K1.getSigner()
                                        .sign(NODE_PTC_PRIVATE_KEY, tpbta.getEncodedToSign())
                        ))).build().encode()
        );
        Assert.assertEquals(ThirdPartyBlockchainTrustAnchor.TypeEnum.LANE_LEVEL, tpbta.type());

        bta = new BlockchainTrustAnchorV1();
        bta.setBcOwnerPublicKey(RAW_NODE_PTC_PUBLIC_KEY);
        bta.setDomain(new CrossChainDomain("antchain.com"));
        bta.setSubjectIdentity("test".getBytes());
        bta.setBcOwnerSigAlgo(SignAlgoEnum.KECCAK256_WITH_SECP256K1);
        bta.setPtcOid(NODE_PTC_CERT.getCredentialSubjectInstance().getApplicant());
        bta.setSubjectProduct("mychain");
        bta.setExtension(
                new VerifyBtaExtension(
                        CommitteeEndorseRoot.decode(tpbta.getEndorseRoot()),
                        crossChainLane
                ).encode()
        );
        bta.setSubjectVersion(0);
        bta.setAmId(RandomUtil.randomBytes(32));

        bta.sign(NODE_PTC_PRIVATE_KEY);

        domainCert = CrossChainCertificateUtil.readCrossChainCertificateFromPem(ANTCHAIN_DOT_COM_CERT.getBytes());

        anchorState = new ConsensusState(
                crossChainLane.getSenderDomain(),
                BigInteger.valueOf(100L),
                RandomUtil.randomBytes(32),
                RandomUtil.randomBytes(32),
                System.currentTimeMillis(),
                "{}".getBytes(),
                "{}".getBytes(),
                "{}".getBytes()
        );

        currState = new ConsensusState(
                crossChainLane.getSenderDomain(),
                BigInteger.valueOf(101L),
                RandomUtil.randomBytes(32),
                anchorState.getParentHash(),
                System.currentTimeMillis(),
                "{}".getBytes(),
                "{}".getBytes(),
                "{}".getBytes()
        );

        ISDPMessage sdpMessage = SDPMessageFactory.createSDPMessage(
                1,
                new byte[32],
                crossChainLane.getReceiverDomain().getDomain(),
                crossChainLane.getReceiverId().getRawID(),
                -1,
                "awesome antchain-bridge".getBytes()
        );

        IAuthMessage am = AuthMessageFactory.createAuthMessage(
                1,
                crossChainLane.getSenderId().getRawID(),
                0,
                sdpMessage.encode()
        );

        ucp = new UniformCrosschainPacket(
                crossChainLane.getSenderDomain(),
                CrossChainMessage.createCrossChainMessage(
                        CrossChainMessage.CrossChainMessageType.AUTH_MSG,
                        BigInteger.valueOf(101L),
                        DateUtil.current(),
                        currState.getHash(),
                        am.encode(),
                        "event".getBytes(),
                        "merkle proof".getBytes(),
                        RandomUtil.randomBytes(32)
                ),
                NODE_PTC_CERT.getCredentialSubjectInstance().getApplicant()
        );

        anchorVcs = BeanUtil.copyProperties(anchorState, ValidatedConsensusStateV1.class);
        anchorVcs.setPtcOid(NODE_PTC_CERT.getCredentialSubjectInstance().getApplicant());
        anchorVcs.setTpbtaVersion(tpbta.getTpbtaVersion());
        anchorVcs.setPtcType(PTCTypeEnum.COMMITTEE);

        CommitteeNodeProof nodeProof = CommitteeNodeProof.builder()
                .nodeId("node1")
                .signAlgo(SignAlgoEnum.KECCAK256_WITH_SECP256K1)
                .signature(SignAlgoEnum.KECCAK256_WITH_SECP256K1.getSigner().sign(NODE_PTC_PRIVATE_KEY, anchorVcs.getEncodedToSign()))
                .build();
        CommitteeEndorseProof proof = new CommitteeEndorseProof();
        proof.setCommitteeId(COMMITTEE_ID);
        proof.setSigs(ListUtil.toList(nodeProof));
        anchorVcs.setPtcProof(proof.encode());

        currVcs = BeanUtil.copyProperties(currState, ValidatedConsensusStateV1.class);
        currVcs.setPtcOid(NODE_PTC_CERT.getCredentialSubjectInstance().getApplicant());
        currVcs.setTpbtaVersion(tpbta.getTpbtaVersion());
        currVcs.setPtcType(PTCTypeEnum.COMMITTEE);

        nodeProof = CommitteeNodeProof.builder()
                .nodeId("node1")
                .signAlgo(SignAlgoEnum.KECCAK256_WITH_SECP256K1)
                .signature(SignAlgoEnum.KECCAK256_WITH_SECP256K1.getSigner().sign(NODE_PTC_PRIVATE_KEY, currVcs.getEncodedToSign()))
                .build();
        proof = new CommitteeEndorseProof();
        proof.setCommitteeId(COMMITTEE_ID);
        proof.setSigs(ListUtil.toList(nodeProof));
        currVcs.setPtcProof(proof.encode());

        CommitteeVerifyAnchor verifyAnchor = new CommitteeVerifyAnchor("committee");
        verifyAnchor.addNode("node1", "default", ((X509PubkeyInfoObjectIdentity) oid).getPublicKey());

        // prepare the network stuff
        CommitteeNetworkInfo committeeNetworkInfo = new CommitteeNetworkInfo("committee");
        committeeNetworkInfo.addEndpoint("node1", "grpcs://0.0.0.0:8080", NODE_SERVER_TLS_CERT);

        // build it first
        PTCTrustRoot ptcTrustRoot = PTCTrustRoot.builder()
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
        System.out.println("ptc trust root : " + HexUtil.encodeHexStr(ptcTrustRoot.encode()));
    }

    private static final Map<String, CommitteeNodeServiceGrpc.CommitteeNodeServiceBlockingStub> mockStubMap = new HashMap<>();

    @BeforeClass
    public static void beforeClass() throws Exception {
        mockStubNode1 = mock(CommitteeNodeServiceGrpc.CommitteeNodeServiceBlockingStub.class);
        when(mockStubNode1.heartbeat(any())).thenReturn(
                Response.newBuilder()
                        .setHeartbeatResp(
                                HeartbeatResponse.newBuilder()
                                        .setCommitteeId(COMMITTEE_ID)
                                        .setNodeId("node1")
                                        .addProducts("mychain")
                        ).build()
        );
        mockStubNode2 = mock(CommitteeNodeServiceGrpc.CommitteeNodeServiceBlockingStub.class);
        when(mockStubNode2.heartbeat(any())).thenReturn(
                Response.newBuilder()
                        .setHeartbeatResp(
                                HeartbeatResponse.newBuilder()
                                        .setCommitteeId(COMMITTEE_ID)
                                        .setNodeId("node2")
                                        .addProducts("mychain")
                        ).build()
        );
        mockStubNode3 = mock(CommitteeNodeServiceGrpc.CommitteeNodeServiceBlockingStub.class);
        when(mockStubNode3.heartbeat(any())).thenReturn(
                Response.newBuilder()
                        .setHeartbeatResp(
                                HeartbeatResponse.newBuilder()
                                        .setCommitteeId(COMMITTEE_ID)
                                        .setNodeId("node3")
                                        .addProducts("mychain")
                        ).build()
        );
        mockStubNode4 = mock(CommitteeNodeServiceGrpc.CommitteeNodeServiceBlockingStub.class);
        when(mockStubNode4.heartbeat(any())).thenReturn(
                Response.newBuilder()
                        .setHeartbeatResp(
                                HeartbeatResponse.newBuilder()
                                        .setCommitteeId(COMMITTEE_ID)
                                        .setNodeId("node4")
                                        .addAllProducts(ListUtil.toList("mychain", "ethereum"))
                        ).build()
        );

        mockStubMap.put("node1", mockStubNode1);
        mockStubMap.put("node2", mockStubNode2);
        mockStubMap.put("node3", mockStubNode3);
        mockStubMap.put("node4", mockStubNode4);

        mockStaticCommitteeNodeServiceBlockingStub = mockStatic(CommitteeNodeServiceGrpc.class);

        mockStaticNetUtil = mockStatic(NetUtil.class);
        mockStaticNetUtil.when(() -> NetUtil.isOpen(notNull(), anyInt())).thenReturn(true);
        mockStaticNetUtil.when(() -> NetUtil.createAddress(anyString(), anyInt())).thenReturn(new InetSocketAddress(10080));
    }

    @AfterClass
    public static void afterClass() throws Exception {
        mockStaticCommitteeNodeServiceBlockingStub.close();
        mockStaticNetUtil.close();
    }

    @Before
    public void before() throws Exception {
        mockStaticCommitteeNodeServiceBlockingStub.when(() -> CommitteeNodeServiceGrpc.newBlockingStub(any()))
                .thenReturn(mockStubNode1, mockStubNode2, mockStubNode3, mockStubNode4);
    }

    @Test
    @SneakyThrows
    public void testStartup() {
        CommitteePTCService ptcService = new CommitteePTCService();
        ptcService.startup(SERVICE_CONF.getBytes());

        assertEquals(4, ptcService.getNodeMap().size());
        assertTrue(ptcService.getNodeMap().values().stream().allMatch(Node::isAvailable));
    }

    @Test
    public void testQuerySupportedBlockchainProducts() {
        CommitteePTCService ptcService = new CommitteePTCService();
        ptcService.startup(SERVICE_CONF.getBytes());

        Set<String> products = ptcService.querySupportedBlockchainProducts();
        assertEquals(1, products.size());
        assertTrue(products.contains("mychain"));
    }

    @Test
    public void testVerifyBlockchainTrustAnchor() {
        CommitteePTCService ptcService = new CommitteePTCService();
        ptcService.startup(SERVICE_CONF.getBytes());

        for (Map.Entry<String, CommitteeNodeServiceGrpc.CommitteeNodeServiceBlockingStub> entry : mockStubMap.entrySet()) {
            ThirdPartyBlockchainTrustAnchor tpbtaTemp = BeanUtil.copyProperties(tpbta, ThirdPartyBlockchainTrustAnchorV1.class);
            tpbtaTemp.setEndorseProof(
                    CommitteeEndorseProof.builder()
                            .committeeId(COMMITTEE_ID)
                            .sigs(ListUtil.toList(
                                    CommitteeNodeProof.builder()
                                            .nodeId(entry.getKey())
                                            .signAlgo(SignAlgoEnum.KECCAK256_WITH_SECP256K1)
                                            .signature(SignAlgoEnum.KECCAK256_WITH_SECP256K1.getSigner()
                                                    .sign(NODE_PTC_PRIVATE_KEY, tpbtaTemp.getEncodedToSign()))
                                            .build()
                            )).build().encode()
            );
            when(entry.getValue().verifyBta(any())).thenReturn(
                    Response.newBuilder()
                            .setVerifyBtaResp(
                                    VerifyBtaResponse.newBuilder()
                                            .setRawTpBta(
                                                    ByteString.copyFrom(tpbtaTemp.encode())
                                            )
                            ).build()
            );
        }

        ThirdPartyBlockchainTrustAnchor result = ptcService.verifyBlockchainTrustAnchor(domainCert, bta);
        CommitteeEndorseProof endorseProof = CommitteeEndorseProof.decode(result.getEndorseProof());
        assertEquals(COMMITTEE_ID, endorseProof.getCommitteeId());
        assertEquals(4, endorseProof.getSigs().size());
    }

    @Test
    public void testCommitAnchorState() {
        CommitteePTCService ptcService = new CommitteePTCService();
        ptcService.startup(SERVICE_CONF.getBytes());

        when(mockStubNode1.commitAnchorState(any())).thenReturn(
                Response.newBuilder()
                        .setCommitAnchorStateResp(
                                CommitAnchorStateResponse.newBuilder()
                                        .setRawValidatedConsensusState(
                                                ByteString.copyFrom(anchorVcs.encode())
                                        )
                        ).build()
        );

        ValidatedConsensusState vcs = ptcService.commitAnchorState(bta, tpbta, anchorState);

        assertArrayEquals(anchorState.getHash(), vcs.getHash());
        assertEquals(anchorState.getHeight(), vcs.getHeight());
        assertEquals(anchorState.getDomain(), vcs.getDomain());

        CommitteeEndorseProof endorseProof = CommitteeEndorseProof.decode(vcs.getPtcProof());
        assertEquals(COMMITTEE_ID, endorseProof.getCommitteeId());
        assertEquals(1, endorseProof.getSigs().size());
    }

    @Test
    public void testCommitConsensusState() {
        CommitteePTCService ptcService = new CommitteePTCService();
        ptcService.startup(SERVICE_CONF.getBytes());

        when(mockStubNode1.commitConsensusState(any())).thenReturn(
                Response.newBuilder()
                        .setCommitConsensusStateResp(
                                CommitConsensusStateResponse.newBuilder()
                                        .setRawValidatedConsensusState(
                                                ByteString.copyFrom(currVcs.encode())
                                        )
                        ).build()
        );

        ValidatedConsensusState vcs = ptcService.commitConsensusState(tpbta, anchorVcs, currState);

        assertArrayEquals(currState.getHash(), vcs.getHash());
        assertEquals(currState.getHeight(), vcs.getHeight());
        assertEquals(currState.getDomain(), vcs.getDomain());

        CommitteeEndorseProof endorseProof = CommitteeEndorseProof.decode(vcs.getPtcProof());
        assertEquals(COMMITTEE_ID, endorseProof.getCommitteeId());
        assertEquals(1, endorseProof.getSigs().size());
    }

    @Test
    @SneakyThrows
    public void testCollectValidatedConsensusStateSkipsFailedOptionalFuture() {
        CommitteePTCService ptcService = new CommitteePTCService();
        CompletableFuture<ValidatedConsensusState> failedOptional = new CompletableFuture<>();
        failedOptional.completeExceptionally(new RuntimeException("optional node is behind"));

        Method collector = CommitteePTCService.class.getDeclaredMethod(
                "collectValidatedConsensusStateResults",
                java.util.List.class
        );
        collector.setAccessible(true);
        ValidatedConsensusState result = (ValidatedConsensusState) collector.invoke(
                ptcService,
                Arrays.<Future<ValidatedConsensusState>>asList(
                        CompletableFuture.completedFuture(currVcs),
                        failedOptional
                )
        );

        assertArrayEquals(currVcs.getEncodedToSign(), result.getEncodedToSign());
        assertEquals(1, CommitteeEndorseProof.decode(result.getPtcProof()).getSigs().size());
    }

    @Test
    @SneakyThrows
    @SuppressWarnings("unchecked")
    public void testCollectNodeVerifyResultsSkipsFailedOptionalFuture() {
        CommitteePTCService ptcService = new CommitteePTCService();
        NodeVerifyCrossChainMessageResult requiredResult =
                new NodeVerifyCrossChainMessageResult(null, "approved", "");
        CompletableFuture<NodeVerifyCrossChainMessageResult> failedOptional = new CompletableFuture<>();
        failedOptional.completeExceptionally(new RuntimeException("optional node has not indexed this state"));

        Method collector = CommitteePTCService.class.getDeclaredMethod(
                "collectNodeVerifyResults",
                java.util.List.class
        );
        collector.setAccessible(true);
        java.util.List<NodeVerifyCrossChainMessageResult> results =
                (java.util.List<NodeVerifyCrossChainMessageResult>) collector.invoke(
                        ptcService,
                        Arrays.<Future<NodeVerifyCrossChainMessageResult>>asList(
                                CompletableFuture.completedFuture(requiredResult),
                                failedOptional
                        )
                );

        assertEquals(1, results.size());
        assertSame(requiredResult, results.get(0));
    }

    @Test
    public void testVerifyCrossChainMessage() {
        CommitteePTCService ptcService = new CommitteePTCService();
        ptcService.startup(SERVICE_CONF.getBytes());
        String ucpId = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

        CommitteeNodeProof nodeProof = CommitteeNodeProof.builder()
                .nodeId("node1")
                .signAlgo(SignAlgoEnum.KECCAK256_WITH_SECP256K1)
                .signature(SignAlgoEnum.KECCAK256_WITH_SECP256K1.getSigner().sign(
                        NODE_PTC_PRIVATE_KEY,
                        ThirdPartyProof.create(
                                tpbta.getTpbtaVersion(),
                                ucp.getSrcMessage().getMessage(),
                                crossChainLane
                        ).getEncodedToSign()
                )).build();

        when(mockStubNode1.verifyCrossChainMessage(any())).thenReturn(
                Response.newBuilder()
                        .setVerifyCrossChainMessageResp(
                                VerifyCrossChainMessageResponse.newBuilder()
                                        .setRawNodeProof(ByteString.copyFrom(nodeProof.encode()))
                                        .setRegulationStatus("approved")
                        ).build()
        );

        clearInvocations(mockStubNode1);
        PTCVerifyCrossChainMessageResult verifyResult =
                ptcService.verifyCrossChainMessageWithResult(tpbta, currVcs, ucp, ucpId);
        ThirdPartyProof tpProof = verifyResult.getThirdPartyProof();

        ArgumentCaptor<VerifyCrossChainMessageRequest> requestCaptor =
                ArgumentCaptor.forClass(VerifyCrossChainMessageRequest.class);
        verify(mockStubNode1).verifyCrossChainMessage(requestCaptor.capture());
        assertEquals(ucpId, requestCaptor.getValue().getUcpId());
        assertArrayEquals(ucp.encode(), requestCaptor.getValue().getRawUcp().toByteArray());

        assertEquals(tpbta.getCrossChainLane().getLaneKey(), tpProof.getTpbtaCrossChainLane().getLaneKey());
        assertEquals("approved", verifyResult.getRegulationStatus());
        assertEquals("", verifyResult.getRegulationReason());
        CommitteeEndorseProof endorseProof = CommitteeEndorseProof.decode(tpProof.getRawProof());
        assertEquals(COMMITTEE_ID, endorseProof.getCommitteeId());
        assertEquals(1, endorseProof.getSigs().size());

        clearInvocations(mockStubNode1);
        ptcService.verifyCrossChainMessageWithResult(tpbta, currVcs, ucp);
        verify(mockStubNode1).verifyCrossChainMessage(requestCaptor.capture());
        assertEquals("", requestCaptor.getValue().getUcpId());
    }

    @Test
    public void testDioxideDomainUsesFullCommitteeProof() {
        CommitteePTCService ptcService = new CommitteePTCService();
        ptcService.startup(SERVICE_CONF.getBytes());
        ThirdPartyBlockchainTrustAnchorV1 dioxideTpBta = BeanUtil.copyProperties(
                tpbta,
                ThirdPartyBlockchainTrustAnchorV1.class
        );
        CrossChainLane dioxideLane = new CrossChainLane(new CrossChainDomain("dioxide2"));
        dioxideTpBta.setCrossChainLane(dioxideLane);

        CommitteeNodeProof nodeProof = CommitteeNodeProof.builder()
                .nodeId("node1")
                .signAlgo(SignAlgoEnum.KECCAK256_WITH_SECP256K1)
                .signature(SignAlgoEnum.KECCAK256_WITH_SECP256K1.getSigner().sign(
                        NODE_PTC_PRIVATE_KEY,
                        ThirdPartyProof.create(
                                dioxideTpBta.getTpbtaVersion(),
                                ucp.getSrcMessage().getMessage(),
                                dioxideLane
                        ).getEncodedToSign()
                )).build();
        when(mockStubNode1.verifyCrossChainMessage(any())).thenReturn(
                Response.newBuilder()
                        .setVerifyCrossChainMessageResp(
                                VerifyCrossChainMessageResponse.newBuilder()
                                        .setRawNodeProof(ByteString.copyFrom(nodeProof.encode()))
                                        .setRegulationStatus("approved")
                        ).build()
        );

        PTCVerifyCrossChainMessageResult result = ptcService.verifyCrossChainMessageWithResult(
                dioxideTpBta,
                currVcs,
                ucp,
                "dioxide-ucp"
        );

        assertNotNull(result.getThirdPartyProof());
        assertTrue(result.getThirdPartyProof().getRawProof().length > 0);
        CommitteeEndorseProof proof = CommitteeEndorseProof.decode(result.getThirdPartyProof().getRawProof());
        assertEquals(1, proof.getSigs().size());
        assertEquals("node1", proof.getSigs().get(0).getNodeId());
    }

    @Test
    public void testQueryThirdPartyBlockchainTrustAnchor() {
        CommitteePTCService ptcService = new CommitteePTCService();
        ptcService.startup(SERVICE_CONF.getBytes());

        for (Map.Entry<String, CommitteeNodeServiceGrpc.CommitteeNodeServiceBlockingStub> entry : mockStubMap.entrySet()) {
            ThirdPartyBlockchainTrustAnchor tpbtaTemp = BeanUtil.copyProperties(tpbta, ThirdPartyBlockchainTrustAnchorV1.class);
            tpbtaTemp.setEndorseProof(
                    CommitteeEndorseProof.builder()
                            .committeeId(COMMITTEE_ID)
                            .sigs(ListUtil.toList(
                                    CommitteeNodeProof.builder()
                                            .nodeId(entry.getKey())
                                            .signAlgo(SignAlgoEnum.KECCAK256_WITH_SECP256K1)
                                            .signature(SignAlgoEnum.KECCAK256_WITH_SECP256K1.getSigner()
                                                    .sign(NODE_PTC_PRIVATE_KEY, tpbtaTemp.getEncodedToSign()))
                                            .build()
                            )).build().encode()
            );
            when(entry.getValue().queryTpBta(any())).thenReturn(
                    Response.newBuilder()
                            .setQueryTpBtaResp(
                                    QueryTpBtaResponse.newBuilder()
                                            .setRawTpBta(
                                                    ByteString.copyFrom(tpbtaTemp.encode())
                                            )
                            ).build()
            );
        }

        ThirdPartyBlockchainTrustAnchor result = ptcService.queryThirdPartyBlockchainTrustAnchor(tpbta.getCrossChainLane());
        CommitteeEndorseProof endorseProof = CommitteeEndorseProof.decode(result.getEndorseProof());
        assertEquals(COMMITTEE_ID, endorseProof.getCommitteeId());
        assertEquals(4, endorseProof.getSigs().size());
    }

    @Test
    public void testEndorseBlockState() {
        IAuthMessage am = AuthMessageFactory.createAuthMessage(
                1,
                CrossChainIdentity.ZERO_ID.getRawID(),
                0,
                SDPMessageFactory.createValidatedBlockStateSDPMsg(
                        new CrossChainDomain("receiverdomain"),
                        new BlockState(currState.getDomain(), currState.getHash(), currState.getHeight(), currState.getStateTimestamp())
                ).encode()
        );
        CommitteePTCService ptcService = new CommitteePTCService();
        ptcService.startup(SERVICE_CONF.getBytes());

        CommitteeNodeProof nodeProof = CommitteeNodeProof.builder()
                .nodeId("node1")
                .signAlgo(SignAlgoEnum.KECCAK256_WITH_SECP256K1)
                .signature(SignAlgoEnum.KECCAK256_WITH_SECP256K1.getSigner().sign(
                        NODE_PTC_PRIVATE_KEY,
                        ThirdPartyProof.create(
                                tpbta.getTpbtaVersion(),
                                am.encode(),
                                crossChainLane
                        ).getEncodedToSign()
                )).build();

        when(mockStubNode1.endorseBlockState(any())).thenReturn(
                Response.newBuilder()
                        .setEndorseBlockStateResp(
                                EndorseBlockStateResponse.newBuilder()
                                        .setCommitteeNodeProof(ByteString.copyFrom(nodeProof.encode()))
                                        .setBlockStateAuthMsg(ByteString.copyFrom(am.encode()))
                        ).build()
        );

        ThirdPartyProof tpProof = ptcService.endorseBlockState(tpbta, new CrossChainDomain("receiverdomain"), currVcs);

        assertEquals(tpbta.getCrossChainLane().getLaneKey(), tpProof.getTpbtaCrossChainLane().getLaneKey());
        CommitteeEndorseProof endorseProof = CommitteeEndorseProof.decode(tpProof.getRawProof());
        assertEquals(COMMITTEE_ID, endorseProof.getCommitteeId());
        assertEquals(1, endorseProof.getSigs().size());

        IAuthMessage respAM = AuthMessageFactory.createAuthMessage(tpProof.getResp().getBody());
        ISDPMessage respSDP = SDPMessageFactory.createSDPMessage(respAM.getPayload());
        assertEquals(new CrossChainDomain("receiverdomain"), respSDP.getTargetDomain());
        EndorseBlockStateResp resp = new EndorseBlockStateResp(respAM, null);

        assertArrayEquals(
                currVcs.getHash(),
                resp.getBlockState().getHash()
        );
        assertEquals(
                currVcs.getHeight(),
                resp.getBlockState().getHeight()
        );
        assertEquals(
                currVcs.getStateTimestamp(),
                resp.getBlockState().getTimestamp()
        );
    }
}

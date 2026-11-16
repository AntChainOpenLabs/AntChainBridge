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

package com.alipay.antchain.bridge.ptc.committee.monitor.node.config;

import java.security.PrivateKey;

import com.alipay.antchain.bridge.commons.bcdns.AbstractCrossChainCertificate;
import com.alipay.antchain.bridge.commons.bcdns.utils.CrossChainCertificateUtil;
import com.alipay.antchain.bridge.commons.utils.crypto.SignAlgoEnum;
import lombok.Getter;
import lombok.SneakyThrows;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

@Configuration
@Getter
public class CredentialConfig {

    @Bean
    @SneakyThrows
    public PrivateKey nodeKey(
            @Value("${committee.node.credential.sign-algo:KECCAK256_WITH_SECP256K1}") SignAlgoEnum signAlgo,
            @Value("${committee.node.credential.private-key-file}") Resource privateKeyFile) {
        return signAlgo.getSigner().readPemPrivateKey(privateKeyFile.getContentAsByteArray());
    }

    @Bean
    @SneakyThrows
    public AbstractCrossChainCertificate ptcCrossChainCert(
            @Value("${committee.node.credential.cert-file}") Resource certFile) {
        return CrossChainCertificateUtil.readCrossChainCertificateFromPem(certFile.getContentAsByteArray());
    }
}

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

package com.alipay.antchain.bridge.ptc.committee.monitor.node.cli.commands;

import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.*;

import cn.hutool.core.util.StrUtil;
import com.alipay.antchain.bridge.commons.utils.crypto.SignAlgoEnum;
import lombok.Getter;
import lombok.SneakyThrows;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.shell.standard.*;

@Getter
@ShellCommandGroup(value = "Utils Commands")
@ShellComponent
public class UtilsCommands extends BaseCommands {

    static {
        Security.addProvider(new BouncyCastleProvider());
    }

    @Value("${grpc.client.admin.address:static://localhost:10088}")
    private String adminAddress;

    @Override
    public boolean needAdminServer() {
        return false;
    }

    @ShellMethod(value = "Generate PEM files for the node private and public key")
    public String generateNodeAccount(
            @ShellOption(help = "Key algorithm, default SECP256K1", defaultValue = "SECP256K1") String keyAlgo,
            @ShellOption(valueProvider = FileValueProvider.class, help = "Directory path to save the keys", defaultValue = "") String outDir
    ) {
        try {
            var keyPair = SignAlgoEnum.getSignAlgoByKeySuffix(keyAlgo).getSigner().generateKeyPair();

            // dump the private key into pem
            Path privatePath = Paths.get(outDir, "private_key.pem");
            writePrivateKey(keyPair.getPrivate(), privatePath);

            // dump the public key into pem
            Path publicPath = Paths.get(outDir, "public_key.pem");
            writePublicKey(keyPair.getPublic(), publicPath);

            return StrUtil.format("private key path: {}\npublic key path: {}", privatePath.toAbsolutePath(), publicPath.toAbsolutePath());
        } catch (Exception e) {
            throw new RuntimeException("unexpected error please input stacktrace to check the detail", e);
        }
    }

    @SneakyThrows
    private void writePrivateKey(PrivateKey privateKey, Path outputFile) {
        // dump the private key into pem
        StringWriter stringWriter = new StringWriter(256);
        JcaPEMWriter jcaPEMWriter = new JcaPEMWriter(stringWriter);
        jcaPEMWriter.writeObject(privateKey);
        jcaPEMWriter.close();
        String privatePem = stringWriter.toString();
        Files.write(outputFile, privatePem.getBytes());
    }

    @SneakyThrows
    private void writePublicKey(PublicKey publicKey, Path outputFile) {
        // dump the public key into pem
        StringWriter stringWriter = new StringWriter(256);
        JcaPEMWriter jcaPEMWriter = new JcaPEMWriter(stringWriter);
        jcaPEMWriter.writeObject(publicKey);
        jcaPEMWriter.close();
        String pubkeyPem = stringWriter.toString();
        Files.write(outputFile, pubkeyPem.getBytes());
    }
}

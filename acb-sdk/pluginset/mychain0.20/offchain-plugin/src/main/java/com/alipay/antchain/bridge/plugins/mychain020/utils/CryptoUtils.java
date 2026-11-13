package com.alipay.antchain.bridge.plugins.mychain020.utils;

import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.openssl.PKCS8Generator;
import org.bouncycastle.openssl.jcajce.JceOpenSSLPKCS8EncryptorBuilder;
import org.bouncycastle.operator.OutputEncryptor;
import org.bouncycastle.util.io.pem.PemObject;
import org.bouncycastle.util.io.pem.PemWriter;

import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.security.SecureRandom;
import java.util.Base64;

public class CryptoUtils {
    public static byte[] addPass(byte[] key, String password) throws Exception {
        PrivateKeyInfo privateKeyInfo = PrivateKeyInfo.getInstance(Base64.getDecoder().decode(key));
        JceOpenSSLPKCS8EncryptorBuilder encryptorBuilder = new JceOpenSSLPKCS8EncryptorBuilder(PKCS8Generator.PBE_SHA1_3DES);
        encryptorBuilder.setRandom(new SecureRandom());
        encryptorBuilder.setPasssword(password.toCharArray());
        OutputEncryptor oe = encryptorBuilder.build();
        PKCS8Generator gen = new PKCS8Generator(privateKeyInfo, oe);
        PemObject obj = gen.generate();

        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        PemWriter pemWriter = new PemWriter(new OutputStreamWriter(byteArrayOutputStream));
        pemWriter.writeObject(obj);
        pemWriter.close();
        return byteArrayOutputStream.toByteArray();
    }
}

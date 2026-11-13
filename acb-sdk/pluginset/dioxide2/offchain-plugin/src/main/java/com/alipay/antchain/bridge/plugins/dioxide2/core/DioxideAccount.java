package com.alipay.antchain.bridge.plugins.dioxide2.core;

import com.alipay.antchain.bridge.plugins.dioxide2.conf.DioxideAccountTypeEnum;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.Base64;

import com.alipay.antchain.bridge.plugins.dioxide2.conf.DioxideAddressTypeEnum;
import com.alipay.antchain.bridge.plugins.dioxide2.utils.Crc32cPure;

import org.bouncycastle.math.ec.rfc8032.Ed25519;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DioxideAccount {

    private byte[] privateKeyBytes;
    private byte[] publicKeyBytes;
    private byte[] addressBytes;
    private DioxideAccountTypeEnum accountType;

    public DioxideAccount(byte[] sk, byte[] vk, byte[] addr, DioxideAccountTypeEnum type) {
        this.privateKeyBytes = sk;
        this.publicKeyBytes = vk;
        this.addressBytes = addr;
        this.accountType = type;
    }

    public static DioxideAccount fromKey(String privateKey) {
        try {
            // 1. Base64 decode
            byte[] skBytes = Base64.getDecoder().decode(privateKey);
            byte[] skBytes32 = Arrays.copyOf(skBytes, 32); // 截取前32字节

            // 2. 生成公钥
            byte[] vkBytes = new byte[Ed25519.PUBLIC_KEY_SIZE];
            Ed25519.generatePublicKey(skBytes32, 0, vkBytes, 0);

            // 3. CRC32C 计算
            int crcValue = Crc32cPure.crc32c(vkBytes, 3);
//            System.out.println("crcValue: " + crcValue);
            long crc = DioxideAccountTypeEnum.ED25519.getValue() | (0xfffffff0L & crcValue);
//            System.out.println("crc: " + crc);

            // 4. 拼接公钥 + 4字节CRC（小端序）
            ByteBuffer buf = ByteBuffer.allocate(vkBytes.length + 4);
            buf.put(vkBytes);
            buf.order(ByteOrder.LITTLE_ENDIAN).putInt((int) crc);
            byte[] address = buf.array();

            return new DioxideAccount(skBytes, vkBytes, address, DioxideAccountTypeEnum.ED25519);

        } catch (Exception e){
            throw new RuntimeException("fail to init dioxideAccount from privateKey", e);
        }
    }

    public String getAddressInString() {
        return new DioxideAddress(this.addressBytes, DioxideAddressTypeEnum.DEFAULT).getAddressInString();
    }

    public String getPrivateKeyInString() {
        return Base64.getEncoder().encodeToString(this.privateKeyBytes);
    }

    public String toString() {
        return String.format(
                "{\n  \"PrivateKey\": \"%s\",\n  \"PublicKey\": \"%s\",\n  \"Address\": \"%s\"\n}",
                Base64.getEncoder().encodeToString(this.privateKeyBytes), Base64.getEncoder().encodeToString(this.publicKeyBytes),
                Base64.getEncoder().encodeToString(this.addressBytes)
        );
    }

//    public boolean isValid() {
//        return this.privateKeyBytes != null && this.privateKeyBytes.length == 64 &&
//                this.publicKeyBytes != null && this.publicKeyBytes.length  == 32 &&
//                this.addressBytes   != null && this.addressBytes.length == 36 &&
//                this.accountType.getValue() < DioxideAccountTypeEnum.END.getValue();
//    }

}

package com.alipay.antchain.bridge.plugins.mychain020.model;

import com.alipay.antchain.bridge.plugins.mychain020.utils.SerializedDataUtils;
import org.bouncycastle.util.encoders.Hex;

/**
 * 合约消息
 *
 * <pre>
 *     MSGPayloadr := ( domain, identity, sequence, message )
 *         - domain: 接收域名
 *         - identity: 接收域名内的身份
 *         - sequence: 消息序号（协议保证一定是致密自增）
 *         - message: 消息体
 *
 *  报文格式:
 *      domain          (variable) 32 + N
 *      identity        (32byte)
 *      sequence        (4 byte)
 *      message         (variable) 32 + N
 * </pre>
 */
public class MSGPayload {

    /**
     * 接收域名
     */
    private String domainName;

    /**
     * 接收域名内的身份
     */
    private String identity;

    /**
     * 消息序号（协议保证一定是致密自增）
     */
    private int seq;

    /**
     * 消息体
     */
    private byte[] message;

    /**
     * 编码AM
     *
     * @return
     */
    public byte[] encode() {

        int domainNameSize = SerializedDataUtils.sizeOfBytes(domainName.getBytes());
        int identitySize = SerializedDataUtils.sizeOfIdentity();
        int sequenceSize = SerializedDataUtils.sizeOfUint32();
        int messageSize = SerializedDataUtils.sizeOfBytes(message);
        int totalSize = domainNameSize + identitySize + sequenceSize + messageSize;
        int offset = totalSize;
        byte[] buffer = new byte[totalSize];

        SerializedDataUtils.putBytes(offset, domainName.getBytes(), buffer);
        offset -= domainNameSize;
        SerializedDataUtils.putIdentity(offset, Hex.decode(identity), buffer);
        offset -= identitySize;
        SerializedDataUtils.putUint32(offset, seq, buffer);
        offset -= sequenceSize;
        SerializedDataUtils.putBytes(offset, message, buffer);
        offset -= sequenceSize;

        return buffer;
    }

    /**
     * 解码AM
     *
     * @param bytes
     * @return
     */
    public static MSGPayload decode(byte[] bytes) {

        MSGPayload payload = new MSGPayload();

        int offset = bytes.length;

        // domain          (variable) 32 + N
        String domain = new String(SerializedDataUtils.extractBytes(bytes, offset));
        offset -= SerializedDataUtils.bytesToBytesSize(bytes, offset);
        payload.setDomainName(domain);

        // identity         (32byte)
        byte[] identity = SerializedDataUtils.extractIdentity(bytes, offset);
        offset -= 32;
        payload.setIdentity(Hex.toHexString(identity));

        // sequence        (4 byte)
        long seq = SerializedDataUtils.extractUint32(bytes, offset);
        offset -= 4;
        payload.setSeq((int) seq);

        // message          (variable) 32 + N
        byte[] parsed_message = SerializedDataUtils.extractBytes(bytes, offset);
        payload.setMessage(parsed_message);

        return payload;
    }


    public String getDomainName() {
        return domainName;
    }

    public void setDomainName(String domainName) {
        this.domainName = domainName;
    }

    public String getIdentity() {
        return identity;
    }

    public void setIdentity(String identity) {
        this.identity = identity;
    }

    public int getSeq() {
        return seq;
    }

    public void setSeq(int seq) {
        this.seq = seq;
    }

    public byte[] getMessage() {
        return message;
    }

    public void setMessage(byte[] message) {
        this.message = message;
    }
}

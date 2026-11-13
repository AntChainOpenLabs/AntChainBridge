import cn.hutool.core.util.HexUtil;
import com.alibaba.fastjson.JSON;
import io.grpc.ManagedChannel;
import io.grpc.netty.shaded.io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import com.alipay.antchain.bridge.ptc.committee.monitor.system.grpc.*;
import com.alipay.antchain.bridge.commons.core.base.CrossChainDomain;
import com.alipay.antchain.bridge.commons.core.base.CrossChainIdentity;

import java.io.File;
import java.io.FileInputStream;

import javax.net.ssl.SSLException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

public class MonitorOrderClient {

    public static void main(String[] args) throws Exception {
        // gRPC 服务端信息
        String host = "localhost";
        int port = 10081;

        // File clientCert = new File("tls_certs/monitor-system.crt");
        // System.out.println(clientCert.exists());

        String cn = getCommonNameFromCert("tls_certs/monitor-node.crt");
        System.out.println("Extracted CN: " + cn);

        // 2. 创建 gRPC channel
        ManagedChannel channel = NettyChannelBuilder.forAddress(host, port)
                .sslContext(
                        GrpcSslContexts.forClient()
                                .trustManager(new File("tls_certs/monitor-node.crt"))
                                .build())
                .overrideAuthority(cn)  // 使用证书中的CN
                .build();

        // 创建 gRPC Stub
        MonitorOrderServiceGrpc.MonitorOrderServiceBlockingStub stub = MonitorOrderServiceGrpc.newBlockingStub(channel);

        // 发送请求
        RecvMonitorOrderRequest request = RecvMonitorOrderRequest.newBuilder().setMonitorOrder(
                MonitorOrder.newBuilder()
                        .setProduct("ethereum3")
                        .setDomain("eth01")
                        .setMonitorOrderType(Long.parseLong("1001" + "0000" + "000000000000000000000000", 2))
                        .setSenderDomain("eth01")
                        .setFromAddress("0000000000000000000000000a01ef051efeeeebe01a333f7323547494f30817")
                        .setReceiverDomain("eth02")
                        .setToAddress("000000000000000000000000df11d829eec4c192774f3ec171d822f6cb4c14d9")
                        .setTransactionContent("this is a monitor order")
                        .setExtra("nothing"))
                .build();

        RecvMonitorOrderResponse response = stub.recvMonitorOrder(request);

        System.out.println("Response Code: " + response.getCode());
        System.out.println("Response ErrorMsg: " + response.getErrorMsg());

        // 关闭通道
        channel.shutdown();
    }

    private static String getCommonNameFromCert(String certPath) throws Exception {
        try (FileInputStream fis = new FileInputStream(certPath)) {
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            X509Certificate cert = (X509Certificate) cf.generateCertificate(fis);
            String dn = cert.getSubjectX500Principal().getName(); // e.g., "CN=myserver.com, OU=..., O=..."
            return extractCN(dn);
        }
    }

    private static String extractCN(String dn) {
        // 解析 "CN=xxx" 的值
        for (String part : dn.split(",")) {
            part = part.trim();
            if (part.startsWith("CN=")) {
                return part.substring(3);
            }
        }
        return dn; // 没有找到 CN 就返回整个DN
    }

}

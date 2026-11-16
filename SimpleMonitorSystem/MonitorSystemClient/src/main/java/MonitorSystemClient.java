import io.grpc.ManagedChannel;
import io.grpc.netty.shaded.io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import com.alipay.antchain.bridge.ptc.committee.monitor.system.grpc.*;
import java.io.File;
import java.io.FileInputStream;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

public class MonitorSystemClient {

    public static void main(String[] args) throws Exception {
        String host = "localhost";
        int port = 50051;
        String cn = getCommonNameFromCert("tls_certs/monitor-system.crt");

        ManagedChannel channel = NettyChannelBuilder.forAddress(host, port)
                .sslContext(
                        GrpcSslContexts.forClient()
                                .trustManager(new File("tls_certs/monitor-system.crt"))
                                .build())
                .overrideAuthority(cn)
                .build();

        // 创建阻塞存根
        MonitorSystemServiceGrpc.MonitorSystemServiceBlockingStub stub =
                MonitorSystemServiceGrpc.newBlockingStub(channel);

        // 构造请求（这里用假数据）
        byte[] rawUcp = new byte[]{0x01, 0x02, 0x03, 0x04};  // 你可以替换成实际数据
        VerifyCrossChainMessageInMonitorSystemRequest request =
                VerifyCrossChainMessageInMonitorSystemRequest.newBuilder()
                        .setRawUcp(com.google.protobuf.ByteString.copyFrom(rawUcp))
                        .build();

        // 调用 gRPC 方法
        MonitorSystemResponse response = stub.verifyCrossChainMessageInMonitorSystem(request);

        // 打印返回
        System.out.println("收到响应:");
        System.out.println("code = " + response.getCode());
        System.out.println("errorMsg = " + response.getErrorMsg());
        if (response.hasVerifyCrossChainMessageInMonitorSystemResp()) {
            VerifyCrossChainMessageInMonitorSystemResponse resp = response.getVerifyCrossChainMessageInMonitorSystemResp();
            System.out.println("result = " + resp.getResult());
            System.out.println("msg = " + resp.getMsg());
        }

        // 关闭通道
        channel.shutdown();
    }

    private static String getCommonNameFromCert(String certPath) throws Exception {
        try (FileInputStream fis = new FileInputStream(certPath)) {
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            X509Certificate cert = (X509Certificate) cf.generateCertificate(fis);
            String dn = cert.getSubjectX500Principal().getName();
            for (String part : dn.split(",")) {
                part = part.trim();
                if (part.startsWith("CN=")) {
                    return part.substring(3);
                }
            }
            return dn;
        }
    }
}

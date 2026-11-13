package com.alipay.antchain.bridge.ptc.committee.monitor.node.client;

import java.io.InputStream;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import javax.naming.ldap.LdapName;
import javax.naming.ldap.Rdn;
import javax.security.auth.x500.X500Principal;
import java.util.function.Function;

import cn.hutool.core.util.StrUtil;
import com.alipay.antchain.bridge.ptc.committee.monitor.system.grpc.MonitorSystemServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.TlsChannelCredentials;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class MonitorSystemGrpcClientManager {

    private static final String CLIENT_NAME = "monitor-system";

    @Value("${grpc.clients.monitor-system.host:localhost}")
    private String host;

    @Value("${grpc.clients.monitor-system.port:50051}")
    private int port;

    @Value("${grpc.clients.monitor-system.security.monitorSystemCert}")
    private Resource monitorSystemCertResource;

    private final Object lock = new Object();

    private volatile ManagedChannel channel;

    private volatile MonitorSystemServiceGrpc.MonitorSystemServiceBlockingStub stub;

    private ManagedChannel createChannel(String clientName) {
        String commonName = getCertX509CommonName(monitorSystemCertResource);
        if (StrUtil.isEmpty(commonName)) {
            throw new RuntimeException("failed to get common name from x509 subject for monitor system");
        }

        try {
            TlsChannelCredentials.Builder tlsBuilder = TlsChannelCredentials.newBuilder();
            tlsBuilder.trustManager(monitorSystemCertResource.getInputStream());
            return NettyChannelBuilder.forAddress(host, port, tlsBuilder.build())
                    .overrideAuthority(commonName)
                    .build();
        } catch (Exception e) {
            throw new RuntimeException(String.format("failed to create client for %s", clientName), e);
        }
    }

    private MonitorSystemServiceGrpc.MonitorSystemServiceBlockingStub getOrCreateStub(String clientName) {
        if (stub != null && channel != null && !channel.isShutdown() && !channel.isTerminated()) {
            return stub;
        }
        synchronized (lock) {
            if (stub != null && channel != null && !channel.isShutdown() && !channel.isTerminated()) {
                return stub;
            }
            channel = createChannel(clientName);
            stub = MonitorSystemServiceGrpc.newBlockingStub(channel);
            return stub;
        }
    }

    private void resetStub(String clientName) {
        synchronized (lock) {
            if (channel != null) {
                log.warn("reset grpc channel for {}", clientName);
                channel.shutdownNow();
            }
            channel = null;
            stub = null;
        }
    }

    public <T> T withStub(Function<MonitorSystemServiceGrpc.MonitorSystemServiceBlockingStub, T> action) {
        return withStub(CLIENT_NAME, action);
    }

    public <T> T withStub(String clientName, Function<MonitorSystemServiceGrpc.MonitorSystemServiceBlockingStub, T> action) {
        try {
            return action.apply(getOrCreateStub(clientName));
        } catch (StatusRuntimeException e) {
            if (!shouldRetry(e)) {
                throw e;
            }
            log.warn("grpc call to {} failed with status {}, recreating channel and retrying once",
                    clientName, e.getStatus().getCode(), e);
            resetStub(clientName);
            return action.apply(getOrCreateStub(clientName));
        }
    }

    private static boolean shouldRetry(StatusRuntimeException e) {
        Status.Code code = e.getStatus().getCode();
        return code == Status.Code.UNAVAILABLE
                || code == Status.Code.CANCELLED
                || code == Status.Code.DEADLINE_EXCEEDED
                || code == Status.Code.INTERNAL
                || code == Status.Code.UNKNOWN;
    }

    private static String getCertX509CommonName(Resource certResource) {
        try (InputStream is = certResource.getInputStream()) {
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            X509Certificate cert = (X509Certificate) cf.generateCertificate(is);

            X500Principal principal = cert.getSubjectX500Principal();
            LdapName ldapName = new LdapName(principal.getName());
            for (Rdn rdn : ldapName.getRdns()) {
                if ("CN".equalsIgnoreCase(rdn.getType())) {
                    return rdn.getValue().toString();
                }
            }
            return null;
        } catch (Exception e) {
            throw new RuntimeException("Failed to get common name from certificate file", e);
        }
    }
}

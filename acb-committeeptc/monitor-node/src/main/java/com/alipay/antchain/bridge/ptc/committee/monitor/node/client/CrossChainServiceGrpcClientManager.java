package com.alipay.antchain.bridge.ptc.committee.monitor.node.client;

import java.io.InputStream;
import cn.hutool.core.util.StrUtil;
import com.alipay.antchain.bridge.pluginserver.service.CrossChainServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.TlsChannelCredentials;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import javax.naming.ldap.LdapName;
import javax.naming.ldap.Rdn;
import javax.security.auth.x500.X500Principal;
import java.util.function.Function;

@Component
@Slf4j
public class CrossChainServiceGrpcClientManager {

    private static final String CLIENT_NAME = "plugin-server";

    @Value("${grpc.clients.plugin-server.host}")
    private String host;

    @Value("${grpc.clients.plugin-server.port}")
    private int port;

    @Value("${grpc.clients.plugin-server.ps-id}")
    private String psId;

    @Value("${grpc.clients.plugin-server.security.pluginServerCert}")
    private Resource psCertResource;

    @Value("${grpc.clients.plugin-server.security.certificate-chain}")
    private Resource tlsCaResource;

    @Value("${grpc.clients.plugin-server.security.private-key}")
    private Resource tlsKeyResource;

    private final Object lock = new Object();

    private volatile ManagedChannel channel;

    private volatile CrossChainServiceGrpc.CrossChainServiceBlockingStub stub;

    private ManagedChannel createChannel(String clientName) {

        String commonName = getPluginServerCertX509CommonName(psCertResource);
        if (StrUtil.isEmpty(commonName)) {
            throw new RuntimeException(
                    String.format("failed to get common name from x509 subject for plugin server %s", psId)
            );
        }

        try {
            TlsChannelCredentials.Builder tlsBuilder = TlsChannelCredentials.newBuilder();
            tlsBuilder.keyManager(tlsCaResource.getInputStream(), tlsKeyResource.getInputStream());
            tlsBuilder.trustManager(psCertResource.getInputStream());
            return NettyChannelBuilder.forAddress(host, port, tlsBuilder.build())
                    .overrideAuthority(commonName)
                    .build();
        } catch (Exception e) {
            throw new RuntimeException(
                    String.format("failed to create client for %s", clientName),
                    e
            );
        }
    }

    private CrossChainServiceGrpc.CrossChainServiceBlockingStub getOrCreateStub(String clientName) {
        if (stub != null && channel != null && !channel.isShutdown() && !channel.isTerminated()) {
            return stub;
        }
        synchronized (lock) {
            if (stub != null && channel != null && !channel.isShutdown() && !channel.isTerminated()) {
                return stub;
            }
            channel = createChannel(clientName);
            stub = CrossChainServiceGrpc.newBlockingStub(channel);
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

    public <T> T withStub(Function<CrossChainServiceGrpc.CrossChainServiceBlockingStub, T> action) {
        return withStub(CLIENT_NAME, action);
    }

    public <T> T withStub(String clientName, Function<CrossChainServiceGrpc.CrossChainServiceBlockingStub, T> action) {
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

    private static String getPluginServerCertX509CommonName(Resource certResource) {
        try (InputStream is = certResource.getInputStream()) {
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            X509Certificate cert = (X509Certificate) cf.generateCertificate(is);

            X500Principal principal = cert.getSubjectX500Principal();
            String dn = principal.getName();

            LdapName ldapName = new LdapName(dn);
            String commonName = null;
            for (Rdn rdn : ldapName.getRdns()) {
                if ("CN".equalsIgnoreCase(rdn.getType())) {
                    commonName = rdn.getValue().toString();
                    break;
                }
            }
            return commonName;
        } catch (Exception e) {
            throw new RuntimeException("Failed to get common name from certificate file", e);
        }
    }
}

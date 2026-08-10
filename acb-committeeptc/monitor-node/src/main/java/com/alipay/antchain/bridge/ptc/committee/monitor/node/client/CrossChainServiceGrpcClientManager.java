package com.alipay.antchain.bridge.ptc.committee.monitor.node.client;

import java.io.FileInputStream;
import java.util.*;

import cn.hutool.core.util.StrUtil;
import com.alipay.antchain.bridge.pluginserver.service.CrossChainServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.TlsChannelCredentials;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import javax.naming.ldap.LdapName;
import javax.naming.ldap.Rdn;
import javax.security.auth.x500.X500Principal;
import java.io.InputStream;

@Component
public class CrossChainServiceGrpcClientManager {

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

    private final Map<String, CrossChainServiceGrpc.CrossChainServiceBlockingStub> blockingStubMap = new HashMap<>();

    public CrossChainServiceGrpc.CrossChainServiceBlockingStub createStub(String clientName) {

        String commonName = getPluginServerCertX509CommonName(psCertResource);
        if (StrUtil.isEmpty(commonName)) {
            throw new RuntimeException(
                    String.format("failed to get common name from x509 subject for plugin server %s", psId)
            );
        }

        ManagedChannel channel;
        try {
            TlsChannelCredentials.Builder tlsBuilder = TlsChannelCredentials.newBuilder();
            tlsBuilder.keyManager(tlsCaResource.getInputStream(), tlsKeyResource.getInputStream());
            tlsBuilder.trustManager(psCertResource.getInputStream());
            channel = NettyChannelBuilder.forAddress(host, port, tlsBuilder.build())
                    .overrideAuthority(commonName)
                    .build();
        } catch (Exception e) {
            throw new RuntimeException(
                    String.format("failed to create client for psId %s", psId)
            );
        }

        return CrossChainServiceGrpc.newBlockingStub(channel);
    }

    public CrossChainServiceGrpc.CrossChainServiceBlockingStub getStub(String clientName) {
        if (this.blockingStubMap.containsKey(clientName)) {
            return this.blockingStubMap.get(clientName);
        }
        this.blockingStubMap.put(clientName, createStub(clientName));
        return this.blockingStubMap.get(clientName);
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

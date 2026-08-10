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

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import cn.hutool.core.util.StrUtil;
import com.alipay.antchain.bridge.ptc.committee.monitor.node.server.grpc.*;
import com.google.protobuf.ByteString;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.shell.standard.*;

@Getter
@ShellCommandGroup(value = "Commands about core functions")
@ShellComponent
@Slf4j
public class CoreCommands extends BaseCommands {

    @Value("${grpc.client.admin.address:static://localhost:10088}")
    private String adminAddress;

    @GrpcClient("admin")
    private AdminServiceGrpc.AdminServiceBlockingStub adminServiceBlockingStub;

    @Override
    public boolean needAdminServer() {
        return true;
    }

    @ShellMethod(value = "Register a new BCDNS bound with specified domain space into Relayer")
    Object registerBCDNSService(
            @ShellOption(help = "The domain space owned by the BCDNS, default the root space \"\"", defaultValue = "") String domainSpace,
            @ShellOption(help = "The type of the BCDNS, e.g. embedded, bif") String bcdnsType,
            @ShellOption(valueProvider = FileValueProvider.class, help = "The properties file path needed to initialize the service stub, e.g. /path/to/bif_bcdns_conf.json") String propFile,
            @ShellOption(valueProvider = FileValueProvider.class, help = "The path to BCDNS trust root certificate file if you have it", defaultValue = "") String bcdnsCertPath
    ) {
        try {
            var resp = adminServiceBlockingStub.registerBcdnsService(
                    RegisterBcdnsServiceRequest.newBuilder()
                            .setDomainSpace(domainSpace)
                            .setBcdnsType(bcdnsType)
                            .setConfig(ByteString.copyFrom(Files.readAllBytes(Paths.get(propFile))))
                            .setBcdnsRootCert(StrUtil.isEmpty(bcdnsCertPath) ? "" : Files.readString(Paths.get(bcdnsCertPath)))
                            .build()
            );
            if (resp.getCode() != 0) {
                return "failed to register BCDNS service: " + resp.getErrorMsg();
            }
            return "success";
        } catch (Throwable t) {
            throw new RuntimeException("unexpected error please input stacktrace to check the detail", t);
        }
    }

    @ShellMethod(value = "Get the BCDNS data bound with specified domain space")
    Object getBCDNSService(@ShellOption(help = "The domain space bound with BCDNS, default the root space", defaultValue = "") String domainSpace) {
        try {
            var resp = adminServiceBlockingStub.getBcdnsServiceInfo(
                    GetBcdnsServiceInfoRequest.newBuilder()
                            .setDomainSpace(domainSpace)
                            .build()
            );
            if (resp.getCode() != 0) {
                return "failed to get BCDNS service info: " + resp.getErrorMsg();
            }
            return resp.getGetBcdnsServiceInfoResp().getInfoJson();
        } catch (Throwable t) {
            throw new RuntimeException("unexpected error please input stacktrace to check the detail", t);
        }
    }

    @ShellMethod(value = "Delete the BCDNS bound with specified domain space")
    Object deleteBCDNSService(@ShellOption(help = "The domain space bound with BCDNS, default the root space", defaultValue = "") String domainSpace) {
        try {
            var resp = adminServiceBlockingStub.deleteBcdnsService(
                    DeleteBcdnsServiceRequest.newBuilder()
                            .setDomainSpace(domainSpace)
                            .build()
            );
            if (resp.getCode() != 0) {
                return "failed to delete BCDNS service: " + resp.getErrorMsg();
            }
            return "success";
        } catch (Throwable t) {
            throw new RuntimeException("unexpected error please input stacktrace to check the detail", t);
        }
    }

    @ShellMethod(value = "Get the BCDNS trust root certificate bound with specified domain space")
    Object getBCDNSCertificate(@ShellOption(help = "The domain space bound with BCDNS, default the root space", defaultValue = "") String domainSpace) {
        try {
            var resp = adminServiceBlockingStub.getBcdnsCertificate(
                    GetBcdnsCertificateRequest.newBuilder()
                            .setDomainSpace(domainSpace)
                            .build()
            );
            if (resp.getCode() != 0) {
                return "failed to get BCDNS certificate: " + resp.getErrorMsg();
            }
            return resp.getGetBcdnsCertificateResp().getCertificate();
        } catch (Throwable t) {
            throw new RuntimeException("unexpected error please input stacktrace to check the detail", t);
        }
    }

    @ShellMethod(value = "Stop the local BCDNS service stub")
    Object stopBCDNSService(@ShellOption(help = "The domain space bound with BCDNS, default the root space", defaultValue = "") String domainSpace) {
        try {
            var resp = adminServiceBlockingStub.stopBcdnsService(
                    StopBcdnsServiceRequest.newBuilder()
                            .setDomainSpace(domainSpace)
                            .build()
            );
            if (resp.getCode() != 0) {
                return "failed to stop BCDNS: " + resp.getErrorMsg();
            }
            return "success";
        } catch (Throwable t) {
            throw new RuntimeException("unexpected error please input stacktrace to check the detail", t);
        }
    }

    @ShellMethod(value = "Restart the local BCDNS service stub from stop")
    Object restartBCDNSService(@ShellOption(help = "domain space, default the root space", defaultValue = "") String domainSpace) {
        try {
            var resp = adminServiceBlockingStub.restartBcdnsService(
                    RestartBcdnsServiceRequest.newBuilder()
                            .setDomainSpace(domainSpace)
                            .build()
            );
            if (resp.getCode() != 0) {
                return "failed to restart BCDNS: " + resp.getErrorMsg();
            }
            return "success";
        } catch (Throwable t) {
            throw new RuntimeException("unexpected error please input stacktrace to check the detail", t);
        }
    }

    @ShellMethod(value = "Add committee-ptc trust root manually")
    Object addPtcTrustRoot(
            @ShellOption(help = "file path leading to the serialized PTCTrustRoot which has been signed by supervisor")
            String rawPtcTrustRootFile
    ) {
        try {
            var filePath = Path.of(rawPtcTrustRootFile);
            if (!Files.exists(filePath)) {
                return "file not exists";
            }

            var resp = adminServiceBlockingStub.addPtcTrustRoot(
                    AddPtcTrustRootRequest.newBuilder()
                            .setRawTrustRoot(ByteString.copyFrom(Files.readAllBytes(filePath)))
                            .build()
            );
            if (resp.getCode() != 0) {
                return "failed to add ptc trust root: " + resp.getErrorMsg();
            }
            return "success";
        } catch (Throwable t) {
            throw new RuntimeException("unexpected error please input stacktrace to check the detail", t);
        }
    }
}

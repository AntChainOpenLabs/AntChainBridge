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

package com.alipay.antchain.bridge.ptc.committee.monitor.node.config;

import java.net.InetAddress;
import java.net.InetSocketAddress;

import cn.hutool.core.util.StrUtil;
import com.alipay.antchain.bridge.ptc.committee.monitor.node.server.AdminServiceImpl;
import io.grpc.Server;
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@Slf4j
public class ServerConfig {

    @Value("${committee.node.admin.host:localhost}")
    private String adminHost;

    @Value("${committee.node.admin.port:10088}")
    private int adminPort;

    @Bean
    @SneakyThrows
    public Server adminGrpcServer(@Autowired AdminServiceImpl adminService) {
        log.info("Starting plugin managing server on port {}", adminPort);
        return NettyServerBuilder.forAddress(
                        new InetSocketAddress(
                                StrUtil.isEmpty(adminHost) ? InetAddress.getLoopbackAddress() : InetAddress.getByName(adminHost),
                                adminPort
                        )
                ).addService(adminService)
                .build()
                .start();
    }
}

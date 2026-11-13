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

package com.alipay.antchain.bridge.ptc.committee.monitor.node.service.impl;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import ch.qos.logback.classic.AsyncAppender;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.rolling.RollingFileAppender;
import ch.qos.logback.core.rolling.SizeAndTimeBasedRollingPolicy;
import ch.qos.logback.core.util.FileSize;
import cn.hutool.core.collection.ListUtil;
import cn.hutool.core.util.ObjectUtil;
import com.alipay.antchain.bridge.plugins.manager.AntChainBridgePluginManagerFactory;
import com.alipay.antchain.bridge.plugins.manager.core.IAntChainBridgePlugin;
import com.alipay.antchain.bridge.plugins.manager.core.IAntChainBridgePluginManager;
import com.alipay.antchain.bridge.plugins.spi.ptc.IHeteroChainDataVerifierService;
import com.alipay.antchain.bridge.ptc.committee.monitor.node.service.IHcdvsPluginService;
import lombok.extern.slf4j.Slf4j;
import org.pf4j.ClassLoadingStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class HcdvsPluginServiceImpl implements IHcdvsPluginService {

    private final IAntChainBridgePluginManager manager;

    private final String hcdvsLoggerDir;

    @Value("${committee.plugin.log.hcdvs.max_history:3}")
    private int maxHCDVSLogHistory;

    @Value("${committee.plugin.log.hcdvs.max_file_size:30mb}")
    private String maxHCDVSLogFileSize;

    @Value("${committee.plugin.log.hcdvs.level:info}")
    private String hcdvsLogLevel;

    @Value("${committee.plugin.log.hcdvs.on:true}")
    private boolean isHCDVSLogOn;

    private final Map<String, Logger> hcdvsLoggerMap = new HashMap<>();

    public HcdvsPluginServiceImpl(
            @Value("${committee.plugin.repo}") String path,
            @Value("${logging.file.path}") String hcdvsLogDir,
            @Value("${committee.plugin.policy.classloader.resource.ban-with-prefix.APPLICATION:}") String[] resourceBannedPrefixOnAppLevel
    ) {
        log.info("plugins path: {}", Paths.get(path).toAbsolutePath());
        this.hcdvsLoggerDir = Paths.get(hcdvsLogDir, "hcdvs").toAbsolutePath().toString();
        log.info("hcdvs logger base dir: {}", Paths.get(hcdvsLoggerDir).toAbsolutePath());

        this.manager = AntChainBridgePluginManagerFactory.createPluginManager(
                path,
                ObjectUtil.defaultIfNull(convertPathPrefixBannedMap(resourceBannedPrefixOnAppLevel), new HashMap<>())
        );
        loadPlugins();
        startPlugins();
    }

    private Map<ClassLoadingStrategy.Source, Set<String>> convertPathPrefixBannedMap(
            String[] resourceBannedPrefixOnAppLevel
    ) {
        Map<ClassLoadingStrategy.Source, Set<String>> result = new HashMap<>();

        Set<String> appSet = new HashSet<>(ListUtil.of(resourceBannedPrefixOnAppLevel));
        result.put(ClassLoadingStrategy.Source.APPLICATION, appSet);

        return result;
    }

    @Override
    public void loadPlugins() {
        manager.loadPlugins();
    }

    @Override
    public void startPlugins() {
        manager.startPlugins();
    }

    @Override
    public void loadPlugin(String path) {
        manager.loadPlugin(Paths.get(path));
    }

    @Override
    public void startPlugin(String path) {
        manager.startPlugin(Paths.get(path));
    }

    @Override
    public void stopPlugin(String product) {
        manager.stopPlugin(product);
    }

    @Override
    public void startPluginFromStop(String product) {
        manager.startPluginFromStop(product);
    }

    @Override
    public void reloadPlugin(String product) {
        manager.reloadPlugin(product);
    }

    @Override
    public void reloadPlugin(String product, String path) {
        manager.reloadPlugin(product, Paths.get(path));
    }

    @Override
    public IAntChainBridgePlugin getPlugin(String product) {
        return manager.getPlugin(product);
    }

    @Override
    public boolean hasPlugin(String product) {
        return manager.hasPlugin(product);
    }

    @Override
    public IHeteroChainDataVerifierService createHCDVSService(String product) {
        return manager.createHCDVSService(product, createHCDVSServiceLogger(product));
    }

    @Override
    public IHeteroChainDataVerifierService getHCDVSService(String product) {
        var service = manager.getHCDVSService(product);
        if (ObjectUtil.isNotNull(service)) {
            return service;
        }
        return createHCDVSService(product);
    }

    private Logger createHCDVSServiceLogger(String product) {
        if (!isHCDVSLogOn) {
            return null;
        }
        String loggerName = product;
        if (hcdvsLoggerMap.containsKey(loggerName) && ObjectUtil.isNotNull(hcdvsLoggerMap.get(loggerName))) {
            return hcdvsLoggerMap.get(loggerName);
        }
        Path logFile = Paths.get(hcdvsLoggerDir, product + ".log");
        Logger logger = LoggerFactory.getLogger(loggerName);
        if (logger instanceof ch.qos.logback.classic.Logger) {
            log.debug("using logback for hcdvs logger");

            LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();

            PatternLayoutEncoder encoder = new PatternLayoutEncoder();
            encoder.setContext(context);
            encoder.setPattern("%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{50} - %msg%n");
            encoder.setCharset(StandardCharsets.UTF_8);
            encoder.start();

            SizeAndTimeBasedRollingPolicy rollingPolicy = new SizeAndTimeBasedRollingPolicy<>();
            rollingPolicy.setContext(context);
            rollingPolicy.setFileNamePattern(logFile + ".%d{yyyy-MM-dd}.%i");
            rollingPolicy.setMaxHistory(maxHCDVSLogHistory);
            rollingPolicy.setMaxFileSize(FileSize.valueOf(maxHCDVSLogFileSize));

            RollingFileAppender<ILoggingEvent> appender = new RollingFileAppender<>();
            appender.setContext(context);
            appender.setEncoder(encoder);
            appender.setFile(logFile.toString());
            appender.setRollingPolicy(rollingPolicy);

            rollingPolicy.setParent(appender);
            rollingPolicy.start();
            appender.start();

            AsyncAppender asyncAppender = new AsyncAppender();
            asyncAppender.setContext(context);
            asyncAppender.setName(loggerName);
            asyncAppender.addAppender(appender);
            asyncAppender.start();

            ch.qos.logback.classic.Logger loggerLogback = (ch.qos.logback.classic.Logger) logger;
            loggerLogback.setLevel(Level.toLevel(hcdvsLogLevel));
            loggerLogback.setAdditive(false);
            loggerLogback.addAppender(asyncAppender);

            hcdvsLoggerMap.put(loggerName, loggerLogback);
            log.info("hcdvs logger {} created", loggerName);

            return loggerLogback;
        }

        log.debug("logger library not support for now");
        return null;
    }

    @Override
    public boolean hasProduct(String product) {
        return manager.hasProduct(product);
    }

    @Override
    public List<String> getAvailableProducts() {
        return manager.allSupportProducts();
    }
}

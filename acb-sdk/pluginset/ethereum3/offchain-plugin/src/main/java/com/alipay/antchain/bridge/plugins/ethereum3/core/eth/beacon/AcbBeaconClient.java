package com.alipay.antchain.bridge.plugins.ethereum3.core.eth.beacon;

import java.io.IOException;
import java.math.BigInteger;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;

import cn.hutool.cache.Cache;
import cn.hutool.cache.CacheUtil;
import cn.hutool.core.collection.ListUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.TypeReference;
import com.alipay.antchain.bridge.plugins.ethereum3.conf.BlockHeightPolicyEnum;
import com.alipay.antchain.bridge.plugins.ethereum3.conf.EthereumConfig;
import com.alipay.antchain.bridge.plugins.ethereum3.core.eth.eth2spec.Eth2ChainConfig;
import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.Setter;
import lombok.SneakyThrows;
import org.apache.tuweni.bytes.Bytes32;
import org.slf4j.Logger;
import org.slf4j.helpers.NOPLogger;
import tech.pegasys.teku.infrastructure.json.JsonUtil;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlockHeader;
import tech.pegasys.teku.spec.datastructures.genesis.GenesisData;
import tech.pegasys.teku.spec.datastructures.lightclient.LightClientBootstrap;
import tech.pegasys.teku.spec.datastructures.lightclient.LightClientBootstrapSchema;
import tech.pegasys.teku.spec.datastructures.lightclient.LightClientUpdate;
import tech.pegasys.teku.spec.datastructures.lightclient.LightClientUpdateSchema;

public class AcbBeaconClient implements BeaconNodeApi {

    private final HttpClient httpClient;

    private String beaconUrl;

    private Map<String, Set<BeaconCallTypeEnum>> beaconNodeApiMap;

    private final Logger bbcLogger;

    @Setter
    private Eth2ChainConfig eth2ChainConfig;

    private final Cache<BigInteger, SignedBeaconBlock> signedBeaconBlockCache = CacheUtil.newFIFOCache(128);

    private final Cache<BigInteger, SignedBeaconBlock> signedBlindedBeaconBlockCache = CacheUtil.newFIFOCache(128);

    @SneakyThrows
    public AcbBeaconClient(EthereumConfig config, Logger bbcLogger) {
        httpClient = HttpClient.newBuilder().build();
        if (ObjectUtil.isEmpty(config.getBeaconNodeApiMap())) {
            bbcLogger.info("use single beacon node api url: {}", config.getBeaconApiUrl());
            beaconUrl = config.getBeaconApiUrl();
        } else {
            if (new HashSet<>(config.getBeaconNodeApiMap().values()).size() != BeaconCallTypeEnum.values().length) {
                throw new RuntimeException("beacon node api map is not valid, please check it: " + JSON.toJSONString(config.getBeaconNodeApiMap()));
            }
            beaconNodeApiMap = config.getBeaconNodeApiMap();
        }

        this.bbcLogger = ObjectUtil.isNull(bbcLogger) ? NOPLogger.NOP_LOGGER : bbcLogger;
        this.eth2ChainConfig = config.getEth2ChainConfig();
    }

    public AcbBeaconClient(String beaconUrl) {
        httpClient = HttpClient.newBuilder().build();
        this.beaconUrl = beaconUrl;
        bbcLogger = NOPLogger.NOP_LOGGER;
        beaconNodeApiMap = new HashMap<>();
    }

    @Override
    public SignedBeaconBlock getWholeBlockBySlot(BigInteger slot) {
        try {
            bbcLogger.debug("get beacon block by slot: {}", slot);
            var block = signedBeaconBlockCache.get(slot);
            if (ObjectUtil.isNotNull(block) && block.getBeaconBlock().isPresent()) {
                bbcLogger.info("get beacon block {} from cache", slot);
                return block;
            }

            var resp = httpClient.send(
                    HttpRequest.newBuilder()
                            .GET()
                            .uri(URI.create(buildGetBlockV2Url(getBeaconUrlForBlock(), slot.toString())))
                            .header("accept", "application/json")
                            .build(),
                    HttpResponse.BodyHandlers.ofString()
            );
            switch (resp.statusCode()) {
                case 200 -> {
                    var body = JSON.parseObject(resp.body(), ObjectAndMetaData.class);
                    if (!body.getFinalized()) {
                        bbcLogger.info("note that block is not finalized, slot: {}", slot);
                    }
                    block = JsonUtil.parse(body.getData(), eth2ChainConfig.getCurrentSchemaDefinitions(slot).getSignedBeaconBlockSchema().getJsonTypeDefinition());
                    if (!signedBeaconBlockCache.containsKey(slot) && ObjectUtil.isNotNull(block)) {
                        signedBeaconBlockCache.put(slot, block);
                    }
                    return block;
                }
                case 404 -> {
                    bbcLogger.warn("block not found, slot: {}, msg: {}", slot, resp.body());
                    return null;
                }
                default ->
                        throw new RuntimeException(StrUtil.format("get beacon block request failed, slot: {}, msg: {}", slot, resp.body()));
            }
        } catch (IOException | InterruptedException e) {
            bbcLogger.error("unexpected error when get block by slot {}", slot, e);
            throw new RuntimeException(StrUtil.format("unexpected error when get block by slot {}", slot), e);
        }
    }

    @Override
    public SignedBeaconBlock getBlindedBlockBySlot(BigInteger slot) {
        try {
            bbcLogger.debug("get blinded beacon block by slot: {}", slot);
            var block = signedBlindedBeaconBlockCache.get(slot);
            if (ObjectUtil.isNotNull(block) && block.getBeaconBlock().isPresent()) {
                bbcLogger.info("get blinded beacon block {} from cache", slot);
                return block;
            }

            var resp = httpClient.send(
                    HttpRequest.newBuilder()
                            .GET()
                            .uri(URI.create(buildGetBlindedBlockUrl(getBeaconUrlForBlock(), slot.toString())))
                            .header("accept", "application/json")
                            .build(),
                    HttpResponse.BodyHandlers.ofString()
            );
            switch (resp.statusCode()) {
                case 200 -> {
                    var body = JSON.parseObject(resp.body(), ObjectAndMetaData.class);
                    if (!body.getFinalized()) {
                        bbcLogger.info("note that blinded block is not finalized, slot: {}", slot);
                    }
                    block = JsonUtil.parse(body.getData(), eth2ChainConfig.getCurrentSchemaDefinitions(slot).getSignedBlindedBeaconBlockSchema().getJsonTypeDefinition());
                    if (!signedBlindedBeaconBlockCache.containsKey(slot) && ObjectUtil.isNotNull(block)) {
                        signedBlindedBeaconBlockCache.put(slot, block);
                    }
                    return block;
                }
                case 404 -> {
                    bbcLogger.warn("blinded block not found, slot: {}, msg: {}", slot, resp.body());
                    return null;
                }
                default ->
                        throw new RuntimeException(StrUtil.format("get blinded beacon block request failed, slot: {}, msg: {}", slot, resp.body()));
            }
        } catch (IOException | InterruptedException e) {
            bbcLogger.error("unexpected error when get blinded block by slot {}", slot, e);
            throw new RuntimeException(StrUtil.format("unexpected error when get blinded block by slot {}", slot), e);
        }
    }

    @Override
    public LightClientBootstrap getLightClientBootstrap(String blockRoot) {
        try {
            bbcLogger.info("get light client bootstrap by block root: {}", blockRoot);

            var resp = httpClient.send(
                    HttpRequest.newBuilder()
                            .GET()
                            .uri(URI.create(buildGetLightClientBootstrapUrl(getBeaconUrlForLightClientBootstrap(), blockRoot)))
                            .header("accept", "application/json")
                            .build(),
                    HttpResponse.BodyHandlers.ofString()
            );
            switch (resp.statusCode()) {
                case 200 -> {
                    var body = JSON.parseObject(resp.body(), ObjectAndMetaData.class);
                    bbcLogger.info("successful to get light client bootstrap by block root: {}", blockRoot);
                    bbcLogger.debug("light client bootstrap: {}", body.getData());
                    return JsonUtil.parse(body.getData(), new LightClientBootstrapSchema(eth2ChainConfig.getSpecConfig()).getJsonTypeDefinition());
                }
                case 404 -> {
                    bbcLogger.error("bootstrap not found with block root {}: {}", blockRoot, resp.body());
                    return null;
                }
                default ->
                        throw new RuntimeException(StrUtil.format("failed to get bootstrap with block root {}, msg: {}", blockRoot, resp.body()));
            }
        } catch (IOException | InterruptedException e) {
            bbcLogger.error("unexpected error when get bootstrap by block root {}", blockRoot, e);
            throw new RuntimeException(StrUtil.format("unexpected error when get bootstrap by block root {}", blockRoot), e);
        }
    }

    @Override
    public List<LightClientUpdate> getLightClientUpdates(long period, int count) {
        try {
            bbcLogger.info("get light client update for period {} and count {}", period, count);
            var resp = httpClient.send(
                    HttpRequest.newBuilder()
                            .GET()
                            .uri(URI.create(buildGetLightClientUpdateUrl(getBeaconUrlForLightClientUpdate(), period, count)))
                            .header("accept", "application/json")
                            .build(),
                    HttpResponse.BodyHandlers.ofString()
            );
            switch (resp.statusCode()) {
                case 200 -> {
                    var jsonArr = JSON.parseArray(resp.body());
                    bbcLogger.info("successful to get light client update for period {} and count {}", period, count);
                    bbcLogger.debug("light client updates: {}", resp.body());
                    return jsonArr.stream().map(x -> (JSONObject) x).map(x -> x.toJavaObject(ObjectAndMetaData.class).getData())
                            .map(x -> {
                                try {
                                    return JsonUtil.parse(x, new LightClientUpdateSchema(this.eth2ChainConfig.getSpecConfig()).getJsonTypeDefinition());
                                } catch (JsonProcessingException e) {
                                    throw new RuntimeException(e);
                                }
                            }).toList();
                }
                case 404 -> {
                    bbcLogger.error("updates not found with period {} and count {}: {}", period, count, resp.body());
                    return ListUtil.empty();
                }
                default ->
                        throw new RuntimeException(StrUtil.format("updates not found with period {} and count {}: {}", period, count, resp.body()));
            }
        } catch (IOException | InterruptedException e) {
            bbcLogger.error("unexpected error when get light client updates with period {} and count {}", period, count, e);
            throw new RuntimeException(
                    StrUtil.format("unexpected error when get light client updates with period {} and count {}", period, count), e);
        }
    }

    @Override
    public LightClientUpdate getLightClientUpdate(BigInteger slot) {
        var updates = this.getLightClientUpdates(slot.divide(BigInteger.valueOf(this.eth2ChainConfig.getSyncPeriodLength())).longValue(), 1);
        if (ObjectUtil.isEmpty(updates)) {
            return null;
        }
        return updates.getFirst();
    }

    @Override
    public BigInteger getLatestFinalizedSlot() {
        try {
            bbcLogger.debug("getting latest finalized slot now...");
            var resp = httpClient.send(
                    HttpRequest.newBuilder()
                            .GET()
                            .uri(URI.create(buildGetBlockHeaderUrl(getBeaconUrlForBlock(), BlockHeightPolicyEnum.FINALIZED.getDefaultBlockParameterName().getValue())))
                            .header("accept", "application/json")
                            .build(),
                    HttpResponse.BodyHandlers.ofString()
            );
            switch (resp.statusCode()) {
                case 200 -> {
                    var body = JSON.parseObject(resp.body(), ObjectAndMetaData.class);
                    if (!body.getFinalized()) {
                        throw new RuntimeException("unexpected that result shows not finalized");
                    }
                    var data = JSON.parseObject(body.getData());
                    bbcLogger.debug("get beacon block header {}", data.getString("root"));
                    return JsonUtil.parse(data.getString("header"), SignedBeaconBlockHeader.SSZ_SCHEMA.getJsonTypeDefinition())
                            .getMessage().getSlot().bigIntegerValue();
                }
                default -> throw new RuntimeException(StrUtil.format("failed to get latest beacon block header: {}", resp.body()));
            }
        } catch (IOException | InterruptedException e) {
            bbcLogger.error("unexpected error when get latest beacon block header:", e);
            throw new RuntimeException(
                    StrUtil.format("unexpected error when get latest beacon block header:"), e);
        }
    }

    @Override
    public GenesisData getGenesisData() {
        try {
            bbcLogger.debug("getting genesis data now...");
            var resp = httpClient.send(
                    HttpRequest.newBuilder()
                            .GET()
                            .uri(URI.create(buildGetGenesisUrl(getBeaconUrlForGenesis())))
                            .header("accept", "application/json")
                            .build(),
                    HttpResponse.BodyHandlers.ofString()
            );
            switch (resp.statusCode()) {
                case 200 -> {
                    var body = JSON.parseObject(resp.body(), ObjectAndMetaData.class);
                    var data = JSON.parseObject(body.getData());
                    return GenesisData.builder()
                            .genesisTime(UInt64.valueOf(data.getString("genesis_time")))
                            .genesisValidatorsRoot(Bytes32.fromHexString(data.getString("genesis_validators_root")))
                            .build();
                }
                default -> throw new RuntimeException(StrUtil.format("failed to get genesis data: {}", resp.body()));
            }
        } catch (IOException | InterruptedException e) {
            bbcLogger.error("unexpected error when get genesis data:", e);
            throw new RuntimeException(
                    StrUtil.format("unexpected error when get genesis data:"), e);
        }
    }

    @Override
    public Map<String, String> getRemoteSpec() {
        try {
            bbcLogger.debug("getting spec config now...");
            var resp = httpClient.send(
                    HttpRequest.newBuilder()
                            .GET()
                            .uri(URI.create(buildGetSpecUrl(getBeaconUrlForSpec())))
                            .header("accept", "application/json")
                            .build(),
                    HttpResponse.BodyHandlers.ofString()
            );
            switch (resp.statusCode()) {
                case 200 -> {
                    var body = JSON.parseObject(resp.body(), ObjectAndMetaData.class);
                    return JSON.parseObject(body.getData(), new TypeReference<>(){});
                }
                default -> throw new RuntimeException(StrUtil.format("failed to spec config: {}", resp.body()));
            }
        } catch (IOException | InterruptedException e) {
            bbcLogger.error("unexpected error when get spec config:", e);
            throw new RuntimeException(
                    StrUtil.format("unexpected error when get spec config:"), e);
        }
    }

    @Override
    public void shutdown() {
        this.httpClient.shutdown();
    }

    private String getBeaconUrlForBlock() {
        return getBeaconUrl(BeaconCallTypeEnum.GET_BLOCK);
    }

    private String getBeaconUrlForLightClientBootstrap() {
        return getBeaconUrl(BeaconCallTypeEnum.LIGHT_CLIENT_BOOTSTRAP);
    }

    private String getBeaconUrlForLightClientUpdate() {
        return getBeaconUrl(BeaconCallTypeEnum.LIGHT_CLIENT_UPDATE);
    }

    private String getBeaconUrlForGenesis() {
        return getBeaconUrl(BeaconCallTypeEnum.GET_GENESIS);
    }

    private String getBeaconUrlForSpec() {
        return getBeaconUrl(BeaconCallTypeEnum.GET_SPEC);
    }

    private String getBeaconUrl(BeaconCallTypeEnum callType) {
        if (StrUtil.isNotEmpty(this.beaconUrl)) {
            return beaconUrl;
        }
        for (Map.Entry<String, Set<BeaconCallTypeEnum>> entry : this.beaconNodeApiMap.entrySet()) {
            if (entry.getValue().contains(callType)) {
                return entry.getKey();
            }
        }
        throw new RuntimeException("no beacon node found for " + callType.name());
    }
}

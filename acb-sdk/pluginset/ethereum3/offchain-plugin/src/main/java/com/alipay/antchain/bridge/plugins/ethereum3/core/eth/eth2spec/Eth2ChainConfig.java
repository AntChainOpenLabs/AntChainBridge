package com.alipay.antchain.bridge.plugins.ethereum3.core.eth.eth2spec;

import java.math.BigInteger;
import java.util.*;

import cn.hutool.core.map.MapUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.annotation.JSONField;
import com.alipay.antchain.bridge.plugins.ethereum3.conf.Eth2NetworkEnum;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import org.web3j.utils.Numeric;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.Spec;
import tech.pegasys.teku.spec.SpecFactory;
import tech.pegasys.teku.spec.config.SpecConfig;
import tech.pegasys.teku.spec.config.SpecConfigDeneb;
import tech.pegasys.teku.spec.config.SpecConfigLoader;
import tech.pegasys.teku.spec.config.SpecConfigReader;
import tech.pegasys.teku.spec.schemas.SchemaDefinitions;

@Getter
@Setter
@AllArgsConstructor
public class Eth2ChainConfig {
    public static Eth2ChainConfig MAINNET_CHAIN_CONFIG = new Eth2ChainConfig(
            Eth2NetworkEnum.MAINNET,
            1606824023,
            Numeric.hexStringToByteArray("0x4b363db94e286120d76eb905340fdd4e54bfe9f06bf33ff6cf5ad27f511bfe95")
    );

    public static Eth2ChainConfig SEPOLIA_CHAIN_CONFIG = new Eth2ChainConfig(
            Eth2NetworkEnum.SEPOLIA,
            1655733600,
            Numeric.hexStringToByteArray("0xd8ea171f3c94aea21ebc42a1ed61052acf3f9209c00e4efbaaddac09ed9b8078")
    );

    public static Eth2ChainConfig HOLESKY_CHAIN_CONFIG = new Eth2ChainConfig(
            Eth2NetworkEnum.HOLESKY,
            1695902400,
            Numeric.hexStringToByteArray("0x9143aa7c615a7f7115e2b6aac319c03529df8242ae705fba9df39b79c59fa8b1")
    );

    static {
        MAINNET_CHAIN_CONFIG.addFork("GENESIS", BigInteger.valueOf(0), new byte[]{0, 0, 0, 0})
                .addFork("ALTAIR", BigInteger.valueOf(74240), new byte[]{1, 0, 0, 0})
                .addFork("BELLATRIX", BigInteger.valueOf(144896), new byte[]{2, 0, 0, 0})
                .addFork("CAPELLA", BigInteger.valueOf(194048), new byte[]{3, 0, 0, 0})
                .addFork("DENEB", BigInteger.valueOf(269568), new byte[]{4, 0, 0, 0})
                .setSpecConfig(SpecConfigLoader.loadConfig(Eth2NetworkEnum.MAINNET.getConfigName()))
                .setSpec(SpecFactory.create(Eth2NetworkEnum.MAINNET.getConfigName()));
        SEPOLIA_CHAIN_CONFIG.addFork("GENESIS", BigInteger.valueOf(0), new byte[]{(byte) 144, 0, 0, 105})
                .addFork("ALTAIR", BigInteger.valueOf(50), new byte[]{(byte) 144, 0, 0, 112})
                .addFork("BELLATRIX", BigInteger.valueOf(100), new byte[]{(byte) 144, 0, 0, 113})
                .addFork("CAPELLA", BigInteger.valueOf(56832), new byte[]{(byte) 144, 0, 0, 114})
                .addFork("DENEB", BigInteger.valueOf(132608), new byte[]{(byte) 144, 0, 0, 115})
                .setSpecConfig(SpecConfigLoader.loadConfig(Eth2NetworkEnum.SEPOLIA.getConfigName()))
                .setSpec(SpecFactory.create(Eth2NetworkEnum.SEPOLIA.getConfigName()));
        HOLESKY_CHAIN_CONFIG.addFork("GENESIS", BigInteger.valueOf(0), new byte[]{1, 1, 112, 0})
                .addFork("ALTAIR", BigInteger.valueOf(0), new byte[]{2, 1, 112, 0})
                .addFork("BELLATRIX", BigInteger.valueOf(0), new byte[]{3, 1, 112, 0})
                .addFork("CAPELLA", BigInteger.valueOf(256), new byte[]{4, 1, 112, 0})
                .addFork("DENEB", BigInteger.valueOf(29696), new byte[]{5, 1, 112, 0})
                .setSpecConfig(SpecConfigLoader.loadConfig(Eth2NetworkEnum.HOLESKY.getConfigName()))
                .setSpec(SpecFactory.create(Eth2NetworkEnum.HOLESKY.getConfigName()));
    }

    //# [New in Electra:EIP7251]
    //MIN_PER_EPOCH_CHURN_LIMIT_ELECTRA: 128000000000 # 2**7 * 10**9 (= 128,000,000,000)
    //MAX_PER_EPOCH_ACTIVATION_EXIT_CHURN_LIMIT: 256000000000 # 2**8 * 10**9 (= 256,000,000,000)
    private static final Map<String, String> MAINNET_ELECTRA_EXTRA_PARAMS = MapUtil.builder(new HashMap<String, String>())
            .put("MIN_PER_EPOCH_CHURN_LIMIT_ELECTRA", "128000000000")
            .put("MAX_PER_EPOCH_ACTIVATION_EXIT_CHURN_LIMIT", "256000000000")
            .build();

    //# [New in Electra:EIP7251]
    //MIN_PER_EPOCH_CHURN_LIMIT_ELECTRA: 64000000000 # 2**6 * 10**9 (= 64,000,000,000)
    //MAX_PER_EPOCH_ACTIVATION_EXIT_CHURN_LIMIT: 128000000000 # 2**7 * 10**9 (= 128,000,000,000)
    private static final Map<String, String> MINIMAL_ELECTRA_EXTRA_PARAMS = MapUtil.builder(new HashMap<String, String>())
            .put("MIN_PER_EPOCH_CHURN_LIMIT_ELECTRA", "64000000000")
            .put("MAX_PER_EPOCH_ACTIVATION_EXIT_CHURN_LIMIT", "128000000000")
            .build();

    public static boolean hasPresetChainConfig(Eth2NetworkEnum eth2Network) {
        return eth2Network == Eth2NetworkEnum.MAINNET || eth2Network == Eth2NetworkEnum.SEPOLIA || eth2Network == Eth2NetworkEnum.HOLESKY;
    }

    public static Eth2ChainConfig getPresetChainConfig(Eth2NetworkEnum eth2Network) {
        return switch (eth2Network) {
            case MAINNET -> MAINNET_CHAIN_CONFIG;
            case SEPOLIA -> SEPOLIA_CHAIN_CONFIG;
            case HOLESKY -> HOLESKY_CHAIN_CONFIG;
            default ->
                    throw new IllegalArgumentException(StrUtil.format("unsupported network {} for preset chain config", eth2Network.getConfigName()));
        };
    }

    public static Eth2ChainConfig createChainConfigFromJson(String json) {
        var config = JSON.parseObject(json, Eth2ChainConfig.class);
        try {
            return getPresetChainConfig(config.getNetworkName());
        } catch (IllegalArgumentException ignored) {
        }

        if (ObjectUtil.isEmpty(config.getRemoteSpecConfig())) {
            throw new IllegalArgumentException("private net needs `remote_spec_config` but get empty one");
        }
        if (!config.getRemoteSpecConfig().containsKey("MIN_PER_EPOCH_CHURN_LIMIT_ELECTRA")) {
            config.getRemoteSpecConfig().putAll(getExtraElectraParams(
                    Eth2NetworkEnum.fromString(config.getRemoteSpecConfig().getOrDefault(SpecConfigReader.PRESET_KEY, "mainnet"))
            ));
        }

        var specConfig = (SpecConfigDeneb) SpecConfigLoader.loadRemoteConfig(config.getRemoteSpecConfig());
        config.setSpecConfig(specConfig);
        config.setSpec(SpecFactory.create(specConfig));

        config.setForks(new ArrayList<>());
        config.addFork("GENESIS", specConfig.getGenesisDelay().bigIntegerValue(), specConfig.getGenesisForkVersion().getWrappedBytes().toArray())
                .addFork("ALTAIR", specConfig.getAltairForkEpoch().bigIntegerValue(), specConfig.getAltairForkVersion().getWrappedBytes().toArray())
                .addFork("BELLATRIX", specConfig.getBellatrixForkEpoch().bigIntegerValue(), specConfig.getBellatrixForkVersion().getWrappedBytes().toArray())
                .addFork("CAPELLA", specConfig.getCapellaForkEpoch().bigIntegerValue(), specConfig.getCapellaForkVersion().getWrappedBytes().toArray())
                .addFork("DENEB", specConfig.getDenebForkEpoch().bigIntegerValue(), specConfig.getDenebForkVersion().getWrappedBytes().toArray());

        var forksJsonArr = JSON.parseObject(json).getJSONArray("extra_forks");
        if (ObjectUtil.isNotEmpty(forksJsonArr)) {
            config.getForks().addAll(forksJsonArr.stream().map(x -> (JSONObject) x).map(x -> x.toJavaObject(Fork.class)).toList());
        }

        if (ObjectUtil.isEmpty(config.getGenesisValidatorsRoot()) || ObjectUtil.isEmpty(config.getForks())) {
            throw new IllegalArgumentException("invalid eth2 chain config, genesisValidatorsRoot and forks are required");
        }
        config.getForks().sort(Comparator.comparing(Fork::getEpoch));
        config.getForks().forEach(fork -> fork.computeDomain(config.getGenesisValidatorsRoot()));

        return config;
    }

    private static Map<String, String> getExtraElectraParams(Eth2NetworkEnum presetNetType) {
        return switch (presetNetType) {
            case SWIFT, MINIMAL -> MINIMAL_ELECTRA_EXTRA_PARAMS;
            default -> MAINNET_ELECTRA_EXTRA_PARAMS;
        };
    }

    public Eth2ChainConfig(Eth2NetworkEnum networkName, long genesisTime, byte[] genesisValidatorsRoot) {
        this.networkName = networkName;
        this.genesisTime = genesisTime;
        this.genesisValidatorsRoot = genesisValidatorsRoot;
    }

    public Eth2ChainConfig(Eth2NetworkEnum networkName, long genesisTime, byte[] genesisValidatorsRoot, Map<String, String> remoteSpecConfig) {
        this.networkName = networkName;
        this.genesisTime = genesisTime;
        this.genesisValidatorsRoot = genesisValidatorsRoot;
        this.remoteSpecConfig = remoteSpecConfig;

        if (!this.getRemoteSpecConfig().containsKey("MIN_PER_EPOCH_CHURN_LIMIT_ELECTRA")) {
            this.getRemoteSpecConfig().putAll(getExtraElectraParams(
                    Eth2NetworkEnum.fromString(this.getRemoteSpecConfig().getOrDefault(SpecConfigReader.PRESET_KEY, "mainnet"))
            ));
        }

        var specConfig = (SpecConfigDeneb) SpecConfigLoader.loadRemoteConfig(this.getRemoteSpecConfig());
        this.setSpecConfig(specConfig);
        this.setSpec(SpecFactory.create(specConfig));

        this.addFork("GENESIS", specConfig.getGenesisDelay().bigIntegerValue(), specConfig.getGenesisForkVersion().getWrappedBytes().toArray())
                .addFork("ALTAIR", specConfig.getAltairForkEpoch().bigIntegerValue(), specConfig.getAltairForkVersion().getWrappedBytes().toArray())
                .addFork("BELLATRIX", specConfig.getBellatrixForkEpoch().bigIntegerValue(), specConfig.getBellatrixForkVersion().getWrappedBytes().toArray())
                .addFork("CAPELLA", specConfig.getCapellaForkEpoch().bigIntegerValue(), specConfig.getCapellaForkVersion().getWrappedBytes().toArray())
                .addFork("DENEB", specConfig.getDenebForkEpoch().bigIntegerValue(), specConfig.getDenebForkVersion().getWrappedBytes().toArray());

        if (ObjectUtil.isEmpty(this.getGenesisValidatorsRoot()) || ObjectUtil.isEmpty(this.getForks())) {
            throw new IllegalArgumentException("invalid eth2 chain config, genesisValidatorsRoot and forks are required");
        }
        this.getForks().sort(Comparator.comparing(Fork::getEpoch));
        this.getForks().forEach(fork -> fork.computeDomain(this.getGenesisValidatorsRoot()));
    }

    @JSONField(name = "network_name")
    private Eth2NetworkEnum networkName;

    /**
     * Unix timestamp of slot 0
     */
    @JSONField(name = "genesis_time")
    private long genesisTime;

    /**
     * Root hash of the genesis validator set, used for signature domain calculation
     */
    @JSONField(name = "genesis_validators_root")
    private byte[] genesisValidatorsRoot;

    @JSONField(name = "forks")
    private List<Fork> forks;

    @JSONField(name = "remote_spec_config")
    private Map<String, String> remoteSpecConfig;

    @JSONField(serialize = false, deserialize = false)
    private Spec spec;

    @JSONField(serialize = false, deserialize = false)
    private SpecConfigDeneb specConfig;

    @JSONField(serialize = false, deserialize = false)
    public Fork getForkBySlot(BigInteger slot) {
        return getForkByEpoch(slot.divide(BigInteger.valueOf(this.specConfig.getSlotsPerEpoch())));
    }

    @JSONField(serialize = false, deserialize = false)
    public Fork getForkByEpoch(BigInteger epoch) {
        for (int i = forks.size() - 1; i >= 0; i--) {
            if (forks.get(i).getEpoch().compareTo(epoch) <= 0) {
                return forks.get(i);
            }
        }
        return null;
    }

    public Eth2ChainConfig addFork(String name, BigInteger epoch, byte[] version) {
        if (ObjectUtil.isNull(forks)) {
            this.forks = new ArrayList<>();
        }
        Fork fork = new Fork(name, epoch, version);
        fork.computeDomain(this.genesisValidatorsRoot);
        forks.add(fork);
        return this;
    }

    public Eth2ChainConfig setSpec(Spec spec) {
        this.spec = spec;
        return this;
    }

    @JSONField(serialize = false, deserialize = false)
    public Spec getSpec() {
        if (spec == null) {
            spec = SpecFactory.create(this.networkName.getConfigName());
            return spec;
        }
        return spec;
    }

    public Eth2ChainConfig setSpecConfig(SpecConfig specConfig) {
        this.specConfig = (SpecConfigDeneb) specConfig;
        return this;
    }

    public SchemaDefinitions getCurrentSchemaDefinitions(BigInteger slot) {
        return this.getSpec().atSlot(UInt64.valueOf(slot)).getSchemaDefinitions();
    }

    @JSONField(serialize = false, deserialize = false)
    public int getSyncCommitteeSuperMajority() {
        return (this.specConfig.getSyncCommitteeSize() * 2 + 2) / 3;
    }

    @JSONField(serialize = false, deserialize = false)
    public int getSyncPeriodLength() {
        return this.specConfig.getEpochsPerSyncCommitteePeriod() * this.specConfig.getSlotsPerEpoch();
    }

    @JSONField(serialize = false, deserialize = false)
    public int getSyncCommitteeSize() {
        return this.specConfig.getSyncCommitteeSize();
    }

    @JSONField(serialize = false, deserialize = false)
    public int getSyncCommitteeBranchLength() {
        return this.specConfig.getSyncCommitteeBranchLength();
    }

    @JSONField(serialize = false, deserialize = false)
    public int getEpochLength() {
        return this.specConfig.getSlotsPerEpoch();
    }

    @JSONField(serialize = false, deserialize = false)
    public int getFinalityBranchLength() {
        return this.specConfig.getFinalityBranchLength();
    }
}

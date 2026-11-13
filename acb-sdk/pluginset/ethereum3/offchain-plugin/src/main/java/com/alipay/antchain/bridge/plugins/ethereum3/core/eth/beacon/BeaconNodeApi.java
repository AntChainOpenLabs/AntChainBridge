package com.alipay.antchain.bridge.plugins.ethereum3.core.eth.beacon;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;

import tech.pegasys.teku.spec.datastructures.blocks.SignedBeaconBlock;
import tech.pegasys.teku.spec.datastructures.genesis.GenesisData;
import tech.pegasys.teku.spec.datastructures.lightclient.LightClientBootstrap;
import tech.pegasys.teku.spec.datastructures.lightclient.LightClientUpdate;

public interface BeaconNodeApi {

    enum BeaconCallTypeEnum {
        GET_BLOCK,

        LIGHT_CLIENT_BOOTSTRAP,

        LIGHT_CLIENT_UPDATE,

        GET_GENESIS,

        GET_SPEC
    }

    default String buildGetBlockV2Url(String beaconUrl, String param) {
        return String.format("%s/eth/v2/beacon/blocks/%s", beaconUrl, param);
    }

    default String buildGetLightClientBootstrapUrl(String beaconUrl, String blockRootHex) {
        return String.format("%s/eth/v1/beacon/light_client/bootstrap/%s", beaconUrl, blockRootHex);
    }

    default String buildGetLightClientUpdateUrl(String beaconUrl, long period, int count) {
        return String.format("%s/eth/v1/beacon/light_client/updates?start_period=%d&count=%d", beaconUrl, period, count);
    }

    default String buildGetBlockHeaderUrl(String beaconUrl, String param) {
        return String.format("%s/eth/v1/beacon/headers/%s", beaconUrl, param);
    }

    default String buildGetBlindedBlockUrl(String beaconUrl, String param) {
        return String.format("%s/eth/v1/beacon/blinded_blocks/%s", beaconUrl, param);
    }

    default String buildGetGenesisUrl(String beaconUrl) {
        return String.format("%s/eth/v1/beacon/genesis", beaconUrl);
    }

    default String buildGetSpecUrl(String beaconUrl) {
        return String.format("%s/eth/v1/config/spec", beaconUrl);
    }

    /**
     * Get whole beacon block. Refer to <a href="https://ethereum.github.io/beacon-APIs/#/Beacon/getBlockV2">here</a>
     *
     * @param slot block slot
     * @return block data
     */
    SignedBeaconBlock getWholeBlockBySlot(BigInteger slot);

    /**
     * Get blinded block by slot. Refer to <a href="https://ethereum.github.io/beacon-APIs/#/Beacon/getBlindedBlock">here</a>
     * @param slot block slot
     * @return blinded block data
     */
    SignedBeaconBlock getBlindedBlockBySlot(BigInteger slot);

    /**
     * Get light client bootstrap info by block root hash. Refer to <a href="https://ethereum.github.io/beacon-APIs/#/Beacon/getLightClientBootstrap">here</a>
     *
     * @param blockRoot block root hash
     * @return bootstrap info
     */
    LightClientBootstrap getLightClientBootstrap(String blockRoot);

    /**
     * Get light client update info by period and count. Refer to <a href="https://ethereum.github.io/beacon-APIs/#/Beacon/getLightClientUpdatesByRange">here</a>
     * @param period
     * @param count
     * @return
     */
    List<LightClientUpdate> getLightClientUpdates(long period, int count);

    LightClientUpdate getLightClientUpdate(BigInteger slot);

    BigInteger getLatestFinalizedSlot();

    GenesisData getGenesisData();

    Map<String, String> getRemoteSpec();

    void shutdown();
}

package com.alipay.antchain.bridge.plugins.ethereum3.core.eth.eth2spec;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import lombok.Setter;
import org.apache.tuweni.bytes.Bytes;
import tech.pegasys.teku.ethereum.execution.types.Eth1Address;
import tech.pegasys.teku.infrastructure.bytes.Bytes4;
import tech.pegasys.teku.infrastructure.unsigned.UInt64;
import tech.pegasys.teku.spec.config.SpecConfigAltair;

@Setter
public class DummySpecConfig implements SpecConfigAltair {

    private int syncCommitteeSize = Eth2ConstantParams.SYNC_COMMITTEE_SIZE;

    private int syncCommitteeBranchLength = Eth2ConstantParams.SYNC_COMMITTEE_BRANCH_LENGTH_DEFAULT;

    private int finalityBranchLength = Eth2ConstantParams.FINALITY_BRANCH_LENGTH_DEFAULT;

    @Override
    public Bytes4 getAltairForkVersion() {
        return null;
    }

    @Override
    public UInt64 getAltairForkEpoch() {
        return null;
    }

    @Override
    public UInt64 getInactivityPenaltyQuotientAltair() {
        return null;
    }

    @Override
    public int getMinSlashingPenaltyQuotientAltair() {
        return 0;
    }

    @Override
    public int getProportionalSlashingMultiplierAltair() {
        return 0;
    }

    @Override
    public int getSyncCommitteeSize() {
        return this.syncCommitteeSize;
    }

    @Override
    public UInt64 getInactivityScoreBias() {
        return null;
    }

    @Override
    public UInt64 getInactivityScoreRecoveryRate() {
        return null;
    }

    @Override
    public int getEpochsPerSyncCommitteePeriod() {
        return 0;
    }

    @Override
    public int getMinSyncCommitteeParticipants() {
        return 0;
    }

    @Override
    public int getUpdateTimeout() {
        return 0;
    }

    @Override
    public int getSyncCommitteeBranchLength() {
        return syncCommitteeBranchLength;
    }

    @Override
    public int getFinalityBranchLength() {
        return finalityBranchLength;
    }

    @Override
    public Optional<SpecConfigAltair> toVersionAltair() {
        return Optional.empty();
    }

    @Override
    public Map<String, Object> getRawConfig() {
        return new HashMap<>();
    }

    @Override
    public int getMinGenesisActiveValidatorCount() {
        return 0;
    }

    @Override
    public UInt64 getMinGenesisTime() {
        return null;
    }

    @Override
    public Bytes4 getGenesisForkVersion() {
        return null;
    }

    @Override
    public UInt64 getGenesisDelay() {
        return null;
    }

    @Override
    public int getSecondsPerSlot() {
        return 0;
    }

    @Override
    public int getSecondsPerEth1Block() {
        return 0;
    }

    @Override
    public int getMinValidatorWithdrawabilityDelay() {
        return 0;
    }

    @Override
    public UInt64 getShardCommitteePeriod() {
        return null;
    }

    @Override
    public UInt64 getEth1FollowDistance() {
        return null;
    }

    @Override
    public UInt64 getEjectionBalance() {
        return null;
    }

    @Override
    public int getMinPerEpochChurnLimit() {
        return 0;
    }

    @Override
    public UInt64 getMaxPerEpochActivationExitChurnLimit() {
        return null;
    }

    @Override
    public int getChurnLimitQuotient() {
        return 0;
    }

    @Override
    public int getProposerScoreBoost() {
        return 0;
    }

    @Override
    public long getDepositChainId() {
        return 0;
    }

    @Override
    public long getDepositNetworkId() {
        return 0;
    }

    @Override
    public Eth1Address getDepositContractAddress() {
        return null;
    }

    @Override
    public UInt64 getBaseRewardsPerEpoch() {
        return null;
    }

    @Override
    public int getDepositContractTreeDepth() {
        return 0;
    }

    @Override
    public int getJustificationBitsLength() {
        return 0;
    }

    @Override
    public Bytes getBlsWithdrawalPrefix() {
        return null;
    }

    @Override
    public int getMaxCommitteesPerSlot() {
        return 0;
    }

    @Override
    public int getTargetCommitteeSize() {
        return 0;
    }

    @Override
    public int getMaxValidatorsPerCommittee() {
        return 0;
    }

    @Override
    public int getShuffleRoundCount() {
        return 0;
    }

    @Override
    public UInt64 getHysteresisQuotient() {
        return null;
    }

    @Override
    public UInt64 getHysteresisDownwardMultiplier() {
        return null;
    }

    @Override
    public UInt64 getHysteresisUpwardMultiplier() {
        return null;
    }

    @Override
    public UInt64 getMinDepositAmount() {
        return null;
    }

    @Override
    public UInt64 getMaxEffectiveBalance() {
        return null;
    }

    @Override
    public UInt64 getEffectiveBalanceIncrement() {
        return null;
    }

    @Override
    public int getMinAttestationInclusionDelay() {
        return 0;
    }

    @Override
    public int getSlotsPerEpoch() {
        return 0;
    }

    @Override
    public long getSquareRootSlotsPerEpoch() {
        return 0;
    }

    @Override
    public int getMinSeedLookahead() {
        return 0;
    }

    @Override
    public int getMaxSeedLookahead() {
        return 0;
    }

    @Override
    public UInt64 getMinEpochsToInactivityPenalty() {
        return null;
    }

    @Override
    public int getEpochsPerEth1VotingPeriod() {
        return 0;
    }

    @Override
    public int getSlotsPerHistoricalRoot() {
        return 0;
    }

    @Override
    public int getEpochsPerHistoricalVector() {
        return 0;
    }

    @Override
    public int getEpochsPerSlashingsVector() {
        return 0;
    }

    @Override
    public int getHistoricalRootsLimit() {
        return 0;
    }

    @Override
    public long getValidatorRegistryLimit() {
        return 0;
    }

    @Override
    public int getBaseRewardFactor() {
        return 0;
    }

    @Override
    public int getWhistleblowerRewardQuotient() {
        return 0;
    }

    @Override
    public UInt64 getProposerRewardQuotient() {
        return null;
    }

    @Override
    public UInt64 getInactivityPenaltyQuotient() {
        return null;
    }

    @Override
    public int getMinSlashingPenaltyQuotient() {
        return 0;
    }

    @Override
    public int getProportionalSlashingMultiplier() {
        return 0;
    }

    @Override
    public int getMaxProposerSlashings() {
        return 0;
    }

    @Override
    public int getMaxAttesterSlashings() {
        return 0;
    }

    @Override
    public int getMaxAttestations() {
        return 0;
    }

    @Override
    public int getMaxDeposits() {
        return 0;
    }

    @Override
    public int getMaxVoluntaryExits() {
        return 0;
    }

    @Override
    public int getSafeSlotsToUpdateJustified() {
        return 0;
    }

    @Override
    public int getReorgMaxEpochsSinceFinalization() {
        return 0;
    }

    @Override
    public int getReorgHeadWeightThreshold() {
        return 0;
    }

    @Override
    public int getReorgParentWeightThreshold() {
        return 0;
    }

    @Override
    public int getGossipMaxSize() {
        return 0;
    }

    @Override
    public int getMaxChunkSize() {
        return 0;
    }

    @Override
    public int getMaxRequestBlocks() {
        return 0;
    }

    @Override
    public int getEpochsPerSubnetSubscription() {
        return 0;
    }

    @Override
    public int getMinEpochsForBlockRequests() {
        return 0;
    }

    @Override
    public int getTtfbTimeout() {
        return 0;
    }

    @Override
    public int getRespTimeout() {
        return 0;
    }

    @Override
    public int getAttestationPropagationSlotRange() {
        return 0;
    }

    @Override
    public int getMaximumGossipClockDisparity() {
        return 0;
    }

    @Override
    public Bytes4 getMessageDomainInvalidSnappy() {
        return null;
    }

    @Override
    public Bytes4 getMessageDomainValidSnappy() {
        return null;
    }

    @Override
    public int getSubnetsPerNode() {
        return 0;
    }

    @Override
    public int getAttestationSubnetCount() {
        return 0;
    }

    @Override
    public int getAttestationSubnetExtraBits() {
        return 0;
    }

    @Override
    public int getAttestationSubnetPrefixBits() {
        return 0;
    }
}

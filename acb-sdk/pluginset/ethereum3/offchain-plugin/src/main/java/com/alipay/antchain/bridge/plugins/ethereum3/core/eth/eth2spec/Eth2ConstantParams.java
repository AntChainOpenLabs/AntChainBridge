package com.alipay.antchain.bridge.plugins.ethereum3.core.eth.eth2spec;

public class Eth2ConstantParams {

    public static final int EPOCH_LENGTH = 32;

    public static final int SYNC_PERIOD_LENGTH = 8192;

    public static final int SYNC_COMMITTEE_SIZE = 512;

    public static final int SYNC_COMMITTEE_SUPER_MAJORITY = (SYNC_COMMITTEE_SIZE * 2 + 2) / 3;

    public static final int STATE_INDEX_FINAL_BLOCK = 105;

    public static final int STATE_INDEX_NEXT_SYNC_COMMITTEE = 55;

    public static final int SYNC_COMMITTEE_BRANCH_LENGTH_DEFAULT = 5;

    public static final int FINALITY_BRANCH_LENGTH_DEFAULT = 6;
}

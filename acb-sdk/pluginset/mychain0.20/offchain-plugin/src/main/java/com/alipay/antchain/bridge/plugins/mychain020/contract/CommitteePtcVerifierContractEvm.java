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

package com.alipay.antchain.bridge.plugins.mychain020.contract;

import java.math.BigInteger;
import java.util.UUID;

import cn.hutool.core.util.ObjectUtil;
import com.alipay.antchain.bridge.plugins.mychain020.sdk.Mychain020Client;
import com.alipay.mychain.sdk.common.VMTypeEnum;
import com.alipay.mychain.sdk.domain.account.Identity;
import com.alipay.mychain.sdk.domain.transaction.TransactionReceipt;
import com.alipay.mychain.sdk.errorcode.ErrorCode;
import com.alipay.mychain.sdk.vm.EVMOutput;
import com.alipay.mychain.sdk.vm.EVMParameter;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.bouncycastle.util.encoders.Hex;
import org.slf4j.Logger;

public class CommitteePtcVerifierContractEvm {

    private static final String COMMITTEE_VERIFIER_EVM_CONTRACT_PREFIX = "COMMITTEE_VERIFIER_EVM_CONTRACT_";
    private static final String COMMITTEE_VERIFIER_EVM_RUNTIME_PATH = "/contract/v1/solidity/CommitteePtcVerifier.bin-runtime";
    private static final String GET_IMPLEMENTATION_VERSION_SIGN = "getImplementationVersion()";
    private static final BigInteger SUPPORTED_IMPLEMENTATION_VERSION = BigInteger.valueOf(2L);

    private Mychain020Client mychain020Client;

    private final Logger logger;

    @Getter
    @Setter
    private String contractAddress;

    public CommitteePtcVerifierContractEvm(Mychain020Client mychain020Client, Logger logger) {
        this.mychain020Client = mychain020Client;
        this.logger = logger;
    }

    public boolean deployContract() {
        if (StringUtils.isEmpty(this.getContractAddress())) {
            String contractPath = MychainContractBinaryVersionEnum.selectBinaryByVersion(
                    mychain020Client.getConfig().getMychainContractBinaryVersion()
            ).getCommitteeVerifierEvm();
            String contractName = COMMITTEE_VERIFIER_EVM_CONTRACT_PREFIX + UUID.randomUUID();

            logger.info("Deploying CommitteePtcVerifierContractEvm {} with code from {}", contractName, contractPath);

            if (mychain020Client.deployContract(contractPath, contractName, VMTypeEnum.EVM, new EVMParameter())) {
                this.setContractAddress(contractName);
                return true;
            }

            return false;
        }

        return true;
    }

    public boolean ensureImplementationSupported(Identity verifierIdentity) {
        if (verifierIdentity == null) {
            logger.error("cannot upgrade an empty committee verifier identity");
            return false;
        }
        if (isImplementationVersionSupported(verifierIdentity)) {
            return true;
        }
        logger.info("Upgrade committee verifier {} with runtime {}",
                verifierIdentity.hexStrValue(), COMMITTEE_VERIFIER_EVM_RUNTIME_PATH);
        if (!mychain020Client.upgradeContract(
                COMMITTEE_VERIFIER_EVM_RUNTIME_PATH,
                verifierIdentity,
                VMTypeEnum.EVM)) {
            logger.error("failed to upgrade committee verifier {}", verifierIdentity.hexStrValue());
            return false;
        }
        return isImplementationVersionSupported(verifierIdentity);
    }

    public boolean isImplementationVersionSupported(Identity verifierIdentity) {
        if (verifierIdentity == null) {
            return false;
        }
        try {
            TransactionReceipt receipt = mychain020Client.localCallContract(
                    verifierIdentity,
                    new EVMParameter(GET_IMPLEMENTATION_VERSION_SIGN));
            if (ObjectUtil.isEmpty(receipt)
                    || receipt.getResult() != ErrorCode.SUCCESS.getErrorCode()
                    || ObjectUtil.isEmpty(receipt.getOutput())) {
                return false;
            }
            BigInteger version = new EVMOutput(Hex.toHexString(receipt.getOutput())).getUint();
            return version.compareTo(SUPPORTED_IMPLEMENTATION_VERSION) >= 0;
        } catch (Exception e) {
            logger.info("Committee verifier {} does not expose a supported implementation version.",
                    verifierIdentity.hexStrValue());
            return false;
        }
    }
}

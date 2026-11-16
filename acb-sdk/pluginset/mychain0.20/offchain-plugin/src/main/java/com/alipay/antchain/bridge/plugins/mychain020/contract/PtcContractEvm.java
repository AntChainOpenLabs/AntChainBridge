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
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import cn.hutool.core.util.ArrayUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.alipay.antchain.bridge.commons.bbc.syscontract.ContractStatusEnum;
import com.alipay.antchain.bridge.commons.bbc.syscontract.PTCContract;
import com.alipay.antchain.bridge.commons.bcdns.AbstractCrossChainCertificate;
import com.alipay.antchain.bridge.commons.bcdns.CrossChainCertificateTypeEnum;
import com.alipay.antchain.bridge.commons.bcdns.utils.CrossChainCertificateUtil;
import com.alipay.antchain.bridge.commons.core.base.CrossChainLane;
import com.alipay.antchain.bridge.commons.core.base.ObjectIdentity;
import com.alipay.antchain.bridge.commons.core.base.SendResponseResult;
import com.alipay.antchain.bridge.commons.core.ptc.PTCTrustRoot;
import com.alipay.antchain.bridge.commons.core.ptc.PTCTypeEnum;
import com.alipay.antchain.bridge.commons.core.ptc.PTCVerifyAnchor;
import com.alipay.antchain.bridge.commons.core.ptc.ThirdPartyBlockchainTrustAnchor;
import com.alipay.antchain.bridge.plugins.mychain020.exceptions.CallContractException;
import com.alipay.antchain.bridge.plugins.mychain020.sdk.Mychain020Client;
import com.alipay.mychain.sdk.api.utils.Utils;
import com.alipay.mychain.sdk.common.VMTypeEnum;
import com.alipay.mychain.sdk.domain.transaction.TransactionReceipt;
import com.alipay.mychain.sdk.errorcode.ErrorCode;
import com.alipay.mychain.sdk.message.transaction.TransactionReceiptResponse;
import com.alipay.mychain.sdk.vm.EVMOutput;
import com.alipay.mychain.sdk.vm.EVMParameter;
import org.apache.commons.lang3.StringUtils;
import org.bouncycastle.util.encoders.Hex;
import org.slf4j.Logger;

public class PtcContractEvm extends PTCContract implements AbstractPtcContract {

    private static final String PTC_HUB_EVM_CONTRACT_PREFIX = "PTC_HUB_EVM_CONTRACT_";

    private static final String PTC_HUB_EVM_RUNTIME_PATH = "/contract/v1/solidity/PtcHub.bin-runtime";

    private static final String GET_IMPLEMENTATION_VERSION_SIGN = "getImplementationVersion()";

    private static final BigInteger ROOT_RECONCILIATION_IMPLEMENTATION_VERSION = BigInteger.valueOf(2L);

    private static final BigInteger MONITOR_PROOF_ROUTING_IMPLEMENTATION_VERSION = BigInteger.valueOf(3L);

    private static final long LEGACY_METHOD_NOT_FOUND_RESULT = 10201L;

    private Mychain020Client mychain020Client;

    private final Logger logger;

    public PtcContractEvm(Mychain020Client mychain020Client, Logger logger) {
        this.mychain020Client = mychain020Client;
        this.logger = logger;
    }

    @Override
    public boolean deployContract(String bcdnsRootCertPem) {
        if (StringUtils.isNotEmpty(this.getContractAddress())) {
            // A configured PTC Hub was initialized when it was deployed. Keep it
            // ready when setup is retried after the plugin or relayer restarts.
            this.setStatus(ContractStatusEnum.CONTRACT_READY);
            return true;
        }

        String contractPath = MychainContractBinaryVersionEnum.selectBinaryByVersion(
                mychain020Client.getConfig().getMychainContractBinaryVersion()
        ).getPtcHubEvm();
        if (StrUtil.isEmpty(contractPath)) {
            logger.error("no ptc hub binary code for this version of contracts");
            return false;
        }

        String contractName = PTC_HUB_EVM_CONTRACT_PREFIX + UUID.randomUUID();

        CommitteePtcVerifierContractEvm committeePtcVerifierContractEvm = new CommitteePtcVerifierContractEvm(mychain020Client, logger);
        if (!committeePtcVerifierContractEvm.deployContract()) {
            logger.error("failed to deploy committee ptc verifier contract");
            return false;
        }

        logger.info("Deploying PTC hub contract {} with code from {}", contractName, contractPath);
        AbstractCrossChainCertificate bcdnsRootCert = CrossChainCertificateUtil.readCrossChainCertificateFromPem(bcdnsRootCertPem.getBytes());
        if (bcdnsRootCert.getType() != CrossChainCertificateTypeEnum.BCDNS_TRUST_ROOT_CERTIFICATE) {
            logger.error("for now, PTC hub contract only support BCDNS trust root certificate to initialize");
            return false;
        }

        EVMParameter parameter = new EVMParameter();
        parameter.addBytes(bcdnsRootCert.encode());
        if (!mychain020Client.deployContract(contractPath, contractName, VMTypeEnum.EVM, parameter)) {
            logger.error("failed to deploy ptc hub contract");
            return false;
        }
        this.setContractAddress(contractName);

        logger.info("Set committee verifier {} into ptc hub now...", committeePtcVerifierContractEvm.getContractAddress());
        setCommitteeVerifier(committeePtcVerifierContractEvm.getContractAddress());

        this.setStatus(ContractStatusEnum.CONTRACT_READY);
        return true;
    }

    @Override
    public void updatePTCTrustRoot(PTCTrustRoot ptcTrustRoot) {
        EVMParameter parameters = new EVMParameter("updatePTCTrustRoot(bytes)");
        parameters.addBytes(ptcTrustRoot.encode());

        SendResponseResult result = mychain020Client.callContract(this.getContractAddress(), parameters, true);
        if (!result.isSuccess()) {
            throw new CallContractException(getContractAddress(), result.getTxId(), result.getErrorMessage());
        }

        logger.info("Update PTC trust root {} with tx {} successfully!",
                ptcTrustRoot.getPtcCredentialSubject().getApplicant().toHex(), result.getTxId());
    }

    public boolean reconcileRootBcdnsCert(String bcdnsRootCertPem) {
        if (!ensureRootReconciliationSupport()) {
            return false;
        }
        AbstractCrossChainCertificate bcdnsRootCert =
                CrossChainCertificateUtil.readCrossChainCertificateFromPem(bcdnsRootCertPem.getBytes());
        if (bcdnsRootCert.getType() != CrossChainCertificateTypeEnum.BCDNS_TRUST_ROOT_CERTIFICATE) {
            throw new IllegalArgumentException("PTC hub requires a BCDNS trust root certificate");
        }

        EVMParameter parameters = new EVMParameter("reconcileRootBcdnsCert(bytes)");
        parameters.addBytes(bcdnsRootCert.encode());
        SendResponseResult result = mychain020Client.callContract(this.getContractAddress(), parameters, true);
        if (!result.isSuccess()) {
            throw new CallContractException(getContractAddress(), result.getTxId(), result.getErrorMessage());
        }
        logger.info("Reconciled PTC hub {} with the configured BCDNS root in tx {}",
                getContractAddress(), result.getTxId());
        return true;
    }

    public boolean ensureRootReconciliationSupport() {
        if (StrUtil.isEmpty(this.getContractAddress())) {
            return false;
        }
        BigInteger version = queryImplementationVersion();
        if (version.compareTo(ROOT_RECONCILIATION_IMPLEMENTATION_VERSION) >= 0) {
            return true;
        }

        logger.info("Upgrade legacy PTC hub {} for BCDNS root reconciliation", getContractAddress());
        if (!mychain020Client.upgradeContract(
                PTC_HUB_EVM_RUNTIME_PATH,
                getContractAddress(),
                VMTypeEnum.EVM)) {
            logger.error("failed to upgrade PTC hub {} for BCDNS root reconciliation", getContractAddress());
            return false;
        }
        return queryImplementationVersion().compareTo(ROOT_RECONCILIATION_IMPLEMENTATION_VERSION) >= 0;
    }

    private BigInteger queryImplementationVersion() {
        TransactionReceipt receipt = mychain020Client.localCallContract(
                this.getContractAddress(),
                new EVMParameter(GET_IMPLEMENTATION_VERSION_SIGN));
        if (ObjectUtil.isEmpty(receipt)) {
            throw new IllegalStateException("empty PTC hub implementation-version receipt");
        }
        if (receipt.getResult() == LEGACY_METHOD_NOT_FOUND_RESULT) {
            return BigInteger.ZERO;
        }
        if (receipt.getResult() != ErrorCode.SUCCESS.getErrorCode()
                || ObjectUtil.isEmpty(receipt.getOutput())) {
            throw new IllegalStateException(
                    StrUtil.format("unexpected PTC hub implementation-version result: {}", receipt.getResult()));
        }
        return new BigInteger(1, receipt.getOutput());
    }

    public void setCommitteeVerifier(String committeeVerifierName) {
        EVMParameter parameters = new EVMParameter("addPtcVerifier(identity)");
        parameters.addIdentity(Utils.getIdentityByName(committeeVerifierName, mychain020Client.getConfig().getMychainHashType()));

        SendResponseResult result = mychain020Client.callContract(this.getContractAddress(), parameters, true);
        if (!result.isSuccess()) {
            throw new CallContractException(getContractAddress(), result.getTxId(), result.getErrorMessage());
        }

        logger.info("Set committee verifier {} with tx {} successfully!", committeeVerifierName, result.getTxId());
    }

    public void setMonitorVerifier(String monitorVerifierName) {
        EVMParameter parameters = new EVMParameter("setMonitorVerifier(identity)");
        parameters.addIdentity(Utils.getIdentityByName(
                monitorVerifierName,
                mychain020Client.getConfig().getMychainHashType()
        ));

        SendResponseResult result = mychain020Client.callContract(this.getContractAddress(), parameters, true);
        if (!result.isSuccess()) {
            throw new CallContractException(getContractAddress(), result.getTxId(), result.getErrorMessage());
        }

        logger.info("Set monitor verifier {} with tx {} successfully!", monitorVerifierName, result.getTxId());
    }

    /** A zero identity is a valid read: it means this binding is not set yet. */
    public com.alipay.mychain.sdk.domain.account.Identity getMonitorVerifierIdentity() {
        TransactionReceipt receipt = mychain020Client.localCallContract(
                getContractAddress(), new EVMParameter("getMonitorVerifier()"));
        if (receipt == null || receipt.getResult() != ErrorCode.SUCCESS.getErrorCode()
                || receipt.getOutput() == null || receipt.getOutput().length != 32) {
            throw new CallContractException(getContractAddress(), "", "PTC hub monitor verifier identity is unavailable");
        }
        return new com.alipay.mychain.sdk.domain.account.Identity(
                new EVMOutput(Hex.toHexString(receipt.getOutput())).getIdentity());
    }

    public void setMonitorVerifier(com.alipay.mychain.sdk.domain.account.Identity monitorVerifierIdentity) {
        EVMParameter parameters = new EVMParameter("setMonitorVerifier(identity)");
        parameters.addIdentity(monitorVerifierIdentity);

        SendResponseResult result = mychain020Client.callContract(this.getContractAddress(), parameters, true);
        if (!result.isSuccess()) {
            throw new CallContractException(getContractAddress(), result.getTxId(), result.getErrorMessage());
        }
        logger.info("Set monitor verifier {} with tx {} successfully!",
                monitorVerifierIdentity.hexStrValue(), result.getTxId());
    }

    /**
     * Upgrade a PTC Hub deployed before monitor support was introduced while
     * preserving the contract identity and its existing trust data.
     *
     * <p>The regulatory PTC Hub appends only {@code monitorVerifierAddr} to the
     * legacy storage layout. V3 also routes committee monitor proofs without
     * adding storage. Mychain's update-contract operation replaces the runtime
     * code at the existing identity, so the BCDNS certificate, PTC trust
     * roots, verify anchors and TpBTAs remain available.</p>
     */
    public boolean ensureMonitorVerifierSupport() {
        return ensureMonitorProofRoutingSupport();
    }

    public boolean ensureMonitorProofRoutingSupport() {
        if (isMonitorProofRoutingSupported()) {
            return true;
        }
        if (StrUtil.isEmpty(this.getContractAddress())) {
            logger.error("cannot upgrade an empty ptc hub contract");
            return false;
        }

        logger.info(
                "Upgrade legacy PTC hub {} with monitor-enabled runtime {}",
                this.getContractAddress(),
                PTC_HUB_EVM_RUNTIME_PATH);
        if (!mychain020Client.upgradeContract(
                PTC_HUB_EVM_RUNTIME_PATH,
                this.getContractAddress(),
                VMTypeEnum.EVM)) {
            logger.error("failed to upgrade legacy ptc hub {}", this.getContractAddress());
            return false;
        }

        boolean supported = isMonitorProofRoutingSupported();
        if (!supported) {
            logger.error("ptc hub {} still lacks monitor support after upgrade", this.getContractAddress());
        }
        return supported;
    }

    public boolean isMonitorVerifierSupported() {
        return isMonitorProofRoutingSupported();
    }

    public boolean isMonitorProofRoutingSupported() {
        if (StrUtil.isEmpty(this.getContractAddress())) {
            return false;
        }
        return queryImplementationVersion().compareTo(MONITOR_PROOF_ROUTING_IMPLEMENTATION_VERSION) >= 0;
    }

    public String getCommitteeVerifier() {
        com.alipay.mychain.sdk.domain.account.Identity identity = getCommitteeVerifierIdentity();
        return identity == null ? null : identity.hexStrValue();
    }

    public com.alipay.mychain.sdk.domain.account.Identity getCommitteeVerifierIdentity() {
        EVMParameter parameters = new EVMParameter("verifierMap(uint8)");
        parameters.addUint(BigInteger.valueOf(PTCTypeEnum.COMMITTEE.ordinal()));

        TransactionReceipt receipt = mychain020Client.localCallContract(this.getContractAddress(), parameters);
        if (ObjectUtil.isEmpty(receipt.getOutput())) {
            return null;
        }
        com.alipay.mychain.sdk.domain.account.Identity identity =
                new com.alipay.mychain.sdk.domain.account.Identity(
                        new EVMOutput(Hex.toHexString(receipt.getOutput())).getIdentity());
        return identity.hexStrValue().matches("(?i)(?:0x)?0+") ? null : identity;
    }

    public boolean ensureCommitteeVerifierMonitorSupport() {
        com.alipay.mychain.sdk.domain.account.Identity verifierIdentity = getCommitteeVerifierIdentity();
        if (verifierIdentity == null) {
            logger.error("committee verifier is not configured in PTC hub {}", getContractAddress());
            return false;
        }
        return new CommitteePtcVerifierContractEvm(mychain020Client, logger)
                .ensureImplementationSupported(verifierIdentity);
    }

    @Override
    public PTCTrustRoot getPTCTrustRoot(ObjectIdentity ptcOwnerOid) {
        EVMParameter parameters = new EVMParameter("getPTCTrustRoot(bytes)");
        parameters.addBytes(ptcOwnerOid.encode());

        TransactionReceipt receipt = mychain020Client.localCallContract(this.getContractAddress(), parameters);
        if (ObjectUtil.isEmpty(receipt.getOutput())) {
            return null;
        }
        byte[] raw = new EVMOutput(Hex.toHexString(receipt.getOutput())).getBytes();
        if (ArrayUtil.isEmpty(raw)) {
            return null;
        }

        return PTCTrustRoot.decode(raw);
    }

    @Override
    public boolean hasPTCTrustRoot(ObjectIdentity ptcOwnerOid) {
        EVMParameter parameters = new EVMParameter("hasPTCTrustRoot(bytes)");
        parameters.addBytes(ptcOwnerOid.encode());

        TransactionReceipt receipt = mychain020Client.localCallContract(this.getContractAddress(), parameters);
        if (ObjectUtil.isEmpty(receipt.getOutput())) {
            return false;
        }
        return new EVMOutput(Hex.toHexString(receipt.getOutput())).getBoolean();
    }

    @Override
    public PTCVerifyAnchor getPTCVerifyAnchor(ObjectIdentity ptcOwnerOid, BigInteger versionNum) {
        EVMParameter parameters = new EVMParameter("getPTCVerifyAnchor(bytes,uint256)");
        parameters.addBytes(ptcOwnerOid.encode());
        parameters.addUint(versionNum);

        TransactionReceipt receipt = mychain020Client.localCallContract(this.getContractAddress(), parameters);
        if (ObjectUtil.isEmpty(receipt.getOutput())) {
            return null;
        }
        byte[] raw = new EVMOutput(Hex.toHexString(receipt.getOutput())).getBytes();
        if (ArrayUtil.isEmpty(raw)) {
            return null;
        }

        return PTCVerifyAnchor.decode(raw);
    }

    @Override
    public boolean hasPTCVerifyAnchor(ObjectIdentity ptcOwnerOid, BigInteger versionNum) {
        EVMParameter parameters = new EVMParameter("hasPTCVerifyAnchor(bytes,uint256)");
        parameters.addBytes(ptcOwnerOid.encode());
        parameters.addUint(versionNum);

        TransactionReceipt receipt = mychain020Client.localCallContract(this.getContractAddress(), parameters);
        if (ObjectUtil.isEmpty(receipt.getOutput())) {
            return false;
        }
        return new EVMOutput(Hex.toHexString(receipt.getOutput())).getBoolean();
    }

    @Override
    public void addTpBta(ThirdPartyBlockchainTrustAnchor tpBta) {
        EVMParameter parameters = new EVMParameter("addTpBta(bytes)");
        parameters.addBytes(tpBta.encode());

        SendResponseResult result = mychain020Client.callContract(this.getContractAddress(), parameters, true);
        if (!result.isSuccess()) {
            throw new CallContractException(getContractAddress(), result.getTxId(), result.getErrorMessage());
        }

        logger.info("Add TpBta {}:{} with tx {} successfully!",
                tpBta.getCrossChainLane().getLaneKey(), tpBta.getTpbtaVersion(), result.getTxId());
    }

    @Override
    public ThirdPartyBlockchainTrustAnchor getTpBta(CrossChainLane tpbtaLane, long tpBtaVersion) {
        EVMParameter parameters = new EVMParameter("getTpBta(bytes,uint32)");
        parameters.addBytes(tpbtaLane.encode());
        parameters.addUint(BigInteger.valueOf(tpBtaVersion));

        TransactionReceipt receipt = mychain020Client.localCallContract(this.getContractAddress(), parameters);
        if (ObjectUtil.isEmpty(receipt.getOutput())) {
            return null;
        }
        byte[] raw = new EVMOutput(Hex.toHexString(receipt.getOutput())).getBytes();
        if (ArrayUtil.isEmpty(raw)) {
            return null;
        }

        return ThirdPartyBlockchainTrustAnchor.decode(raw);
    }

    @Override
    public ThirdPartyBlockchainTrustAnchor getLatestTpBta(CrossChainLane tpbtaLane) {
        EVMParameter parameters = new EVMParameter("getLatestTpBta(bytes)");
        parameters.addBytes(tpbtaLane.encode());

        TransactionReceipt receipt = mychain020Client.localCallContract(this.getContractAddress(), parameters);
        if (ObjectUtil.isEmpty(receipt.getOutput())) {
            return null;
        }
        byte[] raw = new EVMOutput(Hex.toHexString(receipt.getOutput())).getBytes();
        if (ArrayUtil.isEmpty(raw)) {
            return null;
        }

        return ThirdPartyBlockchainTrustAnchor.decode(raw);
    }

    @Override
    public boolean hasTpBta(CrossChainLane tpbtaLane, long tpBtaVersion) {
        EVMParameter parameters = new EVMParameter("hasTpBta(bytes,uint32)");
        parameters.addBytes(tpbtaLane.encode());
        parameters.addUint(BigInteger.valueOf(tpBtaVersion));

        TransactionReceipt receipt = mychain020Client.localCallContract(this.getContractAddress(), parameters);
        if (ObjectUtil.isEmpty(receipt.getOutput())) {
            return false;
        }
        return new EVMOutput(Hex.toHexString(receipt.getOutput())).getBoolean();
    }

    @Override
    public Set<PTCTypeEnum> getSupportedPTCTypes() {
        TransactionReceipt receipt = mychain020Client.localCallContract(this.getContractAddress(), new EVMParameter("getSupportedPTCType()"));
        if (ObjectUtil.isEmpty(receipt.getOutput())) {
            return new HashSet<>();
        }
        Set<PTCTypeEnum> ptcTypeSet = new HashSet<>();
        for (BigInteger val : new EVMOutput(Hex.toHexString(receipt.getOutput())).getUintDynamicArray()) {
            ptcTypeSet.add(PTCTypeEnum.valueOf(val.byteValueExact()));
        }

        return ptcTypeSet;
    }
}

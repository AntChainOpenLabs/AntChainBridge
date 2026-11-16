package com.alipay.antchain.bridge.plugins.mychain;

import java.io.IOException;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson.annotation.JSONField;
import com.alipay.antchain.bridge.commons.bbc.AbstractBBCContext;
import com.alipay.antchain.bridge.commons.bbc.DefaultBBCContext;
import com.alipay.antchain.bridge.commons.bbc.syscontract.AuthMessageContract;
import com.alipay.antchain.bridge.commons.bbc.syscontract.ContractStatusEnum;
import com.alipay.antchain.bridge.commons.bbc.syscontract.PTCContract;
import com.alipay.antchain.bridge.commons.bbc.syscontract.SDPContract;
import com.alipay.antchain.bridge.plugins.mychain.contract.*;
import com.alipay.antchain.bridge.plugins.mychain.model.ContractAddressInfo;
import com.alipay.antchain.bridge.plugins.mychain.sdk.Mychain010Client;
import com.alipay.antchain.bridge.plugins.mychain.sdk.Mychain010Config;
import lombok.Getter;
import lombok.Setter;
import lombok.SneakyThrows;
import org.slf4j.Logger;

@Getter
@Setter
public class Mychain010BBCContext extends AbstractBBCContext {

    private static final String EVM_CONTRACT_KEY = "evm";
    private static final String WASM_CONTRACT_KEY = "wasm";

    @JSONField(serialize = false, deserialize = false)
    private AMContractClientEVM amContractClientEVM;
    @JSONField(serialize = false, deserialize = false)
    private AMContractClientWASM amContractClientWASM;
    @JSONField(serialize = false, deserialize = false)
    private AMContractClientTeeWASM amContractClientTeeWASM;
    @JSONField(serialize = false, deserialize = false)
    private SDPContractClientEVM sdpContractClientEVM;
    @JSONField(serialize = false, deserialize = false)
    private SDPContractClientWASM sdpContractClientWASM;
    @JSONField(serialize = false, deserialize = false)
    private SDPContractClientTeeWASM sdpContractClientTeeWASM;
    @JSONField(serialize = false, deserialize = false)
    private PtcContractEvm ptcContractEvm;

    @JSONField(serialize = false, deserialize = false)
    private final Logger logger;

    // 可靠上链相关
    private long latestBlockTimestamp = 0;
    private long fixedDelay = 10 * 1000; // ms
    private long nonceBefore;

    /**
     * 如果 context 是 Mychain010BBCContext，可以直接进行赋值
     * 如果 context 不是 Mychain010BBCContext，对主干信息进行赋值后，其他信息需要手动init
     *
     * @param context
     */
    public Mychain010BBCContext(AbstractBBCContext context, Logger logger) {
        // 初始化合约状态
        this.setSdpContract(context.getSdpContract());
        this.setPtcContract(context.getPtcContract());
        this.setAuthMessageContract(context.getAuthMessageContract());
        this.setConfForBlockchainClient(context.getConfForBlockchainClient());
        this.setReliable(context.isReliable());
        this.logger = logger;

        if (context instanceof Mychain010BBCContext) {
            // 如果 context 是 Mychain010BBCContext，可以直接进行赋值

            this.setAmContractClientEVM(((Mychain010BBCContext) context).getAmContractClientEVM());
            this.setSdpContractClientEVM(((Mychain010BBCContext) context).getSdpContractClientEVM());

            this.setAmContractClientWASM(((Mychain010BBCContext) context).getAmContractClientWASM());
            this.setSdpContractClientWASM(((Mychain010BBCContext) context).getSdpContractClientWASM());

            this.setPtcContractEvm(((Mychain010BBCContext) context).getPtcContractEvm());

            this.setAmContractClientTeeWASM(((Mychain010BBCContext) context).getAmContractClientTeeWASM());
            this.setSdpContractClientTeeWASM(((Mychain010BBCContext) context).getSdpContractClientTeeWASM());
        }
    }

    /**
     * Serialize the same portable fields as the Mychain 0.20 context.
     *
     * <p>Runtime clients must not cross the PluginServer boundary. Serializing
     * this subclass directly lets bean getters such as getCommitteeVerifier()
     * and getSupportedPTCTypes() perform chain reads. A plain context also
     * avoids relying on plugin-local annotations across class loaders.</p>
     */
    @Override
    public byte[] encodeToBytes() {
        DefaultBBCContext persistedContext = new DefaultBBCContext();
        persistedContext.setSdpContract(this.getSdpContract());
        persistedContext.setMonitorContract(this.getMonitorContract());
        persistedContext.setPtcContract(this.getPtcContract());
        persistedContext.setAuthMessageContract(this.getAuthMessageContract());
        persistedContext.setReliable(this.isReliable());
        persistedContext.setConfForBlockchainClient(this.getConfForBlockchainClient());
        return persistedContext.encodeToBytes();
    }

    /**
     * 初始化合约接口client，方便合约调用
     *
     * @param mychain010Client
     */
    public void initContractClient(Mychain010Client mychain010Client) {
        try {
            initAmContract(mychain010Client);
            initSdpContract(mychain010Client);
            initPtcContract(mychain010Client);

            this.nonceBefore = mychain010Client.queryNonceBefore();
        } catch (Exception e) {
            throw new RuntimeException("init mychain_0.10 context with raw config exception, ", e);
        }
    }

    private void initAmContract(Mychain010Client mychain010Client) throws IOException {
        ContractAddressInfo contractAddressInfo = new ContractAddressInfo();

        if (ObjectUtil.isNotEmpty(this.getAuthMessageContract())
                && StrUtil.isNotEmpty(this.getAuthMessageContract().getContractAddress())) {
            // 上下文可能携带合约部署信息（插件重启）
            contractAddressInfo = ContractAddressInfo.decode(this.getAuthMessageContract().getContractAddress());
        } else if (ObjectUtil.isNotEmpty(this.getConfForBlockchainClient())) {
            // 配置里可能携带合约部署信息（启动前已手动部署）
            Mychain010Config config = Mychain010Config.fromJsonString(new String(this.getConfForBlockchainClient()));
            if (StrUtil.isNotEmpty(config.getAmContractName())) {
                contractAddressInfo = ContractAddressInfo.decode(config.getAmContractName());

                this.setAuthMessageContract(new AuthMessageContract(
                        config.getAmContractName(),
                        ContractStatusEnum.CONTRACT_DEPLOYED));
            }
        }

        if (mychain010Client.isTeeChain()) {
            if (ObjectUtil.isEmpty(amContractClientTeeWASM)) {
                amContractClientTeeWASM = new AMContractClientTeeWASM(mychain010Client, logger);
            }
            if (StrUtil.isEmpty(amContractClientTeeWASM.getContractAddress())
                    && StrUtil.isNotEmpty(contractAddressInfo.getWasmContractAddress())) {
                amContractClientTeeWASM.setContractAddress(contractAddressInfo.getWasmContractAddress());
                amContractClientTeeWASM.setStatus(ContractStatusEnum.CONTRACT_DEPLOYED);
            }
        } else {
            if (ObjectUtil.isEmpty(amContractClientEVM)) {
                amContractClientEVM = new AMContractClientEVM(mychain010Client, logger);
            }
            if (StrUtil.isEmpty(amContractClientEVM.getContractAddress())
                    && StrUtil.isNotEmpty(contractAddressInfo.getEvmContractAddress())) {
                amContractClientEVM.setContractAddress(contractAddressInfo.getEvmContractAddress());
                amContractClientEVM.setStatus(ContractStatusEnum.CONTRACT_DEPLOYED);
            }

            if (ObjectUtil.isEmpty(amContractClientWASM)) {
                amContractClientWASM = new AMContractClientWASM(mychain010Client, logger);
            }
            if (StrUtil.isEmpty(amContractClientWASM.getContractAddress())
                    && StrUtil.isNotEmpty(contractAddressInfo.getWasmContractAddress())) {
                amContractClientWASM.setContractAddress(contractAddressInfo.getWasmContractAddress());
                amContractClientWASM.setStatus(ContractStatusEnum.CONTRACT_DEPLOYED);
            }
        }
    }

    private void initSdpContract(Mychain010Client mychain010Client) throws IOException {
        ContractAddressInfo contractAddressInfo = new ContractAddressInfo();

        if (ObjectUtil.isNotEmpty(this.getSdpContract())
                && StrUtil.isNotEmpty(this.getSdpContract().getContractAddress())) {
            // 上下文可能携带合约部署信息（插件重启）
            contractAddressInfo = ContractAddressInfo.decode(this.getSdpContract().getContractAddress());
        } else if (ObjectUtil.isNotEmpty(this.getConfForBlockchainClient())) {
            // 配置里可能携带合约部署信息（启动前已手动部署）
            Mychain010Config config = Mychain010Config.fromJsonString(new String(this.getConfForBlockchainClient()));
            if (StrUtil.isNotEmpty(config.getSdpContractName())) {
                this.setSdpContract(new SDPContract(
                        config.getSdpContractName(),
                        ContractStatusEnum.CONTRACT_DEPLOYED));

                contractAddressInfo = ContractAddressInfo.decode(config.getSdpContractName());
            }
        }

        if (mychain010Client.isTeeChain()) {
            if (ObjectUtil.isEmpty(sdpContractClientTeeWASM)) {
                sdpContractClientTeeWASM = new SDPContractClientTeeWASM(mychain010Client, logger);
            }
            if (StrUtil.isEmpty(sdpContractClientTeeWASM.getContractAddress())
                    && StrUtil.isNotEmpty(contractAddressInfo.getWasmContractAddress())) {
                sdpContractClientTeeWASM.setContractAddress(contractAddressInfo.getWasmContractAddress());
                sdpContractClientTeeWASM.setStatus(ContractStatusEnum.CONTRACT_DEPLOYED);
            }
        } else {
            if (ObjectUtil.isEmpty(sdpContractClientEVM)) {
                sdpContractClientEVM = new SDPContractClientEVM(mychain010Client, logger);
            }
            if (StrUtil.isEmpty(sdpContractClientEVM.getContractAddress())
                    && StrUtil.isNotEmpty(contractAddressInfo.getEvmContractAddress())) {
                sdpContractClientEVM.setContractAddress(contractAddressInfo.getEvmContractAddress());
                sdpContractClientEVM.setStatus(ContractStatusEnum.CONTRACT_DEPLOYED);
            }

            if (ObjectUtil.isEmpty(sdpContractClientWASM)) {
                sdpContractClientWASM = new SDPContractClientWASM(mychain010Client, logger);
            }
            if (StrUtil.isEmpty(sdpContractClientWASM.getContractAddress())
                    && StrUtil.isNotEmpty(contractAddressInfo.getWasmContractAddress())) {
                sdpContractClientWASM.setContractAddress(contractAddressInfo.getWasmContractAddress());
                sdpContractClientWASM.setStatus(ContractStatusEnum.CONTRACT_DEPLOYED);
            }
        }
    }

    @SneakyThrows
    private void initPtcContract(Mychain010Client mychain010Client) {
        ContractAddressInfo contractAddressInfo = new ContractAddressInfo();
        if (ObjectUtil.isNotEmpty(this.getPtcContract())
                && StrUtil.isNotEmpty(this.getPtcContract().getContractAddress())) {
            // 上下文可能携带合约部署信息（插件重启）
            contractAddressInfo = ContractAddressInfo.decode(this.getPtcContract().getContractAddress());
        } else if (ObjectUtil.isNotEmpty(this.getConfForBlockchainClient())) {
            // 配置里可能携带合约部署信息（启动前已手动部署）
            Mychain010Config config = Mychain010Config.fromJsonString(new String(this.getConfForBlockchainClient()));
            if (StrUtil.isNotEmpty(config.getPtcContractName())) {
                PTCContract ptcContract = new PTCContract();
                ptcContract.setContractAddress(config.getPtcContractName());
                ptcContract.setStatus(ContractStatusEnum.CONTRACT_DEPLOYED);
                this.setPtcContract(ptcContract);

                contractAddressInfo = ContractAddressInfo.decode(config.getPtcContractName());
            }
        }

        if (ObjectUtil.isEmpty(ptcContractEvm)) {
            ptcContractEvm = new PtcContractEvm(mychain010Client, logger);
        }
        if (StrUtil.isEmpty(ptcContractEvm.getContractAddress())
                && StrUtil.isNotEmpty(contractAddressInfo.getEvmContractAddress())) {
            ptcContractEvm.setContractAddress(contractAddressInfo.getEvmContractAddress());
            ptcContractEvm.setStatus(ContractStatusEnum.CONTRACT_DEPLOYED);
        }
    }

    /**
     * 判断AM合约地址是否已设置
     *
     * @param isTeeChain
     * @return
     */
    private boolean isAMInit(boolean isTeeChain) {
        if (isTeeChain) {
            return ObjectUtil.isNotEmpty(amContractClientTeeWASM)
                    && StrUtil.isNotEmpty(amContractClientTeeWASM.getContractAddress());
        } else {
            return ObjectUtil.isNotEmpty(amContractClientEVM)
                    && StrUtil.isNotEmpty(amContractClientEVM.getContractAddress());
        }
    }

    /**
     * 判断SDP合约地址是否已设置
     *
     * @param isTeeChain
     * @return
     */
    private boolean isSDPInit(boolean isTeeChain) {
        if (isTeeChain) {
            return ObjectUtil.isNotEmpty(sdpContractClientTeeWASM)
                    && StrUtil.isNotEmpty(sdpContractClientTeeWASM.getContractAddress());
        } else {
            return ObjectUtil.isNotEmpty(sdpContractClientEVM)
                    && StrUtil.isNotEmpty(sdpContractClientEVM.getContractAddress());
        }
    }

    /**
     * 判断AM合约是否ready，用于AM合约的setProtocol
     * - AM 合约存在且为ready
     * - SDP 合约名称存在（需要set的）
     *
     * @param isTeeChain teechain的默认合约为wasm合约
     * @return
     */
    public boolean isAMReady(boolean isTeeChain) {
        if (isTeeChain) {
            return ObjectUtil.isNotEmpty(amContractClientTeeWASM)
                    && ContractStatusEnum.CONTRACT_READY == amContractClientTeeWASM.getStatus()
                    && ObjectUtil.isNotEmpty(sdpContractClientTeeWASM)
                    && StrUtil.isNotEmpty(sdpContractClientTeeWASM.getContractAddress());
        } else {
            // 非tee链的情况下，evm合约是默认一定要处理的，故检查到evm合约可用即可
            return ObjectUtil.isNotEmpty(amContractClientEVM)
                    && ContractStatusEnum.CONTRACT_READY == amContractClientEVM.getStatus()
                    && ObjectUtil.isNotEmpty(sdpContractClientEVM)
                    && StrUtil.isNotEmpty(sdpContractClientEVM.getContractAddress());
        }

    }

    /**
     * 判断SDP合约是否ready，用于SDP合约的setAMAndDomain
     * - SDP 合约存在且为ready
     * - AM 合约名称存在（需要set的）
     * - domain 名称存在 （需要set的）
     *
     * @param isTeeChain teechain的默认合约为wasm合约
     * @return
     */
    public boolean isSDPReady(boolean isTeeChain) {
        if (isTeeChain) {
            return ObjectUtil.isNotEmpty(sdpContractClientTeeWASM)
                    && ContractStatusEnum.CONTRACT_READY == sdpContractClientTeeWASM.getStatus()
                    && ObjectUtil.isNotEmpty(amContractClientTeeWASM)
                    && StrUtil.isNotEmpty(amContractClientTeeWASM.getContractAddress())
                    && StrUtil.isNotEmpty(sdpContractClientTeeWASM.getLocalDomain());
        } else {
            return ObjectUtil.isNotEmpty(sdpContractClientEVM)
                    && ContractStatusEnum.CONTRACT_READY == sdpContractClientEVM.getStatus()
                    && ObjectUtil.isNotEmpty(this.amContractClientEVM)
                    && StrUtil.isNotEmpty(amContractClientEVM.getContractAddress())
                    && StrUtil.isNotEmpty(sdpContractClientEVM.getLocalDomain());
        }
    }
}

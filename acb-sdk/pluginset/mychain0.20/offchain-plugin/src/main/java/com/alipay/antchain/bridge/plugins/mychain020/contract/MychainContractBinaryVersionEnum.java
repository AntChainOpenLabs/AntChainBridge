package com.alipay.antchain.bridge.plugins.mychain020.contract;

import cn.hutool.core.util.ObjectUtil;
import lombok.Getter;

@Getter
public enum MychainContractBinaryVersionEnum {

    V0(
            "v0",
            "/contract/v0/wasm/auth_message.wasc", "/contract/v0/solidity/am_client_mychain020_0_0_1.bin", "/contract/v0/wasm/auth_message_tee.wasc",
            "/contract/v0/wasm/p2p_message.wasc", "/contract/v0/solidity/am_p2p_msg_mychain020_0_0_1.bin", "/contract/v0/wasm/p2p_message.wasc",
            "", "",
            "", "",
            "/contract/v0/wasm/jit/crosschain_sys.wasc"
    ),
    
    V1(
            "v1",
            "/contract/v1/wasm/auth_message.wasc", "/contract/v1/solidity/AuthMsg_sol_AuthMsg.bin", "/contract/v1/wasm/auth_message_tee.wasc",
                    "/contract/v1/wasm/sdp_message.wasc", "/contract/v1/solidity/SDPMsg_sol_SDPMsg.bin", "/contract/v1/wasm/sdp_message.wasc",
                    "/contract/v1/solidity/PtcHub_sol_PtcHub.bin", "/contract/v1/solidity/CommitteePtcVerifier_sol_CommitteePtcVerifier.bin",
                    "/contract/v1/solidity/Monitor_sol_Monitor.bin", "/contract/v1/solidity/MonitorVerifier_sol_MonitorVerifier.bin",
                    "/contract/v1/wasm/jit/crosschain_sys.wasc"
    );

    public static MychainContractBinaryVersionEnum selectBinaryByVersion(String version) {
        if (ObjectUtil.isNull(version)) {
            return V1;
        }
        switch (version.toLowerCase()) {
            case "v0":
                return V0;
            default:
                return V1;
        }
    }

    MychainContractBinaryVersionEnum(
            String version,
            String amClientWasm, String amClientEvm, String amClientTeeWasm,
            String sdpWasm, String sdpEvm, String sdpTeeWasm,
            String ptcHubEvm, String committeeVerifierEvm,
            String monitorEvm, String monitorVerifierEvm,
            String sysContractAllInOneWasm
    ) {
        this.version = version;

        this.amClientWasm = amClientWasm;
        this.amClientEvm = amClientEvm;
        this.amClientTeeWasm = amClientTeeWasm;

        this.sdpWasm = sdpWasm;
        this.sdpEvm = sdpEvm;
        this.sdpTeeWasm = sdpTeeWasm;

        this.ptcHubEvm = ptcHubEvm;
        this.committeeVerifierEvm = committeeVerifierEvm;
        this.monitorEvm = monitorEvm;
        this.monitorVerifierEvm = monitorVerifierEvm;

        this.sysContractAllInOneWasm = sysContractAllInOneWasm;
    }

    private String version;

    private String amClientWasm;

    private String amClientEvm;

    private String sdpWasm;

    private String sdpEvm;

    private String ptcHubEvm;

    private String committeeVerifierEvm;

    private String monitorEvm;

    private String monitorVerifierEvm;

    private String amClientTeeWasm;

    private String sdpTeeWasm;

    private String sysContractAllInOneWasm;
}

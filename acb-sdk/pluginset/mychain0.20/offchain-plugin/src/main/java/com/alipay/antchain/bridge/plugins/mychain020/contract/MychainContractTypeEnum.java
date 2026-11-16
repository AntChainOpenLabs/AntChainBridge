package com.alipay.antchain.bridge.plugins.mychain020.contract;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum MychainContractTypeEnum {
    NATIVE(0, "NATIVE CONTRACT"),
    EVM(1, "EVM CONTRACT"),
    WASM(2, "WASM CONTRACT"),
    UNKNOW(-1, "UNKNOW"),
    NATIVE_PRECOMPILE(-2, "NATIVE PRECOMPILE"),
    TEE_WASM(-3, "TEE WASM");

    private int typeCode;
    private String typeDesc;

    public static MychainContractTypeEnum fromDesc(String desc) {
        for (MychainContractTypeEnum v : MychainContractTypeEnum.values()) {
            if(v.getTypeDesc().equals(desc)) {
                return v;
            }
        }
        return null;
    }
}

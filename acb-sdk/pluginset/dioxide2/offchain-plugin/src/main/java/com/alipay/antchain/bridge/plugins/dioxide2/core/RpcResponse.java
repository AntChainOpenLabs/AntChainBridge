package com.alipay.antchain.bridge.plugins.dioxide2.core;

import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.annotation.JSONField;
import lombok.Data;

@Data
public class RpcResponse {

    /**
     * rsp 响应说明，标识是对哪个模块动作的响应
     */
    @JSONField(name = "rsp")
    private String rsp;

    /**
     * ret 返回结果
     * - 成功时是 JSON 对象
     * - 失败时是错误说明字符串
     */
    @JSONField(name = "ret")
    private Object ret;

    /**
     * err 响应失败时的错误码
     * 成功响应时为 null
     */
    @JSONField(name = "err")
    private Integer err;

    /**
     * 判断响应是否成功
     */
    @JSONField(serialize = false)
    public boolean isSuccess() {
        return err == null;
    }

    /**
     * 获取 ret 对象，如果是 JSON，返回 JSONObject
     */
    @JSONField(serialize = false)
    public JSONObject getSuccessResponse() {
        if (ret instanceof JSONObject) {
            return (JSONObject) ret;
        }
        return null;
    }

    /**
     * 获取 ret 字符串，如果失败时 ret 是 string
     */
    @JSONField(serialize = false)
    public String getFailResponse() {
        if (ret instanceof String) {
            return (String) ret;
        }
        return null;
    }
}

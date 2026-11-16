package com.alipay.antchain.bridge.plugins.mychain020;

import cn.hutool.core.util.ReflectUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.alipay.antchain.bridge.commons.bbc.DefaultBBCContext;
import com.alipay.antchain.bridge.commons.bbc.syscontract.ContractStatusEnum;
import com.alipay.antchain.bridge.commons.bbc.syscontract.MonitorContract;
import com.alipay.antchain.bridge.plugins.mychain020.sdk.Mychain020Client;
import org.junit.Assert;
import org.junit.Test;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;

import static org.mockito.Mockito.mock;

public class Mychain020BBCContextTest {

    @Test
    public void initMonitorContractShouldRestorePersistedMonitorAddress() {
        MonitorContract persistedMonitorContract = new MonitorContract();
        persistedMonitorContract.setContractAddress("upgraded_monitor_contract");
        persistedMonitorContract.setStatus(ContractStatusEnum.CONTRACT_DEPLOYED);

        DefaultBBCContext persistedContext = new DefaultBBCContext();
        persistedContext.setMonitorContract(persistedMonitorContract);

        Mychain020BBCContext context = new Mychain020BBCContext(
                persistedContext,
                LoggerFactory.getLogger(getClass()));
        ReflectUtil.invoke(context, "initMonitorContract", mock(Mychain020Client.class));

        Assert.assertEquals(
                "upgraded_monitor_contract",
                context.getMonitorContractClientEVM().getContractAddress());
        Assert.assertEquals(
                ContractStatusEnum.CONTRACT_DEPLOYED,
                context.getMonitorContractClientEVM().getStatus());
    }

    @Test
    public void encodeToBytesShouldExcludeRuntimeClients() {
        MonitorContract persistedMonitorContract = new MonitorContract();
        persistedMonitorContract.setContractAddress("monitor_contract");
        persistedMonitorContract.setStatus(ContractStatusEnum.CONTRACT_DEPLOYED);

        DefaultBBCContext persistedContext = new DefaultBBCContext();
        persistedContext.setMonitorContract(persistedMonitorContract);

        Mychain020BBCContext context = new Mychain020BBCContext(
                persistedContext,
                LoggerFactory.getLogger(getClass()));
        ReflectUtil.invoke(context, "initMonitorContract", mock(Mychain020Client.class));

        JSONObject encodedContext = JSON.parseObject(
                new String(context.encodeToBytes(), StandardCharsets.UTF_8));

        Assert.assertEquals(
                "monitor_contract",
                encodedContext.getJSONObject("monitor_contract").getString("contractAddress"));
        Assert.assertFalse(encodedContext.containsKey("monitorContractClientEVM"));
        Assert.assertFalse(encodedContext.containsKey("monitorVerifierContractEVM"));
        Assert.assertFalse(encodedContext.containsKey("logger"));
    }
}

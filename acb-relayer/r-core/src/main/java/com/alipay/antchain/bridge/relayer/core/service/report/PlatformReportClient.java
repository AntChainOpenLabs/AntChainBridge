package com.alipay.antchain.bridge.relayer.core.service.report;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.regex.Pattern;
import javax.annotation.PostConstruct;
import javax.annotation.Resource;

import cn.hutool.core.util.HexUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.alipay.antchain.bridge.commons.core.base.CrossChainMessageReceipt;
import com.alipay.antchain.bridge.commons.core.base.SendResponseResult;
import com.alipay.antchain.bridge.relayer.commons.model.SDPMsgCommitResult;
import com.alipay.antchain.bridge.relayer.commons.model.SDPMsgWrapper;
import com.alipay.antchain.bridge.relayer.commons.model.UniformCrosschainPacketContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Component
public class PlatformReportClient {

    private static final Pattern BARE_32_BYTE_HEX = Pattern.compile("^[0-9a-fA-F]{64}$");

    private static final Pattern PREFIXED_32_BYTE_HEX = Pattern.compile("^0[xX][0-9a-fA-F]{64}$");

    @Value("${relayer.platform_report.enabled:false}")
    private boolean enabled;

    @Value("${relayer.platform_report.endpoint:http://127.0.0.1:49200}")
    private String endpoint;

    @Value("${relayer.platform_report.api_key_path:file:conf/platform-report-api-key}")
    private String apiKeyPath;

    @Value("${relayer.platform_report.connect_timeout_ms:3000}")
    private int connectTimeoutMs;

    @Value("${relayer.platform_report.read_timeout_ms:5000}")
    private int readTimeoutMs;

    @Resource
    private ResourceLoader resourceLoader;

    @Resource
    private UcpReportJsonBuilder ucpReportJsonBuilder;

    private RestTemplate restTemplate;
    private String apiKey;

    @PostConstruct
    public void init() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeoutMs);
        requestFactory.setReadTimeout(readTimeoutMs);
        restTemplate = new RestTemplate(requestFactory);
        if (!enabled) {
            return;
        }
        try {
            byte[] bytes = readAllBytes(resourceLoader.getResource(apiKeyPath));
            apiKey = new String(bytes, StandardCharsets.UTF_8).trim();
            if (StrUtil.isEmpty(apiKey)) {
                log.error("platform report api key file {} is empty, reporting is disabled", apiKeyPath);
            }
        } catch (Exception e) {
            log.error("failed to read platform report api key from {}, reporting is disabled", apiKeyPath, e);
        }
    }

    public void reportUcp(UniformCrosschainPacketContext context) {
        if (!enabled || StrUtil.isEmpty(apiKey)) {
            return;
        }
        post("/api/cc-relayer/ucps", context.getUcpId(), ucpReportJsonBuilder.build(context));
    }

    public void reportRegulation(String ucpId, String status, String reason) {
        if (StrUtil.isEmpty(status)) {
            return;
        }
        JSONObject body = new JSONObject(true);
        body.put("ucpId", ucpId);
        body.put("status", status);
        body.put("reason", StrUtil.nullToEmpty(reason));
        body.put("data", new JSONObject(true));
        post("/api/cc-relayer/ucps/" + ucpId + "/regulation-status", ucpId, body);
    }

    public void reportTargetChainSubmission(String ucpId, SDPMsgWrapper message, SendResponseResult result) {
        if (StrUtil.isEmpty(result.getTxId())) {
            return;
        }
        JSONObject body = new JSONObject(true);
        body.put("ucpId", ucpId);
        body.put("targetChainProduct", message.getReceiverBlockchainProduct());
        body.put("targetBlockchainId", message.getReceiverBlockchainId());
        body.put("txHash", normalizeTargetTxHashForReport(result.getTxId()));
        body.put("submittedAt", formatTimestamp(result.getTxTimestamp()));
        if (ObjectUtil.isNotNull(result.getRawTx())) {
            body.put("rawTx", HexUtil.encodeHexStr(result.getRawTx()));
        }
        post("/api/cc-relayer/ucps/" + ucpId + "/target-chain-submission", ucpId, body);
    }

    public void reportTargetChainExecution(String ucpId, SendResponseResult result, boolean timeout) {
        if (!result.isConfirmed() || StrUtil.isEmpty(result.getTxId())) {
            return;
        }
        reportTargetChainExecution(
                ucpId,
                result.getTxId(),
                result.getTxTimestamp(),
                result.isSuccess(),
                timeout,
                result.getErrorMessage()
        );
    }

    public void reportTargetChainExecution(String ucpId, CrossChainMessageReceipt receipt, boolean timeout) {
        if (ObjectUtil.isNull(receipt) || !receipt.isConfirmed() || StrUtil.isEmpty(receipt.getTxhash())) {
            return;
        }
        reportTargetChainExecution(
                ucpId,
                receipt.getTxhash(),
                receipt.getTxTimestamp(),
                receipt.isSuccessful(),
                timeout,
                receipt.getErrorMsg()
        );
    }

    public void reportTargetChainExecution(String ucpId, SDPMsgCommitResult result) {
        if (!result.isConfirmed() || StrUtil.isEmpty(result.getTxHash())) {
            return;
        }
        reportTargetChainExecution(
                ucpId,
                result.getTxHash(),
                result.getBlockTimestamp(),
                result.isCommitSuccess(),
                result.isTimeout(),
                result.getFailReason()
        );
    }

    private void reportTargetChainExecution(
            String ucpId,
            String txHash,
            long timestamp,
            boolean success,
            boolean timeout,
            String errorMessage
    ) {
        JSONObject body = new JSONObject(true);
        body.put("ucpId", ucpId);
        body.put("txHash", normalizeTargetTxHashForReport(txHash));
        body.put("executedAt", formatTimestamp(timestamp));
        body.put("success", success);
        body.put("timeout", timeout);
        body.put("errorMessage", StrUtil.nullToEmpty(errorMessage));
        post("/api/cc-relayer/ucps/" + ucpId + "/target-chain-execution", ucpId, body);
    }

    /**
     * Normalise fixed-width hexadecimal transaction hashes at the regulatory reporting boundary.
     * Chain-native hashes kept by the relayer are intentionally not changed: Mychain requires the
     * unprefixed value for node queries, while Dioxide uses a non-hexadecimal native hash format.
     */
    static String normalizeTargetTxHashForReport(String txHash) {
        if (StrUtil.isEmpty(txHash)) {
            return txHash;
        }
        if (BARE_32_BYTE_HEX.matcher(txHash).matches()) {
            return "0x" + txHash;
        }
        if (PREFIXED_32_BYTE_HEX.matcher(txHash).matches()) {
            return "0x" + txHash.substring(2);
        }
        return txHash;
    }

    private void post(String path, String ucpId, JSONObject body) {
        if (!enabled || StrUtil.isEmpty(apiKey)) {
            return;
        }
        if (StrUtil.isEmpty(ucpId)) {
            log.warn("skip platform report at {} because ucp id is empty", path);
            return;
        }
        try {
            String requestUrl = StrUtil.removeSuffix(endpoint, "/") + path;
            String requestBody = body.toJSONString();
            log.info(
                    "platform report request: method=POST, url={}, ucpId={}, body={}",
                    requestUrl,
                    ucpId,
                    requestBody
            );
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("x-api-key", apiKey);
            ResponseEntity<String> response = restTemplate.exchange(
                    requestUrl,
                    HttpMethod.POST,
                    new HttpEntity<>(requestBody, headers),
                    String.class
            );
            if (!response.getStatusCode().is2xxSuccessful()) {
                log.error("platform report failed for ucp {} at {}: http status {}", ucpId, path, response.getStatusCodeValue());
                return;
            }
            JSONObject responseBody = JSON.parseObject(response.getBody());
            if (ObjectUtil.isNull(responseBody) || responseBody.getIntValue("code") != 0) {
                log.error("platform report failed for ucp {} at {}: response {}", ucpId, path, response.getBody());
                return;
            }
            log.info(
                    "platform report succeeded: method=POST, url={}, ucpId={}, httpStatus={}, response={}",
                    requestUrl,
                    ucpId,
                    response.getStatusCodeValue(),
                    response.getBody()
            );
        } catch (Exception e) {
            log.error("platform report failed for ucp {} at {}", ucpId, path, e);
        }
    }

    private String formatTimestamp(long timestamp) {
        return Instant.ofEpochMilli(timestamp > 0 ? timestamp : System.currentTimeMillis()).toString();
    }

    private byte[] readAllBytes(org.springframework.core.io.Resource resource) throws IOException {
        try (java.io.InputStream inputStream = resource.getInputStream();
             java.io.ByteArrayOutputStream outputStream = new java.io.ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int length;
            while ((length = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, length);
            }
            return outputStream.toByteArray();
        }
    }
}

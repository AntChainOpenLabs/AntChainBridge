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

package com.alipay.antchain.bridge.plugins.mychain020.model;

import java.io.IOException;
import java.lang.reflect.Type;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.annotation.JSONField;
import com.alibaba.fastjson.parser.DefaultJSONParser;
import com.alibaba.fastjson.parser.deserializer.ObjectDeserializer;
import com.alibaba.fastjson.serializer.JSONSerializer;
import com.alibaba.fastjson.serializer.ObjectSerializer;
import com.alipay.mychain.sdk.domain.transaction.LogEntry;
import com.alipay.mychain.sdk.domain.transaction.TransactionReceipt;
import com.alipay.mychain.sdk.vm.EVMOutput;
import lombok.*;
import org.bouncycastle.util.encoders.Hex;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CrossChainMsgLedgerData {

    public static class TransactionReceiptSerializer implements ObjectSerializer {
        @Override
        public void write(JSONSerializer serializer, Object object, Object fieldName, Type fieldType, int features) throws IOException {
            TransactionReceipt data = (TransactionReceipt) object;
            serializer.write(data.toString());
        }
    }

    public static class TransactionReceiptDeserializer implements ObjectDeserializer {
        @Override
        public <T> T deserialze(DefaultJSONParser parser, Type type, Object fieldName) {
            TransactionReceipt receipt = new TransactionReceipt();
            receipt.fromJson(JSON.parseObject(parser.parseObject(String.class)));
            return (T) receipt;
        }

        @Override
        public int getFastMatchToken() {
            return 0;
        }
    }

    public static CrossChainMsgLedgerData decode(byte[] data) {
        return JSON.parseObject(data, CrossChainMsgLedgerData.class);
    }

    private String amContractIdHex;

    private String logTopicHex;

    private int receiptIndex;

    private int logIndex;

    @JSONField(serializeUsing = TransactionReceiptSerializer.class, deserializeUsing = TransactionReceiptDeserializer.class)
    private TransactionReceipt receipt;

    public byte[] encode() {
        return JSON.toJSONBytes(this);
    }

    @JSONField(serialize = false, deserialize = false)
    public byte[] getCrossChainMessage() {
        LogEntry logEntry = receipt.getLogs().get(logIndex);

        if (!StrUtil.equals(amContractIdHex, logEntry.getTo().hexStrValue())) {
            throw new RuntimeException(
                    StrUtil.format("am contract id {} in data not match with {} in log entry",
                            amContractIdHex, logEntry.getTo().hexStrValue())
            );
        }
        if (!StrUtil.equals(logTopicHex, logEntry.getTopics().get(0))) {
            throw new RuntimeException(
                    StrUtil.format("log topic {} in data not match with {} in log entry",
                            logTopicHex, logEntry.getTopics().get(0))
            );
        }

        if (logEntry.getTopics().size() == 1) {
            return new EVMOutput(Hex.toHexString(logEntry.getLogData())).getBytes();
        }

        return new EVMOutput(logEntry.getTopics().get(1)).getBytes();
    }
}

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

package com.alipay.antchain.bridge.ptc.committee.monitor.node.commons.models;

import java.io.IOException;
import java.lang.reflect.Type;

import com.alibaba.fastjson.annotation.JSONField;
import com.alibaba.fastjson.serializer.JSONSerializer;
import com.alibaba.fastjson.serializer.ObjectSerializer;
import com.alipay.antchain.bridge.bcdns.service.BCDNSTypeEnum;
import com.alipay.antchain.bridge.commons.bcdns.utils.CrossChainCertificateUtil;
import com.alipay.antchain.bridge.commons.core.base.ObjectIdentity;
import com.alipay.antchain.bridge.ptc.committee.monitor.node.commons.enums.BCDNSStateEnum;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class BCDNSServiceDO {

    private String domainSpace;

    @JSONField(serialize = false, deserialize = false)
    private ObjectIdentity ownerOid;

    @JSONField(name = "domainSpaceCert", serializeUsing = DomainSpaceCertSerializer.class)
    private DomainSpaceCertWrapper domainSpaceCertWrapper;

    private BCDNSTypeEnum type;

    private BCDNSStateEnum state;

    private byte[] properties;

    public static class DomainSpaceCertSerializer implements ObjectSerializer {
        @Override
        public void write(JSONSerializer serializer, Object object, Object fieldName, Type fieldType, int features) throws IOException {
            serializer.write(CrossChainCertificateUtil.formatCrossChainCertificateToPem(((DomainSpaceCertWrapper) object).getDomainSpaceCert()));
        }
    }
}

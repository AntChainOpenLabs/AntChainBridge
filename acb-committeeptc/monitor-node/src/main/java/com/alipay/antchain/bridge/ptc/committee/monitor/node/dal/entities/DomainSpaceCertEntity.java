/*
 * Copyright 2023 Ant Group
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

package com.alipay.antchain.bridge.ptc.committee.monitor.node.dal.entities;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("domain_space_cert")
public class DomainSpaceCertEntity extends BaseEntity {

    @TableField("domain_space")
    private String domainSpace;

    @TableField("parent_space")
    private String parentSpace;

    @TableField("owner_oid_hex")
    private String ownerOidHex;

    @TableField("description")
    private String description;

    @TableField("domain_space_cert")
    private byte[] domainSpaceCert;
}

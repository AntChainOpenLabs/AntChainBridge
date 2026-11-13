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

package com.alipay.antchain.bridge.ptc.committee.monitor.node.dal.entities;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@TableName("tpbta")
public class TpBtaEntity extends BaseEntity {

    @TableField("version")
    private int version;

    @TableField("sender_domain")
    private String senderDomain;

    @TableField("bta_subject_version")
    private int btaSubjectVersion;

    @TableField("sender_id")
    private String senderId;

    @TableField("receiver_domain")
    private String receiverDomain;

    @TableField("receiver_id")
    private String receiverId;

    @TableField("tpbta_version")
    private int tpbtaVersion;

    @TableField("ptc_verify_anchor_version")
    private long ptcVerifyAnchorVersion;

    @TableField("raw_tpbta")
    private byte[] rawTpBta;
}

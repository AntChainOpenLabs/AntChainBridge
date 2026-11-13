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

import java.util.Date;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldNameConstants;

@Getter
@Setter
@FieldNameConstants
public class BaseEntity {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 数据创建时间
     */
    @TableField("gmt_create")
    private Date gmtCreate;

    /**
     * 数据更新时间
     */
    @TableField(value = "gmt_modified", update = "now()", updateStrategy = FieldStrategy.ALWAYS)
    private Date gmtModified;

    public void init() {
        Date now = new Date();
        this.setGmtCreate(now);
        this.setGmtModified(now);
    }

    public void update() {
        this.setGmtModified(new Date());
    }
}

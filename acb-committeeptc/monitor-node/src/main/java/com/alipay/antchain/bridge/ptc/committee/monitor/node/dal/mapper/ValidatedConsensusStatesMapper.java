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

package com.alipay.antchain.bridge.ptc.committee.monitor.node.dal.mapper;

import com.alipay.antchain.bridge.ptc.committee.monitor.node.dal.entities.ValidatedConsensusStatesEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface ValidatedConsensusStatesMapper extends BaseMapper<ValidatedConsensusStatesEntity> {

    @Select("SELECT * FROM validated_consensus_states WHERE domain = #{domain} AND height = (SELECT MAX(height) FROM validated_consensus_states WHERE domain = #{domain})")
    ValidatedConsensusStatesEntity getLatestValidatedConsensusState(@Param("domain") String domain);
}

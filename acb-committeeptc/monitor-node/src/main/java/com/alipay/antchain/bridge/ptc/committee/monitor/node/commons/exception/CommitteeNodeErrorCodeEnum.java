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

package com.alipay.antchain.bridge.ptc.committee.monitor.node.commons.exception;

import lombok.Getter;

@Getter
public enum CommitteeNodeErrorCodeEnum {

    UNKNOWN_INTERNAL_ERROR("0001", "internal error"),

    DAL_ERROR("0101", "data access layer error"),

    SERVER_INVALID_REQUEST("0201", "invalid request"),

    SERVER_VERIFY_BTA_ERROR("0202", "verify bta error"),

    SERVER_VERIFY_CONSENSUS_STATE_ERROR("0203", "verify consensus state error"),

    SERVER_VERIFY_CROSSCHAIN_MESSAGE_ERROR("0204", "verify crosschain message error"),

    SERVER_ENDORSE_BLOCK_STATE_ERROR("0205", "block state not validated yet");

    /**
     * Error code for errors happened in project {@code antchain-bridge-committee-node}
     */
    private final String errorCode;

    private final int errorCodeNum;

    /**
     * Every code has a short message to describe the error stuff
     */
    private final String shortMsg;

    CommitteeNodeErrorCodeEnum(String errorCode, String shortMsg) {
        this.errorCode = errorCode;
        this.errorCodeNum = Integer.parseInt(errorCode, 16);
        this.shortMsg = shortMsg;
    }
}

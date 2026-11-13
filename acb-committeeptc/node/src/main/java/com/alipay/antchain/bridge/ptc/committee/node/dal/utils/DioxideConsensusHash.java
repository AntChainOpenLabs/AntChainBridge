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

package com.alipay.antchain.bridge.ptc.committee.node.dal.utils;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Database lookup keys only. Never substitute these digests into a consensus state. */
public final class DioxideConsensusHash {
    private DioxideConsensusHash() {}

    public static String digest(byte[] original) {
        if (original == null) {
            throw new IllegalArgumentException("Missing original consensus hash");
        }
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(original));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Required SHA-256 unavailable", e);
        }
    }

    public static byte[] decode(String originalHex) {
        if (originalHex == null || originalHex.isEmpty()) {
            throw new IllegalArgumentException("Missing original consensus hash");
        }
        return HexFormat.of().parseHex(originalHex);
    }
}

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

package com.alipay.antchain.bridge.plugins.ethereum3.helper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import java.math.BigInteger;

import org.junit.Test;

public class EstimateGasLimitProviderTest {

    @Test
    public void shouldApplyPercentageAndFixedMargins() {
        assertEquals(
                BigInteger.valueOf(130L),
                EstimateGasLimitProvider.applySafetyMargin(BigInteger.valueOf(100L), 10L)
        );
        assertEquals(
                BigInteger.valueOf(2L),
                EstimateGasLimitProvider.applySafetyMargin(BigInteger.ONE, 0L)
        );
    }

    @Test
    public void shouldRejectNegativeInputs() {
        assertThrows(
                IllegalArgumentException.class,
                () -> EstimateGasLimitProvider.applySafetyMargin(BigInteger.valueOf(-1L), 0L)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> EstimateGasLimitProvider.applySafetyMargin(BigInteger.ONE, -1L)
        );
    }
}

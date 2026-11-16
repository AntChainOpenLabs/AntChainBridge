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

package com.alipay.antchain.bridge.plugins.mychain020;import java.nio.file.Files;
import java.nio.file.Paths;

import cn.hutool.core.codec.Base64;
import com.alipay.antchain.bridge.plugins.mychain020.model.MychainSubjectIdentity;

public class MychainBbcTools {

    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Usage: java -cp plugins/mychain020-bbc-${version}-plugin.jar com.alipay.antchain.bridge.plugins.mychain020.MychainBbcTools <method> [<arg1> <arg2> ...]");
            System.exit(1);
        }
        try {
            switch (args[0]) {
                case "buildMychainSubjectIdentity":
                    if (args.length < 2) {
                        System.out.println("Usage: java -cp plugins/mychain020-bbc-${version}-plugin.jar com.alipay.antchain.bridge.plugins.mychain020.MychainBbcTools buildMychainSubjectIdentity <path_to_conf_file>");
                        System.exit(1);
                    }
                    System.out.println(
                            "Your mychain subject identity in Base64 is: \n"
                                    + Base64.encode(MychainSubjectIdentity.decodeFromJson(Files.readAllBytes(Paths.get(args[1]))).encode())
                    );
                    break;
                default:
                    System.out.println("Unknown method: " + args[0]);
                    System.exit(1);
            }
        } catch (Throwable t) {
            t.printStackTrace(System.out);
        }
    }
}

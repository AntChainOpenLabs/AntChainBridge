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

package com.alipay.antchain.bridge.ptc.committee.monitor.node.cli.commands;

import java.io.IOException;
import java.net.Socket;

import cn.hutool.core.util.StrUtil;
import org.springframework.shell.Availability;
import org.springframework.shell.standard.ShellMethodAvailability;

public abstract class BaseCommands {

    public abstract String getAdminAddress();

    public abstract boolean needAdminServer();

    @ShellMethodAvailability
    public Availability baseAvailability() {
        if (needAdminServer()) {
            var addrArr = StrUtil.split(StrUtil.split(getAdminAddress(), "//").get(1), ":");

            if (!checkServerStatus(addrArr.get(0), Integer.parseInt(addrArr.get(1)))) {
                return Availability.unavailable(
                        StrUtil.format("admin server {} is unreachable", getAdminAddress())
                );
            }
        }

        return Availability.available();
    }

    private boolean checkServerStatus(String host, int port) {
        try {
            Socket socket = new Socket(host, port);
            socket.close();
            return true;
        } catch (IOException e) {
            return false;
        }
    }
}

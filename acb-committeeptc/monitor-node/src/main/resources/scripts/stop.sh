#!/bin/bash

#
# Copyright 2023 Ant Group
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

CURR_DIR="$(
  cd $(dirname $0)
  pwd
)"
source ${CURR_DIR}/print.sh

print_title

log_info "stop monitor-node now..."

ps -ewf | grep -e "monitor-node-.*\.jar" | grep -v grep | awk '{print $2}' | xargs kill
if [ $? -ne 0 ]; then
  log_error "failed to stop monitor-node"
  exit 1
fi

log_info "monitor-node stopped successfully"

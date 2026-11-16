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

CURR_DIR="$(cd `dirname $0`; pwd)"
source ${CURR_DIR}/print.sh

print_title

if [ ! -d ${CURR_DIR}/../tls_certs ]; then
	mkdir -p ${CURR_DIR}/../tls_certs
fi

openssl genrsa -out ${CURR_DIR}/../tls_certs/server.key 2048 > /dev/null 2>&1
if [ $? -ne 0 ]; then
    log_error "failed to generate server.key"
    exit 1
fi
openssl pkcs8 -topk8 -inform pem -in ${CURR_DIR}/../tls_certs/server.key -nocrypt -out ${CURR_DIR}/../tls_certs/server_pkcs8.key
if [ $? -ne 0 ]; then
    log_error "failed to generate pkcs8 server.key"
    exit 1
fi
mv ${CURR_DIR}/../tls_certs/server_pkcs8.key ${CURR_DIR}/../tls_certs/server.key
log_info "generate server.key successfully"

openssl req -new -x509 -days 36500 -key ${CURR_DIR}/../tls_certs/server.key -out ${CURR_DIR}/../tls_certs/server.crt -subj "/C=CN/ST=mykey/L=mykey/O=mykey/OU=mykey/CN=MONITOR-NODE"
if [ $? -ne 0 ]; then
    log_error "failed to generate server.crt"
    exit 1
fi
log_info "generate server.crt successfully"

if [ ! -f "trust.crt" ]; then
  cp ${CURR_DIR}/../tls_certs/server.crt ${CURR_DIR}/../tls_certs/trust.crt
  log_info "generate trust.crt successfully"
fi

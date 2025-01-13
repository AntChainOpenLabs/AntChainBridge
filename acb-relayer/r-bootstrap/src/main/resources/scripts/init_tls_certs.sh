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

openssl genrsa -out ${CURR_DIR}/../tls_certs/relayer.key 2048 > /dev/null 2>&1
if [ $? -ne 0 ]; then
    log_error "failed to generate relayer.key"
    exit 1
fi
openssl pkcs8 -topk8 -inform pem -in ${CURR_DIR}/../tls_certs/relayer.key -nocrypt -out ${CURR_DIR}/../tls_certs/relayer_pkcs8.key
if [ $? -ne 0 ]; then
    log_error "failed to generate pkcs8 relayer.key"
    exit 1
fi
mv ${CURR_DIR}/../tls_certs/relayer_pkcs8.key ${CURR_DIR}/../tls_certs/relayer.key
log_info "generate relayer.key successfully"

openssl req -new -x509 -days 36500 -key ${CURR_DIR}/../tls_certs/relayer.key -out ${CURR_DIR}/../tls_certs/relayer.crt -subj "/C=CN/ST=mykey/L=mykey/O=mykey/OU=mykey/CN=ACB-Relayer"
if [ $? -ne 0 ]; then
    log_error "failed to generate relayer.crt"
    exit 1
fi
log_info "generate relayer.crt successfully"
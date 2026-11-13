#!/bin/bash

if [ -n "$1" ]; then
    MYCDT_BIN=$1/
fi

CUR_DIR="$(cd `dirname $0`; pwd)"
TARGET_DIR=${CUR_DIR}/target
if [ ! -d ${TARGET_DIR} ]; then
    mkdir "${TARGET_DIR}"
fi

for i in `ls ${CUR_DIR}/schema/*.fbs`; do ${MYCDT_BIN}myflatc.sh -A ${CUR_DIR}/schema/ $i; done

for i in `ls ${CUR_DIR}/*.cpp`; do ${MYCDT_BIN}my++ $i -jit -compress -o ${TARGET_DIR}/$(basename $i .cpp).wasm; done

# 通过宏，编译出针对tee的合约
#${MYCDT_BIN}my++ ${CUR_DIR}/oracle_service.cpp -DMYCHAIN_TEE -jit -o ${TARGET_DIR}/oracle_service_tee.wasm
${MYCDT_BIN}my++ ${CUR_DIR}/auth_message.cpp -DMYCHAIN_TEE -jit -o ${TARGET_DIR}/auth_message_tee.wasm
${MYCDT_BIN}my++ ${CUR_DIR}/crosschain_sys.cpp -DMYCHAIN_TEE -jit -o ${TARGET_DIR}/crosschain_sys_tee.wasm

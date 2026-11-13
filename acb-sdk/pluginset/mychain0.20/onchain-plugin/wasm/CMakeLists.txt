# This file is intended to help including headers

cmake_minimum_required(VERSION 3.19)

project(crosschain_sys VERSION "0.1")

set(CMAKE_CXX_STANDARD 17)

if (NOT MYCDT_WASM_SYSROOT_INCLUDE_PATH)
    MESSAGE(FATAL_ERROR "you need to set -DMYCDT_WASM_SYSROOT_INCLUDE_PATH")
endif ()

MESSAGE(STATUS "your mycdt include path is ${MYCDT_WASM_SYSROOT_INCLUDE_PATH}")

FILE(GLOB_RECURSE SYS_SRCS
        "${CMAKE_SOURCE_DIR}/*.cpp"
        "${CMAKE_SOURCE_DIR}/*.hpp"
        )

ADD_LIBRARY(
        SRC SHARED
        ${SYS_SRCS}
)

TARGET_INCLUDE_DIRECTORIES(
        SRC PRIVATE
        ${MYCDT_WASM_SYSROOT_INCLUDE_PATH}
)

IF (GENERATE_MYBUFER)
    if (NOT MYCDT_BIN_PATH)
        MESSAGE(FATAL_ERROR "you need to set -DMYCDT_BIN_PATH")
    endif ()
    MESSAGE(STATUS "# compiling the mybuffer...")
    EXECUTE_PROCESS(
            COMMAND for i in `ls ${CMAKE_SOURCE_DIR}/schema/*.fbs`; do ${MYCDT_BIN_PATH}/myflatc.sh -A ${CMAKE_SOURCE_DIR}/schema/ $i; done
    )
ENDIF ()

IF (COMPILE_CONTRACT)
    if (NOT MYCDT_BIN_PATH)
        MESSAGE(FATAL_ERROR "you need to set -DMYCDT_BIN_PATH")
    endif ()
    SET(WASM_TARGET_DIR ${CMAKE_SOURCE_DIR}/target)
    FILE(MAKE_DIRECTORY ${WASM_TARGET_DIR})

    MESSAGE(STATUS "# compiling the crosschain sys contract code...")
    EXECUTE_PROCESS(
            COMMAND bash ${CMAKE_SOURCE_DIR}/compile_all_jit.sh ${MYCDT_BIN_PATH}
    )

    MESSAGE(STATUS "####### ALL COMPILED FILES UNDER DIRECTORY ${WASM_TARGET_DIR} #######")
ENDIF ()
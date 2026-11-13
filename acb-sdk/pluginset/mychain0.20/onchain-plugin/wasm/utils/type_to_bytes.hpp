
#ifndef TYPES_TO_BYTES_HPP
#define TYPES_TO_BYTES_HPP

#include <string>
#include <mychainlib/identity.hpp>
#include <mychainlib/types.h>
#include "utils.hpp"

// offset所指位置为右侧开区间，填充范围[offset-len, offset)
// 所有位置填充完毕后，offset值为0

namespace crosschain {
  void IdentityToBytes(const mychain::Identity& id, std::string& byte_array, uint32_t& offset) {
    mychain::Require(crosschain::IdentityLength <= offset, "CROSSCHAIN_ERROR: offset not enough for data");
    mychain::Require(offset <= byte_array.size(),  "CROSSCHAIN_ERROR: offset exceeds bytes array size");
    byte_array.replace(offset-crosschain::IdentityLength, crosschain::IdentityLength, id.get_data());
    offset -= crosschain::IdentityLength;
  }

  void Uint32ToBytes(const uint32_t& num, std::string& byte_array, uint32_t& offset) {
    mychain::Require(4 <= offset, "CROSSCHAIN_ERROR: offset not enough for data");
    mychain::Require(offset <= byte_array.size(), "CROSSCHAIN_ERROR: offset exceeds bytes array size");
    for (uint32_t i = 0; i < 4; i++) {
      byte_array.replace(offset - 1, 1, 1, static_cast<char>(num >> (i * 8)));
      offset -= 1;
    }
  }
  
  void StringToBytes(const std::string& str, std::string& byte_array, uint32_t& offset) {
    // 与Fabric一致，假设string size不超过uint32
    mychain::Require(str.size() <= UINT32_MAX, "CROSSCHAIN_ERROR: string length exceeds uint32");

    uint32_t set_zero_len = BytesReserveForString(str);
    mychain::Require(set_zero_len <= offset, "CROSSCHAIN_ERROR: offset not enough for data");
    mychain::Require(offset <= byte_array.size(), "CROSSCHAIN_ERROR: offset exceeds bytes array size");
    // string填充区域预置0
    byte_array.replace(offset-set_zero_len, set_zero_len, set_zero_len, 0);

    // 迁就EVM 256位对齐，使用32字节保存string长度
    uint32_t str_len = str.size();
    // 高地址填4字节
    Uint32ToBytes(str_len, byte_array, offset);
    // 填充空白28字节
    byte_array.replace(offset-28, 28, 28, 0);
    offset -= 28;

    // 每32字节为一个单位，逆序填充string内容
    std::string::const_iterator it = str.begin();
    std::string::iterator bytes_head = byte_array.begin();
    while(it != str.end()) {
      if ((it + 32) <= str.end()) {
        byte_array.replace(bytes_head+(long)offset-32, bytes_head+(long)offset, it, it+32);
        it += 32;
      } else {
          byte_array.replace(
            bytes_head+(long)offset-32, bytes_head+(long)offset-32+(str_len%32), it, it+(str_len%32));
        it += (str_len%32);
      }
      offset -= 32;
    }
  }
}

#endif
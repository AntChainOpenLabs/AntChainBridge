#ifndef BYTES_TO_TYPES_HPP
#define BYTES_TO_TYPES_HPP

#include <string>
#include <mychainlib/identity.hpp>
#include <mychainlib/types.h>
#include "utils.hpp"

namespace crosschain {
  // 逆序读取
  // offset所指位置为右侧开区间，读取范围[offset-len, offset)
  // 所有位置读取完毕后，offset值为0
  void BytesToIdentity(const std::string& byte_array, uint32_t& offset, mychain::Identity& id) {
    mychain::Require(crosschain::IdentityLength <= offset, "CROSSCHAIN_ERROR: offset not enough for data");
    mychain::Require(offset <= byte_array.size(), "CROSSCHAIN_ERROR: offset exceeds bytes array size");
    id = mychain::Identity(byte_array.substr(offset-crosschain::IdentityLength, crosschain::IdentityLength));
    offset -= crosschain::IdentityLength;
  }

  void BytesToUint32(const std::string& byte_array, uint32_t& offset, uint32_t& num) {
    mychain::Require(4 <= offset, "CROSSCHAIN_ERROR: offset not enough for data");
    mychain::Require(offset <= byte_array.size(), "CROSSCHAIN_ERROR: offset exceeds bytes array size");
    for (uint32_t i = 4; i > 0; i--) {
      num = (num<<8) + static_cast<unsigned char>(byte_array.at(offset-i));
    }
    offset -= 4;
  }

  void BytesToString(const std::string& byte_array, uint32_t& offset, std::string& str) {
    // 存放字符串长度的32字节，高位28字节为空，低位4字节存储长度
    mychain::Require(32 <= offset, "CROSSCHAIN_ERROR: offset not enough for data");
    mychain::Require(offset <= byte_array.size(), "CROSSCHAIN_ERROR: offset exceeds bytes array size");
    uint32_t str_len;
    BytesToUint32(byte_array, offset, str_len);
    offset -= 28; // 跳过剩余28空白字节

    mychain::Require(BytesReserveForString(str_len) <= (offset+32), "CROSSCHAIN_ERROR: offset not enough for data");
    str.clear();
    str.resize(str_len, 0);
    std::string::iterator it = str.begin();
    std::string::const_iterator bytes_head = byte_array.begin();
    while (it != str.end()) {
      if ((it + 32) <= str.end()) {
        str.replace(it, it+32, bytes_head+(long)offset-32, bytes_head+(long)offset);
        it += 32;
      } else {
          str.replace(it, it+(str_len%32),
            bytes_head+(long)offset-32, bytes_head+(long)offset-32+(str_len%32));
        it += (str_len%32);
      }
      offset -= 32;
    }
  }

  // 顺序读取
  void SequentialBytesToUint32(const std::string& byte_array, uint32_t& offset, uint32_t& num) {
    uint32_t offset_end = offset + 4;
    BytesToUint32(byte_array, offset_end, num);
    offset += 4;
  }

  void SequentialBytesToString(const std::string& byte_array, uint32_t& offset, std::string& str) {
    uint32_t str_len;
    SequentialBytesToUint32(byte_array, offset, str_len);
    // Sequential string
    str.replace(str.begin(), str.end(), byte_array.begin() + (long)offset, byte_array.begin() + (long)offset + (long)str_len);
    offset += str_len;
  }

  void SequentialBytesToString32BytesFormated(const std::string& byte_array, uint32_t& offset, std::string& str) {
    uint32_t str_len;
    offset += 28;
    SequentialBytesToUint32(byte_array, offset, str_len);
    // Sequential string
    str.replace(str.begin(), str.end(), byte_array.begin() + (long)offset, byte_array.begin() + (long)offset + (long)str_len);
    offset += BytesReserveForString(str_len) - 32;
  }
}

#endif
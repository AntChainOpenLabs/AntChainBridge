#ifndef CROSSCHAIN_UTILS_HPP
#define CROSSCHAIN_UTILS_HPP

#pragma once
#include <cstddef>
#include <string>
#include <mychainlib/developer_api.hpp>

// #define CROSSCHAIN_TEE_DEBUG

#ifdef CROSSCHAIN_DEBUG
#define CROSSCHAIN_DEBUG_LOG(...) do { mychain::Log(__VA_ARGS__); } while (0)

#elif defined CROSSCHAIN_TEE_DEBUG
void CROSSCHAIN_DEBUG_LOG(const std::string& data, const std::vector<std::string>& topics) { 
    const std::string TEE_UNENCRYPT = "unencrypt";
    std::vector<std::string> tee_topics = {TEE_UNENCRYPT};
    tee_topics.insert(tee_topics.end(), topics.begin(), topics.end());
    mychain::Log(data, tee_topics); 
}

#else
#define CROSSCHAIN_DEBUG_LOG(...)
#endif

namespace crosschain {
  constexpr uint32_t HashLength = 32;
  constexpr uint32_t IdentityLength = 32;
  
  const uint32_t value = 0; // TODO
  const uint32_t gas = 0;

  uint32_t BytesReserveForString(const uint32_t& str_len){
    mychain::Require(str_len <= UINT32_MAX, "CROSSCHAIN_ERROR: string length exceeds uint32");
    uint32_t reserve = (0 == str_len) ? 0 : (str_len - 1) / 32 + 1;
    return ++reserve * 32;
  }

  uint32_t BytesReserveForString(const std::string& str){
    return BytesReserveForString(str.size());
  }

  template<typename DT, typename T>
  T RecursiveDigest(DT digest_type, T str) {
    std::string out;
    mychain::Require(mychain::Digest(str, digest_type, out), "CROSSCHAIN_ERROR: RecursiveDigest failed");
    return out;
  }

  template<typename DT, typename T, typename... Types>
  T RecursiveDigest(DT digest_type, T str, Types... reset) {
    std::string in(RecursiveDigest(digest_type, str) + RecursiveDigest(digest_type, reset...));
    std::string out;
    mychain::Require(mychain::Digest(in, digest_type, out), "CROSSCHAIN_ERROR: RecursiveDigest failed");
    return out;
  }

  template<typename... Types>
  std::string SM3RecursiveDigest(Types... strs) {
    return RecursiveDigest(mychain::SM3, strs...);
  }

  template<typename... Types>
  std::string SHA256RecursiveDigest(Types... strs) {
    return RecursiveDigest(mychain::SHA256, strs...);
  }

  bool ReadUint8(const std::string& str, const uint32_t& offset, uint8_t& val){
      if(offset >= str.length()) {
          return false;
      }
      val = static_cast<unsigned char> (str.at(offset + 0));
      return true;
  }

  bool ReadUint16LittleEndian(const std::string& str, const uint32_t& offset, uint16_t& val){
      if(offset >= str.length()) {
          return false;
      }
      uint32_t ch1 = static_cast<unsigned char> (str.at(offset + 1));
      uint32_t ch2 = static_cast<unsigned char> (str.at(offset + 0));
      // char ch1 = str.at(offset + 1);
      // char ch2 = str.at(offset + 0);
      val = ((ch1 << 8) + (ch2 << 0));
      return true;
  }

  bool ReadUint32LittleEndian(const std::string& str, const uint32_t& offset, uint32_t& val){
      if(offset + 3 >= str.length()) {
          return false;
      }
      uint32_t ch1 = static_cast<unsigned char> (str.at(offset + 3));
      uint32_t ch2 = static_cast<unsigned char> (str.at(offset + 2));
      uint32_t ch3 = static_cast<unsigned char> (str.at(offset + 1));
      uint32_t ch4 = static_cast<unsigned char> (str.at(offset + 0));
      val = ((ch1 << 24) + (ch2 << 16) + (ch3 << 8) + (ch4 << 0));
      return true;
  }

  bool ReadUint32(const std::string& str, const uint32_t& offset, uint32_t& val){
      if(offset + 3 >= str.length()) {
          return false;
      }
      uint32_t ch1 = static_cast<unsigned char> (str.at(offset + 0));
      uint32_t ch2 = static_cast<unsigned char> (str.at(offset + 1));
      uint32_t ch3 = static_cast<unsigned char> (str.at(offset + 2));
      uint32_t ch4 = static_cast<unsigned char> (str.at(offset + 3));
      val = ((ch1 << 24) + (ch2 << 16) + (ch3 << 8) + (ch4 << 0));
      return true;
  }

  bool StartsWithCaseInsensitive(std::string mainStr, std::string toMatch)
  {
    // Convert mainStr to lower case
    std::transform(mainStr.begin(), mainStr.end(), mainStr.begin(), ::tolower);
    // Convert toMatch to lower case
    std::transform(toMatch.begin(), toMatch.end(), toMatch.begin(), ::tolower);
  
    if(mainStr.find(toMatch) == 0)
      return true;
    else
      return false;
  }
}

#endif
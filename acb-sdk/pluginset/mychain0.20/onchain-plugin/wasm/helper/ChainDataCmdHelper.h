#ifndef CHAIN_DATA_CMD_HELPER_H_
#define CHAIN_DATA_CMD_HELPER_H_

#include <mychainlib/contract.h>

namespace odats {

    enum Base {BASE16, BASE58, BASE64};
    enum Hash {SHA2_256, SHA3_256, KECCAK256};
    enum Content {MYCHAIN010_BLOCK, MYCHAIN010_TX, MYCHAIN010_HEADER};

    const uint8_t UDAG_CID_VERSION = 0x01;

    const std::string BASE16_CODEC = "f";
    const std::string BASE58_CODEC = "b";
    const std::string BASE64_CODEC = "m";

    const uint8_t sha2_256_codec = 0x32;
    const uint8_t sha3_256_codec = 0x35;
    const uint8_t keccak256_codec = 0x39;

    const uint8_t MYCHAIN010_BLOCK_CODEC = 0xA5;
    const uint8_t MYCHAIN010_TX_CODEC = 0xA6;
    const uint8_t MYCHAIN010_HEADER_CODEC = 0xA8;

    std::string VarintEncoding(const uint8_t n);
    std::string VarintEncoding(const uint16_t n);
    std::string VarintEncoding(const uint32_t n);
    std::string VarintEncoding(const uint64_t n);
    std::string BuildMychain020TxCID(const std::string &_hash);
    std::string BuildCommand(std::string _domain, std::string _cid, std::string _path, std::string _parser_cmd);

    std::string BuildMychain020TxCID(const std::string &_hash) {

        std::string multihash = odats::VarintEncoding(sha2_256_codec) + odats::VarintEncoding(static_cast<uint64_t>(_hash.length())) + _hash;
        std::string multicontent = odats::VarintEncoding(UDAG_CID_VERSION) + odats::VarintEncoding(MYCHAIN010_TX_CODEC) + multihash;
        std::string multibase = mychain::Bin2Hex(multicontent);
        std::string content_id = BASE16_CODEC + multibase;
        
        return content_id;
    }

    std::string BuildCommand(std::string _domain, std::string _cid, std::string _path, std::string _parser_cmd) {

        std::string udag_cmd = "udag://" + _domain + "/" + _cid;
        
        if(_path != ""){
            udag_cmd += ("/" + _path);
        }

        if(_parser_cmd != ""){
            udag_cmd += (" --json-path "  + _parser_cmd);
        }

        return udag_cmd;
    }

    std::string BuildCommand(std::string _domain, std::string _cid) {

        return odats::BuildCommand(_domain, _cid, "", "");
    }

    std::string VarintEncoding(const uint8_t n) {
        return odats::VarintEncoding(static_cast<uint64_t>(n));
    }

    std::string VarintEncoding(const uint16_t n) {
        return odats::VarintEncoding(static_cast<uint64_t>(n));
    }

    std::string VarintEncoding(const uint32_t n) {
        return odats::VarintEncoding(static_cast<uint64_t>(n));
    }

    std::string VarintEncoding(uint64_t n) {

        std::string os;

        uint8_t buf[9] = {0};
        for (size_t i = 0; i < sizeof(uint64_t); i++) {
            auto a = (uint8_t)(n & 0xFF);
            n >>= 7;
            if (!n) {
                buf[i] = a;
                i++;
                std::string output(&buf[0], &buf[i]);
                os = output;
                break;
            } else {
                buf[i] = (uint8_t)(a | 0x80);
            }
        }

        return os;
    }

    
    
}

#endif  // CHAIN_DATA_CMD_HELPER_H_
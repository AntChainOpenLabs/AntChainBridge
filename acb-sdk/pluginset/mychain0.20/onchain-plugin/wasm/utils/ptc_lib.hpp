#ifndef CROSSCHAIN_SYS_PTC_LIB_HPP
#define CROSSCHAIN_SYS_PTC_LIB_HPP

#include <cstddef>
#include <string>
#include <mychainlib/developer_api.hpp>
#include "./tlv_utils.hpp"

using namespace crosschain;
using namespace mychain;

struct ThirdPartyResp {
public:
    const uint16_t PTC_RESP_FIELD_BODY = 0;

    void decode(const std::string &raw) {
        TLVPacket p;
        p.decode(raw);

        for (const auto &item: p.items) {
            if (item.tag == PTC_RESP_FIELD_BODY) {
                this->body = item.value;
            }
        }
    }

    std::string body;
};

struct CrossChainChannel {
    const uint16_t TAG_SENDER_DOMAIN = 0;
    const uint16_t TAG_RECEIVER_DOMAIN = 1;

    void decode(const std::string &raw) {
        TLVPacket p;
        p.decode(raw);

        for (const auto &item: p.items) {
            if (item.tag == TAG_SENDER_DOMAIN) {
                this->sender_domain = item.value;
            } else if (item.tag == TAG_RECEIVER_DOMAIN) {
                this->receiver_domain = item.value;
            }
        }
    }

    std::string sender_domain;
    std::string receiver_domain;
};

struct CrossChainLane {
    const uint16_t TAG_CROSS_CHAIN_CHANNEL = 0;
    const uint16_t TAG_SENDER_ID = 1;
    const uint16_t TAG_RECEIVER_ID = 2;

    void decode(const std::string &raw) {
        TLVPacket p;
        p.decode(raw);

        for (const auto &item: p.items) {
            if (item.tag == TAG_CROSS_CHAIN_CHANNEL) {
                this->channel.decode(item.value);
            } else if (item.tag == TAG_SENDER_ID) {
                this->sender_id = Identity(item.value);
            } else if (item.tag == TAG_RECEIVER_ID) {
                this->receiver_id = Identity(item.value);
            }
        }
    }

    CrossChainChannel channel;
    Identity sender_id;
    Identity receiver_id;
};

struct ThirdPartyProof {
public:
    const uint16_t TP_PROOF_TPBTA_VERSION = 0x0100;

    const uint16_t TLV_PROOF_TPBTA_CROSSCHAIN_LANE = 0x0101;

    // tags from ODATS
    const uint16_t TLV_ORACLE_PUBKEY_HASH = 0;
    const uint16_t TLV_ORACLE_REQUEST_ID = 1;
    const uint16_t TLV_ORACLE_REQUEST_BODY = 2;
    const uint16_t TLV_ORACLE_SIGNATURE_TYPE = 3;
    const uint16_t TLV_ORACLE_REQUEST = 4;
    const uint16_t TLV_ORACLE_RESPONSE_BODY = 5;  // 这里填充RESPONSE 内容
    const uint16_t TLV_ORACLE_RESPONSE_SIGNATURE = 6;
    const uint16_t TLV_ORACLE_ERROR_CODE = 7;
    const uint16_t TLV_ORACLE_ERROR_MSG = 8;
    const uint16_t TLV_PROOF_SENDER_DOMAIN = 9;

    const uint16_t TP_PROOF_RAW_PROOF = 0x01ff;

    void decode(const std::string &raw) {
        TLVPacket p;
        p.decode(raw);

        for (const auto &item: p.items) {
            if (item.tag == TLV_ORACLE_RESPONSE_BODY) {
                this->resp.decode(item.value);
            } else if (item.tag == TLV_PROOF_TPBTA_CROSSCHAIN_LANE) {
                this->tpbta_crosschain_lane.decode(item.value);
            } else if (item.tag == TP_PROOF_TPBTA_VERSION) {
                ReadUint32LittleEndian(item.value, 0, this->tpbta_version);
            } else if (item.tag == TP_PROOF_RAW_PROOF) {
                this->raw_proof = item.value;
            }
        }
    }

    ThirdPartyResp resp;
    CrossChainLane tpbta_crosschain_lane;
    uint32_t tpbta_version;
    std::string raw_proof;
};

#endif //CROSSCHAIN_SYS_PTC_LIB_HPP

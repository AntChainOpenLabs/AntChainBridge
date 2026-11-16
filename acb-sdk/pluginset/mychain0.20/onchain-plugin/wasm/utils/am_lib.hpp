#ifndef AM_LIB_HPP
#define AM_LIB_HPP

#pragma once
#include <string>
#include <mychainlib/identity.hpp>
#include <mychainlib/types.h>
#include "utils.hpp"
#include "tlv_utils.hpp"

enum TLVProofTag {
    TLV_PROOF_PUBKEY_HASH,
    TLV_PROOF_REQUEST_ID,
    TLV_PROOF_REQUEST_BODY,
    TLV_PROOF_SIGNATURE_TYPE,
    TLV_PROOF_REQUEST,
    TLV_PROOF_RESPONSE_BODY,
    TLV_PROOF_RESPONSE_SIGNATURE,
    TLV_PROOF_ERROR_CODE,
    TLV_PROOF_ERROR_MSG,
    TLV_PROOF_SENDER_DOMAIN,
    TLV_PROOF_VERSION};

const uint16_t VERSION_SIMPLE_PROOF = 1;

namespace crosschain {
    struct Request {
        std::string req_id;
        std::string raw_req_body;
    };

    struct Proof {
        struct Request req;
        std::string raw_resp_body;
        uint32_t error_code;
        std::string error_msg;
        std::string sender_domain;
        uint16_t version;
    };

    bool DecodeRequestFromBytes(const std::string& raw_data, Request& req){
         uint32_t offset = 6;
         while(offset < raw_data.length()){
            TLVItem item;
            if(!ParseTLVItem(raw_data, offset, item)) return false;

            if(item.tag == TLV_PROOF_REQUEST_ID) {
                req.req_id = item.value;
            } else if(item.tag == TLV_PROOF_REQUEST_BODY){
                req.raw_req_body = item.value;
            }
         }

         return true;
    }

    bool DecodeProof(const std::string& raw_proof, std::string& domain, std::string& pkg){
        Proof proof;
        uint32_t offset = 6;
        while (offset < raw_proof.length()) {
            TLVItem item;
            if(!ParseTLVItem(raw_proof, offset, item)) return false;

            switch(item.tag){
            case TLV_PROOF_REQUEST:
                if(!DecodeRequestFromBytes(item.value, proof.req)) return false;
                break;
            case TLV_PROOF_RESPONSE_BODY:
                proof.raw_resp_body = item.value;
                break;
            case TLV_PROOF_ERROR_CODE:
                if(!ReadUint32LittleEndian(item.value, 0, proof.error_code)){ return false; }
                break;
            case TLV_PROOF_ERROR_MSG:
                proof.error_msg = item.value;
                break;
            case TLV_PROOF_SENDER_DOMAIN:
                proof.sender_domain = item.value;
                break;
            case TLV_PROOF_VERSION:
                if(!ReadUint16LittleEndian(item.value, 0, proof.version)){ return false; }
                break;
            default:
                break;
            }
        }

        domain = proof.sender_domain;

        mychain::Require(proof.raw_resp_body.length() > 12, "CROSSCHAIN_ERROR: illegal length of udag resp");
        uint32_t l = 0;
        mychain::Require(ReadUint32LittleEndian(proof.raw_resp_body, 8, l), "CROSSCHAIN_ERROR: decode proof resp body failed");
        mychain::Require(proof.raw_resp_body.length() >= 12 + l, "CROSSCHAIN_ERROR: illegal length of udag resp");

        pkg.replace(pkg.begin(), pkg.end(), proof.raw_resp_body.begin() + 12, proof.raw_resp_body.begin() + 12 + (long)l);

        return true;
    }
}

#endif
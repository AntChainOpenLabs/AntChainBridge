#ifndef CROSSCHAIN_SYS_AUTH_MESSAGE_HPP
#define CROSSCHAIN_SYS_AUTH_MESSAGE_HPP

//#define CROSSCHAIN_DEBUG
#include <mychainlib/contract.h>
#include <mychainlib/developer_api.hpp>
#include "schema/auth_message_ant_generated.h"
#include "utils/type_to_bytes.hpp"
#include "utils/bytes_to_type.hpp"
#include "utils/utils.hpp"
#include "utils/am_lib.hpp"
#include "utils/ptc_lib.hpp"

#define AUTH_MESSAGE_INTERFACES \
    (SetProtocol) \
    (AddRelayers)(QueryRelayersNum)(QueryRelayers) \
    (RecvFromProtocol)(RecvPkgFromRelayer)(RecvPkgFromRelayerTrusted) \
    (RecvNotaryPkgFromRelayer) (GetCachedAuthMsg) (RemoveCachedAuthMsg) (SetPtcHub)

using namespace mychain;
using namespace crosschain;
using namespace Crosschain::AuthMsgSpace;
using std::string;

class AuthMsg : public Contract {
protected:

    const std::string TEE_UNENCRYPT_AM = "unencrypt";

    AuthMsgSchemaMPtr m_p;

    void OnlyAdmins() {
        Require(m_p->get_contract_admins()->has_element(GetSender().get_data()),
                "AMMSG_ERROR: not from admin");
    }

    void OnlyAllowedRelayers() {
        Require(m_p->get_allowed_relayers()->has_element(GetSender().get_data()),
                "AMMSG_ERROR: not from allowed relayer");
    }

    void OnlyAllowedProtocols() {
        Require(m_p->get_allowed_protocols()->has_element(GetSender().get_data()),
                "AMMSG_ERROR: not from allowed protocol");
    }

    void ParseAMPackage(const string &pkg, Identity &sender_id, uint32_t &protocol_type, string &msg) {
        uint32_t offset = pkg.size();
        uint32_t version;
        BytesToUint32(pkg, offset, version);
        Require(1 == version, "AMMSG_ERROR: non supported AM package version");
        BytesToIdentity(pkg, offset, sender_id);
        BytesToUint32(pkg, offset, protocol_type);
        BytesToString(pkg, offset, msg);
        Require(0 == offset, "AMMSG_ERROR: offset incorrect ParseAMPackage");
    }

    virtual void DoUDAGProofData(const string &proof, const string &hints, bool if_verify) {
        string domain_name;
        string pkg;

        ThirdPartyProof tp_proof;
        tp_proof.decode(proof);
        if (!tp_proof.raw_proof.empty()) {
            domain_name = tp_proof.tpbta_crosschain_lane.channel.sender_domain;
            pkg = tp_proof.resp.body;

            // call ptc hub to verify proof
            auto ret = CallContract<void>(
                    Identity(m_p->get_ptc_hub_contract()), \
                              "verifyProof(bytes)", value, gas, proof);
            Require(0 == ret.code, "AMMSG_ERROR: verify tp-proof failed: " + ret.msg);
        } else {
            Require(DecodeProof(proof, domain_name, pkg), "AMMSG_ERROR: decode proof failed");
        }

        ForwardAMPkg(domain_name, pkg);
    }

    virtual void ForwardAMPkg(const string &domain_name, const string &pkg) {
        Identity sender_id;
        uint32_t protocol_type;
        string msg;
        ParseAMPackage(pkg, sender_id, protocol_type, msg);
        CROSSCHAIN_DEBUG_LOG(""s, { "0002", "0002", domain_name, sender_id.get_data(), msg });

        Require(m_p->get_protocol_routes()->has_element(std::to_string(protocol_type)),
                "AMMSG_ERROR: protocol contract not set for this protocol type");
        auto proto_ret = CallContract<void>(
                m_p->get_protocol_routes()->get_element(std::to_string(protocol_type))->get_protocol_id(), \
                              "RecvMessage", value, gas, domain_name, sender_id, msg);
        Require(0 == proto_ret.code, "AMMSG_ERROR: calling Protocol contract RecvMessage Failed: " + proto_ret.msg);
    }

    void RecvPkgFromRelayerCore(const string &pkg, bool if_verify) {
#ifdef MYCHAIN_TEE
        Log(""s, {TEE_UNENCRYPT_AM, "RecvAuthMessage", GetTxHash(), GetSelf().get_data()});
#else
        Log(GetTxHash(), {"RecvAuthMessage"});
#endif
        CROSSCHAIN_DEBUG_LOG(""s, { "0000", "into recvPkgFromRelayer" });
        OnlyAllowedRelayers();
        uint32_t offset = 0;
        CROSSCHAIN_DEBUG_LOG(""s, { "0000" });

        while (offset < pkg.size()) {
            string hints;
            string proof;
            SequentialBytesToString(pkg, offset, hints);
            SequentialBytesToString(pkg, offset, proof);
            CROSSCHAIN_DEBUG_LOG(""s, { "0001", hints, proof, pkg });
            DoUDAGProofData(proof, hints, if_verify);
        }
        Require(pkg.size() == offset, "AMMSG_ERROR: offset incorrect RecvPkgFromRelayerCore");
    }

public:
    AuthMsg() {
        m_p = GetAuthMsgSchemaM();
    }

    INTERFACE void init() {
        Crosschain::AuthMsgSpace::InitRoot();
        m_p = GetAuthMsgSchemaM();
        m_p->get_contract_admins()->add_element(GetSender().get_data());
        Log(""s, {"AddAdmin", GetSender().get_data()});
    }

    INTERFACE void AddRelayers(const string &relayer_id_str) {
        CROSSCHAIN_DEBUG_LOG(""s, { "00", relayer_id_str, Hex2Bin(relayer_id_str), m_p->get_myoracle_contract() });
        OnlyAdmins();
        CROSSCHAIN_DEBUG_LOG(""s, { "02", relayer_id_str, Hex2Bin(relayer_id_str) });
        Identity relayer_id(relayer_id_str);

        CROSSCHAIN_DEBUG_LOG(""s, { "01", relayer_id.to_hex(), relayer_id.get_data() });
        m_p->get_allowed_relayers()->add_element(relayer_id.get_data());
        m_p->get_relayer_list()->append_element()->set_relayer_id(relayer_id.get_data());
        Log(""s, {"AddRelayer", relayer_id.get_data()});
    }

    INTERFACE uint32_t QueryRelayersNum() {
        OnlyAdmins();
        return m_p->get_relayer_list()->size();
    }

    INTERFACE std::vector<string> QueryRelayers() {
        OnlyAdmins();
        std::vector<string> result;
        for (uint32_t i = 0; i < m_p->get_relayer_list()->size(); i++)
            result.push_back(m_p->get_relayer_list()->get_element(i)->get_relayer_id());
        return result;
    }

    INTERFACE void SetProtocol(const string &protocol_id_str, const uint32_t &protocol_type) {
        OnlyAdmins();
        const Identity protocol_id = Identity(protocol_id_str);
        m_p->get_allowed_protocols()->add_element(protocol_id.get_data())->set_protocol_type(protocol_type);
        Log(""s, {"AddProtocol", protocol_id.get_data(), std::to_string(protocol_type)});
        m_p->get_protocol_routes()->add_element(std::to_string(protocol_type))->set_protocol_id(protocol_id.get_data());
        Log(""s, {"AddProtocolRoute", std::to_string(protocol_type), protocol_id.get_data()});
    }

    INTERFACE void SetPtcHub(const string &ptc_hub_contract) {
        OnlyAdmins();
        const Identity ptc_hub_contract_id = Identity(ptc_hub_contract);
        m_p->set_ptc_hub_contract(ptc_hub_contract_id.get_data());
    }

    INTERFACE void RecvFromProtocol(const string &sender_id_str, const string &msg) {
        CROSSCHAIN_DEBUG_LOG(""s, { "0000", "into recvFromProtocol" });
        OnlyAllowedProtocols();
        Require(BytesReserveForString(msg) < (UINT32_MAX - 4 - 32 - 4),
                "AMMSG_ERROR: msg too large at RecvFromProtocol");
        const Identity sender_id = Identity(sender_id_str);
        uint32_t protocol_type = m_p->get_allowed_protocols()->get_element(GetSender().get_data())->get_protocol_type();
        uint32_t msg_size = BytesReserveForString(msg.size()) + 4 + 32 + 4;
        string pkg(32 + BytesReserveForString(msg_size), 0); // like EVM logdata
        uint32_t offset = 32 + 32 + msg_size;
        uint32_t version = 1;

        Uint32ToBytes(version, pkg, offset);
        IdentityToBytes(sender_id, pkg, offset);
        Uint32ToBytes(protocol_type, pkg, offset);
        StringToBytes(msg, pkg, offset);

        // compatible
        Uint32ToBytes(msg_size, pkg, offset);
        offset -= 28;
        Uint32ToBytes(32, pkg, offset);
        offset -= 28;

        Require(0 == offset, "AMMSG_ERROR: offset incorrect RecvFromProtocol");
        // emit SendAuthMessage
#ifdef MYCHAIN_TEE
        std::string pkg_hash = crosschain::SHA256RecursiveDigest(pkg);
        Log(""s, {TEE_UNENCRYPT_AM, "SendAuthMessage", pkg_hash, GetSelf().get_data()});
        m_p->get_cached_auth_msgs()->add_element(Bin2Hex(pkg_hash))->set_auth_msg(pkg);
#else
        Log("SendAuthMessage"s, {"SendAuthMessage", pkg});
#endif
    }

    INTERFACE std::string GetCachedAuthMsg(std::string msg_hash) {
        OnlyAllowedRelayers();
        if (!m_p->get_cached_auth_msgs()->has_element(msg_hash)) return "";
        return m_p->get_cached_auth_msgs()->get_element(msg_hash)->get_auth_msg();
    }

    INTERFACE void RemoveCachedAuthMsg(std::vector<std::string> msg_hashs) {
        OnlyAllowedRelayers();
        for (auto &msg_hash: msg_hashs) {
            m_p->get_cached_auth_msgs()->delete_element(msg_hash);
        }
    }

    INTERFACE void RecvPkgFromRelayer(const string &pkg) {
        RecvPkgFromRelayerCore(pkg, true);
    }

    INTERFACE void RecvPkgFromRelayerTrusted(const string &pkg) {
        RecvPkgFromRelayerCore(pkg, false);
    }

    INTERFACE void RecvNotaryPkgFromRelayer(const string &domain_name, const string &pkg) {
#ifdef MYCHAIN_TEE
        Log(""s, {TEE_UNENCRYPT_AM, "RecvAuthMessage", GetTxHash(), GetSelf().get_data()});
#else
        Log(GetTxHash(), {"RecvAuthMessage"});
#endif
        OnlyAllowedRelayers();
        uint32_t offset = 0;
        CROSSCHAIN_DEBUG_LOG(""s, { "0000" });
        while (offset < pkg.size()) {
            string hints;
            string proof;
            string msg;
            SequentialBytesToString(pkg, offset, hints); // pass through
            SequentialBytesToString(pkg, offset, proof); // pass through
            SequentialBytesToString(pkg, offset, msg);
            CROSSCHAIN_DEBUG_LOG(""s, { "0001", hints, proof, msg, pkg });
            ForwardAMPkg(domain_name, msg);
        }
        Require(pkg.size() == offset, "AMMSG_ERROR: offset incorrect RecvNotaryPkgFromRelayer");
    }
};

#endif //CROSSCHAIN_SYS_AUTH_MESSAGE_HPP

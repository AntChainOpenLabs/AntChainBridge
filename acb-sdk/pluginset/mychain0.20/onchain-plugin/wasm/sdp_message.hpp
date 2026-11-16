#ifndef CROSSCHAIN_SYS_SDP_MESSAGE_HPP
#define CROSSCHAIN_SYS_SDP_MESSAGE_HPP

//#define CROSSCHAIN_DEBUG
#include <mychainlib/contract.h>
#include <mychainlib/developer_api.hpp>
#include "schema/sdp_message_ant_generated.h"
#include "utils/type_to_bytes.hpp"
#include "utils/bytes_to_type.hpp"
#include "utils/utils.hpp"

#define SDP_MSG_INTERFACES \
    (SetAmContract)(SetAmContractAndDomain) \
    (RecvMessage)(SendMessage)(SendUnorderMessage) \
    (RejectMessage)(SetMsgSeq)(QuerySDPMsgSeqOnChain)(GetLocalDomainHash)

using namespace mychain;
using namespace crosschain;
using namespace Crosschain::SDPMsgSpace;
using std::string;

class SDPMsg : public Contract {
protected:
    SDPMsgSchemaMPtr m_p;
    const uint32_t UNORDERED_SEQUENCE = 0xffffffff;
    // 业务合约实现的回调接口
    const string UNORDERED_CALLBACK_METHOD = "RecvUnorderedMessage";
    const string ORDERED_CALLBACK_METHOD = "RecvMessage";

    /*
     * 需要Admin权限的接口
     */
    void OnlyAdmin() {
        Require(m_p->get_contract_admins()->has_element(GetSender().get_data()), "SDPMSG_ERROR: not from admin");
    }

    void OnlyAmClient() {
        string am = m_p->get_am_contract();
        Require(am.size() == crosschain::IdentityLength, "SDPMSG_ERROR: AM address in contract invalid");
        Require(am == GetSender().get_data(), "SDPMSG_ERROR: not from AM client");
    }

    void ParseMessage(const string &byte_array, Identity &receiver_id, string &msg, uint32_t &recv_seq) {
        uint32_t offset = byte_array.size();
        string receiver_domain;
        BytesToString(byte_array, offset, receiver_domain);
        Require(receiver_domain == m_p->get_this_domain(), "SDPMSG_ERROR: domain name mismatch");

        BytesToIdentity(byte_array, offset, receiver_id);
        BytesToUint32(byte_array, offset, recv_seq);
        BytesToString(byte_array, offset, msg);
        Require(offset == 0, "SDPMSG_ERROR: ParseMessage taking out failed");
    }

    void BuildMessageCore(const uint32_t this_send_seq, const string &receiver_domain, const Identity &receiver_id,
                          const string &msg, string &byte_array) {
        // msg + seq + rev_id + rev_domain
        uint32_t offset = BytesReserveForString(msg) + 4 + 32 + BytesReserveForString(receiver_domain);
        byte_array.clear();
        byte_array.resize(offset, 0); // byte_array置0

        StringToBytes(receiver_domain, byte_array, offset);
        IdentityToBytes(receiver_id, byte_array, offset);
        Uint32ToBytes(this_send_seq, byte_array, offset);
        StringToBytes(msg, byte_array, offset);
        Require(offset == 0, "SDPMSG_ERROR: buildMessage filling failed");
    }

    void BuildMessageOrdered(const string &receiver_domain, const Identity &receiver_id, const string &msg,
                             string &byte_array, uint32_t &send_seq) {
        SendContextMPtr p_send_ctx = get_send_ctx(
                crosschain::SHA256RecursiveDigest(GetSender().get_data(), receiver_domain, receiver_id.get_data()));
        BuildMessageCore(p_send_ctx->get_sequence(), receiver_domain, receiver_id, msg, byte_array);

        p_send_ctx->set_sequence(1 + p_send_ctx->get_sequence());
        Log(""s, {"SetSendSequence", GetSender().get_data() + ":" + receiver_domain + ":" + \
                  receiver_id.get_data(), std::to_string(p_send_ctx->get_sequence())});
        send_seq = p_send_ctx->get_sequence();
    }

    void BuildMessageUnordered(const string &receiver_domain, const Identity &receiver_id, const string &msg,
                               string &byte_array, uint32_t &send_seq) {
        BuildMessageCore(UNORDERED_SEQUENCE, receiver_domain, receiver_id, msg, byte_array);
        send_seq = UNORDERED_SEQUENCE;
    }

    SendContextMPtr get_send_ctx(const string &ctx_hash) {
        if (m_p->get_send_info()->has_element(ctx_hash))
            return m_p->get_send_info()->get_element(ctx_hash);
        else
            return m_p->get_send_info()->add_element(ctx_hash);
    }

    RecvContextMPtr get_recv_ctx(const string &ctx_hash) {
        if (m_p->get_recv_info()->has_element(ctx_hash))
            return m_p->get_recv_info()->get_element(ctx_hash);
        else
            return m_p->get_recv_info()->add_element(ctx_hash);
    }

    void recvMessageCore(const string &sender_domain, const string &sender_id_str,
                         const string &pkg) {
        const Identity sender_id = Identity(sender_id_str);
        Identity receiver_id;
        string msg;
        uint32_t recv_seq;
        ParseMessage(pkg, receiver_id, msg, recv_seq);

        string callback_method;
        int ret_code;
        string ret_msg;
        if (UNORDERED_SEQUENCE == recv_seq) {
            auto ret = CallContract<void>(receiver_id, UNORDERED_CALLBACK_METHOD, value, gas,
                                          sender_domain, sender_id, msg);
            Require(0 == ret.code, "SDPMSG_ERROR: calling client methed " + callback_method + " failed: " + ret.msg);
            ret_code = ret.code;
            ret_msg = ret.msg;
        } else {
            RecvContextMPtr p_recv_ctx = get_recv_ctx(
                    crosschain::SHA256RecursiveDigest(sender_domain, sender_id.get_data(), receiver_id.get_data()));
            Require(recv_seq == p_recv_ctx->get_sequence(), "SDPMSG_ERROR: invalid receiving sequence");
            p_recv_ctx->set_sequence(1 + p_recv_ctx->get_sequence());
            auto ret = CallContract<void>(receiver_id, ORDERED_CALLBACK_METHOD, value, gas,
                                          sender_domain, sender_id, msg);
            ret_code = ret.code;
            ret_msg = ret.msg;
        }
        Log(ret_msg, {
                "ReceiveMessage", sender_domain, sender_id_str, receiver_id.get_data(), std::to_string(recv_seq),
                std::to_string(ret_code)
        });
    }

public:
    SDPMsg() {
        m_p = GetSDPMsgSchemaM();
    }

    INTERFACE void init() {
        Crosschain::SDPMsgSpace::InitRoot();
        m_p = GetSDPMsgSchemaM();
        m_p->get_contract_admins()->add_element(GetSender().get_data());
        Log(""s, {"AddAdmin", GetSender().get_data()});
    }

    INTERFACE void SetAmContract(const string &id_str) {
        OnlyAdmin();
        const Identity id = Identity(id_str);
        m_p->set_am_contract(id.get_data());
        Log(""s, {"SetAMContract", id.get_data()});
    }

    INTERFACE void SetAmContractAndDomain(const string &id_str, const string &domain) {
        SetAmContract(id_str); // OnlyAdmin保证
        m_p->set_this_domain(domain);
        Log(""s, {"SetLocalDomain", domain});
    }

    INTERFACE void RecvMessage(const string &sender_domain, const string &sender_id_str,
                               const string &pkg) {
        OnlyAmClient();
        recvMessageCore(sender_domain, sender_id_str, pkg);
    }

    INTERFACE void RejectMessage(const string &sender_domain, const string &sender_id_str,
                                 const string &receiver_id_str, const uint32_t &recv_seq) {
        OnlyAdmin();
        const Identity sender_id = Identity(sender_id_str);
        const Identity receiver_id = Identity(receiver_id_str);
        RecvContextMPtr p_recv_ctx = get_recv_ctx(
                crosschain::SHA256RecursiveDigest(sender_domain, sender_id.get_data(), receiver_id.get_data()));
        Require(recv_seq == p_recv_ctx->get_sequence(), "SDPMSG_ERROR: invalid receiving sequence rejecting");
        p_recv_ctx->set_sequence(1 + p_recv_ctx->get_sequence());
        Log(""s, {"SetRecvSequence", sender_domain + ":" + sender_id.get_data() + ":" + \
                  receiver_id.get_data(), std::to_string(p_recv_ctx->get_sequence())});
        // emit
    }

    INTERFACE void SetMsgSeq(const string &sender_domain, const string &sender_id_str,
                             const string &receiver_id_str, const uint32_t &new_recv_seq) {
        OnlyAdmin();
        const Identity sender_id = Identity(sender_id_str);
        const Identity receiver_id = Identity(receiver_id_str);
        RecvContextMPtr p_recv_ctx = get_recv_ctx(
                crosschain::SHA256RecursiveDigest(sender_domain, sender_id.get_data(), receiver_id.get_data()));
        p_recv_ctx->set_sequence(new_recv_seq);
        Log(""s, {"SetRecvSequence", sender_domain + ":" + sender_id.get_data() + ":" + \
                  receiver_id.get_data(), std::to_string(p_recv_ctx->get_sequence())});
    }

    INTERFACE void SendMessage(const string &receiver_domain, const string &receiver_id_str,
                               const string &msg) {
        const Identity receiver_id = Identity(receiver_id_str);
        string pkg;
        uint32_t send_seq;
        BuildMessageOrdered(receiver_domain, receiver_id, msg, pkg, send_seq);
        auto ret = CallContract<void>(m_p->get_am_contract(), "RecvFromProtocol", value, gas,
                                      GetSender().get_data(), pkg);
        Require(0 == ret.code, "SDPMSG_ERROR: calling AMClient RecvFromProtocol failed: " + ret.msg + " " +
                               Bin2Hex(m_p->get_am_contract()));
        // emit
    }

    INTERFACE void SendUnorderMessage(const string &receiver_domain, const string &receiver_id_str,
                                      const string &msg) {
        const Identity receiver_id = Identity(receiver_id_str);
        string pkg;
        uint32_t send_seq;
        BuildMessageUnordered(receiver_domain, receiver_id, msg, pkg, send_seq);
        auto ret = CallContract<void>(m_p->get_am_contract(), "RecvFromProtocol", value, gas,
                                      GetSender().get_data(), pkg);
        Require(0 == ret.code, "SDPMSG_ERROR: calling AMClient RecvFromProtocol failed: " + ret.msg);
        // emit
    }

    INTERFACE uint32_t

    QuerySDPMsgSeqOnChain(const string &sender_domain, const string &sender_id_str,
                          const string &this_domain, const string &receiver_id_str) {
        OnlyAdmin();
        const Identity sender_id = Identity(sender_id_str);
        const Identity receiver_id = Identity(receiver_id_str);
        Require(this_domain == m_p->get_this_domain(), "SDPMSG_ERROR: invalid destination domain");

        std::string hash = crosschain::SHA256RecursiveDigest(sender_domain, sender_id.get_data(),
                                                             receiver_id.get_data());
        if (m_p->get_recv_info()->has_element(hash)) {
            return m_p->get_recv_info()->get_element(hash)->get_sequence();
        } else {
            return 0;
        }
    }

    INTERFACE string

    GetLocalDomainHash() {
        return crosschain::SHA256RecursiveDigest(m_p->get_this_domain());
    }
};

#endif //CROSSCHAIN_SYS_SDP_MESSAGE_HPP

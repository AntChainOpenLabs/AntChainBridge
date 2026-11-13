//
// Created by zouxyan on 2023/10/13.
//

#ifndef CROSSCHAIN_SYS_CROSSCHAIN_SYS_HPP
#define CROSSCHAIN_SYS_CROSSCHAIN_SYS_HPP

#include "auth_message.hpp"
#include "sdp_message.hpp"

class CrossChainSys
        : public AuthMsg, public SDPMsg {
public:

    CrossChainSys() : AuthMsg(), SDPMsg() {
    }

    INTERFACE void Init() {
        AuthMsg::init();
        SDPMsg::init();
    }

    INTERFACE void SendMessage(const string &receiver_domain, const string &receiver_id_str,
                               const string &msg) {
        const Identity receiver_id = Identity(receiver_id_str);
        string pkg;
        uint32_t send_seq;
        BuildMessageOrdered(receiver_domain, receiver_id, msg, pkg, send_seq);
        recvFromProtocolInner(
                GetSender().get_data(),
                pkg
        );
    }

    INTERFACE void SendUnorderMessage(const string &receiver_domain, const string &receiver_id_str,
                                      const string &msg) {
        const Identity receiver_id = Identity(receiver_id_str);
        string pkg;
        uint32_t send_seq;
        BuildMessageUnordered(receiver_domain, receiver_id, msg, pkg, send_seq);
        recvFromProtocolInner(
                GetSender().get_data(),
                pkg
        );
    }

protected:
    void DoUDAGProofData(const string &proof, const string &hints, bool if_verify) override {
        string domain_name;
        string pkg;
        Require(DecodeProof(proof, domain_name, pkg), "AMMSG_ERROR: decode proof failed");

        ForwardAMPkg(domain_name, pkg);
    }

    void ForwardAMPkg(const string &domain_name, const string &pkg) override {
        Identity sender_id;
        uint32_t protocol_type;
        string msg;
        ParseAMPackage(pkg, sender_id, protocol_type, msg);
        CROSSCHAIN_DEBUG_LOG(""s, { "0002", "0002", domain_name, sender_id.get_data(), msg });

        SDPMsg::recvMessageCore(domain_name, sender_id.get_data(), msg);
    }

private:
    void recvFromProtocolInner(const string &sender_id_str, const string &msg) {
        CROSSCHAIN_DEBUG_LOG(""s, { "0000", "into recvFromProtocol" });
        Require(BytesReserveForString(msg) < (UINT32_MAX - 4 - 32 - 4),
                "AMMSG_ERROR: msg too large at RecvFromProtocol");
        const Identity sender_id = Identity(sender_id_str);
        uint32_t msg_size = BytesReserveForString(msg.size()) + 4 + 32 + 4;
        string pkg(32 + BytesReserveForString(msg_size), 0); // like EVM logdata
        uint32_t offset = 32 + 32 + msg_size;
        uint32_t version = 1;

        Uint32ToBytes(version, pkg, offset);
        IdentityToBytes(sender_id, pkg, offset);
        Uint32ToBytes(0, pkg, offset);
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
        AuthMsg::m_p->get_cached_auth_msgs()->add_element(Bin2Hex(pkg_hash))->set_auth_msg(pkg);
#else
        Log("SendAuthMessage"s, {"SendAuthMessage", pkg});
#endif
    }
};


#endif //CROSSCHAIN_SYS_CROSSCHAIN_SYS_HPP

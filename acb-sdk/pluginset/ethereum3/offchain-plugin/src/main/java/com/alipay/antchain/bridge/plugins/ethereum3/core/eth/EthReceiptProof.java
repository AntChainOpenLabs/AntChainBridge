package com.alipay.antchain.bridge.plugins.ethereum3.core.eth;

import java.util.List;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.experimental.FieldNameConstants;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.ethereum.rlp.RLP;
import org.hyperledger.besu.ethereum.trie.CompactEncoding;
import org.hyperledger.besu.ethereum.trie.patricia.TrieNodeDecoder;

@AllArgsConstructor
@Getter
@FieldNameConstants
public class EthReceiptProof {

    public static EthReceiptProof decodeFromJson(String json) {
        var jObject = JSON.parseObject(json);
        var receiptIndex = jObject.getInteger(Fields.receiptIndex);
        var proofRelatedNodes = jObject.getJSONArray(Fields.proofRelatedNodes).toJavaList(String.class).stream()
                .map(Bytes::fromHexString).toList();
        return new EthReceiptProof(receiptIndex, proofRelatedNodes);
    }

    private int receiptIndex;

    private final List<Bytes> proofRelatedNodes;

    public Bytes32 validateAndGetRoot() {
        if (this.proofRelatedNodes.isEmpty()) {
            throw new InvalidEthReceiptProofException("proof related nodes is empty");
        }

        var depth = proofRelatedNodes.size();
        var path = CompactEncoding.bytesToPath(EthDataValidator.indexKey(receiptIndex));

        var leafNode = TrieNodeDecoder.decodeNodes(null, this.proofRelatedNodes.get(depth - 1)).getFirst();
        var hash = leafNode.getHash();
        for (int i = this.proofRelatedNodes.size() - 2; i >= 0; i--) {
            Bytes node = this.proofRelatedNodes.get(i);
            var currBranch = TrieNodeDecoder.decodeNodes(null, node).getFirst();
            var currPath = path.get(i);
            if (currBranch.getChildren().get(currPath).getHash().compareTo(hash) != 0) {
                throw new InvalidEthReceiptProofException(StrUtil.format(
                        "hash mismatch at depth {}, expected {}, actual in branch {}",
                        i + 1, hash.toHexString(), currBranch.getChildren().get(currPath).getHash().toHexString()
                ));
            }
            hash = currBranch.getHash();
        }

        return TrieNodeDecoder.decodeNodes(null, this.proofRelatedNodes.getFirst()).getFirst().getHash();
    }

    public EthTransactionReceipt getEthTransactionReceipt() {
        if (this.proofRelatedNodes.isEmpty()) {
            return null;
        }
        return EthTransactionReceipt.readFromTrieValue(RLP.input(
                TrieNodeDecoder.decodeNodes(null, this.proofRelatedNodes.getLast()).getFirst().getValue().orElseThrow()
        ));
    }

    public String encodeToJson() {
        var jObject = new JSONObject();
        jObject.put(Fields.receiptIndex, this.receiptIndex);
        jObject.put(Fields.proofRelatedNodes, this.proofRelatedNodes.stream().map(Bytes::toHexString).toList());
        return jObject.toJSONString();
    }
}

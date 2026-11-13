package com.alipay.antchain.bridge.plugins.ethereum3.core.eth;

import java.util.List;
import java.util.stream.IntStream;

import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import org.hyperledger.besu.ethereum.rlp.RLP;
import org.hyperledger.besu.ethereum.trie.MerkleTrie;
import org.hyperledger.besu.ethereum.trie.patricia.SimpleMerklePatriciaTrie;

public class EthDataValidator {

    /**
     * Generates the receipt root for a list of receipts
     *
     * @param receipts the receipts
     * @return the receipt root
     */
    public static Bytes32 receiptsRoot(final List<EthTransactionReceipt> receipts) {
        return receiptsTrie(receipts).getRootHash();
    }

    public static MerkleTrie<Bytes, Bytes> receiptsTrie(final List<EthTransactionReceipt> receipts) {
        final MerkleTrie<Bytes, Bytes> trie = trie();
        IntStream.range(0, receipts.size())
                .forEach(
                        i -> trie.put(
                                indexKey(i),
                                RLP.encode(rlpOutput ->
                                        receipts.get(i).writeToForReceiptTrie(rlpOutput, false, false))
                        )
                );
        return trie;
    }

    public static EthReceiptProof getReceiptProof(int receiptIndex, final List<EthTransactionReceipt> receipts) {
        return new EthReceiptProof(
                receiptIndex,
                receiptsTrie(receipts).getValueWithProof(indexKey(receiptIndex)).getProofRelatedNodes()
        );
    }

    public static Bytes indexKey(final int i) {
        return RLP.encodeOne(UInt256.valueOf(i).trimLeadingZeros());
    }

    private static MerkleTrie<Bytes, Bytes> trie() {
        return new SimpleMerklePatriciaTrie<>(b -> b);
    }
}

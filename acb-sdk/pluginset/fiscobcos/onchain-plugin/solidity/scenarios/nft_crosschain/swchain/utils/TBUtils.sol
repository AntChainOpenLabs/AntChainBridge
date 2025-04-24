pragma solidity ^0.8.0;

contract TBUtils {
    function encodeSendingData(
        string memory src_domain, 
        string memory dest_domain,
        bytes32 dest_holder
    ) external returns (bytes memory) {
        return abi.encode(
            uint32(1), // 请求类型，默认为1
            src_domain, // 发送链域名
            dest_domain, // 接收链域名
            bytes32(0), // 发送链资产合约，TB自行填充
            bytes32(0), // 接收链资产合约，TB自行填充
            bytes32(0), // 资产发送者地址，TB自行填充
            dest_holder // 资产接收者地址
        );
    }
}
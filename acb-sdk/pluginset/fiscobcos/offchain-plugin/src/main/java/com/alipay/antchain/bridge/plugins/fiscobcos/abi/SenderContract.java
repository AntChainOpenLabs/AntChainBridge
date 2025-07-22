package com.alipay.antchain.bridge.plugins.fiscobcos.abi;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.fisco.bcos.sdk.v3.client.Client;
import org.fisco.bcos.sdk.v3.codec.datatypes.Address;
import org.fisco.bcos.sdk.v3.codec.datatypes.DynamicBytes;
import org.fisco.bcos.sdk.v3.codec.datatypes.Function;
import org.fisco.bcos.sdk.v3.codec.datatypes.Type;
import org.fisco.bcos.sdk.v3.codec.datatypes.TypeReference;
import org.fisco.bcos.sdk.v3.codec.datatypes.Utf8String;
import org.fisco.bcos.sdk.v3.codec.datatypes.generated.Bytes32;
import org.fisco.bcos.sdk.v3.codec.datatypes.generated.tuples.generated.Tuple1;
import org.fisco.bcos.sdk.v3.codec.datatypes.generated.tuples.generated.Tuple3;
import org.fisco.bcos.sdk.v3.contract.Contract;
import org.fisco.bcos.sdk.v3.crypto.CryptoSuite;
import org.fisco.bcos.sdk.v3.crypto.keypair.CryptoKeyPair;
import org.fisco.bcos.sdk.v3.model.CryptoType;
import org.fisco.bcos.sdk.v3.model.TransactionReceipt;
import org.fisco.bcos.sdk.v3.model.callback.TransactionCallback;
import org.fisco.bcos.sdk.v3.transaction.model.exception.ContractException;

@SuppressWarnings("unchecked")
public class SenderContract extends Contract {
    public static final String[] BINARY_ARRAY = {"608060405234801561001057600080fd5b5061034e806100206000396000f3fe608060405234801561001057600080fd5b50600436106100415760003560e01c806340610fd61461004657806356cc92081461005b5780636f9009de1461006e575b600080fd5b6100596100543660046101ce565b61009e565b005b6100596100693660046101ce565b61010c565b61005961007c366004610266565b600080546001600160a01b0319166001600160a01b0392909216919091179055565b6000546040516360e7662d60e11b81526001600160a01b0390911690819063c1cecc5a906100d4908690889087906004016102e3565b600060405180830381600087803b1580156100ee57600080fd5b505af1158015610102573d6000803e3d6000fd5b5050505050505050565b60005460405163f76f703b60e01b81526001600160a01b0390911690819063f76f703b906100d4908690889087906004016102e3565b634e487b7160e01b600052604160045260246000fd5b600067ffffffffffffffff8084111561017357610173610142565b604051601f8501601f19908116603f0116810190828211818310171561019b5761019b610142565b816040528093508581528686860111156101b457600080fd5b858560208301376000602087830101525050509392505050565b6000806000606084860312156101e357600080fd5b83359250602084013567ffffffffffffffff8082111561020257600080fd5b818601915086601f83011261021657600080fd5b61022587833560208501610158565b9350604086013591508082111561023b57600080fd5b508401601f8101861361024d57600080fd5b61025c86823560208401610158565b9150509250925092565b60006020828403121561027857600080fd5b81356001600160a01b038116811461028f57600080fd5b9392505050565b6000815180845260005b818110156102bc576020818501810151868301820152016102a0565b818111156102ce576000602083870101525b50601f01601f19169290920160200192915050565b6060815260006102f66060830186610296565b846020840152828103604084015261030e8185610296565b969550505050505056fea264697066735822122089864caf92bf1a82a21ef82002e4326b8ceb6858d8db0f9daa1175277533ba8864736f6c634300080b0033"};

    public static final String BINARY = org.fisco.bcos.sdk.v3.utils.StringUtils.joinAll("", BINARY_ARRAY);

    public static final String[] SM_BINARY_ARRAY = {"608060405234801561001057600080fd5b5061034e806100206000396000f3fe608060405234801561001057600080fd5b50600436106100415760003560e01c80634d508110146100465780636f0f920914610078578063e029e9fc1461008b575b600080fd5b610076610054366004610142565b600080546001600160a01b0319166001600160a01b0392909216919091179055565b005b6100766100863660046101fe565b61009e565b6100766100993660046101fe565b61010c565b6000546040516365699d8d60e01b81526001600160a01b039091169081906365699d8d906100d4908690889087906004016102e3565b600060405180830381600087803b1580156100ee57600080fd5b505af1158015610102573d6000803e3d6000fd5b5050505050505050565b60005460405163010275e160e11b81526001600160a01b03909116908190630204ebc2906100d4908690889087906004016102e3565b60006020828403121561015457600080fd5b81356001600160a01b038116811461016b57600080fd5b9392505050565b63b95aa35560e01b600052604160045260246000fd5b600067ffffffffffffffff808411156101a3576101a3610172565b604051601f8501601f19908116603f011681019082821181831017156101cb576101cb610172565b816040528093508581528686860111156101e457600080fd5b858560208301376000602087830101525050509392505050565b60008060006060848603121561021357600080fd5b83359250602084013567ffffffffffffffff8082111561023257600080fd5b818601915086601f83011261024657600080fd5b61025587833560208501610188565b9350604086013591508082111561026b57600080fd5b508401601f8101861361027d57600080fd5b61028c86823560208401610188565b9150509250925092565b6000815180845260005b818110156102bc576020818501810151868301820152016102a0565b818111156102ce576000602083870101525b50601f01601f19169290920160200192915050565b6060815260006102f66060830186610296565b846020840152828103604084015261030e8185610296565b969550505050505056fea264697066735822122030feeb41f59cdba4c6e813406d34620166c67cd06be8b7009dc07b7c5066f62664736f6c634300080b0033"};

    public static final String SM_BINARY = org.fisco.bcos.sdk.v3.utils.StringUtils.joinAll("", SM_BINARY_ARRAY);

    public static final String[] ABI_ARRAY = {"[{\"inputs\":[{\"internalType\":\"bytes32\",\"name\":\"receiver\",\"type\":\"bytes32\"},{\"internalType\":\"string\",\"name\":\"domain\",\"type\":\"string\"},{\"internalType\":\"bytes\",\"name\":\"_msg\",\"type\":\"bytes\"}],\"name\":\"send\",\"outputs\":[],\"stateMutability\":\"nonpayable\",\"type\":\"function\"},{\"inputs\":[{\"internalType\":\"bytes32\",\"name\":\"receiver\",\"type\":\"bytes32\"},{\"internalType\":\"string\",\"name\":\"domain\",\"type\":\"string\"},{\"internalType\":\"bytes\",\"name\":\"_msg\",\"type\":\"bytes\"}],\"name\":\"sendUnordered\",\"outputs\":[],\"stateMutability\":\"nonpayable\",\"type\":\"function\"},{\"inputs\":[{\"internalType\":\"address\",\"name\":\"_sdp_address\",\"type\":\"address\"}],\"name\":\"setSdpMSGAddress\",\"outputs\":[],\"stateMutability\":\"nonpayable\",\"type\":\"function\"}]"};

    public static final String ABI = org.fisco.bcos.sdk.v3.utils.StringUtils.joinAll("", ABI_ARRAY);

    public static final String FUNC_SEND = "send";

    public static final String FUNC_SENDUNORDERED = "sendUnordered";

    public static final String FUNC_SETSDPMSGADDRESS = "setSdpMSGAddress";

    protected SenderContract(String contractAddress, Client client, CryptoKeyPair credential) {
        super(getBinary(client.getCryptoSuite()), contractAddress, client, credential);
    }

    public static String getBinary(CryptoSuite cryptoSuite) {
        return (cryptoSuite.getCryptoTypeConfig() == CryptoType.ECDSA_TYPE ? BINARY : SM_BINARY);
    }

    public static String getABI() {
        return ABI;
    }

    public TransactionReceipt send(byte[] receiver, String domain, byte[] _msg) {
        final Function function = new Function(
                FUNC_SEND, 
                Arrays.<Type>asList(new org.fisco.bcos.sdk.v3.codec.datatypes.generated.Bytes32(receiver), 
                new org.fisco.bcos.sdk.v3.codec.datatypes.Utf8String(domain), 
                new org.fisco.bcos.sdk.v3.codec.datatypes.DynamicBytes(_msg)), 
                Collections.<TypeReference<?>>emptyList(), 0);
        return executeTransaction(function);
    }

    public Function getMethodSendRawFunction(byte[] receiver, String domain, byte[] _msg) throws
            ContractException {
        final Function function = new Function(FUNC_SEND, 
                Arrays.<Type>asList(new org.fisco.bcos.sdk.v3.codec.datatypes.generated.Bytes32(receiver), 
                new org.fisco.bcos.sdk.v3.codec.datatypes.Utf8String(domain), 
                new org.fisco.bcos.sdk.v3.codec.datatypes.DynamicBytes(_msg)), 
                Arrays.<TypeReference<?>>asList());
        return function;
    }

    public String getSignedTransactionForSend(byte[] receiver, String domain, byte[] _msg) {
        final Function function = new Function(
                FUNC_SEND, 
                Arrays.<Type>asList(new org.fisco.bcos.sdk.v3.codec.datatypes.generated.Bytes32(receiver), 
                new org.fisco.bcos.sdk.v3.codec.datatypes.Utf8String(domain), 
                new org.fisco.bcos.sdk.v3.codec.datatypes.DynamicBytes(_msg)), 
                Collections.<TypeReference<?>>emptyList(), 0);
        return createSignedTransaction(function);
    }

    public String send(byte[] receiver, String domain, byte[] _msg, TransactionCallback callback) {
        final Function function = new Function(
                FUNC_SEND, 
                Arrays.<Type>asList(new org.fisco.bcos.sdk.v3.codec.datatypes.generated.Bytes32(receiver), 
                new org.fisco.bcos.sdk.v3.codec.datatypes.Utf8String(domain), 
                new org.fisco.bcos.sdk.v3.codec.datatypes.DynamicBytes(_msg)), 
                Collections.<TypeReference<?>>emptyList(), 0);
        return asyncExecuteTransaction(function, callback);
    }

    public Tuple3<byte[], String, byte[]> getSendInput(TransactionReceipt transactionReceipt) {
        String data = transactionReceipt.getInput().substring(10);
        final Function function = new Function(FUNC_SEND, 
                Arrays.<Type>asList(), 
                Arrays.<TypeReference<?>>asList(new TypeReference<Bytes32>() {}, new TypeReference<Utf8String>() {}, new TypeReference<DynamicBytes>() {}));
        List<Type> results = this.functionReturnDecoder.decode(data, function.getOutputParameters());
        return new Tuple3<byte[], String, byte[]>(

                (byte[]) results.get(0).getValue(), 
                (String) results.get(1).getValue(), 
                (byte[]) results.get(2).getValue()
                );
    }

    public TransactionReceipt sendUnordered(byte[] receiver, String domain, byte[] _msg) {
        final Function function = new Function(
                FUNC_SENDUNORDERED, 
                Arrays.<Type>asList(new org.fisco.bcos.sdk.v3.codec.datatypes.generated.Bytes32(receiver), 
                new org.fisco.bcos.sdk.v3.codec.datatypes.Utf8String(domain), 
                new org.fisco.bcos.sdk.v3.codec.datatypes.DynamicBytes(_msg)), 
                Collections.<TypeReference<?>>emptyList(), 0);
        return executeTransaction(function);
    }

    public Function getMethodSendUnorderedRawFunction(byte[] receiver, String domain, byte[] _msg)
            throws ContractException {
        final Function function = new Function(FUNC_SENDUNORDERED, 
                Arrays.<Type>asList(new org.fisco.bcos.sdk.v3.codec.datatypes.generated.Bytes32(receiver), 
                new org.fisco.bcos.sdk.v3.codec.datatypes.Utf8String(domain), 
                new org.fisco.bcos.sdk.v3.codec.datatypes.DynamicBytes(_msg)), 
                Arrays.<TypeReference<?>>asList());
        return function;
    }

    public String getSignedTransactionForSendUnordered(byte[] receiver, String domain,
            byte[] _msg) {
        final Function function = new Function(
                FUNC_SENDUNORDERED, 
                Arrays.<Type>asList(new org.fisco.bcos.sdk.v3.codec.datatypes.generated.Bytes32(receiver), 
                new org.fisco.bcos.sdk.v3.codec.datatypes.Utf8String(domain), 
                new org.fisco.bcos.sdk.v3.codec.datatypes.DynamicBytes(_msg)), 
                Collections.<TypeReference<?>>emptyList(), 0);
        return createSignedTransaction(function);
    }

    public String sendUnordered(byte[] receiver, String domain, byte[] _msg,
            TransactionCallback callback) {
        final Function function = new Function(
                FUNC_SENDUNORDERED, 
                Arrays.<Type>asList(new org.fisco.bcos.sdk.v3.codec.datatypes.generated.Bytes32(receiver), 
                new org.fisco.bcos.sdk.v3.codec.datatypes.Utf8String(domain), 
                new org.fisco.bcos.sdk.v3.codec.datatypes.DynamicBytes(_msg)), 
                Collections.<TypeReference<?>>emptyList(), 0);
        return asyncExecuteTransaction(function, callback);
    }

    public Tuple3<byte[], String, byte[]> getSendUnorderedInput(
            TransactionReceipt transactionReceipt) {
        String data = transactionReceipt.getInput().substring(10);
        final Function function = new Function(FUNC_SENDUNORDERED, 
                Arrays.<Type>asList(), 
                Arrays.<TypeReference<?>>asList(new TypeReference<Bytes32>() {}, new TypeReference<Utf8String>() {}, new TypeReference<DynamicBytes>() {}));
        List<Type> results = this.functionReturnDecoder.decode(data, function.getOutputParameters());
        return new Tuple3<byte[], String, byte[]>(

                (byte[]) results.get(0).getValue(), 
                (String) results.get(1).getValue(), 
                (byte[]) results.get(2).getValue()
                );
    }

    public TransactionReceipt setSdpMSGAddress(String _sdp_address) {
        final Function function = new Function(
                FUNC_SETSDPMSGADDRESS, 
                Arrays.<Type>asList(new org.fisco.bcos.sdk.v3.codec.datatypes.Address(_sdp_address)), 
                Collections.<TypeReference<?>>emptyList(), 0);
        return executeTransaction(function);
    }

    public Function getMethodSetSdpMSGAddressRawFunction(String _sdp_address) throws
            ContractException {
        final Function function = new Function(FUNC_SETSDPMSGADDRESS, 
                Arrays.<Type>asList(new org.fisco.bcos.sdk.v3.codec.datatypes.Address(_sdp_address)), 
                Arrays.<TypeReference<?>>asList());
        return function;
    }

    public String getSignedTransactionForSetSdpMSGAddress(String _sdp_address) {
        final Function function = new Function(
                FUNC_SETSDPMSGADDRESS, 
                Arrays.<Type>asList(new org.fisco.bcos.sdk.v3.codec.datatypes.Address(_sdp_address)), 
                Collections.<TypeReference<?>>emptyList(), 0);
        return createSignedTransaction(function);
    }

    public String setSdpMSGAddress(String _sdp_address, TransactionCallback callback) {
        final Function function = new Function(
                FUNC_SETSDPMSGADDRESS, 
                Arrays.<Type>asList(new org.fisco.bcos.sdk.v3.codec.datatypes.Address(_sdp_address)), 
                Collections.<TypeReference<?>>emptyList(), 0);
        return asyncExecuteTransaction(function, callback);
    }

    public Tuple1<String> getSetSdpMSGAddressInput(TransactionReceipt transactionReceipt) {
        String data = transactionReceipt.getInput().substring(10);
        final Function function = new Function(FUNC_SETSDPMSGADDRESS, 
                Arrays.<Type>asList(), 
                Arrays.<TypeReference<?>>asList(new TypeReference<Address>() {}));
        List<Type> results = this.functionReturnDecoder.decode(data, function.getOutputParameters());
        return new Tuple1<String>(

                (String) results.get(0).getValue()
                );
    }

    public static SenderContract load(String contractAddress, Client client,
            CryptoKeyPair credential) {
        return new SenderContract(contractAddress, client, credential);
    }

    public static SenderContract deploy(Client client, CryptoKeyPair credential) throws
            ContractException {
        return deploy(SenderContract.class, client, credential, getBinary(client.getCryptoSuite()), getABI(), null, null);
    }
}
